# 계약 반영 2차 / 5단계(백엔드): 콕 찌르기 · 똥 뿌리기 (Pokes)

> 범위: **백엔드만** (`src/`, `docs/BACKEND/`). Android/iOS 는 5A 에서 별도로 한다 — 이번에는 건드리지 않는다. 알림(FCM·notifications 행) 발송은 9단계 몫이므로 **이번에는 만들지 않는다**. 결정 근거: `docs/BACKEND/CONTRACT_DECISIONS.md` §4 (사용자 결정 완료).

## Goal

같은 그룹 멤버가 **현재 미션에 아직 제출하지 않은** 멤버에게 `poke`(콕) / `fling`(똥)을 보낼 수 있게 한다. 규칙: **(보낸 사람, 받는 사람, 미션, 종류) 당 1회** — 같은 종류 두 번째는 409 `ALREADY_POKED`. 현재 미션 응답의 `members[]` 에 내가 그 멤버에게 보낸 종류 목록 `pokedByMe` 를 내려 앱이 "보냈어요" 상태를 그린다.

## Findings

이미 있는 것 (`com.sapphire.hawe.poke.*`):
- `PokeController` : `POST /api/groups/{groupId}/pokes` (201, `@AuthenticationPrincipal Long userId`).
- `PokeRequest(@NotNull Long toUserId, @NotBlank @Pattern("poke|fling") String type)` — 이미 `poke|fling` 소문자만 허용.
- `PokeResponse(id, toUserId, type, sentAt)` — **`missionId` 없음**.
- `PokeService.sendPoke` : 그룹 404 → 멤버 403 → 자기 자신 400 → 대상 멤버 404 → `existsCurrentMissionSubmission` 이면 409 `TARGET_ALREADY_SUBMITTED` → insert. 횟수 제한·현재 미션 개념 없음.
- `PokeMapper` : `insert(Poke)`, `existsCurrentMissionSubmission(groupId, userId)` (슬롯별 최신 미션 중 어느 것이든 제출했으면 true — 5단계 요구와 다름).
- `Poke` 도메인(id, fromUserId, toUserId, groupId, type, sentAt:LocalDateTime), `PokeErrorCode`(CANNOT_POKE_SELF 400, TARGET_NOT_GROUP_MEMBER 404, TARGET_ALREADY_SUBMITTED 409).
- 테스트: `poke/service/PokeServiceTests`(Mockito, 생성자 `new PokeService(pokeMapper, groupMapper, groupMemberMapper)`), `poke/dao/PokeMapperTests`(실 MariaDB, `@SpringBootTest @Transactional`, JdbcTemplate).

DB (`src/main/resources/db/migration/V1__initial_schema.sql`, 153행 근처): `pokes(id, from_user_id, to_user_id, group_id, type varchar(20), sent_at)` — `mission_id` 없음, 유니크 키 없음. **프로젝트 결정: 개발 DB 를 지우고 Flyway 를 V1 부터 다시 쓴다(V1 은 "최종 스키마")**. 따라서 새 마이그레이션이 아니라 **V1 의 `pokes` 정의를 직접 수정**한다 (`V2__seed_characters.sql` 은 그대로).

미션 쪽 (`com.sapphire.hawe.mission.*`):
- `MissionMapper.CURRENT` = "슬롯별 최신 미션" 조건 상수. `isCurrent(missionId)`, `findCurrentByGroupId`, `findCurrentSummariesByGroupIds`(그룹 목록 `todayMission` 용; 가장 작은 `slot_no` 의 현재 미션만 — `AND m.slot_no = (SELECT MIN(slot_no) FROM missions c WHERE c.group_id = m.group_id)`).
- `MissionMapper.findMemberStatuses(missionId)` → `List<MissionMemberRow{userId, submitted}>`, `MissionService.toResponse` 가 `MissionMemberStatus(userId, submitted)` 로 변환. `toResponse(row, publishTimes)` 는 `getTodayMissions(userId, groupId)` 와 `getMission(userId, missionId)` 에서 호출된다 — 둘 다 `userId`(요청자)를 이미 가지고 있다.
- `MissionResponse(... boolean submittedByMe, List<MissionMemberStatus> members)` — `MissionMemberStatus(Long userId, boolean submitted)`.
- 테스트: `MissionServiceTests`(52행 생성자, 137행 `when(missions.findMemberStatuses(10L))`), `MissionMapperTests`(51-52행 `findMemberStatuses(firstMissionId)`).

그룹 삭제 (`GroupService.deleteGroupCascade`, 약 285행): 현재 순서가 `... deleteSubmissionsByGroupId → deleteMissionsByGroupId → deletePokesByGroupId → slots → members → group`. `pokes.mission_id` FK 를 추가하면 **pokes 를 missions 보다 먼저 지워야** 한다. 이 순서를 검증하는 테스트가 2개 있다: `GroupContractServiceTests` 약 212행(`InOrder`), `GroupContractMapperTests` 82행(`INSERT INTO pokes (from_user_id, to_user_id, group_id, type) VALUES (..., 'POKE')` — mission_id 없이 삽입, 타입도 대문자).

앱(참고): Android `ApiClient.json` 은 `ignoreUnknownKeys = true` 라서 `members[].pokedByMe` 가 추가돼도 기존 앱이 깨지지 않는다.

## Root Cause / Key Implementation Points

1. **스키마**: `pokes.mission_id`(NOT NULL, FK → missions.id) 추가, 유니크 키 `uk_pokes_from_to_mission_type (from_user_id, to_user_id, mission_id, type)` 추가.
2. **"현재 미션" 의 정의**(콕 전용): 그 그룹의 **가장 작은 `slot_no` 의 현재 미션**(`CURRENT` 조건 + `MIN(slot_no)`; 홈 카드 `findCurrentSummariesByGroupIds` 와 동일 기준). MVP 는 슬롯 1개이므로 그 미션. 없으면 409 `NO_OPEN_MISSION`.
3. **대상 제출 여부는 그 미션 기준**: `EXISTS(SELECT 1 FROM submissions WHERE mission_id=? AND user_id=?)`. 기존 `existsCurrentMissionSubmission` 은 제거하고 이것으로 교체.
4. **횟수 제한**: 서비스에서 `existsPoke` 사전 검사 → 409 `ALREADY_POKED`, 그리고 동시 요청 대비 **`DuplicateKeyException` 을 같은 `ALREADY_POKED` 로 변환**(6단계 `SubmissionService` 와 같은 패턴).
5. **`pokedByMe`**: `members[]` 각 항목에 요청자가 그 미션에서 그 멤버에게 보낸 종류 배열(`"poke"`, `"fling"`, 없으면 `[]`, 순서는 `poke` → `fling` 고정). 쿼리 1회(`mission_id` + `from_user_id`)로 가져와 서비스에서 멤버별로 묶는다 — 멤버마다 쿼리 금지.
6. **알림 훅 없음**: `notifications` 삽입·FCM 호출을 만들지 않는다. 보낸 기록(`pokes` 행)만 남긴다. 9단계가 `PokeService.sendPoke` 성공 직후에 연결한다 — 이번에 빈 인터페이스/이벤트도 만들지 않는다.

## Files / Functions to Change

| 파일 | 변경 |
|---|---|
| `src/main/resources/db/migration/V1__initial_schema.sql` | `pokes` 에 `mission_id bigint(20) NOT NULL`, `KEY fk_pokes_mission`, `CONSTRAINT fk_pokes_mission FOREIGN KEY (mission_id) REFERENCES missions (id)`, `UNIQUE KEY uk_pokes_from_to_mission_type (from_user_id, to_user_id, mission_id, type)`. `pokes` 정의가 `missions` 정의보다 **뒤**에 있는지 확인(현재 153행 vs missions 65행 → OK) |
| `poke/domain/Poke.java` | `Long missionId` + getter/setter |
| `poke/dto/PokeResponse.java` | `(Long id, Long toUserId, String type, Long missionId, OffsetDateTime sentAt)` — 필드 순서는 계약 JSON 순서 |
| `poke/service/PokeErrorCode.java` | `ALREADY_POKED(CONFLICT, "이미 보낸 재촉입니다")`, `NO_OPEN_MISSION(CONFLICT, "지금 열려 있는 미션이 없습니다")` 추가. `TARGET_ALREADY_SUBMITTED` 메시지의 "오늘" → "현재 미션" 으로 다듬어도 됨(코드 불변) |
| `poke/dao/PokeMapper.java` | `insert` 에 `mission_id` 추가; `existsCurrentMissionSubmission` 삭제 → `Long findCurrentMissionId(groupId)`(CURRENT+MIN slot; 없으면 null), `boolean existsSubmission(missionId, userId)`, `boolean existsPoke(fromUserId, toUserId, missionId, type)`, `List<PokedRow> findSentTypes(missionId, fromUserId)` |
| `poke/dao/PokedRow.java` (신규) | `toUserId`, `type` — `MissionMemberRow` 와 같은 getter/setter 스타일. 컬럼 매핑(`map-underscore-to-camel-case`)이 켜져 있는지 `application.properties` 로 확인 |
| `poke/service/PokeService.java` | 검사 순서(아래), `DuplicateKeyException` → `ALREADY_POKED`, 응답에 `missionId` |
| `mission/dto/MissionMemberStatus.java` | `(Long userId, boolean submitted, List<String> pokedByMe)` |
| `mission/service/MissionService.java` | `toResponse` 에 `Long viewerId` 전달(`getTodayMissions`, `getMission` 호출부 수정). 같은 미션의 pokes 를 `PokeMapper.findSentTypes(row.getId(), viewerId)` 로 한 번 읽어 멤버별 `pokedByMe` 구성. 생성자에 `PokeMapper` 추가 |
| `group/service/GroupService.java` | `deleteGroupCascade` 에서 `deletePokesByGroupId` 를 **`deleteMissionsByGroupId` 앞으로** 이동 |
| 테스트 | `PokeServiceTests`, `PokeMapperTests`, `MissionServiceTests`(생성자·`pokedByMe`), `GroupContractServiceTests`(InOrder), `GroupContractMapperTests`(pokes 삽입에 mission_id·소문자 타입), 신규 `PokeControllerTests`(MockMvc; 기존 컨트롤러 테스트 스타일을 따를 것) |
| 문서 | `docs/BACKEND/API_DESIGN.md` §5 Poke(횟수 제한·`missionId`·오류 코드), `docs/BACKEND/BACKEND_DB.md` pokes(mission_id, 유니크), `docs/BACKEND/MISSIONS_API.md` 현재 미션 응답 `members[].pokedByMe`(115·136행 근처), `docs/BACKEND/CONTRACT_DECISIONS.md` §4 상태를 "반영됨 (백엔드; 알림 발송은 9단계, 앱은 5A)"로, `docs/BACKEND/CONTRACT_WORK_PLAN.md` 5단계 비고 한 줄, 신규 `docs/BACKEND/postman/HAWE_POKE.postman_collection.json`(UTF-8, 기존 컬렉션 형식, 한글 깨짐 없음) |

## Implementation Steps

1. **스키마**: V1 `pokes` 수정(위 표). 기존 로컬 DB 는 Flyway checksum 불일치가 나므로 **테스트 전에 DB 를 재생성**한다: `DROP DATABASE hawe; CREATE DATABASE hawe CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;` (테스트가 쓰는 DB 이름은 `application.properties`/`.env` 로 확인; 사용자 데이터가 있을 수 있는 운영성 DB 가 아닌 로컬 개발 DB 만). 결과를 보고서에 적는다. `flyway clean/repair` 설정을 코드에 넣지 말 것.
2. `Poke`·`PokeResponse`·`PokeErrorCode`·`PokedRow` 수정/추가.
3. `PokeMapper` 쿼리 작성.
   - `findCurrentMissionId`: `SELECT m.id FROM missions m WHERE m.group_id = #{groupId} AND ` + `MissionMapper.CURRENT` + `AND m.slot_no = (SELECT MIN(slot_no) FROM missions c WHERE c.group_id = m.group_id) LIMIT 1`. 상수 `MissionMapper.CURRENT` 가 인터페이스 필드(암묵 public)이므로 복사하지 말고 참조한다. `<script>` 래핑이 없는 쿼리이므로 `<`/`&lt;` 이스케이프에 주의(CURRENT 에는 부등호가 없다).
   - `existsSubmission`: `SELECT EXISTS (SELECT 1 FROM submissions WHERE mission_id = #{missionId} AND user_id = #{userId})`.
   - `existsPoke`, `findSentTypes`(`SELECT to_user_id, type FROM pokes WHERE mission_id = #{missionId} AND from_user_id = #{fromUserId}`), `insert`(mission_id 포함).
4. `PokeService.sendPoke` 검사 순서(앞이 우선):
   1) 그룹 없음 → 404 `GROUP_NOT_FOUND` 2) 비멤버 → 403 `NOT_GROUP_MEMBER` 3) 자기 자신 → 400 `CANNOT_POKE_SELF` 4) 대상 비멤버 → 404 `TARGET_NOT_GROUP_MEMBER` 5) `findCurrentMissionId` null → 409 `NO_OPEN_MISSION` 6) 대상이 그 미션 제출 → 409 `TARGET_ALREADY_SUBMITTED` 7) `existsPoke` → 409 `ALREADY_POKED` 8) insert (`DuplicateKeyException` → 409 `ALREADY_POKED`).
   - `type` 검증은 DTO(`@Pattern`)에서 400 `VALIDATION_ERROR` 로 이미 처리된다 — 서비스에서 중복 검증하지 않는다.
   - `sentAt` 은 기존대로 `SeoulTime.now().withNano(0)`.
5. `MissionMemberStatus` 확장 → `MissionService.toResponse(row, publishTimes, viewerId)`. `pokedByMe` 정렬: `poke`, `fling` 순.
6. `GroupService.deleteGroupCascade` 순서 수정.
7. 테스트 갱신·추가, 문서·Postman 갱신.
8. `mvnw.cmd clean test` 전체 통과 확인.

## Constraints

- 변경 범위는 백엔드(`src/main`, `src/test`)와 `docs/BACKEND/` 뿐. `android/`, `ios/`, `frontend/` 수정 금지.
- 새 Flyway 파일을 만들지 않는다(V1 직접 수정이 프로젝트 결정). V2 시드는 수정하지 않는다.
- 알림/FCM/`notifications` 삽입·이벤트 인터페이스 금지(9단계). 스케줄러·`MissionPublisher` 변경 금지.
- 패키지 규칙: `poke` 는 기존 5개 하위 패키지(controller/service/dto/domain/dao)만 사용. `PokedRow` 는 `poke/dao`.
- 응답 JSON 필드명은 계약 그대로: `PokeResponse{id,toUserId,type,missionId,sentAt}`, `members[].pokedByMe`. `sentAt` 은 `+09:00` 오프셋.
- 기존 미션 API 의 다른 필드는 바꾸지 않는다(`members[].userId`, `submitted` 유지).
- 에러 코드 문자열은 enum `name()` 그대로(`ALREADY_POKED`, `NO_OPEN_MISSION`).
- 그룹 목록(`todayMission`)에는 콕 정보를 넣지 않는다.

## Edge Cases

- 현재 미션이 없는 그룹(첫 미션 발행 전) → 409 `NO_OPEN_MISSION`. 단 `TARGET_NOT_GROUP_MEMBER` 검사가 그보다 먼저.
- 대상이 이전 미션에는 제출했지만 **현재 미션에는 미제출** → 콕 가능. 반대로 현재 미션 제출 → `TARGET_ALREADY_SUBMITTED`.
- 콕과 똥은 **독립 1회씩**: 같은 대상에게 poke 후 fling 은 성공, 같은 종류 재전송은 409 `ALREADY_POKED`. 새 미션이 발행되면(현재 미션 id 가 바뀌면) 다시 보낼 수 있다.
- 보낸 사람이 아직 제출하지 않았어도 보낼 수 있다(결정 문서에 제한 없음 — 추가 제한 금지).
- 동시에 같은 요청 2건 → 하나는 201, 다른 하나는 `DuplicateKeyException` → 409 `ALREADY_POKED`(500 금지).
- 대상이 직후 제출하는 경쟁은 허용(요청 시점 기준).
- 멤버가 그룹을 나간 뒤에도 과거 `pokes` 행은 남는다. 이번에 탈퇴/나가기 시 pokes 정리 로직을 추가하지 않는다.
- `toUserId` 누락·`type` 오타/대문자(`POKE`) → 400 `VALIDATION_ERROR`.
- 다른 사람이 보낸 poke 는 요청자의 `pokedByMe` 에 나타나면 안 된다(`from_user_id = viewer`). `members[]` 안 요청자 본인 항목은 항상 `[]`.
- `GET /api/missions/{id}`(이전 미션 조회)에서도 `pokedByMe` 는 그 미션 id 기준으로 나온다.

## Regression Risks

- `deleteGroupCascade` 순서 변경 — FK(`pokes.mission_id → missions`) 위반을 **방지**하기 위한 것. InOrder 테스트와 실 DB 그룹 삭제 테스트(`GroupContractMapperTests`)를 반드시 갱신·통과.
- `MissionService` 생성자 시그니처 변경 → `MissionServiceTests` 등 직접 생성하는 모든 테스트 컴파일 확인.
- `MissionMemberStatus` 시그니처 변경 → 사용처 전수 확인(`rg "MissionMemberStatus"`).
- V1 수정으로 기존 개발/테스트 DB 의 Flyway checksum 불일치 → Step 1 의 DB 재생성 필수. 빈 DB 에서 시작하는 환경은 문제 없음.
- 제출 API(6단계)·스케줄러에는 영향 없어야 한다.

## Tests Required

실 MariaDB 를 쓰는 기존 방식(`@SpringBootTest`+`JdbcTemplate`, 필요 시 MockMvc).

- `PokeMapperTests`: `insert` 가 `mission_id` 저장; 같은 `(from,to,mission,type)` 두 번째 insert → `DuplicateKeyException`; 종류가 다르면 허용; `findCurrentMissionId` — 슬롯 1개 최신 미션 / 같은 슬롯에 새 미션 발행 후 최신 id / 미션 없음 null / 다른 그룹 무시; `existsSubmission` 미션별 구분(이전 미션 제출은 현재 미션에서 false); `findSentTypes` 가 보낸 사람·미션으로만 필터. 기존 `todaySubmissionIsChecked...` 테스트는 새 쿼리 기준으로 교체.
- `PokeServiceTests`(Mockito): 정상 전송(응답 `missionId`), 그룹 404, 비멤버 403, 자기 자신 400, 대상 비멤버 404, `NO_OPEN_MISSION`, `TARGET_ALREADY_SUBMITTED`, `existsPoke` → `ALREADY_POKED`, insert 가 `DuplicateKeyException` 이면 `ALREADY_POKED`, 앞 오류일 때 뒤 쿼리·insert 미호출.
- `MissionServiceTests`: `pokedByMe` 매핑 — 멤버별 종류 목록, 순서 `[poke, fling]`, 없으면 `[]`, 쿼리 인자 `(missionId, viewerId)`; 기존 `members` 단언 갱신.
- 신규 `PokeControllerTests`(MockMvc 통합): 인증 없음 401; 정상 201 본문(`id,toUserId,type,missionId,sentAt` `+09:00`); 같은 종류 재전송 409 `ALREADY_POKED`; 다른 종류 201; 대상 제출 후 409 `TARGET_ALREADY_SUBMITTED`; `type:"POKE"` 400 `VALIDATION_ERROR`; 이후 현재 미션 조회(`MissionController` 의 실제 경로 확인)에서 `members[].pokedByMe == ["poke"]`.
- `GroupContractServiceTests`: InOrder 에서 `deletePokesByGroupId` 가 `deleteMissionsByGroupId` **앞**. `GroupContractMapperTests`: pokes 를 `mission_id` 와 소문자 `type` 으로 삽입하고 그룹 삭제 후 0건·FK 위반 없음.

## Validation Steps

1. 로컬 DB 재생성(Step 1) 후 `mvnw.cmd clean test` 전체 통과(기존 103개 + 신규). 개수를 보고서에 기록.
2. 가능하면 서버를 띄워 수동 스모크: 가입 2명 → 그룹 생성/참여 → 현재 미션 조회에서 `pokedByMe == []` → A 가 B 에게 poke 201 → 같은 poke 409 → fling 201 → 조회 시 `["poke","fling"]` → B 제출 후 A 가 다시 poke 시 `TARGET_ALREADY_SUBMITTED`. 못 띄우면 보고서에 "미검증" 명시.
3. `rg "existsCurrentMissionSubmission"` 잔여 사용처 없음 확인.
4. Postman 컬렉션 JSON 유효성(`ConvertFrom-Json`)과 한글 깨짐 grep.

## Potential Merge Conflicts

- 앱(Android/iOS) 작업과는 겹치지 않는다(이번엔 앱 수정 없음). 5A 는 이 계약을 소비하므로 백엔드 커밋 후 시작.
- 같은 백엔드 파일을 건드릴 수 있는 이후 단계: `MissionService`/`MissionMemberStatus`, `GroupService.deleteGroupCascade`, `V1__initial_schema.sql`(7단계 반응·댓글 등). 5단계를 먼저 커밋한 뒤 시작할 것.
- 작업 트리에 iOS/기타 미커밋·병합 중 변경이 있을 수 있다(다른 터미널 작업). 이번 작업은 `src/`, `docs/BACKEND/` 만 수정하므로 그 변경을 건드리지 말 것.
- `CONTRACT_DECISIONS.md` §4, `MISSIONS_API.md` 는 다른 단계 문서와 같은 파일이므로 해당 절만 수정.
