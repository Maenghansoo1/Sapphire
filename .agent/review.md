# Review — Android 6단계 (1/3): 미션 수행 공통 틀 + 08 글 + 07 사진

기준: `.agent/plan.md`, `.agent/report.md`, 작업 트리의 `android/` 미커밋 변경. 범위 밖 파일 변경 없음, 새 의존성은 CameraX 4개(1.6.2)뿐, `DataStore`/모델 변경 없음, 매니페스트는 CAMERA 권한 + `uses-feature required=false` 2개 확인.
Codex 가 `assembleDebug` / `testDebugUnitTest`(40개 통과) / `lintDebug`(오류 0) / `installDebug` 를 직접 실행한 결과는 보고서대로라고 가정했다. 아래는 코드를 읽어 확인한 내용이며 폰에서 직접 눌러 보지는 않았다.

## 확인한 항목 (문제 없음)
- **내비게이션**: `MissionPerform(groupId, type?)` 라우트, 수직 슬라이드 전환(진입/팝 종료에만 지정), 진입 판단(`performEntryDecision`)은 `remember(entry)` 로 한 번만 결정되고 `LaunchedEffect(entry)` 에서만 내비게이션 → 제출 직후 `didSubmit` 이 true 가 되어 재구성돼도 추가 내비게이션이 없다(4단계 버그 재발 방지 규칙 준수). 제출은 `store.submit` → `previousBackStackEntry.savedStateHandle["submitted"]=true` → `popBackStack()`, 05 가 `getStateFlow` 로 받아 토스트 후 신호를 비운다.
- **공통 헤더**: 내용이 있을 때만 `BackHandler` 활성, 확인창이 떠 있는 동안은 비활성(`hasDraft && !confirming`), 문구·버튼 구성이 iOS 와 일치.
- **08**: 글자(grapheme) 단위 제한·카운트, `TextFieldValue` 오버로드로 제한이 걸릴 때만 값을 되써서 IME 조합을 보호, 본문은 `rememberSaveable`, 연타 방지 플래그.
- **07**: `PreviewView` 를 `COMPATIBLE`(TextureView)로 써서 둥근 모서리 클립 유지, 권한 요청은 진입 시 1회 + `ON_RESUME` 재확인(설정에서 켜고 돌아오면 반영), 사진이 생기면 카메라 언바인드·"다시 찍기" 로 재바인드, 촬영은 임시 파일 → 디코딩 → 삭제, 전면 카메라 플래시는 건너뜀, 반대 카메라 없으면 전환 비활성. EXIF 변환 행렬(TRANSPOSE/TRANSVERSE 포함)을 표준 변환과 대조했고 동등하다. `centerCropRect`/`sampleSizeFor` 수식 확인(4:3·3:4·정사각·세로로 긴 이미지).
- **05 연동**: `isPerformScreenAvailable` 로 글/사진만 수행 화면으로, 그림/음성은 기존 임시 동작 유지. 디버그 유형 전환(`FLAG_DEBUGGABLE` 가드, 알약 길게 누르기)과 `HCtaScaffold` 프리뷰 문자열 복구(UTF-8) 확인.

---

## P1

### File / Location
`android/app/src/main/java/com/sapphire/hawe/feature/mission/WriteMissionScreen.kt` — `BasicTextField(... modifier = Modifier.fillMaxWidth().height(360.dp).border(...).padding(20.dp), ...)`

### Problem
테두리·높이·안쪽 여백을 `BasicTextField` 자신의 `modifier` 에 걸었다. `BasicTextField` 의 포커스/탭 처리는 그 modifier 체인의 **안쪽(패딩 안, 글 내용 높이)** 에만 붙으므로 터치가 먹는 영역은 "글이 있는 첫 줄 높이"의 내용 영역뿐이다(여러 줄 기본 `minLines = 1`). 360dp 높이의 에디터 박스에서 안내 문구 아래의 빈 공간이나 20dp 안쪽 여백을 눌러도 키보드가 올라오지 않는다. iOS 는 에디터 영역 전체에 `onTapGesture { isFocused = true }` 가 걸려 있다(`WriteMissionView.editor`: `.contentShape(Rectangle()).onTapGesture`).

### Why It Matters
08 에 들어오면 사용자가 가장 먼저 하는 동작은 큰 박스를 누르는 것인데, 박스 중앙을 눌러서는 입력이 시작되지 않는다. 첫 줄의 아주 좁은 영역만 눌러야 하므로 "글이 안 써진다" 로 보일 수 있다. 메인 흐름이라 P1 로 본다. (코드 구조에서 확인한 내용이며 폰에서 재현하지는 않았다.)

### Recommended Fix
바깥 `Box` 가 테두리·높이 360·안쪽 여백 20 을 갖고, 그 `Box` 에 `Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { focusRequester.requestFocus(); keyboard?.show() }` 를 건다. 안쪽 `BasicTextField` 는 `Modifier.fillMaxSize().focusRequester(focusRequester)` 로 박스를 채운다(내부 스크롤은 필드가 처리). 안내 문구는 `decorationBox` 안에 그대로 둔다.

---

## P2

### File / Location
`feature/mission/PhotoMissionScreen.kt` — `photo?.recycle()` 호출들(`picker` 콜백, 촬영 콜백, "다시 찍기", `DisposableEffect` 의 `onDispose { latestPhoto?.recycle() }`)

### Problem
화면에 그려지고 있는 `Bitmap`(`asImageBitmap()` 로 Compose `Image` 에 전달)을 상태를 바꾸기 **직전에** `recycle()` 한다. 이미 RenderThread 에 올라간 디스플레이 리스트가 그 비트맵을 참조하고 있는 순간에 호출되면 "Canvas: trying to use a recycled bitmap" 예외가 날 수 있다. API 26+ 에서는 비트맵 메모리가 GC 로 회수되므로 `recycle()` 이 필요하지 않다.

### Why It Matters
드문 타이밍 이슈지만 발생하면 앱이 종료된다. 이득(메모리 즉시 회수)에 비해 위험이 크다.

### Recommended Fix
`recycle()` 호출을 모두 제거하고 참조만 교체한다(`photo = next`, `photo = null`). `loadCroppedPhoto` 내부의 중간 비트맵(`original`, `upright`)은 화면에 올라가지 않으므로 `recycle()` 유지해도 된다.

---

### File / Location
`feature/mission/PhotoMissionScreen.kt` — `picker` 콜백, 촬영 콜백의 `loadCroppedPhoto(...)`, "올리기" 의 `photoJpegBytes(...)`

### Problem
이미지 디코딩·회전·크롭(`loadCroppedPhoto`)과 JPEG 압축(`photoJpegBytes`)이 메인 스레드에서 동기 실행된다. 12MP 사진은 2048px 로 다운샘플해도 수백 ms 가 걸릴 수 있다.

### Why It Matters
앨범 선택 직후/촬영 직후/올리기 직후 화면이 잠깐 멈춘다. 기능 오류는 아니고 체감 문제다.

### Recommended Fix
(선택) `rememberCoroutineScope` + `withContext(Dispatchers.Default)` 로 디코딩·압축을 옮기고, 처리 중에는 셔터/올리기를 막는다(`capturing`/`submitted` 플래그 재사용). 이번 단계에서 하지 않으면 보고서의 "남은 위험"에 한 줄 적는다.

---

## 결론

**수정 후 재리뷰 필요** — P1 1건(08 에디터 터치 영역)을 고친 뒤 다시 리뷰한다. P2 두 건 중 `recycle()` 제거는 같이 고치는 것을 권장하고, 메인 스레드 처리는 선택이다.
수정 후 `.agent/report.md` 에 처리 결과를 추가하고 `assembleDebug` / `testDebugUnitTest` / `lintDebug` / `installDebug` 결과를 갱신해 달라.
