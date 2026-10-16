## Goal
개발자 모드에서 그룹 안의 **현재 미션을 리롤**(새 틀·새 문장으로 교체)하는 기능을 추가한다. 서버 `POST /api/dev/missions/{missionId}/reroll` + Android 개발자 UI(미션 화면 버튼, 개발자 도구 화면 버튼). 안전장치 없음·release 에서도 열림은 기존 개발자 모드 결정 그대로다.

## Findings
- 기존 "미션 생성"은 `DevMissionService.publish` → `MissionPublisher.forcePublish(groupId, now, template)`. **빈 슬롯에만** 새 미션을 넣고(`existsByGroupIdAndDateAndSlot` 이면 건너뜀) 이미 오늘 슬롯이 다 찼으면 "오늘 발행할 빈 슬롯이 없어요"만 돌려준다. 즉 현재 미션을 바꿀 방법이 없다.
- `missions` 에는 `UNIQUE (group_id, mission_date, slot_no)` (`V1__initial_schema.sql` `uk_missions_group_date_slot`)가 있어 **같은 날 같은 슬롯에 새 행을 추가할 수 없다.** 따라서 리롤은 **기존 행을 제자리에서 UPDATE** 해야 한다(id 유지). "현재 미션" 판정은 `MissionMapper.CURRENT`(슬롯별 최신)라 id 가 같으면 앱·피드·달력 로직이 그대로 동작한다.
- 미션 생성 재료: `MissionPublisher.publish` 의 틀 선택(`MissionTemplateSelector.select`, 시드 = `seed(groupId,date,slotNo)` 로 **결정적**), 대상 선택(`needsTarget`/`targetUserId` params), `contexts.build`, `experiments.assign`, `generator.generate(template, seed, context.withRecentMissions(recent), withTarget)`. 시드가 결정적이라 같은 입력으로 다시 부르면 **똑같은 미션이 나온다** → 리롤은 시드와 틀 선택에 무작위성을 넣어야 한다.
- `PlaceholderMissionGenerator` 는 `new Random(seed).nextInt(variants.size())` 로 문장을 고른다(시드만 바꾸면 달라짐). AI 켜져 있으면 `FallbackMissionGenerator` 가 AI 를 부르고 실패 시 placeholder.
- 미션에 매달린 행: `submissions`(→ `reactions`, `comments`(→ `comment_mentions`), `submission_guesses`, `submission_ai_reviews`) 와 `pokes`(mission_id NOT NULL). 틀이 바뀌면 기존 제출 답은 의미가 없으므로 같이 지워야 한다. 그룹 삭제용 삭제 SQL 은 `GroupMapper` 에 그룹 단위로만 있다(`deleteCommentMentionsByGroupId` 등) — 미션 단위 버전을 `DevMapper` 에 새로 만든다.
- 앱: `MissionScreen` 에 `[개발자] 미션 생성` 버튼(미션 없음 화면 / 미션 카드 아래, `dev != null` 일 때만), `DevToolsScreen` 에 미션 생성(`dev.publish(groupId)` / 틀 지정 `dev.publish(groupId, template)`) 과 마감 당기기 UI 가 있다. `DevRepository.publish` / `DevPublishResponse` / `DevPublishedMission` 존재.
- 앱 `DataStore.applyCurrentMission` 은 id 기반으로 현재 미션 목록을 교체한다. 리롤은 id 가 그대로이고 내용만 바뀌므로 새로고침(`refresh++` / `refreshMission()`) 후 카드·제출 여부·참여 현황이 서버 값으로 갱신되어야 한다.

## Root Cause / Key Implementation Points
1. **서버 `MissionPublisher.reroll(Long missionId, LocalDateTime now, MissionTemplate requested)`** (`@Transactional(propagation = REQUIRES_NEW)`, `forcePublish` 옆): 현재 미션을 새로 생성해 **같은 행을 UPDATE**.
   - 대상 행 조회(id, group_id, mission_date, slot_no, template, published_at, deadline_at). 없거나 `missions.isCurrent(missionId)` 가 false 면 `MISSION_NOT_FOUND`(404) — `BusinessException(MissionErrorCode.MISSION_NOT_FOUND)`.
   - 틀: `requested != null` 이면 그것. 아니면 `properties.enabledTemplates()` 에서 **현재 틀을 제외**한 목록에서 무작위 1개(후보가 없으면, 즉 풀에 틀이 하나뿐이면 현재 틀 허용). 매체 분산 규칙(`MissionTemplateSelector.select`)은 리롤에 적용하지 않는다(테스트 목적의 임의 교체).
   - 시드: `MissionTemplateSelector.seed(groupId, missionDate, slotNo) ^ ThreadLocalRandom.current().nextLong()` 로 매번 다른 문장.
   - 대상: `withTarget = MissionTemplateSelector.needsTarget(template, seed)` → 기존 코드와 동일하게 `targetUserId` 를 `members.findUserIdsByGroupId` 에서 같은 방식으로 골라 params 에 넣는다.
   - `recent`: 기존 `missions.findRecentByGroupId(groupId, RECENT_CONTENTS + dailyMissionCount - 1)` 그대로 — 지금 바꾸려는 미션 문장도 들어 있으므로 같은 문장이 다시 나오는 것을 피한다. 별도 처리 없음.
   - 컨텍스트: `contexts.build(group, missionDate).withExperimentArm(experiments.assign(groupId, missionDate).orElse(null))` — 기존과 동일.
   - 저장: 새 `MissionMapper.updateGenerated(id, content, category, template, params, generationSource, experimentArm)` (`UPDATE missions SET ... WHERE id = #{id}`). **`published_at` / `deadline_at` / `mission_date` / `slot_no` 는 건드리지 않는다**(시계 테스트 상태 유지).
   - `publish` 안의 "생성 → params/대상 → insert 직전" 코드와 중복이 생기면 **private 헬퍼 하나**(틀·시드·group·date·slot 을 받아 `GeneratedMission` + params 를 돌려주는 정도)로 뽑아도 된다. 그러나 `publish` 동작·시그니처는 바꾸지 않는다(기존 `MissionPublisherTests` 가 그대로 통과해야 함). 헬퍼가 어색하면 복제해도 된다(dev 전용, 과한 리팩터링 금지).
2. **제출물 정리**: `DevMapper` 에 미션 단위 삭제 추가 — 순서대로 `comment_mentions`, `comments`, `reactions`, `submission_guesses`, `submission_ai_reviews`, `submissions`, `pokes` (`GroupMapper.delete…ByGroupId` 의 JOIN 구문을 `WHERE s.mission_id = #{missionId}` / `WHERE mission_id = #{missionId}` 로 바꾼 형태). `DevMissionService.reroll` 에서 `@Transactional` 로 묶어 "삭제 → 갱신"이 한 트랜잭션. 업로드된 사진·그림 파일(`FileStorage`)은 **지우지 않는다**(개발 전용, 고아 파일 허용 — 보고서에 한 줄 명시).
3. **서버 `DevMissionService.reroll(Long missionId, RerollRequest request)`** + 컨트롤러 `POST /api/dev/missions/{missionId}/reroll` (`@RequestBody(required = false) RerollRequest`). 잘못된 틀 이름은 `publish` 와 같이 `CommonErrorCode.VALIDATION_ERROR`. 반환은 기존 `PublishedMission`(missionId, groupId, slotNo, template, content) 재사용 — 갱신 후 `dev.findMission(id)` 로 다시 읽어 채움. 새 DTO: `DevDtos.RerollRequest(String template)`.
4. **Android `DevRepository.reroll(missionId, template: String? = null): DevPublishedMission`** (`POST /api/dev/missions/$missionId/reroll`, `auth = false`, 본문 `DevRerollRequest(template)`; 본문이 null 이어도 `{}`/template null 이 직렬화되도록 기존 `DevPublishRequest` 와 같은 패턴). `DevApi.kt` 에 `@Serializable internal data class DevRerollRequest(val template: String? = null)`.
5. **UI**
   - `MissionScreen`: 미션 카드 아래 `[개발자] 미션 생성` 버튼 옆(아래 줄)에 `[개발자] 미션 리롤` 추가(`dev != null && mission != null` 일 때만). 누르면 **`HAlert` 확인**("이 미션을 새로 뽑을까요? 이미 낸 제출·반응·댓글·콕이 모두 삭제돼요.") → 확인 시 `dev.reroll(mission.id)` → `refresh++` → 토스트 "미션을 새로 뽑았어요 (<template>)". 실패 시 `미션 리롤 실패: …` 토스트, 진행 중 중복 탭 방지는 기존 `creating` 플래그를 재사용(이름은 `devBusy` 등으로 바꿔도 됨). `CancellationException` 은 재던지기(기존 `createMission` 패턴 그대로). 선택된 슬롯 칩(`selectedId`)은 id 가 유지되므로 그대로 둔다.
   - `DevToolsScreen`: 기존 "미션 생성" 영역(`dev.publish(groupId, template)` 틀 칩이 있는 곳)에 `선택 미션 리롤`(무작위 틀) 과 `선택 미션을 <선택한 틀>로 리롤`(기존 틀 칩 선택값 사용) 버튼 추가. 대상은 기존 `missionId` 선택값. 확인은 기존 삭제 버튼처럼 `HAlert`. 결과는 기존 "최근 결과 20줄" 로그 함수(예: `run("미션 리롤") { … }`)에 `"${result.template} · ${result.content}"` 로 기록하고 `refreshMission()` 호출. 파일을 읽어 기존 패턴(busy 직렬화, 결과 로그)을 따르고 새 화면/상태 홀더를 만들지 않는다.
6. 문서: `docs/BACKEND` 등 외부 문서는 건드리지 않는다. 컨트롤러 `DevController` 의 "운영 배포 시 제거" 주석 범위에 포함되므로 별도 주석 불필요.

## Files / Functions to Change
서버 (`src/main/java/com/sapphire/hawe/`)
- `mission/service/MissionPublisher.java` — `reroll(...)` 추가(+선택적 private 헬퍼).
- `mission/dao/MissionMapper.java` — `updateGenerated(...)` `@Update` 추가. (`findById` 류가 없으면 `DevMapper.findMission` 처럼 필요한 컬럼만 읽는 조회를 `MissionMapper`/`DevMapper` 중 하나에 추가: id, group_id, mission_date, slot_no, template. `DevMapper.findMission` 은 mission_date 를 안 읽으므로 확인 후 필요 컬럼 추가 대신 **새 조회 메서드를 만들고 기존 쿼리는 수정하지 않는다.**)
- `dev/dao/DevMapper.java` — 미션 단위 삭제 7개.
- `dev/service/DevMissionService.java` — `reroll(Long, RerollRequest)`.
- `dev/dto/DevDtos.java` — `RerollRequest`.
- `dev/controller/DevController.java` — 엔드포인트.
Android (`android/app/src/main/java/com/sapphire/hawe/`)
- `core/net/DevApi.kt`, `core/net/DevRepository.kt` — 요청 DTO·`reroll`.
- `feature/mission/MissionScreen.kt` — 리롤 버튼·확인 다이얼로그·핸들러.
- `feature/dev/DevToolsScreen.kt` — 리롤 버튼 2개.

## Implementation Steps
1. 서버: `MissionMapper`(갱신·조회) → `DevMapper`(삭제 7개) → `MissionPublisher.reroll` → `DevDtos.RerollRequest` → `DevMissionService.reroll` → `DevController` 엔드포인트. 서버 테스트 작성·통과.
2. Android: `DevApi`/`DevRepository` → `MissionScreen` → `DevToolsScreen`. 단위 테스트 추가.
3. 전체 서버 테스트 + Android 단위 테스트/`assembleDebug` 실행.

## Constraints
- 개발 전용 기능이다. 안전장치(빌드 타입 게이트·인증) 추가 금지(기존 결정). 기존 `/api/dev/**` 와 같은 `permitAll` 아래이므로 `SecurityConfig` 변경 없음.
- `V1` 등 마이그레이션 변경 없음. 운영 코드 경로(`publishIfDue`, 스케줄러, 일반 미션 API)의 동작을 바꾸지 않는다.
- 새 의존성·큰 추상화 금지. 범위 밖(달력 다중 미션 표시, 마감 재오픈 등) 건드리지 않는다.
- JDK: Gradle 은 JDK 17(`C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot`) 로 실행(셸 `JAVA_HOME` 이 25 면 Gradle 이 깨진다). Android 는 PowerShell 에서 `android\gradlew.bat :app:testDebugUnitTest`. 서버는 `.\mvnw.cmd`.
- 커밋·푸시 금지.

## Edge Cases
- 현재 미션이 아닌 id(지난 날 미션·존재하지 않는 id) → 404 `MISSION_NOT_FOUND`, 아무것도 지우지 않는다(조회·검증을 삭제보다 먼저).
- 잘못된 `template` 문자열 → 400 `VALIDATION_ERROR`, 삭제 전에 검증.
- 풀(`enabledTemplates`)이 틀 1종뿐 → 현재 틀 재선택 허용(문장만 바뀜).
- 틀 지정이 풀에 없는 틀(예: 2차 틀)이어도 지정 틀은 허용(`publish` 의 `template` 지정과 동일하게 풀 검증 안 함).
- `TEXT_FILL_BLANK`/그리기 틀로 바뀔 때 `targetUserId` 포함, 다른 틀로 바뀔 때는 이전 params 가 남지 않아야 한다(params 는 통째로 교체).
- 마감이 지난 미션을 리롤하면 내용만 바뀌고 계속 마감 상태(제출 불가) — 의도된 동작, 보고서에 한 줄.
- 이미 제출한 사용자가 있는 미션 → 제출·반응·댓글·콕이 지워지고 다음 새로고침에 `didSubmit=false`. 앱이 열려 있던 `MissionPerform`/결과 화면에서 돌아올 때 오류 없이 새로고침되는지(결과 화면은 제출이 없으면 잠금 상태가 됨) 확인.
- AI 켜진 환경에서 생성이 느리면 `reroll` 이 트랜잭션 안에서 대기(기존 `publish` 와 동일) — 앱 타임아웃(기존 값) 안에서 끝나는지만 확인, 별도 처리 없음.
- 동시 탭: 앱은 busy 플래그로 직렬화.

## Regression Risks
- `MissionPublisher.publish` 를 헬퍼로 일부 뽑는 경우 시드·결정성이 바뀌면 `MissionPublisherTests`(틀 선택·시드 고정 가정)가 깨진다 — 헬퍼 추출 시 기존 호출 인자·순서를 그대로 유지.
- `DevMapper` 삭제 SQL 의 JOIN 순서 오류 시 FK 위반 → 서버 통합 확인 필요(자식→부모 순서 엄수).
- `MissionScreen` 은 A6(다중 미션)·개발자 모드 변경이 공존하는 파일 — 버튼만 추가하고 선택 칩/`refresh` 로직은 건드리지 않는다.

## Tests Required
- `MissionPublisherTests`(Mockito) 추가:
  - `reroll` 이 `updateGenerated` 를 호출하고 `insert` 는 호출하지 않으며 published/deadline 을 바꾸지 않는다.
  - 틀 미지정 시 새 틀이 현재 틀과 다르다(풀 2종 이상일 때, 여러 번 반복해 항상 다름), 지정 시 그 틀을 쓴다.
  - 현재 미션이 아니면 `MISSION_NOT_FOUND`.
  - `TEXT_FILL_BLANK` 로 리롤하면 params 에 `targetUserId` 가 들어간다.
- `DevMissionService` 단위 테스트(기존 `DevActionServiceTests` 스타일): 잘못된 틀 → `VALIDATION_ERROR` 이고 삭제 메서드가 호출되지 않음; 정상 호출 시 삭제 후 갱신 순서(`InOrder`).
- Android `DevRepositoryTest` 에 `reroll` 추가: 경로 `/api/dev/missions/7/reroll`, 본문 `{}` 또는 `{"template":"PHOTO_NOW"}`, 응답 디코딩.
- 기존 서버 테스트 전체와 Android 단위 테스트 전체 통과.

## Validation Steps
1. `.\mvnw.cmd clean test -q` (필요 시 MariaDB·`.env` 가 필요한 테스트 포함 여부를 보고서에 명시).
2. PowerShell 에서 JDK 17 로 `android\gradlew.bat :app:testDebugUnitTest :app:assembleDebug`.
3. 서버를 띄울 수 있으면(DB 필요) `POST /api/dev/seed` 로 그룹을 만들고 `POST /api/dev/missions/publish` 후 `POST /api/dev/missions/{id}/reroll` 을 두 번 호출해 `template`/`content` 가 바뀌고 id 가 유지되는지, 제출이 있던 미션에서 제출이 사라지는지 curl 로 확인한다(임시로 띄운 서버와 `adb reverse` 는 확인 후 정리하지 말고 **그대로 두거나, 정리했다면 보고서에 명시**: 사용자가 폰에서 `adb reverse tcp:8080 tcp:8080` 를 쓰고 있다).
4. 기기 확인은 사용자가 한다(보고서에 수동 체크리스트: 개발자 모드 → 그룹 → 미션 화면 `[개발자] 미션 리롤` → 확인 → 카드 문구 변경, 제출 상태 초기화 / 개발자 도구에서 틀 지정 리롤).

## Potential Merge Conflicts
- `MissionScreen.kt`, `DevToolsScreen.kt`, `DevRepository.kt`, `DevApi.kt`, `DevMissionService.java`, `DevController.java`, `DevMapper.java`, `MissionPublisher.java`, `MissionMapper.java` 는 모두 A2/A3/A9/A6·개발자 모드 작업이 같은 worktree 에 있던 파일이다. 현재 작업 트리가 깨끗한지 시작 전에 `git status` 로 확인하고, 다른 미커밋 변경이 보이면 건드리지 말고 보고서에 적는다. 병렬 작업자는 없다(Codex 단독).
