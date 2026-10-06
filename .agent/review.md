# Re-review: GROUP (`/api/groups`) — Codex 수정 반영분

기준: 이전 리뷰 항목, `.agent/report.md`, 현재 `git diff` 및 신규 파일
(`V2__unique_group_members.sql`, `GroupServiceTests`, `GroupMapperTests`).
코드 수정 없음. 테스트 재실행은 하지 않음(report의 `mvnw test`/`package` 통과 기록을 신뢰, 코드는 직접 읽어 확인).

## 이전 지적 사항 처리 결과

| 항목 | 결과 |
|---|---|
| P1 join 중복 가입 | **해결**. V2에서 기존 중복(최소 id 유지) 정리 후 `UNIQUE(group_id, user_id)` 추가, `DuplicateKeyException` → `ALREADY_MEMBER`. 마이그레이션이 V1을 건드리지 않고 별도 파일로 분리된 점 적절. `leaveGroup`의 다중 행 500 위험도 함께 제거됨 |
| P1 group 테스트 부재 | **대체로 해결**. 서비스 단위 테스트 + 실DB 매퍼 테스트 추가(아래 P2 참고) |
| P2 offset 오버플로우 | **해결**. `(long)` 곱셈 + 매퍼 `long offset`, 경계값 테스트 있음 |
| P2 leave 시 없는 그룹 | **해결**. `requireGroup` 선행, 테스트 있음 |
| P2 `CURDATE()` 타임존 | 보류(합당). 미션 기능에서 기준 시각 정책을 정할 때 함께 결정 |
| P2 발행/마감 시각 선후 검증 | 보류(합당). 정책 미정 |
| P2 `COMMIT_MESSAGE.md` 삭제 | 미해결, 사용자 결정 필요(아래) |

## P2

### File / Location
`GroupMemberMapper.findMembersByGroupId` — `ORDER BY gm.joined_at ASC`

### Problem
`joined_at`은 초 단위 DATETIME이라 같은 초에 가입한 멤버끼리 순서가 비결정적이다. 페이지네이션(offset/limit)과 결합하면 페이지 경계에서 멤버가 중복되거나 누락될 수 있다. (기존부터 있던 문제로 이번 수정이 만든 것은 아님)

### Why It Matters
그룹 생성 직후 owner와 초대로 들어온 멤버가 같은 초에 가입하거나 멤버가 20명을 넘는 그룹에서 목록이 흔들릴 수 있다.

### Recommended Fix
`ORDER BY gm.joined_at ASC, gm.id ASC` (`findSummariesByUserId`는 이미 `g.id DESC`로 안정적).

### File / Location
`src/test/java/.../group/service/GroupServiceTests.java`

### Problem
이전 리뷰가 요구한 케이스 중 일부가 없다: join 정상 경로와 사전 `existsBy...` 기반 `ALREADY_MEMBER`, 변조/다른 type 토큰의 `INVALID_INVITE_TOKEN`, 비owner `createInvite`, owner 탈퇴 `OWNER_CANNOT_LEAVE`, 일반 멤버 탈퇴 성공. `JwtTokenProvider`가 mock이라 "access/refresh 토큰을 초대 토큰으로 사용" 시나리오는 실제 검증 코드(type 클레임 검사)를 거치지 않는다.

### Why It Matters
이번 수정의 핵심 위험(가입/탈퇴 경로)에서 정상 경로와 owner 보호가 회귀해도 테스트가 잡지 못한다. 단, 코드 자체는 읽어서 맞다고 확인했으므로 결함이 아니라 커버리지 갭이다.

### Recommended Fix
위 4~5개 케이스를 단위 테스트에 추가. 토큰 type 검증은 실제 `JwtTokenProvider`를 쓰는 작은 테스트로 커버. merge 차단 사유는 아님.

### File / Location
워크트리: `docs/COMMIT_MESSAGE.md` 삭제(미커밋)

### Problem
group 작업과 무관한 삭제가 그대로 남아 있음. Codex가 의도적으로 건드리지 않은 것은 타당.

### Why It Matters
`git add -A`/`commit -a` 시 함께 커밋되면 `ba15c77`에서 추가한 커밋 가이드 문서가 사라진다.

### Recommended Fix
삭제 의도가 없다면 사용자가 복원(`git restore docs/COMMIT_MESSAGE.md`)하고, 커밋 시 group 변경분만 스테이징.

## 확인 사항 (문제 없음)
- V2의 `DELETE ... JOIN` 구문과 제약 추가가 로컬 MariaDB에서 적용된 것을 report가 확인. 중복 행의 role이 서로 다른 데이터가 운영에 있다면 낮은 id만 남는 점은 report에도 위험으로 명시됨(현재 owner 행은 항상 그룹 생성 시 가장 먼저 삽입되므로 일반적으로 안전).
- `joinByInvite`에서 `DuplicateKeyException`을 잡은 뒤 `BusinessException`을 던지므로 트랜잭션은 롤백됨, 부분 저장 없음.
- 매퍼 테스트는 `@Transactional`로 롤백되어 DB를 오염시키지 않음. 실DB(MariaDB) 필요 조건은 기존 컨텍스트 테스트와 동일.
- 변경 범위는 group 모듈과 V2/테스트에 한정되어 unrelated 코드 수정 없음. 병렬 feature와 충돌 가능 지점은 `group_members` 스키마(V2 번호)뿐이므로, 다른 브랜치가 V2 마이그레이션을 추가했다면 번호 조정 필요.

## 결론: 승인 가능
(남은 항목은 P2 수준의 보강/정리. `COMMIT_MESSAGE.md` 삭제 건만 커밋 전에 사용자가 확인할 것.)
