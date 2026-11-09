# A2. 실시간 AI 판단 — Android 그리는 중 추론 연동 + UI

> 범위: Android `feature/mission/DrawMissionScreen.kt` 와 `feature/draw/` 연동. **선행: A1**(`LiveGuessTracker`, `InferenceGate`). 서버 변경 없음. 근거: `docs/FEATURE/AI_FEATURES.md` §1.
> 빌드/테스트: JDK 21 — `android\gradlew.bat :app:testDebugUnitTest --offline`.

## Goal

그림 미션에서 **획을 그을 때마다 AI 가 계속 추측**하고, 주제를 맞히는 순간 즉시 반응한다("AI가 맞혔어요!" + 경과 시간). 기존 "AI에게 보여주기" 버튼과 제출 흐름(`aiGuess` 업로드)은 그대로 유지한다.

## Findings

- `DrawMissionScreen.kt`: `onSubmit: suspend (ByteArray, List<AiGuessEntry>) -> Boolean`, `rememberAiGuessAvailable()`, `rememberAiGuessRunner()`, `AiGuessDialog(aiGuess)`, 제출 시 `guessForUpload(context, ink)`.
- `feature/draw/`: `InkPreprocessor`(스트로크/PNG → 28×28), `QuickDrawClassifier`(TFLite, 지연 로드 싱글턴), `DrawSubjectMatcher`(주제→범주), `AiGuess.kt`. 모델/라벨 로드 실패 시 기능 숨김이 이미 규칙이다.
- A1 이 제공하는 순수 로직: `LiveGuessTracker.onInference/onEmpty/reset`, `InferenceGate`.
- 대상 틀: `DRAW_SKETCH`, `DRAW_A_TO_B`(판정 기준은 `drawTo`). 대상 지정 미션은 판정하지 않고 추측만(A8 규칙).

## Key Implementation Points

1. **호출 시점**: 캔버스에서 획이 끝날 때(pointer up) + 그리는 중에는 `InferenceGate` 로 500ms 간격 제한. 추론은 `Dispatchers.Default`. 이전 추론이 진행 중이면 최신 요청 하나만 대기.
2. 스트로크 → 마스크 → `InkPreprocessor` → `QuickDrawClassifier.classify` → `LiveGuessTracker.onInference`. 지우개 적용 후 잉크가 부족하면 `onEmpty()`.
3. **UI**: 캔버스 위/옆에 작은 AI 말풍선(임시 아이콘 재사용: `ic_ai_guess_temp`). 상태별 문구 — 생각 중: "AI 가 지켜보는 중…", 추측: 상위 1~2개 라벨 + 퍼센트(깜빡이지 않게 A1 안정화 결과만 표시), 불확실: "음… 아직 모르겠어요", 맞힘: "AI가 맞혔어요! 🎉" + "○초 만에"(경과 시간은 그림 시작 시각 기준). **비난 문구 금지**("AI가 아직 배우는 중이에요" 같은 중립/유머).
4. 맞혀도 자동 제출·잠금 없음. 계속 그릴 수 있다. 새로 시작/전체 지우기 시 `reset()`.
5. 제출 시 기존 `guessForUpload(context, ink)` 를 **그대로** 호출한다(실시간 상태에 의존하지 않음). 실시간 결과는 어디에도 저장·전송하지 않는다.
6. 30초 스케치(`seconds` 제한)의 타이머와 겹치는 레이아웃을 확인하고 작은 화면에서 말풍선이 캔버스를 가리지 않게 한다.
7. 접근성: 말풍선에 `contentDescription`(라벨·확률). 모델/자산 누락 시 말풍선을 숨기고 크래시 없음(`rememberAiGuessAvailable()` 재사용).

## Files / Functions to Change

| 파일 | 변경 |
|---|---|
| `android/app/src/main/java/com/sapphire/hawe/feature/mission/DrawMissionScreen.kt` | 획 종료/그리는 중 추론 호출, 말풍선 표시, reset 연결 |
| `feature/draw/LiveGuessRunner.kt` (신규) | `rememberLiveGuess(...)` — 코루틴/게이트/트래커를 묶는 Compose 헬퍼 |
| `feature/draw/LiveGuessBubble.kt` (신규) | 말풍선 UI |
| `feature/draw/QuickDrawClassifier.kt` | 필요하면 raw 확률 노출 API만 추가(기존 동작 불변) |
| 테스트 | `LiveGuessRunner` 의 순수 부분(상태→문구 매핑)만 JVM 테스트 |

## Constraints

- `android/` 만. 서버 API·제출 필드 변경 금지. 새 의존성 금지.
- 그림·마스크·추론 결과를 파일/로그/분석/서버에 남기지 않는다.
- 제출 가능 여부·도장·점수에 AI 추측이 영향을 주지 않는다.
- 호출 빈도 상한을 지킨다(매 프레임 금지) — 저사양 기기 프레임 드랍 확인.
- 커밋/푸시 금지.

## Edge Cases

- 빠르게 연속 획 → coalescing 으로 추론이 쌓이지 않음.
- 화면 회전/백그라운드 → 진행 중 추론 취소, 복귀 시 상태 재계산 또는 reset.
- 주제가 범주에 없음 → 판정 없이 추측만("AI는 ○○라고 생각해요" 수준).
- 대상 지정 미션 → 판정 없음.
- 첫 추론 지연(모델 로드) → "생각 중" 표시로 가림.
- 지우개로 전부 지움 → TooEmpty, 이미 맞힌 기록은 유지.

## Tests Required

- 상태→문구/표시 매핑 순수 함수 테스트(`Correct`, `Unsure`, `TooEmpty`, 주제 미매핑).
- A1 테스트는 A1 에서 이미 수행. 이 단위에서는 UI 연결 부분은 수동.

## Validation Steps

`android\gradlew.bat :app:testDebugUnitTest --offline` 통과. 수동 체크리스트(실기기, 결과를 보고서에 기록): 고양이/집/구름 등 10개 그려 보기 — 맞히기까지 획 수/초, 오탐 횟수, 빈 그림, 작은 그림, 저사양 기기 프레임, 30초 스케치 타이머와 레이아웃. 못 했으면 "미검증".

## Potential Merge Conflicts

`DrawMissionScreen.kt`(다른 그림 화면 작업과 순차), `feature/draw/QuickDrawClassifier.kt`.
