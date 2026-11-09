# A6. Android — 하루 여러 개 미션 (05 화면·홈·수행·콕)

> 범위: **Android 앱만**(`android/`). **전제: 백엔드 B7(하루 여러 개 개방)이 커밋된 뒤**, 그리고 앱 쪽은 A1(콕)·A4(새 틀) 뒤(같은 파일을 가장 많이 바꾸는 계획이라 마지막). 서버 `hawe.mission.max-daily-count` 가 5로 열리고 그룹이 `dailyMissionCount ≥ 2` 로 설정돼야 의미가 있다 — 그룹 설정 UI(하루 미션 수 변경)는 이번에 **만들지 않는다**(Figma 에 없음, 요구 없음. 기본 1개 유지, 다중 미션 그룹은 서버/관리 경로로 설정). iOS 는 A5. 구현자: Claude 또는 Codex. 디자인 없는 부분(여러 카드 세로 나열, "외 N개" 배지)은 기존 컴포넌트 조합 + 텍스트로 임시 구성.
> 빌드/테스트: JDK 21 — `set JAVA_HOME=C:\Users\kimch\.jdks\ms-21.0.10` 후 `android\gradlew.bat :app:testDebugUnitTest --offline`.

## Goal

한 그룹에 현재 열린 미션이 2개 이상일 때 앱이 모두 보여 주고 각각 수행·열람·콕이 동작한다. 미션이 1개인 그룹(기본)의 동작·화면은 **변경되지 않는다**.

## Findings

- 서버 계약(B7): `GET /api/groups/{groupId}/missions/today` 는 이미 `missions[]`(슬롯 순) 배열; 그룹 목록 항목에 `todayMissions: [{missionId, media, content, submittedCount, submittedByMe}…]` 추가(기존 `todayMission` = 가장 작은 슬롯 하나는 유지); 콕 요청 `{toUserId, type, missionId?}`(없으면 가장 작은 슬롯의 현재 미션); 도장은 미션별(달력은 이미 미션별 행), 피드 잠금은 미션별.
- 앱 현재 구조(단일 미션 가정):
  - `MissionRepository.current(groupId, myUserId)` 가 `response.missions.firstOrNull()` 만 `ServerMission` 으로 변환(`CurrentMissionState(mission: ServerMission?, nextPublishAt)`).
  - `DataStore.applyCurrentMission` 이 `group.copy(missions = listOfNotNull(current), …)` 로 **미션 한 개만** 저장; `StoredMission` 필드: id, type, topic, number, opensAt, closesAt, submissions, poked, deadlineAt, template, spec, serverMembers, serverSubmittedByMe, feedLoaded. `DataStore.todayMission(groupID)` 는 `stored.missions.lastOrNull()` 하나를 `TodayMission` 으로 변환. `applyFeed`/`applySubmitted` 도 `missions.lastOrNull()` 를 현재 미션으로 간주.
  - 화면: `MissionScreen(id, …)`(`feature/mission/MissionScreen.kt`)는 `store.todayMission(id)` 한 개의 `MissionCard` + 참여 현황; 수행 라우트 `MainRoute.MissionPerform(groupId, type)` 에 **missionId 없음**(그룹의 현재 미션으로 가정), 결과 `Results(groupId, missionId)`/`SubmissionDetail(…)` 은 이미 missionId 보유. 제출 `SubmissionRepository.submit(missionId, …)`.
  - 홈 카드: `GroupSummary.missionType/topic/submittedCount/didSubmit` (단일 `MissionSummary`; `GroupRepository.loadAll` 이 `summary.todayMission` 으로 채움).
  - 메뉴: 05 에서 수행/결과 버튼, 완료 후 결과 보기, `onSubmittedHandled` 등 단일 미션 상태 신호.
- 다중 미션에서 의미가 모호해지는 앱 개념: "오늘 미션을 올렸어요" 토스트, 홈 카드의 `didSubmit`(서버 `submittedByMe` = 첫 슬롯 기준), 달력 진입(이미 미션별).

## Root Cause / Key Implementation Points

1. **데이터 모델**: `CurrentMissionState(missions: List<ServerMission>, nextPublishAt)`; `MissionRepository.current` 가 `missions` 전체를 변환(슬롯 순 유지). `DataStore.applyCurrentMission` 이 `missions = 변환된 목록`(이전 상태 `submissions/poked/feedLoaded` 는 **미션 id 별로** 보존)을 저장. `todayMission(groupID)` 는 **첫 미션**을 계속 돌려주고(호환), 새 `todayMissions(groupID): List<TodayMission>` 추가. `applyFeed`/`applySubmitted`/`poke` 는 `missionID` 로 미션을 찾도록 일반화(`lastOrNull` 제거, 해당 id 없으면 무시).
2. **05 화면**: 미션이 1개면 현재 UI 그대로. 2개 이상이면 위쪽에 미션 선택 칩/탭 행(`1번째`·`2번째`… 또는 `number` 기반, 완료 여부 체크 표시, 임시 UI)과 선택된 미션의 `MissionCard` + 참여 현황을 보여 준다. 선택 상태는 화면 `rememberSaveable`. 아직 발행 전인 슬롯은 칩에 나타나지 않는다(서버가 아직 내려주지 않음) — `nextPublishAt` 카운트다운은 "다음 미션까지 N시간" 로 하단에 계속 표시.
3. **수행 라우트**: `MainRoute.MissionPerform(groupId, missionId: Long? = null, type: String? = null)` 로 `missionId` 추가(기본 null=첫 미션, 직렬화 호환), `MainNavigation` 수행 목적지가 해당 미션의 `template/spec/topic` 으로 `MissionPerformScreen` 호출, 제출 성공 시 해당 미션만 `applySubmitted`. 제출 후 이동/토스트는 기존 흐름 유지(미션 번호 표시 "N번째 미션을 올렸어요").
4. **홈 카드**: 단일 요약 유지 + 서버 `todayMissions` 가 2개 이상이면 카드에 "외 N개" 임시 배지와 완료 수(`k/N`). `GroupSummaryDto` 에 `todayMissions: List<TodayMissionSummaryDto> = emptyList()` 추가, `MissionSummary` 에 `extraCount`/`submittedOfTotal` 필요한 최소 필드만 추가. 홈 카드 탭은 기존대로 05 로 이동.
5. **콕(A1 위)**: 요청에 `missionId`(05 에서 선택된 미션) 포함. `PokeRepository.send(groupId, toUserId, kind, missionId)`; 서버 409 `NO_OPEN_MISSION`(그 사이 닫힘) → 새로고침.
6. **결과/상세**: 이미 missionId 기반이라 변경 최소 — `Results` 진입 버튼이 선택된 미션 id 를 쓰도록만 연결.
7. **달력**: 변경 없음(서버가 미션별 도장을 이미 내려줌). 같은 날 여러 행이 날짜 칸에 올바르게 쌓이는지만 확인(`RecordCalendarLogic`).
8. **문구**: "오늘의 미션" 문구는 1개일 때만 유지, 2개 이상이면 "오늘의 미션 (N번째)" 형태(임시).

## Files / Functions to Change

| 파일 | 변경 |
|---|---|
| `core/net/MissionRepository.kt`, `MissionApi.kt`(변경 없음 또는 필드 보강) | `missions` 전체 변환 |
| `core/net/GroupApi.kt`, `GroupRepository.kt`, `core/model/GroupSummary.kt`(`MissionSummary`) | `todayMissions`, 홈 요약 확장 |
| `core/data/DataStore.kt` | `applyCurrentMission` 다중 보존, `todayMissions`, `applyFeed/applySubmitted/markPoked` 일반화, `StoredGroup.missions` 의미 변경(현재 미션 전체) |
| `core/model/TodayMission.kt` | (필요 시) 변경 없음 — `TodayMission` 은 미션 단위 |
| `feature/mission/MissionScreen.kt`, `MissionFormat.kt` | 선택 칩, 번호 문구 |
| `app/MainNavigation.kt` | `MissionPerform.missionId`, 수행/결과 연결, 홈 카드 |
| `feature/groups/HomeScreen.kt` | "외 N개" 배지 |
| `core/net/PokeRepository.kt`(A1) | `missionId` 인자 |
| 테스트 | `MissionRepositoryTest`(다중 매핑·순서), `DataStoreTest`(다중 보존/갱신, id 기반 `applyFeed`/`applySubmitted`, 미션 1개 회귀), `GroupRepositoryTest`(`todayMissions` 매핑), `MissionFormatTest`(번호 문구), 선택 칩 로직 순수 함수 단위 테스트 |

## Implementation Steps

1. 모델/레포지토리 다중화 + **미션 1개일 때 기존 테스트가 그대로 통과**하는지 먼저 확인. 2. `DataStore` id 기반 일반화. 3. 라우트 `missionId`. 4. 05 화면 칩·카드. 5. 홈 배지. 6. 콕 `missionId`. 7. 전체 테스트. 8. 서버(B7 적용, `dailyMissionCount=3`)에서 수동 확인은 사용자.

## Constraints

- `android/` 만. **미션 1개 그룹의 동작/화면 불변**. 그룹 설정 UI(하루 미션 수)·알림·푸시 추가 금지. 서버 값이 진실(앱이 슬롯/마감을 계산하지 않음). 새 라이브러리 금지. 커밋/푸시 금지.

## Edge Cases

- 미션 2→3 으로 늘어나는 도중(새 슬롯 발행) 새로고침 시 선택 상태 유지(선택한 미션 id 가 사라지면 첫 미션으로).
- 한 슬롯이 닫히고 새 미션이 열려 id 가 바뀜 → 이전 `submissions/feedLoaded` 는 id 가 다르므로 폐기, 같은 id 면 보존.
- 모든 슬롯 중 일부만 제출 → 칩에 완료 표시가 미션별, 홈 배지 `k/N`.
- 같은 틀이 한 날에 두 번 나오는 경우(서버가 방지하지만 불가피할 때) UI 가 id 로 구분(topic 으로 구분하지 않음).
- 수행 화면 진입 후 그 미션이 닫힘 → 제출 시 `MISSION_CLOSED` 처리(기존 `classifySubmitFailure`)로 05 복귀.
- 지각 표시/마감 시각은 미션별.

## Regression Risks

- `StoredGroup.missions` 를 `listOf(single)` 로 가정한 코드(`lastOrNull`, `dropLast(1)`, `listOf(mission)` 복사)가 `DataStore` 곳곳에 있다 — grep 으로 전수 점검: `rg "missions\.(last|first)|listOf\(mission|dropLast" android/app/src/main`.
- `MissionPerform` 라우트 변경 시 저장된 백스택/딥링크(푸시 `PushCenter`)가 이 라우트를 쓰는지 확인(기본값 null 로 호환).
- 목업(`DemoSeed`)과 서버 데이터가 섞이지 않게(서버 연결 후 미사용 경로 유지).

## Tests Required

위 표 + 기존 `DataStoreTest`/`MissionRepositoryTest` 전부 통과(미션 1개 회귀 보호). 화면은 컴파일 + 수동 확인.

## Validation Steps

`android\gradlew.bat :app:testDebugUnitTest --offline`(JDK 21) 통과 기록, 위 `rg` 점검 결과 보고, 서버 연동 수동 확인 체크리스트(3개 미션 그룹: 05 칩 전환, 각 미션 제출·결과, 홈 배지, 콕) 보고서에 포함.

## Potential Merge Conflicts

`DataStore.kt`, `MissionScreen.kt`, `MissionRepository.kt`, `MainNavigation.kt`, `HomeScreen.kt`, `GroupRepository.kt`(A1·A3·A4 와 겹침 — **마지막에 진행**).
