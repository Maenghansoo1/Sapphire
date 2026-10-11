# Android 6단계 (1/3) 구현 보고

## 변경 파일과 내용

- `android/app/src/main/java/com/sapphire/hawe/feature/mission/MissionPerformCommon.kt`, `MissionPerformScreen.kt`: 글·사진 수행 화면의 공통 제목과 닫기 확인창, 진입 판단, 유형 분기.
- `WriteMissionScreen.kt`: 300 글자 제한, 글자 수, 빈 글 검사, 작성 내용 보존, 제출 중복 방지.
- `PhotoMissionScreen.kt`, `PhotoCamera.kt`, `PhotoProcessing.kt`: 카메라 권한, CameraX 미리보기·촬영·전후면·플래시, 앨범 선택, EXIF 방향 보정·다운샘플·3:4 중앙 크롭·JPEG 제출. 카메라가 없거나 권한이 거부되면 앨범을 사용할 수 있다.
- `app/MainNavigation.kt`, `feature/mission/MissionScreen.kt`: 05 → 수행 화면의 아래에서 올라오는 라우트, 제출 후 05 복귀와 토스트 전달, 진입 시 일회성 방어, 디버그 전용 유형 전환. 06 그림·09 음성은 기존 임시 동작을 유지한다.
- `AndroidManifest.xml`, `gradle/libs.versions.toml`, `app/build.gradle.kts`: CAMERA 권한과 선택적 카메라 기능 선언, CameraX 1.6.2의 core/camera2/lifecycle/view 네 모듈 추가. 3:4 해상도는 CameraX `setTargetAspectRatio(RATIO_4_3)`으로 요청했다.
- `designsystem/layout/HCtaScaffold.kt`: 깨진 프리뷰 문구 두 개 복구 및 UTF-8 BOM 제거.
- `android/app/src/test/java/com/sapphire/hawe/feature/mission/WriteMissionStateTest.kt`, `PhotoProcessingTest.kt`, `MissionPerformCommonTest.kt`: 글자 제한·공백 처리, 크롭·다운샘플 계산, 수행 화면 진입 판단 검증.

## 검증

- JDK 17에서 `./gradlew.bat assembleDebug testDebugUnitTest lintDebug installDebug`: 성공. 단위 테스트 40개 통과, 실패 0개(기존 34개 + 신규 6개).
- lint: 오류 0개, 경고 22개, 힌트 1개. 이전 20개 경고에 `android.media.ExifInterface` 사용 경고 2개가 추가됐다. 계획에서 지정한 Android 기본 API를 사용했고 별도 의존성은 추가하지 않았다.
- USB 기기 `R5CY54VQA5M`(SM-S937N, Android 16)에 설치하고 `adb shell am start -n com.sapphire.hawe/.MainActivity`로 실행 성공.
- `git diff --check -- android`: 공백 오류 없음. 최종 diff·상태를 확인했다. 기존 단계의 미커밋 Android 변경과 다른 세션이 수정한 `docs/FRONTEND/ANDROID.md`는 건드리지 않았다. 커밋·푸시 없음.

## 판단·가정 및 남은 위험

- 기준 iOS 커밋: `45a4996`. iOS 파일은 수정하지 않았다. 계획대로 Android에서 큰 사진 메모리를 위해 2048px 이내로 다운샘플하고, 전면 촬영 결과는 좌우 반전하지 않는다. 디버그 유형 전환은 실제 저장 유형을 바꾸지 않는다.
- 카메라 제공자 바인딩 및 촬영·앨범 선택의 실제 동작, 화면 전환·키보드·하단 여백은 자동 UI 테스트가 없어 폰에서 확인이 필요하다. 사진 Bitmap은 Activity 재생성 때 보존되지 않는다. 글 본문은 `rememberSaveable`로 보존한다. 사진 디코딩·크롭·JPEG 압축은 백그라운드에서 실행하고, 제출 시 `DataStore`의 파일 쓰기는 기존대로 메인 스레드에서 실행한다.
- 이번 단계에서 `app/MainNavigation.kt`와 `feature/mission/MissionScreen.kt`를 변경했으므로 다음 그림·음성 단계와 병합할 때 이 흐름을 함께 확인해야 한다. 이전 단계의 미커밋 작업을 보존했다.

## 폰 확인 체크리스트

1. 가입 후 `K7Q2PX` 참여 또는 그룹 생성 → 홈 카드 → 05. 오늘 유형이 글/사진이 아니면 디버그 빌드에서 “오늘은 ○○” 알약을 길게 눌러 글·사진으로 전환한다. 그림·음성의 기존 임시 제출도 확인한다.
2. 08 글: “글 쓰기”에서 화면이 아래에서 올라오는지, X·`0 / 300`·제목·안내 문구, 입력과 300자 제한(붙여넣기 포함), 공백만 입력 시 비활성, 글 작성 후 X/뒤로에서 “그만둘까요?”, 올리기 후 05 토스트·완료 수 +1·내 줄 완료·잠금 안내 제거·CTA 변경을 확인한다.
3. 07 사진: 최초 카메라 권한 허용 후 3:4 둥근 미리보기, 셔터 촬영, “다시 찍기/올리기”, 전후면 전환, 플래시 색 변경, 앨범의 세로·가로 사진 3:4 크롭, 올리기 후 05 복귀·토스트를 확인한다.
4. 권한 거부 시 안내와 앨범 사용, 설정에서 권한을 켠 뒤 복귀 시 카메라 동작을 확인한다.
5. 글·사진 작성 중 앱을 백그라운드로 보냈다 복귀해 카메라가 다시 동작하는지 확인한다.
6. 3버튼·제스처 내비게이션에서 하단 컨트롤이 가려지지 않는지, 08에서 키보드가 열렸을 때 “올리기” 위치를 확인한다.
7. 05 ↔ 수행 ↔ 05 뒤 카운트다운, 16 그룹 정보·나가기 후 홈 복귀를 확인한다.
8. 릴리스에서는 실행 확인이 불가하다. 코드에서 유형 전환 및 그림·음성 임시 제출이 `FLAG_DEBUGGABLE` 조건 안에 있음을 확인했다.

## 리뷰 반영 (Android 6단계 1/3)

1. **P1, 08 에디터 터치 영역 — 적용.** 기존 `BasicTextField`의 내용 영역 밖에서는 포커스가 되지 않을 수 있다는 지적이 코드상 타당하다. 360dp 바깥 `Box` 전체에 탭 처리와 `FocusRequester`를 추가하고 필드를 안쪽 전체 크기로 채웠다. 키보드를 명시적으로 연다. 자동 UI 테스트가 없어 박스 중앙·안쪽 여백 탭은 폰에서 확인이 필요하다.
2. **P2, 표시 중인 사진 Bitmap 재활용 — 적용.** `Image`가 참조 중인 사진을 상태 변경 직전에 `recycle()`하면 렌더 스레드와 경합할 수 있다. 화면 상태의 `recycle()` 호출을 제거하고 참조만 교체했다. 화면에 표시되지 않는 파이프라인 중간 비트맵의 재활용은 유지했다.
3. **P2, 사진 처리의 메인 스레드 실행 — 적용.** 디코딩·크롭·JPEG 압축이 UI를 멈출 수 있다. `Dispatchers.Default`에서 처리하고 처리 중 앨범·셔터·카메라 전환 및 제출 연타를 막았다. 촬영 임시 파일은 비동기 처리 완료·취소 후 삭제하고 화면이 이미 닫힌 뒤 촬영 콜백이 와도 즉시 삭제하도록 수명을 조정했다.

**리뷰 후 검증:** JDK 17에서 `./gradlew.bat assembleDebug testDebugUnitTest lintDebug installDebug` 성공. 마지막 임시 파일 정리 보완 후 실행한 동일 명령에서는 Gradle 데몬이 설치 단계에서 예기치 않게 종료됐지만, `./gradlew.bat --no-daemon assembleDebug testDebugUnitTest lintDebug` 재실행은 성공했다. 최종 APK는 `adb install -r`로 USB 기기에 설치하고 Activity 실행 성공. 테스트 40개 전부 통과, lint 오류 0·경고 22·힌트 1. `git diff --check -- android` 공백 오류 없음. 이번 리뷰에서는 `WriteMissionScreen.kt`, `PhotoMissionScreen.kt`, `PhotoCamera.kt`와 이 보고서만 수정했다. `.agent/review.md` 및 기존 미커밋 작업은 보존했다.
