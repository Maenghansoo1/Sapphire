# 개발자 모드 ② Android — 숨김 진입(버전 10탭) + 개발자 도구 화면 + 원클릭 유저 전환 + 앱 시계

> 이 파일은 **서버 작업(`.agent/plan.md` — `/api/dev/**` API)이 끝난 뒤** `.agent/plan.md` 로 복사해 실행한다(한 번에 한 계획). 서버 API 계약은 `.agent/plan.md` 의 "API 계약" 표가 정본이다. 서버가 아직 없으면 이 작업은 시작하지 않는다.
> 범위: **`android/` 만**. 사용자 결정(확정): 서버 시계까지 변경(앱은 같은 오프셋을 표시용으로 반영), 유저 전환은 서버 시드 계정, **release 빌드에서도 열림**, **안전장치 금지**(빌드 타입/플래그로 숨기지 않는다 — 10번 탭이 유일한 진입 조건).
> 전제: A2·A3·A9·A6 변경이 worktree 에 미커밋으로 섞여 있을 수 있다(`MainNavigation.kt`, `AppViewModel.kt`, `RootScreen.kt`, `MyPageScreen.kt`, `MissionScreen.kt` 등). **덮어쓰거나 되돌리지 말 것.**
> 빌드/테스트: JDK 21 권장(없으면 JDK 17 — 셸의 `JAVA_HOME` 이 JDK 25 면 Gradle 이 실패하므로 `C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot` 로 지정). `android\gradlew.bat :app:testDebugUnitTest --offline`.

## Goal

1. **마이페이지 → 앱 정보**에서 "버전 1.0.0" 글자를 10번 누르면 개발자 모드가 켜진다(유지됨, 앱 재실행 후에도).
2. 개발자 모드에서:
   - 마이페이지에서 **원클릭 유저 전환**(서버 시드 계정 목록, 탭 한 번).
   - **개발자 도구 화면**: 상태 표시, 서버 시계 변경(앱 시계도 동기), 미션 생성(슬롯 강제 발행, 틀 선택), 마감 당기기, 대리 제출/콕/반응/댓글, 시드 데이터 일괄 생성/초기화, 인사이트 강제 재생성, 그룹 설정(하루 미션 수·발행 시각), 로컬 초기화·그룹 삭제, 네트워크 오류 주입.
   - 05(미션) 화면에도 개발자용 **"미션 생성"** 버튼.

## Findings (코드로 확인함)

경로 접두 `J/` = `android/app/src/main/java/com/sapphire/hawe/`, 테스트 `T/` = `android/app/src/test/java/com/sapphire/hawe/`.

- **앱 정보 화면** `J/feature/mypage/AppInfoScreen.kt`: `Text("버전 $version", style = HType.plex(14), color = HColors.textSecondary)` 가 "버전 1.0.0" 표시(`rememberAppVersionName()`). 여기에 탭 카운터를 건다.
- **마이페이지** `J/feature/mypage/MyPageScreen.kt`: 시그니처 `MyPageScreen(session, store, calendar, onBack, onProfileEdit, onNotifications, onUserInfo, onAppInfo, onWithdraw)`, 목록 행은 `HListRow("알림 설정"|"유저 정보"|"앱 정보"|…)`, `DangerRow("로그아웃")`/`DangerRow("회원 탈퇴")`. 개발자 모드일 때 맨 위(통계 아래)에 "개발자 도구" 행과 유저 전환 칩 목록을 추가한다.
- **세션** `J/core/AppSession.kt`: `state` (SIGNED_OUT/SIGNED_IN/RESTORING), `account`, `beginRestoring()`, `failRestoring()`, `signIn(account)`, `signOut()`(= `onCleared()` + SIGNED_OUT; `onCleared` 는 `AppViewModel` 에서 `{ auth.clearSession(); dataStore.reset() }`). `J/app/RootScreen.kt` 가 `state` 로 `AuthNavHost`/`MainNavHost`/빈 화면을 고르므로 **상태를 RESTORING 으로 한 번 내렸다 SIGNED_IN 으로 올리면 `MainNavHost` 가 새로 구성**된다(백스택 초기화) — 유저 전환에 이 방식을 쓴다(`signOut()` 은 토큰을 지우므로 쓰지 않는다).
- **ViewModel** `J/app/AppViewModel.kt`: `tokens = KeystoreTokenStore(application)`, `api = ApiClient(BuildConfig.API_BASE_URL, tokens, onSessionExpired = …)`, `auth/groups/missions/pokes/submissions/calendar/insights` 레포지토리, `appSession`, `dataStore`. `init` 에서 RESTORING 이면 `auth.restore()` → `dataStore.setMyProfile` → `appSession.signIn`.
- **ApiClient** `J/core/net/ApiClient.kt`: 생성자 `ApiClient(baseUrl, tokens, http: OkHttpClient = …, json, onSessionExpired)`. `get/post/patch/put/delete`(+ `auth: Boolean = true` 로 인증 없이 호출 가능), `postMultipart/putMultipart`. 2xx 는 모두 성공(202 도 디코드). `Json { ignoreUnknownKeys = true; explicitNulls = false }`. 업로드용 `uploadHttp` 는 `http.newBuilder()` 파생.
- **TokenStore** `J/core/net/TokenStore.kt`: `save(access, refresh)`/`clear()`. `KeystoreTokenStore` 가 실제 구현.
- **시간 사용처(앱)**: `Instant.now()`/`LocalDate.now` 를 직접 쓰는 곳 — `J/core/model/GroupSummary.kt`(`daysTogether`), `J/feature/groups/CreateGroupScreen.kt`(50·142행), `GroupFormat.kt`(34행 `groupCardSubtitle`), `HomeScreen.kt`(47), `J/feature/insight/GroupInsightScreen.kt`(42), `J/feature/mission/MissionScreen.kt`(93·251·253: 다음 미션 대기·카운트다운), `J/feature/records/RecordCalendarScreen.kt`(50), `J/feature/results/ResultsFormat.kt`(26 `koreanRelativeText`), `ResultsScreen.kt`(103). 그 외 `DataStore(clock = Clock.systemUTC())`(데모 초대 전용, 변경 불필요), `SketchTimer`/`AiGuessDialog`(경과 시간 측정, 변경 금지). 한국 시간 유틸 `J/core/HaweTime.kt`(`HaweTime.SEOUL`).
- **재사용 UI**: `HNavBar`, `HButton`, `HListRow`, `HAlert`, `HToggle`, `HTextField`, `HToastHost/rememberHToastState`, `HAvatar`, `FlowRow`(`CreateGroupScreen.kt` 선례, `@OptIn(ExperimentalLayoutApi::class)`), `WheelPicker`(`J/designsystem/components/WheelPicker.kt`), 시간 선택 시트 `TopicTimeSheet`(`GroupInfoScreen.kt` private — 필요하면 `internal` 로 승격), 날짜 선택 Material3 `DatePicker`(`CreateGroupScreen.kt` 선례).
- 라우트 `MainRoute`(`J/app/MainNavigation.kt` sealed interface, `@Serializable`), `MainNavHost(session, store, auth, push, groups, missions, pokes, submissions, calendar, insights)` — 새 파라미터로 `dev: DevRepository` 와 `devMode` 상태를 넘긴다.
- 빌드 정보: `BuildConfig.VERSION_NAME`, `BuildConfig.VERSION_CODE`(없으면 `PackageManager`), `BuildConfig.BUILD_TYPE`, `BuildConfig.API_BASE_URL`(debug 기본 `http://10.0.2.2:8080`, 폰은 `adb reverse` + `-PhaweApiBaseUrl=http://localhost:8080`). `buildFeatures { buildConfig = true }` 이미 켜짐.
- 서버 계약은 `.agent/plan.md` 의 표(`/api/dev/...`)가 정본: `status`, `clock`(PUT/DELETE), `missions/publish`, `missions/{id}/deadline-now`, `groups/{id}/deadline-now`, `missions/{id}/submissions`, `missions/{id}/submit`, `missions/{id}/submit-missing`, `groups/{id}/poke`, `submissions/{id}/react`, `submissions/{id}/comment`, `groups/{id}/insights/regenerate`, `groups/{id}/settings`(PATCH), `users`, `login-as`, `seed`, `groups/{id}`(DELETE), `reset`.

## Root Cause / Key Implementation Points

### 1. 상태와 시계 (`J/core/dev/`)
- `DevMode`(싱글톤 `object` 또는 `AppViewModel` 이 보유하는 클래스): SharedPreferences(`context.getSharedPreferences("hawe_dev", MODE_PRIVATE)`, 토큰 저장소와 별개)에 `enabled: Boolean`, `clockOffsetSeconds: Long` 저장. Compose 에서 관찰 가능한 `mutableStateOf` 로 노출(`enabled`, `offsetSeconds`). 초기화는 `AppViewModel`/`Application` 에서 `init(context)` 한 번.
- `AppClock`: `now(): Instant = Instant.now().plusSeconds(offsetSeconds)`, `today(): LocalDate`(서울 기준). 오프셋 0 이면 `Instant.now()` 와 동일해야 한다. **Findings 의 시간 사용처 중 "표시/판정용" 8곳**(`GroupSummary.daysTogether`, `CreateGroupScreen`, `groupCardSubtitle`, `HomeScreen`, `GroupInsightScreen`, `MissionScreen` 93·251·253, `RecordCalendarScreen`, `ResultsFormat.koreanRelativeText`, `ResultsScreen` 103)을 `AppClock.now()` 로 교체(기본 인자값 `Instant.now()` → `AppClock.now()`). 타이머/경과시간 측정(`SketchTimer`, `AiGuessDialog`)은 건드리지 않는다.
- 서버와 동기: 개발자 모드가 켜져 있을 때 앱 시작(그리고 Dev 도구 열 때·시계 변경 후)에 `GET /api/dev/status` → `AppClock.offsetSeconds = status.offsetSeconds`. 서버 시계 변경 API 의 응답으로도 즉시 갱신. 개발자 모드가 꺼지면 오프셋 0.

### 2. 진입(10탭)
- 순수 함수/클래스 `DevUnlock`(예: `fun registerTap(state, nowMs): TapResult`): 연속 10회 탭(탭 사이 간격이 3초를 넘으면 카운트 리셋)이면 `DevMode.enabled = true`. 5회부터 토스트 "개발자 모드까지 N번" 표시, 10회에 "개발자 모드가 켜졌어요", 이미 켜져 있으면 "이미 개발자 모드예요". `AppInfoScreen` 의 `Text("버전 $version")` 에 `clickable`(리플 없이) 로 연결하고 토스트(`HToastHost`)를 붙인다. 로직은 컴포저블 밖 순수 함수로 두어 단위 테스트 가능하게.

### 3. 서버 레포지토리 (`J/core/net/DevApi.kt`, `DevRepository.kt`)
- DTO + `DevRepository(api: ApiClient)`: 서버 계약 표의 각 엔드포인트를 1:1 메서드로(`status()`, `setClock(now|plusSeconds)`, `resetClock()`, `publish(groupId?, template?)`, `deadlineNow(missionId)`, `groupDeadlineNow(groupId)`, `submissionsOf(missionId)`, `submitAs(missionId, userId)`, `submitMissing(missionId)`, `poke(groupId, from, to, type, missionId?)`, `react(submissionId, userId, stickerType)`, `comment(submissionId, userId, content)`, `regenerateInsight(groupId, month)`, `updateSettings(groupId, dailyMissionCount?, slotPublishTimes)`, `users()`, `loginAs(userId): TokenResponseDto`, `seed(days, memberCount, dailyMissionCount, relationType)`, `deleteGroup(groupId)`, `reset()`). **인증이 필요 없는 호출은 `auth = false`** (login-as, users, status, clock 등 전부 `auth=false` 로 통일해도 무방 — 서버가 인증을 요구하지 않는다).
- 응답 DTO 는 서버 계약 표 그대로(`@Serializable internal data class …`, 누락 필드 기본값). `AppViewModel` 에 `val dev = DevRepository(api)`.

### 4. 원클릭 유저 전환
- `AppViewModel.switchUser(userId: Long)`(`viewModelScope.launch`): `dev.loginAs(userId)` → `tokens.save(access, refresh)` → `appSession.beginRestoring()` → `dataStore.reset()` → `auth.restore()` → `dataStore.setMyProfile(account.profile)` → `appSession.signIn(account)`. 실패 시 이전 토큰 복구(`tokens.save(oldAccess, oldRefresh)`)하고 `appSession` 을 다시 SIGNED_IN 으로 되돌리며 토스트. 이전 토큰은 호출 전에 읽어 둔다(`tokens.accessToken/refreshToken`). 서버 `login-as` 는 이메일/비번 없이 userId 로 토큰을 발급한다.
- `MyPageScreen` 개발자 모드 섹션: `LaunchedEffect` 로 `dev.users()` 로딩 → `FlowRow` 칩(닉네임, 현재 사용자(`session.account.userId`)는 강조/체크), 칩 탭 = `switchUser`(진행 중 비활성). 목록 로딩 실패 시 "다시 시도". 마이페이지 시그니처에 `dev: DevRepository`, `onDevTools`, `onSwitchUser: (Long) -> Unit`, 개발자 모드 상태를 전달(`MainNavigation` 에서 연결).

### 5. 개발자 도구 화면 `J/feature/dev/DevToolsScreen.kt` (+ 섹션 분리 파일)
라우트 `MainRoute.DevTools`. 위에서 아래로 섹션(임시 UI: `Column` 카드 + `HButton`/`HListRow`, 코드 주석 `임시 UI`):
1. **상태 줄**: `서버 {API_BASE_URL} · 유저 {닉네임}(#{id}) · 서버시간 {serverNow} (오프셋 {n}초) · 빌드 {VERSION_NAME}({code}) {BUILD_TYPE}` — 한 블록(복사 가능 `SelectionContainer`), "새로고침" 버튼(`dev.status()`).
2. **시간**: 날짜(Material3 `DatePicker`)+시각 선택 → `setClock(now=…)`; 빠른 버튼 `+1시간` `+1일` `+7일`(→ `plusSeconds`), `다음 발행 시각으로`(선택 그룹의 `nextPublishAt` 까지), `초기화`(→ `resetClock`). 성공 시 `AppClock.offsetSeconds` 갱신 + `groups.loadAll` → `store.applyServerGroups` 로 홈 데이터 새로고침 + 결과 토스트(경고 `warning` 표시).
3. **대상 그룹/미션 선택**: 내 그룹(`store.groups`) 드롭다운(칩 행), 선택된 그룹의 현재 미션(`store.todayMissions`) 칩. 이 선택이 아래 섹션들에 쓰인다.
4. **미션**: `미션 생성`(= `publish(groupId)`), 틀 선택 칩 13종(`MissionTemplates` 모델에 있는 이름/서버 enum 문자열) 후 `선택 틀로 생성`, `마감 당기기`(선택 미션 `deadline-now`), `그룹 전체 마감 당기기`. 완료 후 `missions.current` → `store.applyCurrentMission` 새로고침.
5. **대리 행동**: 멤버 선택(`GroupDetail.members`), 선택 미션에 `대리 제출`, `미제출자 전원 제출`, 선택 멤버→다른 멤버 `콕`/`똥`, 선택 미션의 제출 목록(`submissionsOf`)에서 하나 선택 → 스티커 5종으로 `반응`, 간단 입력창으로 `댓글`. 결과/오류 코드는 토스트 + 화면 하단 "최근 결과" 텍스트 박스(최근 20줄).
6. **시드**: `일수`(기본 30), `멤버 수`(기본 5), `하루 미션 수` 입력 → `시드 생성`(응답의 그룹 id 로 홈 새로고침), `[DEV] 그룹 전체 삭제`(`reset`, `HAlert` 확인).
7. **인사이트**: 월 이전/다음 + `강제 재생성`(→ `regenerateInsight`) 후 `인사이트 열기` 버튼(`MainRoute.GroupInsight(groupId)`).
8. **그룹 설정**: `하루 미션 수` 스테퍼(1..5), 슬롯별 발행 시각(슬롯 수만큼 시각 선택 — 기존 시간 시트 재사용) → `dev.updateSettings` 후 `groups.detail` 재조회/`applyServerGroup`.
9. **초기화**: `로컬 캐시 초기화`(`dataStore.reset()` + `groups.loadAll` 재조회), `토큰 삭제 후 로그아웃`(`appSession.signOut()`), `선택 그룹 삭제`(`dev.deleteGroup`, `HAlert` 확인 후 `store.leave`).
10. **네트워크 오류 주입**: 아래 6번의 토글 목록.
11. **개발자 모드 끄기**: `DevMode.enabled=false`(오프셋 0, 주입 해제).
- 모든 서버 호출은 `rememberCoroutineScope` + 진행 중 비활성(`busy` 플래그) + `try/catch`(`CancellationException` 재던짐, 오류는 `toUserMessage()` 대신 **원문 코드·메시지**를 결과 박스에 표시 — 개발 도구이므로 `ApiException.code/message` 그대로).

### 6. 네트워크 오류 주입 (앱 전용)
- `J/core/dev/DevFaults.kt`: 규칙 enum + 남은 횟수 — `INSIGHT_GENERATING`(GET `*/insights*` → 202 `{"groupId":…,"month":…,"status":"GENERATING","retryAfterSeconds":2}`; 요청 URL 의 groupId/month 를 채워 반환), `FEED_LOCKED`(GET `/api/missions/*/submissions*` → 423 `{"code":"FEED_LOCKED","message":"…"}`), `SERVER_ERROR`(임의 요청 → 500 `{"code":"INTERNAL_ERROR","message":"…"}`), `NETWORK_DOWN`(요청 시 `IOException` 던짐). 각 규칙은 on/off + "다음 N회" 카운터. OkHttp `Interceptor` 로 구현하고 **`AppViewModel` 의 `ApiClient` 생성 시 `http` 에 추가**(`DevMode.enabled` 가 true 일 때만 동작, 아니면 `chain.proceed`). `/api/dev/**` 호출은 주입 대상에서 제외.
- 토글 UI 는 Dev 도구 10번 섹션(체크 + 횟수).

### 7. 05 화면 "미션 생성" 버튼
- `MissionScreen` 에 `devMode` 이면(미션이 없을 때의 빈 상태와 평소 미션 카드 아래 모두) `HButton("[개발자] 미션 생성")` → `dev.publish(groupId)` 후 `refresh++`(기존 `LaunchedEffect(id, refresh)` 재사용), 결과 토스트. `MissionScreen` 시그니처에 `dev: DevRepository? = null`(null 이면 버튼 없음) 추가, `MainNavigation` 이 `devMode` 일 때만 전달.

### 8. 내비게이션/배선
- `MainRoute.DevTools`(`@Serializable data object` 또는 기존 object 라우트 방식 따라), composable, `MyPageScreen` 의 "개발자 도구" 행 → 이동. `MainNavHost` 에 `dev: DevRepository`, `onSwitchUser: (Long) -> Unit` 파라미터 추가, `RootScreen` → `viewModel.dev`, `viewModel::switchUser`. `AppInfoScreen(onBack, onLicenses)` 는 개발자 모드 켜기 위해 토스트 호스트와 `DevMode` 접근을 내부에서 처리(시그니처 변경 최소).

## Files / Functions to Change

| 파일 | 변경 |
|---|---|
| `J/core/dev/DevMode.kt`, `AppClock.kt`, `DevUnlock.kt`, `DevFaults.kt` (신규) | 상태·시계·탭 카운터·오류 주입 |
| `J/core/net/DevApi.kt`, `DevRepository.kt` (신규) | 서버 dev API 클라이언트 |
| `J/app/AppViewModel.kt` | `dev`, `switchUser`, 오류 주입 인터셉터 등록, 시작 시 서버 시계 동기, `DevMode.init` |
| `J/app/RootScreen.kt`, `J/app/MainNavigation.kt` | `dev`/`switchUser` 전달, `MainRoute.DevTools`, 05 화면 `dev` 전달 |
| `J/feature/mypage/AppInfoScreen.kt` | 버전 텍스트 10탭 |
| `J/feature/mypage/MyPageScreen.kt` | 개발자 섹션(유저 전환 칩, 도구 진입 행) |
| `J/feature/dev/DevToolsScreen.kt`, `DevSections.kt` (신규) | 개발자 도구 UI |
| `J/feature/mission/MissionScreen.kt` | 개발자 "미션 생성" 버튼, `AppClock` 교체 |
| 시간 사용처 8곳 (`GroupSummary.kt`, `CreateGroupScreen.kt`, `GroupFormat.kt`, `HomeScreen.kt`, `GroupInsightScreen.kt`, `RecordCalendarScreen.kt`, `ResultsFormat.kt`, `ResultsScreen.kt`) | `Instant.now()` → `AppClock.now()` |
| 테스트 | 아래 Tests Required |

## Implementation Steps

1. `DevMode`/`AppClock` + 시간 사용처 교체(오프셋 0 이면 동작 동일) + 테스트. 2. `DevUnlock` + `AppInfoScreen` 10탭. 3. `DevApi`/`DevRepository` + MockWebServer 계약 테스트. 4. `AppViewModel`(`dev`, `switchUser`, 시계 동기) + 마이페이지 유저 전환. 5. `DevToolsScreen` 섹션들(상태→시간→선택→미션→대리→시드→인사이트→설정→초기화). 6. `DevFaults` 인터셉터 + 토글 UI. 7. 05 화면 버튼. 8. 라우트·배선·전체 테스트·assemble(·lint).

## Constraints

- `android/` 만 수정(`docs/`·백엔드·iOS 금지, `.agent/report.md` 만 예외). 새 라이브러리 금지. 커밋/푸시 금지.
- **안전장치 금지**: `BuildConfig.DEBUG`/`BuildType` 으로 개발자 기능을 숨기지 않는다. 10탭만이 진입 조건.
- 개발자 모드가 꺼져 있을 때 앱의 모든 동작·화면은 **현재와 동일**(오프셋 0, 인터셉터 비활성, 개발자 UI 비노출).
- A2·A3·A9·A6 의 기존 변경 보존. `MissionScreen`/`MainNavigation`/`MyPageScreen` 수정은 최소 범위.
- 타이머/AI 추론 시간 측정(`SketchTimer`, `AiGuessDialog`)은 건드리지 않는다.
- 코드 스타일은 주변(한국어 문구, 짧은 DTO, 한 줄 `@Test`)에 맞춘다. 임시 UI 는 주석 `임시 UI`.

## Edge Cases

- 10탭: 중간에 3초 이상 멈추면 리셋, 다른 화면으로 이동했다 오면 리셋, 이미 켜진 상태에서 탭, 화면 회전(`rememberSaveable` 불필요 — 리셋 허용).
- 유저 전환: `login-as` 실패/네트워크 오류 → 이전 세션 복구, 전환 도중 `onSessionExpired` 중복 발생 방지(전환 중 플래그), 전환 직후 홈에서 `groups.loadAll` 이 새 유저 기준으로 실행(RESTORING→SIGNED_IN 으로 `MainNavHost` 재구성), 시드 계정이 아닌 유저로 로그인했을 때 현재 사용자 칩 표시.
- 시계: 서버가 꺼져 있거나 `status` 실패 → 오프셋 유지, 앱 시작 지연 금지(비동기, 타임아웃 짧게). 오프셋이 큰 값일 때 `Duration.between(now, nextPublishAt)` 음수 처리(`coerceAtLeast(0)` 기존). 개발자 모드 해제 시 오프셋 0 으로 되돌려 서버와 불일치가 남아도 일반 동작에 영향 없게(서버 시계는 `reset` 안내 토스트).
- 대리 행동: 선택 그룹/미션/멤버가 없을 때 버튼 비활성, 서버의 `ALREADY_SUBMITTED`/`MISSION_CLOSED` 등 오류 코드를 결과 박스에 그대로.
- 시드 직후: 새 그룹이 홈에 보이도록 `loadAll`; 시드된 그룹은 현재 로그인 유저가 멤버가 아닐 수 있음(소유자 dev1) → 안내 토스트("dev1 로 전환하세요") 또는 현재 유저가 dev1~devN 중 하나일 때만 홈에 보임을 문구로 안내.
- 오류 주입: `INSIGHT_GENERATING` 이 `InsightLoader` 재시도(최대 8회) 안에서 소진되도록 "N회" 지정, `NETWORK_DOWN` 이 `/api/dev/**` 호출까지 막지 않음(제외), 개발자 모드 해제 시 전체 해제.
- 설정 변경: `dailyMissionCount` 를 줄일 때 슬롯 시각 목록 길이 동기화, 서버 검증 오류(`VALIDATION_ERROR`) 표시.

## Regression Risks

- `Instant.now()` → `AppClock.now()` 교체: 오프셋 0 일 때 결과 동일해야 함 → 기존 단위 테스트(특히 `GroupFormatTest`, `ResultsFormat` 관련) 통과 확인. 기본 인자값으로 쓰인 곳은 호출부 시그니처 호환 유지.
- `MyPageScreen`/`MissionScreen`/`MainNavHost` 시그니처 변경 → 호출부·프리뷰·테스트 컴파일(`rg "MyPageScreen(\|MissionScreen(\|MainNavHost("`). 새 파라미터는 기본값을 줘서 기존 호출을 최대한 유지.
- OkHttp 인터셉터 추가가 개발자 모드 꺼짐 상태에서 요청을 바꾸지 않아야 함(단위 테스트로 pass-through 고정).
- `AppViewModel.switchUser` 가 `appSession` 상태 전이(RESTORING→SIGNED_IN)로 `RootScreen` 을 재구성 — 토큰 저장 순서와 `dataStore.reset()` 타이밍(이전 유저 데이터가 새 유저에게 보이지 않게).
- SharedPreferences 키 충돌 없음(`hawe_dev` 파일 분리).

## Tests Required

JVM 단위 테스트(`T/…`):
- `AppClockTest`: 오프셋 0 → `Instant.now()` 와 근접, 오프셋 설정 시 이동, `today()` 서울 기준 날짜 경계.
- `DevUnlockTest`: 9탭 미해제, 10탭 해제, 간격 3초 초과 리셋, 5~9탭 진행 문구, 이미 켜짐.
- `DevRepositoryTest`(MockWebServer): 각 엔드포인트의 메서드·경로·쿼리·본문(`now` 서울 시각 문자열, `plusSeconds`, `userId`, `stickerType`, `month`)과 응답 파싱, `auth=false`(Authorization 헤더 없음), 오류 응답이 `ApiException` 코드로 전파.
- `DevFaultsTest`: 규칙별 가짜 응답(202/423/500, `IOException`), "다음 N회" 소진, 개발자 모드 꺼짐 시 pass-through, `/api/dev/**` 제외.
- 유저 전환 순수 로직(토큰 보관/복구 순서)을 함수로 분리했다면 그 테스트(성공 시 새 토큰 저장, 실패 시 이전 토큰 복구).
- 시간 교체한 포맷 함수들의 기존 테스트 통과.
- UI 테스트 없음(컴파일 + 수동 확인).

## Validation Steps

1. `android\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline` 통과 기록(JDK 17 사용 시 명시).
2. `rg "Instant.now\(\)" android/app/src/main` 로 남은 곳이 타이머/AI 시간 측정/`DataStore` 데모 시계뿐인지 확인.
3. `rg "BuildConfig.DEBUG|BuildType" android/app/src/main/java` 에 개발자 기능 게이트가 없는지 확인.
4. 수동(기기+서버, 미검증이면 "미검증"): 앱 정보 버전 10탭 → 개발자 모드 켜짐 → 앱 재실행 후에도 유지 → 마이페이지 시드 유저 칩 한 번 탭으로 전환 → Dev 도구에서 `시드 생성` → dev1 전환 → 달력 도장(GOOD/LATE/PENALTY)·인사이트 확인 → 시계 `+1일` → 새 미션 발행·이전 미션 닫힘 → `미션 생성`/틀 선택/마감 당기기/대리 제출·콕·반응·댓글 → 인사이트 강제 재생성(202→200) → 그룹 설정(하루 미션 3개) → 05 화면 칩 → 오류 주입 → 개발자 모드 끄기.

## Potential Merge Conflicts

- `AppViewModel.kt`, `RootScreen.kt`, `MainNavigation.kt`, `MyPageScreen.kt`, `MissionScreen.kt`, `AppInfoScreen.kt`, `GroupFormat.kt`, `HomeScreen.kt`: A2·A3·A9·A6 미커밋 변경과 같은 파일. 기존 변경 보존, 변경은 이 계획 범위에 한정.
- 서버 계획(`.agent/plan.md`)이 먼저 병합돼 있어야 한다(`DevRepository` 계약).
