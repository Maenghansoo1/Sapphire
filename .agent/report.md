# Android 6단계 (2/3) 구현 보고 — 06 그림 + 09 음성

## 변경 파일과 내용

- `android/app/src/main/java/com/sapphire/hawe/feature/mission/DrawingModel.kt`, `DrawingRenderer.kt`, `DrawMissionScreen.kt`: 펜·픽셀 지우개, 굵기 3단계, 색 7개, 획별 되돌리기/다시 실행, 탭 점 및 드래그 입력, 흰 바탕 PNG 제출. 화면과 PNG는 동일한 획 렌더러를 사용한다.
- `VoiceLogic.kt`, `VoiceRecorderController.kt`, `VoiceMissionScreen.kt`: 32개 막대 파형·시간·컨트롤 규칙, 마이크 권한 및 설정 안내, 30초 AAC/m4a 녹음, 100ms 레벨 갱신, 다시 녹음·미리 듣기·제출, 백그라운드 정지와 화면 종료 시 임시 파일 정리.
- `MissionPerformScreen.kt`, `MissionPerformCommon.kt`, `MissionScreen.kt`: 06·09 수행 분기 연결, 네 유형 전부 수행 화면 진입, 05의 그림/음성 디버그 임시 제출 삭제. 디버그 전용 유형 전환은 폰 테스트를 위해 유지했다. 기존 수행 라우트와 제출 후 복귀·토스트 흐름은 유지했다.
- `android/app/src/main/AndroidManifest.xml`: `RECORD_AUDIO` 권한과 선택적 마이크 하드웨어 선언.
- `android/app/src/test/java/com/sapphire/hawe/feature/mission/DrawingModelTest.kt`, `VoiceLogicTest.kt`, `MissionPerformCommonTest.kt`: 그림 상태·히스토리·팔레트, 음성 레벨·파형·컨트롤, 네 유형 진입 검증.

## 검증

- JDK 17에서 `./gradlew.bat --no-daemon assembleDebug testDebugUnitTest lintDebug installDebug`: 성공. 단위 테스트 46개 통과, 실패 0개(기존 40개 + 신규 6개).
- lint: 오류 0개, 경고 24개, 힌트 1개. 기존 대비 경고 2개는 PNG 렌더러의 플랫폼 `Bitmap.createBitmap`에 대한 KTX 권고다. 새 의존성은 추가하지 않았다.
- USB 기기 `R5CY54VQA5M`(SM-S937N, Android 16)에 설치하고 `adb shell am start -n com.sapphire.hawe/.MainActivity`로 실행했다. 프로세스 실행도 확인했다.
- `git diff --check -- android`: 공백 오류 없음. 최종 diff·상태를 확인했다. `.agent/plan.md`의 선행 변경은 보존했고 이 작업에서는 Android 코드·테스트와 이 보고서만 수정했다. 커밋·푸시 없음.

## 판단·가정 및 남은 위험

- 기준 iOS 커밋: `45a4996`. iOS는 읽기만 했고 수정하지 않았다. 그림의 `isEmpty`는 **펜 획 기록이 있는지**로 판단한다. 픽셀 지우개로 화면을 모두 지워도 기록이 남으면 제출할 수 있다는 점이 PencilKit과 다르다. 지우개는 투명 오프스크린 레이어에서 `CLEAR`로 픽셀을 지우고 PNG 저장 시 흰 배경과 합성한다.
- 음성 레벨은 Android `MediaRecorder.maxAmplitude`를 dB로 변환해 iOS의 -50~0dB 범위에 대응한다. Android 마이크 제한을 고려해 앱이 `ON_STOP`에 들어가면 녹음·재생을 멈춘다. `DataStore.submit`이 임시 m4a를 복사한 뒤 화면 종료에서 원본을 삭제한다.
- 작은 화면의 캔버스는 남은 세로 공간에 맞춰 최대 400dp까지 사용하고, 공간이 모자라면 높이를 줄인다. 구성 변경으로 Activity가 재생성되면 작성 중 그림·음성이 보존되지 않는다. Canvas 제스처, MediaRecorder/MediaPlayer, 권한 UI와 레이아웃은 자동 UI 테스트가 없어 폰에서 확인이 필요하다.
- 다음 결과 공개 단계에서 `MissionScreen.kt`를 다시 수정하므로 05 CTA와 제출 후 복귀 흐름을 함께 확인해야 한다.

## 폰 확인 체크리스트

1. 가입 → 03에서 `K7Q2PX` 참여(또는 02로 그룹 생성) → 홈 카드 → 05. 디버그 빌드에서 “오늘은 ○○” 알약을 길게 눌러 그림 → 06, 음성 → 09를 번갈아 연다. 글/사진은 이전 단계 흐름을 확인한다.
2. **06 그림:** 처음에는 “올리기” 비활성·되돌리기/다시 실행 회색. 손가락으로 그리면 선이 따라오고, 탭 한 번으로 점이 찍힌다. 펜/지우개, 굵기 3단계, 색 7개와 선택 테두리, 지우개 중 색 표시 제거·색 선택 시 펜 복귀, 선 일부만 지우기, 되돌리기/다시 실행, 그림이 있을 때 X/뒤로 → “그만둘까요?”, 올리기 후 05 토스트·완료 +1을 확인한다.
3. **09 음성:** 처음 `00:00`, 낮은 회색 파형, 다시/미리 듣기/올리기 비활성. 녹음 버튼 → 권한 허용 → 시간 증가·말할 때 파형 변화·흰 정지 네모, 정지 후 세 컨트롤 활성, 미리 듣기 소리·진행 색·자동 종료, 30초 자동 정지, 다시 초기화, 올리기 후 05 토스트를 확인한다.
4. 권한 거부 시 “마이크 권한이 필요해요” 알림창 → “설정 열기”로 앱 설정 이동 → 마이크를 켜고 돌아와 다시 녹음한다.
5. 녹음 중 앱을 홈으로 보냈다 돌아오면 녹음이 멈추고 화면이 정상인지, 녹음 중 X → “그만둘까요?”인지 확인한다.
6. 폰 글자 크기·화면 크기를 키워도 06 올리기 버튼이 아래에 보이는지, 3버튼·제스처 내비게이션에서 하단 컨트롤이 가려지지 않는지 확인한다.
7. 이전 단계 회귀: 05 카운트다운·콕 찌르기·16 이동과 나가기 후 홈 복귀, 글/사진 수행·제출을 확인한다.
8. 릴리스 화면은 직접 확인하지 못했다. 코드에서 유형 전환은 `FLAG_DEBUGGABLE`로 가드되고 임시 제출 분기는 삭제되었음을 확인했다.
