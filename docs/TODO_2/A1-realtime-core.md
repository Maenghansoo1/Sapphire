# A1. 실시간 AI 판단 — 연속 추론 상태 로직 (순수 Kotlin)

> 범위: Android `feature/draw/` 의 **순수 로직 + 단위 테스트만**. 화면 연결은 A2. 서버 변경 없음. 근거: `docs/FEATURE/AI_FEATURES.md` §1.
> 빌드/테스트: JDK 21 — `set JAVA_HOME=C:\Users\kimch\.jdks\ms-21.0.10` 후 `android\gradlew.bat :app:testDebugUnitTest --offline`.

## Goal

그리는 도중 호출되는 추론 결과(범주별 확률)를 받아 화면에 보여 줄 **안정된 추측**과 **"맞혔다" 판정**을 계산하는 상태 머신을 만든다. UI·TFLite·코루틴 스코프에 의존하지 않는 순수 클래스라서 JVM 테스트로 검증된다.

## Findings

- 이미 있는 것(`android/app/src/main/java/com/sapphire/hawe/feature/draw/`): `AiGuess.kt`(`AiGuess(category, probability)`, `topGuesses`, `AiVerdict`(`TooEmpty/Unsure/Guessed/Correct/Close/Missed`), `aiVerdict(guesses, subjects, threshold = AI_GUESS_THRESHOLD)`, `AiVerdictCopy`), `InkPreprocessor.kt`, `QuickDrawClassifier.kt`, `DrawSubjectMatcher.kt`(미션 주제 → 범주 매핑), `AiGuessDialog.kt`. 현재 흐름은 "AI에게 보여주기" 버튼 → 1회 추론이다(`DrawMissionScreen.kt` 의 `rememberAiGuessRunner`).
- 모델 출력은 확률 벡터(범주 ~50), 입력은 28×28. 추론 비용이 작아 획마다 호출 가능.
- 새 모델·서버는 필요 없다. 이 단위는 **언제 추론을 부르고 결과를 어떻게 안정화·판정하는가** 만 다룬다.

## Key Implementation Points

1. 신규 파일 `feature/draw/LiveGuess.kt` (이름은 구현 시 조정 가능):
   - `class LiveGuessTracker(config: LiveGuessConfig, subjects: List<DrawCategory>)` — 시간 소스를 주입(`now: () -> Long`)해 테스트에서 가짜 시계 사용.
   - `fun onInference(probabilities: FloatArray, categories: List<DrawCategory>, atMillis: Long): LiveGuessState`.
   - `LiveGuessState`: 안정화된 상위 3개(`List<AiGuess>`), `guessed: Boolean`, `guessedAtMillis: Long?`(경과 시간 표시용), `status`(`TooEmpty/Thinking/Unsure/Guessed/Correct` — 기존 `AiVerdict` 와 대응하되 라이브용으로 단순화해도 됨).
2. **안정화(EMA)**: 범주별 확률을 최근 3회 지수이동평균(`alpha` 설정 가능, 기본 0.5)으로 평활. 상위 1위 교체는 **새 1위 확률이 현재 1위보다 `margin`(기본 0.1) 이상 높을 때만**(히스테리시스).
3. **맞힘 판정**: 미션 주제가 범주에 매핑된 경우(`subjects` 비어 있지 않음), 그 범주 중 하나의 확률이 `correctThreshold`(기본 0.6, 보정 전 임시값 — 상수 하나로 노출) 이상인 상태가 **`requiredStreak`(기본 2)회 연속**이면 `Correct`. 한 번 `Correct` 가 되면 `guessedAtMillis` 를 **첫 도달 시각**으로 고정(그림을 더 그려도 바뀌지 않음, 새 그림 시작/지우기로 `reset()` 시 초기화).
4. 주제 미매핑이거나 대상 지정(`[대상]`) 미션이면 판정 없이 추측만 반환(`subjects` 비어 있음).
5. **빈 그림**: 잉크 부족(`InkPreprocessor` 가 null)이면 `onEmpty()` 로 `TooEmpty` 상태, EMA 를 리셋.
6. 호출 빈도 제어는 이 클래스가 아니라 A2 의 책임이지만, **디바운스/coalescing 순수 헬퍼**(`class InferenceGate`: "획 종료 즉시 허용, 그 외 마지막 호출 후 `minIntervalMillis`(기본 500) 이내면 거부, 추론 진행 중 새 요청은 `pending=true` 로 표시해 끝나면 한 번만 재실행")를 같은 파일에 두어 JVM 에서 테스트한다.

## Files / Functions to Change

| 파일 | 변경 |
|---|---|
| `android/app/src/main/java/com/sapphire/hawe/feature/draw/LiveGuess.kt` (신규) | `LiveGuessConfig`, `LiveGuessState`, `LiveGuessTracker`, `InferenceGate` |
| `android/app/src/test/java/com/sapphire/hawe/feature/draw/LiveGuessTest.kt` (신규) | 아래 테스트 |

기존 `AiGuess.kt` 의 `AiVerdict`/`aiVerdict` 는 **수정하지 않는다**(재사용만).

## Constraints

- `android/` 만. 서버/다른 모듈 수정 금지. 의존성 추가 금지.
- Android 프레임워크 클래스(Context, Bitmap) 사용 금지 — 순수 Kotlin.
- 추론 결과·경과 시간을 저장·전송하지 않는다(메모리 상태만).
- 비난 문구를 이 계층에서 만들지 않는다(문구는 A2).
- 커밋/푸시 금지.

## Edge Cases

- 1위가 두 범주 사이를 번갈아 흔들림 → 히스테리시스로 고정.
- `requiredStreak` 도중 확률이 임계 아래로 한 번 떨어짐 → 연속 카운터 0 으로.
- 추론 결과가 같은 값으로 반복 → 상태 불변(불필요한 recomposition 방지를 위해 `equals` 가 같음).
- 지우개로 대부분 지움 → `onEmpty()` 호출 시 맞힘 상태 해제 여부: **해제하지 않는다**(이미 맞혔다는 기록 유지), 단 `reset()` 은 새 그림에서만.
- 범주 목록 순서와 확률 길이가 다름 → 예외 대신 `Unsure`/무시하고 로그(크래시 금지).

## Tests Required (`LiveGuessTest`)

- EMA 수렴, 히스테리시스(마진 미만이면 1위 유지, 이상이면 교체).
- `requiredStreak` 경계(1회 → 아직, 2회 연속 → Correct, 중간 하락 → 리셋).
- 주제 미매핑/대상 미션 → `guessed=false` 유지하고 추측만.
- `guessedAtMillis` 고정(가짜 시계), `reset()` 초기화.
- `InferenceGate`: 500ms 이내 거부, 진행 중 요청은 pending 으로 1회만 재실행, 획 종료는 즉시 허용.
- 길이 불일치/NaN 입력 방어.

## Validation Steps

`android\gradlew.bat :app:testDebugUnitTest --offline`(JDK 21) 통과 기록. 새 클래스의 공개 API(A2 가 호출할 함수 시그니처)를 보고서에 적는다.

## Potential Merge Conflicts

신규 파일 위주라 충돌 낮음. `feature/draw/AiGuess.kt` 는 읽기만 한다.
