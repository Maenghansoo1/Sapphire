# A10. 그리는 동안 AI가 실시간으로 추측하기 (Android)

> 범위: Android 앱만(서버 변경 없음). 작성 2026-11-09. iOS 이식은 경환(아래 "iOS 넘김"). 근거: A8(온디바이스 그림 인식) 결과물을 그대로 쓴다.
>
> **목표 한 줄**: 지금은 다 그린 뒤 "AI 보기"를 눌러야 한 번 추측한다. 이걸 **획을 하나 그을 때마다** AI가 "고양이? 강아지?"처럼 실시간으로 추측해 보여 주게 바꾼다.

## Goal

- 획을 끝낼 때마다(손을 뗄 때) 상위 추측 3개와 확률이 그림판 위에 바뀌어 보인다.
- 미션 주제를 알아본 순간 "AI가 알아봤어요!"를 보여 준다(진동 포함, 끌 수 있음).
- 그리는 손이 끊기지 않는다(추론은 백그라운드, 오래된 결과는 버림).
- 모든 계산은 휴대폰 안에서 한다. 서버 호출 없음.

## Findings (2026-11-09 코드 기준)

- 획 데이터: `feature/mission/DrawingModel.kt` — `strokes`(끝난 획), `activeStroke`(그리는 중), 손을 떼면 `end()`(54줄)에서 `history` 에 추가. 되돌리기·다시 실행·지우개 있음.
- 그림 → 모델 입력: `feature/draw/DrawingInk.kt` `strokeInkImage(strokes, widthDp, heightDp)`(25줄)가 **그림판 크기 그대로** 비트맵을 그린 뒤 `InkImage.modelInput()` → `InkPreprocessor.preprocess`(28×28).
- 모델: `feature/draw/QuickDrawClassifier.kt` `classify`(24줄). `Mutex` 로 동시 호출을 막는다(Interpreter 는 동시에 못 씀). 모델 158KB int8.
- 판정: `feature/draw/AiGuess.kt` `topGuesses`(37줄) → `aiVerdict`(63줄, 0.55 기준) → `aiVerdictCopy`(79줄). 주제 찾기는 `DrawSubjectMatcher.subjects`(50줄, A를 B로는 A·B 둘 다).
- 지금 흐름: `DrawMissionScreen.kt` 166줄 "AI 보기" 버튼 → `AiGuessRunner.guess`(`AiGuessDialog.kt` 51줄) → 대화상자. 제출할 때는 `guessForUpload`(184줄)로 한 번 더 추론해 서버(마스코트 댓글)에 보낸다.
- 너무 빈 그림은 `InkPreprocessor.minInk` 기준으로 추론하지 않는다(TooEmpty).

## Key Implementation Points

1. **언제 추론하나**: 손을 뗄 때(`end()`), 되돌리기, 다시 실행, 지우개 획이 끝날 때. 그리는 중(손가락 이동)에는 하지 않는다.
2. **짧은 대기(디바운스)**: 손을 떼고 약 250ms 안에 다음 획을 시작하면 그 추론은 건너뛴다(빠르게 여러 획을 그을 때 낭비 방지).
3. **오래된 결과 버리기**: 그림이 바뀔 때마다 버전 번호를 올리고, 추론이 끝났을 때 버전이 다르면 결과를 버린다. 진행 중인 추론은 취소한다.
4. **가볍게 그리기**: 지금 `strokeInkImage` 는 그림판 원래 크기(수백~천 px)로 그린다. 어차피 28×28로 줄이므로 **작은 크기(예: 긴 변 256px)로 그려도 결과가 같은지** 먼저 확인하고, 같으면 작게 그린다(속도). 다르면 원래 크기 유지.
5. **표시 규칙**: 1등 확률이 0.55보다 낮으면 "음… 뭘까?"처럼 자신 없는 표시, 높으면 이름과 %. 주제(`subjects`)와 같은 범주가 1등이 되면 "AI가 알아봤어요!" 한 번만.
6. **"AI 보기" 버튼과 제출 흐름은 그대로 둔다.** 실시간 표시는 그 위에 더하는 기능이다(설정으로 끌 수 있게).

## Implementation Steps

**준비**
1. `git pull`, 커밋 안 한 작업 정리.
2. A8 코드 읽기: `DrawingModel`, `DrawingInk`, `InkPreprocessor`, `QuickDrawClassifier`, `AiGuess`, `AiGuessDialog`, `DrawSubjectMatcher`.
3. 실제 기기에서 지금 1회 추론 시간 재기(그림 그리기 + 전처리 + 모델 실행을 나눠서). 로그 또는 계측 테스트.
4. 목표 시간 정하기(예: 손을 뗀 뒤 100ms 안에 표시). 3번 결과로 가능한지 판단.

**속도 개선 확인**
5. `strokeInkImage` 를 작은 크기로 그리는 버전 만들기(선 굵기도 비율에 맞춤).
6. 팀 그림 249장(`ml/eval-data/quickdraw/`)과 골든 그림으로 "원래 크기 vs 작은 크기"의 1등 추측이 같은지, 확률 차이가 얼마인지 비교.
7. 결과가 같으면 작은 크기 채택, 다르면 원래 크기 유지(보고서에 수치 기록).

**상태 관리 (순수 Kotlin, 단위 테스트 가능하게)**
8. `LiveGuessState` 만들기: 현재 버전, 마지막 추측 3개, 판정, "알아봤어요"를 이미 보여 줬는지.
9. 디바운스·취소·버전 확인 로직 만들기(코루틴 `Job` 하나만 유지).
10. 빈 그림(TooEmpty), 모델 로드 실패(`aiAvailable == false`) 처리.
11. 단위 테스트: 순서가 뒤바뀐 결과 버리기, 빠른 연속 획에서 마지막 것만 추론, 되돌리기로 빈 그림이 되면 표시 지우기, "알아봤어요"는 한 번만.

**그림판 연결**
12. `DrawingModel.end()`, 되돌리기, 다시 실행에서 "그림이 바뀜" 신호 보내기.
13. `DrawMissionScreen` 에서 신호를 받아 `LiveGuessState` 로 추론 요청.

**화면**
14. 그림판 위쪽에 작은 추측 칩(1등 이름 + %) 만들기. 디자인 토큰만 사용.
15. 칩을 누르면 상위 3개 막대(지금 대화상자의 막대 컴포넌트 재사용).
16. 추측이 바뀔 때 부드러운 전환(접근성 "동작 줄이기" 설정이면 애니메이션 끔).
17. "AI가 알아봤어요!" 순간 표시 + 가벼운 진동(설정으로 끌 수 있음).
18. 실시간 표시 켜기·끄기 설정(기본 켬). 저장은 기기 안(`DataStore` 또는 SharedPreferences).
19. 접근성 문구(contentDescription) 추가.

**재미 데이터 (나중 기능 대비, 기기 안에만)**
20. "AI가 알아볼 때까지 걸린 획 수"를 그림마다 계산해 둔다(이번에는 서버로 보내지 않음). 그림 성장 그래프·스피드상 같은 다음 기능의 바탕.
21. 제출할 때 실시간으로 이미 계산한 마지막 추측이 있으면 `guessForUpload` 의 중복 추론을 건너뛰고 재사용(선택).

**테스트·확인**
22. 계측 테스트: 실제 기기에서 1회 추론 시간이 목표 안인지.
23. 수동 확인 목록: 빠른 낙서, 긴 한 획, 지우개, 되돌리기 반복, 앱 백그라운드 갔다 오기, 저사양 기기, 설정 끔.
24. 기존 테스트(`InkPreprocessorTest`, `AiGuessTest`, 계측 `QuickDrawClassifierTest`) 통과 확인.

**마무리**
25. `docs/FRONTEND/ANDROID.md` 에 기능 설명 추가.
26. iOS 넘김 문서 작성(아래).
27. 커밋·푸시, 계획 파일 이름을 `A10(완료)-...` 로 바꾸기.

## iOS 넘김 (경환)

- iOS 그림 화면(`DrawMissionView`)에서 같은 방식(손을 뗄 때 추론, 디바운스, 버전 확인)으로 Core ML 모델 호출.
- 표시 규칙(0.55, 알아봤어요 한 번)과 문구는 Android 와 같게.

## Edge Cases

- 지우개로 전부 지움, 되돌리기로 빈 그림 → 표시 지움.
- 아주 빠른 연속 획 → 마지막 상태만 추론.
- 모델을 못 불러옴 → 실시간 표시 숨김(지금 "AI 보기" 버튼과 같은 조건).
- "A를 B로" 미션 → A나 B 중 하나만 1등이어도 "알아봤어요".
- 그림판 크기가 다른 기기(태블릿 등).

## Constraints

- 서버 호출 없음. 그림과 추측은 표시용으로만 쓴다(제출 흐름은 기존 그대로).
- 전처리 규칙(`PREPROCESS_SPEC.md`)은 바꾸지 않는다. 작게 그리기는 결과가 같을 때만.
- 메인 스레드에서 추론하지 않는다.

## Tests Required

- `LiveGuessState` 단위 테스트(위 11번).
- 작게 그리기 동등성 테스트(6번 결과를 테스트로 고정).
- 계측 테스트: 추론 시간.
- 기존 A8 테스트 전부.

## Potential Merge Conflicts

- `DrawMissionScreen.kt`, `AiGuess.kt`, `AiGuessDialog.kt` 는 10월 20일에 찬희가 고쳤다(마스코트 연동). 시작 전 pull, 끝나면 바로 push.