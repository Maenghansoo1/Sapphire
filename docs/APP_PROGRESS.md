# HA'WE 앱 진행 현황 (iOS · Android)

> 다른 PC에서 이어서 작업할 때 보는 문서예요. 앱 명세는 루트의 `CLAUDE.md`, 화면 원본은 Figma예요.
> 마지막 갱신: 2026-10-12 · 브랜치 `feat/ios-step1`

## 지금 어디까지 했나

| | 상태 |
|---|---|
| iOS (`ios/`, SwiftUI) | Figma 31개 화면 전부 완료. 데이터는 앱 안 임시 저장소(`Core/Data/DataStore.swift`) |
| Android (`android/`, Kotlin + Compose) | 1~4단계 완료 — A1~A3·비밀번호 찾기, 01~04, 16, 16-1, 05~09, 10~15 |
| 서버 연결 | 아직 안 함 (기존 Spring Boot 서버에 붙일 예정) |

### 다음 할 일
1. **Android 5단계** — 17 마이페이지, 17-1 알림 설정, 17-2 유저 정보, 17-3 앱 정보, 17-4 로그아웃 확인, 17-5 회원 탈퇴 + 알림 권한 요청
   - iOS `ios/HAWE/Features/MyPage/` 를 그대로 옮기면 돼요.
   - 지금 Android 에서 연결 안 된 곳: 홈·01 시작 화면의 사람 아이콘(→ 17).
   - 실제 푸시 발송(FCM)은 서버 연결 때. Firebase `google-services.json` 은 사용자가 직접 넣어요(git 에 올리지 않기).
2. **Spring Boot 서버 연결** — 먼저 `src/` 의 API 구조를 읽고, 두 앱 `DataStore` 의 공개 함수 안쪽만 API 호출로 바꿔요.

## 정해진 것 (사용자 결정)

- 백엔드는 CLAUDE.md 의 Supabase 가 아니라 **기존 Spring Boot**. 인증·스토리지·스키마는 Spring 쪽에 맞춰야 해요.
- 작업 방식: 화면 UI 먼저(임시 데이터), 앱을 켜면 컴포넌트 갤러리가 아니라 실제 화면이 떠야 해요.
- 단계가 끝날 때마다 멈추고 결과 요약. 모르는 정책은 임의로 정하지 말고 물어보기. 색은 토큰만 사용.
- 포인트 색 `accent` = **#67A0C3** (눌림 #558BB0). 로고 오늘 칸·앱 아이콘도 같은 색.
- 팀 이름 "Sapphire Developers". 공지사항 기능 없음.
- 비밀번호 찾기 = 이메일 재설정 링크 방식. 약관·개인정보 처리방침은 Notion 페이지(앱 안 브라우저로 열기).
- 그룹을 나가도 내 작품·댓글은 그룹에 남아요 (회원 탈퇴 §5-5 와 다름).
- 14 작품 상세의 ⋯ 메뉴 없음. 08 글 화면의 "모두가 올리기 전까지…" 문구 없음.
- 지난 날짜도 내가 안 올린 날은 결과를 못 봐요 (서버에서도 막아야 해요).
- 15 캘린더 날짜 밑에는 유형 이름 전체(그림·사진·글·음성).
- 마이페이지 계정 표시: 가입한 이메일, 간편 로그인으로 가입했을 때만 "○○ 로그인" 줄.
- Android 도 네이티브(Kotlin + Jetpack Compose)로 만들어요.

### 아직 정하지 않은 것
- 초대 링크 도메인 (지금은 임시 `hawe.app`)
- 푸시 알림 권한을 묻는 시점 (정하지 않으면 17-1 에서 알림을 켤 때 묻기)
- iOS 푸시 인증서 / 유료 개발자 계정
- 캐릭터: 앱은 CLAUDE.md 7종(mongsil…)을 쓰는데 기존 프론트는 8종(star 포함) — 서버 연결 때 확인 필요

## 체험용 초대 코드 (임시 저장소)
- `K7Q2PX` 컴공 동기(친구 4명, 지난 기록·오늘 올린 친구 있음)
- `LOVE22` 우리 둘(연인)
- `FULL00` 정원 초과 확인용

## Figma
- 파일: https://www.figma.com/design/aMQOGgIQqdVP1nfcYJGG4G (에듀 계정 사본 — Figma MCP 는 에듀 계정으로 연결해야 읽혀요)
- CLAUDE.md 에 적힌 링크는 무료 계정 원본이라 접근 안 돼요.
- "screens" 페이지 하나(31개 프레임)뿐이고 Design System 페이지는 없어요. 화면 속 인스턴스와 `color/*` 변수로 읽어요.

## 개발 환경 준비

### Android
- Android Studio 설치 → `android/` 폴더 열기. 처음 열면 `local.properties`(SDK 경로)가 자동으로 생겨요(git 에 안 올라가요).
- JDK 17~21 필요 (JDK 26 은 Gradle 8.12 와 안 맞아요). Android Studio 내장 JDK 를 쓰면 돼요.
- 명령줄 빌드: `cd android && ./gradlew :app:assembleDebug`
- 버전: Kotlin 2.1.10, AGP 8.8.2, Gradle 8.12, compileSdk 35, minSdk 26, CameraX 1.4.1

### iOS (Mac 필요)
- `brew install xcodegen` → `cd ios && xcodegen` → `HAWE.xcodeproj` 열기
- `Config.example.xcconfig` 를 복사해 `Config.xcconfig` 를 만들고 서버 주소·키를 직접 넣어요(git 에 안 올라가요).
