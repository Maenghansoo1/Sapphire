# Plan — Android 4단계: 그룹 화면 (04 홈 · 02 그룹 만들기/02-1 연인 · 03 초대 코드 · 16 그룹 정보 · 16-1 그룹 내 프로필)

## Goal

iOS 의 그룹 관련 화면 5개를 Android(Compose)로 옮기고, 3단계에서 만든 `MainNavHost` 를 iOS `MainFlowView` 와 같은 흐름으로 확장한다.

- 로그인 후 첫 화면: 그룹이 0개면 **01 시작**, 1개 이상이면 **04 홈** (둘 다 같은 루트 목적지가 `DataStore.groups` 로 분기).
- 01/04 → **02 그룹 만들기** → 만들면 **16 그룹 정보**(초대 코드 공유)로 이동하고, 뒤로 가면 홈.
- 01/04(+) → **03 초대 코드 참여** → 참여하면 홈으로.
- 16 → **16-1 그룹 내 프로필**, 주제 도착 시간(방장만), 그룹 알림 토글, 그룹 나가기.
- 04 의 + → 01 시작(뒤로 가기 포함 `showsBack = true`).
- 아직 없는 화면(05 미션, 17 마이페이지)으로 가는 버튼은 3단계처럼 **"준비 중이에요" 토스트**로 처리한다.

전체 로드맵은 `docs/FRONTEND/ANDROID.md` (§5 대응표, §6 4단계, §7 주의점). 3단계 계획·결과는 이 파일의 이전 버전과 `.agent/report.md` (덮어쓰기 전 내용은 git 에 없음 — 필요한 사실은 아래 Findings 에 옮겨 적었다).

이번 범위 아님: 05 미션, 06~09, 10~15, 17 계열, 알림, 서버 연결, 딥링크(App Links) 수신.

## Findings

### Android 현재 상태 (3단계까지 완료, 폰에서 확인됨)
- 내비게이션: `app/RootScreen.kt` 가 `AppSession.state` 로 `AuthNavHost`(SIGNED_OUT) / `MainNavHost`(SIGNED_IN) 를 고른다. `LocalDataStore` / `LocalAppSession` CompositionLocal 은 `RootScreen.kt` 에 있다.
- `app/MainNavigation.kt`: 지금은 `sealed interface MainRoute { @Serializable data object Start }` 하나와 `MainNavHost(session)` 가 `StartScreen` 만 보여준다. `onCreateGroup`/`onJoinGroup`/`onMyPage` 는 비어 있고 `StartScreen` 이 버튼마다 내부 토스트("준비 중이에요")를 띄운다.
- `feature/groups/StartScreen.kt` (3단계 산출물): `StartScreen(session, showsBack=false, onBack, onCreateGroup, onJoinGroup, onMyPage)`. 세 버튼 모두 콜백 호출 + `showToast = true`. 로고 길게 누름 = 디버그 전용 로그아웃(`FLAG_DEBUGGABLE`).
- 데이터: `core/data/DataStore.kt` (불변 모델, 읽기 전용 `groups: List<StoredGroup>`, `myProfile`) 공개 API —
  `summaries: List<GroupSummary>`, `group(id): GroupDetail?`, `createGroup(name, relationship, startDate: LocalDate? = null): UUID`, `lookUpInvite(code): InviteLookupResult`(`Success(preview)` / `Failure(error: InviteLookupError)`, `InviteLookupError.message` 가 화면 문구), `join(code): UUID?`, `leave(id)`, `setTopicTime(time: LocalTime, groupID)`, `setNotify(on, groupID)`, `setMyGroupProfile(profile: Profile?, groupID)`, `refreshTodayMissions(now = Instant.now(clock))`.
- 모델: `core/model/GroupDetail.kt` (`Profile`, `AvatarContent.Character/Photo`, `GroupMember(id, defaultProfile, groupProfile, isMe, isOwner)` + `displayed`, `GroupDetail(... topicTime: LocalTime, notify, members, startDate: LocalDate?)` + `me`/`amIOwner`), `GroupSummary(...).daysTogether(on: Instant)`, `GroupInvitePreview(name, relationship, members)` + `isFull`, `InviteCode.sanitize/LENGTH`, `MissionType`, `AvatarContent.choiceName`(`core/model/AvatarContentExt.kt`).
- 디자인 시스템: `HButton(title, modifier, kind, height, enabled, onClick)`, `HChip(title, isSelected, modifier, onClick)`, `HTextField(value, onValueChange, placeholder, modifier, label, isSecure, helper, maxLength, error, keyboardType, textSize, capitalization, autoCorrectEnabled, imeAction, onImeAction)`, `HListRow(label, modifier, value, height, showsDivider, showsChevron, onClick)`, `HToggle(isOn, onChange, modifier, enabled)`(접근성 라벨 파라미터 없음), `HNavBar(title, leading, onLeading, trailing)`, `HNavStep`, `HAvatar(content, modifier, size, ringed, characterScale)`, `MemberRow(members, relationship, avatarSize)`, `CharacterPicker(selection, onSelect, cellSize, characterSize, columnSpacing, rowSpacing)`, `HAlert(title, message, confirmTitle, onCancel, onConfirm, cancelTitle, confirmKind)`, `HToast(message)`, `HCtaScaffold(horizontalPadding, bottomInset, content, cta)` + `ctaBottomPadding(bottomInset)`, `HIconView`, `HLogoLockup`, `RelationshipDecoration`.
- 키보드: `HTextField` 의 `KeyboardActions` 는 3단계 리뷰 수정으로 기본 동작(Next=다음 칸, Done=키보드 숨김)을 쓴다. `MainActivity` 는 `enableEdgeToEdge()`, `RootScreen` 이 `statusBarsPadding()` 적용. 시스템 바/키보드는 각 화면이 `HCtaScaffold`·`imePadding`·`ctaBottomPadding` 으로 처리.
- 의존성: `navigation-compose 2.9.5`, `kotlinx-serialization-json`, `androidx.browser`, `coil3`, Compose BOM `2025.10.01`(Material3 포함), `lifecycle-viewmodel-compose`. **새 의존성 추가 금지** (아래 Constraints).

### 원본 iOS (읽기 전용, 수정 금지) — 파일 안 주석·수치가 기준
- `ios/HAWE/App/MainFlowView.swift` — `MainRoute` 와 내비게이션 규칙(아래 정리)
- `ios/HAWE/Features/Groups/HomeView.swift` — 04 + `GroupCard`
- `ios/HAWE/Features/Groups/CreateGroupView.swift` — 02/02-1 + `StartDateSheet`
- `ios/HAWE/Features/Groups/JoinGroupView.swift` — 03 + `GroupInviteCard` + `JoinGroupModel`
- `ios/HAWE/Features/Groups/GroupInfoView.swift` — 16 + `TopicTimeSheet` (`HToast` 도 여기 정의 — Android 는 이미 있음)
- `ios/HAWE/Features/Groups/GroupProfileView.swift` — 16-1
- `ios/HAWE/Features/Groups/StartView.swift` — 3단계에서 이미 구현. 이번엔 콜백 연결만.

### iOS 화면 명세 (그대로 구현)

**내비게이션 규칙 (`MainFlowView`)**
- 루트: `groups.isEmpty ? StartView(showsBack=false) : HomeView`. 루트 위에 쌓이는 목적지: start(=StartView showsBack=true), createGroup, joinGroup(code?), groupInfo(groupID), mission(groupID)(5단계), results/calendar/myPage(이번엔 없음).
- 01·04(+)의 "새 그룹 만들기" → createGroup. 01 의 "초대 코드로 참여하기" → joinGroup(null).
- 02 완료(`onCreated`) → `store.createGroup(name, relationship, startDate)` 후 **백스택을 [groupInfo(id)] 로 교체**(루트 위에 groupInfo 하나만; 뒤로 가면 루트=홈).
- 03 완료(`onJoined`) → `store.join(code)` 후 **백스택 비움**(루트로 복귀 → 그룹이 생겼으니 홈).
- 16 나가기(`onLeft`) → 백스택 비움(루트로) **그리고** `store.leave(groupID)`. (iOS 는 비운 뒤 leave; 마지막 그룹을 나가면 루트가 01 시작으로 바뀜.)

**04 홈** (`HomeView`) — 좌우 20(`screenPaddingCompact`)
- 헤더 높이 52: 왼쪽 "내 그룹"(26 Bold, `HType.screenTitle`), 오른쪽 아이콘 두 개(간격 16): plus(24) → 01 시작(showsBack) / person(22) → 마이페이지(이번엔 토스트). 아이콘은 그대로 두고 터치 영역만 44dp.
- 그 아래 `"{오늘 날짜} · 그룹 {N}개"`(14, textSecondary, 위 6, 아래 20). 오늘 날짜는 한국어 로캘 "M월 d일 EEEE"(예: `10월 12일 월요일`) — iOS 가 `month(.defaultDigits).day().weekday(.wide)` ko_KR 이다. 정확한 문자열을 확인할 수 없으니 이 형식으로 하고 보고서에 "iOS 와 대조 필요"라고 적는다. 날짜는 **서울 시간대 기준**(`HaweTime.SEOUL`).
- 그룹 카드 목록: 세로 스크롤(`LazyColumn` 또는 `Column`+`verticalScroll`), 간격 12, 아래 24. 카드를 누르면 05 미션(이번엔 토스트 "준비 중이에요").
- 화면에 들어올 때마다 `store.refreshTodayMissions()` 호출(iOS `onAppear`). Navigation Compose 는 위에 다른 화면이 쌓이면 이 목적지가 컴포지션에서 빠졌다가 돌아올 때 다시 들어오므로 `LaunchedEffect(Unit)` 로 충분하다.
- `GroupCard`: 테두리 `line` 1dp, 모서리 `HRadius.card`(20), 안쪽 18, 내부 간격 14.
  1. 그룹 이름(18 Bold, 한 줄 말줄임) + 부제(13): `"{관계} · {N}명"` + 연인이고 `daysTogether()` 가 있으면 `" · 함께한 지 {N}일"`.
  2. `MemberRow(members, relationship)` (기본 아바타 34).
  3. 미션 박스: surface 배경, 모서리 `HRadius.input`(14), 안쪽 14, 폭 가득. 윗줄 `"오늘의 미션 · {유형 표시명}"`(12 SemiBold textSecondary), 아랫줄 주제(15 SemiBold ink), 간격 6.
  4. 맨 아래 줄: 왼쪽 `"{submittedCount} / {멤버 수}명 완료"`(13 textSecondary), 오른쪽 배지: `!didSubmit` 이면 "내 미션 남음"(accent 배경 / 흰 글자) 아니면 "열어보기"(surface 배경 / ink), 13 SemiBold, 좌우 12, 높이 28, 알약.

**02 그룹 만들기 / 02-1 연인** — 헤더(뒤로) 좌우 24, `HCtaScaffold(bottomInset = 40.dp)`, 내용 간격 20, 위 12
- 제목 "새 그룹 만들기"(26 Bold), 부제(15 textSecondary): 연인이면 "만든 뒤 초대 코드로 연인 1명을 초대할 수 있어요." 아니면 `"만든 뒤 초대 코드로 최대 {maxMembers-1}명을 더 초대할 수 있어요."`
- `HTextField`: 라벨 "그룹 이름", placeholder "그룹 이름을 입력해 주세요", `maxLength = 16`, `textSize = 17`, 라벨-입력 간격 10(현재 `HTextField` 는 8 고정 — 필요하면 `labelSpacing` 파라미터 추가, 기본값 8 유지).
- "어떻게 불러드릴까요?" 아님 — 섹션 라벨은 **"어떤 사이인가요?"**(14 SemiBold textSecondary) 아래 `HChip` 5개(연인·친구·가족·웬수·그저그런사이, `Relationship.entries` 순서) 줄바꿈 배치(간격 8, `FlowRow`). 기본 선택 = 친구. 칩 아래 미리보기 박스: 테두리 `line`, 모서리 `HRadius.button`(16), 좌우 16·상하 14. 왼쪽에 "홈 화면에서 이렇게 보여요"(13 SemiBold ink) / `"캐릭터 사이에 {decorPhrase} 떠요"`(12 textSecondary), 오른쪽에 `MemberRow([몽실이, 곰돌이], relationship, avatarSize = 38)`.
- 연인 선택 시에만(애니메이션: 위에서 페이드 인) "우리의 1일" 섹션: 라벨(14 SemiBold textSecondary), 버튼 모양 필드(높이 54, 모서리 14, 테두리 `border`, 좌우 16): 왼쪽 날짜 텍스트(17, 형식 `yyyy. M. d.` — iOS `year().month(.defaultDigits).day()` ko_KR, 보고서에 "iOS 대조 필요"), 오른쪽 `HIcon.CALENDAR`(20). 누르면 날짜 시트. 아래 설명 "이 날부터 함께한 날을 세고, 100일·1주년 같은 기념일엔 특별한 주제가 와요."(13, 줄 높이 1.5, textSecondary). 기본값 = 오늘(서울 날짜).
- 최대 인원 박스(surface, 모서리 16, 좌우 18·상하 16): 왼쪽 "최대 인원"(15 SemiBold) / 연인 "연인 그룹은 둘만 함께해요" 아니면 `"나를 포함해 2~{maxMembers}명"`(13 textSecondary), 오른쪽 `"{maxMembers}명"`(17 Bold).
- CTA "그룹 만들고 초대 코드 받기": 이름 trim 이 비면 비활성. 누르면 (trim 이름, 관계, 연인이면 날짜 아니면 null) 전달.
- **날짜 시트(`StartDateSheet`)**: 헤더 "우리의 1일" + 오른쪽 "완료"(16 SemiBold ink), 달력 선택(오늘 이후 선택 불가), tint ink, 한국어, 높이 약 480, 드래그 핸들 표시. Android: `ModalBottomSheet` + Material3 `DatePicker`(+`SelectableDates`) — 색은 `DatePickerDefaults.colors` 로 ink/accent 토큰으로 덮어쓴다. **Material3 DatePicker 는 UTC 자정 밀리초를 쓴다** → 서울 날짜 ↔ UTC 자정 변환을 한 곳(작은 헬퍼 + 단위 테스트)에서 처리: 초기값 = `LocalDate.atStartOfDay(ZoneOffset.UTC)`, 선택 가능 = 밀리초 ≤ 오늘(서울) 날짜의 UTC 자정, 읽을 때 `Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()`.

**03 초대 코드 참여** — 헤더(뒤로), `HCtaScaffold(bottomInset = 40.dp)`, 내용 간격 14, 위 16
- 제목 "초대 코드 입력"(26 Bold), 부제 "친구에게 받은 6자리 코드를 입력해 주세요."(15 textSecondary)
- 코드 칸 6개: 간격 8, 높이 58, 폭 균등, 모서리 14. 입력된 칸/비어 있는 칸은 surface, **지금 입력할 칸**(= 포커스 중이고 `index == 글자 수`)은 흰 바탕 + ink 테두리 1.5, 글자는 24 Bold ink 가운데. 칸 영역을 누르면 키보드 포커스. 실제 입력은 **보이지 않는 입력창**(`BasicTextField`, 투명/크기 최소)이 받는다.
- 입력 규칙: 영문 대문자 + 숫자만, 6자리까지(`InviteCode.sanitize`), 키보드는 ASCII 위주(`KeyboardType.Ascii`, 자동 대문자 `Characters`, 자동 수정 끔). 코드가 바뀔 때마다 조회(`lookUpInvite`, 6자리가 아니면 미리보기·오류 모두 비움). 6자리가 되면 포커스 해제(키보드 숨김).
- 칸 아래 한 줄: 오류가 있으면 오류 문구(13 danger — `InviteLookupError.message`) 아니면 안내 "링크로 받았다면 링크를 누르면 자동으로 입력돼요."(13 textSecondary).
- 미리보기 카드(조회 성공 시, 페이드): 테두리 `line`, 모서리 18, 안쪽 18, 간격 12. "참여할 그룹"(13 SemiBold textSecondary), 그룹 이름(18 Bold 한 줄) + `"{관계} · {현재인원} / {최대인원}명 참여 중"`(14 textSecondary), `MemberRow(preview.members, relationship, avatarSize = 32)`.
- CTA "참여하기": 미리보기가 없으면 비활성. 누르면 코드 전달 → `store.join(code)`.
- `initialCode` 파라미터(딥링크용): 값이 있고 입력이 비어 있으면 `InviteCode.sanitize(initialCode)` 로 채운다. 이번엔 호출처가 없지만 라우트 인자로 구현해 둔다. 처음 나타날 때 6자리가 안 찼으면 키보드 포커스, 찼으면 조회만.

**16 그룹 정보** — 헤더 "그룹 정보"(가운데 제목, 뒤로), 좌우 20
- 스크롤: 머리(위 12, 아래 20): 그룹 이름(24 Bold) + `"{관계} · {생성일}부터 · 미션 {missionCount}회"`(14 textSecondary, 생성일 형식 `yyyy. M. d.`).
- 초대 코드 카드(surface, 모서리 20, 안쪽 18, 간격 14): "초대 코드"(13 SemiBold textSecondary) + 코드(26 Bold, 글자 간격 3.12sp, 길게 눌러 선택 가능하면 좋음) / 오른쪽 복사 버튼(원 44, 흰 배경, 테두리 `border`, `HIcon.COPY` 20, 접근성 라벨 "초대 코드 복사"). 아래 `HButton("초대 링크 공유하기")` → 시스템 공유(`ACTION_SEND` text/plain): 본문은 `"HA'WE '{그룹명}' 그룹에 초대할게요. 초대 코드: {코드}\n{AppLinks.invite(코드)}"`.
- 복사: 클립보드에 코드 복사 + 토스트 "초대 코드를 복사했어요". Android 13+ 는 시스템이 복사 안내를 띄우므로 SDK 33 미만에서만 토스트(ANDROID.md §5).
- "멤버"(16 Bold) + 오른쪽 `"{N} / {최대}명"`(13 textSecondary) (위 24, 아래 6). 멤버 줄(높이 52, 아바타 36, 간격 12): 닉네임(15 Medium, 한 줄) + 배지(12 textSecondary): 내가면 "나", 방장이면 "방장", 둘 다면 "나 · 방장" (둘 다 아니면 없음). 내 줄 오른쪽 "내 프로필 편집" 버튼(13 SemiBold, 좌우 14, 높이 34, 알약 테두리 `border`) → 16-1.
- 설정 영역(위 구분선 `line` 1dp + 위 12): `HListRow("주제 도착 시간", value = 시간 문자열, showsChevron = 방장)` — 방장만 시트 열기, 방장이 아니면 눌러도 아무 일 없음; `HListRow("알림", value = "켜짐"/"꺼짐")` — 누르면 즉시 토글 + 토스트 "이 그룹 알림을 켰어요"/"이 그룹 알림을 껐어요"; "그룹 나가기"(15 danger, 높이 52, 왼쪽 정렬) → `HAlert`.
- 시간 문자열: 한국어 `a h:mm`(예: `오전 9:00`). 
- 나가기 알림: 제목 "그룹에서 나갈까요?", 취소 / "나가기"(danger). 메시지: `"'{그룹명}'에서 나가도 내가 올린 작품과 댓글은 그룹에 남아요. 다시 들어오려면 초대 코드가 필요해요."` + (내가 방장이고 멤버가 2명 이상이면) `"\n방장은 가장 먼저 들어온 멤버에게 넘어가요."`.
- 토스트: 하단 24 위, 1.6초 후 사라짐(`HToast`).
- 그룹이 사라진 직후(나가기 처리 중 재구성)에도 화면이 죽지 않아야 한다(iOS 는 빈 `GroupDetail` 로 그림) → `store.group(id) == null` 이면 빈 화면을 그리거나 즉시 뒤로 가도록 가드.
- **주제 도착 시간 시트(`TopicTimeSheet`)**: 헤더 "주제 도착 시간" + 오른쪽 "완료"(16 SemiBold ink), 안내 "매일 이 시간에 그룹의 새 미션이 열려요."(13 textSecondary, 위 4), **휠 시간 선택**(오전/오후 · 시 · 분), 높이 약 340, 드래그 핸들. 시트가 열릴 때 현재 `topicTime` 으로 초기화, "완료"에서만 `store.setTopicTime` 저장(시트를 그냥 내리면 저장 안 함). 결정(ANDROID.md §8-4 의 추천을 따름): **휠은 직접 구현** — `LazyColumn` + `rememberSnapFlingBehavior`, 한 줄 높이 40, 5줄 보이기, 가운데 줄 강조(ink, 나머지 textTertiary), 열 3개: [오전, 오후] / [1~12] / [00~59]. 구현이 불안정하면 Material3 `TimePicker`(입력 모드 아님) 로 대체하고 보고서에 이유를 적는다.

**16-1 그룹 내 프로필** — 헤더 "그룹 내 프로필"(뒤로), 좌우 24, `HCtaScaffold`, 내용 간격 18, 위 8
- 설명 `"'{그룹명}' 그룹에서만 이렇게 보여요. 다른 그룹에는 영향이 없어요."`(14, 줄 높이 1.5, textSecondary)
- "기본 프로필 사용" 카드(surface, 모서리 16, 좌우 16·상하 14): 제목(15 SemiBold ink) + `"마이페이지의 프로필({기본 프로필 캐릭터 이름 · 닉네임})을 그대로 써요"`(12 textSecondary) + 오른쪽 `HToggle`(접근성 라벨 "기본 프로필 사용").
- 미리보기 줄: `HAvatar(size 80, characterScale 0.8)` + "그룹 멤버에게 보이는 모습"(13 textSecondary) / 닉네임(20 Bold, 한 줄; 비면 공백 한 칸). 기본 사용 중이면 기본 프로필을, 아니면 편집 중인 아바타·닉네임.
- 기본 사용을 **끄면** 아래가 나타남(애니메이션): `CharacterPicker(cellSize 64, characterSize 48, columnSpacing 22, rowSpacing 10)` + `HTextField` 라벨 "그룹 내 닉네임", placeholder "닉네임을 입력해 주세요", `maxLength 10`, `textSize 17`.
- 초기 상태: `groupProfile == null` 이면 기본 사용 ON, 아니면 OFF + 아바타·닉네임 = 현재 `displayed`. 
- CTA "저장": 기본 사용 ON 이거나 trim 닉네임 2..10자일 때 활성. 저장 = `store.setMyGroupProfile(if (기본 사용) null else Profile(avatar, trim 닉네임), groupID)` 후 뒤로.

## Root Cause / Key Implementation Points

1. **루트 목적지가 Start/Home 둘 다를 담당**: `MainRoute.Home`(루트, `startDestination`) 컴포저블이 `store.groups.isEmpty()` 로 `StartScreen(showsBack=false)` 또는 `HomeScreen` 을 그린다(iOS `Group { if store.groups.isEmpty ... }`). `store.groups` 는 Compose 스냅샷 상태를 읽으므로 그룹 생성/참여/나가기 후 자동 전환된다. 3단계의 `MainRoute.Start` 는 의미가 바뀐다 — **루트용은 `Home`, 홈에서 + 로 여는 01 은 `Start`(showsBack=true)**.
2. **라우트** (`@Serializable`, UUID 는 문자열로 직렬화):
   ```kotlin
   sealed interface MainRoute {
       @Serializable data object Home : MainRoute          // 루트: 그룹 0개면 01 시작, 아니면 04 홈
       @Serializable data object Start : MainRoute         // 홈의 + 로 연 01 (뒤로 가기 있음)
       @Serializable data object CreateGroup : MainRoute
       @Serializable data class JoinGroup(val code: String? = null) : MainRoute
       @Serializable data class GroupInfo(val groupId: String) : MainRoute
       @Serializable data class GroupProfile(val groupId: String) : MainRoute
   }
   ```
   `UUID.fromString` 이 실패하면(비정상 인자) 화면에서 즉시 뒤로 가도록 방어.
3. **백스택 조작 (iOS 와 1:1)**
   - 02 완료: `nav.navigate(MainRoute.GroupInfo(id)) { popUpTo<MainRoute.Home>() }` (Home 은 남기고 그 위의 02·Start 제거).
   - 03 완료: `nav.popBackStack<MainRoute.Home>(inclusive = false)`.
   - 16 나가기: `nav.popBackStack<MainRoute.Home>(inclusive = false)` 후 `store.leave(id)`.
   - 이 호출들은 `launchSingleTop` 과 더블 탭 방지에 주의(버튼 두 번 눌러 02 가 두 번 처리되지 않게, 아래 Edge Cases).
4. **상태 소유**: 화면 입력 상태는 화면 안(`remember`/`rememberSaveable`) + 순수 Kotlin 상태 클래스(테스트용). 별도 ViewModel·DI 추가 금지. `store` 변경 후 화면은 `store.group(id)`/`store.summaries` 를 컴포지션에서 읽어 자동 갱신(불변 모델 규칙 유지).
5. **토스트 공통화**: 3단계에서 `StartScreen` 이 `showToast: Boolean` + `LaunchedEffect(delay)` 로 직접 구현했다. 이번에 홈·16 도 필요하므로 작은 상태 홀더를 만든다 — `designsystem/components/HToastState.kt`: `class HToastState { var message: String?; fun show(text: String) }`(마지막 호출 기준 1.6초 후 사라짐) + `@Composable fun rememberHToastState()` + `@Composable fun BoxScope.HToastHost(state)` (하단 가운데, 아래 여백은 호출부 지정). `StartScreen` 도 이것을 쓰도록 바꾼다(동작은 동일: "준비 중이에요", 단 이제 **마이페이지 버튼에서만** 뜨고 만들기/참여는 실제로 이동).
6. **날짜·시간 포맷 헬퍼**: `feature/groups/GroupFormat.kt` 에 순수 함수로 — `homeDateText(now: Instant)`, `dotDate(LocalDate)`(`yyyy. M. d.`), `dotDate(Instant)`(서울), `topicTimeText(LocalTime)`(`오전 9:00`), `memberBadge(member)`, `leaveMessage(group)`, `groupCardSubtitle(summary, now)`. 단위 테스트 대상. `Locale.KOREAN` 고정, 시간대 `HaweTime.SEOUL` 고정(기기 설정 무시).
7. **초대 코드 입력 안정성**: 이전 Android 시도에서 "입력 중 대문자 변환으로 키보드 조합이 꼬여 글자가 빠지는 문제"가 있었다. `BasicTextField` 를 `String` 오버로드로 쓰고 변환값을 되돌려 쓰면 비슷한 문제가 날 수 있다. **`TextFieldValue` 오버로드**를 쓰고, 입력이 바뀌면 `sanitize` 한 결과만 상태로 저장하되 sanitize 결과와 입력이 다를 때는 `TextFieldValue(text = sanitized, selection = TextRange(sanitized.length))` 로 조합 영역을 정리해 새로 쓴다. 키보드는 `KeyboardType.Ascii` + `KeyboardCapitalization.Characters` + `autoCorrectEnabled = false`. 한글 키보드 상태, 소문자, 붙여넣기(`k7-q2 px99`)에서 글자가 빠지거나 중복되지 않는지 폰 확인 항목에 포함.
8. **개발용 접근 경로**: 05 가 없어서 16 에는 "내가 만든 그룹"(02 직후)로만 갈 수 있다. 참여한 그룹(예: 체험 코드 `K7Q2PX`)의 16(방장이 아닌 경우의 표시)을 보려면 **디버그 빌드에서만** 홈 카드를 길게 누르면 16 으로 이동하게 한다(`FLAG_DEBUGGABLE` 가드, `// 개발용, 5단계에서 삭제` 주석, 몇 줄). 일반 탭은 토스트.

## Files / Functions to Change

새 파일 (`android/app/src/main/java/com/sapphire/hawe/` 기준):
- `feature/groups/HomeScreen.kt` — 04 + `GroupCard`
- `feature/groups/CreateGroupScreen.kt` — 02/02-1 + `StartDateSheet` + `CreateGroupState`(이름 trim·`canCreate`·연인 여부 규칙, 순수 클래스)
- `feature/groups/JoinGroupScreen.kt` — 03 + `GroupInviteCard` + `JoinGroupState`(코드 입력→조회, `sanitize` 연동, 순수 클래스; `lookUp` 은 `(String) -> InviteLookupResult` 를 받아 스토어 의존 없이 테스트)
- `feature/groups/GroupInfoScreen.kt` — 16 + `TopicTimeSheet`
- `feature/groups/GroupProfileScreen.kt` — 16-1 + `GroupProfileState`(초기값·`canSave`·저장될 `Profile?` 계산, 순수 클래스)
- `feature/groups/GroupFormat.kt` — 위 포맷 헬퍼
- `designsystem/components/HToastState.kt` — 토스트 상태 홀더 + `HToastHost`
- `designsystem/components/WheelPicker.kt` — 휠 열 하나(`WheelPicker(items: List<String>, selectedIndex, onSelected, ...)`) + (필요 시) 시간 휠 조합 `TimeWheel(time: LocalTime, onChange)`. `TopicTimeSheet` 에서만 쓰지만 디자인 시스템 컴포넌트로 둔다.

수정할 파일:
- `app/MainNavigation.kt` — `MainRoute` 확장(위 정의), `MainNavHost(session, store)` 에 목적지 6개 연결, 백스택 규칙(Root Cause 3), `Home` 루트에서 Start/Home 분기. 시그니처에 `store` 추가 → `app/RootScreen.kt` 의 호출부도 수정(`MainNavHost(viewModel.appSession, viewModel.dataStore)`).
- `feature/groups/StartScreen.kt` — 내부 `showToast` 제거, `HToastState` 사용. 만들기/참여 콜백은 이동만(토스트 없음), `onMyPage` 만 "준비 중이에요" 토스트. 나머지(디자인·디버그 로그아웃)는 유지.
- `designsystem/components/HToggle.kt` — `contentDescription: String? = null` 파라미터 추가(`Modifier.semantics { contentDescription = ...; role = Role.Switch; toggleableState }`). 기본값으로 기존 호출 유지.
- `designsystem/components/HTextField.kt` — 02 의 라벨 간격 10 이 필요하면 `labelSpacing: Dp = 8.dp` 파라미터 추가(기본값 8 유지). 03 은 별도 숨은 입력이라 `HTextField` 를 쓰지 않는다.
- 변경 금지(원칙): `core/data/DataStore.kt`, `core/model/*`, `core/AppSession.kt`, `app/AppViewModel.kt`, `app/AuthNavigation.kt`, `feature/auth/*`. 필요해 보이면 최소 변경 후 보고서에 이유를 적는다(특히 `DataStore` 는 iOS 와 같은 API 유지).

테스트 (`app/src/test/java/com/sapphire/hawe/feature/groups/`):
- `GroupFormatTest.kt`, `CreateGroupStateTest.kt`, `JoinGroupStateTest.kt`, `GroupProfileStateTest.kt`, 날짜 시트 변환 헬퍼 테스트(`StartDateConversionTest.kt` 또는 `GroupFormatTest` 안)

## Implementation Steps

0. **환경** (3단계와 동일, 이미 준비됨): `cd C:\hawe\android`, `$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot"`, SDK `C:\Android\Sdk`(`local.properties` 있음). 빌드 `.\gradlew.bat assembleDebug`, 테스트 `testDebugUnitTest`, `lintDebug`, 설치 `installDebug`. 폰은 USB 연결(`C:\Android\Sdk\platform-tools\adb.exe devices` 에 `device`). 폰이 안 보이면 설치만 건너뛰고 보고서에 적는다.
1. `HToastState` / `HToastHost`, `HToggle` 접근성 라벨, (필요 시) `HTextField.labelSpacing` 추가. 갤러리·기존 화면이 그대로 컴파일되는지 확인.
2. `GroupFormat.kt` + 단위 테스트(날짜·시간·배지·나가기 메시지·카드 부제).
3. `MainNavigation.kt` 라우트·NavHost 뼈대 + `Home` 루트 분기 + `StartScreen` 토스트 정리. 이 시점에 빌드가 되고 01 → (만들기/참여 버튼은 아직 빈 화면) 이 깨지지 않게 단계별로 목적지를 채운다.
4. `HomeScreen` (카드, 헤더, 토스트, `LaunchedEffect` 새로고침, 디버그 길게 누름) — 임시로 `store` 에 체험 그룹을 `join("K7Q2PX")` 해서 확인(디버그 빌드에서 03 으로도 가능).
5. `CreateGroupState`(+테스트) → `CreateGroupScreen` + `StartDateSheet`(날짜 변환 헬퍼 + 테스트).
6. `JoinGroupState`(+테스트) → `JoinGroupScreen` (숨은 입력, 칸, 미리보기).
7. `WheelPicker`/`TimeWheel` → `GroupInfoScreen`(+`TopicTimeSheet`, 공유·복사·나가기 알림) 
8. `GroupProfileState`(+테스트) → `GroupProfileScreen`.
9. `MainNavigation.kt` 에 모든 콜백·백스택 규칙 연결, 가드(없는 groupId, 더블 탭) 확인.
10. `assembleDebug` / `testDebugUnitTest` / `lintDebug` / `installDebug` 실행, 결과를 `.agent/report.md` 에 기록.

## Constraints

- 변경 범위는 `android/` 안. `ios/`, `src/`(백엔드), `cordova/`, `frontend/`, `docs/`, 루트 `.gitignore`, `AGENTS.md`/`CLAUDE.md` 수정 금지. git 상태를 바꾸는 명령(commit/checkout/stash/reset/clean 등) 금지.
- iOS 파일은 읽기 전용. 문구·수치는 iOS 와 동일(위에 모두 옮겼으나 의심되면 iOS 파일이 기준).
- **새 의존성 추가 금지**(Hilt/Room/Retrofit/Accompanist 등). Compose Material3 `ModalBottomSheet`/`DatePicker`(BOM 2025.10.01 에 포함)와 이미 있는 라이브러리만 사용. `@OptIn(ExperimentalMaterial3Api::class)` / `@OptIn(ExperimentalLayoutApi::class)` 는 필요한 곳에만.
- 서버 호출·영속화 없음. 앱을 끄면 그룹이 사라지는 것은 iOS 와 같다.
- 05 미션·17 마이페이지·딥링크 처리 구현 금지(버튼은 토스트). `MainRoute` 에 아직 쓰지 않는 경로를 미리 정의하지 않는다(위 6개만).
- `DataStore`/모델의 시그니처·불변 규칙 변경 금지. 새 모델에 `var`/`MutableList` 금지.
- Material 기본 컴포넌트(`Switch`, `TextButton`, `AlertDialog` 등)를 시각 요소로 쓰지 않는다(ANDROID.md §3). 시트/날짜 달력 같이 직접 만들기 비싼 것만 Material3 허용, 색은 토큰으로 덮어쓴다.
- 코드 주석은 iOS 처럼 한국어로 짧게. 화면 문구는 Kotlin 코드 안 한국어 리터럴(문자열 리소스화는 별도 작업).

## Edge Cases

- **루트 전환**: 그룹 0개 → 02 로 만들면 곧바로 16 이 열리고, 뒤로 가면 홈(그룹 1개). 03 참여 후 홈. 마지막 그룹을 16 에서 나가면 루트가 01 시작으로 바뀐다(백스택은 루트 하나).
- **더블 탭**: 02 "그룹 만들고 초대 코드 받기" / 03 "참여하기" / 16 "나가기" 확인 버튼을 빠르게 두 번 눌러도 그룹이 두 개 만들어지거나 내비게이션이 두 번 일어나지 않게 한다(첫 클릭에서 처리 플래그 또는 `launchSingleTop`; `store.join` 은 두 번째에 `alreadyMember` 라 null 반환 → 무시).
- **02**: 이름이 공백만이면 비활성. 16자 초과 입력/붙여넣기는 16자로 잘림. 연인에서 다른 관계로 바꾸면 날짜 섹션이 사라지고 전달 값은 null. 연인으로 두고 날짜를 고른 뒤 만들면 `startDate` 가 `LocalDate` 로 전달. 날짜 시트는 미래 날짜 선택 불가, 기본 오늘(서울). 시트를 바깥 터치/내려서 닫으면 마지막으로 확정한 값 유지(iOS 는 선택 즉시 바인딩이 갱신된다 — Android 는 시트 안에서 고른 값을 "완료"로 확정해도 되고 즉시 반영해도 된다. 둘 중 하나로 통일하고 보고서에 기록).
- **03**: 6자 미만 → 오류·미리보기 없음, CTA 비활성. 소문자 `k7q2px`→`K7Q2PX`. 붙여넣기 `k7-q2 px99`→`K7Q2PX`. 한글 입력은 무시. `ZZZZZZ`→"초대 코드를 찾을 수 없어요. 다시 확인해 주세요.", `FULL00`→"이 그룹은 정원이 다 찼어요.", 이미 참여한 코드→"이미 참여 중인 그룹이에요.". 코드를 지우면(5자) 오류가 사라진다. 6자리 입력 후 한 글자 지우면 다시 포커스(키보드)가 필요하다 — 칸 영역 탭으로 다시 열림. 화면 회전은 세로 고정. 코드 칸을 눌러도 이미 6자리면 키보드 열림 후 지우기 가능.
- **04**: 그룹이 정확히 1개일 때 문구 "그룹 1개". 연인 그룹의 "함께한 지 N일"(시작일=1일째, 서울 날짜). 멤버가 6명인 카드에서 `MemberRow` 가 가로로 넘치지 않는지(아바타 34 × 6 + 장식 20 × 5 − 겹침 ≈ 280dp, 폭 360 − 40 − 36 = 284dp 근처 → 작은 화면에서 넘치지 않는지 확인, 넘치면 카드 안에서 가로 클립 대신 `horizontalScroll` 또는 크기 유지 + 잘림 방지를 보고서에 기록). 이름이 긴 그룹은 한 줄 말줄임. 주제가 긴 경우 줄바꿈.
- **16**: 방장이 아닌 멤버는 "주제 도착 시간" 꺾쇠 없음 + 눌러도 반응 없음. 방장이면서 멤버 1명이면 나가기 메시지에 방장 이전 문구 없음. 내 줄에만 "내 프로필 편집". 알림 토글 연타해도 마지막 상태 기준 토스트. 시간 시트에서 "완료" 없이 닫으면 변경 없음. `FULL00` 처럼 6명 그룹의 멤버 목록은 스크롤.
- **16-1**: 기본 사용 ON 상태에서 저장 → `groupProfile = null`. OFF + 닉네임 1자 → 저장 비활성. 닉네임 앞뒤 공백 trim 후 2..10. OFF 로 바꿨다가 다시 ON 으로 해도 편집 중이던 값은 화면 안에서 유지(저장 전엔 DataStore 변경 없음). '내 사진' 선택 후 저장하면 `AvatarContent.Photo` 가 그룹 프로필로 저장(캐시 폴더 파일 Uri — `CharacterPicker` 가 이미 복사해 준다). 키보드가 열려도 CTA 가 가려지지 않음(`HCtaScaffold`).
- **나가기 직후**: `popBackStack` 과 `store.leave` 사이에 `GroupInfoScreen` 이 다시 그려질 수 있으므로 `store.group(id) == null` 가드(빈 화면 또는 무동작)가 필요하다. 16-1 에서 그룹이 사라지는 경우는 없음.
- **프로세스 복원/없는 id**: 라우트 인자의 groupId 가 스토어에 없으면(프로세스 종료 후 복원 등) 즉시 루트로 돌아가거나 빈 화면 — 크래시 금지.
- **시스템 바**: 3버튼 내비게이션에서 하단 CTA·마지막 목록이 가려지지 않는다(홈 목록 마지막 카드 아래 여백, 16 설정 목록 아래 여백에 내비게이션 바 inset 반영).
- **키보드**: 02·03·16-1 에서 Next/Done 이 기본 동작(3단계 수정)대로 작동.
- **서울 시간**: 홈 날짜·생성일·연인 일수·주제 시간 표시 모두 기기 시간대와 무관하게 서울 기준.

## Regression Risks

- `StartScreen` 변경(토스트 → `HToastState`): 3단계에서 동작하던 01 화면의 디자인·디버그 로그아웃이 그대로여야 한다. 만들기/참여 버튼 동작이 "토스트"에서 "이동"으로 바뀌는 것이 의도된 변경.
- `MainRoute.Start` 의미 변경(루트 → 홈에서 연 01): 3단계에서 `startDestination = MainRoute.Start` 였던 곳을 `Home` 으로 바꾸는 것을 빠뜨리면 로그인 후 바로 뒤로 가기 가능한 01 이 나온다.
- `HToggle`/`HTextField` 파라미터 추가: 기본값으로 갤러리·기존 화면 영향 없어야 함.
- `Home` 루트가 `store.groups` 를 구독하므로, DataStore 변경(제출·좋아요 등 5단계 이후) 때 홈이 과도하게 재구성되는지 — 이번엔 문제 없음.
- 백스택 정리(`popUpTo`, `popBackStack<Home>`)는 `AuthNavHost` 와 별개 NavController 라 로그아웃 흐름과 충돌하지 않는다. 디버그 로그아웃 후 재로그인 시 `MainNavHost` 가 새로 만들어져 백스택이 초기화되어야 한다.
- 업로드된 사진(프로필)이 캐시 폴더에 쌓이는 것은 기존 `CharacterPicker` 동작 — 변경 없음.

## Tests Required

JVM 단위 테스트 (`app/src/test`, JUnit4; 3단계와 동일). 기존 17개가 계속 통과해야 한다.
- `GroupFormatTest`: `topicTimeText` (09:00→`오전 9:00`, 00:00→`오전 12:00`, 12:00→`오후 12:00`, 13:05→`오후 1:05`), `dotDate` (2026-06-02 → `2026. 6. 2.`, 서울 자정 경계 Instant 입력), `memberBadge` (나만/방장만/둘 다/둘 다 아님 → null/"나"/"방장"/"나 · 방장"), `leaveMessage`(방장+멤버 2명 이상일 때만 이전 문구), `groupCardSubtitle`(비연인 "친구 · 4명", 연인+시작일 "연인 · 2명 · 함께한 지 128일"), `homeDateText` (고정 Instant → `10월 12일 월요일` 형식, 기기 기본 시간대를 UTC 로 바꿔도 동일).
- `CreateGroupStateTest`: 기본 관계 = 친구, 이름 공백만 → `canCreate` false, 연인 선택 시 날짜 섹션 표시 플래그/최대 인원, 연인 아닌 관계의 전달 `startDate` = null, 연인이면 선택 날짜, 이름 16자 제한, 부제 문구(연인/비연인).
- `StartDateConversionTest`(또는 `GroupFormatTest`): 서울 날짜 → DatePicker UTC 밀리초 → 읽기 왕복 동일, 선택 가능 판정(오늘 O, 내일 X), 서울 자정 직후/직전 경계.
- `JoinGroupStateTest`: 입력 정규화(`k7q2px`, `k7-q2 px99`, 한글 포함), 6자 미만이면 preview·error 모두 null, 6자리에서 조회 결과 Success→preview, 실패 3종→문구 일치, 코드를 지우면 초기화, `initialCode` 적용 규칙(입력이 비었을 때만).
- `GroupProfileStateTest`: `groupProfile == null` 이면 기본 사용 ON·`canSave` true·저장값 null, OFF 에서 닉네임 1자/2자/10자/11자(입력 제한)·공백 trim 경계, 저장값 `Profile(avatar, trim 닉네임)`, 미리보기가 모드에 따라 기본/편집값.
- (선택) `DataStoreTest` 에 `setMyGroupProfile(null)` 로 되돌리기, 마지막 그룹 `leave` 후 `groups` 비어 있음 확인 — 이미 비슷한 케이스가 있으면 중복 추가하지 않는다.

화면 UI 테스트(Compose test)는 만들지 않는다 — 폰 확인으로 대체.

## Validation Steps

Codex 가 직접 실행: `.\gradlew.bat assembleDebug testDebugUnitTest lintDebug installDebug` (JDK 17 지정). 기준: 빌드 성공, 테스트 전부 통과(기존 17 + 신규), lint 새 오류 0(경고 건수 보고), 폰 설치 성공 후 `adb shell am start -n com.sapphire.hawe/.MainActivity`.

사용자가 폰에서 확인할 시나리오 — 보고서에 그대로 적을 것:
1. 가입(A2→A3) → **01 시작** → "초대 코드로 참여하기" → **03**: 칸 6개, 칸을 누르면 키보드. `K7Q2PX` 입력 → "참여할 그룹" 카드(컴공 동기 · 친구 · 4 / 6명 참여 중) → "참여하기" → **04 홈**: 카드 1개(내 미션 남음 배지, 멤버 5명, `1 / 5명`이 아니라 오늘 제출 수 / 멤버 수).
2. 03 오류: `ZZZZZZ`(없음), `FULL00`(정원 초과), 참여 후 다시 `K7Q2PX`(이미 참여). 소문자·붙여넣기·한글 키보드에서 글자가 빠지지 않는지.
3. 홈 + 버튼 → **01(뒤로 가기 있음)** → "새 그룹 만들기" → **02**: 칩 5개, 친구가 기본, 미리보기 박스에서 관계별 장식이 바뀜, 연인 선택 시 "우리의 1일"+달력 시트(미래 날짜 선택 불가)와 최대 인원 2명, 이름 입력 전 CTA 비활성 → 만들기 → **16 그룹 정보**(초대 코드, 멤버 1명 "나 · 방장") → 뒤로 → 홈에 카드 2개.
4. **16**: 코드 복사(클립보드), 초대 링크 공유(공유 시트에 텍스트+링크), 주제 도착 시간 시트(휠, 완료 시 값 변경), 알림 눌러 켜짐↔꺼짐 + 토스트, "내 프로필 편집" → **16-1**: 기본 사용 토글 끄면 캐릭터·닉네임 입력이 나타남, 저장 후 16 멤버 목록에 그룹 내 닉네임 반영.
5. **16 그룹 나가기**: 알림창 문구, 나가면 홈(남은 그룹 있으면) / 마지막 그룹이면 01 시작.
6. 디버그: 홈에서 체험 그룹 카드를 길게 눌러 **16**(방장이 아닌 경우 주제 도착 시간 꺾쇠 없음, 멤버 5명).
7. 홈 카드 탭 / 사람 아이콘 → "준비 중이에요" 토스트.
8. 3버튼 내비게이션과 제스처 내비게이션에서 하단 CTA·마지막 카드가 가려지지 않는지, 키보드가 올라와도 CTA 가 보이는지.
9. 앱 재실행 시 A1 로 복귀(영속화 없음).

## Potential Merge Conflicts

- `android/` 만 수정. 백엔드·iOS·Cordova 와 겹치지 않는다.
- 공유 파일 수정: `app/MainNavigation.kt`, `app/RootScreen.kt`, `feature/groups/StartScreen.kt`, `designsystem/components/HToggle.kt`, `HTextField.kt`. 이후 5단계(05 미션)가 `MainNavigation.kt` 와 `HomeScreen`(카드 탭 연결) 을 다시 수정하므로 이 단계를 먼저 병합한다.
- 작업 트리에 3단계(와 그 이전)의 미커밋 변경이 남아 있을 수 있다. `git status` 로 확인만 하고 커밋하지 않는다.
- `.agent/report.md` 는 Codex 소유. 기준 iOS 커밋(`git log -1 --format=%h -- ios/`), 빌드·테스트·lint·설치 결과, iOS 와 다르게 한 부분(날짜/시간 문자열 대조 필요 항목, 시트 확정 방식, 휠 구현/대체 여부), 폰 확인 체크리스트를 적는다.
