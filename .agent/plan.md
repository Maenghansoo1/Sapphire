# Plan — Android 6단계 (1/3): 미션 수행 공통 틀 + 08 글 + 07 사진

## Goal

iOS 의 미션 수행 화면 중 **08 글**과 **07 사진**, 그리고 둘이 함께 쓰는 **공통 틀**(전체 화면으로 올라오는 수행 화면, 공통 헤더/제목, "그만둘까요?" 확인)을 Android(Compose)로 옮긴다. 06 그림 / 09 음성은 다음 계획(6단계 2/3, 3/3)에서 한다.

- 05 오늘의 미션에서 CTA(`글 쓰기` / `사진 찍기`)를 누르면 수행 화면이 아래에서 올라온다. 올리면 05 로 돌아와 토스트 "오늘의 미션을 올렸어요" 가 뜨고, 05 가 "올린 뒤" 상태(완료 수 +1, 잠금 안내 제거, CTA "친구들 작품 보기")로 바뀐다.
- 08: 최대 300자 글쓰기 + 글자 수 표시. 07: 카메라(후면/전면, 플래시) 촬영 또는 앨범 선택 → 3:4 중앙 크롭 → "다시 찍기 / 올리기".
- 작성 중 내용이 있는데 닫기(X) 또는 시스템 뒤로 가기를 누르면 "그만둘까요?" 확인창.
- 06 그림 / 09 음성 유형의 05 CTA 는 지금 동작(디버그 임시 제출 / 릴리스 "준비 중이에요")을 유지한다.

전체 로드맵은 `docs/FRONTEND/ANDROID.md` (§5 대응표 — 카메라/사진 선택/크롭 행, §6 6단계, §7 위험 요소).

이번 범위 아님: 06 그림, 09 음성(녹음 권한 포함), 10~15, 17 계열, 알림, 서버 연결(제출은 `DataStore` 임시 저장소에만).

## Findings

### Android 현재 상태 (5단계까지 완료, 폰에서 확인됨)
- `app/MainNavigation.kt`: `MainRoute` = `Home`, `Start`, `CreateGroup`, `JoinGroup(code)`, `GroupInfo(groupId: String)`, `GroupProfile(groupId)`, `Mission(groupId)`. 각 UUID 라우트는 `runCatching { UUID.fromString }` + **진입 시 한 번만** `LaunchedEffect(entry)` 로 방어(없는 id → `popBackStack<Home>(false)`). 화면 컴포저블은 그룹/미션이 사라져도(퇴장 애니메이션 중) **내비게이션 없이 빈 배경만** 그린다 — 이 규칙(4단계에서 발견된 "나가기 직후 빈 화면" 버그의 재발 방지)을 새 화면에도 적용한다.
- `feature/mission/MissionScreen.kt` (5단계): `MissionScreen(id, store, onBack, onGroupInfo)`. CTA 클릭은 `onPerform` 한 곳에 모여 있다: 디버그 빌드에서 `store.submit(SubmissionContent.Text("개발용 임시 제출"), id)` + 토스트 "오늘의 미션을 올렸어요", 릴리스는 토스트 "준비 중이에요". 이미 올렸으면 CTA 는 "친구들 작품 보기"(토스트). 토스트는 `rememberHToastState()` + `HToastHost(toast)`.
- `core/data/DataStore.kt`: `todayMission(id): TodayMission?`(마지막 미션, `type`, `topic`, `didSubmit`…), `submit(content: SubmissionContent, groupID)` — 마지막 미션에 내가 이미 올렸으면 무시(유형 검사 없음). `SubmissionContent.Image(bytes: ByteArray, fileExtension: String)` → 생성자에 받은 `mediaDir`(앱에서는 `cacheDir/hawe-media`)에 `UUID.ext` 로 저장하고 `Submission.mediaURL`(java.net.URI) 에 넣는다. `SubmissionContent.Text(body)` → `Submission.text`.
- 디자인 시스템(사용 가능): `HNavBar(title, leading: NONE/BACK/CLOSE, onLeading, trailing)`(제목은 좌우 최소 52dp 여백, 오른쪽 컨트롤 폭을 측정해 반영), `HButton(title, modifier, kind: PRIMARY/SECONDARY/GRAY/DANGER, height, enabled, onClick)`, `HAlert(title, message, confirmTitle, onCancel, onConfirm, cancelTitle = "취소", confirmKind = DANGER)`, `HCtaScaffold(horizontalPadding, bottomInset, content, cta)` + `ctaBottomPadding(bottomInset)`, `HToast`/`HToastState`, `HIconView`, `HIcon.FLASH/FLIP/IMAGE/CAMERA/CLOSE`, `HType.plex(size, weight, lineHeightMultiple)`, `HColors`, `HRadius`(card=20, largeCard=24, input=14).
- `HCtaScaffold.kt` 하단 `@Preview` 의 문자열이 **깨져 있다**(`"怨꾩젙??留뚮뱾?댁슂"`, `"?ㅼ쓬"` — 3단계 수정 중 인코딩이 망가진 흔적, 프리뷰 전용이라 동작에는 영향 없음). 이 파일의 해당 두 문자열만 `"계정을 만들어요"` / `"다음"` 으로 되돌린다(다른 파일에서는 깨진 문자열을 찾지 못했다).
- `app/src/main/AndroidManifest.xml`: 권한·`uses-feature` 없음. `gradle/libs.versions.toml` / `app/build.gradle.kts`: CameraX 의존성 없음. minSdk 26, compileSdk 36, Compose BOM 2025.10.01, navigation-compose 2.9.5, activity-compose 1.11.0.
- 시간/서울 규칙은 이번 화면과 무관.

### 원본 iOS (읽기 전용, 수정 금지)
- `ios/HAWE/Features/Mission/MissionPerformView.swift` — 컨테이너(유형별 분기, `submit` 후 dismiss) + `MissionTitle` + `MissionHeader`(+닫기 확인)
- `ios/HAWE/Features/Mission/WriteMissionView.swift` — 08
- `ios/HAWE/Features/Mission/PhotoMissionView.swift` — 07 + `CameraModel`, `CameraPreview`, `UIImage.croppedToThreeByFour`
- `ios/HAWE/Features/Mission/MissionView.swift` — 05 (`fullScreenCover` → `MissionPerformView`, 제출 후 `store.submit` + 토스트)

### iOS 명세 (그대로 구현)

**수행 컨테이너 (`MissionPerformView`)**: 전체 화면(위에서 아래로 덮음), 배경 `bg`. 유형에 따라 06/07/08/09 분기. 제출 시 `store.submit(content, groupID)` → 화면 닫기 → 05 에서 토스트 "오늘의 미션을 올렸어요".

**공통 제목 (`MissionTitle`)**: "오늘의 {유형 표시명}"(13 SemiBold `textSecondary`) + 주제(20 Bold `ink`, 줄바꿈 허용), 간격 4, 위 4, 폭 가득·왼쪽 정렬.

**공통 헤더 (`MissionHeader`)**: `HNavBar(leading: close)` + 오른쪽 슬롯(화면별). 쓰던 내용이 있으면(`hasDraft`) 닫기에서 확인창: 제목 "그만둘까요?", 메시지 "지금까지 한 내용은 저장되지 않아요.", 취소 쪽 "계속하기"(회색), 확정 "그만두기"(danger). 내용이 없으면 바로 닫는다.

**08 글 (`WriteMissionView`)** — 좌우 20(`screenPaddingCompact`)
- 헤더 오른쪽: `"{글자 수} / 300"`(13 `caption13`, textSecondary, 고정폭 숫자). `hasDraft = trim 한 글이 비어 있지 않음`.
- `HCtaScaffold(horizontalPadding = 20, bottomInset = 32)`: 위 16, 간격 16, `MissionTitle(WRITE, 주제)` + 에디터.
- 에디터: 테두리 `line` 1dp, 모서리 `HRadius.card`(20), 높이 **360**, 안쪽 20, 17pt 글자(ink), **줄 높이 1.7**(`HType.plex(17, lineHeightMultiple = 1.7f)`), 비어 있으면 "여기에 적어보세요"(17, textTertiary) 안내. 에디터 영역을 누르면 포커스·키보드. 커서 색 ink.
- 글자 수 제한 300자: 초과 입력/붙여넣기는 300자로 잘린다. **iOS 는 `String.count`(보이는 글자 단위)** 이므로 Android 도 UTF-16 길이가 아니라 **글자(grapheme) 단위**로 센다(이모지 😀 하나 = 1). `java.text.BreakIterator.getCharacterInstance()` 로 세고 자르는 순수 함수를 둔다.
- CTA "올리기": trim(공백·줄바꿈 양끝 제거)한 글이 비면 비활성. 누르면 `SubmissionContent.Text(trimmed)`.

**07 사진 (`PhotoMissionView`)** — 좌우 20, 세로 간격 16
- 헤더 오른쪽: 사진이 아직 없고 카메라가 쓸 수 있을 때만 플래시 토글(`HIcon.FLASH` 22, 켜짐 = ink / 꺼짐 = textTertiary, 접근성 라벨 "플래시 켜기"/"플래시 끄기", 터치 영역 넓게). `hasDraft = 사진이 있음`.
- `MissionTitle(PHOTO, 주제)`.
- **미리보기**: 배경 `surfaceStrong`, 모서리 24(`largeCard`), 비율 **3:4**, 폭은 가용 폭, 높이가 모자라면 비율을 유지한 채 줄어들고 가운데 정렬(iOS `aspectRatio(3/4, .fit)` + `layoutPriority(-1)`). 내용: 찍은/고른 사진(가득 채움, 넘치면 클립) / 없으면 카메라 미리보기(가득 채움) / 카메라를 못 쓰면 안내 — 카메라 아이콘(34, textTertiary) + 글자(14 textSecondary): 권한 거부면 "설정에서 카메라 권한을 켜 주세요", 아니면 "앨범에서 사진을 골라 주세요".
- 미리보기와 컨트롤 사이는 남는 공간(Spacer).
- **촬영 컨트롤**(사진이 없을 때, 좌우 20 추가 패딩): 왼쪽 앨범 버튼(52×52, 모서리 14, 테두리 `border` 1dp, `HIcon.IMAGE` 22, 라벨 "앨범에서 고르기"), 가운데 셔터(84×84: 바깥 링 `accent` 4dp, 안쪽 채운 원 64 `accent`, 라벨 "사진 찍기"; 카메라를 못 쓰면 비활성 + 투명도 0.4), 오른쪽 전환 버튼(52 원, 테두리 `border`, `HIcon.FLIP` 22, 라벨 "카메라 전환"; 못 쓰면 비활성 0.4). 세 컨트롤 사이는 균등(Spacer).
- **사진이 있을 때**: 컨트롤 대신 가로로 두 버튼(간격 8): "다시 찍기"(SECONDARY) → 사진 비우고 카메라로 복귀 / "올리기"(PRIMARY) → JPEG(품질 0.85)로 인코딩해 `SubmissionContent.Image(jpeg, "jpg")`.
- 아래 여백 40(+ 내비게이션 바 inset 반영), 배경 `bg`.
- 3:4 중앙 크롭: 이미지 방향(EXIF 회전)을 먼저 바로잡은 뒤, 가로가 더 길면 가로를 `높이×3/4` 로, 아니면 세로를 `가로×4/3` 로 줄여 중앙을 자른다. 촬영·앨범 모두 같은 경로.
- 카메라 권한: 화면에 처음 들어올 때 한 번 요청(이미 허용이면 바로 시작). 거부되면 "설정에서 카메라 권한을 켜 주세요" + 앨범은 계속 사용 가능. 카메라가 없는 기기/연결 실패면 앨범만.
- 화면을 떠나면 카메라 정지(iOS `.onDisappear`).
- 전면 카메라로 찍은 사진은 **좌우 반전하지 않는다**(iOS `AVCapturePhoto` → `UIImage` 에 반전 처리가 없다. 미리보기만 거울처럼 보일 수 있음).

## Root Cause / Key Implementation Points

1. **수행 화면은 별도 라우트**: `MainRoute.MissionPerform(groupId: String, type: String? = null)`(`type` 은 아래 디버그용; 일반 흐름에서는 null). `composable<MainRoute.MissionPerform>(enterTransition = { slideInVertically(tween(300)) { it } }, popExitTransition = { slideOutVertically(tween(300)) { it } }, exitTransition = { null 또는 기본 }, popEnterTransition = { 기본 })` 로 아래에서 올라오는 전환(iOS `fullScreenCover`). 백스택: `Home → Mission → MissionPerform`.
2. **제출 후 복귀 + 토스트 전달**: 수행 화면의 제출 콜백이 `store.submit(content, id)` → 이전 백스택 항목의 `savedStateHandle` 에 `"submitted" = true` 를 쓰고 → `nav.popBackStack()`. `composable<MainRoute.Mission>` 에서 `entry.savedStateHandle.getStateFlow("submitted", false)` 를 `collectAsState()` 로 읽어 `MissionScreen(..., submittedSignal: Boolean, onSubmittedHandled: () -> Unit)` 에 넘기고, `MissionScreen` 은 `LaunchedEffect(submittedSignal) { if (it) { toast.show("오늘의 미션을 올렸어요"); onSubmittedHandled() } }` 후 핸들 쪽에서 `savedStateHandle["submitted"] = false` 로 비운다. (05 는 수행 화면이 위에 있는 동안 컴포지션에서 빠지므로 `didSubmit` 전이를 감지해 토스트를 띄울 수 없어서 이 방식을 쓴다.)
3. **유형 분기**: 05 의 CTA(`onPerform`)는 `type in {WRITE, PHOTO}` 이면 `MissionPerform` 으로 이동, 아니면(DRAW/VOICE) **기존 동작 유지**(디버그 임시 제출 / 릴리스 "준비 중이에요"). 이 지원 여부는 순수 함수 `isPerformScreenAvailable(type)`(지금은 WRITE, PHOTO 만 true)로 두어 06/09 구현 때 한 줄로 바꾸게 한다.
4. **진입 방어(한 번만)**: `MissionPerform` 목적지 진입 시 `LaunchedEffect(entry)` 로 확인 — id 가 잘못되었거나 미션이 없거나, **이미 내가 올렸거나**(`didSubmit`), 지원하지 않는 유형이면 `popBackStack<Home>(false)` 가 아니라 **`popBackStack()`**(바로 아래 05 로) 하거나 Home 으로 정리(없는 id 의 경우). 판단은 순수 함수 `performEntryDecision(mission: TodayMission?, requestedType: MissionType?)` → `Allow(type)` / `Missing` / `AlreadySubmitted` / `Unsupported` 로 분리해 테스트한다. 화면 컴포저블은 제출 직후(`didSubmit` 이 true 가 되어 재구성돼도) **내비게이션하지 않는다**.
5. **공통 헤더의 닫기/뒤로**: `MissionHeader(hasDraft, trailing)` 가 `HNavBar(leading = CLOSE)` + `BackHandler(enabled = hasDraft)` 를 함께 처리한다: 내용이 있으면 닫기와 시스템 뒤로 가기 모두 `HAlert("그만둘까요?", "지금까지 한 내용은 저장되지 않아요.", confirmTitle = "그만두기", cancelTitle = "계속하기")` 를 띄우고, 확정 시 닫기. 내용이 없으면 즉시 닫기(`BackHandler` 비활성 → 기본 뒤로 가기). 닫기 = `onClose` 콜백(= `nav.popBackStack()`).
6. **제출 중복 방지**: "올리기" 를 연타해도 `store.submit` / 복귀가 한 번만 일어난다(화면 안 `submitted` 플래그; 이미 올렸으면 `DataStore.submit` 이 무시하지만 내비게이션이 두 번 호출되면 05 가 팝되어 Home 으로 갈 수 있다).
7. **CameraX 도입(이 단계에서 허용되는 유일한 새 의존성)**: `androidx.camera:camera-core`, `camera-camera2`, `camera-lifecycle`, `camera-view` 를 `libs.versions.toml` 에 하나의 `camerax` 버전으로 등록하고 `app/build.gradle.kts` 에 추가. 버전은 compileSdk 36 / AGP 8.13.2 / Kotlin 2.2.21 과 호환되는 **최신 안정판**(작업 시점 확인)이며 선택한 버전을 보고서에 적는다. 그 외 라이브러리(Accompanist 등) 추가 금지.
   - 매니페스트: `<uses-permission android:name="android.permission.CAMERA" />`, `<uses-feature android:name="android.hardware.camera" android:required="false" />`, `<uses-feature android:name="android.hardware.camera.front" android:required="false" />`(카메라 없는 기기에서도 설치되어 앨범만 쓸 수 있게).
   - 미리보기는 `PreviewView`(`AndroidView`)를 `implementationMode = COMPATIBLE`(TextureView)로, `scaleType = FILL_CENTER` — 기본 `PERFORMANCE`(SurfaceView)는 둥근 모서리 클립이 깨진다.
   - 바인딩: `ProcessCameraProvider.getInstance` → `bindToLifecycle(LocalLifecycleOwner, selector, Preview, ImageCapture)`. `ImageCapture` 는 `CAPTURE_MODE_MINIMIZE_LATENCY`(또는 기본), 비율은 3:4(`AspectRatio.RATIO_4_3`/`ResolutionSelector` 중 현재 버전 API). 사진이 있을 때(`photo != null`)와 화면을 떠날 때(`DisposableEffect`)는 `unbindAll()`; "다시 찍기" 로 사진을 비우면 다시 바인딩. 권한이 없으면 바인딩하지 않는다.
   - 촬영: 캐시 폴더 임시 파일(`File(cacheDir, "capture-<uuid>.jpg")`)로 `takePicture(OutputFileOptions, mainExecutor, callback)` → 완료되면 아래 "사진 파이프라인"으로 디코딩·크롭 → 임시 파일 삭제. 촬영 중에는 셔터 연타 방지(`capturing` 플래그).
   - 플래시: `imageCapture.flashMode = FLASH_MODE_ON / FLASH_MODE_OFF` (토글 시 갱신). 플래시 유닛이 없는 카메라(전면 등)에서는 효과가 없을 뿐 오류가 되면 안 된다(예외는 잡아서 무시).
   - 전·후면 전환: `CameraSelector.DEFAULT_BACK_CAMERA` ↔ `DEFAULT_FRONT_CAMERA`, 반대편 카메라가 없으면(`hasCamera`) 무시.
   - 카메라 사용 가능 여부 = 권한 허용 && `hasCamera(back or front)` && 바인딩 성공. 하나라도 실패하면 안내 문구 상태(권한 거부 / 앨범만).
8. **사진 파이프라인**(촬영·앨범 공통, 순수 로직은 분리): `Uri/File → (EXIF 방향 읽기, `android.media.ExifInterface(InputStream)`) → 다운샘플 디코딩(긴 변이 약 2048px 이하가 되도록 `BitmapFactory.Options.inSampleSize`; 메모리 보호 — iOS 에는 없지만 Android 에서는 필요) → 회전 보정 → 3:4 중앙 크롭 → `Bitmap`(화면 상태) → 올릴 때 `compress(JPEG, 85)` → `ByteArray`. 크롭 사각형 계산(`centerCropRect(width, height)`)과 다운샘플 크기 계산(`sampleSizeFor(width, height, maxSide)`)은 Android API 에 의존하지 않는 순수 함수로 분리해 단위 테스트한다. 디코딩 실패(손상 파일, 읽기 실패)는 조용히 무시하고 사진 상태를 바꾸지 않는다(가능하면 토스트 없음 — iOS 도 무시).
   - 앨범: `rememberLauncherForActivityResult(PickVisualMedia())` + `ImageOnly` (권한 불필요). 선택 후 위 파이프라인. 취소 시 변화 없음.
9. **08 입력 처리**: `BasicTextField`(여러 줄, `singleLine = false`, 커서 ink)로 구현. 상태는 순수 클래스 `WriteMissionState(text)`: `withText(input)`(300 글자 제한), `count`, `trimmed`, `canSubmit`, `counterText`("12 / 300"). 글자 단위 제한을 입력 중에 적용할 때 한글 조합(IME)이 깨지지 않게 **String 을 되쓰지 말고 `TextFieldValue` 오버로드**를 쓰고, 제한이 걸릴 때만 `TextFieldValue(text = 잘린 글, selection = 끝)` 로 바꾼다(3~4단계 03 초대 코드 입력과 같은 방식).
10. **디버그 전용 유형 전환(테스트 보조)**: 참여/생성한 그룹의 오늘 미션 유형은 랜덤·고정이라 사용자가 08/07 을 고를 수 없다. **디버그 빌드에서만**(`FLAG_DEBUGGABLE`) 05 의 "오늘은 ○○" 알약을 길게 누르면 화면 안 상태 `debugType` 이 `MissionType.entries` 를 순환(없음 → 그림 → 사진 → 글 → 음성 → 없음 …)하고, CTA 문구와 `onPerform` 의 분기가 `debugType ?: mission.type` 을 따르며, 수행 화면 라우트의 `type` 인자로 전달된다(수행 화면 내용 분기는 `type ?: mission.type`). `DataStore.submit` 은 항상 실제 `mission.type` 으로 저장하므로 화면 유형과 저장 유형이 달라질 수 있으나 개발용이다. 주석 `// 개발용 유형 전환, 6단계(06·09) 완료 시 삭제`. 릴리스에서는 코드 경로가 실행되지 않는다.

## Files / Functions to Change

새 파일 (`android/app/src/main/java/com/sapphire/hawe/` 기준):
- `feature/mission/MissionPerformScreen.kt` — `MissionPerformScreen(id: UUID, type: MissionType, topic: String, store/submit 콜백, onClose)` : 유형 분기(`WRITE` → `WriteMissionScreen`, `PHOTO` → `PhotoMissionScreen`, 나머지는 도달 불가 — 방어적으로 빈 화면), 공통 `MissionTitle`, `MissionHeader`
- `feature/mission/MissionPerformCommon.kt` — `MissionTitle`, `MissionHeader`(+닫기 확인, `BackHandler`), `isPerformScreenAvailable(type)`, `performEntryDecision(...)`
- `feature/mission/WriteMissionScreen.kt` — 08 + `WriteMissionState`
- `feature/mission/PhotoMissionScreen.kt` — 07 (화면, 컨트롤, 권한 처리)
- `feature/mission/PhotoCamera.kt` — CameraX 바인딩/촬영/플래시/전환을 담은 작은 상태 홀더(`rememberCameraController(...)` 등)와 `CameraPreviewView`(`AndroidView`)
- `feature/mission/PhotoProcessing.kt` — 파일/Uri → 방향 보정 → 다운샘플 → 3:4 크롭 → JPEG 바이트. 순수 계산 함수(`centerCropRect`, `sampleSizeFor`)와 Android 의존 함수(`loadCroppedPhoto(context, uri)`)를 분리.

수정할 파일:
- `app/MainNavigation.kt` — `MainRoute.MissionPerform(groupId, type)` 추가 + 전환 애니메이션 + 진입 방어 + 제출 시 `store.submit` / `savedStateHandle` / `popBackStack()`. `composable<Mission>` 에서 `submitted` 신호를 `MissionScreen` 에 전달. `MissionScreen` 호출에 `onPerform: (MissionType?) -> Unit` 추가.
- `feature/mission/MissionScreen.kt` — CTA 분기(`isPerformScreenAvailable`), `submittedSignal`/`onSubmittedHandled` 처리, 디버그 `debugType` 순환(알약 길게 누르기), 기존 임시 제출은 DRAW/VOICE 에서만.
- `app/src/main/AndroidManifest.xml` — CAMERA 권한, `uses-feature` 2개.
- `gradle/libs.versions.toml`, `app/build.gradle.kts` — CameraX.
- `designsystem/layout/HCtaScaffold.kt` — 깨진 `@Preview` 문자열 두 개만 복구(그 외 변경 금지).
- 변경 금지(원칙): `core/data/DataStore.kt`, `core/model/*`, `core/AppSession.kt`, `app/AppViewModel.kt`, `app/AuthNavigation.kt`, `feature/auth/*`, `feature/groups/*`, `designsystem/components/*`(필요하면 최소 변경 후 보고서에 이유 기재).

테스트 (`android/app/src/test/java/com/sapphire/hawe/feature/mission/`):
- `WriteMissionStateTest.kt`, `PhotoProcessingTest.kt`(순수 계산), `MissionPerformCommonTest.kt`(`isPerformScreenAvailable`, `performEntryDecision`)

## Implementation Steps

0. **환경**(이전 단계와 동일, 준비됨): `cd C:\hawe\android`, `$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot"`, SDK `C:\Android\Sdk`(`local.properties` 있음). `.\gradlew.bat assembleDebug testDebugUnitTest lintDebug installDebug`. 폰은 USB 연결(`C:\Android\Sdk\platform-tools\adb.exe devices` 에 `device`); 안 보이면 설치만 건너뛰고 보고서에 적는다.
1. `HCtaScaffold.kt` 프리뷰 문자열 복구(파일 인코딩이 UTF-8 인지 확인하고 **UTF-8 로 저장**; PowerShell 로 한글 포함 파일을 다시 쓰지 말 것 — 이 문자열이 깨진 원인으로 보임).
2. CameraX 의존성 + 매니페스트 → 빌드가 통과하는지 확인.
3. `MissionPerformCommon.kt`(순수 함수 + `MissionTitle` + `MissionHeader`) + 테스트.
4. `WriteMissionState` + 테스트 → `WriteMissionScreen`.
5. `PhotoProcessing.kt`(순수 함수 + 테스트 → Android 디코딩) → `PhotoCamera.kt` → `PhotoMissionScreen`.
6. `MissionPerformScreen.kt` + `MainNavigation.kt` 연결(전환 애니메이션, 진입 방어, 제출 신호) + `MissionScreen.kt` 수정(CTA 분기, 토스트 신호, 디버그 유형 전환).
7. 빌드·테스트·lint·설치 실행, 결과를 `.agent/report.md` 에 기록.

## Constraints

- 변경 범위는 `android/` 안. `ios/`, `src/`(백엔드), `cordova/`, `frontend/`, `docs/`, 루트 `.gitignore`, `AGENTS.md`/`CLAUDE.md` 수정 금지. git 상태를 바꾸는 명령 금지(commit/checkout/stash/reset/clean 등).
- iOS 파일은 읽기 전용. 문구·수치는 iOS 와 동일(위에 모두 옮겼으나 의심되면 iOS 파일이 기준).
- 새 의존성은 **CameraX 4개만** 허용. Material 기본 시각 컴포넌트(`Button`, `AlertDialog`, `TextField` 등)는 쓰지 않는다 — `HButton`, `HAlert`, `BasicTextField` + 직접 그리기.
- `DataStore`/모델 시그니처·불변 규칙 변경 금지. 서버 호출·영속화 없음.
- 06 그림·09 음성 구현 금지(녹음 권한·`RECORD_AUDIO` 도 추가하지 않는다). 05 에서 그 유형은 기존 동작 유지.
- **화면이 사라지는 중에도 내비게이션 호출 금지**(퇴장 애니메이션 중 `popBackStack` 이 엉뚱한 목적지를 팝하는 4단계 버그 재발 방지). 진입 방어는 `LaunchedEffect(entry)` 한 번만.
- 한글이 들어간 파일은 **UTF-8(BOM 없음)** 로 저장. PowerShell 의 `Set-Content`/`Out-File` 로 한글 소스를 쓰지 않는다.
- 코드 주석은 iOS 처럼 한국어로 짧게, 화면 문구는 Kotlin 안 한국어 리터럴.

## Edge Cases

- **08**: 공백/줄바꿈만 입력 → "올리기" 비활성, `hasDraft` false(닫기 확인 없음). 앞뒤 공백·줄바꿈은 제출 시 제거, 중간 줄바꿈은 보존. 300자 정확히/301자 붙여넣기(300자로 잘림, 커서는 끝). 이모지(😀)·한글 조합 중 입력이 깨지거나 중복되지 않음. 글자 수 표시는 잘린 뒤 값. 키보드가 올라와도 "올리기" 버튼이 키보드 위에 보이고 에디터 안 커서가 보인다(에디터 높이 360 은 고정, 화면이 작으면 스크롤).
- **07 권한**: 최초 진입에서 시스템 권한 대화상자 → 허용: 미리보기 시작 / 거부: "설정에서 카메라 권한을 켜 주세요" + 앨범 사용 가능, 셔터·전환 비활성(투명도 0.4), 플래시 토글 숨김. "다시 묻지 않음" 후 재진입: 요청 대화상자가 안 뜨므로 거부 안내가 바로 보인다. 설정에서 권한을 켠 뒤 돌아오면(앱 재개) 다시 확인해 미리보기가 시작되면 좋다(필수 아님 — 안 하면 보고서에 한 줄).
- **07 카메라 없음/바인딩 실패**(에뮬레이터 등): "앨범에서 사진을 골라 주세요" 안내, 앨범 선택은 동작.
- **07 촬영**: 셔터 연타 → 한 번만 촬영. 촬영 실패(콜백 오류) → 조용히 무시하고 계속 촬영 가능. 후면/전면 전환 직후 촬영. 플래시 ON + 전면 카메라(플래시 없음) 촬영이 실패하지 않는다. 가로로 긴/세로로 긴 사진 모두 중앙 3:4 크롭, EXIF 회전 90°/180°/270° 사진(앨범)이 바로 보인다. 아주 큰 사진(예: 12000×9000)에서 메모리 오류가 나지 않는다.
- **07 상태 전환**: 사진 촬영 후 "다시 찍기" → 카메라 재시작, 플래시 상태 유지. 사진이 있는 상태에서 닫기/뒤로 → "그만둘까요?" 확인. 앱이 백그라운드로 갔다 오면 카메라가 정상 복구(라이프사이클 바인딩).
- **내비게이션**: "올리기" 연타 → 05 가 한 번만 복귀하고 토스트 한 번. 제출 직후 퇴장 애니메이션 중 수행 화면이 다시 그려져도(`didSubmit` true) 추가 내비게이션 없음. 05 로 돌아온 뒤 CTA 는 "친구들 작품 보기"(이 화면에 다시 들어올 수 없음). 수행 화면에서 그룹이 사라지는 경우는 없지만(16 접근 불가) 진입 방어는 한다.
- **디버그 유형 전환**: 알약 길게 누르기로 "그림"/"음성" 을 고른 상태의 CTA 는 기존 임시 제출/토스트 동작, "글"/"사진" 을 고르면 수행 화면이 열린다. 릴리스 빌드에서는 알약이 눌리지 않는다.
- **시스템 바**: 3버튼 내비게이션에서 "올리기"/촬영 컨트롤이 가려지지 않는다(아래 여백 `ctaBottomPadding`). 상태 바 영역은 `RootScreen` 이 이미 처리.
- **회전**: 세로 고정. 구성 변경(글자 크기 등)으로 Activity 가 재생성되면 작성 중 내용이 사라질 수 있다(`rememberSaveable` 로 글 본문은 보존하는 것을 권장; 사진 Bitmap 은 보존하지 않아도 됨 — 보고서에 한계 기재).

## Regression Risks

- `MissionScreen` 의 CTA 흐름 변경: 5단계에서 확인된 카운트다운·콕 찌르기·잠금 안내·16 이동·16 나가기 후 홈 복귀가 그대로여야 한다. 특히 05 → 수행 → 05 복귀 시 카운트다운이 정상 재개되고, `LaunchedEffect(entry)` 방어가 복귀 때 다시 실행되어도(미션이 있으므로) 아무 일도 하지 않아야 한다.
- 매니페스트 CAMERA 권한 추가로 스토어 심사/권한 UI 영향 — 이번엔 문제 없음. `uses-feature required=false` 누락 시 카메라 없는 기기에서 설치가 막힌다.
- CameraX 의존성 추가로 APK 크기·빌드 시간 증가, lint 경고 증가 가능 — 오류 0 유지.
- `MainNavigation.kt` 전환 애니메이션 파라미터 추가가 다른 목적지의 기본 전환을 바꾸지 않아야 한다(수행 화면에만 지정).
- 깨진 프리뷰 문자열 복구 시 다른 부분이 인코딩 때문에 또 깨지지 않게 UTF-8 유지.

## Tests Required

JVM 단위 테스트(JUnit4), 기존 34개 통과 유지:
- `WriteMissionStateTest`: 300자 정확히/301자 → 300자로 잘림, 이모지(😀 한 글자 = 1)·한글·영문 섞인 입력의 글자 수, 공백/줄바꿈만이면 `canSubmit` false·`trimmed` 빈 문자열, 앞뒤 공백 제거 후 제출 텍스트, `counterText` = `"0 / 300"`, `"12 / 300"`, 붙여넣기(긴 문자열)가 300 글자로 잘리는지.
- `PhotoProcessingTest`: `centerCropRect` — 가로 4000×3000 → 2250×3000(x 오프셋 875), 세로 3000×4000 → 3000×4000(전체), 정사각 1000×1000 → 750×1000 중앙, 매우 작은 1×1/0 크기 방어, 이미 3:4 인 경우 전체; `sampleSizeFor(width, height, maxSide=2048)` — 작은 이미지 1, 4096→2, 12000×9000→8 등 2의 거듭제곱, 0/음수 방어.
- `MissionPerformCommonTest`: `isPerformScreenAvailable`(WRITE, PHOTO 만 true), `performEntryDecision` — 미션 null → Missing, 이미 올림 → AlreadySubmitted, 요청 유형 없음이면 미션 유형으로 Allow, 요청 유형이 DRAW/VOICE 면 Unsupported, WRITE/PHOTO 면 Allow.

CameraX·Photo Picker·BackHandler·화면 레이아웃의 UI 테스트는 만들지 않는다 — 폰 확인으로 대체.

## Validation Steps

Codex 가 직접 실행: `.\gradlew.bat assembleDebug testDebugUnitTest lintDebug installDebug` (JDK 17). 기준: 빌드 성공, 테스트 전부 통과(기존 34 + 신규), lint 새 오류 0(경고 건수 보고), 폰 설치 후 `adb shell am start -n com.sapphire.hawe/.MainActivity`. 사용한 CameraX 버전·카메라 API(해상도 선택 방식 등)를 보고서에 적는다.

사용자가 폰에서 확인할 시나리오 — 보고서에 그대로 적을 것:
1. 가입 → 03 에서 `K7Q2PX` 참여(또는 02 로 그룹 생성) → 홈 카드 → 05. 오늘 유형이 글/사진이 아니면 **디버그 빌드에서 "오늘은 ○○" 알약을 길게 눌러** 글 → 사진으로 바꿔 가며 확인(그림/음성으로 바꾸면 기존 임시 제출 동작).
2. **08 글**: CTA "글 쓰기" → 아래에서 올라오는 화면, 헤더 X + "0 / 300", 제목 "오늘의 글" + 주제, 에디터(안내 문구 "여기에 적어보세요"), 입력할 때 글자 수 증가, 300자 제한(붙여넣기 포함), 공백만 쓰면 "올리기" 비활성, 글을 쓰고 X/뒤로 가기 → "그만둘까요?"(계속하기/그만두기), 올리기 → 05 로 복귀 + 토스트 "오늘의 미션을 올렸어요" + `완료 +1`·내 줄 "완료"·잠금 안내 사라짐·CTA "친구들 작품 보기".
3. **07 사진**: CTA "사진 찍기" → 카메라 권한 대화상자 → 허용 시 미리보기(3:4, 둥근 모서리), 셔터로 촬영 → 사진 확정 상태("다시 찍기"/"올리기"), 다시 찍기, 후면/전면 전환, 플래시 토글(아이콘 색), 앨범 버튼으로 사진 선택(세로/가로 사진 모두 3:4 로 보임), 올리기 → 05 복귀 + 토스트.
4. 권한 거부 시 안내 문구 + 앨범 사용 가능, 앱 설정에서 권한을 켠 뒤 다시 들어오면 카메라 동작.
5. 사진/글 작성 중 앱을 홈으로 보냈다 돌아와도 카메라가 다시 동작.
6. 3버튼/제스처 내비게이션에서 하단 컨트롤이 가려지지 않는지, 키보드가 열린 08 에서 "올리기" 위치.
7. 05 ↔ 수행 ↔ 05 후 카운트다운이 정상, 16 그룹 정보·나가기 → 홈 복귀가 여전히 정상.
8. 릴리스 동작 확인은 불가하므로 디버그 전용 코드(유형 전환, DRAW/VOICE 임시 제출)가 `FLAG_DEBUGGABLE` 로 가드되어 있는지 코드로 확인했다고 보고서에 적기.

## Potential Merge Conflicts

- `android/` 만 수정. 백엔드·iOS·Cordova 와 겹치지 않는다.
- 공유 파일 수정: `app/MainNavigation.kt`, `feature/mission/MissionScreen.kt`, `AndroidManifest.xml`, `libs.versions.toml`, `app/build.gradle.kts`, `designsystem/layout/HCtaScaffold.kt`(프리뷰 문자열만). 다음 단계(06 그림 / 09 음성)가 `MissionPerformScreen`·`MainNavigation.kt`·`MissionScreen.kt`·매니페스트(`RECORD_AUDIO`)를 다시 수정하므로 이 단계를 먼저 병합한다.
- 작업 트리에 이전 단계들의 미커밋 변경이 남아 있을 수 있다. `git status` 로 확인만 하고 커밋하지 않는다.
- `.agent/report.md` 는 Codex 소유. 기준 iOS 커밋(`git log -1 --format=%h -- ios/`), 빌드·테스트·lint·설치 결과, CameraX 버전, iOS 와 다르게 한 부분(다운샘플, 파이프라인, 디버그 유형 전환, 전면 카메라 반전 없음), 폰 확인 체크리스트, 남은 위험을 적는다.
