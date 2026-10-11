# Android stage 3 implementation report

## Files changed
- `android/app/src/main/java/com/sapphire/hawe/app/{RootScreen,AuthNavigation,MainNavigation}.kt`: separate typed auth and main navigation hosts, selected by session state. Main currently starts at 01 Start.
- `android/app/src/main/java/com/sapphire/hawe/feature/auth/`: A1 login, A2 account signup, A3 profile signup, password reset, account creation and validation logic.
- `android/app/src/main/java/com/sapphire/hawe/feature/groups/StartScreen.kt`: 01 Start, mission cards, temporary "준비 중이에요" toast for future routes, and debug-only long-press sign-out.
- `android/app/src/main/java/com/sapphire/hawe/core/model/AvatarContentExt.kt`: selected avatar name.
- `android/app/src/main/java/com/sapphire/hawe/designsystem/components/{HTextField,HNavBar,CustomTabs}.kt` and `designsystem/layout/HCtaScaffold.kt`: keyboard/IME options, shared inset padding, step-label width, and browser fallback.
- `android/app/src/test/java/com/sapphire/hawe/feature/auth/`: signup validation and login account factory tests.

## What changed
- A1 normalizes a nonempty entered email and creates a local default account when it differs from the current account; empty or matching email signs in without resetting the store. Social buttons lead to A3 with the chosen provider.
- A2 validates email, password, confirmation and both required agreements. A3 records the avatar and trimmed nickname, resets the store, and signs in. Password reset moves between input and sent states without server calls.
- Auth and main navigation each own a controller, so sign-in and debug sign-out clear the previous flow's back stack. Future group and my-page destinations are represented only by temporary toast callbacks.
- A2 password fields live only in the auth host's memory so backing out of A3 retains entries while process recreation does not save passwords.

## Validation
- `.\gradlew.bat assembleDebug testDebugUnitTest lintDebug installDebug`: **PASS** with Java 17 and Android SDK at `C:\Android\Sdk`.
- Unit tests: **17 passed, 0 failures, 0 errors** (11 existing, 6 new).
- Lint: **0 errors, 19 warnings**.
- Installed on `R5CY54VQA5M` (SM-S937N, Android 16). `adb shell am start -n com.sapphire.hawe/.MainActivity` started the app; the process remained running and the UI hierarchy was available.
- `git diff --check -- android`: no whitespace errors. Final file list contains only intended Android code and tests, plus this report. `.agent/plan.md` was already modified before implementation and was not edited here. iOS reference commit: `45a4996`.

## Remaining risks and manual checklist
- No full visual or interaction walkthrough was performed. On device, check: A1 layout and links; A2 validation, agreement links and step label; A3 character/photo picker, nickname limit and keyboard/CTA; Start cards and temporary toasts; relaunch behavior; social route without a step label; password-reset prefill and sent state; system back and auth/main back-stack separation; debug long-press sign-out.
- The app is intentionally local-only: login, social signup, password reset, group creation/join, and my page have no server or destination implementation in this stage.
- Compose autofill content-type semantics were not added; the existing component API supports keyboard type and IME actions without new dependencies.
- Current session state is not persisted across process restarts, matching the iOS reference behavior described in the plan.

## Assumptions and plan adjustments
- The Android project uses JUnit 4, so the new tests follow its existing test dependency rather than the repository root's JUnit Jupiter convention.
- No product or architecture redesign was needed. Password fields were held in the auth host to satisfy both the A2 back-navigation and no-password-persistence requirements.
- No iOS, backend, frontend, docs, build configuration, or instruction files were changed. The Android gallery remains available through its preview rather than a runtime route.

## Potential merge conflicts
- Shared files touched: `RootScreen.kt`, `HTextField.kt`, `HNavBar.kt`, and `CustomTabs.kt`. Later Android stages may edit these same files.

## Review feedback resolution

1. **P1 — HTextField IME actions: fixed.** The previous non-null `onAny` handler replaced Compose's default Next/Done behavior even when no callback was supplied. `KeyboardActions.Default` now handles fields without a custom callback; the A1 password field keeps its explicit login action. The Android build, 17 tests, lint, and device install passed. Keyboard taps were not manually exercised.
2. **P1 — A1 keyboard and navigation insets: fixed.** A1 now applies `imePadding()` outside its scrollable content. Its bottom padding uses the same navigation-bar and keyboard calculation as `HCtaScaffold`, extracted to `ctaBottomPadding`. The Android build, 17 tests, lint, and device install passed. Three-button navigation and keyboard layout still need a visual check.
3. **P2 — Agreement “보기” control: fixed.** Replaced the Material `TextButton` with a centered 44dp plain clickable label and no ripple, keeping the view action independent of the checkbox. The Android build, 17 tests, lint, and device install passed. Visual spacing still needs a device check.
4. **P2 — A2 password configuration changes: accepted limitation.** The review correctly notes that an Activity recreation clears only the in-memory password and confirmation while saveable email and agreements remain. This follows the plan's direction to avoid saving passwords and to leave `AppViewModel` unchanged. No code change was made for this item. The behavior remains a manual configuration-change check.
5. **P2 — Validation test gaps: fixed.** Added assertions for whitespace in email, the exact password error, and each required agreement missing independently. Existing tests already covered empty error states and case-insensitive matching for the login account factory. All 17 tests pass.

## Review validation and scope

- Re-ran `.\gradlew.bat assembleDebug testDebugUnitTest lintDebug installDebug`: **PASS** on the connected SM-S937N.
- Test XML: **17 tests, 0 failures, 0 errors**. Lint XML: **0 errors, 19 warnings**.
- `git diff --check -- android`: no whitespace errors. Review changes are limited to `HTextField.kt`, `HCtaScaffold.kt`, `LoginScreen.kt`, `SignupAccountScreen.kt`, `SignupAccountStateTest.kt`, and this report.
- `.agent/plan.md` and `.agent/review.md` were already modified by the other session and were not edited. No iOS, backend, frontend, docs, or instruction files changed here.