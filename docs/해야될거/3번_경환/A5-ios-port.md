# A5. iOS 일괄 이식 (음성 제거 · 10 달력 · 5A 콕 · 7A 스티커·댓글 · 8A 프로필·탈퇴 · A3 그룹 2차 · A4 새 틀 · A6 다중 미션 · A7 찰떡상 배지 · A8 Core ML 그림 맞히기 · A9 인사이트)

> 범위: **iOS 앱만**(`ios/`, SwiftUI, iOS 17+, `project.yml` 로 XcodeGen). **Mac + Xcode 환경이 필요**하다 — 이 Windows PC 에서는 빌드·실행·테스트할 수 없으므로 **이 PC 의 Claude/Codex 가 코드를 쓰더라도 컴파일 검증은 불가**하다(그 경우 "미검증"/"미컴파일"을 명시하고 Mac 에서 사용자가 빌드). 이식 기준은 Android 구현(각 A 계획의 결과물)이며, 이전 이식 선례: 커밋 `c00bfec feat(ios): Android 서버 연결(3A·4A·6A) iOS 이식`. 알림·푸시는 범위 밖.
>
> 이 문서는 **개별 구현 계획이 아니라 이식 작업의 묶음 계획**이다. 실행 시에는 Android 쪽 해당 계획이 끝나 커밋된 뒤, 항목마다 `.agent/plan.md` 로 올려 하나씩 진행하는 것을 권장한다(항목별 Findings 는 그때 Android diff 를 보고 채운다).

## 진행 상황 (2026-10-18)

항목 0~10 을 한 번에 옮겼어요 (Mac 에서 `xcodebuild` 빌드·`HAWETests` 단위 테스트로 확인). 서버를 켠 시뮬레이터 수동 확인은 아직이에요.

| # | 상태 | 메모 |
|---|---|---|
| 0 | 완료 | 음성 화면·재생·파형·마이크 권한 삭제. `rg -i "voice\|audio\|waveform\|AVAudio\|NSMicrophone"` 잔여 0건(VoiceOver 주석만) |
| 1 | 완료 | 달력·활동 요약은 이미 있던 코드. 앱 시계(`AppClock`)로 바꿈 |
| 2 | 완료 | `PokeRepository`, 05 멤버 줄 콕·똥 버튼(임시 아이콘 `icon-poke-temp`·`icon-fling-temp`) |
| 3 | 완료 | 14 상세의 스티커(이모지 임시)·댓글(멘션·사진 첨부·삭제) |
| 4 | 완료 | 프로필 편집(닉네임→캐릭터/사진→`GET /me`), 가입 사진, 탈퇴(그룹장 막기) |
| 5 | 완료 | 그룹 내 프로필, 그룹장 넘기기(`SuccessorPickerView`), 코드 다시 만들기, 주제 도착 시간, 비밀번호 변경 |
| 6 | 완료 | 새 틀 6종 수행 화면(`TemplateMissionViews.swift`) + 결과 카드·그룹 결과(`ResultSummary.swift`) |
| 7 | 완료 | 하루 여러 미션 — 05 위 슬롯 칩(임시), 홈 카드 "외 N개 · 내 제출 수" |
| 8 | 완료 | 찰떡상 배지·코멘트(`AiReviewLine`), 방금 올린 사진은 5초 뒤 한 번 더 불러옴 |
| 9 | 완료 | Core ML: int8 TFLite → `QuickDrawCNN.mlpackage`(`ios/Tools/quickdraw_coreml.py`, 층마다 int8 양자화 흉내). 골든 8개 최대 차이 0.0000. 라이선스 화면에 Quick, Draw!(CC BY 4.0) |
| 10 | 완료 | `GroupInsightView`(16 그룹 정보 → 우리 그룹 이야기) |
| + | 완료 | 개발자 모드(버전 10번 누르기): 개발자 도구·서버 시계·오류 주입·유저 전환·05 미션 생성/리롤 (Android `feature/dev`) |

- 테스트 타깃 `HAWETests` 를 `project.yml` 에 추가했어요(Android `app/src/test` 를 옮김). 그림 골든은 `ios/HAWETests/Resources/quickdraw/` 에 복사해 두고 `ml/` 원본과 같은지 테스트로 확인해요.
- `docs/테스트_문제점_발견/문제점.md` 1번(갤러리 미션 버튼)은 Android 도 아직 그대로라 iOS 도 같게 뒀어요.

## Goal

Android 에 반영된 변경(음성 제거 포함)을 iOS 에도 같은 계약·같은 동작으로 반영한다. iOS 는 현재 **인증·그룹·미션·제출·피드 연결(3A·4A·6A)** 까지 되어 있고, 달력(10단계)은 서버 연결 계층이 없어 보류, 콕·반응·댓글·프로필/탈퇴·그룹 2차·새 AI 기능은 미연결(로컬 `DataStore` 목업 또는 미구현)이다.

## Findings

- iOS 구조 (`ios/HAWE/`):
  - `Core/Net/`: `APIClient.swift`, `APIError.swift`, `APIModels.swift`, `AuthRepository.swift`, `GroupRepository.swift`, `MissionRepository.swift`, `SubmissionRepository.swift`, `TokenStore.swift`, `Services.swift`(공용 서비스, "아직 준비 중이에요" 메시지 포함). `Core/Data/DataStore.swift`, `Core/Models/`, `Core/AppSession.swift`, `Core/Push/`.
  - `Features/`: `Auth`(Login, SignupAccount, SignupProfile, PasswordReset), `Groups`(Create, GroupInfo, GroupProfile, Home, Join, Start), `Mission`(Draw/Photo/**Voice**/Write/Perform/Mission), `MyPage`(AppInfo, MyPage, NotificationSettings, ProfileEdit, UserInfo, Withdraw), `Records/RecordCalendarView`, `Results`(ResultComponents, ResultFeed, ResultsView, SubmissionDetailView).
  - **음성 관련 iOS 사용처(제거 대상)**: `Features/Mission/VoiceMissionView.swift`, `Features/Mission/MissionPerformView.swift`(음성 분기), `Features/Results/{ResultComponents,ResultFeed,ResultsView,SubmissionDetailView}.swift`(음성 카드·재생·파형), `Core/Models/MissionType.swift`(`.voice`), `Core/Models/TodayMission.swift`(`.voice: "음성 녹음"`), `Core/Models/GroupSummary.swift`(목업 `missionType: .voice`), `Core/Models/Submission.swift`, `Core/Net/APIModels.swift`(`waveform: [Double]?`, `duration`), `Core/Net/{MissionRepository,SubmissionRepository}.swift`, `Core/Data/{DataStore,DemoSeed}.swift`, `DesignSystem/Tokens/Color+HAWE.swift`(`waveformIdle`), 에셋 `Assets.xcassets/Icons/icon-mic.imageset`(+ 재생/일시정지 아이콘이 음성 전용이면 `icon-play`, `icon-pause`), **`project.yml` 의 `NSMicrophoneUsageDescription`**.
  - 현재 "준비 중" 지점(코드 주석 기준): `AppSession.swift:43`(탈퇴 서버 API 연결 TODO), `DataStore.swift:320`(15 캘린더 서버 기록 API 전까지 진입 준비 중), `GroupInfoView.swift`(그룹 내 프로필·주제 도착 시간·그룹 알림 "준비 중", 그룹장 넘기기 준비 중), `APIError.swift:29`(`SUCCESSOR_REQUIRED` 문구), `LoginView.swift`(간편 로그인), `UserInfoView.swift`(간편 로그인 연결/해제).
  - 임시 도장 이미지는 `Resources/Assets.xcassets/Stamps/` 에 SVG 로 이미 추가돼 있다(10단계 커밋) — 새 임시 이미지도 같은 방식(`*.imageset` + SVG, `임시` 표기).
  - 그림 화면은 **PencilKit**(`Features/Mission/DrawMissionView.swift` 의 `PencilCanvas: UIViewRepresentable`, `canvasView.drawing.strokes`) — Core ML 입력 마스크는 `PKDrawing.image(from:scale:)` 로 렌더한 이미지에서 얻는다(PNG 어댑터와 동일 경로로 통일 가능).
- 이식 대상별 Android 근거(이식 시 해당 커밋/파일을 읽고 1:1 대응):

| # | iOS 작업 | Android 기준 계획 | iOS 주요 파일 |
|---|---|---|---|
| 0 | **음성 제거** | A0 | 위 "음성 관련 iOS 사용처" 전부, `project.yml`(마이크 권한 문구 삭제), `ios/PushSamples`(음성 샘플이 있으면) |
| 1 | 10단계 달력·활동 요약 연결 | 커밋 `65bca7b`(Android `CalendarRepository`, `RecordCalendarScreen`, `MyPageScreen` 통계) | 신규 `Core/Net/CalendarRepository.swift`, `RecordCalendarView.swift`, `MyPageView.swift`, `DataStore.swift`(캘린더 기록) |
| 2 | 5A 콕·똥뿌리기 | A1 | `MissionView.swift`, `DataStore.swift`, 신규 `Core/Net/PokeRepository.swift`, `Core/Models/*`, `Assets.xcassets`(임시 아이콘) |
| 3 | 7A 스티커·댓글 | 7A 구현(Android `feature/results/SubmissionSocial.kt`, `core/net/SubmissionRepository.kt` 의 `toggleReaction/comments/addComment/deleteComment`) | `SubmissionDetailView.swift`, `ResultComponents.swift`, `SubmissionRepository.swift`, `Core/Models`(Submission/Reaction/Comment) |
| 4 | 8A 프로필 편집·가입 사진·탈퇴 | A2 | `ProfileEditView.swift`, `SignupProfileView.swift`, `WithdrawView.swift`, `AuthRepository.swift`, `AppSession.swift`, `APIClient.swift`(PUT multipart) |
| 5 | 그룹 2차(그룹 내 프로필·후임자 선택·코드 재발급·주제 도착 시각·비밀번호 변경) | A3 | `GroupProfileView.swift`, `GroupInfoView.swift`, 신규 후임자 선택 뷰, `GroupRepository.swift`, `UserInfoView.swift`/`ChangePasswordView`(파일 위치 확인), `AuthRepository.swift` |
| 6 | 새 미션 틀 6종(별점 후기·한 단어·밸런스·슬라이더 3종) + 집계 결과 | A4 | `MissionPerformView.swift` 및 신규 틀별 뷰, `ResultsView.swift`, `ResultFeed.swift`, `SubmissionDetailView.swift`, `SubmissionRepository.swift`, `Core/Models` |
| 7 | 하루 여러 개 미션 | A6 | `MissionView.swift`, `HomeView.swift`, `DataStore.swift`, `MissionRepository.swift`, `GroupRepository.swift` |
| 8 | "오늘의 주제 찰떡상" 배지·코멘트 | A7 | `Core/Models/Submission.swift`, `SubmissionRepository.swift`, `ResultFeed.swift`, `SubmissionDetailView.swift`, 신규 `AiReviewBadge.swift`, 임시 에셋 |
| 9 | **"AI가 맞혀볼게요" Core ML 그림 인식** | A8 + M1 산출물 `ml/quickdraw/export/QuickDrawCNN.mlpackage`, `labels_ko.txt`, `golden/` | 신규 `Features/Draw/{InkPreprocessor,QuickDrawClassifier,DrawSubjectMatcher,AiGuessCard}.swift`, `DrawMissionView.swift`, `ResultFeed.swift`, `SubmissionDetailView.swift`, 라이선스 화면(`AppInfoView.swift` 하위 라이선스 항목 — 위치 확인) |
| 10 | 그룹 활동 인사이트 대시보드 | A9 | 신규 `Features/Insight/*`, `Core/Net/InsightRepository.swift`, `GroupInfoView.swift`(진입 행), 임시 에셋 |

- 계약(서버)은 Android 계획과 동일. 공통 규칙: JSON camelCase, 오류 `{code, message}`, 401 `TOKEN_EXPIRED` → refresh 후 재시도, `INVALID_TOKEN` → 로그아웃(`APIClient.swift` 의 기존 구현 확인), 시각은 `+09:00` 오프셋 ISO-8601, 인사이트 202 처리(`APIClient` 가 2xx 로 처리하는지 확인).
- Swift 관례: 프로젝트가 `async/await` 기반 Repository 패턴(클래스/액터)을 쓰는지, `Codable` 모델 위치(`APIModels.swift`), 오류 매핑(`APIError.swift`), 세션(`AppSession`) 이 `ObservableObject`/`@Observable` 중 무엇인지 — **기존 코드에서 확인하고 따른다**(이 문서는 추정하지 않는다).

## Root Cause / Key Implementation Points

1. **순서**: 0(음성 제거) → 1(달력) → 2(콕) → 3(반응·댓글) → 4(프로필·탈퇴) → 5(그룹 2차) → 6(새 틀) → 8(찰떡상 배지) → 9(Core ML) → 10(인사이트) → 7(다중 미션, 마지막). 서버 계약이 안정된 순서이며, 6·7 은 Android 와 같은 이유로 같은 파일을 광범위하게 바꾸므로 뒤로 둔다. 0 이 가장 먼저인 이유: 이후 모든 항목이 `MissionType` 의 `switch` 를 고치므로 음성 분기를 먼저 없애면 충돌이 줄어든다.
2. **1:1 이식 원칙**: Android 의 순수 함수(분류 함수, 입력 규칙, 집계 표시 계산, 전처리, 주제 매칭)는 같은 이름·같은 규칙의 Swift 함수로 옮기고 **XCTest 로 같은 시나리오를 검증**한다(Android 테스트 파일을 번역). 서버 호출 순서(예: 프로필 저장 = 닉네임→캐릭터→사진→`GET /me`)와 오류 분류는 Android 와 동일하게.
3. **Core ML(항목 9)**: 모델은 M1 의 `.mlpackage`(float16)를 Xcode 리소스로 추가하면 Xcode 가 Swift 래퍼 클래스를 생성한다 — 생성 클래스 이름은 파일명(`QuickDrawCNN`)에 의존하므로 M1 과 이름을 맞춘다. 입력은 `MLMultiArray`(1×28×28×1) 또는 이미지 입력 타입 중 M1 이 정한 계약대로. 전처리는 **`PREPROCESS_SPEC.md` 와 골든 벡터**를 XCTest 로 검증한다(Swift 구현 오차 허용 ≤ 0.02). 추론은 백그라운드 `Task`, 모델 로드는 지연 로드 싱글턴. 그림·결과를 저장/전송하지 않는다. CC BY 4.0 출처를 라이선스 화면에 추가한다.
4. **임시 이미지**: 디자인 없는 요소는 SF Symbols 또는 임시 SVG 에셋으로 채우고(예: 슬라이더 — SwiftUI `Slider`, 별 — `star`/`star.fill`, 찰떡상 배지 — `rosette` 등) 교체 지점을 한 곳에 모은다. Android 임시 항목과 이름을 맞춘다(`*_temp`).
5. **원격 아바타**: Android `avatarOf` 와 동일하게 `profileImageUrl` 이 있으면 `AsyncImage`(원격) 로, 없으면 캐릭터. 기존 `AvatarContent` 타입 확장 여부 확인.
6. **음성 제거 시 주의**: 에셋 카탈로그의 아이콘 삭제는 `project.yml`/XcodeGen 이 에셋을 폴더째 포함하므로 폴더 삭제로 충분. `NSMicrophoneUsageDescription` 삭제 후 App Store 심사 관점에서 마이크 API 호출 코드가 하나도 남지 않았는지 `rg -i "AVAudioRecorder|AVAudioSession|AVAudioEngine|AVAudioPlayer"` 로 확인.
7. **빌드 검증 불가 시**: 코드 작성 후 `xcodebuild` 를 실행할 수 없으면 보고서에 "미컴파일" 을 명시하고, 사용자가 Mac 에서 `xcodegen generate && xcodebuild -scheme HAWE -destination 'platform=iOS Simulator,name=iPhone 15' test` 로 확인한다(스킴/목적지 이름은 `project.yml` 에서 확인).

## Files / Functions to Change

위 표의 iOS 파일들. 항목별 상세 목록은 해당 Android 계획의 "Files / Functions to Change" 를 iOS 대응 파일로 치환해 작성한다(실행 시점에 Android diff 를 보고 확정).

## Implementation Steps

항목 0~10 을 각각 `.agent/plan.md` 로 올려 순차 진행. 각 항목: (a) Android diff 읽기 → (b) 모델/Repository/오류 문구 → (c) 순수 함수 + XCTest → (d) 화면 → (e) 보고서(미컴파일 여부 명시) → (f) 사용자가 Mac 에서 빌드·실행 확인.

## Constraints

- `ios/` 만 수정(항목 9 는 `ml/quickdraw/export/` 산출물을 읽기만). 백엔드/Android 수정 금지. 알림·푸시(`Core/Push`) 변경 금지. 서버 값이 진실, 낙관 반영 금지, 목업과 서버 데이터 혼용 금지. 새 SPM 패키지 추가 금지(표준 프레임워크 + Core ML/Vision/PencilKit 만). 커밋/푸시 금지.
- 그림·사진·추론 결과를 서버/외부로 전송하지 않는다(항목 9). 마이크 권한·코드를 되살리지 않는다(항목 0).
- Windows 환경에서는 컴파일 불가 — 문법 오류 위험이 크므로 **작은 단위로 나눠 Mac 에서 자주 빌드**해야 한다는 점을 보고서에 명시.

## Edge Cases

각 항목은 해당 Android 계획의 Edge Cases 를 그대로 따른다. iOS 고유: 사진 선택(`PhotosPicker`) 권한/취소, 이미지 압축(HEIC → JPEG 변환, 10MB 한도), 키보드 회피(`.scrollDismissesKeyboard`, safe area), 백그라운드 전환 중 업로드 유지, VoiceOver 접근성 라벨, 다크 모드(앱이 라이트 고정인지 확인), 작은 화면(iPhone SE) 레이아웃. Core ML: 시뮬레이터와 실기기 결과 차이(Neural Engine vs CPU)로 확률이 소수점 단위로 달라질 수 있음 → 골든 테스트 허용 오차로 흡수, 모델 로드 실패 시 기능 숨김.

## Regression Risks

- 기존 iOS 연결(3A·4A·6A)과 로컬 `DataStore` 동작이 새 연결과 섞여 깨지는 것(특히 `DataStore` 의 목업 경로).
- `project.yml` 에 파일이 자동 포함되는지(XcodeGen 소스 글로브) — 새 파일 추가 후 프로젝트 재생성 필요.
- 컴파일 미검증 상태로 커밋되면 빌드가 깨질 수 있음 → 사용자가 Mac 에서 빌드한 뒤 커밋.
- 음성 제거 후 `switch` 가 `@unknown default` 없이 exhaustive 인 곳의 연쇄 수정.

## Tests Required

Android 의 단위 테스트에 대응하는 XCTest(Repository 의 요청/파싱: `URLProtocol` 스텁 사용, 순수 함수, `DataStore` 상태 전이, 전처리 골든 벡터). 기존 iOS 테스트 타깃 위치를 확인하고 같은 곳에 추가(없으면 `project.yml` 에 테스트 타깃 추가는 별도 합의 필요 → 사용자에게 질문).

## Validation Steps

Mac 에서 `xcodegen generate`(필요 시) → Xcode 빌드/테스트 통과 → 시뮬레이터에서 항목별 수동 확인(서버 기동 필요). 항목 0 은 `rg -i "voice|audio|waveform|AVAudio|NSMicrophone"` 잔여 0건 확인. Windows 에서 작성한 경우 보고서 첫 줄에 "컴파일/테스트 미실행" 을 명시.

## Potential Merge Conflicts

iOS 전용 파일끼리의 충돌이 주(`DataStore.swift`, `SubmissionRepository.swift`, `MissionView.swift`, `GroupInfoView.swift`, `ResultFeed.swift`). Android·백엔드와는 충돌하지 않음. 문서(`docs/FRONTEND/*`)에 iOS 진행 표를 갱신하면 다른 문서 작업과 겹칠 수 있음.
