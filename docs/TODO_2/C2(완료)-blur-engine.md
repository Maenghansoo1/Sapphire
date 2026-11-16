# C2. 얼굴 자동 블러 — 모자이크 엔진 (순수 로직)

> 범위: Android `feature/photo/`(신규) 의 **픽셀 처리 + 상태 로직 + 단위 테스트**. 얼굴 검출·UI 는 C3. 서버 변경 없음. 근거: `AI_FEATURES.md` §3.2.
> 빌드/테스트: JDK 21 — `android\gradlew.bat :app:testDebugUnitTest --offline`.

## Goal

"이 이미지의 이 영역들을 비가역적으로 모자이크해 새 이미지를 만든다"를 하는 엔진과, 사용자가 얼굴별로 켜고 끄는 **선택 상태 머신**을 만든다. 안드로이드 프레임워크 의존(Bitmap)은 얇은 어댑터에 가두고 핵심은 `IntArray`/`FloatArray` 로 테스트 가능하게 한다.

## Findings

- 가우시안 블러는 약하면 복원 가능성이 있으므로 **픽셀화(블록 평균)** 를 쓴다. 블록 크기는 얼굴 폭의 약 1/8, 최소 8px.
- 업로드 이미지는 **픽셀 자체가 변환된 비트맵**이어야 한다(오버레이만 얹은 이미지 금지). 서버는 블러 여부를 모르므로 메타데이터 필드를 추가하지 않는다.
- 사진 제출은 JPEG 바이트로 올라간다(`PhotoMissionScreen.kt` 의 `photoJpegBytes`). 큰 원본에서 비트맵 2개를 동시에 들면 메모리 위험 → 긴 변 기준 다운스케일(예: 2048px) 후 처리하는 기존 파이프라인 위치를 C1 결과로 확인.

## Key Implementation Points

1. 신규 `feature/photo/Mosaic.kt`:
   - `data class FaceRegion(val id: Int, val rect: IntRect(left, top, right, bottom), val source: Source(AUTO|MANUAL), var blurred: Boolean)` — 좌표는 원본 비트맵 픽셀 기준, 이미지 경계로 클램프.
   - `fun pixelate(pixels: IntArray, width: Int, height: Int, rect: IntRect, blockSize: Int)` — 사각형 안을 블록 평균색으로 채움(알파 유지, 경계 블록은 잔여 크기 처리). 박스 밖 픽셀은 변경하지 않는다.
   - `fun blockSizeFor(rect: IntRect): Int` — `max(8, rect.width / 8)`.
   - 여유 영역(패딩) 확장 함수: 검출 박스는 얼굴을 빡빡하게 감싸므로 기본 15% 확장 후 클램프(`expand(rect, ratio, bounds)`).
2. `class FaceBlurState`: 얼굴 목록, `toggle(id)`, `addManual(rect)`, `removeManual(id)`, `applyDefaultMode(mode: BlurMode)`(`ALL`/`NONE`/`OFF`), `selected(): List<FaceRegion>`. 수동 영역도 같은 방식으로 토글/삭제.
3. `fun applyBlur(bitmap: Bitmap, regions: List<FaceRegion>): Bitmap` — Bitmap 어댑터(내부는 `getPixels` → `pixelate` → `setPixels`), 입력을 변경하지 않고 새 비트맵을 반환(원본 보존, 취소 시 되돌리기 위해).
4. 겹치는 영역/이미지 밖으로 나간 영역 처리, 빈 선택이면 **원본 그대로 반환**(재인코딩 비용 회피는 호출자 판단).

## Files / Functions to Change

| 파일 | 변경 |
|---|---|
| `android/app/src/main/java/com/sapphire/hawe/feature/photo/Mosaic.kt` (신규) | 픽셀화·블록 크기·패딩 |
| `feature/photo/FaceBlurState.kt` (신규) | 선택 상태 머신 |
| `android/app/src/test/java/com/sapphire/hawe/feature/photo/MosaicTest.kt`, `FaceBlurStateTest.kt` (신규) | 테스트 |

기존 파일 수정 없음.

## Constraints

- `android/` 만. 새 의존성 금지. 얼굴 검출 SDK 호출은 이 단위에서 하지 않는다.
- 비가역 변환(역연산 불가능한 평균 블록)만 허용. 블러 강도를 사용자가 임의로 약하게 만들 수 없게 최소 블록 크기를 강제.
- 원본 비트맵/픽셀을 파일·로그에 남기지 않는다.
- 커밋/푸시 금지.

## Edge Cases

- 박스가 이미지 밖/음수 크기 → 클램프 후 크기 0이면 무시.
- 아주 작은 얼굴(폭 < 16px) → 블록 최소 8px 이 박스보다 큼: 박스 전체 평균색 단일 블록으로 처리.
- 박스 겹침 → 두 번 처리해도 결과는 평균색에 수렴(멱등 아님 허용), 단 크래시 없음.
- 알파 채널 있는 PNG 입력 → 알파 유지.
- 큰 이미지 → 호출자가 다운스케일 후 전달(이 엔진은 크기 제한 없음, 메모리는 호출자 책임) — 보고서에 권장 상한 기록.
- `BlurMode.OFF` → 선택 불가(빈 목록).

## Tests Required

- `pixelate`: 박스 안 픽셀 분산 감소, 박스 밖 픽셀 완전 불변, 경계 블록(박스 크기가 블록의 배수가 아닐 때), 알파 유지.
- `blockSizeFor`/`expand`: 경계(작은 박스 8px 하한, 이미지 밖 클램프).
- `FaceBlurState`: 토글, 모드 적용(ALL/NONE/OFF), 수동 추가/삭제, 선택 목록.
- `applyBlur` 는 JVM 에서 Bitmap 을 못 쓰면 `pixelate` 레벨에서 검증하고 어댑터는 얇게 유지(Robolectric 미사용이 기본).

## Validation Steps

`android\gradlew.bat :app:testDebugUnitTest --offline`(JDK 21) 통과 기록. C3 가 호출할 공개 API 시그니처를 보고서에 적는다.

## Potential Merge Conflicts

신규 파일이라 낮음.
