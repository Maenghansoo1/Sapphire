# A5. iOS 일괄 이식 (5A 콕 · 7A 스티커·댓글 · 8A 프로필·탈퇴 · 10 달력 · A3 그룹 2차 · A4 새 틀 · A6 다중 미션)

> 범위: **iOS 앱만**(`ios/`, SwiftUI, iOS 17+, `project.yml` 로 XcodeGen). **Mac + Xcode 환경이 필요**하다 — 이 Windows PC 에서는 빌드·실행·테스트할 수 없으므로 **이 PC 의 Claude/Codex 가 코드를 쓰더라도 컴파일 검증은 불가**하다(그 경우 "미검증" 을 명시하고 Mac 에서 사용자가 빌드). 이식 기준은 Android 구현(각 A 계획의 결과물)이며, 이전 이식 선례: 커밋 `c00bfec feat(ios): Android 서버 연결(3A·4A·6A) iOS 이식`. 알림·푸시는 범위 밖.
>
> 이 문서는 **개별 구현 계획이 아니라 이식 작업의 묶음 계획**이다. 실행 시에는 Android 쪽 A1/A2/A3/A4/A6 이 끝나 커밋된 뒤, 항목마다 `.agent/plan.md` 로 올려 하나씩 진행하는 것을 권장한다(항목별 Findings 는 그때 Android diff 를 보고 채운다).

## Goal

Android 에 반영된 서버 연결을 iOS 에도 같은 계약·같은 동작으로 반영한다. iOS 는 현재 **인증·그룹·미션·제출·피드 연결(3A·4A·6A)** 까지 되어 있고, 달력(10단계)은 서버 연결 계층이 없어 보류, 콕·반응·댓글·프로필/탈퇴·그룹 2차는 미연결(로컬 `DataStore` 목업)이다.

## Findings

- iOS 구조 (`ios/HAWE/`):
  - `Core/Net/`: `APIClient.swift`, `APIError.swift`, `APIModels.swift`, `AuthRepository.swift`, `GroupRepository.swift`, `MissionRepository.swift`, `SubmissionRepository.swift`, `TokenStore.swift`, `Services.swift`(공용 서비스, "아직 준비 중이에요" 메시지 포함). `Core/Data/DataStore.swift`, `Core/Models/`, `Core/AppSession.swift`, `Core/Push/`.
  - `Features/`: `Auth`(Login, SignupAccount, SignupProfile, PasswordReset), `Groups`(Create, GroupInfo, GroupProfile, Home, Join, Start), `Mission`(Draw/Photo/Voice/Write/Perform/Mission), `MyPage`(AppInfo, MyPage, NotificationSettings, ProfileEdit, UserInfo, Withdraw), `Records/RecordCalendarView`, `Results`(ResultComponents, ResultFeed, ResultsView, SubmissionDetailView).
  - 현재 "준비 중" 지점(코드 주석 기준): `AppSession.swift:43`(탈퇴 서버 API 연결 TODO), `DataStore.swift:320`(15 캘린더 서버 기록 API 전까지 진입 준비 중), `GroupInfoView.swift`(그룹 내 프로필·주제 도착 시간·그룹 알림 "준비 중", 그룹장 넘기기 준비 중), `APIError.swift:29`(`SUCCESSOR_REQUIRED` 문구), `LoginView.swift`(간편 로그인), `UserInfoView.swift`(간편 로그인 연결/해제).
  - 임시 도장 이미지는 `Resources/Assets.xcassets/Stamps/` 에 SVG 로 이미 추가돼 있다(10단계 커밋) — 새 임시 이미지도 같은 방식(`*.imageset` + SVG, `임시` 표기).
- 이식 대상별 Android 근거(이식 시 해당 커밋/파일을 읽고 1:1 대응):

| # | iOS 작업 | Android 기준 계획 | iOS 주요 파일 |
|---|---|---|---|
| 1 | 10단계 달력·활동 요약 연결 | 커밋 `65bca7b`(Android `CalendarRepository`, `RecordCalendarScreen`, `MyPageScreen` 통계) | 신규 `Core/Net/CalendarRepository.swift`, `RecordCalendarView.swift`, `MyPageView.swift`, `DataStore.swift`(캘린더 기록) |
| 2 | 5A 콕·똥뿌리기 | A1 | `MissionView.swift`, `DataStore.swift`, 신규 `Core/Net/PokeRepository.swift`, `Core/Models/*`, `Assets.xcassets`(임시 아이콘) |
| 3 | 7A 스티커·댓글 | 이번 세션 구현(Android `feature/results/SubmissionSocial.kt`, `core/net/SubmissionRepository.kt` 의 `toggleReaction/comments/addComment/deleteComment`) | `SubmissionDetailView.swift`, `ResultComponents.swift`, `SubmissionRepository.swift`, `Core/Models`(Submission/Reaction/Comment) |
| 4 | 8A 프로필 편집·가입 사진·탈퇴 | A2 | `ProfileEditView.swift`, `SignupProfileView.swift`, `WithdrawView.swift`, `AuthRepository.swift`, `AppSession.swift`, `APIClient.swift`(PUT multipart) |
| 5 | 그룹 2차(그룹 내 프로필·후임자 선택·코드 재발급·주제 도착 시각·비밀번호 변경) | A3 | `GroupProfileView.swift`, `GroupInfoView.swift`, 신규 후임자 선택 뷰, `GroupRepository.swift`, `UserInfoView.swift`/`ChangePasswordView`(파일 위치 확인), `AuthRepository.swift` |
| 6 | 새 미션 틀 8종 + 집계 결과 + 정답 맞히기 | A4 | `MissionPerformView.swift` 및 신규 틀별 뷰, `ResultsView.swift`, `ResultFeed.swift`, `SubmissionDetailView.swift`, `SubmissionRepository.swift`, `Core/Models` |
| 7 | 하루 여러 개 미션 | A6 | `MissionView.swift`, `HomeView.swift`, `DataStore.swift`, `MissionRepository.swift`, `GroupRepository.swift` |

- 계약(서버)은 Android 계획과 동일. 공통 규칙: JSON camelCase, 오류 `{code, message}`, 401 `TOKEN_EXPIRED` → refresh 후 재시도, `INVALID_TOKEN` → 로그아웃(`APIClient.swift` 의 기존 구현 확인), 시각은 `+09:00` 오프셋 ISO-8601.
- Swift 관례: 프로젝트가 `async/await` 기반 Repository 패턴(클래스/액터)을 쓰는지, `Codable` 모델 위치(`APIModels.swift`), 오류 매핑(`APIError.swift`), 세션(`AppSession`) 이 `ObservableObject`/`@Observable` 중 무엇인지 — **기존 코드에서 확인하고 따른다**(이 문서는 추정하지 않는다).

## Root Cause / Key Implementation Points

1. **순서**: 1(달력) → 2(콕) → 3(반응·댓글) → 4(프로필·탈퇴) → 5(그룹 2차) → 6(새 틀) → 7(다중 미션). 서버 계약이 안정된 순서이며, 6·7 은 Android 와 같은 이유로 가장 마지막(같은 파일을 광범위하게 바꿈).
2. **1:1 이식 원칙**: Android 의 순수 함수(분류 함수, 입력 규칙, 집계 표시 계산)는 같은 이름·같은 규칙의 Swift 함수로 옮기고 **XCTest 로 같은 시나리오를 검증**한다(Android 테스트 파일을 번역). 서버 호출 순서(예: 프로필 저장 = 닉네임→캐릭터→사진→`GET /me`)와 오류 분류는 Android 와 동일하게.
3. **임시 이미지**: 디자인 없는 요소는 SF Symbols 또는 임시 SVG 에셋으로 채우고(예: 슬라이더 — SwiftUI `Slider`, 별 — `star`/`star.fill`) 교체 지점을 한 곳에 모은다. Android 임시 항목과 이름을 맞춘다(`*_temp`).
4. **원격 아바타**: Android `avatarOf` 와 동일하게 `profileImageUrl` 이 있으면 `AsyncImage`(원격) 로, 없으면 캐릭터. 기존 `AvatarContent` 타입 확장 여부 확인.
5. **빌드 검증 불가 시**: 코드 작성 후 `xcodebuild` 를 실행할 수 없으면 보고서에 "미컴파일" 을 명시하고, 사용자가 Mac 에서 `xcodegen generate && xcodebuild -scheme HAWE -destination 'platform=iOS Simulator,name=iPhone 15' test` 로 확인한다(스킴/목적지 이름은 `project.yml` 에서 확인).

## Files / Functions to Change

위 표의 iOS 파일들. 항목별 상세 목록은 해당 Android 계획의 "Files / Functions to Change" 를 iOS 대응 파일로 치환해 작성한다(실행 시점에 Android diff 를 보고 확정).

## Implementation Steps

항목 1~7 을 각각 `.agent/plan.md` 로 올려 순차 진행. 각 항목: (a) Android diff 읽기 → (b) 모델/Repository/오류 문구 → (c) 순수 함수 + XCTest → (d) 화면 → (e) 보고서(미컴파일 여부 명시) → (f) 사용자가 Mac 에서 빌드·실행 확인.

## Constraints

- `ios/` 만 수정. 백엔드/Android 수정 금지. 알림·푸시(`Core/Push`) 변경 금지. 서버 값이 진실, 낙관 반영 금지, 목업과 서버 데이터 혼용 금지. 새 SPM 패키지 추가 금지(표준 프레임워크만). 커밋/푸시 금지.
- Windows 환경에서는 컴파일 불가 — 문법 오류 위험이 크므로 **작은 단위로 나눠 Mac 에서 자주 빌드**해야 한다는 점을 보고서에 명시.

## Edge Cases

각 항목은 해당 Android 계획의 Edge Cases 를 그대로 따른다. iOS 고유: 사진 선택(`PhotosPicker`) 권한/취소, 이미지 압축(HEIC → JPEG 변환, 10MB 한도), 키보드 회피(`.scrollDismissesKeyboard`, safe area), 백그라운드 전환 중 업로드 유지, VoiceOver 접근성 라벨, 다크 모드(앱이 라이트 고정인지 확인), 작은 화면(iPhone SE) 레이아웃.

## Regression Risks

- 기존 iOS 연결(3A·4A·6A)과 로컬 `DataStore` 동작이 새 연결과 섞여 깨지는 것(특히 `DataStore` 의 목업 경로).
- `project.yml` 에 파일이 자동 포함되는지(XcodeGen 소스 글로브) — 새 파일 추가 후 프로젝트 재생성 필요.
- 컴파일 미검증 상태로 커밋되면 빌드가 깨질 수 있음 → 사용자가 Mac 에서 빌드한 뒤 커밋.

## Tests Required

Android 의 단위 테스트에 대응하는 XCTest(Repository 의 요청/파싱: `URLProtocol` 스텁 사용, 순수 함수, `DataStore` 상태 전이). 기존 iOS 테스트 타깃 위치를 확인하고 같은 곳에 추가(없으면 `project.yml` 에 테스트 타깃 추가는 별도 합의 필요 → 사용자에게 질문).

## Validation Steps

Mac 에서 `xcodegen generate`(필요 시) → Xcode 빌드/테스트 통과 → 시뮬레이터에서 항목별 수동 확인(서버 기동 필요). Windows 에서 작성한 경우 보고서 첫 줄에 "컴파일/테스트 미실행" 을 명시.

## Potential Merge Conflicts

iOS 전용 파일끼리의 충돌이 주(`DataStore.swift`, `SubmissionRepository.swift`, `MissionView.swift`, `GroupInfoView.swift`). Android·백엔드와는 충돌하지 않음. 문서(`docs/FRONTEND/*`)에 iOS 진행 표를 갱신하면 다른 문서 작업과 겹칠 수 있음.
