# Android native app, stages 0–2

## Files changed

- `android/`: new Gradle Kotlin DSL app, wrapper, manifest, resources, Compose design system and gallery, Kotlin models, in-memory store, demo data, session, and JVM tests.
- `android/.gitignore`: ignores local SDK settings and generated Android files. Root `.gitignore` was unchanged.

## Implementation

- Added a single Activity with a light Compose theme and a runnable component gallery. The gallery shows tokens, fonts, all icons and characters, decorations, logos, and controls.
- Ported the iOS model and `DataStore` behavior: Seoul mission windows, invite codes, demo groups, mission history, submissions, likes, comments, records, and profile changes. Store state lasts for the Activity ViewModel lifetime. Local media is copied into `cacheDir/hawe-media`.
- Converted all 40 iOS SVGs into Android VectorDrawable XML. No PNG fallback was needed. Added an adaptive launcher icon from the iOS logo vector, with safe-area padding. Copied five TTF files and two OFL license files.
- Added JUnit tests for time boundaries, invite code normalization, store creation, refresh, joining, submission, media, records, streaks, and mission type selection.

## Versions and plan adjustments

- AGP 8.13.2, Kotlin 2.2.21, Gradle 8.13, Compose BOM 2025.10.01; `minSdk 26`, `compileSdk 36`, `targetSdk 35`, Java 17. `compileSdk` is 36 because Activity 1.11.0 is compiled against API 36; the planned target remains 35.
- The official Gradle wrapper JAR is present. Android uses the SVG logo vector for its launcher foreground instead of resizing the iOS PNG; this preserves the same mark within the adaptive icon safe area.
- `Submission.mediaURL` uses `java.net.URI` for locally stored media, while avatar photos use `android.net.Uri`. `GroupSummary.startDate` is a Seoul `LocalDate`. Other model names and raw enum values follow iOS.
- iOS reference commit: `45a4996` (`git log -1 --format=%h -- ios/`). iOS files were read only. Earlier Android commits were not used as source.

## Verification

- `git diff --check`: passed (Git printed Windows line-ending conversion notices only).
- XML parser: all 48 Android XML resources parsed. All 39 referenced drawable names and five font names resolve to files. All VectorDrawable colors are hexadecimal values. After review fixes, a static check also confirmed that core model files contain no `var`, `MutableList`, or `MutableSet` fields.
- `./gradlew.bat :app:compileDebugKotlin --no-daemon`: blocked before compilation because no Android SDK platform is installed or configured. The initial run also picked up `JAVA_HOME` JDK 25; rerunning with JDK 17 reached the SDK-location check. Unit tests, lint, package build, and emulator review therefore have not run.
- Final diff inspection: changes made for this task are under `android/` and this report. The worktree already had unrelated `frontend/myApp` deletions, `cordova/` and `docs/FRONTEND/` additions, and a modified `.agent/plan.md`; these were left alone. Existing tracked Android paths had been deleted before this task and now appear as modifications where new files use the same names.

## Android Studio validation checklist

1. Open `android/`, configure/install SDK platform 36 and build tools, and select JDK 17 for Gradle sync.
2. Run `assembleDebug`, `testDebugUnitTest`, and `lintDebug`.
3. On an emulator, inspect the gallery against the iOS component gallery: Plex and Fredoka fonts; all 25 icons, seven characters, five relation decorations, logos; button pressed/disabled, chip and checkbox selection, text-field error, alert and toast, CharacterPicker photo selection, and CTA behavior with the keyboard.

## Review feedback decisions

| Item | Decision and reason | Change | Validation |
|---|---|---|---|
| P1 store state | Applied. In-place nested mutation could leave Compose readers with stale snapshots. | Made group/member/mission/submission fields and collections immutable; store writes now replace the changed object path. `groups` exposes a read-only snapshot. Demo data builds lists before constructing the group. | Static immutable-model check passed; tests now assert old snapshots retain their values. JVM run awaits SDK. |
| P1 CTA navigation inset | Applied. A fixed 36dp gap can overlap three-button navigation. | Uses the larger of 36dp and navigation inset plus 12dp; when the keyboard is open, `imePadding` supplies the inset. | Code inspection; gesture, three-button, and keyboard positioning await emulator review. |
| P1 MemberRow overlap | Applied. `offset` moved pixels without changing row spacing or draw order. | Uses −5dp row spacing, rounded decoration size, and decoration `zIndex`. | Code inspection; visual parity awaits emulator review. |
| P2 alert | Applied. The disabled card click did not consume taps, and dialog/window dim could stack. | Card now consumes clicks, the dialog draws across system bars, and window dim is set to zero. | Code inspection; tap behavior and exact dim need emulator review. |
| P2 toggle | Applied. Disabled state and motion were missing. | Added 0.4 alpha when disabled and a 200ms knob animation. | Code inspection; animation needs emulator review. |
| P2 theme | Applied. The navigation-bar XML attribute is newer than minSdk 26 and duplicates edge-to-edge setup. | Removed the system-bar XML items. | XML parsing passed; lint awaits SDK. |
| P2 tests | Applied. The missing cases protect the state rewrite and demo rules. | Added multiple-seed mission checks, foreign-comment deletion, `LOVE22`, lover start date, settings/poke, and snapshot identity tests. History/streak tests now use a clock or a narrow internal test fixture instead of mutating the exposed groups. | Test source inspected; `testDebugUnitTest` awaits SDK. |

## Remaining risks and merge conflicts

- Kotlin compilation and visual parity remain unverified until SDK setup. The generated vectors use SVG path geometry directly and need an emulator visual check, especially characters and the adaptive icon.
- The gallery displays `HToast` and opens `HAlert`; `HCtaScaffold` has a preview. Their visual and keyboard behavior need emulator inspection.
- Parallel changes under `android/` could conflict with this new project. The pre-existing frontend deletion and Cordova move were not changed.
