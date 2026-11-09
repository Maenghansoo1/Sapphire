# C4. 얼굴 자동 블러 — iOS 이식 (Vision)

> 범위: `ios/HAWE/`. **Mac 필요.** 선행: C3(Android 에서 동작·UX 확정). 서버 변경 없음. 이식 방식은 `docs/해야될거/3번_경환/A5-ios-port.md` 를 따른다. 근거: `AI_FEATURES.md` §3.

## Goal

C2(엔진)·C3(검출+편집 UI)와 같은 동작을 iOS 사진 미션에 구현한다. 검출은 Vision, 외부 전송 없음.

## Findings

- 얼굴 검출: Vision `VNDetectFaceRectanglesRequest`(또는 필요 시 `VNDetectFaceLandmarksRequest`). Android(ML Kit)와 검출 결과가 다르므로 플랫폼별 수동 검수가 필요하다.
- 좌표계: Vision 은 **정규화 좌표 + 좌하단 원점**이다. 이미지 픽셀 좌표(좌상단 원점)로 변환하는 오류가 가장 흔한 버그.
- 이미지 방향: `UIImage.imageOrientation` 반영(Vision 요청에 orientation 전달).
- 기본 모드/문구/동작은 C3 와 동일(`PHOTO_GALLERY` 기본 "모두 블러").

## Key Implementation Points

1. 모자이크 엔진 포팅(블록 평균, 최소 8px, 패딩 15%) + 선택 상태(`FaceBlurState`) — 같은 테스트 케이스를 XCTest 로 재현.
2. Vision 검출 래퍼(좌표 변환 순수 함수 분리 → 테스트).
3. 편집 UI(SwiftUI): 박스 오버레이, 탭 토글, 영역 추가 드래그, 안내 문구.
4. 제출: 픽셀 변환된 이미지를 JPEG 로 인코딩해 기존 업로드 경로에 전달. EXIF 처리는 C1 결론 적용.
5. 설정(`BlurMode`) 저장(기존 설정 저장 방식 확인).

## Files / Functions to Change

`ios/HAWE/` 하위 사진 미션 화면, 신규 `Photo/` 모듈, 설정, `ios/HAWETests/`, 에셋 카탈로그(임시 아이콘 `임시` 표기).

## Constraints

외부 검출 API 금지, 원본 이미지·검출 결과 저장·로그 금지, 오버레이 합성 이미지 업로드 금지, 새 외부 SDK 금지, 커밋/푸시 금지.

## Edge Cases / Tests / Validation

- 좌표 변환(정규화·좌하단 → 픽셀·좌상단, 회전별) 단위 테스트.
- C3 와 같은 수동 검수(얼굴 1/3/10명, 측면, 마스크, 어두운 사진) 결과 기록, 업로드된 이미지의 비가역성 확인.
- Mac 이 없으면 "미검증"으로 보고.

## Potential Merge Conflicts

iOS 사진 미션 화면, 설정 화면(다른 iOS 작업과 순차).
