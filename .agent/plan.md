# Plan — 계약 반영 1차 / 4단계(백엔드): 미션 생성(임시 생성기) · 현재 미션 · 마감/종료 규칙

> 범위: **백엔드만** (`src/`, `docs/BACKEND/**`, 일부 `docs/FEATURE/PROJECT_OVERVIEW.md`). Android 연결(4A)은 이 단계 이후 별도 계획. `android/`, `ios/`, `frontend/` 는 건드리지 않는다. AI 연동은 하지 않는다(고정 문장 임시 생성기).

## Goal

`docs/BACKEND/CONTRACT_DECISIONS.md` §3 의 1차 범위를 서버에 구현한다. 지금 서버에는 **미션을 만드는 코드가 전혀 없다**(조회만 있음).

1. **미션 자동 생성**: 그룹·슬롯별 발행 시각에 미션이 열린다 (분 단위 스케줄러). 중복 없이(멱등), 서버 재시작/다운 후에도 오늘 것은 따라잡는다.
2. **MVP 틀 8종** 안에서 서버가 틀을 정한다 (결정적 규칙). 내용은 `MissionGenerator` 인터페이스 뒤의 **고정 문장 임시 생성기**(`PlaceholderMissionGenerator`)가 만든다. 나중에 AI 구현체로 교체.
3. **첫 미션 즉시 발행**: 그룹을 만들 때 오늘 발행 시각이 이미 지났으면 바로 연다 (커밋 이후, 실패해도 그룹 생성은 유지).
4. **시간 규칙**: `deadlineAt`(발행 후 처음 오는 마감 시각), `closesAt`(다음 발행 예정 시각), "오늘"이 아니라 **"현재 미션"**(슬롯별 가장 최근 발행) 기준.
5. **응답 확장**: 미션에 `number`, `closesAt`, `members[{userId, submitted}]`; `content` 의 `[대상]` 을 닉네임으로 치환; 그룹 목록에 `todayMission` 요약.
6. `CURDATE()` 로 "오늘"을 판단하던 쿼리를 현재 미션 기준으로 교체 (미션 조회, 그룹 목록 `submittedToday`, 멤버 `stampedToday`, 콕의 "이미 제출함").
7. `hawe.mission.max-daily-count=1` (MVP 하루 1개를 설정값으로 강제).

**이번에 하지 않는 것**: AI 호출, 하루 여러 개 UI, `MISSION_CLOSED` 적용(제출 구현인 6단계에서), 알림 발송(9단계), `pokedByMe`(5단계), 스키마 변경(없음 — DB 초기화 불필요).

## Findings

(전부 코드/문서로 확인함. 패키지 루트 `src/main/java/com/sapphire/hawe/`)

**작업 트리**: 1·2·3단계 백엔드와 Android 2A/3A 변경이 **미커밋으로 섞여 있을 수 있다.** 되돌리거나 덮어쓰지 말고 그 위에서 작업한다. 특히 3단계가 만든 `GroupService`, `GroupMapper`, `GroupSummaryResponse`, `GroupMapperTests` 등을 이 단계가 다시 수정한다(기존 변경 보존).

**현재 미션 코드** (`mission/`)
- `controller/MissionController`: `GET /api/groups/{groupId}/missions/today`, `GET /api/missions/{missionId}`, `GET /api/mission-templates`.
- `service/MissionService`: `getTodayMissions`(→ `missionMapper.findTodayByGroupId` + `LocalDate`/`nextPublishAt(date, count, rows, slots)` 로 "오늘 아직 발행 안 된 슬롯의 가장 이른 시각"), `getMission`, `listTemplates`, `toResponse`(미션 행 → `MissionResponse`; `params` JSON 파싱 후 `targetUserId` → `params.target{userId,nickname}`), `requireMember`. 생성자: `MissionMapper, GroupMapper, GroupMemberMapper, GroupMissionSlotMapper, UserMapper, MissionProperties, ObjectMapper`(`tools.jackson.databind.ObjectMapper`, Jackson 3).
- `dao/MissionMapper`: `findTodayByGroupId`(**`m.mission_date = CURDATE()`**), `findById`. `MissionRow{id, groupId, content, category, template, params(JSON 문자열), slotNo, publishedAt, deadlineAt(LocalDateTime), submittedByMe}`. **insert 없음.**
- `domain/MissionTemplate` enum 15종 (`media()`: PHOTO/VOICE/TEXT/DRAW/SLIDER, `paramsSchema()`, `payloadSchema()`, `displayName()`, `phase()`). 파라미터 스키마: `PHOTO_NOW{subject}`, `PHOTO_GALLERY{condition}`, `PHOTO_COLOR{color}`, `VOICE_LINE{sentence,tone}`, `TEXT_ACROSTIC{word(3)}`, `TEXT_FILL_BLANK{sentence(한 개의 ___), targetUserId(서버 부여)}`, `DRAW_SKETCH{subject(선택), targetUserId(선택), seconds}`, `DRAW_A_TO_B{a(선택), targetUserId(선택), b}`.
- DTO: `MissionResponse(Long missionId, Long groupId, int slotNo, String content, String category, String template, String media, Map params, OffsetDateTime publishedAt, OffsetDateTime deadlineAt, boolean submittedByMe)`, `TodayMissionsResponse(LocalDate date, int dailyMissionCount, OffsetDateTime nextPublishAt, List<MissionResponse> missions)` (1단계에서 `OffsetDateTime` 으로 변경됨; 변환은 `SeoulTime.toOffset(LocalDateTime)`).
- 오류 코드: `MissionErrorCode.MISSION_NOT_FOUND` 하나.
- `global/config/MissionProperties(LocalTime defaultPublishTime, LocalTime defaultDeadlineTime, int maxDailyCount)` (`hawe.mission.*`), 테스트 4곳이 `new MissionProperties(LocalTime.of(9,0), LocalTime.of(23,0), 5)` 로 직접 생성(`GroupServiceTests`, `MissionServiceTests`, `MissionTemplateTests`, 3단계의 `GroupContractServiceTests` 등) → **필드를 추가하지 않는다.**
- 스케줄러 없음(`@EnableScheduling` 없음). `application.properties`: `hawe.mission.default-publish-time=09:00`, `default-deadline-time=23:00`, `max-daily-count=5`.

**스키마(변경 없음)**: `missions(id, group_id, content, category, mission_date, template, params JSON, slot_no, published_at, deadline_at)` + `UNIQUE (group_id, mission_date, slot_no)`, `group_mission_slots(group_id, slot_no, publish_time, deadline_time)` (NULL 이면 기본값), `groups(... daily_mission_count, created_at, ...)`, `submissions(mission_id, user_id, ...)`.

**`CURDATE()` 쿼리** (현재 미션 기준으로 바꿔야 함): `MissionMapper.findTodayByGroupId`, `GroupMapper.findSummariesByUserId` 의 `submitted_today`, `GroupMemberMapper.findMembersByGroupId` 의 `stamped_today`, `PokeMapper.existsTodaySubmission`. (`submission_count_this_month` 의 `CURDATE()` 월 기준은 이번 단계에서 **유지**.)

**규칙 출처**: `CONTRACT_DECISIONS.md` §3(결정·시간 규칙 §3-2·계약 §3-3), `docs/BACKEND/MISSIONS_API.md` §6(생성), §6-1(틀 선택 규칙).

## Root Cause / Key Implementation Points

1. **시간 규칙은 순수 함수 `MissionTiming` 으로** (`mission/domain/MissionTiming.java`, static 메서드, Seoul `LocalDateTime` 입력):
   - `scheduledPublishAt(LocalDate date, LocalTime publishTime)`.
   - `deadlineAfter(LocalDateTime publishedAt, LocalTime deadlineTime)`: `publishedAt` **이후 처음 오는** `deadlineTime`(같은 순간이면 다음 날). 예: 발행 09:00/마감 23:00 → 당일 23:00; 발행 23:30/마감 23:00 → 다음 날 23:00; 발행 14:00 → 당일 23:00.
   - `nextPublishAfter(LocalDateTime after, LocalTime publishTime)`: `after` 이후 처음 오는 발행 시각(같은 순간이면 다음 날). `closesAt` 과 "다음 미션까지" 에 사용.
2. **발행은 멱등한 하나의 함수** `MissionPublisher.publishIfDue(Long groupId, LocalDateTime now)` (`@Transactional(propagation = REQUIRES_NEW)` 로 그룹별 독립 트랜잭션; `mission/service/`). 각 슬롯 `1..group.dailyMissionCount` 에 대해, `date = now.toLocalDate()`, `p` = 슬롯 발행 시각(없으면 기본), `d` = 슬롯 마감 시각(없으면 기본):
   - `now < date@p` → 아직 아님(건너뜀).
   - 이미 `(group, date, slot)` 미션이 있음 → 건너뜀.
   - 아니면 발행: `publishedAt = max(date@p, group.createdAt)` (`createdAt` 이후에만 열림 — 그룹 생성 후 늦게 만들어진 경우 즉시 발행, 다운 후 따라잡기는 예정 시각), `mission_date = date`, `deadlineAt = deadlineAfter(publishedAt, d)`, 틀 선택(3번), 내용 생성(4번), 대상 멤버 선택(5번), `INSERT`. `DuplicateKeyException` 은 "이미 발행됨"으로 무시.
   - **과거 날짜는 소급하지 않는다**(오늘 것만).
3. **틀 선택은 결정적 순수 함수** `MissionTemplateSelector` (`mission/domain/` 또는 `service/`, 입력: `groupId, date, slotNo, dailyMissionCount, yesterdayMedia: Set<String>`) — `MISSIONS_API.md` §6-1 그대로, 풀은 MVP 8종뿐: `PHOTO_NOW, PHOTO_GALLERY, PHOTO_COLOR, VOICE_LINE, DRAW_SKETCH, DRAW_A_TO_B, TEXT_ACROSTIC, TEXT_FILL_BLANK` (매체 4개: PHOTO, VOICE, TEXT, DRAW).
   - `mediaOrder = shuffle(4매체, seed = seed(groupId, date))`.
   - `dailyMissionCount >= 2` → 슬롯 `k` 의 매체 = `mediaOrder[(k-1) % 4]`. `== 1` → `mediaOrder` 에서 `yesterdayMedia` 에 없는 첫 번째, 모두 겹치면 `mediaOrder[0]`.
   - 틀 = 그 매체의 풀 틀 중 `seed(groupId, date, slotNo)` 로 하나. `seed(...)` 는 JVM 간 안정적이어야 하므로 `Long` 산술(`groupId`, `date.toEpochDay()`, `slotNo` 를 소수 곱/XOR 로 섞음)로 만들고 `java.util.Random(seed)` 로 사용(`Objects.hash` 금지).
   - `yesterdayMedia` = 같은 그룹의 `mission_date = date - 1` 미션들의 `MissionTemplate.valueOf(template).media()` (알 수 없는 값은 무시). 새 mapper 메서드 `findTemplatesByGroupIdAndDate`.
4. **내용 생성 인터페이스**:
   ```java
   public interface MissionGenerator {
       GeneratedMission generate(MissionTemplate template, long seed, String previousContent);
   }
   public record GeneratedMission(String content, String category, Map<String, Object> params) {}
   ```
   `PlaceholderMissionGenerator`(`@Component`, 유일한 구현): 틀마다 문장 변형 **3개 이상**을 코드 상수로 갖고 `seed` 로 하나를 고르되 `previousContent`(그룹의 직전 미션 content)와 같으면 다음 변형을 쓴다. 규칙:
   - `PHOTO_NOW`: `params{subject}`, 예 "지금 창밖으로 보이는 풍경"(subject "창밖 풍경").
   - `PHOTO_GALLERY`: `params{condition}`, 예 "갤러리에서 가장 오래된 사진 하나" .
   - `PHOTO_COLOR`: `params{color}`, 예 color "파란색", content "주변에서 파란색인 것을 찍어줘".
   - `VOICE_LINE`: `params{sentence, tone}`, content 에 문장을 포함 ("이 문장을 {tone} 말투로 읽어줘").
   - `DRAW_SKETCH`: `params{subject, seconds: 30}` (대상이 지정되면 `[대상]` 을 문장에 포함: 예 "[대상]의 얼굴을 30초 안에 그려줘" 와 대상 없는 변형). 대상 지정 여부는 호출자가 결정해 `generate` 가 `needsTarget` 변형을 고르도록 인자로 받는다 → 시그니처를 `generate(template, seed, previousContent, boolean withTarget)` 로 한다.
   - `DRAW_A_TO_B`: `params{a, b}` ("[대상]을 …"는 `withTarget` 일 때).
   - `TEXT_ACROSTIC`: `params{word}` — **정확히 한글 3글자**.
   - `TEXT_FILL_BLANK`: `params{sentence}`, `sentence` 에 `___` 정확히 1개와 `[대상]` 포함, `content` 와 `sentence` 동일 문장. (`withTarget` 은 항상 true)
   - `category` 는 짧은 한글 라벨(예 "일상", "추억", "유머", "감성"), 50자 이하. `params` 에 `targetUserId` 는 **넣지 않는다**(서버가 5번에서 추가).
5. **대상 지정**: `TEXT_FILL_BLANK` 는 항상, `DRAW_SKETCH`/`DRAW_A_TO_B` 는 확률 0.3(`seed(groupId, date, slotNo)` 로 결정 — `MissionPublisher`/Selector 의 상수 `DRAW_TARGET_PROBABILITY = 0.3`, 설정 속성은 만들지 않음 → `MissionProperties` 시그니처 불변). 대상 = 그룹 멤버(`joined_at` 오름차순, 새 mapper `findUserIdsByGroupId`) 중 `seed` 로 1명. `params.targetUserId`(숫자)로 저장.
6. **"현재 미션" 쿼리**: 슬롯별 가장 최근 발행.
   ```sql
   ... FROM missions m WHERE m.group_id = #{groupId}
     AND m.id = (SELECT m2.id FROM missions m2 WHERE m2.group_id = m.group_id AND m2.slot_no = m.slot_no
                 ORDER BY m2.published_at DESC, m2.id DESC LIMIT 1)
   ORDER BY m.slot_no
   ```
   자정을 넘겨도(23:30 발행) 다음 발행 전까지 현재 미션이다. `number` 는 `(SELECT COUNT(*) FROM missions x WHERE x.group_id = m.group_id AND (x.published_at < m.published_at OR (x.published_at = m.published_at AND x.id <= m.id)))`.
7. **`closesAt` 은 예정값을 응답 시점에 계산**: `nextPublishAfter(m.publishedAt, 슬롯 발행 시각(현재 설정))`. 실제 "닫힘" 판정(다음 미션이 같은 슬롯에 발행되는 순간)은 6단계 제출에서 사용한다.
8. **그룹 생성 직후 즉시 발행**: `GroupService.createGroup` 은 `@Transactional` 이므로 같은 트랜잭션에서 발행하면 FK/롤백 문제가 생긴다 → `ApplicationEventPublisher` 로 `GroupCreatedEvent(Long groupId)`(`group/domain/GroupCreatedEvent.java` record)를 발행하고, `mission/service/MissionPublishListener` 가 `@TransactionalEventListener(phase = AFTER_COMMIT)` 로 받아 `MissionPublisher.publishIfDue(groupId, SeoulTime.now())` 를 호출한다. 리스너는 `Exception` 을 잡아 로그만 남긴다(그룹 생성은 이미 커밋됨; 실패해도 스케줄러가 다음 분에 재시도). **생성 응답의 `missionCount` 는 0 일 수 있다**(발행이 커밋 이후) — 허용, 16 화면 진입 시 상세 조회에서 갱신됨. 문서에 한 줄 명시.

## Files / Functions to Change

### A. 신규
- `mission/domain/MissionTiming.java`, `mission/domain/MissionTemplateSelector.java`(+ `MVP_POOL`), `mission/domain/GeneratedMission.java`(record), `mission/service/MissionGenerator.java`(interface), `mission/service/PlaceholderMissionGenerator.java`, `mission/service/MissionPublisher.java`, `mission/service/MissionScheduler.java`, `mission/service/MissionPublishListener.java`, `group/domain/GroupCreatedEvent.java`, `mission/dto/MissionMemberStatus.java`(`record(Long userId, boolean submitted)`), `mission/dto/TodayMissionSummary.java`(`record(Long missionId, String media, String content, long submittedCount, boolean submittedByMe)`).
- `MissionScheduler`: `@Component`, `@Scheduled(cron = "0 * * * * *", zone = "Asia/Seoul")` 메서드가 `groupMapper.findAllIds()`(신규)로 모든 그룹 id 를 순회하며 `publisher.publishIfDue(id, SeoulTime.now())`, 그룹별 `try/catch(Exception)` + 로그(한 그룹의 실패가 다른 그룹을 막지 않게). `@EnableScheduling` 을 `HaweApplication` 또는 `global/config` 의 설정 클래스에 추가.

### B. 수정
- `application.properties`: `hawe.mission.max-daily-count=1`.
- `mission/dao/MissionMapper.java`: `findTodayByGroupId` → `findCurrentByGroupId(groupId, userId)`(위 6번 SQL, 컬럼에 `mission_date`, `number` 추가), `findById` 에 `mission_date`, `number` 추가, 신규: `insert(MissionInsertRow)`(`group_id, content, category, mission_date, template, params, slot_no, published_at, deadline_at`; `useGeneratedKeys`), `existsByGroupIdAndDateAndSlot(groupId, date, slotNo)`, `findTemplatesByGroupIdAndDate(groupId, date)`, `findLatestContentByGroupId(groupId)`(직전 미션 content, 없으면 null), `findMemberStatuses(missionId)`(`SELECT gm.user_id, EXISTS(submission) FROM group_members gm WHERE gm.group_id = ? ORDER BY gm.joined_at, gm.id` → 새 row 클래스 `MissionMemberRow`), `findCurrentSummariesByGroupIds(List<Long> groupIds, Long userId)`(`<script>` + `foreach`, 그룹별 가장 낮은 `slot_no` 의 현재 미션 + `submitted_count` + `submitted_by_me` → 새 row `MissionSummaryRow`), `MissionRow` 에 `missionDate`(LocalDate), `number`(int) 필드. `insert` 용 파라미터 객체 클래스는 `mission/dao/` 에 둔다.
- `group/dao/GroupMapper.java`: `findSummariesByUserId` 에서 `submitted_today` 서브쿼리 삭제(`GroupSummaryRow.submittedToday` 필드·접근자 삭제), 신규 `findAllIds()`. `group/dao/GroupMemberMapper.java`: `stamped_today` 를 "현재 미션(슬롯별 최신) 제출 여부" 로 교체(위 6번 서브쿼리 패턴), 신규 `findUserIdsByGroupId(groupId)`(`joined_at, id` 오름차순).
- `poke/dao/PokeMapper.java`: `existsTodaySubmission` → `existsCurrentMissionSubmission(groupId, userId)`(현재 미션 기준), `poke/service/PokeService.java` 호출 이름 수정 + 관련 테스트의 이름만 수정.
- `mission/dto/MissionResponse.java`: `(Long missionId, Long groupId, int slotNo, int number, String content, String category, String template, String media, Map<String,Object> params, OffsetDateTime publishedAt, OffsetDateTime deadlineAt, OffsetDateTime closesAt, boolean submittedByMe, List<MissionMemberStatus> members)`. `TodayMissionsResponse` 는 필드 유지(날짜 의미만 변경).
- `mission/service/MissionService.java`:
  - `getTodayMissions`: `findCurrentByGroupId`; `date = rows 가 있으면 첫 미션의 mission_date, 없으면 SeoulTime.today()`; `nextPublishAt = min over slot(1..dailyMissionCount) of (해당 슬롯의 현재 미션이 있으면 그 closesAt, 없으면 nextPublishAfter(SeoulTime.now(), 슬롯 발행 시각))`.
  - `toResponse(row)`: `closesAt` 계산, `members` 조회, **`content` 의 `[대상]` 치환**: `params.targetUserId` 의 사용자 닉네임(`UserMapper.findById`), 사용자가 없으면 "친구". `params.target{userId,nickname}` 은 기존대로 유지.
  - 신규 `Map<Long, TodayMissionSummary> summarizeCurrent(List<Long> groupIds, Long userId)`(`GroupService` 가 사용; `media` 는 템플릿에서, `content` 는 같은 `[대상]` 치환 적용).
  - `getMission` 도 새 필드 포함.
- `group/service/GroupService.java` + `group/dto/GroupSummaryResponse.java`: `GroupSummaryResponse` 에 `TodayMissionSummary todayMission` 추가(없으면 `null`), `submittedToday` 는 유지하되 **`todayMission != null && todayMission.submittedByMe()`** 로 계산. `listMyGroups` 가 `missionService.summarizeCurrent(groupIds, userId)` 를 한 번 호출(그룹 0개면 생략). `createGroup` 이 커밋 이후 처리를 위해 `ApplicationEventPublisher.publishEvent(new GroupCreatedEvent(group.getId()))`. 생성자에 `ApplicationEventPublisher`, `MissionService` 추가(→ 테스트의 생성자 호출 수정).
- `mission/service/MissionErrorCode`: 변경 없음(`MISSION_CLOSED` 는 6단계).

### C. 문서
- `docs/BACKEND/MISSIONS_API.md`: §0 지각/종료(마감 이후~다음 미션 발행 전은 지각 제출 가능, 다음 미션 발행 순간 종료 — 적용은 6단계), §2 `today` 응답에 `number`, `closesAt`, `members`, "현재 미션" 의미, `nextPublishAt` 정의 변경, §6 첫 미션 즉시 발행·`deadlineAt` 계산·스케줄러 동작·임시 생성기, §6-1 MVP 틀 풀, §3·§10 `MISSION_CLOSED` 는 "6단계 구현 예정" 표기. 문서 맨 위 "구현되지 않았다" 문구를 코드 상태(조회·생성 구현, 제출/피드 미구현)에 맞게 한 줄 수정.
- `docs/BACKEND/API_DESIGN.md` §2: 그룹 목록에 `todayMission`(필드 설명), `submittedToday` 정의 변경.
- `docs/BACKEND/CONTRACT_DECISIONS.md` §3 헤더 상태 → `반영됨 (1차: AI 연동·하루 여러 개·MISSION_CLOSED 는 제외)`, §3-5 미결 중 "생성 실패 시 대체 문장 정책"에 "임시 생성기가 항상 성공, 실패 시 스케줄러 1분 후 재시도" 반영.
- `docs/FEATURE/PROJECT_OVERVIEW.md`: §3 마감과 지각, §5 MVP 하루 1개, §11-6 자정 넘는 마감 항목을 위 규칙으로 최소 수정(해당 문장만).
- Postman `HAWE_MISSION.postman_collection.json`, `HAWE_GROUP.postman_collection.json`: 하루 미션 수가 1로 고정되므로 `dailyMissionCount` 2 이상을 쓰는 요청은 400 기대로 바꾸거나 제거. `today` 응답 모양(`number/closesAt/members`)과, 그룹 생성 시 **발행 시각 이후면 미션이 이미 열려 있음**(시간 의존)을 반영 — 기대값을 시각에 따라 달라지게(`missions` 비어 있음 vs 1개) 쓰고, SQL 로 미리 넣는 픽스처(`INSERT INTO missions … CURDATE()`)가 있으면 `(group, date, slot)` 유일 제약과 충돌하지 않게 슬롯/날짜를 조정. 컬렉션 JSON 유효성과 한글 이름 깨짐(`?`)이 없는지 확인(UTF-8 저장).

## Implementation Steps

0. `git status`/`git diff` 로 트리 확인(앞 단계 미커밋 보존). 베이스라인 `.\mvnw.cmd test` 확인(JDK 25; `JAVA_HOME` 문제 이력 있음).
1. **테스트 먼저(RED)**: `MissionTimingTest`, `MissionTemplateSelectorTest`, `PlaceholderMissionGeneratorTest`, `MissionPublisherTest`, 갱신된 `MissionServiceTests`, `MissionMapperTests`(DB), 그룹/콕 mapper·service 테스트. 컴파일 실패/RED 확인.
2. `MissionTiming` → `MissionTemplateSelector` → `MissionGenerator`/`PlaceholderMissionGenerator` 구현, 순수 단위 테스트 GREEN.
3. mapper 변경(현재 미션 쿼리, insert 등) + `MissionRow` 확장, DB 통합 테스트 GREEN.
4. `MissionPublisher`(+`MissionScheduler`, `@EnableScheduling`), `MissionPublishListener`/`GroupCreatedEvent`, `GroupService` 연결.
5. `MissionService`/DTO 확장, 그룹 목록 `todayMission`, `CURDATE()` 쿼리 교체(`GroupMapper`, `GroupMemberMapper`, `PokeMapper`).
6. `max-daily-count=1`, 문서·Postman 갱신.
7. `.\mvnw.cmd clean test`(낡은 `target/classes` 방지) 전체 통과 → 실서버 스모크 → `.agent/report.md`.

## Constraints

- **스키마(V1) 변경 금지** — DB 초기화가 필요 없어야 한다. 새 컬럼/인덱스/테이블 없음.
- `MissionProperties` 시그니처(3개 필드) 불변. 새 설정 속성 추가 금지(`DRAW_TARGET_PROBABILITY` 는 상수). 새 의존성 금지.
- 범위 밖 금지: AI 호출, `MISSION_CLOSED` 적용, 알림, 콕 응답(`pokedByMe`), 제출/피드 API, 클라이언트.
- 시각 계산은 모두 `SeoulTime`/`LocalDateTime`(Seoul 벽시계)로, JVM 기본 시간대에 의존하지 않는다(1단계 규칙). DB `DATETIME` 컬럼에는 `LocalDateTime` 을 그대로 넣는다.
- 패키지 구조 유지(5개 하위 패키지: controller/service/dto/domain/dao). 코드 스타일은 기존 `mission`/`group` 패키지(4칸 스페이스, MyBatis 어노테이션 SQL, getter/setter Row 클래스)를 따른다. 무관한 포맷 변경 금지. `git commit`/`push` 금지.
- 스케줄러/리스너는 예외를 삼키되 **로그를 남긴다**(SLF4J), 민감 정보 로그 금지.

## Edge Cases

- **멱등/동시 실행**: 같은 `(group, date, slot)` 을 스케줄러와 즉시 발행(또는 서버 두 대)이 동시에 시도 → `UNIQUE` 위반을 정상 흐름으로 처리, 미션은 하나.
- **발행 시각 전/후**: 생성 08:59, p=09:00 → 즉시 발행 없음, `today` 응답 `missions=[]`, `nextPublishAt = 오늘 09:00`. 생성 14:00 → 즉시 발행(`publishedAt` ≈ 생성 시각, 마감 오늘 23:00, `closesAt` 내일 09:00). 생성 23:10, 마감 기본 23:00 → 마감 **내일** 23:00 (생성 22:55 → 마감 5분 뒤: §3-5 미결, 그대로 둔다).
- **자정 넘김**: p=23:30 발행 → 다음 날 00:10 에도 그 미션이 현재 미션(`date` = 발행일). 다음 날 23:30 에 새 미션. `mission_date` 는 발행 기준 날짜(서울).
- **서버 다운 후 복구**: 09:00~11:00 다운 → 11:00 첫 tick 에서 오늘 미션 발행(`publishedAt` = 09:00, 마감 오늘 23:00). 하루 전체 다운이면 어제 미션은 만들지 않음.
- **어제 매체 제외**: 어제 미션 매체 PHOTO → 오늘 PHOTO 가 아닌 매체. 4매체를 모두 어제 썼다면(불가능한 상황이지만) `mediaOrder[0]`. 어제 미션이 없으면 제외 없음.
- **그룹 설정 변경 후**: 슬롯 발행 시각이 바뀌면 이미 오늘 미션이 있으면 오늘은 유지(내일부터 적용), 없고 새 시각이 이미 지났으면 다음 tick 에서 발행.
- **대상 지정**: 멤버 1명뿐인 그룹도 `TEXT_FILL_BLANK` 는 대상을 지정(본인). 대상 사용자가 닉네임을 바꿨으면 응답은 최신 닉네임. 대상 사용자가 없으면 "친구".
- **`[대상]` 치환**: `content` 에 `[대상]` 이 여러 번이면 모두 치환. `params.sentence` 는 원문 유지(앱이 치환 책임 없음: `content` 사용).
- **그룹 삭제 후**: 스케줄러가 id 목록을 가져온 뒤 삭제된 그룹 → `findById` null 이면 건너뜀(예외 아님).
- **그룹 0개**: 스케줄러가 즉시 반환.
- **응답**: 미발행 그룹의 `todayMission` 은 `null`, `submittedToday=false`. `members[]` 는 참여 순서, `submitted` 는 해당 미션 제출 여부. 제출이 없는 단계에서는 항상 false.
- **동시성/성능**: 그룹 수가 늘면 분당 순회 비용이 커지지만 MVP 에서는 허용 — report 에 "분당 전체 그룹 순회"를 알려진 한계로 기록.

## Regression Risks

- **`CURDATE()` 교체**: 미션이 "오늘 날짜"가 아니어도(어제 발행) 현재 미션이면 제출/콕 판단이 일관돼야 한다. `MissionMapperTests`, `PokeMapperTests`, `GroupMapperTests`(특히 3단계가 추가한 `GroupContractMapperTests`)가 `mission_date = CURDATE()` 로 직접 INSERT 하는 픽스처를 갖고 있으니 새 쿼리(슬롯별 최신 `published_at`)로 같은 단언이 성립하는지 확인하고, 필요 시 픽스처의 `published_at` 을 명시.
- **`GroupService` 생성자 변경**(이벤트 발행자·`MissionService` 추가) → `GroupServiceTests`, `GroupContractServiceTests` 생성자 호출 수정. `MissionService` ↔ `GroupService` 순환 의존이 생기지 않게(MissionService 는 GroupService 를 참조하지 않는다; `GroupMapper`/`GroupMemberMapper` 직접 사용).
- **`GroupSummaryRow.submittedToday` 삭제** → `GroupMapperTests` 의 해당 단언 수정(응답의 `submittedToday` 는 서비스에서 유도되므로 서비스 테스트로 이동).
- **`@EnableScheduling`**: `@SpringBootTest` 컨텍스트에서도 분 단위 tick 이 돈다. 테스트 DB 의 커밋된 그룹(스모크로 남은 데이터)에 미션이 발행될 수 있으나 테스트 트랜잭션(롤백)의 데이터는 보이지 않아 영향 없음. 기존 테스트가 개발 DB 의 전체 미션 수에 의존하지 않는지 확인.
- **`max-daily-count=1`**: 기존 서비스 테스트는 `MissionProperties(…, 5)` 를 직접 쓰므로 영향 없음. Postman/문서의 `dailyMissionCount` 2 이상 예시는 갱신.
- **JSON `params`**: 대상 지정 시 `targetUserId` 는 숫자로 저장해 기존 `resolveParams`(`Number`/문자열 모두 허용)와 호환. `params` 직렬화는 `ObjectMapper.writeValueAsString`.
- **트랜잭션 경계**: `MissionPublisher.publishIfDue` 는 `REQUIRES_NEW`. 스케줄러/리스너(트랜잭션 없는 호출자)에서 호출되므로 프록시를 거쳐야 한다(같은 클래스 내부 호출 금지).
- **AFTER_COMMIT 리스너**: 단위 테스트에서 `createGroup` 이 이벤트를 발행하는지만 검증하고, 리스너 자체는 별도 테스트. 통합 테스트는 커밋이 필요하므로 non-@Transactional 테스트로 만들고 정리(cleanup)를 한다.

## Tests Required

**순수 단위**
- `MissionTimingTest`: `deadlineAfter`(09:00→당일 23:00 / 23:30→다음 날 23:00 / 정확히 23:00→다음 날 / 14:00), `nextPublishAfter`(09:00 이전/정확히 09:00/이후), 월말·연말 경계.
- `MissionTemplateSelectorTest`: 풀이 정확히 8종·매체 4종, 같은 입력 → 같은 출력(재현성), 여러 `groupId/date` 에서 `dailyMissionCount=1` 이면 어제 매체가 오늘과 다름(어제 매체 지정 케이스), 모두 겹치면 `mediaOrder[0]` 폴백, `dailyMissionCount>=2` 에서 슬롯 간 매체 중복 없음, 대상 지정(`TEXT_FILL_BLANK` 항상, DRAW 확률 0.3 근사 — 많은 시드의 비율이 0.2~0.4, 같은 시드는 같은 결과).
- `PlaceholderMissionGeneratorTest`: 8개 틀 각각 `params` 키 집합이 `paramsSchema()` 에서 `targetUserId` 를 뺀 집합과 같음, `TEXT_ACROSTIC.word` 가 한글 3글자, `TEXT_FILL_BLANK.sentence` 에 `___` 정확히 1개, `withTarget=true` 인 틀의 `content` 에 `[대상]` 포함·`false` 에는 미포함, `category` ≤ 50자·비어 있지 않음, 같은 입력 재현성, `previousContent` 와 같은 문장을 연속으로 내지 않음(변형 ≥ 3).

**Mockito**
- `MissionPublisherTest`: 아직 시각 전(미발행), 시각 후(발행, 필드 검증: `mission_date`, `publishedAt`, `deadlineAt`, `slotNo`, `template ∈ 풀`), 이미 존재(발행 안 함), 그룹 생성 이후(`createdAt` > 예정 시각 → `publishedAt=createdAt`), `DuplicateKeyException` 무시, 대상 지정 시 `params.targetUserId` 저장, 그룹 없음 건너뜀, 어제 매체 조회가 선택에 반영.
- `MissionSchedulerTest`/리스너 테스트: 한 그룹 예외가 다른 그룹 처리를 막지 않음, 리스너가 예외를 삼킴.
- `MissionServiceTests` 갱신: 현재 미션 응답의 `number`, `closesAt`, `members`, `[대상]` 치환(닉네임/삭제된 대상 폴백), `nextPublishAt` 규칙(미션 없음: 다음 발행 시각, 있음: `closesAt`), `summarizeCurrent`.
- `GroupServiceTests`/`GroupContractServiceTests` 갱신: 목록에 `todayMission`/`submittedToday` 유도, `createGroup` 이 `GroupCreatedEvent` 를 발행.
- `PokeServiceTests`: 메서드 이름 변경 반영.

**DB 통합 (`@SpringBootTest @Transactional`)**
- `MissionMapperTests`: 현재 미션 = 슬롯별 최신(여러 날짜 미션 중 최신만), **어제 날짜에 발행된 미션도 현재 미션**(자정 넘김), `number` 계산, `insert` 후 조회, UNIQUE 위반, `existsByGroupIdAndDateAndSlot`, `findTemplatesByGroupIdAndDate`, `findMemberStatuses`(제출자/미제출자), `findCurrentSummariesByGroupIds`(제출 수·내 제출).
- `GroupMapper`/`GroupMemberMapper`/`PokeMapper` 의 현재 미션 기준 `stamped_today`/`existsCurrentMissionSubmission`.
- (선택, non-@Transactional) 그룹 생성 서비스 → 커밋 후 첫 미션 발행 통합 테스트: 요청에 슬롯 `publishTime="00:00"` 을 줘서 시각과 무관하게 즉시 발행되게 하고, 끝에서 그룹을 정리. 시간대(오전/오후)에 따라 결과가 달라지지 않는 형태로.

기존 전체 테스트(3단계 포함)가 통과해야 한다.

## Validation Steps

1. `.\mvnw.cmd clean test` 전체 통과.
2. 실서버 스모크(`.\mvnw.cmd spring-boot:run`, 끝나면 종료). 더미 사용자 1~2명으로:
   - 그룹을 슬롯 `publishTime="00:00"` 으로 생성(`missionSlots:[{"slotNo":1,"publishTime":"00:00"}]`) → 곧바로 `GET /api/groups/{id}/missions/today`: `missions` 1개, `number:1`, `template ∈ 8종`, `media`, `content`(대상 틀이면 `[대상]` 이 닉네임으로 치환), `publishedAt`/`deadlineAt`/`closesAt` 모두 `+09:00`, `members:[{userId, submitted:false}]`, `nextPublishAt == closesAt`.
   - `GET /api/groups` 의 `todayMission` 요약과 `submittedToday:false`.
   - 발행 시각이 미래인 그룹(슬롯 `publishTime` 을 현재+2분)을 만들고 `missions=[]`, `nextPublishAt` 확인 → 2분 뒤 tick 에서 미션이 열리는지(`todayMission` 이 채워짐) — 시간이 걸리면 report 에 미수행으로 기록.
   - `dailyMissionCount:2` 로 그룹 생성 → 400 (`max-daily-count=1`).
   - 스케줄러 로그에 예외가 반복되지 않는지.
3. `GET /api/mission-templates` 는 변경 없이 15종 그대로.
4. 문서·Postman 갱신 확인(JSON 유효성, 한글 깨짐 없음), `git diff --check`.
5. report 에: 변경 파일, 명령·결과, 스모크 응답 예시(토큰 제외), 알려진 한계(분당 전체 그룹 순회, 생성 응답 `missionCount` 지연, `MISSION_CLOSED` 미적용), 4A/6단계가 알아야 할 것.

## Potential Merge Conflicts

- **3단계(Group, 백엔드)**: `GroupService`, `GroupMapper`, `GroupMemberMapper`, `GroupSummaryRow/Response`, 3단계 테스트(`GroupContractServiceTests`, `GroupContractMapperTests`, `GroupMapperTests`)를 이 단계가 다시 수정한다 → **3단계가 먼저 커밋돼 있어야 한다.** 미커밋이라면 3단계 변경을 보존한 채 필요한 부분만 수정.
- **6단계(제출·피드)**: `MissionMapper`/`MissionService`/`MissionResponse` 를 다시 만진다 → 이 단계 커밋 후 시작. `MISSION_CLOSED` 와 "다음 미션이 발행되면 닫힘" 판정(`findCurrentByGroupId` 재사용 가능)은 6단계 몫.
- **4A(Android 미션 연결)**: 이 단계의 응답(`number`, `closesAt`, `members`, `todayMission`, `content`)이 입력이다. 4A 는 이 단계가 확정된 뒤 시작.
- 공유 파일: `application.properties`(1·3단계도 수정), `docs/BACKEND/*`, Postman — 미커밋 변경을 보존한 채 해당 부분만 편집.
