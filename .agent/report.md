# Developer mode Android implementation report

## Files changed
- New `android/app/src/main/java/com/sapphire/hawe/core/dev/` (`DevMode`, `AppClock`, `DevUnlock`, `DevFaults`), `core/net/DevApi.kt`, `DevRepository.kt`, and `feature/dev/DevToolsScreen.kt`.
- Updated `app/AppViewModel.kt`, `RootScreen.kt`, `MainNavigation.kt`, `core/net/ApiClient.kt`, `feature/mypage/AppInfoScreen.kt`, `MyPageScreen.kt`, and `feature/mission/MissionScreen.kt` for entry, navigation, session switching, and developer actions.
- Replaced display and business clock reads in `core/model/GroupSummary.kt`, `feature/groups/{CreateGroupScreen,GroupFormat,HomeScreen}.kt`, `feature/insight/GroupInsightScreen.kt`, `feature/records/RecordCalendarScreen.kt`, and `feature/results/{ResultsFormat,ResultsScreen}.kt`.
- New JVM tests under `android/app/src/test/java/com/sapphire/hawe/core/dev/` and `core/net/DevRepositoryTest.kt`; this report.

## What changed
- Ten taps on the App Info version text within three seconds per tap persistently enable developer mode. My Page then shows seed user chips and the developer tools entry. User switching obtains server tokens, clears prior user data, rebuilds the signed-in navigation graph, and restores the previous session on failure.
- Added the full `/api/dev/**` Android client and a temporary developer tools screen for clock control, mission publishing and deadlines, proxy actions, seed/reset, insight regeneration, group settings and deletion, local reset/logout, and fault injection. The mission screen also has a developer publish button.
- App date and time display follows the server clock offset while developer mode is enabled. The offset is saved and refreshed from the server on launch, enablement, and tool entry. Disabling developer mode restores a zero app offset. The network interceptor is inactive while the mode is off and excludes `/api/dev/**`.

## Verification
- JDK 17: `android\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug :app:compileReleaseKotlin --offline` passed. After the final clock boundary test, `:app:testDebugUnitTest --offline` passed again; 185 JVM tests, 0 failures. Android test APK assembled; instrumented tests were not run.
- `rg` audit: remaining `Instant.now()` in app source is the `AppClock` real time source; demo `DataStore` and elapsed timers use their injected clocks. No `BuildConfig.DEBUG` or build type gate was added. `git diff --check -- android` passed (only line-ending warnings).
- Built and installed a debug APK configured for `http://localhost:8080` on the connected Android device. The device was locked, so the tap-through workflow could not be verified. The temporary server and ADB reverse mapping were stopped; no server test data was created in this task.
- Reviewed the final diff and working tree. Only Android source/tests and `.agent/report.md` were changed for this task.

## Remaining risks
- The device walkthrough remains unverified: unlock, persistence after restart, user switch, seed and calendar, clock and mission flows, proxy actions, insight, settings, and injected errors need a manual pass on an unlocked device.
- Developer endpoints are intentionally available in release without an authentication gate, per the approved server and Android plans. Remove or block `/api/dev/**` before production deployment.
- Server clock state is process memory; the app retains its last offset if the server status request fails. Opening tools or reconnecting refreshes it.

## Assumptions, plan adjustments, and merge conflicts
- Implemented the Android-only plan against the previously completed backend API. Used Android's platform time picker for clock and slot times; no new dependency or server change was needed.
- Preserved pre-existing backend, `.agent/plan.md`, `.agent/review.md`, and Cordova Gradle cache changes. The touched navigation, My Page, and mission files are shared hot spots for parallel Android work; no textual conflict occurred.
- No commit or push was made.
