# C1. 얼굴 자동 블러 — 사전 확인: EXIF 제거 여부와 사진-주제 적합도(B10) 영향 측정

> 범위: **확인·측정 위주**(코드 변경은 필요 시 EXIF 제거 한정). 선행 없음. C3 의 설계 입력이 된다. 근거: `docs/FEATURE/AI_FEATURES.md` §3.4·§3.5.

## Goal

1. 사진 제출 경로(Android, iOS)가 **EXIF(위치 등 메타데이터)를 제거한 채 업로드하는지** 확인한다. 아니면 제거한다.
2. 얼굴 모자이크가 사진-주제 적합도(B10, CLIP) 점수 분포를 얼마나 이동시키는지 측정해 **임계값 재평가가 필요한지** 판단한다.

## Findings

- Android 사진 제출: `android/app/src/main/java/com/sapphire/hawe/feature/mission/PhotoMissionScreen.kt` 가 `onSubmit(withContext(Dispatchers.Default) { photoJpegBytes(current) })` 로 JPEG 바이트를 만든다. `photoJpegBytes` 의 정의 위치와 EXIF 처리는 이 단위에서 **확인**한다(Bitmap 으로 디코드 후 `compress(JPEG)` 로 재인코딩하면 일반적으로 EXIF 가 사라지지만, 파일 바이트를 그대로 올리는 경로가 있으면 남는다 — 갤러리 선택 경로 `PickVisualMedia` 와 카메라 경로를 **각각** 확인).
- 프로필 사진 쪽 `feature/mypage/AvatarPhoto.kt` 는 EXIF 방향만 읽고 JPEG 로 재인코딩한다(참고 패턴).
- B10: `ml/vision-service/` (Python, CLIP), 임계값 `hawe.vision.photo-now.perfect-threshold=0.024` / `good=0.017`, `photo-color.perfect=0.132` / `good=0.104`. 평가는 2026-10-13, 177장 기준(`ml/vision-service/EVAL.md`). 평가 방법/스크립트는 그 폴더와 `docs/BACKEND/VISION_TOPIC_FIT.md` 참고.
- 서버에서 EXIF 를 제거하는지는 별도 확인 대상(`FileStorage` 저장 경로): 클라이언트가 못 믿을 값이라는 점에서 서버 측 제거가 더 안전하다.

## Key Implementation Points

1. **EXIF 점검 표** 작성: (플랫폼 × 카메라/갤러리) 4~6 경로마다 업로드 직전 바이트에 GPS/기기 정보 태그가 남는지 실제 파일로 확인(예: `ExifInterface` 로 읽는 임시 테스트 또는 업로드된 파일을 외부 도구로 확인). 남는 경로가 있으면 Bitmap 재인코딩(EXIF 방향 보정 포함, `AvatarPhoto.kt` 패턴 참고) 또는 서버 저장 시 제거를 제안·구현.
2. **서버 측 확인**: 서버가 저장 시 메타데이터를 제거하는지(`FileStorage` 구현 확인). 없으면 별도 후속 과제로 기록(이 단위에서 서버 구현은 하지 않고 판단만, 필요 시 사용자 승인 후).
3. **적합도 영향 측정**: 얼굴이 포함된 사진 30~50장(동의된 것)을 모으고, 각 사진에 대해 (a) 원본 (b) 모자이크 적용(C2 엔진 또는 임시 스크립트로 얼굴 영역 픽셀화) 의 `topic-fit` 점수를 비전 서비스로 계산. 틀은 `PHOTO_NOW` 위주(+`PHOTO_COLOR` 일부). 측정치: 평균 점수 변화, 임계(`good`/`perfect`) 이상 비율 변화(배지가 뜨는 비율이 줄어드는지).
4. **판단 기준(제안)**: perfect 비율 감소가 5%p 이상이면 임계 재평가 또는 "블러 사진은 배지 판정에서 불이익이 없도록" 대책(예: 얼굴 영역 제외 비교) 검토를 후속 작업으로 기록. 서버는 블러 여부를 모르므로(AI_FEATURES.md §3.3) 서버 측 보정은 불가 → 임계 조정이 현실적.
5. 결과를 `docs/FEATURE/AI_FEATURES.md` §3.4·§3.5 에 반영하고 C3 에 넘길 결정 사항을 정리한다.

## Files / Functions to Change

원칙적으로 문서만. EXIF 가 남는 경로가 발견되면 `PhotoMissionScreen.kt`/`photoJpegBytes` (및 iOS 대응 파일) 최소 수정. 측정용 스크립트는 `ml/vision-service/` 아래 비커밋 임시 파일 또는 `tools/` 로(커밋 여부는 팀 결정).

## Constraints

- 측정에 쓰는 사진은 저장소에 커밋하지 않는다(`ml/eval-data/` 비커밋 규칙). 얼굴 사진을 외부 서비스에 올리지 않는다(로컬 비전 서비스만).
- 비전 임계값을 이 단위에서 직접 바꾸지 않는다(측정·제안까지).
- 커밋/푸시 금지.

## Edge Cases

- 카메라 경로는 Bitmap 이라 EXIF 가 없고 갤러리 경로는 원본 파일이라 남는 경우 — 경로별 분리 확인.
- 회전 정보가 EXIF 에만 있던 사진을 재인코딩하면 방향이 틀어짐 → 방향 보정 후 인코딩.
- 모자이크 영역이 큰 사진은 점수 변화가 큼 → 영역 비율별(작은 얼굴/큰 얼굴) 구분해 기록.

## Tests Required / Validation Steps

- EXIF 수정이 있으면 단위 테스트(태그 없음 확인).
- 보고서: 경로별 EXIF 표, 서버 처리 여부, 적합도 영향 표(평균·비율), 후속 권고.

## Potential Merge Conflicts

`PhotoMissionScreen.kt`(C3 와 같은 파일) — EXIF 수정이 있으면 먼저 푸시하고 C3 가 이어서 한다.
