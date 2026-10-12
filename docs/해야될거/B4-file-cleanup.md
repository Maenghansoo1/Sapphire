# B4. 그룹 삭제·나가기 때 고아 파일 정리 (백엔드)

> 범위: 백엔드만. 알림·배포(S3) 무관 — 로컬 디스크 `FileStorage` 기준이며 S3 로 바뀌어도 `FileStorage.delete(key)` 만 쓰므로 그대로 동작. **전제: 7·8단계 + B1 커밋 후**(그룹 내 프로필 사진 key 컬럼이 B1 에서 생긴다). 근거: `CONTRACT_DECISIONS.md` §2-4("그룹 삭제 … 제출물 파일 정리는 파일 저장소 구현 시 함께"), §6-4 "그룹 삭제 후 파일 정리도 후속 단계".

## Goal

DB 행만 지워지고 파일이 남는 경로를 없앤다.
1. **그룹이 삭제될 때**(혼자인 그룹장이 나가기/탈퇴로 `deleteGroupCascade`): 그 그룹의 제출물 파일(`submissions.media_url`), 댓글 이미지(`comments.image_url`), 멤버들의 그룹 내 프로필 사진(`group_members.profile_image_url_override`)을 커밋 후 삭제.
2. **일반 멤버가 그룹을 나갈 때**: 본인의 그룹 내 프로필 사진 파일 삭제(작품·댓글은 그룹에 남는다 — 기존 규칙 유지).

## Findings

- `GroupService.leaveGroup(userId, groupId, successorUserId)`: 일반 멤버는 `groupMemberMapper.deleteByGroupIdAndUserId`; 혼자인 그룹장은 `deleteGroupCascade(groupId)`(순서: 댓글 멘션 → 댓글 → 반응 → 추측 → 제출 → 콕 → 미션 → 슬롯 → 멤버 → 그룹). 어느 경로도 파일을 지우지 않는다.
- 파일을 가리키는 컬럼(모두 **key** 저장, `FileStorage.publicUrl(key)` 로 URL): `submissions.media_url`, `comments.image_url`(7단계), `users.profile_image_url`(8단계), `group_members.profile_image_url_override`(B1).
- `FileStorage.delete(key)` 는 `LocalFileStorage` 에서 `Files.deleteIfExists`, 실패 시 `UncheckedIOException`, 키가 루트를 벗어나면 `IllegalArgumentException`.
- 커밋 후 삭제 패턴(7단계 `CommentService.delete`, 8단계 `ProfileService`/`WithdrawService`): `TransactionSynchronizationManager.registerSynchronization(afterCommit …)` + key 별 try/catch + `log.warn`. 중복 코드가 세 군데 생겼으므로 이 계획에서 **작은 공용 헬퍼**를 만들어 새 코드에 쓴다(기존 세 군데 리팩터링은 하지 않는다 — 범위 확장 금지).
- 8단계 `WithdrawService` 는 `leaveGroup(userId, groupId, null)` 을 재사용해 혼자인 그룹을 cascade 삭제하면서 자기 파일 key 를 **미리 수집해 따로 삭제**한다. 이 계획 이후엔 cascade 삭제가 그룹 파일을 정리하므로 탈퇴 경로에서 같은 key 를 두 번 지울 수 있다 → `delete` 는 멱등이어야 한다(`deleteIfExists`)이고 문제 없음. 단 로그 노이즈를 피하려고 코드 중복 삭제를 허용하는 쪽으로 둔다(수정하지 않는다).

## Root Cause / Key Implementation Points

1. **공용 헬퍼** `global/storage/FileCleanup`(`@Component`): `void deleteAfterCommit(Collection<String> keys)` — 활성 트랜잭션 동기화가 있으면 `afterCommit` 에서, 없으면 즉시 삭제. key 별 try/catch(`RuntimeException` → `log.warn`), null/빈 key 무시, 중복 제거. (롤백 시 삭제 안 함 = 의도.)
2. **그룹 단위 key 수집 쿼리**(`GroupMapper`에 추가, DB 삭제 **전에** 호출): 
   - `findSubmissionMediaKeysByGroupId(groupId)` — `SELECT s.media_url FROM submissions s JOIN missions m ON m.id = s.mission_id WHERE m.group_id = ? AND s.media_url IS NOT NULL`
   - `findCommentImageKeysByGroupId(groupId)` — 같은 경로의 `comments.image_url IS NOT NULL`
   - `findGroupProfileImageKeysByGroupId(groupId)` — `SELECT profile_image_url_override FROM group_members WHERE group_id = ? AND profile_image_url_override IS NOT NULL`
3. **적용 지점**: `GroupService.deleteGroupCascade(groupId)` 맨 앞에서 위 세 목록을 모아 `fileCleanup.deleteAfterCommit(keys)` 예약(실제 삭제는 커밋 후라서 이후 DB 삭제가 실패해 롤백되면 파일은 남는다). 일반 멤버 나가기 분기에서는 **그 멤버의 `profile_image_url_override` 만** 읽어 예약(`GroupMemberMapper.findByGroupIdAndUserId` 결과에 B1 이 이미 필드를 추가했으므로 재사용).
4. `GroupService` 외에 `deleteGroupCascade` 호출부가 더 있는지 확인(현재는 `leaveGroup` 한 곳). 호출부가 늘어도 수집이 `deleteGroupCascade` 안에 있으므로 자동 적용.
5. 기존 삭제 SQL·순서는 변경하지 않는다.

## Files / Functions to Change

| 파일 | 변경 |
|---|---|
| `global/storage/FileCleanup.java` (신규) | Key Point 1 |
| `group/dao/GroupMapper.java` | 수집 쿼리 3개 |
| `group/service/GroupService.java` | 생성자에 `FileCleanup` 추가, `deleteGroupCascade` 와 일반 멤버 나가기 분기에서 예약 호출 |
| 테스트 | 아래 |
| 문서 | `CONTRACT_DECISIONS.md` §2-4·§6-4 의 "그룹 삭제 후 파일 정리" 를 "반영됨(그룹 삭제·나가기)" 로, `API_DESIGN.md` 그룹 나가기 설명에 "그룹이 삭제되면 파일도 삭제" 한 줄 |

## Implementation Steps

1. `FileCleanup` + 단위 테스트. 2. 수집 쿼리 + 매퍼 테스트. 3. `GroupService` 연결. 4. 통합 테스트(임시 업로드 디렉터리). 5. 전체 테스트·문서. (스키마 변경 없음 — DB 재생성 불필요.)

## Constraints

- 스키마 변경 없음. 삭제는 **커밋 후**에만, 실패는 로그만(요청 실패 금지). S3/원격 호출 가정 금지(`FileStorage` 인터페이스만 사용, 인터페이스 변경 금지).
- 기존 `CommentService`/`ProfileService`/`WithdrawService` 의 삭제 코드를 `FileCleanup` 으로 바꾸는 리팩터링 금지. 일괄 "고아 파일 스캔/배치" 도입 금지.
- 일반 멤버 나가기에서 **작품·댓글 파일은 지우지 않는다**. 커밋/푸시·지침 파일 수정 금지.

## Edge Cases

- 파일이 이미 없음(수동 삭제) → `deleteIfExists` 로 무시. key 가 비정상(`..` 포함) → `IllegalArgumentException` 을 잡아 로그만, 나머지 key 계속 처리.
- 삭제 도중 DB 롤백(예: 후속 쿼리 실패) → 파일 보존.
- 같은 key 가 중복(여러 행이 같은 key — 정상적으론 없음)이어도 한 번만 삭제.
- 그룹에 제출물/댓글이 0개 → 예약 key 목록이 비어 있어도 오류 없음.
- 혼자인 그룹장 탈퇴(8단계) → 그룹 cascade 파일 + 탈퇴 경로의 개인 파일 삭제가 겹쳐도 정상.
- 트랜잭션 동기화가 없는 호출(테스트에서 `@Transactional` 없이 서비스 직접 호출)에서는 즉시 삭제.

## Regression Risks

- `GroupService` 생성자 변경 → 직접 `new` 하는 단위 테스트 수정(`FileCleanup` 목).
- 수집 쿼리가 조인 조건을 잘못 쓰면 **다른 그룹의 파일을 지울 수 있음** → 통합 테스트에서 두 그룹을 만들고 한쪽 삭제 시 다른 쪽 파일이 남는지 반드시 검증.
- 이후 `deleteGroupCascade` 호출이 커밋 전에 예외로 끝나면 예약이 실행되지 않도록 `afterCommit` 만 사용(롤백 시 실행 금지).

## Tests Required

- `FileCleanupTests`: 커밋 후 삭제(트랜잭션 동기화 사용 또는 `TransactionTemplate`), 롤백 시 삭제 안 함, 동기화 없으면 즉시, 개별 실패가 나머지를 막지 않음, null/중복 처리.
- `GroupMapperTests`(실 MariaDB): 세 수집 쿼리가 해당 그룹의 key 만 반환(다른 그룹 제외, NULL 제외).
- `GroupServiceTests`(Mockito): 혼자인 그룹장 나가기 → `FileCleanup.deleteAfterCommit` 이 세 목록의 합집합으로 호출되고 DB 삭제보다 **앞서 수집**됨(`InOrder`), 일반 멤버 나가기 → 본인 그룹 프로필 사진 key 만, 그룹 프로필 없는 멤버 → 호출 없음/빈 목록.
- MockMvc/서비스 통합(임시 업로드 디렉터리): 그룹 2개(A 삭제 대상, B 유지)에 각각 제출 파일·댓글 이미지·그룹 프로필 사진을 만든 뒤 A 의 혼자인 그룹장 나가기 → A 파일 3종 삭제, B 파일 유지; 일반 멤버 나가기 → 그 멤버 프로필 사진만 삭제, 작품 파일 유지.

## Validation Steps

`.\mvnw.cmd clean test -q` 통과 기록, `rg "deleteGroupCascade"` 호출부 확인, `git diff --check`, 보고서에 "다른 그룹 파일 보존" 검증 결과 명시.

## Potential Merge Conflicts

`GroupService`/`GroupMapper`(B1·B2 후 순차), `global/storage/*`, `docs/BACKEND/CONTRACT_DECISIONS.md`. 8단계 `WithdrawService`(탈퇴 시 파일 수집)와 로직이 겹치지만 건드리지 않는다.
