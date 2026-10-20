# HA'WE 앱 결정 사항 (iOS · Android)

> 두 네이티브 앱(`ios/`, `android/`)을 만들며 정한 화면·정책 결정과 아직 정하지 않은 것을 모아 둔 문서예요.
> 원래 `feat/ios-step1` 브랜치의 `docs/APP_PROGRESS.md` 에 있던 내용을 2026-10-12 기준으로 옮기고 고쳤어요.
> 실행 방법은 루트 `README.md`, Android 이식 계획은 `docs/FRONTEND/ANDROID.md` 를 보세요.

## 현재 상태

| | 상태 |
|---|---|
| iOS (`ios/`, SwiftUI) | Figma 31개 화면 전부 구현. 데이터는 앱 안 임시 저장소(`Core/Data/DataStore.swift`) |
| Android (`android/`, Kotlin + Compose) | iOS 와 같은 화면 전부 구현 (`ANDROID.md` 0~9단계). 데이터는 `core/data/DataStore.kt` |
| 서버 연결 | 아직 안 함 — 두 앱 `DataStore` 공개 함수 안쪽만 Spring Boot API 호출로 바꿀 예정 |

## 정해진 것 (사용자 결정)

- 백엔드는 Supabase 가 아니라 **기존 Spring Boot**. 인증·스토리지·스키마는 Spring 쪽에 맞춰요.
- 프론트엔드는 **iOS·Android 네이티브만** 써요 (Cordova 하이브리드 앱은 2026-10-12 폐기).
- 작업 방식: 화면 UI 먼저(임시 데이터), 앱을 켜면 컴포넌트 갤러리가 아니라 실제 화면이 떠야 해요.
- 단계가 끝날 때마다 멈추고 결과 요약. 모르는 정책은 임의로 정하지 말고 물어보기. 색은 토큰만 사용.
- 포인트 색 `accent` = **#67A0C3** (눌림 #558BB0). 로고 오늘 칸·앱 아이콘도 같은 색.
- 팀 이름 "Sapphire Developers". 공지사항 기능 없음.
- 비밀번호 찾기 = 이메일 재설정 링크 방식.
- 약관·개인정보 처리방침은 Notion 페이지를 앱 안 브라우저로 열어요 (주소는 `ios/HAWE/Core/AppLinks.swift`).
- 그룹을 나가도 내 작품·댓글은 그룹에 남아요 (회원 탈퇴와 다름).
- 14 작품 상세의 ⋯ 메뉴 없음. 08 글 화면의 "모두가 올리기 전까지…" 문구 없음.
- 지난 날짜도 내가 안 올린 날은 결과를 못 봐요 (서버에서도 막아야 해요).
- 15 캘린더 날짜 밑에는 유형 이름 전체(그림·사진·글). 음성은 2026-10-12 음성 기능 제거로 없어졌어요(09 음성 화면·13 음성 결과·마이크 권한도 제거됨).
- 마이페이지 계정 표시: 가입한 이메일, 간편 로그인으로 가입했을 때만 "○○ 로그인" 줄.
- 프로필 캐릭터는 앱 기준 7종(`mongsil` 등). 서버도 `V5__align_characters_with_apps.sql` 로 맞췄어요 (별이 `star` 삭제).
- 알림 권한은 로그인 후 첫 진입에 물어요.
- 초대 링크는 제공하지 않고 초대 코드만 사용해요.

## 아직 정하지 않은 것

- iOS 푸시 인증서 / 유료 개발자 계정
- 실제 푸시 발송(FCM·APNs)은 서버 연결 때. Firebase `google-services.json` 은 각자 넣고 git 에 올리지 않아요.

## 체험용 초대 코드 (임시 저장소)

- `K7Q2PX` 컴공 동기 (친구 4명, 지난 기록·오늘 올린 친구 있음)
- `LOVE22` 우리 둘 (연인)
- `FULL00` 정원 초과 확인용

## Figma

- 파일: https://www.figma.com/design/aMQOGgIQqdVP1nfcYJGG4G (에듀 계정 사본 — Figma MCP 는 에듀 계정으로 연결해야 읽혀요)
- iOS 코드 주석의 `CLAUDE.md` 에 적힌 링크는 무료 계정 원본이라 접근 안 돼요.
- "screens" 페이지 하나(31개 프레임)뿐이고 Design System 페이지는 없어요. 화면 속 인스턴스와 `color/*` 변수로 읽어요.
