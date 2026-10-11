# Plan — Android 6단계 (2/3): 06 그림 + 09 음성

## Goal

iOS 의 **06 그림**(`DrawMissionView`)과 **09 음성**(`VoiceMissionView`)을 Android(Compose)로 옮겨 6단계(미션 수행)를 마무리한다. 이전 묶음(6단계 1/3)에서 만든 공통 틀(`MissionPerformScreen` / `MissionTitle` / `MissionHeader` / 수행 라우트 / 제출 후 05 복귀 + 토스트)을 그대로 쓴다.

- 05 의 CTA 가 네 가지 유형(그림·사진·글·음성) 모두에서 수행 화면을 연다. **05 의 "디버그 전용 임시 제출"(DRAW/VOICE 분기)을 삭제**한다.
- **06 그림**: 손가락으로 그리는 그림판 — 펜/지우개, 굵기 3단계, 색 7개, 되돌리기/다시 실행, 올리기 → 흰 바탕 PNG.
- **09 음성**: 마이크 권한 → 녹음(최대 30초, m4a/AAC) → 정지 → 다시 / 미리 듣기, 실시간 파형, 올리기.
- 작성 중 내용이 있으면 닫기·뒤로 가기에서 "그만둘까요?" 확인(공통 헤더).

전체 로드맵은 `docs/FRONTEND/ANDROID.md` (§5 대응표 — PencilKit→Compose Canvas, AVAudioRecorder→MediaRecorder, AVAudioPlayer→MediaPlayer, 마이크 권한/설정 열기; §7 위험 요소).

이번 범위 아님: 10~15, 17 계열, 알림, 서버 연결. 그림/음성의 서버 업로드는 없다(제출은 `DataStore` 임시 저장소에 파일로만 저장).

## Findings

### Android 현재 상태 (6단계 1/3 완료)
- `app/MainNavigation.kt`: `MainRoute.MissionPerform(groupId, type: String? = null)` 라우트, 수직 슬라이드 전환, 진입 판단은 `remember(entry)` + `LaunchedEffect(entry)` 로 **한 번만**(`performEntryDecision`), 제출은 `store.submit(content, id)` → `previousBackStackEntry.savedStateHandle["submitted"] = true` → `popBackStack()`. `MissionPerformScreen(decision.type, mission.topic, onClose, onSubmit)`.
- `feature/mission/MissionPerformScreen.kt`: `when (type)` 이 `WRITE`/`PHOTO` 만 분기, 나머지는 `else -> Unit`. 이 `when` 에 `DRAW`/`VOICE` 를 추가한다.
- `feature/mission/MissionPerformCommon.kt`: `isPerformScreenAvailable(type)` = WRITE||PHOTO 만 true, `performEntryDecision(mission, requestedType)`(Allow/Missing/AlreadySubmitted/Unsupported), `MissionTitle(type, topic)`, `MissionHeader(hasDraft, onClose, trailing)`(닫기 X + 확인창 + `BackHandler`).
- `feature/mission/MissionScreen.kt`: CTA `performAction` = `isPerformScreenAvailable(displayType)` 이면 `onPerform(...)`, 아니면 디버그에서 `store.submit(SubmissionContent.Text("개발용 임시 제출"), id)` + 토스트 / 릴리스에서 "준비 중이에요". 디버그 빌드 전용 `debugType`(알약 길게 누르기로 유형 순환)이 있어 테스트에서 네 유형을 모두 열 수 있다.
- `feature/mission/WriteMissionScreen.kt` / `PhotoMissionScreen.kt`: 구현 패턴 참고 — 제출 연타 방지 플래그(`submitted`), 처리 중 플래그, 무거운 작업은 `Dispatchers.Default`, 하단 여백 `ctaBottomPadding(...)`, 헤더 `MissionHeader(hasDraft, onClose, trailing = {...})`, 권한 `rememberLauncherForActivityResult` + `ON_RESUME` 재확인 패턴(PhotoMissionScreen).
- `core/data/DataStore.kt`: `SubmissionContent.Image(bytes, fileExtension)`(PNG 는 `"png"`), `SubmissionContent.Voice(file: File, duration: Double(초), waveform: List<Float>)`. `submit` 의 Voice 처리는 `content.file.readBytes()` 를 `mediaDir/<UUID>.m4a` 로 **복사**하고 `Submission.duration`/`waveform` 저장 → 제출 시점에 원본 임시 파일이 존재해야 하고, 제출 후에는 지워도 된다.
- 디자인 시스템: `HButton`, `HChip(title, isSelected, modifier, onClick)`, `HAlert(title, message, confirmTitle, onCancel, onConfirm, cancelTitle = "취소", confirmKind = DANGER)`, `HIconView`, `HIcon.UNDO/REDO/PLAY/PAUSE`, `HColors`(`ink, border, line, controlBorder, surfaceStrong, waveformIdle, textTertiary, accent, onAccent, bg`), `HType.plex(size, weight, lineHeightMultiple)`, `ctaBottomPadding(bottomInset)`.
- `app/src/main/AndroidManifest.xml`: `CAMERA` 권한 + `uses-feature camera/camera.front required=false` 만 있고 **마이크 권한 없음**.
- 현재 테스트 40개 통과 상태.

### 원본 iOS (읽기 전용, 수정 금지)
- `ios/HAWE/Features/Mission/DrawMissionView.swift` — 06 + `DrawingCanvasModel` + `PencilCanvas`
- `ios/HAWE/Features/Mission/VoiceMissionView.swift` — 09 + `Waveform` + `VoiceRecorder`
- `ios/HAWE/Features/Mission/MissionPerformView.swift` — 컨테이너/공통 요소 (이미 구현됨)

### iOS 06 그림 명세 (그대로 구현)
- 좌우 20, 세로 간격 16, 아래 여백 32(+ 내비게이션 바 inset — `ctaBottomPadding(32.dp)`), 배경 `bg`.
- 헤더: 닫기 + 오른쪽 `HIcon.UNDO`, `HIcon.REDO` (각 22, 간격 16, 터치 영역 넓게). 활성 = ink, 비활성 = `controlBorder`(회색) + 눌림 불가. 접근성 라벨 "되돌리기", "다시 실행". `hasDraft` = 캔버스가 비어 있지 않음.
- `MissionTitle(DRAW, 주제)`.
- **캔버스**: 흰 바탕, 모서리 `HRadius.card`(20) 클립 + 테두리 `line` 1dp, 높이 **최소 220 ~ 최대 400**(작은 화면에서는 줄어듦), 가로 가득. 손가락으로 그린다.
- **도구 줄**: 왼쪽 `HChip("펜")`, `HChip("지우개")`(간격 6, 선택된 쪽이 selected), 오른쪽 굵기 버튼 3개(간격 4): 각 44×44 원, 고른 것만 `surfaceStrong` 바탕(아니면 `bg`), 가운데 ink 점 지름 4 / 8 / 14, 접근성 라벨 "가는 굵기"/"보통 굵기"/"굵은 굵기".
- **색 줄**: 7색 팔레트 — `ink(#111111)`, `#8E8E93`, `#E5484D`, `#F5A524`, `#F2D74E`, `#30A46C`, `#2F5BFF`. 각 칸 44×44 에 지름 30 원, 줄 전체에 균등 분포(Spacer 사이사이). 색을 누르면 그 색 선택 **그리고 도구가 펜으로 바뀜**. 선택 표시 = ink 테두리 2dp, 단 **도구가 펜일 때만** 표시(지우개 상태에서는 색 선택 표시 없음). 접근성 라벨 "색 1" … "색 7".
- 기본값: 도구 펜, 굵기 보통(medium), 색 0(ink).
- 실제 펜 굵기(dp): 가는 3 / 보통 6 / 굵은 12. 지우개 굵기 = 펜 굵기 × 3 (9 / 18 / 36).
- 남는 공간 Spacer, 맨 아래 `HButton("올리기")` — 캔버스가 비어 있으면 비활성. 누르면 PNG(흰 바탕, 해상도 = 캔버스 크기(dp) × 2) → `SubmissionContent.Image(png, "png")`.
- 되돌리기/다시 실행: 획(pen 또는 eraser 한 번의 드래그) 단위. 새 획을 그리면 다시 실행 목록은 비워진다.
- 지우개는 **픽셀 지우개**(PencilKit `.bitmap`) — 이미 그린 획의 일부를 지운다.

### iOS 09 음성 명세 (그대로 구현)
- 좌우 20, 아래 여백 32(+ 내비게이션 바 inset), 배경 `bg`. 세로 순서: 헤더 → `MissionTitle(VOICE, 주제)` → (위 72) 시간·파형·안내 블록(간격 28) → Spacer(최소 24) → 컨트롤(아래 32) → `HButton("올리기")`.
- 헤더: 닫기만(오른쪽 비어 있음). `hasDraft` = 녹음된 파일이 있음 **또는** 녹음 중.
- 시간 텍스트 `mm:ss`(44 SemiBold, ink, 고정폭 숫자) — `Int(elapsed 내림)` 의 분:초. 그 아래 파형, 그 아래 "최대 30초"(13, textSecondary).
- **파형**: 막대 32개(`barCount`), 막대 폭 4, 간격 4, 모서리 2, 컨테이너 높이 80(가운데 정렬 — iOS `HStack` 기본). 녹음된 막대 높이 = `8 + 72 × level`(level 0..1), 아직 녹음 안 된 막대(level = -1)는 높이 8 색 `waveformIdle`. 녹음된 막대 색: 미리 듣기 중이 아니면 ink; 미리 듣기 중이면 재생 진행률(0..1)보다 앞쪽(`index / count < progress`)은 ink, 뒤쪽은 `textTertiary`. 높이 변화는 짧은 선형 애니메이션(0.1초) 허용(선택).
- **컨트롤**(가운데, 간격 36): 
  - "다시" — 56×56 원, 테두리 `border` 1dp, 글자 13 SemiBold ink. 녹음된 것이 있고 녹음 중이 아닐 때만 활성(아니면 투명도 0.4 + 비활성). 누르면 `reset`. 라벨 "다시 녹음".
  - 녹음 — 84×84 `accent` 원. 대기: 가운데 흰(`onAccent`) 원 지름 28. 녹음 중: 흰 둥근 네모 26×26 모서리 6(정지 모양). 라벨 "녹음 시작"/"녹음 멈추기". 누르면 토글.
  - 미리 듣기 — 56×56 원, 테두리 `border`, `HIcon.PLAY`/`HIcon.PAUSE`(20). 활성 조건은 "다시"와 같음. 라벨 "미리 듣기"/"일시정지".
- `HButton("올리기")`: 녹음이 있고 녹음 중이 아닐 때만 활성. 누르면 재생 정지 후 `SubmissionContent.Voice(file, duration = elapsed(초), waveform = levels 중 0 이상인 값만)`.
- **권한**: 녹음 버튼을 처음 누를 때 마이크 권한 요청. 거부되면 알림창 — 제목 "마이크 권한이 필요해요", 메시지 "설정 > HA'WE 에서 마이크를 켜 주세요.", 버튼 "취소"(회색) / "설정 열기"(PRIMARY, 앱 설정 화면으로 이동). 허용되면 바로 녹음 시작.
- **녹음**: AAC, 모노, 44.1kHz, m4a 컨테이너. 최대 30초가 되면 자동 정지. 시작 시 이전 녹음은 지운다(`reset`). 100ms 간격으로 `elapsed`(최대 30) 와 소리 크기를 갱신: 소리 크기 level = `clamp((dB + 50) / 50, 0, 1)` (dB = 평균 파워, -50dB~0dB → 0~1), 현재 막대 `index = min(31, floor(elapsed / 30 × 32))` 에 `levels[index] = max(levels[index], level)`.
- **정지**: 정지/자동 정지 시 `hasRecording = elapsed > 0`.
- **미리 듣기**: 녹음 파일을 재생, 100ms 마다 진행률 갱신(`currentPosition / max(duration, 0.1초)`), 끝나면 자동으로 정지 상태로 복귀(진행률 0). 재생 중 다시 누르면 정지. 
- 화면이 사라질 때 모든 것 `reset`(녹음·재생 정지, 임시 파일 삭제).

## Root Cause / Key Implementation Points

1. **그림판은 Compose `Canvas` + 자체 상태**. 획(stroke) = 점 목록(dp 단위) + 색 + 굵기(dp) + 지우개 여부. 화면과 PNG 내보내기 모두 **같은 그리기 코드**(`android.graphics.Canvas` 에 그리는 `drawStrokes(canvas, strokes, scale)`)를 쓴다 — 화면에서는 `drawIntoCanvas { it.nativeCanvas }` 로 호출(scale = density), 내보내기에서는 `Bitmap` 위 `Canvas`(scale = 2)로 호출해 결과가 같게 한다.
   - 픽셀 지우개는 `Paint.xfermode = PorterDuffXfermode(CLEAR)` 로 이미 그려진 획을 지운다. CLEAR 는 **투명 레이어**에서만 의미가 있으므로 화면 쪽은 캔버스 Composable 에 `Modifier.graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)` 를 적용하고 흰 배경은 그 레이어 **밖**(아래)에서 칠한다. 내보내기는 (1) 투명 `Bitmap` 레이어에 획 그리기 → (2) 흰색으로 채운 최종 `Bitmap` 위에 레이어를 합성해 PNG 로 압축.
   - 획 그리기: 연속된 점을 `Path` 로 이어 `quadTo` 로 부드럽게(`moveTo(p0)`; 각 구간 중점으로 `quadTo`), `Paint(ANTI_ALIAS, STYLE=STROKE, CAP=ROUND, JOIN=ROUND)`. 점이 하나뿐인(탭만 한) 획은 같은 점으로 `drawPoint`/선 길이 0 + ROUND cap 으로 **점이 찍힌다**.
2. **제스처**: `pointerInput` 안에서 `awaitEachGesture { val down = awaitFirstDown(); … drag(down.id) { change.consume() … } }` 로 첫 포인터만 처리(멀티터치 무시). 점은 캔버스 로컬 좌표(px) → dp 로 변환해 저장. 드래그 중 현재 획은 별도 상태로 두어 매 이벤트마다 전체 목록을 복사하지 않는다(`SnapshotStateList` 의 마지막 원소에 점을 추가하거나 `mutableStateOf` + 버전 카운터로 다시 그리기). 손을 떼면 획 확정. 획이 확정될 때만 undo 스택에 들어가고 redo 스택이 비워진다.
3. **상태/규칙은 순수 클래스로 분리** (`DrawingModel`, Compose·android.graphics 비의존) → 단위 테스트: 도구/굵기/색, 팔레트, 굵기별 dp, 지우개 굵기 = ×3, `strokes`/`redoStack`, `undo()`/`redo()`/`canUndo`/`canRedo`, `isEmpty`, 색 선택 시 도구가 펜으로. **`isEmpty` 정의**: 펜 획이 하나도 없으면 true(iOS 는 PencilKit `strokes.isEmpty` 인데 픽셀 지우개로 모두 지우면 true 가 되는 동작을 Android 에서는 재현하지 않는다 — 전부 지워도 펜 획 기록이 남아 있으면 비어 있지 않은 것으로 본다. 보고서에 차이로 적는다). 지우개 획만 있는 경우도 `isEmpty = true`.
4. **레이아웃 높이**: 헤더(52) + 제목 + 캔버스(220~400) + 도구(44) + 색(44) + 버튼(56) + 간격 16×N + 아래 32. 캔버스 높이 = `(남는 높이).coerceIn(220.dp, 400.dp)`. 구현은 `BoxWithConstraints` 로 가용 높이를 받아 계산하거나, 열의 나머지를 먼저 측정하는 레이아웃 중 하나를 쓰되, 결과: 360×640 같은 작은 화면에서도 내용이 화면 밖으로 넘치지 않고(세로 스크롤을 넣지 않는다 — 스크롤이 캔버스 드래그와 충돌) 올리기 버튼이 항상 아래에 고정되며, 큰 화면에서는 캔버스가 400dp 로 고정되고 남는 공간은 버튼 위 여백이 된다.
5. **09 녹음기는 Compose 밖의 컨트롤러 클래스**(`VoiceRecorderController`)로: `MediaRecorder`(API 31+ `MediaRecorder(context)`, 그 미만 `@Suppress("DEPRECATION") MediaRecorder()`), `setAudioSource(MIC)`, `setOutputFormat(MPEG_4)`, `setAudioEncoder(AAC)`, `setAudioChannels(1)`, `setAudioSamplingRate(44100)`, `setAudioEncodingBitRate(128_000)`, `setMaxDuration(30_000)`, `setOutputFile(path)`, `prepare()`, `start()`. `setOnInfoListener` 로 `MEDIA_RECORDER_INFO_MAX_DURATION_REACHED` 를 받으면 정지 처리. 파일은 `cacheDir/mission-voice.m4a`.
   - `stop()` 은 시작 직후 호출하면 `RuntimeException` 을 던질 수 있다 → 잡아서 "녹음 없음"(`hasRecording = false`, 파일 삭제)으로 처리. `start()`/`prepare()` 실패도 잡아서 녹음 상태를 되돌린다(사용자에게 별도 오류 UI 없음, iOS 와 동일).
   - 소리 크기: 100ms 마다 `getMaxAmplitude()`(0..32767, 호출 사이의 최대값) → 순수 함수 `levelFromAmplitude(amplitude)`: `amplitude <= 0` → 0, 아니면 `dB = 20·log10(amplitude / 32767.0)`, `level = clamp((dB + 50) / 50, 0, 1)`.
   - 경과 시간: `SystemClock.elapsedRealtime()` 차이로 계산(`MediaRecorder` 에는 `currentTime` 이 없다), 최대 30초로 clamp, 정지 후 값 고정.
   - 재생: `MediaPlayer`(`setDataSource(path)`, `prepare()`, `start()`), `setOnCompletionListener` 로 정지 처리, 진행률 = `currentPosition / max(duration, 100ms)`. 한 번에 하나(녹음 중에는 재생 불가 — UI 에서 이미 비활성).
   - 화면을 떠날 때(`DisposableEffect` onDispose) `reset()`: 녹음 중이면 `stop()`(예외 무시)·`release()`, 재생 정지·`release()`, 임시 파일 삭제. **제출 직후 퇴장 애니메이션 중에도 안전**하도록 이미 해제된 객체를 다시 만지지 않는다.
   - 앱이 백그라운드로 가면(`ON_STOP`) 녹음 중이면 **정지**(그때까지 녹음분 유지)하고 재생 중이면 정지한다 — Android 는 백그라운드 마이크 접근을 제한하므로 무음이 녹음되는 것을 막는다.
6. **마이크 권한**: `RECORD_AUDIO` 를 매니페스트에 추가, `<uses-feature android:name="android.hardware.microphone" android:required="false" />`. 녹음 버튼을 누를 때 `checkSelfPermission` → 허용이면 시작, 아니면 `RequestPermission` 런처 → 결과가 허용이면 시작, 거부면 알림창 상태(`showsPermissionAlert`) 켜기. "설정 열기" = `Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))`. 권한 요청 중/직후 화면 재개(`ON_RESUME`) 때 자동 시작하지 않는다(사용자가 다시 눌러야 함).
7. **05 정리**: `isPerformScreenAvailable` 은 네 유형 모두 true(함수는 유지 — 단위 테스트 변경). `MissionScreen.performAction` 은 `onPerform(if (debug) debugType else null)` 만 호출하고 임시 제출 분기·`SubmissionContent`/`store.submit` import 를 제거한다. 디버그 유형 전환(`debugType`, 알약 길게 누르기)은 **남긴다** — 오늘 미션 유형이 랜덤이라 네 유형을 폰에서 확인하려면 필요하다. 주석을 `// 개발용 유형 전환(디버그 전용). 서버 연결 단계에서 삭제해요.` 로 갱신.
8. **PNG/무거운 작업은 메인 스레드 밖**: PNG 렌더링+압축, (필요하면) 오디오 파일 읽기는 `Dispatchers.Default/IO`. 처리 중 "올리기" 연타 방지(`submitted`/`processing` 플래그).

## Files / Functions to Change

새 파일 (`android/app/src/main/java/com/sapphire/hawe/feature/mission/` 기준):
- `DrawingModel.kt` — 순수 상태/규칙: `DrawTool`, `DrawThickness`(dot 4/8/14, penWidthDp 3/6/12, label), `DrawPalette`(색 7개 ARGB `Long`/`Int` 값과 라벨), `DrawStroke(points: List<StrokePoint>, color, widthDp, isEraser)`, `DrawingModel`(undo/redo, `isEmpty`, 도구 선택 규칙, 지우개 굵기 ×3)
- `DrawingRenderer.kt` — `android.graphics` 기반 `drawStrokes(canvas, strokes, scale)` 와 PNG 렌더링 `renderDrawingPng(strokes, widthDp, heightDp, scale = 2f): ByteArray`
- `DrawMissionScreen.kt` — 06 화면(헤더 되돌리기/다시 실행, 제목, 캔버스, 도구 줄, 색 줄, 올리기)
- `VoiceLogic.kt` — 순수 함수/상태: `levelFromAmplitude`, `barIndexFor(elapsedSeconds)`, `updatedLevels(levels, elapsed, level)`, `voiceTimeText(seconds)`, `waveformBarState(index, count, level, progress)`, 컨트롤 활성 규칙(`VoiceUiState`: `canRecord…`, `controlsEnabled`, `canSubmit`, `hasDraft`)
- `VoiceRecorderController.kt` — `MediaRecorder`/`MediaPlayer`/타이머/상태(Compose 상태 노출), `start()/stop()/reset()/togglePlayback()/release()`
- `VoiceMissionScreen.kt` — 09 화면(시간·파형·컨트롤·올리기·권한 알림창)

수정할 파일:
- `feature/mission/MissionPerformScreen.kt` — `DRAW`/`VOICE` 분기 추가: `DrawMissionScreen(topic, onClose) { onSubmit(SubmissionContent.Image(it, "png")) }`, `VoiceMissionScreen(topic, onClose) { file, duration, waveform -> onSubmit(SubmissionContent.Voice(file, duration, waveform)) }`.
- `feature/mission/MissionPerformCommon.kt` — `isPerformScreenAvailable` = 네 유형 true. (`PerformEntryDecision.Unsupported` 는 잘못된 유형 문자열 방어용으로 유지.)
- `feature/mission/MissionScreen.kt` — 위 7번.
- `app/src/main/AndroidManifest.xml` — `RECORD_AUDIO` + `uses-feature microphone required=false`.
- 변경 금지(원칙): `core/data/DataStore.kt`, `core/model/*`, `app/MainNavigation.kt`(필요 없을 것으로 보임 — 수행 라우트는 이미 유형 일반), `app/AuthNavigation.kt`, `feature/auth/*`, `feature/groups/*`, `designsystem/components/*`. 필요해 보이면 최소 변경 후 보고서에 이유 기재.

테스트 (`android/app/src/test/java/com/sapphire/hawe/feature/mission/`):
- `DrawingModelTest.kt`, `VoiceLogicTest.kt`, 기존 `MissionPerformCommonTest.kt` 수정(네 유형 모두 available)

## Implementation Steps

0. **환경**(이전 단계와 동일, 준비됨): `cd C:\hawe\android`, `$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot"`, SDK `C:\Android\Sdk`(`local.properties` 있음). `.\gradlew.bat assembleDebug testDebugUnitTest lintDebug installDebug`. 폰은 USB 연결(`C:\Android\Sdk\platform-tools\adb.exe devices` 에 `device`); 안 보이면 설치만 건너뛰고 보고서에 적는다.
1. `DrawingModel.kt` + `DrawingModelTest` (순수 규칙부터).
2. `DrawingRenderer.kt` (그리기·PNG), `DrawMissionScreen.kt` (제스처, 헤더 undo/redo, 도구/색 줄, 높이 계산, 올리기).
3. `VoiceLogic.kt` + `VoiceLogicTest`.
4. `VoiceRecorderController.kt`, `VoiceMissionScreen.kt` (파형, 컨트롤, 권한 요청/알림창, 라이프사이클 정리).
5. 매니페스트, `MissionPerformScreen` 분기, `isPerformScreenAvailable`, `MissionScreen` 정리 + 테스트 수정.
6. 빌드·테스트·lint·설치, 결과를 `.agent/report.md` 에 기록.

## Constraints

- 변경 범위는 `android/` 안. `ios/`, `src/`(백엔드), `cordova/`, `frontend/`, `docs/`, 루트 `.gitignore`, `AGENTS.md`/`CLAUDE.md` 수정 금지. git 상태를 바꾸는 명령 금지(commit/checkout/stash/reset/clean 등).
- iOS 파일은 읽기 전용. 문구·수치는 iOS 와 동일.
- **새 의존성 추가 금지**(그림판·녹음 모두 플랫폼 API 로 구현). Material 기본 시각 컴포넌트 사용 금지(`HButton`, `HChip`, `HAlert` 등 기존 컴포넌트와 직접 그리기).
- `DataStore`/모델 시그니처·불변 규칙 변경 금지. 서버 호출·영속화 없음.
- **화면이 사라지는 중에도 내비게이션 호출 금지**(4단계 버그 재발 방지). 진입 방어는 이미 `MainNavigation.kt` 에서 한 번만 한다.
- 한글이 들어간 파일은 **UTF-8(BOM 없음)**. PowerShell `Set-Content`/`Out-File` 로 한글 소스를 쓰지 않는다.
- 코드 주석은 iOS 처럼 한국어로 짧게, 화면 문구는 Kotlin 안 한국어 리터럴.

## Edge Cases

**06 그림**
- 아무것도 그리지 않음 → "올리기" 비활성, `hasDraft` false(닫기 확인 없음), undo/redo 둘 다 비활성(회색).
- 탭만 한 번 → 점이 찍히고 비어 있지 않음. 아주 빠른 짧은 획, 캔버스 가장자리 밖으로 드래그(좌표가 캔버스 밖이어도 크래시 없음 — 클리핑됨).
- 되돌리기 → 다시 실행 → 새 획을 그리면 다시 실행 비활성화. 되돌리기를 모두 하면 비어 있음으로 돌아간다. 지우개 획도 한 번 되돌리기로 복원된다.
- 지우개: 이미 그린 획 위에서만 지워지고 흰 바탕이 파이지 않는다(PNG 에서도 배경이 투명/검정이 되지 않고 흰색). 지우개 상태에서 색을 누르면 펜으로 바뀌고 그 색이 선택 표시된다. 지우개 상태에서는 색 선택 표시가 없다.
- 굵기 변경은 이후 획에만 적용(이미 그린 획 불변).
- 두 손가락/멀티터치: 첫 포인터만 그리고 나머지는 무시(획이 튀지 않음).
- 화면 가장자리에서 시작하는 시스템 뒤로 가기 제스처와 캔버스 드래그가 충돌하지 않도록 캔버스는 좌우 20dp 여백 안에 있다.
- 작은 화면(360×640): 캔버스 최소 220, 내용이 넘치지 않음. 큰 화면: 캔버스 400 고정.
- 올리기 연타 → 제출 한 번. PNG 인코딩 중에도 UI 가 멈추지 않는다. 구성 변경(글자 크기 등)으로 Activity 가 재생성되면 그림이 사라질 수 있다(보존 불필요 — 보고서에 한계 기재).
- 전부 지운 뒤(펜 획 기록은 남음) 올리면 흰 빈 이미지가 제출될 수 있다(iOS 와 다른 점, 허용).

**09 음성**
- 최초 녹음 버튼 → 시스템 권한 대화상자 → 허용 즉시 녹음 시작 / 거부 → 알림창 "마이크 권한이 필요해요" → "설정 열기" 로 앱 설정 이동, "취소" 로 닫기. "다시 묻지 않음" 후에는 대화상자 없이 곧바로 알림창.
- 30초 자동 정지: 시간 표시 00:30, 마지막 막대까지 채워지고 녹음 버튼이 대기 모양으로, "다시/미리 듣기/올리기" 활성.
- 녹음을 아주 짧게(0.1~0.3초) 시작하자마자 멈춤 → `MediaRecorder.stop()` 예외 가능 → 녹음 없음으로 정리(크래시·고착 금지). 연속으로 녹음 버튼을 빠르게 두 번 눌러도 상태가 꼬이지 않는다.
- "다시" → 시간 00:00, 파형 초기화(전부 높이 8 `waveformIdle`), 파일 삭제, 컨트롤 비활성. 다시 녹음하면 새 파일.
- 미리 듣기 중 "다시"/"올리기"/닫기 → 재생 정지 후 처리. 재생이 끝나면 자동으로 정지 상태(아이콘 play). 재생 중 파형 진행 색 변화.
- 녹음 중 닫기 X → "그만둘까요?" 확인(`hasDraft` 가 녹음 중도 포함); 확정 시 녹음이 정리되고 임시 파일이 지워진다.
- 앱을 백그라운드로 보냈다 돌아오면: 녹음 중이었다면 이미 정지되어 있고(그때까지 녹음분 유지) 화면이 정상, 재생 중이었다면 정지.
- 전화 수신/다른 앱의 오디오 포커스 — 별도 처리 없음(범위 밖). 단 크래시가 없어야 한다.
- 올리기 연타 → 제출 한 번. 제출 후 퇴장 애니메이션 동안 `release()` 된 recorder/player 를 다시 만지지 않는다.
- 마이크가 없는 기기: 녹음 시작 실패 → 상태 되돌림(`isRecording=false`), 알림창은 띄우지 않는다.

**공통**: 3버튼/제스처 내비게이션에서 하단 버튼이 가려지지 않는다. 같은 그룹의 오늘 미션을 이미 올렸다면 수행 화면에 들어올 수 없다(기존 진입 방어). 디버그 유형 전환으로 열린 06/09 에서 제출하면 `Submission.type` 은 실제 `mission.type` 으로 저장된다(개발용).

## Regression Risks

- `MissionScreen` CTA 변경: 5·6단계(1/3)에서 확인된 카운트다운, 콕 찌르기, 16 이동, 글/사진 수행·제출 후 토스트가 그대로여야 한다. 임시 제출 분기 삭제로 **릴리스에서 그림/음성이 "준비 중이에요" 토스트였던 동작은 사라진다**(이제 정상 수행 화면).
- 매니페스트 권한 추가는 설치/실행에 영향 없음. `uses-feature microphone required=false` 누락 시 마이크 없는 기기에서 설치가 막힌다.
- 수행 라우트의 전환 애니메이션·진입 방어·제출 신호 흐름은 변경하지 않는다(유형만 늘어남). 그림/음성 화면이 `MissionHeader` 를 쓰므로 닫기 확인과 `BackHandler` 가 글/사진과 같이 동작해야 한다.
- 녹음 파일 `cacheDir/mission-voice.m4a` 는 `DataStore.submit` 이 복사한 뒤 화면 정리에서 지워진다 — 제출 전에 지우면 `Submission.mediaURL` 이 null 이 된다(순서 유의: 제출 → 복귀 → onDispose 정리).
- 그림판의 `graphicsLayer(Offscreen)` 이 낮은 사양 기기에서 성능에 영향 — 이번엔 문제 없음. 점 수가 매우 많은 긴 획에서도 입력 지연이 심하지 않은지 폰에서 확인.

## Tests Required

JVM 단위 테스트(JUnit4), 기존 40개 + 6단계 1/3 에서 늘어난 개수 통과 유지:
- `DrawingModelTest`:
  - 초기값: 펜, 보통 굵기, 색 0, `isEmpty`, `canUndo/canRedo` false, 팔레트 7개와 색상 값(`#111111, #8E8E93, #E5484D, #F5A524, #F2D74E, #30A46C, #2F5BFF`)
  - 굵기: dot 4/8/14, 펜 dp 3/6/12, 지우개 dp = 펜 × 3 (9/18/36)
  - 획 추가 → `isEmpty` false, `canUndo` true, undo → 비어 있음·`canRedo` true, redo → 복원, undo 후 새 획 → redo 비워짐, 빈 상태 undo/redo 는 무동작
  - 지우개 획만 있는 경우 `isEmpty` true, 펜 획 + 지우개 획이면 false
  - 색 선택 시 도구가 펜으로 바뀜, 지우개 상태에서는 "색 선택 표시" 규칙(`showsColorSelection`) false
  - 굵기 변경은 기존 획에 영향 없음
- `VoiceLogicTest`:
  - `levelFromAmplitude`: 0 → 0, 32767 → 1, 약 -50dB 이하(예: 100) → 0, -25dB 부근 → 약 0.5, 음수 방어
  - `barIndexFor`: 0초 → 0, 15초 → 16, 29.99초 → 31, 30초 이상 → 31(범위 clamp)
  - `updatedLevels`: 같은 막대에서 큰 값 유지(max), 미녹음 막대는 -1 유지, 제출용 필터(`>= 0`)
  - `voiceTimeText`: 0 → `00:00`, 5.9 → `00:05`, 59.99 → `00:59`, 61 → `01:01`, 30 → `00:30`
  - `waveformBarState`: 미녹음 → idle(높이 8), 녹음된 막대 높이 `8 + 72·level`, 재생 진행률에 따른 색 상태(played / notPlayed), 진행률 null 이면 모두 played
  - `VoiceUiState`: 대기/녹음 중/녹음 있음 조합에 따른 컨트롤 활성(다시·미리듣기·올리기), `hasDraft`(녹음 있음 또는 녹음 중)
- `MissionPerformCommonTest`: `isPerformScreenAvailable` 네 유형 모두 true, 진입 판단 케이스 갱신(DRAW/VOICE 요청 → Allow).

Canvas 제스처·MediaRecorder·MediaPlayer·권한 UI 는 자동 테스트하지 않는다 — 폰 확인으로 대체.

## Validation Steps

Codex 가 직접 실행: `.\gradlew.bat assembleDebug testDebugUnitTest lintDebug installDebug` (JDK 17). 기준: 빌드 성공, 테스트 전부 통과, lint 새 오류 0(경고 건수 보고), 폰 설치 후 `adb shell am start -n com.sapphire.hawe/.MainActivity`.

사용자가 폰에서 확인할 시나리오 — 보고서에 그대로 적을 것:
1. 가입 → 03 에서 `K7Q2PX` 참여(또는 02 로 그룹 생성) → 홈 카드 → 05. **디버그 빌드에서 "오늘은 ○○" 알약을 길게 눌러** 그림 → 06, 음성 → 09 를 번갈아 연다(글/사진은 이전 단계 확인).
2. **06 그림**: 처음에는 "올리기" 비활성·되돌리기/다시 실행 회색. 손가락으로 그리면 선이 부드럽게 따라온다. 탭 한 번으로 점. 펜/지우개 전환, 굵기 3단계가 선 굵기와 지우개 크기에 반영, 색 7개(고르면 테두리 표시, 지우개 중에는 표시 없음, 색을 누르면 펜으로 복귀), 지우개로 선 일부가 지워지고 흰 바탕은 그대로, 되돌리기/다시 실행, 그림이 있는 상태에서 X/뒤로 → "그만둘까요?", 올리기 → 05 복귀 + 토스트 + 완료 +1.
3. **09 음성**: 처음에는 `00:00`, 파형 전부 회색 낮은 막대, 다시/미리듣기/올리기 비활성(투명). 녹음 버튼 → 권한 대화상자 → 허용하면 시간이 올라가고 말하면 파형이 차오름, 녹음 버튼이 흰 네모로 바뀜. 정지 → 다시/미리듣기/올리기 활성. 미리 듣기 → 소리가 나고 파형이 재생 위치에 따라 진해졌다가 끝나면 자동 정지. 30초까지 두면 자동 정지. 다시 → 초기화. 올리기 → 05 복귀 + 토스트.
4. 권한을 거부했을 때 "마이크 권한이 필요해요" 알림창 → "설정 열기" 로 앱 설정 이동 → 마이크 켜고 돌아와 다시 녹음.
5. 녹음 중 앱을 홈으로 보냈다 돌아오면 녹음이 멈춰 있고 화면이 정상인지. 녹음 중 X → "그만둘까요?".
6. 작은 화면 느낌 확인: 폰의 글자 크기/화면 크기를 키워도 06 의 올리기 버튼이 화면 아래에 보이는지, 3버튼/제스처 내비게이션 하단.
7. 이전 단계 회귀: 05 카운트다운, 콕 찌르기, 16 이동·나가기 후 홈 복귀, 글/사진 수행·제출.
8. 릴리스 동작 확인은 불가하므로 디버그 전용 코드(유형 전환)가 `FLAG_DEBUGGABLE` 로 가드되어 있고 임시 제출 분기가 삭제되었음을 코드로 확인했다고 보고서에 적기.

## Potential Merge Conflicts

- `android/` 만 수정. 백엔드·iOS·Cordova 와 겹치지 않는다.
- 공유 파일 수정: `feature/mission/MissionScreen.kt`, `MissionPerformScreen.kt`, `MissionPerformCommon.kt`, `AndroidManifest.xml`. 다음 단계(7단계 결과 공개 10~14: 05 의 "친구들 작품 보기" 연결, 음성 작품 재생)가 `MissionScreen.kt` 를 다시 수정하므로 이 단계를 먼저 병합한다.
- 작업 트리에 이전 단계들의 미커밋 변경이 남아 있을 수 있다. `git status` 로 확인만 하고 커밋하지 않는다.
- `.agent/report.md` 는 Codex 소유. 기준 iOS 커밋(`git log -1 --format=%h -- ios/`), 빌드·테스트·lint·설치 결과, iOS 와 다르게 한 부분(`isEmpty` 정의, 픽셀 지우개 구현 방식, 앰플리튜드→레벨 변환, 백그라운드 정지, 높이 계산 방식), 폰 확인 체크리스트, 남은 위험을 적는다.
