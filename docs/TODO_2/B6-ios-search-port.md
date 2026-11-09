# B6. AI 미션 검색 — iOS 검색 화면 이식

> 범위: `ios/HAWE/`. **Mac 필요.** 선행: B3(Android 동작 확정). 서버 변경 없음. 이식 방식은 `docs/해야될거/3번_경환/A5-ios-port.md` 를 따른다.

## Goal

B3 와 같은 검색 화면·이유 문구·내비게이션을 iOS 에 구현한다.

## Findings

- API 계약은 B1/B2 와 동일(`GET /api/search/missions`). 응답 모델/문구 규칙은 B3 참고.
- iOS 는 SwiftUI 화면 + 저장소/네트워크 계층 패턴을 기존 코드에서 확인해 맞춘다(경로는 구현 시 확인).

## Key Implementation Points

1. 응답 DTO(Codable), 저장소 메서드, 뷰모델.
2. 검색 화면: 디바운스, 그룹 칩 필터, 결과 리스트, 빈/오류/로딩 상태. 이유 문구 조립 함수는 Android 와 같은 규칙(XCTest 로 같은 케이스).
3. 항목 선택 시 기존 미션 상세/결과 진입 경로 재사용(423 처리 포함).
4. 임시 에셋은 에셋 카탈로그 한 곳에 모아 `임시` 표기.

## Files / Functions to Change

`ios/HAWE/` 하위 신규 `Search/` 화면, 네트워크/저장소, 내비게이션 진입점, `ios/HAWETests/`.

## Constraints

점수/순위 노출 금지, 검색어 로컬 저장 금지(1차), 새 SDK 금지, 커밋/푸시 금지.

## Edge Cases / Tests / Validation

B3 와 동일한 케이스(0건, 오프라인, 긴 문장, 그룹 1개). XCTest 로 문구 조립·상태 전이. Mac 이 없으면 "미검증"으로 보고.

## Potential Merge Conflicts

iOS 내비게이션/홈 화면(다른 이식 작업과 순차).
