# A6. Android — 하루 여러 개 미션 (05 화면 · 홈 · 수행 · 콕 · 푸시)

> 이전 `.agent/plan.md`(A9)는 덮어쓴다. 원본 계획: `docs/해야될거/2번_우준/A6-android-multi-mission.md` (이 파일이 코드 확인 후의 최신판).
> 범위: **`android/` 만**. 서버(B7 — 하루 여러 개 개방)는 이미 구현돼 있다. iOS·알림 설정·그룹 설정 UI(하루 미션 수 변경)는 **만들지 않는다**. 디자인(Figma) 없음 → 기존 컴포넌트 조합 + 텍스트의 임시 UI.
> 전제: A2·A3·A9 변경이 worktree 에 **미커밋으로 섞여 있다**(`MainNavigation.kt`, `AppViewModel.kt`, `RootScreen.kt`, `GroupInfoScreen.kt` 등). **덮어쓰거나 되돌리지 말 것.**
> 빌드/테스트: JDK 21 권장(없으면 설치된 JDK 17 로도 빌드됨). `android\gradlew.bat :app:testDebugUnitTest --offline`.

## Goal

한 그룹에 현재 열린 미션이 2개 이상일 때 앱이 모두 보여 주고, 각각 **수행·열람·콕**이 동작한다. **미션이 1개인 그룹(기본)의 동작·화면은 변하지 않는다**(기존 테스트가 그대로 통과해야 함).

## Findings (코드로 확인함)

경로 접두: 앱 `J/` = `android/app/src/main/java/com/sapphire/hawe/`, 테스트 `T/` = `android/app/src/test/java/com/sapphire/hawe/`, 서버 `S/` = `src/main/java/com/sapphire/hawe/`.

### 서버 계약 (모두 구현됨)
- `GET /api/groups/{id}/missions/today` → `{date, dailyMissionCount, nextPublishAt, missions:[MissionResponse…]}` (`S/mission/service/MissionService.java` `getTodayMissions`): `missions` 는 **현재 열려 있는 미션만, 슬롯 오름차순**(`findCurrentByGroupId`). `nextPublishAt` 은 모든 슬롯 중 가장 빠른 다음 발행 시각이라 항상 값이 있다(`nextPublishAt()` 209행~). 앱 `MissionDto`(`J/core/net/MissionApi.kt`)는 이미 `slotNo, number, submittedByMe, members[]` 를 받는다.
- `GET /api/groups` 항목: 기존 `todayMission`(가장 작은 슬롯 하나) + `todayMissions: [{missionId, media, content, submittedCount, submittedByMe}…]`(`S/group/dto/GroupSummaryResponse.java`, 슬롯 순). 앱 `GroupSummaryDto`(`J/core/net/GroupApi.kt` 7~10행)에는 `todayMission` 만 있다.
- 콕 `POST /api/groups/{id}/pokes` 요청 `{toUserId, type, missionId?}`(`S/poke/dto/PokeRequest.java`): `missionId` 없으면 가장 작은 슬롯의 현재 미션, 있으면 **그 그룹의 현재(열린) 미션**이어야 하고 아니면 409 `NO_OPEN_MISSION`(`PokeService` 46~50행). 응답 `{id, toUserId, type, missionId, sentAt}`.
- 제출 `POST /api/missions/{missionId}/submissions`(앱 `SubmissionRepository.submit(missionId, …)` 이미 미션 id 기반). 도장·피드 잠금은 미션별. 달력은 이미 미션별 행.

### 앱 — 단일 미션 가정이 박혀 있는 곳 (전수)
- `J/core/net/MissionRepository.kt`: `CurrentMissionState(mission: ServerMission?, nextPublishAt)`; `current()` 가 `response.missions.firstOrNull()?.toModel()` 만 변환. `ServerMission` 에는 `slotNo` 가 없다(순서는 리스트 순서로 충분).
- `J/core/data/DataStore.kt`:
  - `StoredGroup(detail, missions: List<StoredMission>, summary: MissionSummary?, nextPublishAt, missionState)`; `StoredMission(id, type, topic, number, opensAt, closesAt, submissions, poked, deadlineAt, template, spec, serverMembers, serverSubmittedByMe, feedLoaded)`.
  - `applyCurrentMission`(≈109행): `previous = group.missions.lastOrNull()?.takeIf { it.id == row?.id }`, 결과 `missions = listOfNotNull(current)` — **미션 하나만 저장**.
  - `todayMission(groupID)`: `missions.lastOrNull()` 하나를 `TodayMission` 으로 변환. `applyFeed`(`isCurrent = …missions?.lastOrNull()?.id == missionID`), `applyCurrentFeed`(`lastOrNull` → `listOf(mission.copy(…))` 로 **목록을 하나로 덮어씀**), `applySubmitted`(`lastOrNull` → `missions = listOf(updated)`, `summary` 갱신은 `summary.missionId == missionID` 일 때만), `markPoked`(`lastOrNull`, `dropLast(1) + …`), `needsMissionSpec`(이미 `none { id }`), `result()`(이미 `firstOrNull { id }`), `markMissionFailed`, `replaceMissionsForTest`, `summaries`(홈 요약 = `summary` 단일), `applyServerGroups`(`previous?.missions` 보존, `loaded.mission` → `summary`).
- `J/core/net/GroupRepository.kt`: `data class MissionSummary(missionId, type, topic, submittedCount, submittedByMe)`, `LoadedGroup(detail, mission: MissionSummary?)`, `loadAll` 이 `summary.todayMission` 으로 채움.
- `J/core/model/GroupSummary.kt`: `GroupSummary(id, name, relationship, members, startDate, missionType, topic, submittedCount, didSubmit)` 단일 미션 요약. `J/feature/groups/HomeScreen.kt` `GroupCard`: `group.missionType == null` 이면 "아직 오늘의 미션이 열리지 않았어요", 아니면 "오늘의 미션 · 유형" + 주제 + "k / N명 완료" + (`didSubmit` ? "열어보기" : "내 미션 남음").
- `J/core/model/TodayMission.kt`: 미션 단위 모델(`id, group, type, topic, number, opensAt, closesAt, members, deadlineAt, template, spec`, `doneCount`, `didSubmit`). 변경 불필요.
- `J/feature/mission/MissionScreen.kt`(277줄): `val mission = store.todayMission(id)` 하나, `MissionCard(mission, onPerform = { onPerform(null) }, onResults = onResults)`, 참여 현황·콕 행, 잠금 안내. 콜백 시그니처 `onPerform: (MissionType?) -> Unit`, `onResults: () -> Unit`. 새로고침: 진입/`ON_RESUME`/`nextPublishAt` 도달 시 `missions.current` → `store.applyCurrentMission`. 제출 토스트 "오늘의 미션을 올렸어요"/"지각으로 올렸어요"(`submittedSignal`, `submittedLate`). 콕: `sendPoke(missionID, member, kind)` 가 `pokes.send(id, member.id, kind)`(missionId 없음) → 응답 `sent.missionId` 로 `store.markPoked(id, sent.missionId, …)`; 실패 시 `classifyPokeFailure`(`NO_OPEN_MISSION` → REFRESH).
- `J/app/MainNavigation.kt`: `MainRoute.MissionPerform(groupId, type: String? = null)`(72행, **missionId 없음**), `Mission` composable 의 `onPerform = { type -> nav.navigate(MissionPerform(id, type?.name)) }`, `onResults = { store.todayMission(id)?.let { nav.navigate(Results(id, it.id)) } }`; `MissionPerform` composable 은 `store.todayMission(id)` + `performEntryDecision(mission, requestedType)`, 제출은 `submissions.submit(mission.id, …)` → `store.applySubmitted(id, mission.id, submitted)` → `previousBackStackEntry.savedStateHandle["submitted"/"submittedLate"] = …`; `Results` composable 의 가드: `current = store.todayMission(groupID)`, `canOpen = if (current?.id == missionID) current.didSubmit else store.calendarRecord(groupID, missionID)?.didSubmit == true` (**현재 미션이 둘 이상이면 두 번째 미션이 달력 경로로 빠져 막힘**). 푸시 스냅샷(≈105행): `group.missions.lastOrNull()` → `PushGroupSnapshot(group.id, last?.id, last?.serverSubmittedByMe == true)`.
- `J/feature/mission/MissionPerformCommon.kt` `performEntryDecision(mission: TodayMission?, requestedType)`; `MissionPerformScreen`(topic/template/spec 받음). `J/core/push/PushModel.kt` `pushTarget(destination, groups: List<PushGroupSnapshot>)`: COMMENT 푸시이고 `lastMissionSubmittedByMe` 면 `Results(group.id, lastMissionId)`, 아니면 `Mission(group.id)`.
- `J/core/net/PokeRepository.kt` `send(groupId, toUserId, kind): SentPoke`, `PokeApi.kt` `PokeRequestDto(toUserId, type)`.
- 영향받는 기존 테스트(수정 필요 가능): `T/core/data/DataStoreTest.kt`, `T/core/net/GroupRepositoryTest.kt`, `T/core/net/MissionRepositoryTest.kt`(`current()`/`CurrentMissionState` 사용), `T/core/net/PokeRepositoryTest.kt`, `T/core/push/PushModelTest.kt`, `T/feature/groups/GroupFormatTest.kt`, `T/feature/mission/MissionPerformCommonTest.kt`. 수정 전 `rg "CurrentMissionState|\.mission\b|LoadedGroup\(|MissionSummary\(|replaceMissionsForTest|todayMission\(|\.summary\b" android/app/src` 로 호출부 전수 확인.

## Root Cause / Key Implementation Points

앱 전체가 "그룹당 현재 미션 = 1개"를 가정한다. 데이터 층을 목록으로 일반화하고, 화면은 "선택된 미션"을 기준으로 동작시키며, **미션이 1개면 기존 UI 경로와 동일**하게 한다.

1. **레포지토리/모델** (`MissionRepository.kt`):
   - `CurrentMissionState(missions: List<ServerMission>, nextPublishAt: Instant?)` 로 변경. `current()` 가 `response.missions.map { it.toModel() }`(슬롯 순 유지). 기존 호출부(`MissionScreen`, 테스트)는 `missions.firstOrNull()` 로 대응.
2. **DataStore 일반화** (`DataStore.kt`):
   - `applyCurrentMission`: 새 `state.missions` 각각에 대해 **같은 id 의 이전 `StoredMission`** 이 있으면 `submissions/poked/serverMembers(완료만)/serverSubmittedByMe/feedLoaded` 보존(기존 합치기 규칙 그대로, id 별로), 없으면 새로 생성. 결과 `missions = 변환 목록`(서버 순서). 서버 목록에 없는(닫힌) 미션은 버린다.
   - `todayMissions(groupID): List<TodayMission>`(전체, 슬롯 순) 추가 + `todayMission(groupID, missionID): TodayMission?` 추가. 기존 `todayMission(groupID)` 는 **첫 번째 미션**(= 가장 작은 슬롯, 서버 `todayMission` 규약과 동일)을 돌려준다(기존 `lastOrNull` 은 미션 1개일 때 같은 값). 내부 변환은 공통 private 함수로 분리(`TodayMission` 생성 로직 복제 금지).
   - `applyFeed`: `isCurrent = missions.any { it.id == missionID }`. `applyCurrentFeed`/`applySubmitted`/`markPoked`: **목록에서 id 로 찾아 그 항목만 `map` 으로 교체**(`lastOrNull`/`listOf(...)`/`dropLast(1)` 제거), id 가 없으면 변경 없음.
   - 홈 요약: `StoredGroup.summary: MissionSummary?` → **`summaries: List<MissionSummary>`**(슬롯 순). `applyServerGroups` 가 `loaded.missions` 를 넣는다. `applySubmitted` 는 `summaries` 중 `missionId == missionID` 항목을 `submittedByMe = true`, `submittedCount` +1(이미 내가 제출이면 +0)로 갱신. 호환을 위해 `val summary: MissionSummary? get() = summaries.firstOrNull()` 를 두면 기존 호출부 수정이 적다(필요한 곳만 사용).
   - `summaries`(GroupSummary 목록) 계산: 첫 요약으로 `missionType/topic/submittedCount` 를 채우고, 새 필드 `missionCount = summaries.size`, `mySubmittedCount = summaries.count { it.submittedByMe }`, `didSubmit = summaries.isNotEmpty() && mySubmittedCount == missionCount`. (미션 1개면 기존과 같은 값.)
   - `replaceMissionsForTest`, `DataStore.preview`, `DemoSeed` 의 `StoredGroup(...)` 생성부를 새 필드에 맞게 수정(데모/프리뷰는 기존과 동일하게 동작).
3. **홈 요약 데이터** (`GroupApi.kt`, `GroupRepository.kt`, `GroupSummary.kt`, `HomeScreen.kt`):
   - `GroupSummaryDto` 에 `todayMissions: List<TodayMissionSummaryDto> = emptyList()` 추가. `LoadedGroup(detail, missions: List<MissionSummary>)`; `loadAll` 은 `summary.todayMissions` 가 비어 있고 `todayMission` 이 있으면 `[todayMission]` 으로 폴백(구서버 호환), 둘 다 없으면 빈 목록. 기존 `MissionSummary` 데이터 클래스는 그대로 재사용.
   - `GroupSummary` 에 `missionCount: Int = 0`, `mySubmittedCount: Int = 0` 추가(기본값으로 기존 생성부 호환). `GroupCard`: 미션 ≥ 2 이면 주제 위 라벨을 "오늘의 미션 · 유형 · 외 {N-1}개" 로 하고, 하단 상태는 "{내 제출 수} / {미션 수} 제출" 형태를 완료 인원 문구 옆에 추가(임시), 배지는 `didSubmit`(전부 제출) ? "열어보기" : "내 미션 남음". **미션 ≤ 1 이면 현재 문구 그대로.** 카드 탭은 기존대로 05 로 이동.
4. **05 화면** (`MissionScreen.kt`, `MissionFormat.kt`):
   - `val missions = store.todayMissions(id)`. 선택 상태 `var selectedId by rememberSaveable(id) { mutableStateOf<Long?>(null) }`; 실제 표시 미션 = `missions.firstOrNull { it.id == selectedId } ?: missions.firstOrNull()` (선택한 id 가 사라지면 첫 미션으로). **미션이 1개면 칩 행을 그리지 않고 현재 UI 그대로.**
   - 2개 이상: `HNavBar` 아래에 가로 칩 행(임시 UI — `Row` + 둥근 `Box`, 선택은 `ink`/`bg` 반전, 내가 제출한 미션은 체크 `HIcon.CHECK_SMALL`), 칩 라벨은 리스트 순서의 "1", "2"… (`number` 는 그룹 누적 번호라 라벨에 쓰지 않음). `MissionCard` 의 헤더에 "{index+1}번째" 를 표시(`#number` 유지). 칩 선택은 `selectedId` 만 바꾼다(재조회 없음).
   - 콜백: `onPerform: (missionId: Long, type: MissionType?) -> Unit`, `onResults: (missionId: Long) -> Unit` 로 변경(선택된 미션 id 전달). `MainNavigation` 의 호출부 갱신.
   - 참여 현황·콕 행·잠금 안내는 **선택된 미션**의 `members`/`didSubmit` 기준. `sendPoke(missionID, member, kind)` 는 `pokes.send(id, member.id, kind, missionID)`; `sending` 키를 `(memberId, kind)` 에서 `(missionId, memberId, kind)` 로 확장(미션별 독립). `classifyPokeFailure` 로 `NO_OPEN_MISSION`/`TARGET_ALREADY_SUBMITTED` → `refresh++` 는 그대로.
   - 제출 후 복귀: `savedStateHandle` 에 `submittedMissionId: Long`(수행 화면이 설정) 추가. `MissionScreen` 은 `submittedMissionId` 를 받으면 그 미션을 선택하고, 미션이 2개 이상이면 토스트를 "{N}번째 미션을 올렸어요"/"{N}번째 미션을 지각으로 올렸어요", 1개면 기존 "오늘의 미션을 올렸어요"/"지각으로 올렸어요"(순수 함수 `submittedToastText(index, total, late)` 로 `MissionFormat.kt` 에 두고 테스트).
   - 새로고침/카운트다운: 기존 `LaunchedEffect(id, nextPublishAt)`(서버 `nextPublishAt` 도달 시 3회 재조회)로 새 슬롯이 열리면 칩이 늘어난다 — 변경하지 않는다. 미션이 없을 때 빈 상태 문구·카운트다운도 그대로. 카드 안의 `MissionCountdown(closesAt, deadlineAt)` 는 미션별 값이라 그대로.
5. **수행 라우트** (`MainNavigation.kt`, `MissionPerformCommon.kt`):
   - `MainRoute.MissionPerform(groupId: Long, type: String? = null, missionId: Long? = null)` — 기본값 null 이면 첫 미션(직렬화/기존 백스택 호환). composable 이 `val mission = route.missionId?.let { store.todayMission(id, it) } ?: store.todayMission(id)`; 이후 `performEntryDecision`/제출은 그 `mission` 기준(이미 `mission.id` 사용). 제출 성공 시 `previousBackStackEntry.savedStateHandle["submittedMissionId"] = mission.id` 추가(기존 `submitted`, `submittedLate` 유지). `MISSION_CLOSED`/`AlreadySubmitted` 분기 동작 불변.
   - `performEntryDecision` 시그니처는 변경하지 않는다(미션을 인자로 받음).
6. **결과 연결** (`MainNavigation.kt`): `Mission` composable `onResults = { missionId -> nav.navigate(Results(id, missionId)) }`. **`Results` 가드**: `current = store.todayMission(groupID, missionID)`; `canOpen = if (current != null) current.didSubmit else store.calendarRecord(groupID, missionID)?.didSubmit == true`.
7. **푸시** (`MainNavigation.kt` ≈105행, `PushModel.kt`): 스냅샷은 `group.missions` 중 `serverSubmittedByMe` 인 가장 뒤 미션(없으면 마지막 미션)으로 `PushGroupSnapshot(group.id, thatMission?.id, thatMission?.serverSubmittedByMe == true)` 를 만든다. `PushModel.kt` 의 모델·`pushTarget` 은 **변경하지 않는다**. (푸시 페이로드에 missionId 가 없어 정확한 미션 매핑은 불가 — 이 한계를 보고서에 기록.)
8. **콕 API**: `PokeApi.kt` `PokeRequestDto(toUserId, type, missionId: Long? = null)`(`explicitNulls=false` 라 null 이면 키 생략), `PokeRepository.send(groupId, toUserId, kind, missionId: Long? = null)`.
9. **달력**: 변경 없음. 같은 날 미션 여러 행이 한 날짜 칸에서 어떻게 합쳐지는지(`recordsByDate`, `J/feature/records/RecordCalendarLogic.kt`)만 읽어서 확인하고, 문제가 있으면 보고서에 적기만 한다(필요한 최소 수정이 아니면 손대지 않음).

## Files / Functions to Change

| 파일 | 변경 |
|---|---|
| `J/core/net/MissionRepository.kt` | `CurrentMissionState.missions`, `current()` 전체 변환 |
| `J/core/net/GroupApi.kt`, `GroupRepository.kt` | `todayMissions` DTO, `LoadedGroup.missions`, 폴백 |
| `J/core/model/GroupSummary.kt` | `missionCount`, `mySubmittedCount` |
| `J/core/data/DataStore.kt`, `DemoSeed.kt` | 목록 보존/갱신, `todayMissions`, `todayMission(group, mission)`, id 기반 `apply*`/`markPoked`, `summaries` 목록화 |
| `J/feature/mission/MissionScreen.kt`, `MissionFormat.kt` | 선택 칩, 선택 미션 기준 UI, 콜백 시그니처, 토스트 문구 |
| `J/feature/groups/HomeScreen.kt` | 다중 미션 카드 문구 |
| `J/app/MainNavigation.kt` | `MissionPerform.missionId`, 수행/결과 연결, `Results` 가드, 푸시 스냅샷 |
| `J/core/net/PokeApi.kt`, `PokeRepository.kt` | `missionId` |
| 테스트 | 아래 Tests Required |

## Implementation Steps

1. `MissionRepository`/`CurrentMissionState` 다중화 + 기존 테스트 수정 → 미션 1개 동작 동일 확인. 2. `DataStore` 일반화(`todayMissions`, id 기반 갱신, `summaries` 목록화) + `DataStoreTest` 정비·추가. 3. `GroupApi`/`GroupRepository`/`GroupSummary`/`HomeScreen`. 4. `PokeApi`/`PokeRepository`. 5. `MissionScreen` 선택 칩·콜백·토스트, `MissionFormat`. 6. `MainNavigation`(`MissionPerform.missionId`, `Mission` 콜백, `Results` 가드, 푸시 스냅샷). 7. `rg` 전수 점검 → 전체 단위 테스트·assemble(·lint).

## Constraints

- `android/` 만 수정(`docs/` 포함 다른 곳 금지). 백엔드·iOS 변경 금지. 새 라이브러리 금지. 커밋/푸시 금지.
- **미션 1개 그룹의 화면·동작·문구 불변**(칩 행·"외 N개"·번째 문구는 미션 ≥ 2 일 때만).
- 서버 값이 진실: 슬롯 번호·마감 시각을 앱이 계산하지 않는다. 그룹 설정 UI(하루 미션 수)·알림·푸시 페이로드 변경 금지.
- A2/A3/A9 변경 보존. `MainNavigation.kt`·`MissionScreen.kt` 수정은 위 목적에 한정.
- 코드 스타일은 주변(짧은 DTO, 한국어 문구, 한 줄 `@Test`)에 맞춘다. 임시 UI 는 코드 주석 `임시 UI` 로만 표기.

## Edge Cases

- 미션 수가 2→3 으로 늘어남(새 슬롯 발행) / 3→2 로 줄어듦(슬롯 마감): 새로고침 후 선택 id 가 목록에 없으면 첫 미션으로. 같은 id 는 `submissions/feedLoaded/poked` 보존, 다른 id 는 폐기.
- 일부만 제출: 칩 체크는 미션별, 홈은 "k/N 제출" + "내 미션 남음".
- 같은 틀이 한 날에 두 번: UI·상태 모두 id 로 구분(주제 문자열로 구분 금지).
- 수행 화면에서 그 미션이 닫힘 → 제출 시 `MISSION_CLOSED`(기존 `classifySubmitFailure`) → 05 복귀 + 새로고침.
- 선택 미션 B 에서 콕 → 응답 `missionId` 로 B 의 `poked` 갱신(A 에는 영향 없음). 콕 도중 B 가 닫힘 → 409 `NO_OPEN_MISSION` → `refresh++`.
- `ResultsScreen` 에서 두 번째 미션 열기: `Results` 가드가 현재 미션 목록을 먼저 보므로 달력 캐시 없이도 열림(내가 제출한 경우), 제출 안 했으면 막힘.
- 구서버(`todayMissions` 없음) 폴백, `missions` 가 빈 목록(미개방), 앱이 모르는 `media`/`template` 미션(`type == null`) 칩·카드 처리(기존 `UNSUPPORTED_MISSION_TEXT` 경로 유지).
- 프로세스 복원: `selectedId` 는 `rememberSaveable`, `MissionPerform.missionId` 는 직렬화 기본값 null 이라 옛 백스택과 호환.

## Regression Risks

- `StoredGroup.missions`/`summary` 를 `listOf(single)`·`lastOrNull`·`dropLast` 로 다루던 코드를 빠뜨리면 두 번째 미션 상태가 사라지거나 덮어써진다 → `rg "missions\.(last|first)|listOf\(mission|dropLast|\.summary\b|lastOrNull" android/app/src/main` 로 전수 점검하고 결과를 보고서에 기록.
- `MissionScreen` 콜백 시그니처 변경 → 호출부(`MainNavigation`, 프리뷰, 테스트) 컴파일.
- `todayMission(groupID)` 의 의미가 `lastOrNull` → `firstOrNull` 로 바뀐다(미션 1개에서는 동일). 사용처: `MissionScreen`, `MainNavigation`(Results 가드·수행·결과), `DataStore.preview`.
- `MainRoute.MissionPerform` 필드 추가(기본값 있음) → 직렬화 호환 확인, 푸시 딥링크는 `Mission` 라우트만 사용.
- 홈 `GroupSummary` 필드 추가는 기본값으로 호환. `GroupFormatTest`/`HomeScreen` 미션 1개 문구 불변.
- `SubmissionRepository`·`DataStore` 는 우준의 다른 Android 작업과 겹칠 수 있으나 A4/A7/A8 은 이미 커밋돼 충돌 가능성은 낮음.

## Tests Required

JVM 단위 테스트(`T/…`) — 기존 테스트는 **미션 1개 회귀 보호용으로 그대로 통과**해야 하며, 시그니처 변경분만 최소 수정:
- `MissionRepositoryTest`(MockWebServer): 여러 미션 매핑·순서(slotNo 순 유지), 빈 목록, 단일 미션 회귀.
- `DataStoreTest`: 다중 미션 `applyCurrentMission`(같은 id 는 `submissions/poked/feedLoaded/serverSubmittedByMe` 보존, 새 id 는 새로 생성, 사라진 id 는 제거), `todayMissions` 순서, `todayMission(group, mission)`, `applyFeed`/`applySubmitted`/`markPoked` 가 **해당 id 만** 바꾸고 나머지는 보존, 모르는 id 무시, 미션 1개 회귀, 홈 `summaries`(`missionCount`, `mySubmittedCount`, `didSubmit` 전부 제출 기준) 및 `applySubmitted` 가 요약 갱신.
- `GroupRepositoryTest`: `todayMissions` 매핑(2개), `todayMissions` 비고 `todayMission` 만 있는 폴백, 둘 다 없음.
- `PokeRepositoryTest`: 요청 본문에 `missionId` 포함(지정 시)/생략(미지정 시).
- `MissionFormatTest` 또는 신규: `submittedToastText`(1개/2개 이상/지각), 선택 미션 해석 순수 함수(선택 id 존재/없음 → 첫 미션)를 순수 함수로 분리해 테스트.
- `GroupFormatTest`/신규: 홈 카드 문구 순수 함수가 있다면(미션 1개 불변, ≥2 "외 N개") 테스트 — 문구 생성을 `GroupFormat.kt` 의 순수 함수로 분리하는 편이 테스트하기 쉽다.
- `PushModelTest`: 변경 없음(모델 불변) — 스냅샷 선택 로직을 별도 순수 함수로 뺀다면 그 테스트 추가.
- UI 테스트 없음(컴파일 + 수동 확인).

## Validation Steps

1. `android\gradlew.bat :app:testDebugUnitTest --offline` 전체 통과(출력 요약 기록). 가능하면 `:app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline`.
2. 위 Regression Risks 의 `rg` 점검 결과를 보고서에 포함. `rg "COMING_SOON"` 에 신규 사용이 없는지 확인.
3. 사용자 수동 확인(서버+기기, 미검증이면 "미검증" 명시): 서버에서 `dailyMissionCount=3`(관리 경로로 설정) 그룹으로 — 05 칩 전환, 각 미션 제출→"N번째 미션을 올렸어요", 각 미션 결과 열기, 홈 카드 "외 N개"/"k/N 제출", 미션별 콕(다른 미션 선택 후 콕), 슬롯 발행 시각 경과 후 칩 증가, 미션 1개 그룹 UI 불변.

## Potential Merge Conflicts

- `MainNavigation.kt`, `MissionScreen.kt`, `DataStore.kt`, `GroupRepository.kt`, `HomeScreen.kt`: A2·A3·A9 미커밋 변경과 같은 파일(특히 `MainNavigation.kt`·`DataStore.kt`). 변경 범위를 이 계획에 한정하고 기존 변경을 보존.
- 우준의 후속 작업이 없다면 A6 이 Android 마지막 항목. iOS 이식(A5 항목 7)은 별도.
