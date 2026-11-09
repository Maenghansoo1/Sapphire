# A3. 실시간 AI 판단 — 임계 보정과 수동 평가

> 범위: **측정과 값 조정**. 코드 변경은 상수(임계·연속 횟수)와 문서 정도. **선행: A2**(실기기에서 동작). 근거: `AI_FEATURES.md` §1.4, §6 결정 1.

## Goal

실시간 맞힘 임계(`correctThreshold`, 초안 0.6)와 연속 횟수(`requiredStreak`, 초안 2)를 **실측으로 확정**하고, 주제→범주 매핑 실패율을 측정해 범주 확장(재학습)이 필요한지 판단한다.

## Findings

- 모델 카드의 신뢰도 임계(기본 0.35)는 "버튼을 눌러 한 번 보는" 용도다. 실시간은 그리는 도중 불완전한 그림에서도 호출되므로 오탐 위험이 달라 별도 값이 필요하다.
- 범주는 ~50개(`ml/quickdraw/categories.json`). 미션 주제는 AI 생성(B8)이라 자유 문장이어서 범주에 매핑되지 않는 비율이 높을 수 있다(`DrawSubjectMatcher`).
- 기록 위치 제안: `ml/quickdraw/REPORT.md` 에 "실시간 보정" 절 추가 또는 `docs/FEATURE/AI_FEATURES.md` §1.4 갱신.

## Key Implementation Points

1. **측정 절차**: 범주 10개 이상(고양이, 집, 구름, 해, 나무 등) × 사람 3명 이상이 각 범주를 그려 본다. 각 시도마다 기록: (a) 처음 `Correct` 가 뜬 시각/획 수, (b) 그 시점 그림이 알아볼 만했는지(사람 판단 O/X), (c) 틀린 범주를 `Correct` 로 맞힌 오탐 여부.
2. **임계 후보 스윕**: 0.5 / 0.6 / 0.7, 연속 2 / 3. 후보마다 맞힘 성공률, 오탐률, 평균 획 수를 표로 만든다(엑셀/CSV 수작업 가능). 오탐률 5% 이하에서 가장 빨리 맞히는 조합을 채택.
3. **매핑 실패율**: 최근 AI 생성 그림 미션 문장 N(≥50)개를 `DrawSubjectMatcher` 에 넣어 범주에 매핑되는 비율을 계산(간단한 단위 테스트/스크립트). 매핑률이 낮으면(예: < 40%) 범주 확장 또는 별칭 추가를 제안으로 기록.
4. 결과를 반영해 `LiveGuessConfig` 기본값(A1)을 수정하고 근거를 문서에 남긴다.

## Files / Functions to Change

| 파일 | 변경 |
|---|---|
| `feature/draw/LiveGuess.kt` | 기본 상수만 수정 |
| `ml/quickdraw/REPORT.md` 또는 `docs/FEATURE/AI_FEATURES.md` | 측정 표·결론 추가 |
| (선택) `feature/draw/DrawSubjectMatcher` 의 별칭 데이터(`categories.json`) | 매핑률이 낮을 때 별칭 보강 제안 — 변경은 별도 합의 |

## Constraints

- 평가용 그림 파일은 **저장소에 커밋하지 않는다**(팀원 동의 하 수집, `ml/eval-data/` 비커밋 규칙 유지).
- 모델 재학습·범주 변경은 이 단위의 범위가 아니다(결정 후 M1 계열 별도 계획).
- 커밋/푸시 금지.

## Edge Cases

- 표본이 작아 결론이 흔들림 → 보고서에 표본 수와 "경향" 임을 명시.
- 기기 성능에 따라 획 간 간격이 다름 → 최소 2종 기기에서 확인(가능하면).
- 유사 범주 혼동(고양이/개, 해/별) → 혼동 쌍을 기록해 임계 외에 범주 교체 필요성도 메모.

## Tests Required

- 상수 변경으로 깨지는 A1 테스트가 있으면 갱신(경계 테스트가 상수에 의존하지 않도록 config 주입 사용).

## Validation Steps

측정표와 채택값, 매핑 실패율, 범주 확장 필요 여부 결론이 문서에 있고 `android\gradlew.bat :app:testDebugUnitTest --offline` 통과.

## Potential Merge Conflicts

낮음(상수·문서).
