# A6 Android 하루 여러 개 미션 — 리뷰

대상: 미커밋 변경(A2·A3·A9·A6 혼재), `.agent/plan.md`, `.agent/report.md`. A6 범위를 중점 확인했다.

## 요구사항 충족 확인
- **데이터 층 일반화**: `MissionRepository.current()` 가 열린 미션 전체를 서버 순서로 변환(`CurrentMissionState.missions`, 기존 생성자·`mission` 접근자는 호환용으로 유지). `DataStore.applyCurrentMission` 이 **id 별로** `submissions/poked/완료 멤버/serverSubmittedByMe/feedLoaded` 를 보존하고 닫힌(서버 목록에 없는) 미션은 제거. `applyCurrentFeed`/`applySubmitted`/`markPoked` 가 `map` 으로 해당 id 만 교체하고 모르는 id 는 무시(`lastOrNull`/`listOf(...)`/`dropLast` 제거 확인). `applyFeed` 의 `isCurrent` 도 `any { id }` 로 변경.
- **조회 API**: `todayMissions(group)`, `todayMission(group, mission)` 추가, `todayMission(group)` 는 첫(최소 슬롯) 미션 — 서버 `todayMission` 규약과 일치하고 미션 1개에서는 기존과 동일. 데모(음수 id) 그룹만 과거 미션을 들고 있어 `takeLast(1)` 로 기존 `lastOrNull` 동작을 유지(프로덕션 그룹은 열린 미션만 보유).
- **홈**: `GroupSummaryDto.todayMissions` + 구서버 폴백(`todayMissions.ifEmpty { listOfNotNull(todayMission) }`), `StoredGroup.summaries` 목록화(`summary` 호환 getter), `GroupSummary.missionCount/mySubmittedCount`, `didSubmit` = 전부 제출. 카드는 미션 ≥ 2 일 때만 "외 N개"·"k / N 제출", 1개면 기존 문구(`homeMissionCopyChangesOnlyForMultipleMissions` 테스트).
- **05 화면**: 칩은 미션 ≥ 2 일 때만, 선택 id 가 사라지면 첫 미션으로 복귀(`selectedMission` + `LaunchedEffect`), 참여 현황·콕·잠금 안내·결과/수행 버튼 모두 선택 미션 기준, 콕 `sending` 키가 `(missionId, memberId, kind)` 라 미션별 독립, 응답 `missionId` 로 `markPoked`. 제출 후 `submittedMissionId` 로 그 미션 선택 + "N번째 미션을 올렸어요" (1개면 기존 문구). `savedStateHandle` 에 `submittedMissionId` 를 `submitted` 보다 먼저 설정해 신호 순서 경합 없음.
- **라우트/가드**: `MissionPerform.missionId`(기본 null → 첫 미션, 직렬화 호환), `Results` 가드가 `todayMission(group, mission)` 를 먼저 봐서 현재 열린 두 번째 미션도 달력 캐시 없이 열린다. 푸시 스냅샷은 제출한 마지막 미션 → 없으면 마지막 미션(페이로드에 missionId 가 없는 한계는 보고서에 명시).
- **콕 API**: `PokeRequestDto.missionId`(null 이면 `explicitNulls=false` 로 키 생략) 및 테스트.
- 백엔드/iOS/문서 변경 없음. 보고: 단위 테스트 178개·assemble·androidTest 패키징·lint 통과(JDK 17).

## P2
### File / Location
`android/app/src/main/java/com/sapphire/hawe/feature/records/RecordCalendarLogic.kt` `recordsByDate` (14~18행, "같은 날 기록이 둘이면 앞의 것을 써요")
### Problem
달력은 날짜당 한 기록만 보여 준다. 서버는 이미 미션별 행을 내려주므로, 하루 여러 개 미션 그룹에서 **지난 날의 2번째 이후 미션은 달력에서 열 수 없다**(날짜 칸 도장·선택 카드도 첫 미션만). 계획은 "달력 변경 없음, 확인만"으로 범위를 제한했고 Codex 도 보고서에 한계를 적었다.
### Why It Matters
오늘 미션은 05 칩으로 모두 열리지만, 하루가 지나면 나머지 미션의 결과/도장을 앱에서 볼 방법이 없어 "각각 열람" 목표가 과거 날짜에서는 충족되지 않는다. 미션 1개 그룹에는 영향 없음.
### Recommended Fix
이번 변경의 결함은 아니므로 A6 안에서 고치지 말고 후속 작업으로 분리: 날짜당 기록 목록(`Map<LocalDate, List<DayRecord>>`)과 선택 카드의 미션 선택(또는 칸에 여러 도장) UI 를 설계. 사용자 결정 필요(디자인 포함).

### File / Location
`android/app/src/test/.../core/data/DataStoreTest.kt` (`openMissionsKeepIndependentStateAndSummary` 1개), `GroupRepositoryTest`/`MissionRepositoryTest`/`PokeRepositoryTest`/`MissionFormatTest`/`GroupFormatTest` 각 1~2개
### Problem
다중 미션 로직의 핵심(`applyCurrentMission` id 별 보존, `applyFeed`/`applySubmitted`/`markPoked` 의 id 한정 갱신, 모르는 id 무시)이 한 테스트에 몰려 있다. 개별로는 통과하지만 회귀 시 어느 동작이 깨졌는지 구분이 어렵다. 05 화면의 칩·콜백 연결은 컴파일 외 검증이 없다.
### Why It Matters
이후 변경(예: 달력 후속, 우준의 다른 작업)이 `DataStore` 를 건드릴 때 실패 원인 파악이 어렵다. 치명적이진 않다.
### Recommended Fix
선택 사항: `applyCurrentMission`(보존/제거/신규)과 `applyFeed`/`markPoked`(해당 id 만 변경) 를 별도 `@Test` 로 나눈다.

### File / Location
worktree 전체 (A2 + A3 + A9 + A6 미커밋 혼재), 기기 미검증
### Problem
네 작업이 `MainNavigation.kt`, `DataStore.kt`, `GroupRepository.kt`, `MissionScreen` 등을 공유한 채 미커밋이고, 실제 서버·기기 검증이 전혀 없다(계측 테스트도 미실행).
### Why It Matters
한 커밋에 섞이면 되돌리기/원인 추적이 어렵고, 다중 미션은 서버에서 `dailyMissionCount ≥ 2` 그룹을 만들어야만 확인되는 흐름이 많다.
### Recommended Fix
(코드 수정 아님) 커밋은 사용자 몫: A2/A3/A9/A6 로 나눠 커밋하거나 메시지에 구분. 머지 전 `connectedDebugAndroidTest` 와 `dailyMissionCount=3` 그룹 수동 확인(칩 전환, 미션별 제출·결과, 홈 "k/N 제출", 미션별 콕, 슬롯 발행 후 칩 증가, 1개 그룹 UI 불변).

## 확인했지만 문제 없음
- 미션 1개 그룹 경로: 칩 행/번째 문구/외 N개 모두 `>= 2` 조건부, `submittedToastText(total <= 1)` 는 기존 문구, `todayMission(group)` 의미 동일.
- `performEntryDecision`/`PushModel` 은 변경 없음(계획대로).
- `applyCurrentMission` 이 홈 `summaries` 를 갱신하지 않는 것은 기존 동작과 같고, 홈은 진입 시마다 `loadAll` 로 다시 채운다.

결론: **승인 가능** (달력의 날짜당 다중 기록은 후속 과제로 분리, 머지 전 기기 수동 확인 권장)
