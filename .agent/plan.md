# B2. 초대 코드 재발급 + 코드 조회 시도 제한 (백엔드)

> 원본 계획: `docs/해야될거/1번_찬희/B2-invite-reissue-and-limits.md` 를 현재 코드와 대조해 구체화한 것. 이전 `.agent/plan.md`(B3)는 덮어쓴다.
> 범위: 백엔드(`src/`, `docs/BACKEND/`)만. 앱(16 화면 재발급 버튼 = A3)·알림·`ml/` 미수정. 스키마 변경 없음 → **DB 재생성 불필요**.
> **작업 트리 주의**: B1 은 커밋됨(`297aab9`). **B3(비밀번호 변경·토큰 버전) 변경은 아직 미커밋 상태로 작업 트리에 있다**(`auth/`, `global/security/`, V1 `users.token_version`, `docs/BACKEND/*`, Postman `HAWE_ME`). B3 변경을 되돌리거나 덮어쓰지 말 것. B2 는 B3 와 코드 파일이 겹치지 않고 문서만 겹치며, 문서는 섹션이 다르다(아래 Potential Merge Conflicts).

## Goal

1. `POST /api/groups/{groupId}/invite-code` — **그룹장만** 코드 재발급. 이전 코드는 즉시 무효. 응답 200 `{ "inviteCode": "M8R3TQ", "inviteUrl": "https://hawe.app/invite/M8R3TQ" }`.
2. `GET /api/invites/{code}`(미리보기)와 `POST /api/groups/join` 의 **무차별 대입 방지**: 사용자별로 "코드를 못 찾는 시도"가 짧은 시간에 많으면 429 `TOO_MANY_REQUESTS`.

## Findings

(모두 코드로 확인함)

- 코드: `groups.invite_code char(6) NOT NULL UNIQUE`. `group/service/InviteCodeGenerator`(`generate()` — 문자집합 `ABCDEFGHJKLMNPQRSTUVWXYZ23456789`·`SecureRandom`, `normalize(raw)` — trim·대문자·길이 6·문자집합 아니면 null).
- `GroupService.createGroup`(L108~): `InviteCodeGenerator.generate()` → `groupMapper.insert` 를 최대 5회 반복, `DuplicateKeyException` 이면 재시도(마지막 시도 `attempt == 4` 면 rethrow), 끝까지 실패하면 `IllegalStateException`. **재발급도 같은 방식**으로 구현.
- `GroupMapper`: `findByIdForUpdate(id)`, `findByInviteCode(code)`, `findByInviteCodeForUpdate(code)`, `update(Group)` — `update` 는 `name/owner_id/relation_type/daily_mission_count/start_date` 만 갱신하고 **`invite_code` 는 갱신하지 않는다**. 코드 갱신 쿼리 없음.
- `GroupService`: `requireGroup(groupId)`(404 `GROUP_NOT_FOUND`), `requireMember(groupId, userId)`(403 `NOT_GROUP_MEMBER`), `requireOwner(group, userId)`(`group.getOwnerId().equals(userId)` 아니면 403 `NOT_GROUP_OWNER`). `inviteUrl` 은 `toGroupResponse`(L349)에서 `inviteBaseUrl + "/" + group.getInviteCode()` 로 만든다(`hawe.invite.base-url`, 생성자에서 끝의 `/` 제거).
- `previewInvite(userId, rawCode)`(L208): `findInviteGroup(rawCode, false)` → `checkJoinable` → 멤버 미리보기. `join`(L217, `@Transactional`): `findInviteGroup(code, true)`(잠금) → `checkJoinable` → insert. `findInviteGroup`(L262): `normalize` null 또는 조회 결과 null 이면 `GroupErrorCode.INVITE_NOT_FOUND`(404). `checkJoinable` 은 `ALREADY_MEMBER`(409)·`GROUP_FULL`(409) — **코드는 맞은 경우**.
- `GroupController`(`/api/groups`): 재발급 엔드포인트 없음. `/join` 이 있으므로 새 경로 `/{groupId}/invite-code` 는 충돌 없음. `InviteController`(`GET /api/invites/{code}`).
- `GroupService` 생성자(8개 인자): `(GroupMapper, GroupMemberMapper, GroupMissionSlotMapper, MissionProperties, @Value inviteBaseUrl, MissionService, ApplicationEventPublisher, FileStorage)`. **직접 `new` 하는 테스트 2곳**: `GroupServiceTests`(L57), `GroupContractServiceTests`(L49).
- `CommonErrorCode` 에 429 없음(VALIDATION_ERROR, NOT_FOUND, METHOD_NOT_ALLOWED, FILE_TOO_LARGE, UNSUPPORTED_MEDIA_TYPE, INTERNAL_ERROR). 전역 예외 처리기는 `BusinessException` 을 `ErrorCode.httpStatus()` 로 응답.
- 메인 코드에 `java.time.Clock` 사용 없음. `SeoulTime` 은 정적 `now()` 만 있고 고정 시계 지원 없음 → 제한기 테스트용으로 `Clock` 주입이 필요.
- `application.properties` 에는 `hawe.invite.base-url` 만 있음. 그룹 통합 테스트는 `GroupProfileControllerTests` 한 곳이 `/api/invites/{code}` 를 호출(성공 케이스).
- 계약 문서에 이미 재발급 응답이 정의됨: `CONTRACT_DECISIONS.md` L227~231(`POST /api/groups/{groupId}/invite-code`, 200, `{inviteCode, inviteUrl}`, "이전 코드는 즉시 무효, 403 `NOT_GROUP_OWNER`"). 시도 제한은 §2-4 L271 에 "구현 시 검토" 로 미결.

## Root Cause / Key Implementation Points

1. **재발급** `GroupService.reissueInviteCode(Long userId, Long groupId)` `@Transactional`:
   `groupMapper.findByIdForUpdate(groupId)` null → 404 `GROUP_NOT_FOUND` → `requireMember(groupId, userId)`(403 `NOT_GROUP_MEMBER`) → `requireOwner(group, userId)`(403 `NOT_GROUP_OWNER`) → 최대 5회: `candidate = InviteCodeGenerator.generate()`(**현재 코드와 같으면 다시 생성**), `groupMapper.updateInviteCode(groupId, candidate)`; `DuplicateKeyException` 이면 재시도(5회째 실패는 rethrow 또는 `IllegalStateException`, `createGroup` 과 같은 방식). 성공 시 `new InviteCodeResponse(candidate, inviteBaseUrl + "/" + candidate)`. 이전 코드는 UPDATE 로 즉시 사라져 무효(별도 보관·유예 금지). 행 잠금(`FOR UPDATE`)이 동시 재발급과 `join`(`findByInviteCodeForUpdate`)을 직렬화한다.
2. **시도 제한기** `global/ratelimit/AttemptLimiter`(신규, `@Component`, 인메모리):
   - 키 = `userId`(Long). 값 = 실패 시각 deque. 규칙: **슬라이딩 윈도우 `failureWindow`(기본 10분) 안에 실패 `maxFailures`(기본 10)회가 쌓이면, 그 다음 시도(11번째)는 코드 조회 전에 429**.
   - API: `void check(Long userId)` — 만료된 실패를 제거하고 남은 수가 `>= maxFailures` 면 `BusinessException(CommonErrorCode.TOO_MANY_REQUESTS)`. `void recordFailure(Long userId)` — 현재 시각 추가. 성공은 기록도 초기화도 하지 않는다. 차단 중의 요청은 실패로 기록하지 않는다(차단이 연장되지 않음; 가장 오래된 실패가 창을 벗어나면 풀림). 차단 중에는 **유효한 코드로도 429**.
   - 만료 항목은 접근 시 정리(별도 스케줄러 금지). 빈 deque 는 맵에서 제거. 메모리가 무한히 늘지 않게 `recordFailure` 시 오래된 키를 가볍게 정리하는 정도면 충분(과설계 금지).
   - 스레드 안전: `ConcurrentHashMap` + 키별 동기화(예: `compute`/`synchronized`).
   - 설정: `@Value("${hawe.invite.max-failures:10}") int`, `@Value("${hawe.invite.failure-window:10m}") Duration`(Spring 이 `10m` 을 `Duration` 으로 변환). 시계: `java.time.Clock`. 생성자 2개 — `@Autowired` 를 단 `(int maxFailures, Duration window)`(내부에서 `Clock.systemUTC()`)와 테스트용 `(int, Duration, Clock)`. (Spring 은 생성자가 여러 개면 `@Autowired` 가 붙은 것을 사용.) `Clock` 빈을 새로 등록하지 않는다.
   - `application.properties` 에 `hawe.invite.max-failures=10`, `hawe.invite.failure-window=10m` 기본값 명시.
3. **적용 위치**: `GroupService.previewInvite` 와 `join` 둘이 같은 한도(같은 `userId` 키)를 공유. 구현은 `findInviteGroup` 를 감싼다: 호출 전 `limiter.check(userId)`; `findInviteGroup` 가 `GroupErrorCode.INVITE_NOT_FOUND` 를 던지면 `limiter.recordFailure(userId)` 후 그대로 rethrow. `findInviteGroup` 시그니처에 `userId` 를 추가하거나 호출부에서 try/catch — 둘 중 변경이 작은 쪽. `checkJoinable` 의 `ALREADY_MEMBER`/`GROUP_FULL` 은 **실패로 세지 않는다**(코드가 맞았으므로). 형식이 틀린 코드(`normalize == null`)는 실패로 센다. 인메모리 기록은 `join` 의 `@Transactional` 롤백과 무관하게 유지됨(의도된 동작).
4. **새 오류 코드**: `CommonErrorCode.TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, "요청이 너무 많습니다. 잠시 후 다시 시도해 주세요")`.
5. 단일 서버 인스턴스 가정(메모리 제한기). 서버를 여러 대로 늘리면 인스턴스별 카운트가 되므로 재검토 — 문서에 한 줄 명시.

## Files / Functions to Change

| 파일 | 변경 |
|---|---|
| `group/dao/GroupMapper.java` | `@Update("UPDATE `groups` SET invite_code = #{code} WHERE id = #{groupId}") int updateInviteCode(@Param("groupId") Long groupId, @Param("code") String code)` (테이블명 백틱 — 기존 쿼리 형식 따름) |
| `group/service/GroupService.java` | `reissueInviteCode` 추가, `previewInvite`/`join` 에 limiter 적용, 생성자 마지막에 `AttemptLimiter limiter` 추가 |
| `group/controller/GroupController.java` | `@PostMapping("/{groupId}/invite-code") InviteCodeResponse reissueInviteCode(@AuthenticationPrincipal Long userId, @PathVariable Long groupId)` → 200 (기본 상태) |
| `group/dto/InviteCodeResponse.java` (신규) | `record InviteCodeResponse(String inviteCode, String inviteUrl)` |
| `global/ratelimit/AttemptLimiter.java` (신규) | Key Point 2 |
| `global/exception/CommonErrorCode.java` | `TOO_MANY_REQUESTS` 추가 |
| `src/main/resources/application.properties` | `hawe.invite.max-failures`, `hawe.invite.failure-window` |
| 기존 테스트 | `GroupServiceTests`, `GroupContractServiceTests` 의 `new GroupService(...)` 에 `AttemptLimiter`(목 또는 실제) 인자 추가 |
| 문서 | `API_DESIGN.md` §2: L105 "코드 재발급은 2차 범위(미구현)이다" 문구 교체, 표에 `POST /groups/{groupId}/invite-code` 행 추가, 재발급 응답·오류(403 `NOT_GROUP_OWNER`, 404, 403 `NOT_GROUP_MEMBER`)·429 규칙 서술 · `CONTRACT_DECISIONS.md`: §2 헤더(L116 "코드 재발급…은 후속 단계")에서 재발급 제거, §2-2 재발급 항목에 "백엔드 반영됨", §2-4 L271 시도 제한 → **확정**(사용자별·10분 창·실패 10회·미리보기/가입 공유·인메모리·단일 인스턴스 가정·배포 확장 시 재검토) · `CONTRACT_WORK_PLAN.md` L13·L44 진행 표시 · Postman `HAWE_GROUP.postman_collection.json` 에 재발급 요청(+그룹장 아님 403) 추가 |

## Implementation Steps

1. `AttemptLimiter` + 단위 테스트, `CommonErrorCode.TOO_MANY_REQUESTS`, 프로퍼티.
2. `GroupMapper.updateInviteCode`, `InviteCodeResponse`, `GroupService.reissueInviteCode`, `GroupController` 엔드포인트.
3. `previewInvite`/`join` 에 limiter 연결, `GroupService` 생성자 변경에 맞춰 기존 테스트 2곳 수정.
4. 나머지 테스트 작성 → `.\mvnw.cmd clean test -q`.
5. 문서·Postman.

## Constraints

- 스키마 변경·새 마이그레이션 금지(DB 재생성 불필요). 코드 형식(대문자 6자, 문자집합) 유지. 이전 코드 보관·유예 금지(즉시 무효).
- Redis·외부 저장소·스케줄러 도입 금지(인메모리, 접근 시 정리). 키는 **사용자 단위만**(IP 단위 금지).
- 기존 JWT 초대 흔적(`createInvite` 등)은 이미 제거됨 — 되살리지 않는다.
- 범위 확장·불필요한 리팩터링 금지. 패키지 규칙(`global/ratelimit` 는 `global` 하위 새 패키지로 허용: `global/exception`, `security`, `storage`, `time` 와 같은 수준).
- 커밋/푸시 금지, `CLAUDE.md`·`AGENTS.md` 수정 금지. 시작 전 `git status` 로 B3 미커밋 변경이 그대로 있는지 확인하고 건드리지 않는다.

## Edge Cases

- 재발급 직후: 이전 코드로 미리보기/가입 → 404 `INVITE_NOT_FOUND`(그리고 실패 카운트 +1). 새 코드는 즉시 사용 가능. 재발급 응답의 `inviteUrl` 은 `inviteBaseUrl/새코드`, `GET /groups/{id}` 의 `inviteCode` 도 새 값.
- 권한/검사 순서: 없는 그룹 404 `GROUP_NOT_FOUND` → 비멤버 403 `NOT_GROUP_MEMBER` → 일반 멤버 403 `NOT_GROUP_OWNER` → 그룹장 200. 토큰 없음 401.
- 동시 재발급 2건 → 행 잠금으로 직렬화, 둘 다 200(서로 다른 코드), 마지막 코드만 유효. 재발급과 `join` 동시 → 잠금으로 직렬화(join 이 이전 코드를 먼저 잠그면 성공, 재발급 후에는 404).
- 새 코드가 현재 코드와 같게 나오면 다시 생성. 유니크 충돌은 재시도로 흡수(5회 모두 충돌하면 예외).
- 제한: 형식 오류(5자·문자집합 밖)도 실패로 카운트, 소문자·공백은 정규화되어 정상 처리(맞으면 실패 아님). `ALREADY_MEMBER`/`GROUP_FULL` 은 카운트 안 함. 실패 10회 후 11번째 요청은 429(유효 코드도). 창(10분)이 지나 가장 오래된 실패가 만료되면 다시 허용. 서로 다른 사용자는 독립. 429 응답은 실패로 기록하지 않음. 성공 요청은 카운트를 초기화하지 않는다.
- `maxFailures`/`window` 프로퍼티 미지정 시 기본값(10 / 10분)으로 기동.

## Regression Risks

- `GroupService` 생성자 변경 → `new GroupService(...)` 하는 테스트 2곳 컴파일 오류(위 목록). 빈 주입 경로(`@SpringBootTest`)는 영향 없음.
- `AttemptLimiter` 는 싱글턴이라 통합 테스트들 사이에 상태가 공유된다. 키가 `userId`(AUTO_INCREMENT, 재사용 없음)라 테스트 간 간섭은 없어야 하나, 한 테스트가 한 사용자로 못 찾는 코드를 10회 넘게 조회하는 경우 429 가 나므로 기존 테스트(`GroupProfileControllerTests`, `GroupContractServiceTests` 등)가 그런 호출을 하지 않는지 확인.
- 두 `AttemptLimiter` 생성자 때문에 Spring 이 잘못된 생성자를 고르지 않는지(`@Autowired` 지정) — 컨텍스트 로딩(`@SpringBootTest`)으로 확인.
- `previewInvite` 는 `@Transactional` 이 아니므로 limiter 호출 위치가 트랜잭션 경계와 무관함을 확인. `join` 의 롤백은 limiter 기록을 되돌리지 않음(의도).
- Postman `HAWE_GROUP` 시나리오(`2-7 Invalid preview code` 등)는 실패 1~2회라 한도에 걸리지 않음.

## Tests Required

- `AttemptLimiterTests`(단위, 고정/조작 가능한 `Clock`): 한도 직전(10회 실패 후 check 는 429, 9회 실패 후는 통과), 창 만료 후 해제, 사용자 독립, 차단 중 `recordFailure` 가 창을 연장하지 않음, 기본 생성자 값(10/10분).
- `GroupServiceTests`/`GroupContractServiceTests`(Mockito): 재발급 — 그룹장 성공(응답 코드·URL), 일반 멤버 `NOT_GROUP_OWNER`, 비멤버 `NOT_GROUP_MEMBER`, 없는 그룹 `GROUP_NOT_FOUND`, 코드 충돌 시 재시도(첫 `updateInviteCode` 가 `DuplicateKeyException`), 현재 코드와 같은 값이 나오면 재생성; `previewInvite`/`join` — `INVITE_NOT_FOUND` 만 `recordFailure`, `ALREADY_MEMBER`/`GROUP_FULL` 은 호출 안 함, 차단되면 조회 없이 429(`TOO_MANY_REQUESTS`).
- `GroupMapperTests`(실 MariaDB): `updateInviteCode` 후 이전 코드 `findByInviteCode` null, 새 코드 조회 성공, 유니크 위반 시 `DuplicateKeyException`.
- MockMvc 통합(실 MariaDB, `GroupProfileControllerTests` 패턴, 데이터 INSERT 후 finally 정리): 재발급 200/403(일반 멤버)/403(비멤버)/404/401, 이전 코드로 `GET /invites/{code}` 404, 새 코드로 다른 사용자 가입 200, 같은 사용자가 존재하지 않는 코드로 10회 404 후 11번째 429 `TOO_MANY_REQUESTS`(유효 코드로도 429), 다른 사용자는 영향 없음.
- 기존 테스트 전체 통과.

## Validation Steps

1. `.\mvnw.cmd clean test -q` 통과 — 총 테스트 수·실패 0 기록(직전 B3 기준 177+).
2. `rg "createInvite|InviteResponse|createInviteToken"` 로 폐지된 JWT 초대 흔적이 없는지 확인(없어야 정상). `rg "TOO_MANY_REQUESTS|AttemptLimiter"` 로 사용처가 `GroupService` 와 테스트뿐인지 확인.
3. Postman JSON 파싱(`python -c "import json;json.load(open(path,encoding='utf-8'))"`) + 한글 확인.
4. `git diff --check`.
5. 가능하면 서버 스모크(재발급 → 이전 코드 404 → 새 코드 가입, 11회 실패 시 429), 못 했으면 "미검증" 명시.
6. 보고서(`.agent/report.md`)에: 인메모리·단일 인스턴스 가정, 한도 값(10회/10분), 실패로 세는/안 세는 경우, 범위 밖(앱 16 화면 재발급 버튼 = A3), 미검증 항목, B3 미커밋 변경을 건드리지 않았다는 점 기재.

## Potential Merge Conflicts

- `group/service/GroupService.java`, `GroupController.java`, `GroupMapper.java`: B1(커밋 완료)과 같은 파일 — 이미 반영돼 있으므로 B1 코드 위에서 수정. B4(파일 정리)·우준 B7(그룹 목록 `todayMissions`, 멤버 지표)도 `GroupService`/`GroupMapper` 를 고치므로 **B4 시작 전에 B2 커밋**, B7 과는 서로 알리고 순차 진행.
- `global/exception/CommonErrorCode.java`, `application.properties`: 다른 계획과 겹침 적음.
- `docs/BACKEND/API_DESIGN.md`·`CONTRACT_DECISIONS.md`·`CONTRACT_WORK_PLAN.md`: **미커밋 B3 변경과 같은 파일**이지만 수정 위치가 다르다(B3: Me/Auth 절, B2: Group 절 §2). B3 가 커밋되기 전이라면 B3 변경 위에 얹혀 편집한다. `HAWE_GROUP.postman_collection.json` 은 B1 이 수정했던 파일.
- 앱 A3(경환): 16 화면 재발급 버튼과 새 `inviteUrl` 사용 — 이번 범위 밖, 끝나면 알림.
