# ANDROID.md — iOS(SwiftUI) 앱을 Android 네이티브로 옮기는 계획

> **음성 기능 제거 (2026-10-12):** 음성 미션·녹음·재생·파형·마이크 권한은 서비스에서 제거하기로 결정했다. 서버는 B0 에서 제거됐고, Android 코드는 A0, iOS 는 A5 항목 0 에서 삭제한다. 아래 문서의 음성 관련 내용(09 음성 화면, 13 음성 목록, `MediaRecorder`/`MediaPlayer`, `RECORD_AUDIO`)은 **제거됨 / 제거 예정**으로 읽는다.

## 6A 서버 제출·결과 연결 (2026-10-12)

오늘 미션의 사진·그림·글은(음성은 제거됨) `POST /api/missions/{id}/submissions`로 업로드해요. 업로드 중에는 버튼을 잠그고, 실패하면 입력을 유지해 다시 시도할 수 있어요. 재시도에서 `ALREADY_SUBMITTED`가 오면 서버의 제출 상태를 다시 조회해요. 서버 응답의 지각 여부를 성공 안내와 결과 화면에 표시해요.

결과 화면은 진입하거나 앱으로 돌아올 때 `GET /api/missions/{id}/submissions` 피드를 불러와요. 이미지는 서버 파일 URL을 사용해요(음성은 제거됨). 앱 안 임시 제출·로컬 파일 저장·제출 상태 합성은 제거했어요. 스티커(좋아요)·댓글은 7A 서버 연결 전까지 숨겨요. `DemoSeed`와 일부 로컬 기록·콕 로직은 테스트와 프리뷰를 위해 남아 있으며 제품 화면에서는 숨기거나 준비 중으로 안내해요.

아래 4A 및 초기 이식 단계의 임시 저장 설명은 당시 구현 기록이에요. 현재 제출과 결과의 데이터 출처는 서버예요.

## 4A 서버 미션 연결 (2026-10-12)

홈 카드는 그룹 목록의 `todayMission`, 05 화면은 `/api/groups/{id}/missions/today`의 현재 미션을 사용해요. 서버 미션 번호·내용·마감·다음 발행 시각·멤버 제출 상태를 표시하고, 발행 전에는 빈 상태와 다음 발행까지의 시간을 보여 줘요. 글의 빈칸 채우기는 100자, 삼행시는 3줄과 제시어 첫 글자를 검사해요. 30초 스케치는 첫 획부터 타이머를 적용해요.

제출·결과·피드는 6A 전까지 앱 안 임시 저장을 사용해요. 서버가 아직 모르는 내 제출은 앱이 켜져 있는 동안 화면에 합성하며 재시작하면 사라져요. 콕 버튼과 활동 요약은 숨기고 캘린더 진입은 준비 중으로 안내해요.

> 작성일 2026-10-12. 기준 코드: `ios/HAWE` (SwiftUI, 약 7,600줄, 67개 파일).
> 목표는 **iOS 앱과 같은 화면·같은 동작의 Android 앱**을 Kotlin + Jetpack Compose 로 만드는 것이에요.
> 서버 연결은 이 계획의 범위가 아니에요 (iOS 도 아직 앱 안 임시 저장소 `DataStore` 로만 동작해요).

---

## 1. 범위

### 하는 것

- iOS 의 모든 화면(A1~~A3, 비밀번호 찾기, 01~~17-5)을 Compose 로 1:1 이식
- 디자인 토큰(색·글꼴·모서리·여백), 아이콘·캐릭터·로고 SVG, 공통 컴포넌트 이식
- 임시 저장소 `DataStore` + `DemoSeed` 이식 (미션 생성 규칙, 체험 초대 코드 `K7Q2PX` / `LOVE22` / `FULL00` 포함)
- 기기 기능: 카메라·앨범, ~~녹음·재생~~(제거됨), 손그림, 공유·복사, 앱 안 웹 보기, 알림 권한·알림 눌림 처리

### 하지 않는 것 (iOS 도 아직 안 된 것)

- Spring 백엔드(`/api/...`) 연결, 실제 로그인·토큰 저장
- 간편 로그인(카카오·Apple·Google) 실제 연동 — 버튼 모양만 (MVP 범위 밖, `docs/FEATURE/PROJECT_OVERVIEW.md`)
- FCM 서버 푸시 수신 (아래 §8 결정 필요), 초대 링크 App Links 도메인 검증 (도메인 미정)
- 백엔드 설계(21개 미션 틀, 하루 여러 미션)와 iOS 모델(유형 4개, 하루 1미션)의 차이 맞추기 — iOS 를 먼저 맞춘 뒤 따라가요

---

## 2. 현재 상태


| 항목            | 내용                                                                                                                                                                                                  |
| ------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| iOS 소스        | `ios/HAWE/{App, Core, DesignSystem, Features, Resources}` — XcodeGen(`ios/project.yml`), iOS 17, 세로 고정, 라이트 모드 고정                                                                                   |
| 상태 관리         | `@Observable` 클래스 3개를 environment 로 주입: `AppSession`(로그인 상태·계정·알림 설정), `DataStore`(그룹·미션·제출·댓글), `PushCenter`(싱글턴)                                                                                  |
| 화면 이동         | `NavigationStack(path:)` + `MainRoute`/`AuthRoute`/`MyPageRoute` enum. 화면 사이에는 id 만 넘기고 내용은 `DataStore` 에서 읽음                                                                                       |
| 에셋            | `Assets.xcassets` 의 SVG 40개 (Icons 25 · Characters 7 · Decor 3 · Logo 2 · Social 3), 앱 아이콘 PNG, 글꼴 TTF 5개 (IBM Plex Sans KR 4종 + Fredoka SemiBold)                                                  |
| 이전 Android 시도 | `5354076`(main, 1단계: 디자인 시스템·A1\~A3·01) 과 `db678dc`(`origin/feat/ios-step1`, 2단계: DataStore·04·02·03·16·16-1) 에 Kotlin+Compose 코드가 있었음. **사용자 결정(2026-10-12): 이전 코드는 버리고 0단계부터 새로 만든다.** 참고·복원하지 않음 |
| 참고            | iOS 주석의 `CLAUDE.md §N` 은 이 저장소에 없는 iOS 기획 문서를 가리켜요. 규칙은 iOS 코드 주석을 기준으로 삼아요                                                                                                                         |


---

## 3. 기술 선택


| 항목      | 선택                                                                                                                                                                         | 이유                                                                           |
| ------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------- |
| 언어/UI   | Kotlin + Jetpack Compose (Material3 는 바탕만, 모양은 전부 직접 그림)                                                                                                                   | SwiftUI 와 구조가 가장 비슷해 화면 단위로 옮기기 쉬움                                           |
| SDK     | minSdk 26 / target·compile 35, JDK 17                                                                                                                                      | 26 부터 `java.time` 기본 지원 (iOS 의 `Calendar`/`DateComponents` 대체). 이전 시도와 같음    |
| 패키지     | `com.sapphire.hawe` (iOS 번들 ID 와 같음)                                                                                                                                       |                                                                              |
| 화면 이동   | 단일 Activity + Navigation Compose (2.8+, `@Serializable` 타입 안전 라우트)                                                                                                         | `MainRoute` enum 을 그대로 sealed 라우트로 옮길 수 있음                                   |
| 상태      | `AppSession`, `DataStore` 를 Compose 상태(`mutableStateOf`/`mutableStateListOf`)를 가진 일반 클래스로 만들고 `CompositionLocal` 로 주입. Activity 범위 ViewModel 하나가 둘을 들고 있어 화면 회전·구성 변경에도 유지 | iOS `@Observable` + `.environment()` 와 1:1. 인증 토큰은 암호화 저장하고, 그룹·미션 목업 상태는 영속화하지 않음 |
| 이미지 로딩  | Coil 3 (`AsyncImage`)                                                                                                                                                      | 내 사진·제출 사진(로컬 파일 Uri) 표시                                                     |
| 카메라     | CameraX (`camera-camera2`, `camera-lifecycle`, `camera-view`)                                                                                                              | AVCaptureSession 대체                                                          |
| 웹 보기    | `androidx.browser` Custom Tabs                                                                                                                                             | SFSafariViewController 대체                                                    |
| 빌드 스크립트 | Gradle Kotlin DSL + 버전 카탈로그(`gradle/libs.versions.toml`)                                                                                                                   |                                                                              |
| 위치      | 저장소 루트 `android/` (iOS 의 `ios/` 와 나란히)                                                                                                                                     |                                                                              |


서버 연결(2A)부터 OkHttp(+kotlinx-serialization)를 사용해요. Retrofit·Hilt·Room은 쓰지 않아요.

인증 토큰은 Android Keystore AES-GCM 키로 암호화해 SharedPreferences에 저장해요. 앱 시작 시 저장된 토큰으로 `/api/me`를 호출해 세션을 복구해요. `BuildConfig.API_BASE_URL` 기본값은 debug 에뮬레이터용 `http://10.0.2.2:8080`, release 임시 주소 `https://api.hawe.app`예요. 빌드 시 `-PhaweApiBaseUrl=...`로 바꿀 수 있어요. 평문 HTTP는 debug 빌드에서만 허용해요.

---

## 4. 폴더 구조와 파일 대응

iOS 폴더 구조를 그대로 따라요. 파일 하나 = 화면 하나를 기본으로 하고, 작은 화면은 묶어도 돼요.

```
android/app/src/main/java/com/sapphire/hawe/
  MainActivity.kt              ← HAWEApp.swift (+ AppDelegate 의 알림 처리 진입점)
  app/
    RootScreen.kt              ← App/RootView.swift
    MainFlow.kt                ← App/MainFlowView.swift (MainRoute → NavHost)
  core/
    AppSession.kt              ← Core/AppSession.swift
    AppLinks.kt                ← Core/AppLinks.swift (약관·개인정보 URL, 초대 링크)
    data/DataStore.kt          ← Core/Data/DataStore.swift (+ TopicPool)
    data/DemoSeed.kt           ← Core/Data/DemoSeed.swift
    model/*.kt                 ← Core/Models/*.swift (6개)
    push/PushCenter.kt         ← Core/Push/PushNotifications.swift
  designsystem/
    tokens/{Color,Type,Layout}.kt         ← Tokens/*
    assets/{HIcon,ProfileCharacter,Relationship}.kt ← Assets/*
    brand/HaweLogo.kt          ← Brand/HAWELogo.swift
    components/*.kt            ← Components/* (12개, SafariView 는 CustomTabs.kt)
    layout/HCtaScaffold.kt     ← Layout/HCTAScaffold.swift (FlowLayout 은 Compose FlowRow 로 대체)
  feature/
    auth/       ← Features/Auth (AuthFlow, Login, SignupAccount, SignupProfile, PasswordReset)
    groups/     ← Features/Groups (Start, Home, CreateGroup, JoinGroup, GroupInfo, GroupProfile)
    mission/    ← Features/Mission (Mission, MissionPerform, Draw, Photo, Write, ~~Voice~~ 제거됨)
    results/    ← Features/Results (Results, ResultComponents, ResultFeed, SubmissionDetail)
    records/    ← Features/Records (RecordCalendar)
    mypage/     ← Features/MyPage (MyPage, NotificationSettings, ProfileEdit, UserInfo+ChangePassword, AppInfo+Licenses, Withdraw)
android/app/src/main/res/
  drawable/   ← SVG → VectorDrawable (ic_*, character_*, decor_*, logo_*, social_*)
  font/       ← TTF 5개 (파일 이름은 소문자_밑줄)
  mipmap-*/   ← 앱 아이콘 (adaptive icon)
```

`ComponentGalleryView` 는 앱 화면이 아니라 `@Preview` 모음 파일 하나로 옮겨요.

---

## 5. iOS → Android API 대응표


| iOS                                                                       | Android                                                                                                                                        | 주의                                                                                                |
| ------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------- |
| `NavigationStack(path:)`, `path.append`, `path = [x]`, `path.removeAll()` | `navController.navigate(route)`, `navigate(x) { popUpTo<Home>() }`, `popBackStack<Home>(inclusive=false)`                                      | 02 만들기 완료 → 16 그룹 정보(뒤로 가면 홈), 03 참여 완료 → 홈, 16 나가기 → 홈 처리를 그대로 맞춰요                               |
| `store.groups.isEmpty ? StartView : HomeView` (루트 분기)                     | 시작 목적지를 `Home` 하나로 두고 그 안에서 그룹 수에 따라 01/04 를 그림                                                                                                | 그룹 0 → 01, 01 의 `showsBack` 은 + 로 열었을 때만                                                          |
| `.fullScreenCover` (05 → 06\~09 수행)                                       | 별도 라우트 `MissionPerform(groupId)` (아래에서 올라오는 전환)                                                                                                | 쓰던 내용이 있으면 닫기 전에 확인 — `BackHandler` 로 시스템 뒤로 가기도 같은 확인을 거침                                        |
| `.sheet` + `DatePicker(.graphical)` (02-1 우리의 1일)                         | `ModalBottomSheet` + Material3 `DatePicker` (오늘 이후 선택 불가 `SelectableDates`)                                                                    | 색은 ink/accent 토큰으로 덮어써요                                                                           |
| `.sheet` + `DatePicker(.wheel)` (16 주제 도착 시간)                             | `ModalBottomSheet` + 시·분 휠(직접 구현, `LazyColumn` 스냅) 또는 Material3 `TimePicker`                                                                   | 휠 모양을 지킬지 §8 결정 4                                                                                 |
| `.alert`, `.confirmationDialog`                                           | 직접 만든 `HAlert`(Dialog), 하단 액션 시트는 `ModalBottomSheet`                                                                                           | 시스템 `AlertDialog` 를 쓰지 않음 — Figma 모양 유지                                                           |
| `HToast` overlay + `.animation`                                           | `Box` 오버레이 + `AnimatedVisibility`, `LaunchedEffect` 로 자동 숨김                                                                                    |                                                                                                   |
| `NavigationSwipeBack` (밀어서 뒤로)                                            | 시스템 뒤로 가기 / 예측 뒤로 가기(`enableOnBackInvokedCallback`)                                                                                            | 별도 구현 불필요                                                                                         |
| `HCTAScaffold` 키보드 위로 CTA                                                 | `enableEdgeToEdge()` + `Modifier.imePadding()` / `windowInsetsPadding`                                                                         | `adjustResize` 대신 insets 로 처리                                                                     |
| `Font.custom` + `plexLineHeight`                                          | `FontFamily(Font(R.font.…, weight))` + `TextStyle(lineHeight = size*배수, lineHeightStyle = Center/Trim.None)`, `includeFontPadding = false`     | Figma 줄 높이를 맞추는 게 핵심 — 토큰 단계에서 확인                                                                 |
| `Color(hex:)` 토큰                                                          | `object HColors { val ink = Color(0xFF111111) … }`                                                                                             | 값은 `Color+HAWE.swift` 그대로 (accent `#67A0C3`, pressed `#558BB0`)                                   |
| SVG imageset (template 렌더링)                                               | VectorDrawable + `Icon(painterResource, tint=…)`                                                                                               | 아이콘은 tint 가능해야 함. 캐릭터·로고는 tint 없이 `Image`                                                         |
| SF Symbol `house.fill`, `ellipsis` (관계 장식 가족·그저그런사이)                      | Material Icons `Home`, `MoreHoriz` 또는 같은 모양 VectorDrawable                                                                                     | iOS 도 임시 표시 — Figma 에 장식이 생기면 교체                                                                  |
| `FlowLayout`                                                              | `FlowRow`                                                                                                                                      |                                                                                                   |
| `LazyVGrid`                                                               | `LazyVerticalGrid` (스크롤 안에 있으면 고정 개수 Row 로)                                                                                                    | 중첩 스크롤 충돌 주의                                                                                      |
| `PhotosPicker` (프로필 사진, 07 앨범)                                            | Photo Picker `ActivityResultContracts.PickVisualMedia(ImageOnly)`                                                                              | 권한 필요 없음. 받은 Uri 는 앱 캐시로 복사해 보관                                                                   |
| `AVCaptureSession`/`AVCapturePhotoOutput` (07)                            | CameraX `Preview` + `ImageCapture`, `PreviewView`(FILL\_CENTER)                                                                                | 플래시(`flashMode`), 전·후면 전환, 3:4 크롭, 전면 카메라 좌우 반전, `CAMERA` 런타임 권한                                  |
| `ImageRenderer` (사진 3:4 크롭, 그림 PNG)                                       | `Bitmap.createBitmap` + `android.graphics.Canvas` → `compress(JPEG/PNG)`                                                                       | EXIF 회전 보정 필요 (CameraX `ImageProxy.imageInfo.rotationDegrees`)                                    |
| `PencilKit` `PKCanvasView` (06)                                           | Compose `Canvas` + `pointerInput` 로 획(Path) 목록 관리, 되돌리기/다시 실행 스택                                                                               | 펜/지우개(획 단위가 아니라 흰색 덧칠 or `BlendMode.Clear`), 굵기 3단계, 7색 — iOS `DrawingCanvasModel` 동작에 맞춤. 손가락 입력 |
| ~~`AVAudioRecorder` (09, 최대 30초, m4a/AAC)~~ 제거됨                                   | `MediaRecorder` (`MPEG_4` + `AAC`, `setMaxDuration(30_000)`)                                                                                   | 파형은 `getMaxAmplitude()` 를 주기적으로 읽어 0..1 로 정규화. `RECORD_AUDIO` 권한, 거부 시 설정 열기                      |
| ~~`AVAudioPlayer` (미리 듣기, 13 재생·이어 듣기)~~ 제거됨                                      | `MediaPlayer`                                                                                                                                  | 한 번에 하나만 재생, 화면 떠나면 정지. 파일 없는 목업은 길이만큼 '재생하는 척'(iOS 와 같음)                                         |
| `Timer` (녹음 진행, 05 카운트다운)                                                 | `LaunchedEffect` + `delay` 루프                                                                                                                  |                                                                                                   |
| `openSettingsURLString`                                                   | `Settings.ACTION_APPLICATION_DETAILS_SETTINGS` 인텐트                                                                                             |                                                                                                   |
| `ShareLink` (초대 링크 공유)                                                    | `Intent.ACTION_SEND` + `createChooser`                                                                                                         |                                                                                                   |
| `UIPasteboard` (초대 코드 복사)                                                 | `ClipboardManager.setPrimaryClip`                                                                                                              | Android 13+ 는 시스템이 복사 알림을 띄우므로 그 버전에서는 앱 토스트를 생략                                                  |
| `SafariView` (약관·개인정보)                                                    | Custom Tabs                                                                                                                                    |                                                                                                   |
| `mailto:` (문의하기)                                                          | `Intent.ACTION_SENDTO`, `Uri.parse("mailto:…")`                                                                                                | 메일 앱 없을 때 예외 처리                                                                                   |
| StoreKit `requestReview` (앱 평가하기)                                         | Play Store 페이지 인텐트 (`market://details?id=…`, 실패 시 https)                                                                                       | Play In-App Review 는 출시 전엔 동작 안 해서 일단 링크로                                                         |
| `UNUserNotificationCenter` 권한 요청 (로그인 후 첫 진입)                             | Android 13+ `POST_NOTIFICATIONS` 런타임 권한 (12 이하는 요청 없음)                                                                                         |                                                                                                   |
| APNs 알림 눌림 → `PushCenter.pendingDestination`                              | 알림 `PendingIntent` extras(`type`, `group_id`, `submission_id`) → `MainActivity.onNewIntent`/`onCreate` 에서 파싱 → `PushCenter.pendingDestination` | 이동 규칙은 `MainFlowView.open()` 그대로: topic/poke/friend\_done → 05, comment → (내가 올렸으면) 05 → 10\~13   |
| `UUID`, `Date`, `DateComponents`, `Calendar(Asia/Seoul)`                  | `java.util.UUID`, `Instant`/`ZonedDateTime`, `LocalTime`, `ZoneId.of("Asia/Seoul")`                                                            | 미션 창(`TodayMission.window`), 연속 참여일, 캘린더는 **반드시 서울 시간대**                                          |
| `AvatarContent.photo(URL)` / `.image(UIImage)`                            | `AvatarContent.Photo(uri: Uri)` 하나로 통합                                                                                                         |                                                                                                   |
| `Info.plist` 권한 문구                                                        | 권한 요청 전 안내는 iOS 문구 재사용, `AndroidManifest` 에 `CAMERA`, ~~`RECORD_AUDIO`~~(제거됨), `POST_NOTIFICATIONS`                                                     |                                                                                                   |
| `UIUserInterfaceStyle: Light`, 세로 고정                                      | 테마 `Theme.Material3.Light.NoActionBar`, `android:screenOrientation="portrait"`                                                                 | 다크 모드 무시                                                                                          |


---

## 6. 단계별 작업

각 단계는 **빌드되고 실행해서 확인 가능한 상태**로 끝나요. 단계 하나 = 커밋(PR) 하나를 권장해요.
커밋 메시지는 `docs/GIT_GUIDE/GIT_COMMIT_GUIDE.md` 형식(`feat(android): …`).

### 0단계 — 프로젝트 뼈대

- `android/` Gradle 프로젝트 (wrapper, `settings.gradle.kts`, `libs.versions.toml`, `app/build.gradle.kts`), `.gitignore`(`local.properties`, `build/`, `.gradle/`)
- `MainActivity`(edge-to-edge, 세로 고정, 라이트 테마), 빈 `RootScreen`
- 완료 기준: `./gradlew assembleDebug` 성공, 에뮬레이터에서 빈 화면 실행

### 1단계 — 디자인 시스템

- 토큰: 색(`Color+HAWE.swift` 전부), 글꼴(Plex 4종 + Fredoka, 자주 쓰는 크기 `screenTitle`·`navTitle`·…), 모서리(`HRadius`), 여백(`HLayout`)
- 에셋: SVG 40개 → VectorDrawable (Android Studio Vector Asset 또는 `svg2vd`). 변환 후 그라데이션·마스크가 깨지지 않았는지 캐릭터 7종은 눈으로 확인. 앱 아이콘 adaptive icon
- 컴포넌트 12개 + `HCtaScaffold` + `HToast` + 로고/워드마크(아포스트로피만 accent) + `MemberRow`(아바타 34, 흰 테두리 2, 장식 20, 5pt 겹침)
- 완료 기준: 갤러리 `@Preview` 에서 iOS `ComponentGalleryView` 와 나란히 비교해 크기·색·글꼴이 맞음

### 2단계 — 모델 · 임시 저장소 · 세션

- `Core/Models` 6개 → Kotlin `data class`/`enum class` (iOS `Hashable struct` → `data class`, `id` 는 `UUID`)
- `DataStore` 공개 메서드를 **이름·의미 그대로** 옮김 (나중에 서버 연결 시 이 클래스 안쪽만 바꾸는 구조 유지):
`setMyProfile`, `reset`, `stats`, `group`, `summaries`, `createGroup`, `lookUpInvite`, `join`, `leave`, `setTopicTime`, `setNotify`, `setMyGroupProfile`, `refreshTodayMissions`, `todayMission`, `submit`, `poke`, `result`, `toggleLike`, `addComment`, `deleteComment`, `records`
- 규칙 그대로: 초대 코드 6자리(0·O·1·I 제외, 고유), 미션 유형 3일 연속 금지, 주제 30일 내 재사용 금지, 제출 1회(수정·삭제 불가), 결과는 내가 올린 미션만, 콕 찌르기 하루 1회, 연인 그룹 최대 2명·나머지 6명, 그룹 나가기 시 목록에서만 제거
- 제출 미디어는 `cacheDir/hawe-media/` 에 저장 (iOS `saveMedia` 와 같음)
- `AppSession`: `signIn`/`signOut`/`withdraw`(+ `DataStore.reset`), `NotificationPrefs`
- 완료 기준: `DataStore` 단위 테스트 (JUnit, 시각 주입) — 미션 창 경계(09:00 전/후, 서울 기준), 연속 참여일, 초대 코드 오류 3종, 유형 연속 규칙

### 3단계 — 인증 흐름 (A1 · A2 · A3 · 비밀번호 찾기)

- `AuthRoute` → 별도 NavHost. 간편 로그인 신규 가입은 A2 를 건너뛰고 A3
- A2 입력 검사·약관 체크(전체 동의 포함)·약관 Custom Tabs, A3 `CharacterPicker`(캐릭터 7 + 내 사진, 기본 몽실이, 닉네임 2–10자)
- 완료 기준: 로그인/가입 후 그룹 0개 → 01 시작으로 진입

### 4단계 — 그룹 (01 · 04 · 02 · 02-1 · 03 · 16 · 16-1)

- `MainRoute` → sealed 라우트 + NavHost, 위 §5 의 스택 조작 규칙
- 04 홈 카드(연인 그룹 'D+N일' — 시작일을 1일째로), 02 이름 최대 16자·관계 장식 미리보기·연인 날짜 시트, 03 6자리 입력(**한글 IME 조합 중 대문자 변환으로 글자가 빠지는 문제** — 이전 Android 2단계에서 발견. `KeyboardOptions(capitalization=Characters, keyboardType=Ascii)` + 확정된 값만 정규화), 16 복사·공유·멤버·주제 시간(방장만)·알림 토글·나가기 확인, 16-1 그룹 내 프로필
- 완료 기준: 체험 코드 `K7Q2PX`(참여 가능)·`LOVE22`·`FULL00`(정원 초과)·잘못된 코드·내 그룹 코드(이미 참여 중) 모두 iOS 와 같은 결과

### 5단계 — 오늘의 미션 (05)

- 유형·주제·#번호, 다음 미션까지 카운트다운(초 단위 갱신, 마감 지나면 `refreshTodayMissions`), 멤버 완료 현황, 콕 찌르기(하루 1회, 토스트), 미제출 시 잠금 안내, 헤더의 캘린더·그룹 정보
- 완료 기준: 제출 전/후 화면 차이, 콕 찌르기 중복 불가

### 6단계 — 미션 수행 (06 그림 · 07 사진 · 08 글 · ~~09 음성~~ 제거됨) — **가장 위험한 단계**

- 공통: `MissionTitle`, `MissionHeader`(닫기 시 작성 중이면 확인), 올리기 → `DataStore.submit` → 05 로 돌아와 "오늘의 미션을 올렸어요" 토스트
- 06: Compose 그림판 (펜/지우개, 굵기 3, 7색, undo/redo) → PNG
- 07: CameraX (플래시·전후면) + 앨범, 3:4 크롭 → JPEG. 권한 거부 화면
- 08: 최대 300자, 글자 수 표시
- ~~09: 녹음 최대 30초 자동 정지, 정지·다시·미리 듣기, 파형(막대 4·간격 4·높이 80, 녹음 구간 ink / 나머지 waveformIdle), 권한 거부 시 설정 열기 알림~~ — 음성 제거로 삭제(A0)
- 완료 기준: **실기기**에서 네 유형(음성 제거 후 세 유형) 모두 제출 → 결과 화면에 보임. 회전·백그라운드 전환 시 카메라/녹음 자원 해제 확인

### 7단계 — 결과 · 상세 · 기록 (10~15)

- 10 사진 세로 피드, 11 그림 2열 격자, 12 글 카드, ~~13 음성 목록 + 전체 이어 듣기~~(제거됨), 아직 안 올린 멤버 '아직' 표시
- 14 작품 상세: 좋아요 토글(채운 하트), 댓글 목록·입력(키보드 위 고정), 내 댓글 삭제 확인. 10~13 과 상태 공유(iOS `ResultFeed` → 화면 범위 상태 홀더)
- 15 캘린더: 월 이동, 기록 있는 날 유형 이름, 오늘 테두리, 날짜 선택 → 카드 → '모아보기'(내가 올린 날만 열림)
- 완료 기준: 좋아요·댓글이 10~13 ↔ 14 에서 같이 반영

### 8단계 — 마이페이지 (17 · 17-1 ~ 17-5)

- 17 프로필·활동 요약(완료 미션·연속 참여일·참여 그룹)·유형별 횟수·메뉴, 17-4 로그아웃 확인
- 17-1 알림 설정(전체 OFF 면 나머지 비활성, 친구 완료 기본 OFF), 프로필 편집(기본 프로필 바꾸면 그룹 내 프로필을 안 쓰는 그룹에 반영), 17-2 유저 정보(연결/해제, 비밀번호 변경은 이메일 가입자만), 17-3 앱 정보(버전·문의·평가·라이선스), 17-5 탈퇴(동의 체크 후 danger 버튼 → 모든 데이터 초기화 → A1)
- 완료 기준: 탈퇴 후 재가입하면 그룹 0개 상태

### 9단계 — 알림 · 마무리

- `PushCenter`: 로그인 후 첫 진입에 권한 요청, 알림 채널 4개(topic/poke/friend_done/comment), 알림 눌림 → 화면 이동
- 테스트 수단: 디버그 빌드 전용으로 로컬 알림을 띄우는 방법(예: `adb shell am start` 로 extras 넣어 실행) — `ios/PushSamples/*.apns` 의 payload 와 같은 키 사용
- 초대 링크 `https://hawe.app/invite/{code}` 인텐트 필터는 도메인 확정 전까지 보류 (iOS 도 아직 링크 수신 처리 없음, 03 의 `initialCode` 파라미터만 지원)
- 완료 기준: 알림 4종을 눌렀을 때 iOS `MainFlowView.open()` 과 같은 화면으로 이동

---

## 7. 위험 요소와 주의할 점

1. **카메라·녹음·그림판** 은 SwiftUI 코드를 옮기는 게 아니라 새로 짜는 부분이에요. 6단계에 시간을 가장 많이 잡고, 에뮬레이터가 아니라 실기기로 확인해요.
2. **시간대**: 기기 시간대가 서울이 아니어도 미션 창·연속 참여일·캘린더가 서울 기준이어야 해요. `ZoneId.systemDefault()` 를 쓰지 말고 상수 하나(`HaweTime.SEOUL`)로 통일.
3. **줄 높이·글꼴 여백**: Compose 기본 `includeFontPadding` 과 Plex 의 큰 ascent 때문에 iOS 보다 글자가 아래로 밀려 보일 수 있어요. 1단계에서 `TextStyle` 기본값을 정해 두고 화면마다 손대지 않게 해요.
4. **IBM Plex Sans KR TTF 가 개당 약 2.8MB** (4종 약 11MB). 앱 크기가 신경 쓰이면 Downloadable Fonts 나 서브셋을 검토 (지금은 iOS 와 같이 그대로 넣음).
5. **SVG → VectorDrawable 변환 손실**: `clipPath`/`mask`/그라데이션이 있는 SVG 는 변환이 깨질 수 있어요. 깨지면 그 파일만 PNG(xxhdpi) 로 대체.
6. **뒤로 가기**: Android 에는 하드웨어/제스처 뒤로 가기가 있어서 iOS 에 없는 경로가 생겨요. 06~09 작성 중 닫기 확인, 02 만들기 직후 16 에서 뒤로 → 홈, 로그인 화면에서 뒤로 → 앱 종료를 명시적으로 처리.
7. **프로세스 종료**: 백그라운드에서 시스템이 앱을 죽이면 임시 저장소가 사라져요. iOS 와 같은 동작이라 허용하되, 화면 복원 시 없는 groupId 로 들어와도 터지지 않게(iOS 의 `TodayMission.empty` 처럼) 빈 상태를 그려요.
8. **iOS 와 동시 수정**: iOS 쪽 화면이 계속 바뀌는 중이면 Android 가 따라가야 해요. 각 단계를 시작할 때 해당 iOS 파일의 최신 커밋을 기준으로 삼고, 단계 PR 설명에 기준 iOS 커밋 해시를 적어요.

---

## 8. 정해야 할 것

1. ~~이전 Android 코드를 살릴지~~ — **결정됨(2026-10-12): 버리고 0단계부터 새로 만든다.**
2. **Material3 컴포넌트 사용 범위** — DatePicker 처럼 직접 만들기 비싼 것만 Material3 를 쓰고 색만 덮어쓸지, 전부 직접 그릴지. 추천: 날짜 선택만 Material3.

1. **16 주제 도착 시간 휠** — iOS 휠 모양을 직접 구현할지, Material3 `TimePicker`(시계 모양)로 갈지. 추천: 휠 직접 구현(Figma 일치).
2. **배포 서명·applicationId 확정** — Play Console 등록 전에 정하면 돼요. 지금은 `com.sapphire.hawe`.

---

## 9. 검증 방법

### 3A 그룹 서버 연결

Android 홈의 그룹 목록, 그룹 만들기, 초대 코드 미리보기·참여, 그룹 정보·멤버, 그룹 나가기는 `/api/groups`와 `/api/invites`를 사용해요. 그룹과 멤버 ID는 서버의 숫자 ID를 사용하고, 로그인 후 그룹 목록을 다시 불러와요. 미션·제출·결과는 4A·6A 전까지 그룹 ID에 연결된 앱 안 임시 데이터예요. 후임자 선택, 그룹 알림·주제 시간 변경, 그룹 내 프로필 편집은 현재 "준비 중"으로 표시해요.

- 단계마다: `cd android && ./gradlew assembleDebug lint` 통과
- 2단계 이후: `./gradlew testDebugUnitTest` (DataStore 규칙 테스트)
- 화면 단계: iOS 시뮬레이터와 Android 에뮬레이터(Pixel 계열, 1080×2400)를 같은 데이터(체험 코드 `K7Q2PX`)로 띄워 화면별 나란히 비교 — 크기·여백·글꼴·색·문구
- 6·9단계: 실기기에서 카메라·녹음·권한 거부·알림 확인
- 최종: 아래 시나리오를 처음부터 끝까지
  1. 가입(A2→A3) → 01 → 02 그룹 만들기 → 16 코드 복사·공유 → 홈
  2. 03 `K7Q2PX` 참여 → 05 → 네 유형 중 오늘 유형 제출 → 10~13 → 14 좋아요·댓글·삭제
  3. 15 캘린더에서 지난 날 모아보기, 안 올린 날 막힘
  4. 17 활동 요약 숫자 확인 → 알림 설정 → 프로필 편집 반영 → 로그아웃 → 다시 로그인 → 탈퇴 → 그룹 0개
