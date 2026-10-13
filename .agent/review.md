# B2 초대 코드 재발급 + 코드 조회 시도 제한 리뷰

대상: B2 관련 미커밋 변경(`group/`, `global/ratelimit/`, `CommonErrorCode`, `application.properties`, 테스트, 문서/Postman), `.agent/plan.md`, `.agent/report.md`. 코드는 수정하지 않았다. 작업 트리에는 아직 커밋되지 않은 **B3 변경이 함께 있어** B2 범위 파일만 따로 검토했다.

## 확인한 것

- 재발급(`GroupService.reissueInviteCode`): `findByIdForUpdate` → 404 → `requireMember`(403) → `requireOwner`(403) 순서, 행 잠금으로 동시 재발급·`join` 직렬화, 새 코드 생성(현재 코드와 같으면 건너뜀), `DuplicateKeyException` 최대 5회 재시도, 응답 `inviteBaseUrl + "/" + code`. `GroupMapper.updateInviteCode` 추가(기존 `update` 는 코드 미갱신이므로 필요). 이전 코드는 UPDATE 로 즉시 무효.
- 제한기: `findInviteGroup(userId, …)` 가 호출 전 `check`, `INVITE_NOT_FOUND`(형식 오류 포함)만 `recordFailure` 후 rethrow. `checkJoinable` 의 `ALREADY_MEMBER`/`GROUP_FULL` 은 `findInviteGroup` 밖이라 카운트되지 않음. 미리보기·가입이 같은 키(`userId`)와 한도 공유. 차단 중 요청은 기록 안 함(윈도 연장 없음), 성공 시 초기화 없음 — 계획과 일치.
- `AttemptLimiter`: `computeIfPresent`/`compute` 로 키별 원자성 확보, 만료 정리는 접근 시 + 64회마다 전체 정리(스케줄러 없음), 빈 deque 제거, 두 생성자 중 `@Autowired` 지정, 설정값 검증. `Clock` 주입으로 테스트 가능. 동시 요청 시 한도를 약간 넘을 수 있는 경합은 인메모리 제한기에서 허용 가능한 수준.
- `CommonErrorCode.TOO_MANY_REQUESTS`(429), `application.properties` 기본값(10 / 10m), `GroupService` 생성자 변경에 맞춘 테스트 2곳 수정. 스키마·B3 코드 변경 없음.
- 테스트: `AttemptLimiterTests`(한도 직전/초과·사용자 독립·창 만료·차단이 윈도 연장 안 함·기본값), `GroupServiceTests`(권한 순서, 현재 코드 건너뜀+충돌 재시도, INVITE_NOT_FOUND 만 카운트·429 차단), `GroupMapperTests`(코드 갱신·유니크), `InviteCodeControllerTests`(MockMvc+MariaDB: 재발급, 이전 코드 무효, 새 코드로 가입, 429, 사용자별 독립). 보고서: 185 테스트·실패 0.
- 문서: API_DESIGN(재발급 API·429·단일 인스턴스 가정), CONTRACT_DECISIONS §2-4 확정, 재발급 상태 갱신, WORK_PLAN, Postman 3개 요청.

## P0

없음.

## P1

없음.

## P2

### File / Location
작업 트리 전체(커밋 단위)

### Problem
작업 트리에 B2 와 무관한 변경이 섞여 있다: 미커밋 **B3**(auth/security/V1/문서/Postman `HAWE_ME`), B1·B3 계획 문서 이름 변경(`docs/해야될거/1번_찬희/B1-…`, `B3-…` 삭제 + `B1(완료)-…`, `B3(완료)-…` 신규), 미추적 `docs/템플릿.txt`. 보고서에 따르면 이름 변경·템플릿은 Codex 가 만든 것이 아니다. `docs/BACKEND/API_DESIGN.md`·`CONTRACT_DECISIONS.md`·`CONTRACT_WORK_PLAN.md` 는 B3 와 B2 변경이 같은 파일에 함께 들어 있다.

### Why It Matters
한 커밋에 B2·B3·문서 정리가 섞이면 이력 추적과 되돌리기가 어렵다. 코드 파일은 겹치지 않아 B3 를 먼저 커밋하면 쉽게 분리된다.

### Recommended Fix
B3 를 먼저 커밋(B3 코드·테스트·`HAWE_ME` Postman + 문서의 B3 hunk)한 뒤 B2 를 커밋. 문서 파일은 `git add -p` 로 B3/B2 hunk 를 나눈다. 계획 문서 이름 변경과 `템플릿.txt` 는 별도 커밋이거나 제외. 코드 수정은 필요 없다.

## 미검증(보고서에도 명시됨)

- 실서버 스모크 미실행(MockMvc 로 대체).
- 인메모리·단일 인스턴스 가정: 서버를 여러 대로 늘리면 인스턴스별 카운트 — 문서에 명시돼 있음. 사용자 단위 키라 계정을 여러 개 만들어 우회하는 것은 설계상 막지 않는다(계획의 결정사항).
- 앱(A3, 경환): 16 화면 재발급 버튼과 `inviteUrl` 사용 연결 필요.

## 결론

**승인 가능** — P0/P1 없음. P2 는 커밋 정리(코드 수정 불필요). B4 시작 전에 B2 를 커밋할 것(`GroupService`·`GroupMapper` 겹침).
