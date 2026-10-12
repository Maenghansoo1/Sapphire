# Implementation report — Android auth contract phase 2A

## Files changed and behavior

- `android/gradle/libs.versions.toml`, `android/app/build.gradle.kts`, and Android manifests: added OkHttp, coroutines, Internet permission, build-specific API URL, and debug-only cleartext network config. Release keeps HTTPS-only policy and a placeholder API domain.
- `android/app/src/main/java/com/sapphire/hawe/core/net/`: added JSON API client, single-flight refresh and one retry on expired access tokens, error mapping, auth DTOs/repository, in-memory token store, and Android Keystore AES-GCM token storage. Refresh rotates both tokens; network failures retain the session.
- `AppSession.kt`, `AppViewModel.kt`, `RootScreen.kt`: added RESTORING state, startup `/api/me` recovery, and token/data cleanup on sign-out.
- Auth screens/navigation: login and signup now use server endpoints; signup passes consent, retries after successful registration without re-registering, and selects a server character when available. Removed the mock login factory and its test. The signup photo cell is hidden. Unsupported auth actions show the shared coming-soon toast.
- My page and user info: blocked unsupported profile edit, account linking, password change, and withdrawal actions with the same toast.
- `docs/FRONTEND/ANDROID.md`: documented network stack, encrypted tokens, API URL override, and debug HTTP behavior.
- Added `ApiClientTest`, `AuthRepositoryTest`, `AppSessionTest`, and `ApiMessagesTest`; retained the other existing tests.

## Verification

- JDK 17: `testDebugUnitTest` **passed (73 tests)**.
- `assembleDebug`, `compileReleaseKotlin`, and `assembleRelease` **passed**.
- `lint` **passed with 0 errors, 30 warnings, 1 hint**. An earlier combined run failed while Maven hosts were unreachable for a transitive lint dependency; retry succeeded.
- `git diff --check -- android docs/FRONTEND/ANDROID.md` **passed**. Checked changed paths; Android files and `docs/FRONTEND/ANDROID.md` are the only implementation changes from this task. Pre-existing backend, Cordova, plan, and review changes were left intact; this report was overwritten as requested. Debug merged manifest contains the network config; release merged manifest retains `usesCleartextTraffic=false` and has no debug config.
- The initial baseline test run overlapped implementation edits and is not a clean baseline result; final tests and builds above passed.

## Remaining risks and manual checks

- No emulator was available, and the backend was not listening on localhost:8080. A physical device was connected, but the server flows were **not manually verified**. On an emulator with the backend running, check signup and character selection, restart/restore, logout, wrong-password and duplicate-email toasts, offline behavior, and all coming-soon actions.
- Android Keystore persistence and debug cleartext behavior need device verification. Release `https://api.hawe.app` is a placeholder; set the actual URL with `-PhaweApiBaseUrl=...` before deployment.
- Lint warnings are existing project-level findings; no warnings were reported in the new network or auth files.

## Assumptions, adjustments, and merge conflicts

- Followed the backend contract in the plan, including the PATCH character response field `characterId`. Kept character selection best-effort after registration and login.
- Delayed the duplicate-email navigation briefly so its toast remains visible.
- Potential future merge overlap is limited to Android app/session/auth build files and the frontend Android guide for later Android phases. No backend files were edited.