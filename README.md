# HA'WE (하위)

그룹마다 매일 미션이 열리고, 내가 먼저 답해야 그룹원들의 답을 볼 수 있는 모바일 그룹 앱.
프로젝트 소개는 `docs/FEATURE/PROJECT_OVERVIEW.md` 를 보세요.

## 구성

| 폴더 | 내용 |
|---|---|
| `ios/` | iOS 네이티브 앱 (SwiftUI, iOS 17+, XcodeGen) |
| `android/` | Android 네이티브 앱 (Kotlin + Jetpack Compose, minSdk 26) |
| `src/` | 백엔드 (Spring Boot, Java 25, Maven) |
| `docs/` | 기획·설계 문서 |

프론트엔드는 iOS·Android 네이티브 앱만 써요. (이전 Cordova 하이브리드 앱은 폐기했어요.)
두 앱 모두 Spring 서버에 연결돼 있어요 (로그인·그룹·오늘의 미션·제출·결과 피드). 좋아요·댓글·기록 캘린더·프로필 수정 등 서버 API 가 아직 없는 기능은 "준비 중"으로 안내해요.
화면·정책 결정 사항과 체험용 초대 코드는 `docs/FRONTEND/APP_DECISIONS.md` 를 보세요.

## iOS 실행

```bash
brew install xcodegen                          # 최초 1회
cd ios
cp Config.example.xcconfig Config.xcconfig     # 최초 1회
xcodegen generate                              # project.yml → HAWE.xcodeproj (파일을 추가·삭제한 뒤에도 다시 실행)
open HAWE.xcodeproj                            # Xcode 에서 HAWE 스킴을 시뮬레이터로 실행
```

서버 주소는 `Config.xcconfig` 의 `API_BASE_URL` 이에요. 비워 두면 디버그 빌드는 `http://localhost:8080`(시뮬레이터 → Mac 의 백엔드)을 써요. 실기기에서는 Mac 의 IP 주소로 바꿔 주세요.

명령줄로 빌드만 확인하려면:

```bash
xcodebuild -project HAWE.xcodeproj -scheme HAWE -sdk iphonesimulator \
  -destination 'generic/platform=iOS Simulator' CODE_SIGNING_ALLOWED=NO build
```

## Android 실행

- Android SDK(API 35)와 **JDK 17~23** 이 필요해요. Gradle 8.13 은 JDK 25 에서 실행되지 않으니,
  백엔드용 JDK 25 가 기본이면 `JAVA_HOME` 을 따로 지정하세요.
- Android Studio 에서 `android/` 폴더를 열고 `app` 을 실행하거나, 명령줄로:

```bash
cd android
export JAVA_HOME=$(/usr/libexec/java_home -v 23)   # macOS 예시
export ANDROID_HOME=~/Library/Android/sdk           # 또는 local.properties 의 sdk.dir (Android Studio 가 자동 생성)
./gradlew assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug           # 연결된 기기·에뮬레이터에 설치
./gradlew testDebugUnitTest      # 단위 테스트
```

이식 계획과 iOS ↔ Android 화면 대응은 `docs/FRONTEND/ANDROID.md` 를 보세요.

## 백엔드 실행

```bash
cp .env.example .env    # DB 접속 정보·JWT secret 입력
./mvnw spring-boot:run
```
