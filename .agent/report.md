# 6A Android 서버 제출·결과 연결 보고

## 변경 파일

- `android/app/src/main/java/com/sapphire/hawe/core/net/ApiClient.kt`, `SubmissionApi.kt`, `SubmissionRepository.kt`: multipart 제출, 60초 업로드 타임아웃, 토큰 재발급 후 본문 재전송, 서버 DTO 및 오류 분류를 추가했습니다.
- `android/app/src/main/java/com/sapphire/hawe/core/data/DataStore.kt`, `DemoSeed.kt`, `core/model/Submission.kt`: 제출·피드의 서버 ID와 상태를 사용하고, 로컬 제출·파일 저장·좋아요·댓글 상태를 제거했습니다. 같은 미션의 피드는 재조회 때 유지하고 새 미션에서는 비웁니다.
- `android/app/src/main/java/com/sapphire/hawe/app/{AppViewModel,RootScreen,MainNavigation}.kt`, `feature/mission/*Screen.kt`: 네 유형을 서버에 업로드하며 진행 표시, 실패 시 입력 보존·재시도, 오류별 안내, 지각 안내와 미션 재조회 신호를 연결했습니다.
- `android/app/src/main/java/com/sapphire/hawe/feature/results/{ResultsScreen,SubmissionDetailScreen,ResultsFormat,VoicePlayerController}.kt`: 진입·복귀 시 서버 피드 로드, 재시도 및 접근 오류 처리, 지각 표시, 좋아요·댓글 UI 숨김, 서버 URL 음성 비동기 재생을 적용했습니다. 기존 `ResultComponents.kt`의 URL 모델 경로는 그대로 사용합니다.
- `android/gradle/libs.versions.toml`, `android/app/build.gradle.kts`: Coil `coil-network-okhttp`을 추가해 서버 이미지 URL을 로드합니다.
- `android/app/src/test/java/com/sapphire/hawe/core/{data/DataStoreTest,net/ApiClientTest,net/SubmissionRepositoryTest}.kt`, `feature/results/ResultsFormatTest.kt`: 서버 상태·multipart·재발급·DTO·오류·표시 문구 회귀 검증을 갱신/추가했습니다.
- `docs/FRONTEND/ANDROID.md`: 6A의 현재 서버 연결 상태를 기록했습니다.

## 검증

- JDK 17: `android\gradlew.bat testDebugUnitTest assembleDebug lint assembleRelease --quiet` 통과. JVM 테스트 96개, 실패 0, 오류 0입니다.
- `git diff --check` 통과(내용 오류 없음; 기존 파일의 LF/CRLF 변환 경고만 출력).
- 잔여 코드 검색: 제품 화면에 `toggleLike`/`addComment`/`deleteComment`/`CommentInput` 호출 없음. `LikeButton` 정의만 7A 재사용을 위해 남아 있습니다.
- 수동 서버 시나리오는 미검증입니다. `adb` 연결 기기는 있었으나 로컬 8080 백엔드가 실행 중이지 않아 가입·제출·피드·이미지·음성·재시작·오프라인·지각 동작을 실제 서버로 확인하지 못했습니다.

## 남은 위험·가정·충돌

- 4A Android 작업과 백엔드 6단계 변경이 작업 트리에 이미 미커밋 상태였습니다. 이 구현은 그 위에 6A를 쌓았고 백엔드, iOS, Cordova 파일은 수정하지 않았습니다. Android의 공통 파일(`DataStore.kt`, `MainNavigation.kt`, `MissionScreen.kt` 등)은 4A와 변경이 겹치므로 별도 커밋으로 분리하려면 검토가 필요합니다.
- `DemoSeed`/`TopicPool`은 테스트·프리뷰용, `poke`/`records`/`stats`는 기존 코드 호환용으로 남겼습니다. 제품의 콕·달력·활동 요약은 계속 숨김/준비 중 상태입니다.
- 서버 계약과 다른 별도 설계 변경은 하지 않았습니다. 서버에서 제공하지 않는 스티커·댓글은 7A까지 숨깁니다.
- 디바이스 기반 흐름, 특히 URL 미디어 로딩·재생과 네트워크 타임아웃 후 중복 제출 수렴은 JVM 테스트 외 수동 확인이 필요합니다.
