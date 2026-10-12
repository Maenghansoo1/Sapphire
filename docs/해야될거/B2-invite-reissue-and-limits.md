# B2. 초대 코드 재발급 + 코드 조회 시도 제한 (백엔드)

> 범위: 백엔드만. 앱(16 화면 재발급 버튼)은 A3. 알림 무관. 결정 근거: `CONTRACT_DECISIONS.md` §2-1 "초대 방식/초대 권한", §2-2 "초대 코드", §2-4 미결 "코드 대입 시도 제한". **B1 과 같은 파일(`GroupService`, `GroupMapper`)을 건드리므로 B1 커밋 후 진행.**

## Goal

1. `POST /api/groups/{groupId}/invite-code` — **그룹장만** 재발급. 이전 코드는 즉시 무효. 응답 `{ "inviteCode": "M8R3TQ", "inviteUrl": "https://hawe.app/invite/M8R3TQ" }`(200). 403 `NOT_GROUP_OWNER`, 404 `GROUP_NOT_FOUND`(멤버가 아닌 사람도 404 가 아니라 403 `NOT_GROUP_MEMBER` — 기존 `requireMember`/`requireOwner` 와 같은 순서).
2. `GET /api/invites/{code}`(미리보기)와 `POST /api/groups/join` 의 **무차별 대입 방지**: 사용자별로 "코드를 못 찾은 시도(404 `INVITE_NOT_FOUND`/형식 오류)"가 짧은 시간에 많으면 429 `TOO_MANY_REQUESTS`.

## Findings

- 코드는 `groups.invite_code char(6) NOT NULL UNIQUE`, 생성은 `InviteCodeGenerator.generate()`(문자집합 `ABCDEFGHJKLMNPQRSTUVWXYZ23456789`, `SecureRandom`), 정규화 `normalize(raw)`(대문자·길이6·문자집합, 아니면 null). `GroupService.createGroup` 이 최대 5회 재시도하며 `DuplicateKeyException` 을 처리한다 — **같은 방식으로 재발급**.
- `GroupMapper.update(Group)` 는 `invite_code` 를 갱신하지 않는다(name/owner/relation/daily_count/start_date). `findByIdForUpdate(groupId)` 로 행 잠금 가능. `GroupService.requireOwner(group, userId)`, `requireMember(groupId, userId)` 존재.
- `inviteUrl` 은 `GroupService.toGroupResponse` 가 `inviteBaseUrl + "/" + code` 로 만든다(`hawe.invite.base-url`, 기본 `https://hawe.app/invite`).
- `GroupController`(`/api/groups`)에 재발급 엔드포인트 없음. `InviteController`(`GET /api/invites/{code}` → `previewInvite`), 가입 `POST /api/groups/join` → `join`. 둘 다 `findInviteGroup(rawCode, lock)` → 못 찾으면 `INVITE_NOT_FOUND`.
- `CommonErrorCode` 에 429 없음. 전역 예외 처리기는 `BusinessException` 을 `ErrorCode.httpStatus()` 로 응답.
- 단일 서버 인스턴스를 가정한 메모리 제한기로 충분(배포 확장 시 교체 — 문서에 한 줄).

## Root Cause / Key Implementation Points

1. **재발급 트랜잭션**: `@Transactional reissueInviteCode(userId, groupId)` — `findByIdForUpdate` → 404 → `requireOwner`(멤버가 아니면 403 `NOT_GROUP_MEMBER` 가 먼저 나오게 `requireMember` 후 `requireOwner`) → 새 코드 생성(**현재 코드와 같으면 다시 생성**) → `UPDATE groups SET invite_code=? WHERE id=?`, `DuplicateKeyException` 이면 최대 5회 재시도 후 `IllegalStateException`(createGroup 과 동일). 이전 코드는 UPDATE 로 즉시 사라져 무효. 응답은 새 코드와 `inviteBaseUrl/코드`.
2. **시도 제한**: `global/ratelimit/AttemptLimiter`(신규, 인메모리) — 키 = `userId`, 규칙 = **10분 창에서 실패 10회 초과 시 차단**(설정: `hawe.invite.max-failures=10`, `hawe.invite.failure-window=10m`). 사용법: 서비스가 `limiter.check(userId)`(차단 중이면 429 던짐) → 코드 조회 → `INVITE_NOT_FOUND`/정규화 실패면 `limiter.recordFailure(userId)`. **성공 시 초기화하지 않는다**(성공으로 카운트를 리셋하면 우회 가능 — 단, 성공은 카운트하지도 않는다). 차단 중에는 유효한 코드로도 429(차단 해제까지). 만료 항목은 접근 시 정리(별도 스케줄러 금지).
   - 적용 대상: `GroupService.previewInvite`, `GroupService.join` (둘이 같은 한도를 공유).
   - 새 `CommonErrorCode.TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, "요청이 너무 많습니다. 잠시 후 다시 시도해 주세요")`.
3. 시간은 `Clock` 주입 또는 `SeoulTime`/`System.nanoTime` 대신 테스트 가능하게 `java.time.Clock` 을 생성자로 받는다(기존 `SeoulTime` 이 고정 시계를 지원하는지 먼저 확인하고, 없으면 `Clock` 빈 대신 `AttemptLimiter` 생성자에 `Clock` 파라미터를 두고 기본 빈은 `Clock.systemUTC()` 로 구성).

## Files / Functions to Change

| 파일 | 변경 |
|---|---|
| `group/dao/GroupMapper.java` | `@Update int updateInviteCode(@Param("groupId") Long groupId, @Param("code") String code)` |
| `group/service/GroupService.java` | `reissueInviteCode`, `previewInvite`/`join` 에 limiter 적용(생성자에 `AttemptLimiter` 추가 — `GroupService` 를 직접 `new` 하는 테스트 수정) |
| `group/controller/GroupController.java` | `@PostMapping("/{groupId}/invite-code") InviteCodeResponse reissueInviteCode(@AuthenticationPrincipal Long userId, @PathVariable Long groupId)` (200) |
| `group/dto/InviteCodeResponse.java` (신규) | `record InviteCodeResponse(String inviteCode, String inviteUrl)` |
| `global/ratelimit/AttemptLimiter.java` (신규) | 인메모리 실패 카운터 |
| `global/exception/CommonErrorCode.java` | `TOO_MANY_REQUESTS` |
| `src/main/resources/application.properties` | `hawe.invite.max-failures`, `hawe.invite.failure-window` 기본값 |
| 문서 | `API_DESIGN.md` §2(재발급 API·429), `CONTRACT_DECISIONS.md` §2-4 시도 제한 확정(인메모리·단일 인스턴스 가정, 배포 시 재검토), Postman `HAWE_GROUP` 에 재발급 요청 |

## Implementation Steps

1. `AttemptLimiter` + 단위 테스트. 2. `CommonErrorCode`·프로퍼티. 3. 매퍼·서비스·컨트롤러. 4. `previewInvite`/`join` 연결. 5. 테스트 → `.\mvnw.cmd clean test -q`. 6. 문서·Postman. (스키마 변경 없음 — DB 재생성 불필요.)

## Constraints

- 스키마 변경 없음. 기존 코드 형식(대문자 6자, 문자집합) 유지. 이전 코드 별도 보관/유예 기간 금지(즉시 무효).
- Redis·외부 저장소 도입 금지(인메모리). 사용자 단위 키만(IP 단위 금지 — 프록시 환경 미정).
- 커밋/푸시·지침 파일 수정 금지.

## Edge Cases

- 재발급 직후 이전 코드로 미리보기/가입 → 404 `INVITE_NOT_FOUND`(그리고 실패 카운트 증가). 새 코드는 즉시 사용 가능.
- 그룹장이 아닌 멤버 403 `NOT_GROUP_OWNER`, 비멤버 403 `NOT_GROUP_MEMBER`, 없는 그룹 404, 토큰 없음 401.
- 동시 재발급 2건 → 행 잠금으로 직렬화되어 마지막 코드만 유효, 둘 다 200(서로 다른 코드).
- 새 코드 충돌(유니크 위반) → 재시도로 흡수.
- 제한: 형식이 틀린 코드(5자, 소문자 변환 가능한 것은 정상 처리, 문자집합 밖)도 실패로 카운트; 이미 멤버(409 `ALREADY_MEMBER`)·가득 참(409 `GROUP_FULL`)은 "코드는 맞음"이므로 **실패로 세지 않는다**; 한도 초과 후 창이 지나면 다시 허용; 서로 다른 사용자는 독립.

## Regression Risks

- `GroupService` 생성자 변경(`AttemptLimiter`) → `GroupService` 를 직접 `new` 하는 테스트 수정(빈 주입 테스트는 영향 없음).
- 한도 때문에 기존 통합 테스트가(한 사용자가 못 찾는 코드를 10회 넘게 조회하는 경우) 429 를 받을 수 있음 — 해당 테스트가 있으면 사용자/한도를 분리.
- `GroupController` 경로 추가는 `/{groupId}/invite-code` 이며 기존 `/join` 과 겹치지 않음.

## Tests Required

- `AttemptLimiter` 단위: 한도 직전/초과, 창 만료 후 해제(시계 주입), 사용자 독립.
- `GroupServiceTests`(Mockito): 재발급 권한(멤버/그룹장/비멤버/없는 그룹), 코드 충돌 재시도, 현재 코드와 같은 값 재생성 방지, `previewInvite`/`join` 의 실패 카운트(INVITE_NOT_FOUND 만 카운트, ALREADY_MEMBER/GROUP_FULL 은 카운트 안 함) 및 차단 시 429.
- `GroupMapperTests`: `updateInviteCode` 후 이전 코드 조회 null, 새 코드 조회 성공.
- MockMvc 통합: 재발급 200/403/404/401, 이전 코드 404, 새 코드로 가입 성공, 11번째 실패에서 429 `TOO_MANY_REQUESTS`.

## Validation Steps

`.\mvnw.cmd clean test -q` 통과 기록, `rg "createInvite|InviteResponse"` 로 폐지된 JWT 초대 흔적이 없는지 확인(없어야 정상), Postman 파싱·한글 확인, `git diff --check`. 가능하면 서버 스모크, 아니면 "미검증". 보고서에 단일 인스턴스 가정 명시.

## Potential Merge Conflicts

`group/service/GroupService.java`, `GroupController.java`, `GroupMapper.java`(B1·B4 와 순차), `CommonErrorCode`, `application.properties`, `docs/BACKEND/API_DESIGN.md`.
