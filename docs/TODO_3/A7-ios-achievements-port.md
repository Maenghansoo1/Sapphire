# A7. 업적 — iOS 업적 화면 이식

> 범위: `ios/HAWE/`. **Mac 필요.** 선행: A6(Android 동작·UX 확정), A4(API). 이식 방식은 `docs/해야될거/3번_경환/A5-ios-port.md` 를 따른다.

## Goal

A6 과 같은 업적 화면(그리드·진행도·상세·상태 문구·탐색기 링크)과 `badge` 알림 표시를 iOS 에 구현한다.

## Findings

- API 계약은 A4 와 같다. 상태 정규화값 `RECORDING | ON_CHAIN | FAILED`, `explorerUrl` 은 `ON_CHAIN` 일 때만.
- iOS 는 SwiftUI + 기존 네트워크/저장소 패턴(경로는 구현 시 확인), 마이페이지/알림 화면이 있다. 알 수 없는 알림 타입을 받아도 디코딩 실패로 목록 전체가 깨지지 않게 해야 한다(`Codable` 의 알 수 없는 enum 값 처리).
- 문구/정렬/진행도 클램프 규칙은 Android 와 동일하게 맞춘다.

## Key Implementation Points

1. 모델(`Codable`), 저장소 메서드, 뷰모델.
2. 업적 화면 + 상세 시트, 상태 문구(과장 금지), "블록체인에서 확인" 은 `SFSafariViewController`/`openURL`.
3. 마이페이지 진입 항목, 알림 `badge` 타입 표시.
4. 임시 에셋은 에셋 카탈로그 한 곳에 모아 `임시` 표기.

## Files / Functions to Change

`ios/HAWE/` 하위 신규 `Achievements/`, 네트워크/저장소, 마이페이지·알림 화면, `ios/HAWETests/`, 에셋 카탈로그.

## Constraints

지갑/전송/구매 UI 금지, 주소·키 저장/표시 금지, 새 외부 SDK 금지, 커밋/푸시 금지.

## Edge Cases / Tests / Validation

- 상태→문구 매핑, 정렬, 진행도 클램프, 알 수 없는 알림 타입 디코딩을 XCTest 로 Android 와 같은 케이스 재현.
- 실기기에서 업적 목록·상세·외부 링크 확인. Mac 이 없으면 "미검증"으로 보고.

## Potential Merge Conflicts

iOS 마이페이지/알림 화면, 내비게이션(다른 iOS 이식 작업과 순차).
