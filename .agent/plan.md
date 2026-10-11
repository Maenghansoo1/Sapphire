# Plan — Android 네이티브 앱 0~2단계 (뼈대 · 디자인 시스템 · 모델/임시 저장소)

## Goal

iOS SwiftUI 앱(`ios/HAWE`)을 Android 네이티브(Kotlin + Jetpack Compose)로 옮기는 작업의 **첫 묶음(0~2단계)** 을 새로 구현한다.
전체 로드맵·API 대응표는 `docs/FRONTEND/ANDROID.md` 에 있다 — 반드시 먼저 읽는다 (특히 §3 기술 선택, §4 폴더 구조, §5 대응표, §6 0~2단계, §7 주의점).

이번 범위:
- **0단계** 프로젝트 뼈대: `android/` Gradle 프로젝트, 빌드·실행 가능
- **1단계** 디자인 시스템: 토큰, 에셋(SVG→VectorDrawable, 글꼴, 앱 아이콘), 공통 컴포넌트
- **2단계** 모델 · `DataStore`(앱 안 임시 저장소) · `DemoSeed` · `AppSession` + 단위 테스트

이번 범위 **아님**: 실제 화면(A1~17-5), 내비게이션 그래프, 카메라/녹음/그림판, 알림, 서버 연결. 3단계 이후 별도 계획으로 진행한다.

## Findings

- 저장소 루트에 `android/` 가 **없다**. 예전 커밋(`5354076`, `db678dc`)에 Android 코드가 있었지만 **사용자가 버리기로 결정**했다. 그 커밋에서 파일을 복원하거나 참고해서 복사하지 말 것 — 처음부터 새로 만든다.
- 원본 iOS 코드 (모두 읽기 전용, 수정 금지):
  - 토큰: `ios/HAWE/DesignSystem/Tokens/Color+HAWE.swift`, `Font+HAWE.swift`, `HLayout.swift`
  - 에셋 enum: `ios/HAWE/DesignSystem/Assets/HIcon.swift`(25종), `ProfileCharacter.swift`(7종), `Relationship.swift`(5종 + 장식), `Brand/HAWELogo.swift`
  - 컴포넌트: `ios/HAWE/DesignSystem/Components/*.swift` 12개 (`CharacterPicker`, `HAlert`, `HAvatar`, `HButton`, `HCheckbox`, `HChip`, `HListRow`, `HNavBar`(+`HNavStep`), `HTextField`, `HToggle`, `MemberRow`, `SafariView`), 레이아웃 `Layout/HCTAScaffold.swift`, `Layout/FlowLayout.swift`
  - `HToast` 는 `ios/HAWE/Features/Groups/GroupInfoView.swift` 맨 아래에 정의돼 있다 (ink 90% 알약, 높이 44, 좌우 18, 14 SemiBold 흰 글자).
  - 모델: `ios/HAWE/Core/Models/*.swift` 6개, `ios/HAWE/Features/Records/RecordCalendarView.swift` 상단의 `DayRecord`, `ios/HAWE/Features/Groups/JoinGroupView.swift` 의 `GroupInvitePreview`(+`isFull`) 와 `JoinGroupModel.sanitize`
  - 저장소: `ios/HAWE/Core/Data/DataStore.swift`(+`TopicPool`, `SubmissionContent`, `InviteLookupError`, `StoredGroup`, `StoredMission`), `ios/HAWE/Core/Data/DemoSeed.swift`
  - 세션: `ios/HAWE/Core/AppSession.swift`, 링크: `ios/HAWE/Core/AppLinks.swift`
  - 에셋 원본: `ios/HAWE/Resources/Assets.xcassets/{Icons,Characters,Decor,Logo,Social}/*.imageset/*.svg` (40개, Icons 는 template 렌더링 = tint 가능), 앱 아이콘 `AppIcon.appiconset/AppIcon-1024.png`, 글꼴 `ios/HAWE/Resources/Fonts/*.ttf` 5개 + OFL 라이선스 txt 2개
- iOS 주석의 `CLAUDE.md §N` 은 이 저장소에 없는 문서다. 규칙은 iOS 코드와 주석을 기준으로 삼는다.
- iOS `ComponentGalleryView`(`ios/HAWE/Features/ComponentGallery/ComponentGalleryView.swift`)는 토큰·에셋·컴포넌트를 한 화면에 보여주는 개발용 화면이다 — Android 에서는 이번 단계의 실행 화면으로 쓴다.
- 이 PC 에는 JDK 17·25 는 있지만 작업 시작 시점에 **Android SDK 와 Gradle 이 설치돼 있지 않다**. 사용자가 Android Studio 를 직접 설치할 예정이다.
- **사용자 결정: 빌드·테스트·실행 검증은 사용자가 직접 한다.** Codex 는 코드와 테스트 코드를 작성하고, Gradle 빌드/테스트/에뮬레이터 실행은 하지 않는다 (Validation Steps 참고).

## Root Cause / Key Implementation Points

1. **iOS 와 1:1 대응 유지** — 이름·값·규칙을 iOS 와 같게 옮긴다. 나중에 화면 단계에서 iOS 파일과 나란히 보며 옮길 수 있어야 한다. Swift 이름은 Kotlin 관례로만 바꾼다 (`HCTAScaffold` → `HCtaScaffold`, `check-small` → `ic_check_small`).
2. **상태 관리** — iOS `@Observable` 클래스 + `.environment()` 를 Compose 로: `DataStore`, `AppSession` 은 일반 Kotlin 클래스이고 공개 상태를 Compose 상태(`mutableStateOf` / `mutableStateListOf`, 외부에는 읽기 전용 노출)로 가진다. 주입용 `CompositionLocal` (`LocalDataStore`, `LocalAppSession`) 을 만든다. Activity 범위 `ViewModel` 하나(`AppViewModel`)가 둘을 소유해 구성 변경에도 유지. 영속화(파일/DB)는 하지 않는다 — 앱을 끄면 사라지는 것도 iOS 와 같다.
3. **시간대** — 미션 창·연속 참여일·기록은 **항상 Asia/Seoul**. `java.time` 사용, `ZoneId.of("Asia/Seoul")` 를 상수 하나(`HaweTime.SEOUL`)로 두고 `ZoneId.systemDefault()` 는 쓰지 않는다. iOS `TodayMission.window(topicTime:now:)` 를 그대로 옮긴다: 오늘 주제 시간 이전이면 어제 주제 시간에 열린 창, 닫히는 시각 = 열린 시각 + 1일.
4. **테스트 가능성** — iOS `DataStore` 는 `.now` 와 랜덤을 직접 쓴다. Kotlin 에서는 생성자에 `clock: java.time.Clock = Clock.systemDefaultZone()`(시간대는 계산 시 SEOUL 로 변환) 와 `random: kotlin.random.Random = Random.Default` 를 받아 테스트에서 고정한다. 동작은 바꾸지 않는다.
5. **글꼴 줄 높이** — Compose `TextStyle` 기본값에 `platformStyle = PlatformTextStyle(includeFontPadding = false)` 와 `lineHeightStyle = LineHeightStyle(Alignment.Center, Trim.None)` 를 넣어 Plex 의 큰 ascent 때문에 글자가 밀리지 않게 한다. iOS `plexLineHeight(multiple, size)` 는 `lineHeight = (size * multiple).sp` 로 대응.
6. **모양은 직접 그린다** — Material3 는 테마 바탕으로만 쓰고 버튼·칩·토글·체크박스·입력창·알림창은 iOS 컴포넌트 수치(주석에 적힌 높이·모서리·테두리·글자 크기)대로 직접 그린다. 시스템 `AlertDialog`/`Switch`/`Checkbox` 를 쓰지 않는다.

## Files / Functions to Change

모두 **새 파일**이다. 기존 파일은 아래 두 개만 수정한다.

수정:
- `.gitignore` (루트): `android/` 용 항목 추가 — `android/local.properties`, `android/.gradle/`, `android/**/build/`, `android/.idea/`, `android/.kotlin/`, `android/captures/`. (루트 `.gitignore` 에 이미 `build/`, `.idea` 가 있으면 중복 추가하지 않는다. 대신 `android/.gitignore` 를 따로 두는 방식도 가능 — 둘 중 하나만.)

새로 만들 것 (`android/` 기준):
- 빌드: `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`, `gradle/wrapper/*`, `gradlew`, `gradlew.bat`, `app/build.gradle.kts`, `app/proguard-rules.pro`(비어 있어도 됨)
- `app/src/main/AndroidManifest.xml` — 단일 `MainActivity`, `android:screenOrientation="portrait"`, 테마 `Theme.Hawe`(Material3 Light NoActionBar 기반), `android:label="HA'WE"`. 권한은 이번에 넣지 않는다 (카메라·녹음·알림은 6·9단계).
- `app/src/main/res/values/{strings.xml, themes.xml, colors.xml}` (colors 는 런처 아이콘 배경용 정도만)
- `app/src/main/res/font/` — `ibm_plex_sans_kr_regular.ttf`, `_medium`, `_semibold`, `_bold`, `fredoka_semibold.ttf` (iOS Fonts 에서 복사, 파일명만 변경)
- `app/src/main/res/drawable/` — SVG 40개를 VectorDrawable 로: `ic_<이름>`(Icons 25, 하이픈→밑줄), `character_<이름>`(7), `decor_lover|friend|enemy`(3), `logo_symbol`, `logo_symbol_small`, `social_apple|google|kakao`
- `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` + 전경/배경 (AppIcon-1024.png 기반, 전경은 PNG 를 `mipmap-*dpi` 로 리사이즈하거나 logo VectorDrawable 사용 — 1024 PNG 를 눈으로 보고 판단)
- `app/src/main/assets/licenses/OFL-IBMPlexSansKR.txt`, `OFL-Fredoka.txt` (17-3 라이선스 화면용으로 지금 복사해 둠)
- Kotlin (`app/src/main/java/com/sapphire/hawe/`):
  - `MainActivity.kt` — `enableEdgeToEdge()`, `setContent { HaweTheme { CompositionLocalProvider(LocalDataStore, LocalAppSession) { RootScreen() } } }`
  - `app/AppViewModel.kt` — `DataStore`, `AppSession` 소유
  - `app/RootScreen.kt` — 이번 단계에서는 `ComponentGalleryScreen()` 을 보여준다 (3단계에서 인증/메인 분기로 교체한다는 TODO 주석 한 줄)
  - `core/HaweTime.kt` — `SEOUL`, 공용 `todayWindow(...)` 등 시간 유틸 (iOS `TodayMission.window`, `DataStore.calendar`)
  - `core/AppSession.kt` ← `Core/AppSession.swift`
  - `core/AppLinks.kt` ← `Core/AppLinks.swift` (URL 상수 + `invite(code)`)
  - `core/model/` ← `Core/Models/*.swift`: `MissionType.kt`(+`displayName`, `icon`, `actionTitle`, `subjectForm`), `GroupSummary.kt`(+`daysTogether`), `GroupDetail.kt`(`Profile`, `GroupMember`, `GroupDetail`), `MyAccount.kt`(`LoginProvider`, `NotificationPrefs`, `ActivityStats`, `MyAccount`), `Submission.kt`(`SubmissionComment`, `Submission`, `MissionResult`), `TodayMission.kt`(`MissionMember`, `TodayMission`), 그리고 `DayRecord.kt`, `GroupInvitePreview.kt`(+`isFull`), `InviteCode.kt`(`sanitize` — iOS `JoinGroupModel.sanitize` 와 같은 규칙)
  - iOS 목업 팩토리(`GroupSummary.mock`, `GroupDetail.mock`, `MissionResult.mock`, `TodayMission.mock`, `SubmissionComment.mockList`, `MyAccount.mock`, `Profile.mockMine`)는 `@Preview` 와 `AppSession` 기본값에 필요한 것만 옮긴다: **`Profile.mockMine`, `MyAccount.mock`, `GroupDetail.mock` 는 필수**(DataStore·AppSession 이 씀). 나머지는 이번에 생략 가능.
  - `core/data/DataStore.kt` ← `Core/Data/DataStore.swift` 전체 (+ `TopicPool`, `SubmissionContent`, `InviteLookupError`, `StoredGroup`, `StoredMission`)
  - `core/data/DemoSeed.kt` ← `Core/Data/DemoSeed.swift` (+ `DataStore.preview(submitted)` 는 `DataStore` companion 또는 DemoSeed 안 함수로)
  - `designsystem/tokens/HColors.kt` ← `Color+HAWE.swift` (모든 색, 값 그대로)
  - `designsystem/tokens/HType.kt` ← `Font+HAWE.swift` (`PlexFamily`, `FredokaFamily`, `plex(size, weight)`, `wordmark(size)`, `screenTitle`·`navTitle`·`sectionTitle`·`fieldLabel`·`body15`·`caption13`·`caption12`, 줄 높이 헬퍼)
  - `designsystem/tokens/HLayout.kt` ← `HLayout.swift` (`HRadius`, `HLayout` 값 그대로, `Dp`)
  - `designsystem/theme/HaweTheme.kt` — 라이트 고정 Material3 테마, 배경 `bg`, 기본 글꼴 Plex
  - `designsystem/assets/HIcon.kt`(enum → `@DrawableRes`, `HIconView(icon, size=24.dp, color=ink)`), `ProfileCharacter.kt`(+`displayName`, `drawable`, `DEFAULT = MONGSIL`), `Relationship.kt`(+`displayName`, `decorPhrase`, `maxMembers`, `decorColor`, `RelationshipDecoration` — 가족/그저그런사이는 Material `Icons.Filled.Home` / `Icons.Filled.MoreHoriz` 를 decorColor 로 tint; iOS 도 SF Symbol 임시 표시)
  - `designsystem/brand/HaweLogo.kt` ← `HLogoSymbol`(60 초과면 `logo_symbol`, 이하면 `logo_symbol_small`), `HWordmark`(아포스트로피만 accent, `AnnotatedString`), `HLogoLockup`
  - `designsystem/components/` ← iOS 컴포넌트 12개 + `HToast.kt`: `HButton.kt`, `HChip.kt`, `HNavBar.kt`(+`HNavStep`), `HTextField.kt`, `HToggle.kt`, `HCheckbox.kt`(+`HCheckboxMark`), `HListRow.kt`, `HAvatar.kt`(+`AvatarContent`), `MemberRow.kt`, `CharacterPicker.kt`, `HAlert.kt`, `HToast.kt`, `CustomTabs.kt`(`openInCustomTab(context, url)` ← `SafariView`)
  - `designsystem/layout/HCtaScaffold.kt` ← `HCTAScaffold.swift` (FlowLayout 은 Compose `FlowRow` 로 대체하므로 파일 없음)
  - `feature/gallery/ComponentGalleryScreen.kt` ← `ComponentGalleryView.swift` (실행 화면 + `@Preview`)
- 테스트 (`app/src/test/java/com/sapphire/hawe/`):
  - `core/data/DataStoreTest.kt`, `core/HaweTimeTest.kt`, `core/model/InviteCodeTest.kt`

## Implementation Steps

### 0단계 — 뼈대
1. `android/` 에 Gradle Kotlin DSL 프로젝트 생성. 플러그인: `com.android.application`, `org.jetbrains.kotlin.android`, `org.jetbrains.kotlin.plugin.compose`, `org.jetbrains.kotlin.plugin.serialization`(3단계 라우트용, 지금 넣어 둠).
   - `namespace`/`applicationId` = `com.sapphire.hawe`, `minSdk 26`, `compileSdk`/`targetSdk` 35 (설치된 AGP 가 36 을 요구하면 36 — 보고서에 기록), `versionName "1.0.0"`, `versionCode 1`, JDK 17 타깃.
   - 버전은 **작업 시점의 최신 안정판**으로 `libs.versions.toml` 에 고정: AGP, Kotlin, Compose BOM, `activity-compose`, `lifecycle-viewmodel-compose`, `navigation-compose`(지금은 의존성만), `androidx.browser`, `coil3-compose`, `kotlinx-serialization-json`, `material-icons-core`, JUnit4(또는 JUnit5) — 서로 호환되는 조합인지 확인.
   - Gradle wrapper: `gradle/wrapper/gradle-wrapper.properties`(AGP 와 호환되는 Gradle 배포판 URL), `gradlew`, `gradlew.bat` 을 작성한다. `gradle-wrapper.jar` 는 공식 Gradle 배포판에서 받을 수 있으면 넣고, 받을 수 없으면 넣지 말고 보고서에 "Android Studio 에서 열면 wrapper 를 생성"이라고 적는다. jar 를 직접 만들어 내거나 출처 불명 파일을 넣지 않는다.
2. `MainActivity` + `HaweTheme` + 빈 `RootScreen`.

### 1단계 — 디자인 시스템
3. 토큰 3개 파일 (`HColors`, `HType`, `HLayout`/`HRadius`). 색 hex·크기·모서리 값은 iOS 파일에서 그대로 복사.
4. 글꼴 TTF 복사, `FontFamily` 정의 (Plex: Normal/Medium/SemiBold/Bold 가중치, Fredoka: SemiBold).
5. SVG 40개 → VectorDrawable 변환 (Android Studio Vector Asset Studio 또는 `npx svg2vectordrawable` 등 CLI). 변환 후:
   - `ic_*` 는 `fillColor`/`strokeColor` 를 tint 가능하게 (단색 `#000000` 등으로 두고 Compose `Icon(tint=)` 로 칠함).
   - `character_*`, `logo_*`, `social_*`, `decor_*` 는 원래 색 유지.
   - 변환 실패/손상(그라데이션·mask·clipPath)이 있으면 그 파일만 PNG(xxhdpi, 원본 크기 ×3)로 대체하고 보고서에 목록 기록.
6. 앱 아이콘(adaptive) 구성.
7. `HIcon`, `ProfileCharacter`, `Relationship`(+`RelationshipDecoration`), 로고 3종.
8. 컴포넌트 구현 — **각 iOS 파일 상단 주석의 수치를 기준**으로 한다. iOS 파일을 열어 레이아웃·상태(pressed/disabled/selected/error)를 그대로 옮긴다:
   - `HButton`(종류 primary/secondary/gray/danger, 높이 56 · 모서리 16 · 17 SemiBold, pressed 색, disabled)
   - `HChip`, `HNavBar`(+`HNavStep`, 높이 52, 좌 44×44 back/close, 가운데 17 Bold, 우측 슬롯), `HTextField`(높이 54 · 모서리 14 · 라벨·안내·글자 수·error 상태), `HToggle`(52×32, 손잡이 26), `HCheckbox`(22×22, 라벨 있으면 줄 전체 높이 44 클릭), `HListRow`(높이 52 기본, 값·꺾쇠·구분선), `HAvatar`(원형 surface + 캐릭터 0.85배 / 사진은 Coil `AsyncImage` 원형 크롭, `ringed` 흰 테두리 2)
   - `AvatarContent` 는 `Character(ProfileCharacter)` 와 `Photo(uri: android.net.Uri)` 두 가지로 통합 (iOS 의 `.photo(URL)` + `.image(UIImage)` → Uri 하나), `isPhoto` 속성 유지.
   - `MemberRow`(아바타 34·흰 테두리 2·장식 20·5pt 겹침, iOS 파일 참조)
   - `CharacterPicker`: 4열 격자(스크롤 안에 들어가므로 Lazy 그리드 말고 `Row` 반복), 선택 칸 흰 바탕 + ink 테두리 2.5, 이름 12 Bold/Medium, '내 사진' 점선 칸 → `rememberLauncherForActivityResult(PickVisualMedia())` 로 Photo Picker(`ImageOnly`). 받은 Uri 는 `context.cacheDir/hawe-media/` 로 복사한 파일 Uri 를 `AvatarContent.Photo` 로 사용 (원본 Uri 권한이 사라지는 문제 방지). `lastPhoto` 동작(캐릭터로 바꿨다가 다시 누르면 앨범 열기)도 iOS 와 같게.
   - `HAlert`(dim ink 45%, 흰 카드 모서리 24, 좌우 28 여백, 제목 20 Bold, 내용 14·줄 높이 1.6, 버튼 2개 높이 50 — 취소 gray/확정 danger). `Dialog(properties = DialogProperties(usePlatformDefaultWidth = false))` 로 전체 화면 dim 직접 그림. 바깥 탭 동작은 iOS `HAlert.swift` 의 제스처 처리와 같게.
   - `HToast`, `openInCustomTab`
   - `HCtaScaffold`: 위쪽만 스크롤, CTA 는 좌우 패딩·위 12·아래 `bottomInset`(기본 36) 고정, 키보드가 올라오면 CTA 가 키보드 위로 (`Modifier.imePadding()`; edge-to-edge 이므로 navigation bar inset 과 bottomInset 이 이중으로 더해지지 않게 처리 — iOS 는 `ignoresSafeArea(.bottom)` 후 36 을 준다).
9. `ComponentGalleryScreen`: iOS `ComponentGalleryView` 구성 그대로 (색 견본, 글꼴, 아이콘 25, 캐릭터 7, 관계 장식 5, 로고, 각 컴포넌트 상태별). `RootScreen` 에서 표시.

### 2단계 — 모델 · 저장소 · 세션
10. 모델 `data class`/`enum class` 작성. 규칙:
    - `id` 는 `java.util.UUID`, 시각은 `java.time.Instant`, iOS `DateComponents`(시·분) 는 `java.time.LocalTime`, 날짜만 쓰는 곳(`startDate`)은 `LocalDate`(서울 기준) 또는 `Instant` — 한 가지로 통일하고 보고서에 기록.
    - `TimeInterval` → `Double` 초 또는 `kotlin.time.Duration` (통일).
    - iOS `CGFloat` 파형 → `Float` (0..1).
    - `Relationship`/`MissionType`/`ProfileCharacter`/`LoginProvider` rawValue 는 iOS 와 같은 소문자 문자열을 `value` 속성으로 유지 (`friend_done` 처럼 나중에 서버/푸시 매핑에 씀).
    - `Hashable` → `data class` 기본 equals. `GroupMember.displayed = groupProfile ?: defaultProfile`, `GroupDetail.me`, `amIOwner`, `TodayMission.doneCount`/`didSubmit`, `GroupSummary.daysTogether`(시작일 = 1일째, 서울 기준 날짜 차이 + 1), `GroupInvitePreview.isFull`(`members.size >= relationship.maxMembers`).
11. `HaweTime`: `SEOUL`, `window(topicTime: LocalTime, now: Instant): Pair<Instant, Instant>` — iOS `TodayMission.window` 와 동일 동작.
12. `DataStore` — iOS 공개 API 를 **이름·의미 그대로** 옮긴다 (서버 연결 때 이 클래스 안쪽만 바꾸는 구조):
    `groups`(읽기 전용), `myProfile`, `setMyProfile`, `reset`, `stats`, `group(id)`, `summaries`, `createGroup(name, relationship, startDate)`, `lookUpInvite(code): Result`(Kotlin 은 sealed `InviteLookupResult` 또는 `kotlin.Result` 대신 `sealed interface` 권장 — `Success(preview)` / `Failure(InviteLookupError)`), `join(code): UUID?`, `leave`, `setTopicTime`, `setNotify`, `setMyGroupProfile`, `refreshTodayMissions(now)`, `todayMission(groupId)`, `submit(content, groupId)`, `poke(memberId, groupId)`, `result(groupId, missionId)`, `toggleLike`, `addComment`, `deleteComment`, `records(groupId)`.
    지켜야 할 규칙 (iOS 코드 그대로):
    - 초대 코드: 6자리, 문자 집합 `ABCDEFGHJKLMNPQRSTUVWXYZ23456789`(0·O·1·I 제외), 내 그룹·DemoSeed 코드와 겹치지 않게
    - `lookUpInvite`: 내 그룹 코드 → `alreadyMember`, DemoSeed 에 없음 → `notFound`, `isFull` → `full`. 오류 메시지 문구 iOS 그대로
    - `join`: 성공 시 DemoSeed 그룹에 나(`isMe`, 기본 프로필 = `myProfile`) 추가 후 `refreshTodayMissions`
    - `createGroup`: 나 혼자 방장, 주제 시간 09:00, notify true, `startDate` 는 연인일 때만, 생성 후 `refreshTodayMissions`
    - `leave`: 내 목록에서 그룹 제거만
    - 미션 생성(`makeMission`): 최근 2개 유형이 같으면 그 유형 제외, 주제는 30일 이내 사용분 제외 후 랜덤(없으면 전체 풀에서 랜덤), `number = missions.size + 1`, `missionCount` 갱신
    - `refreshTodayMissions`: 마지막 미션의 `opensAt >= 현재 창 opens` 면 건너뜀
    - `submit`: 오늘(마지막) 미션에 내가 이미 올렸으면 무시. 이미지(PNG/JPEG) → `cacheDir/hawe-media/<UUID>.<ext>` 저장 후 `mediaUri`, 글 → `text`, 음성 → 파일 복사(`m4a`) + `duration` + `waveform`. **파일 저장 위치는 생성자로 받는 `mediaDir: File`** 로 만들어 단위 테스트에서 임시 폴더를 쓸 수 있게 한다 (Android `Context` 를 DataStore 에 넘기지 않는다).
    - `poke`: 마지막 미션의 `poked` 에 추가 (하루 1회 표시는 화면이 `pokedToday` 로 처리)
    - `result`: 내가 그 미션에 올렸을 때만 반환, `pending` = 올리지 않은 멤버
    - `toggleLike`: `likedByMe` 토글, `likeCount ±1`
    - `addComment`: 작성자 = 그룹 내 내 표시 프로필(없으면 `myProfile`), `deleteComment`: 내 댓글만 삭제
    - `records`: 제출이 하나라도 있고 (오늘 이전이거나 내가 올린 날) 인 미션만
    - `stats`: 내가 올린 미션 수, 연속 참여일(오늘 안 올렸으면 어제부터 거꾸로), 그룹 수, 유형별 횟수 — 날짜 경계는 서울 자정
    - `setMyProfile`: 모든 그룹의 내 `defaultProfile` 갱신
    - 상태 변경은 Compose 가 재구성되도록 해야 한다: `groups` 를 `mutableStateListOf<StoredGroup>` 로 두고 원소를 **교체**(`groups[i] = groups[i].copy(...)`)하는 방식으로 불변 `data class` 를 갱신한다 (iOS `update(groupID) { inout }` 헬퍼에 대응하는 `private fun update(groupId, transform: (StoredGroup) -> StoredGroup)`).
13. `DemoSeed` — `K7Q2PX`(컴공 동기, 친구 4명, 2026-06-02 부터 매일 9시 미션 기록, 오늘 앞 3명 제출, 패턴·doneCount 규칙 iOS 그대로), `LOVE22`(우리 둘, 연인, 도윤 1명, 127일 전 생성 = startDate), `FULL00`(6명, 정원 초과). **매 호출마다 새 UUID 로 새 객체를 만든다** (iOS 는 computed property). 생성 시각 기준은 `DataStore` 의 clock 을 넘겨받는다.
14. `AppSession` — `state`(SignedOut/SignedIn), `account`(기본 `MyAccount.mock`), `notificationPrefs`, `signIn()`, `signOut()`, `withdraw()`(account·prefs 초기화 + SignedOut; DataStore.reset 호출은 화면 단계에서 — iOS 와 같게 여기서는 하지 않는다).
15. `AppViewModel` + `LocalDataStore`/`LocalAppSession` 연결 (`DataStore` 의 `mediaDir` = `application.cacheDir/hawe-media`).
16. 단위 테스트 코드 작성 (아래 Tests Required). 실행은 사용자가 한다.

## Constraints

- **production 코드 범위**: `android/` 아래 새 파일 + 루트 `.gitignore` 만. `ios/`, `src/`(백엔드), `cordova/`, `frontend/`, `docs/` 는 수정하지 않는다.
- 예전 Android 커밋(`5354076`, `db678dc`)을 checkout/restore/cherry-pick 하거나 내용을 복사하지 않는다.
- git 커밋·브랜치·stash 등 git 상태 변경 금지 (사용자가 직접 관리). 작업 트리에 이미 있는 `android/`·`frontend/` 삭제 상태와 `cordova/` 는 그대로 둔다.
- 새 라이브러리는 Findings/0단계에 적은 것만. Hilt·Room·Retrofit·Accompanist 등 추가 금지.
- 화면(A1~17-5)·내비게이션 그래프·카메라·녹음·그림판·알림 구현 금지 (다음 계획).
- 문구(한국어)는 iOS 문자열을 그대로 쓴다. 주석은 iOS 처럼 한국어로, 짧게.
- 다크 모드 대응하지 않음 (라이트 고정), 세로 고정.
- 코드 스타일: Kotlin 공식 스타일, 패키지 소문자, 컴포저블 PascalCase.

## Edge Cases

- **기기 시간대가 서울이 아님** — 예: 기기 UTC 에서 서울 08:59 / 09:00 경계, 서울 자정 경계. 모든 계산이 `HaweTime.SEOUL` 기준이어야 함.
- `refreshTodayMissions` 를 같은 창에서 여러 번 불러도 미션이 하나만 생김.
- 그룹이 하나도 없을 때 `stats`, `summaries` 가 비어 있고 예외 없음. 없는 groupId 로 `todayMission`/`result`/`records` 호출 시 null/빈 목록.
- `submit` 두 번 호출 → 두 번째 무시. 오늘 미션 없는 그룹(방금 나간 그룹)에 submit → 무시.
- `deleteComment` 로 남의 댓글 id 를 넘겨도 지워지지 않음.
- 초대 코드 정규화 `sanitize`: 소문자 → 대문자, 영문·숫자 외 제거, 한글 제거, 6자 자름 (`"k7q2px"` → `"K7Q2PX"`, `"k7-q2 px99"` → `"K7Q2PX"`).
- `LOVE22` 는 멤버 1명 + 나 = 2명 → 참여 가능, 참여 후 다시 조회하면 `alreadyMember`.
- 연인이 아닌 관계로 만들면 `startDate` 를 넘겨도 null 로 저장.
- 오늘 내가 안 올렸고 어제 올렸으면 연속 참여일은 어제부터 셈.
- `CharacterPicker` 에서 Photo Picker 취소 시 선택 변화 없음.
- 아주 작은 화면(360dp 폭)에서 `CharacterPicker` 4열·`MemberRow` 6명 + 장식이 넘치지 않음.
- `HCtaScaffold` — 키보드 열림/닫힘, 제스처 내비게이션/3버튼 내비게이션 모두에서 CTA 위치.

## Regression Risks

- 기존 코드는 건드리지 않으므로 백엔드·iOS·Cordova 회귀 위험은 없다. 확인할 것은 루트 `.gitignore` 변경이 기존 무시 규칙(`target/`, `.mvn/...`, `build/`)을 깨지 않는지뿐.
- 루트 `.gitignore` 의 `build/` 가 이미 있으므로 `android/app/build/` 는 무시되지만, `android/gradle/wrapper/gradle-wrapper.jar` 가 무시 규칙(예: `*.jar`)에 걸리지 않는지 `git status` 로 확인 — wrapper jar 는 커밋되어야 한다.
- 이후 단계 영향: `DataStore` 공개 API 시그니처와 모델 필드 이름은 3단계 이후 화면이 그대로 쓴다. iOS 와 다르게 이름을 바꾸면 보고서에 대응표를 남긴다.

## Tests Required

JVM 단위 테스트 코드를 작성한다 (`app/src/test`, Android 기기 불필요). **실행은 사용자가 한다** — Codex 는 컴파일되도록 신중히 작성만 한다. `Clock.fixed(...)` 와 시드 고정 `Random` 사용, `mediaDir` 은 JUnit 임시 폴더.

`HaweTimeTest`
- 서울 08:59 → 어제 09:00 ~ 오늘 09:00 창, 서울 09:00 정각 → 오늘 09:00 ~ 내일 09:00 창
- JVM 기본 시간대를 UTC 로 바꿔도(`TimeZone.setDefault`) 결과 동일

`InviteCodeTest`
- `sanitize` 케이스 (Edge Cases 참조)

`DataStoreTest`
- `createGroup` → 그룹 1개, 나 = 방장, 오늘 미션 1개 생성, 초대 코드 6자리·허용 문자만, 연인이 아니면 startDate null
- `refreshTodayMissions` 반복 호출 시 미션 수 그대로, 다음 날 09:00 이후 시각으로 호출하면 1개 추가
- `lookUpInvite`: `K7Q2PX` 성공, `ZZZZZZ` notFound, `FULL00` full, 참여 후 `K7Q2PX` alreadyMember, 내가 만든 그룹 코드 alreadyMember
- `join("K7Q2PX")` → 멤버 5명(나 포함), 오늘 미션 제출 3건, 지난 미션 기록 존재
- `submit(Text)` 성공 → `todayMission.didSubmit == true`, 다시 `submit` 해도 제출 수 그대로
- `submit(Image)` → `mediaDir` 에 파일 생성
- `result` 는 제출 전 null, 제출 후 non-null 이고 `pending` 에 미제출 멤버만
- `toggleLike` 두 번 → 원래 수, `addComment` 후 `deleteComment`(내 것) 삭제 / 남의 댓글 id 는 유지
- 미션 유형 규칙: 최근 2개 유형이 같은 상태를 만들어 다음 미션 유형이 그 유형이 아님 (여러 시드로 반복)
- `stats.streakDays`: 오늘 미제출·어제부터 3일 연속 제출 → 3
- `records`: 제출 없는 날 제외, 오늘은 내가 올렸을 때만 포함
- `setMyProfile` → 모든 그룹 내 `defaultProfile` 반영
- `leave` → 목록에서 제거, `reset` → 그룹 0개

## Validation Steps

**Codex 는 Gradle 빌드·단위 테스트·lint·에뮬레이터 실행을 하지 않는다** (사용자 결정: 검증은 사용자가 Android Studio 로 직접 한다). Codex 가 할 것:

1. 작성한 코드를 스스로 다시 읽어 컴파일 오류 가능성(import 누락, 타입 불일치, 리소스 이름 오타 — `R.drawable.*`/`R.font.*` 가 실제 파일명과 같은지)을 점검
2. `git status` — 변경이 `android/` 와 루트 `.gitignore` 뿐인지 확인 (git 상태를 바꾸는 명령은 쓰지 않음)
3. `.agent/report.md` 에 아래를 적는다:
   - 사용한 라이브러리·AGP·Kotlin·Gradle 버전, compileSdk/targetSdk
   - wrapper jar 포함 여부
   - PNG 로 대체한 SVG 목록, 변환 방법
   - iOS 와 이름/타입을 다르게 한 부분 (대응표)
   - 기준 iOS 커밋 (`git log -1 --format=%h -- ios/`, 작성 시점 `45a4996`)
   - **사용자 검증 체크리스트**: Android Studio 로 `android/` 열기 → Gradle sync → `assembleDebug` → `testDebugUnitTest` → `lintDebug` → 에뮬레이터 실행 후 갤러리에서 확인할 항목(글꼴 적용, 아이콘 25·캐릭터 7·장식·로고 렌더링, 버튼 pressed/disabled, 토글·체크박스, `HTextField` error, `HAlert`·`HToast`, `CharacterPicker` 의 '내 사진' Photo Picker, `HCtaScaffold` 키보드 동작)
   - 자신 없는 부분(컴파일·동작 확인이 특히 필요한 곳)

## Potential Merge Conflicts

- 백엔드(`src/`)·iOS(`ios/`)·Cordova(`cordova/`) 작업과 파일이 겹치지 않는다.
- 루트 `.gitignore` 는 다른 작업이 함께 고칠 수 있다 — 추가만 하고 기존 줄은 건드리지 않는다.
- 작업 트리에 커밋되지 않은 대량 삭제(`android/` 예전 파일, `frontend/myApp` → `cordova/` 이동)가 있다. 새 `android/` 파일 경로가 예전 삭제 경로(`android/app/src/main/java/com/sapphire/hawe/...`, `android/build.gradle.kts` 등)와 일부 같아서 `git status` 에서 **삭제가 아닌 수정(M)** 으로 보일 수 있다. 정상이며, 사용자가 커밋할 때 예전 코드와 섞이지 않았는지만 diff 로 확인하면 된다.
- iOS 쪽 디자인 토큰·컴포넌트가 계속 바뀌는 중이면 Android 가 따라가야 한다. 보고서에 기준 iOS 커밋(`git log -1 --format=%h -- ios/`)을 적는다.
