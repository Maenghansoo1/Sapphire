# A6 Android multi mission implementation report

## Files changed
- API and models: `android/app/src/main/java/com/sapphire/hawe/core/net/MissionRepository.kt`, `GroupApi.kt`, `GroupRepository.kt`, `PokeApi.kt`, `PokeRepository.kt`; `core/model/GroupSummary.kt`.
- State and UI: `core/data/DataStore.kt`; `feature/groups/GroupFormat.kt`, `HomeScreen.kt`; `feature/mission/MissionFormat.kt`, `MissionScreen.kt`; `app/MainNavigation.kt`.
- Tests: `android/app/src/test/java/com/sapphire/hawe/core/net/MissionRepositoryTest.kt`, `GroupRepositoryTest.kt`, `PokeRepositoryTest.kt`; `core/data/DataStoreTest.kt`; `feature/groups/GroupFormatTest.kt`; `feature/mission/MissionFormatTest.kt`.

## What changed
- The current mission response and in memory group state now retain every open mission in server order. Refresh merges submissions, completed members, sent pokes, and loaded feeds by mission ID; closed missions leave the current list.
- Group summaries use `todayMissions`, with a fallback to the legacy `todayMission` field. Home shows the extra mission count and my submission progress for groups with at least two missions.
- The mission screen selects a mission by ID. Chips, member progress, pokes, performance, result opening, and return to the submitted mission follow that selection. One mission groups retain their existing screen and messages.
- Mission specific pokes include `missionId`. Results checks the requested current mission before consulting the calendar. Push routing selects the latest current mission I submitted, or the last current mission.
- Kept compatibility constructors/accessors for existing one mission call sites and tests. Local demo groups retain their historical mission list; their current screen uses the last demo mission.

## Validation
- `android\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline` passed on installed JDK 17. 178 JVM tests, 0 failures, 37 suites. Kotlin compilation passed; no separate formatter or typecheck task is configured.
- `git diff --check` passed. Audited `missions.lastOrNull`, `firstOrNull`, `listOf(mission)`, `dropLast`, and `.summary` usages: remaining mission selections are the deliberate push fallback, summary compatibility getter, or ID based lookup; no current mission list replacement remains.
- No new `COMING_SOON` usage. `adb devices` showed no connected device, so the live UI and server scenario remain unverified.
- Reviewed the final diff and preserved pre-existing A2/A3/A9 edits and unrelated dirty files. No commit or push was made.

## Remaining risks and manual checks
- Calendar `recordsByDate` retains only the first mission for a date. Multiple missions on one date are therefore not all selectable from the calendar; calendar redesign was outside this plan.
- Push payloads have no mission ID, so the client cannot identify the exact mission that caused a comment push. The selected latest submitted mission is a best effort fallback.
- On a device with a `dailyMissionCount=3` group, verify chip switching, per mission submission and result access, per mission poke state, home progress, newly opened/closed slots, and the unchanged one mission UI.

## Assumptions, plan adjustments, merge conflicts
- Server list order is slot order as described in the plan. The app does not calculate slot timing.
- The report is the required exception to the Android-only source scope. No backend, iOS, or docs files were edited for A6.
- Shared files, especially `MainNavigation.kt`, `DataStore.kt`, and `GroupRepository.kt`, already contained uncommitted A2/A3/A9 changes. They were retained; future parallel Android edits may conflict.
