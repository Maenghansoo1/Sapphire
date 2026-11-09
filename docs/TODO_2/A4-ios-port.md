# A4. 실시간 AI 판단 — iOS 이식 (Core ML)

> 범위: `ios/HAWE/`. **Mac 필요.** 선행: A2(Android 에서 동작·값 확정은 A3 이후가 이상적). 서버 변경 없음. iOS 이식 방식은 기존 A5(iOS 일괄 이식) 계획(`docs/해야될거/3번_경환/A5-ios-port.md`)을 따른다.

## Goal

A1 의 상태 로직과 A2 의 UI 동작을 iOS 그림 미션 화면에 동일하게 구현한다. 모델은 `ml/quickdraw/export/QuickDrawCNN.mlpackage` (M1 산출물) 를 쓴다.

## Findings

- iOS 그림 화면은 PencilKit 기반(`DrawMissionView.swift`, `PencilCanvas`). "AI가 맞혀볼게요" 온디바이스 추론은 A5 항목 9 로 이식돼 있어야 한다(확인 필요 — 없으면 먼저 그 이식이 선행).
- 전처리 명세(`ml/quickdraw/PREPROCESS_SPEC.md`)와 골든 벡터(`ml/quickdraw/golden/`)가 학습-앱 일치를 보장한다.
- Android 값(A1 기본값, A3 확정값)을 그대로 쓴다: EMA, 히스테리시스 margin, `correctThreshold`, `requiredStreak`, 500ms 게이트.

## Key Implementation Points

1. A1 의 `LiveGuessTracker`/`InferenceGate` 를 Swift 로 포팅(순수 타입, XCTest 로 같은 테스트 케이스 재현).
2. PencilKit 획 종료 이벤트(`canvasViewDrawingDidChange` 등) + 디바운스로 추론 호출, 백그라운드 큐에서 Core ML 실행.
3. 말풍선 UI 는 Android 와 같은 상태/문구(임시 에셋은 에셋 카탈로그 한 곳에 모아 `임시` 표기).
4. 제출 시 기존 AI 추측 업로드 흐름 유지.

## Files / Functions to Change

`ios/HAWE/` 하위 그림 미션 화면, AI 추측 관련 파일(경로는 구현 시 확인), `ios/HAWETests/`.

## Constraints

- 그림·추론 결과를 저장·전송하지 않는다. 새 외부 SDK 금지. Android 와 값/문구가 어긋나지 않게 한다.
- 커밋/푸시 금지.

## Edge Cases / Tests / Validation

- Android A1 의 테스트 케이스를 XCTest 로 동일하게 포팅(EMA, 히스테리시스, 연속 판정, 게이트).
- 실기기에서 Core ML 지연(≤30ms 목표)·프레임 확인. Mac 이 없으면 "미검증"으로 보고.
- TFLite vs Core ML 같은 골든 입력의 top-1 일치(M1 기준 ≥99%) 재확인.

## Potential Merge Conflicts

iOS 그림 화면 파일(다른 iOS 이식 작업과 순차).
