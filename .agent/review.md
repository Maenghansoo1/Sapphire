# Review — Android 6단계 (2/3): 06 그림 + 09 음성

기준: `.agent/plan.md`, `.agent/report.md`, 작업 트리의 `android/` 미커밋 변경. 범위 밖 파일 변경 없음, 새 의존성 없음, `DataStore`/모델/`MainNavigation.kt` 변경 없음, 매니페스트는 `RECORD_AUDIO` + `uses-feature microphone required=false` 확인.
Codex 가 `assembleDebug` / `testDebugUnitTest`(46개 통과) / `lintDebug`(오류 0) / `installDebug` 를 직접 실행한 결과는 보고서대로라고 가정했다. 아래는 코드를 읽어 확인한 내용이며 폰에서 직접 그려 보거나 녹음해 보지는 않았다.

## 확인한 항목 (문제 없음)
- **그림 렌더링**: 화면과 PNG 가 같은 `drawStrokes(canvas, strokes, scale)` 를 쓰고(화면 = density, 내보내기 = 2), 지우개는 `PorterDuff.CLEAR` 를 **투명 오프스크린 레이어**(화면: `graphicsLayer(Offscreen)` 안쪽 `Canvas`, 흰 배경은 바깥 `Box`; 내보내기: 투명 `ink` 비트맵 → 흰 비트맵에 합성)에서만 적용한다. 단일 탭은 채운 원으로 찍힘(지우개도 CLEAR 복사 페인트), 곡선은 중점 `quadTo` 로 부드럽게.
- **제스처**: `awaitEachGesture` + `awaitFirstDown` + `drag(down.id)` 로 첫 포인터만 처리, 취소돼도 `model.end()` 호출, 점은 dp 로 저장. 되돌리기/다시 실행은 획 단위이며 새 획에서 redo 가 비워지고, 도구/굵기/색 규칙(색 선택 → 펜, 지우개 폭 ×3, 팔레트 7색 값)은 iOS 명세와 일치. `isEmpty` 정의(펜 획 기준, 지우개만 있으면 true)도 계획과 같다.
- **PNG 제출**: `Dispatchers.Default` 에서 렌더링·압축 후 메인에서 `onSubmit`, 연타 방지.
- **녹음 컨트롤러**: `MediaRecorder` 설정 순서(source → format → encoder → channels/rate/bitrate → maxDuration → file → prepare → start), API 31 분기, `stop()` 예외를 잡아 "녹음 없음" 처리, 30초 도달 시 ticker 와 info 리스너의 중복 `stop()` 은 `isRecording` 가드로 무해, 실패 경로(`start` 예외)에서 상태 복구·파일 삭제, `ON_STOP` 에서 녹음/재생 정지, `release()` 이후 재사용 가드.
- **음성 흐름**: 제출 시 `DataStore.submit` 이 임시 m4a 를 복사한 뒤 화면 정리(`onDispose → release → reset`)에서 원본을 지우는 순서가 맞다. 권한은 녹음 버튼에서 요청, 거부면 "마이크 권한이 필요해요" 알림창 → 앱 설정 이동. 진행 중 요청에는 버튼 비활성.
- **05 정리**: 디버그 임시 제출 분기가 삭제되고 CTA 는 `onPerform(...)` 만 호출, 유형 전환(`debugType`)은 `FLAG_DEBUGGABLE` 가드 아래 유지. `MissionPerformScreen` 이 4개 유형을 모두 분기한다.

---

## P2

### File / Location
`android/app/src/main/java/com/sapphire/hawe/feature/mission/DrawMissionScreen.kt` — `version++` 와 `drawStrokes(..., model.strokes + listOfNotNull(model.activeStroke), density)`, `DrawingModel.kt` — `strokes` / `activeStroke` 게터

### Problem
(1) 포인터 이벤트마다 `version++` 를 올리고 그 값을 컴포지션 본문에서 읽기 때문에(`val drawVersion = version`) **드래그 중 매 이벤트마다 화면 전체**(헤더, 제목, 도구/색 줄, 버튼)가 재구성된다. (2) `strokes` 게터는 매번 `history.toList()`, `activeStroke` 는 `currentPoints.toList()` 로 목록을 복사하고, 그리기 때마다 이 복사본으로 모든 획의 `Paint`/`Path` 를 다시 만든다. 긴 획(수천 점)이나 획이 많아지면 입력 지연이 생길 수 있다(계획 Root Cause 2 의 "매 이벤트마다 전체 목록을 복사하지 않는다" 와 다름).

### Why It Matters
그림판의 입력 반응성은 06 의 핵심 체감이다. 폰(고주사율)에서 문제없을 수도 있지만, 오래 그리거나 획이 많을 때 끊김이 생길 수 있는 구조다. 현재는 정확성 문제는 아니다.

### Recommended Fix
(선택, 폰에서 끊김이 느껴질 때) `version` 을 캔버스 `Canvas` 의 draw 단계에서만 읽도록 분리하고(UI 상태 `canUndo/canRedo/hasDraft` 는 획 시작·종료·undo/redo 에서만 갱신하는 별도 상태 또는 `derivedStateOf`), 그리기에서는 `history` 를 복사하지 않고 읽기 전용 뷰/인덱스 접근을 쓰며 `Paint` 는 획마다 재생성하지 않고 재사용한다. 지금 고치지 않으면 보고서의 "남은 위험"에 한 줄 적어 두면 충분하다.

---

### File / Location
`DrawMissionScreen.kt` — `BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) { val canvasHeight = minOf(maxHeight, 400.dp) ... }`

### Problem
캔버스 높이는 `min(남는 높이, 400dp)` 라서 (a) 계획의 **최소 220dp** 하한이 없고(아주 작은 화면에서 220 미만으로 줄어듦, iOS `minHeight: 220`), (b) 남는 높이가 400dp 를 넘는 큰 화면에서는 캔버스가 박스 위쪽에 400dp 로 붙고 **남는 공간이 캔버스와 도구 줄 사이**(`weight(1f)` 박스 아래쪽)에 생긴다. iOS 는 캔버스 바로 아래에 도구/색 줄이 붙고 남는 공간은 색 줄과 올리기 버튼 사이에 생긴다.

### Why It Matters
시각 차이(큰 화면에서 캔버스와 도구 줄이 떨어져 보임)와 아주 작은 화면에서의 캔버스 너무 작음. 기능에는 영향이 없다.

### Recommended Fix
캔버스 `Box` 만 `heightIn(min = 220.dp, max = 400.dp)` 로 두고 나머지는 위에서 아래로 쌓은 뒤, 올리기 버튼 위에만 `Spacer(Modifier.weight(1f))` 를 둔다. 작은 화면에서 줄이는 쪽은 캔버스 높이(가용 높이 계산 기준 220~400)로 맞춘다.

---

## 결론

**승인 가능** — P0/P1 없음. P2 두 건(그림판 재구성·복사 비용, 캔버스 높이 하한/여백 위치)은 선택이며, 폰에서 끊김이나 큰 화면의 여백이 눈에 거슬릴 때 고치면 된다. 폰에서 06·09 시나리오(그리기/지우개/되돌리기, 녹음/미리 듣기/30초 자동 정지, 권한 거부 → 설정 열기)와 이전 단계 회귀를 확인한 뒤 7단계로 넘어가도 좋다.
