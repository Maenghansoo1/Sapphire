# C3. 얼굴 자동 블러 — Android 얼굴 검출 + 편집 UI

> 범위: Android 앱. **선행: C1(EXIF·영향 확인), C2(엔진).** 서버 변경 없음. 근거: `AI_FEATURES.md` §3, §6 결정 3. 빌드/테스트: JDK 21 — `android\gradlew.bat :app:testDebugUnitTest --offline`.

## Goal

사진 제출 직전 단계에서 **기기 안에서 얼굴을 자동 검출**해 박스를 보여 주고, 사용자가 얼굴별로 모자이크를 켜고 끄며, 놓친 얼굴은 직접 영역을 지정할 수 있게 한다. 제출되는 이미지는 모자이크가 **픽셀에 적용된** JPEG 이다.

## Findings

- 사진 화면: `android/app/src/main/java/com/sapphire/hawe/feature/mission/PhotoMissionScreen.kt` — `PhotoMissionScreen(topic, template, onClose, onSubmit: suspend (ByteArray) -> Boolean)`. 카메라(`PhotoCamera.kt`) 또는 갤러리(`PickVisualMedia`, `template == PHOTO_GALLERY`)로 사진을 얻고 `photoJpegBytes(current)` 로 JPEG 를 만든다.
- 대상 틀: `PHOTO_NOW`, `PHOTO_GALLERY`, `PHOTO_COLOR`. 사진 틀의 서버 후처리(B10 적합도 AI)는 블러 여부를 모른다.
- C2 가 엔진/상태 API 제공: `FaceRegion`, `FaceBlurState`, `applyBlur`, `blockSizeFor`.
- 의존성: `android/app/build.gradle.kts`, `android/gradle/libs.versions.toml`(버전 카탈로그). ML Kit Face Detection 은 **번들 모델 버전**(`com.google.mlkit:face-detection`, 온디바이스; 구버전 Play 서비스 모델 방식이 아닌 번들 방식)을 쓰는 것이 오프라인·즉시 동작에 안전 — 정확한 아티팩트·버전은 구현 시 확인·고정. 외부 서버로 이미지를 보내지 않는다.
- 결정 3 기본값(AI_FEATURES.md §6): `PHOTO_GALLERY` 만 기본 "모두 블러", 나머지는 기본 "선택 안 함(탭해서 블러)". 댓글 첨부·프로필 사진은 1차 제외.
- C1 결과(EXIF 제거 방식, 적합도 영향)를 먼저 읽고 반영한다.

## Key Implementation Points

1. **검출기** `FaceDetectorClient`(인터페이스) + ML Kit 구현: `suspend fun detect(bitmap: Bitmap): List<IntRect>`. 정확도 우선 옵션(`PERFORMANCE_MODE_ACCURATE`), 최소 얼굴 크기 설정(작은 얼굴은 놓치기 쉬움 — 값은 측정으로 조정), 이미지 회전 정보 반영. 실패/모델 로드 실패는 예외 대신 빈 목록 + 수동 모드 안내(기능 전체가 죽지 않게).
2. **제출 전 단계 화면** `FaceBlurEditor`: 촬영/선택 직후 미리보기 → "얼굴 가리기" 단계(사용자가 건너뛸 수 있음, 설정으로 끄기 가능). 검출 박스 오버레이, 탭 → 해당 얼굴 토글, "영역 추가" 모드(드래그 사각형), 수동 영역 삭제, 모자이크 미리보기(처리된 비트맵을 보여 줌). 안내 문구 **"놓친 얼굴이 없는지 확인해 주세요"** 필수, "자동이 완벽히 찾아 준다"는 취지의 문구 금지.
3. **기본 모드**: 설정(DataStore, 기존 설정 저장 방식 확인)에서 `BlurMode`(모두/선택 안 함/사용 안 함) 선택 가능. 기본값은 위 결정 3.
4. **제출 파이프라인**: 편집 결과 비트맵 → JPEG 인코딩(품질 기존과 동일) → 기존 `onSubmit(bytes)`. 선택이 없으면 원본 경로 그대로(재인코딩 없음). 메모리: 긴 변 2048px 다운스케일 후 검출·처리(C2 권장 상한).
5. **취소/되돌리기**: 편집 중 원본 비트맵을 유지하고 적용은 새 비트맵 생성(C2 `applyBlur`) — 취소하면 원본으로.
6. 검출·처리는 `Dispatchers.Default`. 큰 사진에서 로딩 인디케이터.
7. 임시 에셋(얼굴 박스/모자이크 아이콘)은 `HIcon` 에 모으고 `임시` 주석.

## Files / Functions to Change

| 파일 | 변경 |
|---|---|
| `android/app/build.gradle.kts`, `android/gradle/libs.versions.toml` | ML Kit face-detection 의존성(버전 고정) |
| `android/app/src/main/java/com/sapphire/hawe/feature/photo/FaceDetectorClient.kt`, `FaceBlurEditor.kt` (신규) | 검출 + 편집 UI |
| `feature/mission/PhotoMissionScreen.kt` | 제출 전 편집 단계 삽입, 편집 결과 바이트로 `onSubmit` |
| 설정 저장소(기존 DataStore 위치 확인) | `BlurMode` 설정 |
| `designsystem/assets/HIcon.kt` | 임시 아이콘 |
| 테스트 | 아래 |

## Constraints

- **이미지를 외부로 보내는 검출 API 금지**(온디바이스 모델만). 서버 API·필드 변경 금지(블러 여부 전송 금지).
- 원본 비트맵·검출 결과를 파일/로그/분석에 남기지 않는다.
- 업로드되는 이미지는 모자이크가 픽셀에 반영된 것이어야 한다(오버레이 합성 이미지 금지).
- 의존성 추가는 ML Kit face-detection 하나만. APK 크기 증가량을 보고한다.
- 커밋/푸시 금지.

## Edge Cases

- 얼굴 미검출(측면, 마스크, 작은 얼굴, 그림 속 얼굴) → 수동 영역 지정 안내, "얼굴이 없어요" 로 건너뛰기.
- 얼굴이 매우 많은 단체 사진(10명+) → 검출 시간/박스 겹침/탭 정확도(작은 박스 탭 영역 확대).
- 가로/세로·EXIF 회전 사진 → 좌표 변환 오류 주의(검출은 회전 반영된 비트맵 기준으로).
- 갤러리 사진이 매우 큼(수천 px) → 다운스케일 후 처리, OOM 방어.
- 설정이 "사용 안 함" → 편집 단계 자체를 건너뜀.
- 모델 초기화 지연 → 로딩 표시, 검출 전에 사용자가 제출을 눌러도 안전(원본 제출 경로).
- 접근성: 박스와 토글 상태 `contentDescription`.

## Tests Required

- 좌표 변환 순수 함수(화면 터치 좌표 ↔ 이미지 좌표, 회전 반영), 박스 히트 테스트(겹침 시 가장 작은 박스 우선).
- `FaceBlurState` 기본 모드별 초기 상태(C2 테스트 재사용 + 틀별 기본값 매핑: `PHOTO_GALLERY → ALL`, 나머지 `NONE`).
- 검출기는 인터페이스로 분리해 가짜 구현으로 편집기 로직(빈 검출 → 수동 모드) 테스트.

## Validation Steps

`android\gradlew.bat :app:testDebugUnitTest --offline` 통과. 수동(실기기, 결과를 보고서에 기록): 얼굴 1/3/10명, 측면, 마스크, 어두운 사진 각 5장 검출률, 모자이크 적용 후 서버 업로드 이미지를 내려받아 얼굴이 복원 불가한지 확인, APK 크기 증가량, 갤러리/카메라 두 경로. 못 했으면 "미검증".

## Potential Merge Conflicts

`PhotoMissionScreen.kt`(C1 의 EXIF 수정과 겹침 — C1 먼저), `build.gradle.kts`/`libs.versions.toml`, `HIcon.kt`.
