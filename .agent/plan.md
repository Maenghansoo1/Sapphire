# Plan — Android 9단계: 푸시 알림 기반 (권한 · 채널 · 알림 눌림 처리)

## Goal

iOS 의 푸시 처리(`ios/HAWE/Core/Push/PushNotifications.swift` + `MainFlowView.open(_:)`)를 Android 로 옮긴다. **서버 푸시(FCM)는 이번 범위가 아니다** — 서버가 없고 Firebase 프로젝트도 없다. 대신 "알림이 오면 어떻게 보이고, 누르면 어디로 가는가"까지를 완성하고, FCM 이 붙을 때 `PushNotifier.show(...)` 한 곳만 호출하면 되도록 만든다.

- 로그인 뒤 처음 메인 화면(01/04)에 들어오면 **알림 권한**(Android 13+ `POST_NOTIFICATIONS`)을 한 번 묻는다 (iOS: 로그인 뒤 처음 홈/01 진입 시 `requestAuthorizationIfNeeded`).
- **알림 채널 4종**(오늘의 주제 도착 / 콕 찌르기 / 친구가 미션을 완료 / 내 작품에 댓글)을 만든다 (Android 8+).
- **알림을 눌러 앱이 열리면** payload(`type`, `group_id`, `submission_id`)에 따라 화면 이동: 주제/콕/친구 완료 → 05 오늘의 미션, 댓글 → 내가 올렸으면 05 → 결과 공개, 아니면 05. 앱이 꺼져 있을 때(콜드 스타트)와 켜져 있을 때(웜) 모두.
- 서버 없이도 동작을 폰에서 확인할 수 있게 **디버그 빌드 전용 알림 발송 수단**(adb 브로드캐스트)을 둔다.

전체 로드맵은 `docs/FRONTEND/ANDROID.md` (§6 9단계).

이번 범위 아님: FCM(`google-services.json`, `FirebaseMessagingService`, 기기 토큰 등록/해제), 실제 서버 연동, 초대 링크(App Links), 알림 설정 값의 영속화, 17-1 에서 권한 상태 안내 UI.

## Findings

### 원본 iOS (읽기 전용, 수정 금지)
- `ios/HAWE/Core/Push/PushNotifications.swift`:
  - `PushKind`: `topic`, `poke`, `friendDone = "friend_done"`, `comment` — 서버 payload 의 `"type"` 값.
  - `PushDestination(kind, groupID: String?, submissionID: String?)`.
  - `PushCenter.shared`: `authorization`, `deviceToken`(TODO 서버 등록), `pendingDestination`; `requestAuthorizationIfNeeded()`(상태가 미결정일 때만 시스템 대화상자 → 허용이면 원격 알림 등록); `handle(userInfo:)` — `type` 이 알려진 값이 아니면 무시, 맞으면 `pendingDestination` 설정.
  - `AppDelegate`: 앱을 보고 있을 때도 배너+소리로 보여줌(`willPresent`), 알림을 누르면(`didReceive`) `PushCenter.handle`.
- `ios/HAWE/App/MainFlowView.swift`: `.task { requestAuthorizationIfNeeded() }` (로그인 뒤 처음 홈/01 진입 때), `.onChange(of: pendingDestination)` 에서 `open(destination)` 후 `pendingDestination = nil`. `open`: **첫 번째 그룹**을 대상으로(주석 TODO: 서버 연결 시 payload 의 groupID/submissionID 사용) —
  - `.topic, .poke, .friendDone` → `path = [.mission(groupID)]`
  - `.comment` → 마지막 미션에 내가 올렸으면 `path = [.mission, .results(groupID, missionID)]`, 아니면 `[.mission]`
  - 그룹이 하나도 없으면 아무것도 하지 않음.
- `ios/PushSamples/topic.apns`, `comment.apns`: payload 예 — `title` = 그룹 이름("컴공 동기"), `body` = "오늘의 주제가 도착했어요 · 사진" / "서연님이 내 작품에 댓글을 남겼어요", `type`, `group_id`("mock"), `submission_id`("mock").
- 보낼지 말지(전체 알림 OFF, 그룹 알림 OFF)는 **서버가** `notification_prefs`·`group_members.notify` 를 보고 결정한다 (iOS 주석).

### Android 현재 상태 (8단계 + 15 완료)
- `MainActivity.kt`: `ComponentActivity`, `enableEdgeToEdge()`, `setContent { HaweTheme { RootScreen(viewModel<AppViewModel>()) } }`. `onNewIntent`/인텐트 처리 없음. 매니페스트 액티비티: `exported`, `screenOrientation=portrait`, 런처 인텐트 필터만 있고 **`launchMode` 없음**, `<application>` 에 `android:name`(Application 서브클래스) 없음, `POST_NOTIFICATIONS` 권한 없음.
- `app/AppViewModel.kt`: `AndroidViewModel` — `dataStore`, `appSession` 소유. (Activity 범위 ViewModel 이라 Activity 가 완전히 종료되면 상태가 모두 사라지는 것이 iOS 와 같은 "메모리 전용" 동작이다.)
- `app/RootScreen.kt`: `SIGNED_OUT` → `AuthNavHost(session, store)`, `SIGNED_IN` → `MainNavHost(session, store)`.
- `app/MainNavigation.kt`: `MainRoute` = `Home`(루트), `Start`, `CreateGroup`, `JoinGroup`, `GroupInfo`, `GroupProfile`, `Mission(groupId)`, `Calendar`, `MissionPerform`, `Results(groupId, missionId)`, `SubmissionDetail`, `MyPage` 계열. `MainNavHost(session, store)`. 각 UUID 라우트 컴포저블은 `runCatching { UUID.fromString }` + **진입 시 한 번만**(`LaunchedEffect(entry)`) 방어한다. 화면 컴포저블은 데이터가 사라져도 내비게이션 없이 빈 배경만 그린다(4단계 "퇴장 중 `popBackStack`" 버그 재발 방지 규칙).
- `core/data/DataStore.kt`: `groups: List<StoredGroup>`(읽기 전용 스냅샷, 순서 = 참여 순), `StoredGroup(detail, missions)`, `StoredMission(id, type, topic, number, opensAt, closesAt, submissions, poked)`, `Submission.isMine`, `group(id): GroupDetail?`, `todayMission(groupID)`, `result(groupID, missionID)`(내가 올린 미션만 반환).
- `core/model/MyAccount.kt`: `NotificationPrefs(all, topic, poke, friendDone = false, comment)`; `AppSession.notificationPrefs`(17-1 알림 설정에서 바뀜). `GroupDetail.notify`(그룹별 알림, 16 에서 바뀜).
- 리소스: `res/drawable/logo_symbol_small.xml` — 120×120 뷰포트의 **둥근 사각 타일 4개**(`M43 14H27C19.82 14 14 19.82 …` 등, 색 #111111/#67A0C3). 알림 작은 아이콘은 **알파만 쓰므로** 이 타일 4개를 모두 흰색 한 가지로 칠한 단색 벡터를 새로 만들어 쓴다. 앱 이름 `@string/app_name`("HA'WE"), 액센트 색 `HColors.accent`(#67A0C3).
- 의존성: Compose, activity-compose 1.11, lifecycle, navigation-compose 2.9.5. **새 의존성 불필요**(`androidx.core.app.NotificationCompat` 은 이미 `core-ktx` 가 transitive 로 제공 — 컴파일되지 않으면 `androidx.core` 를 확인하되 새 라이브러리를 임의 추가하지 말고 보고서에 적는다).
- 현재 테스트 58개 통과.

## Root Cause / Key Implementation Points

1. **FCM 없이 "수신 → 표시 → 탭 → 이동" 파이프라인만 만든다.** `PushNotifier.show(context, kind, title, body, groupId, submissionId)` 가 알림을 만들어 띄우고, 알림의 `PendingIntent` 가 `MainActivity` 를 `type`/`group_id`/`submission_id` extras 와 함께 연다. 미래의 `FirebaseMessagingService.onMessageReceived` 는 data payload 를 이 함수에 그대로 넘기면 된다(이번에 구현하지 않는다).
2. **순수 로직을 분리**(Compose·android 비의존, 단위 테스트):
   - `PushKind`(value: `"topic" | "poke" | "friend_done" | "comment"`, `fromValue(String?)`), `PushDestination(kind, groupId: String?, submissionId: String?)`.
   - `parsePushDestination(type: String?, groupId: String?, submissionId: String?): PushDestination?` — 알 수 없는/없는 `type` 이면 null. 빈 문자열 id 는 null 로 정규화.
   - `pushTarget(destination, groups: List<PushGroupSnapshot>): PushTarget` — iOS `open()` 규칙: 그룹이 없으면 `PushTarget.None`; 대상 그룹 = `destination.groupId` 가 **존재하는 그룹 id 와 일치하면 그 그룹**, 아니면(없음/`"mock"` 같은 값/존재하지 않음) **첫 번째 그룹**(iOS 와 같음); 종류가 topic/poke/friendDone → `PushTarget.Mission(groupId)`; comment → 그 그룹의 마지막 미션에 내가 올렸으면 `PushTarget.Results(groupId, missionId)` (백스택은 `Mission → Results`), 아니면 `PushTarget.Mission(groupId)`. `PushGroupSnapshot(id: UUID, lastMissionId: UUID?, lastMissionSubmittedByMe: Boolean)`.
   - `isNotificationEnabled(prefs: NotificationPrefs, kind: PushKind, groupNotify: Boolean? = null): Boolean` — 서버가 할 필터링을 흉내 내는 순수 함수(미래 FCM 처리와 디버그 수단에서 쓰기 위함): `prefs.all` 이 꺼지면 false; 종류별 값(`topic`/`poke`/`friendDone`/`comment`); `groupNotify == false` 면 false.
   - `defaultPushTexts(kind, groupName)`(기본 제목/본문; iOS 샘플 문구 기준): 제목 = 그룹 이름(없으면 "HA'WE"), 본문 = topic "오늘의 주제가 도착했어요", poke "누군가 콕 찔렀어요", friend_done "친구가 미션을 완료했어요", comment "내 작품에 댓글이 달렸어요".
   - `pushChannelSpecs`: 4개 채널의 id/이름/설명 상수 목록.
3. **`PushCenter`**(Compose 상태를 가진 작은 클래스, `AppViewModel` 이 소유): `pendingDestination: PushDestination?`(읽기 전용 노출), `handle(intent: Intent?)`(extras 파싱 → `pendingDestination` 설정; 알 수 없으면 무시), `consume()`(처리 뒤 null). iOS `PushCenter.shared` 의 싱글턴 대신 ViewModel 소유(Activity 범위) — 새 Activity 인스턴스가 생기면 새 `PushCenter` 가 생긴다.
4. **인텐트 연결**: `MainActivity` — `onCreate` 에서 `savedInstanceState == null` 일 때만 `intent` 를 `PushCenter.handle` 에 넘기고(회전/재생성 때 같은 알림을 다시 처리하지 않게), `onNewIntent(intent)` 에서 `setIntent(intent)` 후 `handle`. 매니페스트에 `android:launchMode="singleTop"` 을 추가해 앱이 이미 떠 있을 때 알림을 누르면 새 Activity 가 아니라 `onNewIntent` 로 들어오게 한다. `PendingIntent` 는 `FLAG_IMMUTABLE | FLAG_UPDATE_CURRENT`, 알림마다 고유 `requestCode`(예: `System.nanoTime().toInt()` 또는 증가 카운터)로 extras 가 섞이지 않게 한다. `FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TOP`.
5. **화면 이동은 `MainNavHost` 에서만**: `MainNavHost(session, store, push)` 가 `push.pendingDestination` 을 관찰(`LaunchedEffect(push.pendingDestination)`)하고, null 이 아니면 `pushTarget(...)` 로 계산한 뒤 → `push.consume()` → 백스택 정리(`nav.popBackStack(MainRoute.Home, inclusive = false)`) 후 `Mission(groupId)` 으로 이동, 댓글+제출했으면 이어서 `Results(groupId, missionId)`. `PushTarget.None`(그룹 없음)이면 소비만 하고 이동 없음. **로그아웃 상태에서 알림을 눌렀다면** `MainNavHost` 가 아직 없으므로 `pendingDestination` 은 남아 있고, 로그인해서 `MainNavHost` 가 뜬 뒤 처리된다(iOS 와 동일; 그룹이 없으면 무시).
   - 이동 코드는 화면이 사라지는 중에 호출되지 않는다(`MainNavHost` 는 항상 살아 있는 최상위 컴포저블이고 `LaunchedEffect` 는 컴포지션에 묶여 있다).
6. **권한 요청**: `MainNavHost` 가 처음 컴포지션될 때(`LaunchedEffect(Unit)`) `Build.VERSION.SDK_INT >= 33` 이고 `POST_NOTIFICATIONS` 가 허용되지 않았으면 `rememberLauncherForActivityResult(RequestPermission())` 로 한 번 요청한다(결과는 무시 — iOS 와 같이 거부돼도 앱 사용엔 지장 없음). 33 미만은 요청 없음. 같은 `MainNavHost` 수명 동안 한 번만(`rememberSaveable` 플래그) — 로그아웃 뒤 다시 로그인하면 새 `MainNavHost` 이므로 다시 시도될 수 있다(시스템이 이미 영구 거부면 대화상자가 뜨지 않고 즉시 결과만 돌아옴; 허용).
7. **채널**: `PushChannels.ensure(context)` — 4개 `NotificationChannel`(id = `topic`, `poke`, `friend_done`, `comment`; 이름 "오늘의 주제"/"콕 찌르기"/"친구 완료"/"댓글"; 설명은 iOS 17-1 문구 참고; 중요도 `IMPORTANCE_DEFAULT`; 한국어). 멱등(여러 번 호출해도 안전). API 26 미만은 no-op(minSdk 26 이라 사실상 항상 실행). 호출 위치: **`HaweApplication.onCreate`**(아래).
8. **`HaweApplication`**(새 `Application` 서브클래스, 매니페스트 `android:name=".HaweApplication"`): `onCreate` 에서 `PushChannels.ensure(this)`. 그리고 **디버그 빌드(`FLAG_DEBUGGABLE`)에서만** `DebugNotificationReceiver` 를 동적으로 등록한다(`ContextCompat.registerReceiver(…, RECEIVER_EXPORTED)` — adb 쉘이 보내는 브로드캐스트를 받아야 하므로 export 필요, 릴리스에서는 등록 코드가 실행되지 않는다).
9. **디버그 알림 발송**: 액션 `com.sapphire.hawe.debug.NOTIFY`, extras `type`(필수: topic/poke/friend_done/comment), 선택 `group_id`, `submission_id`, `title`, `body`. 수신하면 기본 문구(`defaultPushTexts`)로 `PushNotifier.show(...)`. **권한이 없으면 아무것도 표시되지 않는다**(`NotificationManagerCompat.areNotificationsEnabled()` 이 false 이면 조용히 무시; 33+ 에서는 `POST_NOTIFICATIONS` 필요). 코드에 `// 개발용(디버그 빌드 전용). FCM 연결 단계에서 삭제해요.` 주석. (프로세스가 살아 있어야 하므로 앱을 한 번 실행한 뒤 홈으로 보낸 상태에서 쓰는 방식 — Application 에 등록돼 있어 Activity 가 닫혀도 프로세스가 살아 있는 동안은 동작.)
10. **알림 작은 아이콘**: `res/drawable/ic_stat_hawe.xml` — `logo_symbol_small.xml` 의 타일 4개 경로를 그대로 쓰되 `android:fillColor` 를 모두 `#FFFFFF` 로 한 단색 벡터. 알림 색(`setColor`)은 `0xFF67A0C3`(accent).

## Files / Functions to Change

새 파일 (`android/app/src/main/java/com/sapphire/hawe/` 기준):
- `core/push/PushModel.kt` — `PushKind`, `PushDestination`, `parsePushDestination`, `PushGroupSnapshot`, `PushTarget`, `pushTarget`, `isNotificationEnabled`, `defaultPushTexts`, `PushChannelSpec` 상수
- `core/push/PushCenter.kt` — Compose 상태를 가진 `PushCenter`(`pendingDestination`, `handle(Intent?)`, `consume()`)
- `core/push/PushNotifier.kt` — `PushChannels.ensure(context)`, `PushNotifier.show(...)`(알림 구성, `PendingIntent`, 권한/차단 확인)
- `core/push/DebugNotificationReceiver.kt` — 디버그 전용 브로드캐스트 수신기 + 등록 헬퍼
- `HaweApplication.kt` — 채널 생성 + 디버그 수신기 등록
- `res/drawable/ic_stat_hawe.xml` — 알림 작은 아이콘

수정할 파일:
- `AndroidManifest.xml` — `<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />`, `<application android:name=".HaweApplication" …>`, 액티비티 `android:launchMode="singleTop"`.
- `MainActivity.kt` — `AppViewModel.push.handle(intent)`(`onCreate` 에서 `savedInstanceState == null` 일 때, `onNewIntent` 에서 `setIntent` 후).
- `app/AppViewModel.kt` — `val push = PushCenter()`.
- `app/RootScreen.kt` — `MainNavHost(session, store, push)`.
- `app/MainNavigation.kt` — `MainNavHost` 시그니처에 `push: PushCenter` 추가, 권한 요청 `LaunchedEffect`, `pendingDestination` 처리(`pushTarget` → 백스택 정리 + `Mission`/`Results` 이동).
- 변경 금지(원칙): `core/data/DataStore.kt`, `core/model/*`, `core/AppSession.kt`, `app/AuthNavigation.kt`, 기존 화면들. 17-1 알림 설정 화면은 수정하지 않는다.

테스트 (`android/app/src/test/java/com/sapphire/hawe/core/push/`):
- `PushModelTest.kt`

## Implementation Steps

0. **환경**(이전 단계와 동일, 준비됨): `cd C:\hawe\android`, `$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot"`, SDK `C:\Android\Sdk`(`local.properties` 있음). `.\gradlew.bat assembleDebug testDebugUnitTest lintDebug installDebug`. 폰은 USB 연결(`C:\Android\Sdk\platform-tools\adb.exe devices` 에 `device`); 안 보이면 설치만 건너뛰고 보고서에 적는다.
1. `PushModel.kt` + `PushModelTest`.
2. `ic_stat_hawe.xml`, `PushNotifier.kt`(채널 + 표시), `PushCenter.kt`.
3. `HaweApplication.kt` + `DebugNotificationReceiver.kt` + 매니페스트 수정.
4. `AppViewModel` / `MainActivity` / `RootScreen` 연결.
5. `MainNavigation.kt` — 권한 요청 + 대기 중 목적지 처리.
6. 빌드·테스트·lint·설치, 폰에서 adb 로 발송해 확인(`Validation Steps`), 결과를 `.agent/report.md` 에 기록.

## Constraints

- 변경 범위는 `android/` 안. `ios/`, `src/`(백엔드), `cordova/`, `frontend/`, `docs/`, 루트 `.gitignore`, `AGENTS.md`/`CLAUDE.md` 수정 금지. git 상태를 바꾸는 명령 금지(commit/checkout/stash/reset/clean 등).
- iOS 파일은 읽기 전용. 문구·이동 규칙은 iOS 와 동일(확장: payload 의 `group_id` 가 실제 그룹과 일치하면 그 그룹 사용 — 그 외에는 iOS 와 같이 첫 번째 그룹).
- **FCM/Firebase 추가 금지**(`google-services`, `firebase-messaging`, 서비스 클래스 등). 새 라이브러리 추가 금지.
- `DataStore`/모델/`AppSession` 시그니처·불변 규칙 변경 금지. 서버 호출·영속화 없음.
- 디버그 전용 코드는 **`FLAG_DEBUGGABLE` 일 때만 실행**되게 가드한다. 릴리스에서 수신기를 등록하지 않는다. 디버그 수신기는 `exported` 이므로 릴리스에 남지 않는 것이 중요하다.
- 알림 payload 의 extras 는 **신뢰하지 않는다** — 알 수 없는 `type` 은 무시, id 는 UUID 파싱 실패 시 무시, 존재하지 않는 그룹이면 첫 번째 그룹/무시.
- 화면이 사라지는 중에도 내비게이션 호출 금지(4단계 버그). 이동은 `MainNavHost` 의 `LaunchedEffect` 안에서만.
- 한글이 들어간 파일은 **UTF-8(BOM 없음)**. PowerShell `Set-Content`/`Out-File` 로 한글 소스를 쓰지 않는다.
- 코드 주석은 iOS 처럼 한국어로 짧게, 문구는 Kotlin 안 한국어 리터럴.

## Edge Cases

- **권한 요청**: Android 13+ 에서만 대화상자. 이미 허용이면 요청하지 않음. 거부해도 앱은 정상 동작(알림이 표시되지 않을 뿐). 12 이하에서는 요청 없이 알림이 표시된다. 로그인 직후 01/홈이 먼저 그려진 뒤 대화상자가 뜬다.
- **채널**: 이미 만든 채널을 다시 만들어도 사용자의 채널 설정(소리 끄기 등)은 유지된다(Android 동작). 채널 이름/설명 변경은 최초 생성 시에만 반영됨 — 개발 중 이름을 바꾸면 앱 정보에서 데이터 삭제가 필요할 수 있음(보고서에 한 줄).
- **알림 누르기 — 콜드 스타트**: 프로세스가 죽은 상태에서 알림을 누르면 새 Activity+새 ViewModel 이 만들어져 **로그아웃 상태(A1)** 로 열린다(메모리 전용 상태 — iOS 와 동일). `pendingDestination` 은 유지되고, 로그인 후 `MainNavHost` 가 처리하되 그룹이 없으면 무시된다. 이 경우 에러·무한 반복이 없다.
- **알림 누르기 — 웜**(앱이 켜진 상태/백그라운드): `singleTop` + `onNewIntent` 로 기존 Activity 에 전달되어 현재 백스택이 `Home` 까지 정리된 뒤 목적지로 이동한다. 이미 05 에 있을 때 같은 05 알림을 눌러도 `Home → Mission` 으로 정리되고 단일 인스턴스.
- **같은 인텐트 재처리 금지**: 화면 회전(세로 고정이라 드묾)/구성 변경으로 Activity 가 재생성돼도 `savedInstanceState != null` 이면 인텐트를 다시 처리하지 않는다. `pendingDestination` 은 처리 뒤 `consume()` 로 비워 같은 알림이 두 번 이동하지 않는다.
- **로그아웃 상태에서 알림**: 위 6번. 로그인 뒤 처리, 그룹 없으면 소비만.
- **comment 알림**: 마지막 미션에 내가 올렸을 때만 `Results` 까지. 안 올렸으면 `Mission` 만(결과 공개는 올려야 볼 수 있다).
- **존재하지 않는 `group_id`**(예: 샘플의 `"mock"`): 첫 번째 그룹으로. 그룹이 하나도 없으면 이동 없음.
- **빈/누락된 extras**: `type` 없음 → 아무 일도 하지 않음(앱은 그냥 열림).
- **알림 표시**: 앱이 포그라운드일 때도 알림이 표시된다(Android 기본). 알림 탭 시 알림은 자동으로 사라진다(`setAutoCancel(true)`).
- **디버그 수신기**: 앱 프로세스가 죽어 있으면 수신 못 함(앱을 한 번 실행). `type` 누락/알 수 없는 값이면 무시. 권한 없으면 표시 없음(로그 한 줄).
- **작은 아이콘**: 단색(알파)이어야 하며 컬러 아이콘이면 회색 사각형으로 보인다 — `ic_stat_hawe.xml` 사용.

## Regression Risks

- `launchMode="singleTop"`: 런처에서 앱을 다시 열 때 동작(새 인스턴스가 아니라 기존 인스턴스 사용)이 바뀐다 — 기존 흐름(로그인, 화면 이동, 카메라/사진 선택/권한 대화상자 복귀)에 영향이 없어야 한다. 특히 시스템 권한 대화상자·사진 선택기에서 돌아올 때 Activity 가 재생성되지 않는지.
- `MainNavHost` 시그니처 변경: 호출부는 `RootScreen.kt` 한 곳.
- `AppViewModel` 에 `push` 추가: 기존 `dataStore`/`appSession` 생성과 무관.
- 새 `Application` 서브클래스: 기존에 `android:name` 이 없었으므로 처음 도입 — 앱 시작 시간/동작 영향 없음(채널 생성은 가볍다).
- 권한 대화상자가 로그인 직후 뜨면서 키보드/화면 전환과 겹치지 않는지(01/04 진입 뒤). 대화상자가 떠 있는 동안 화면 회전/백그라운드 전환에서 크래시가 없어야 한다.
- 17-1 알림 설정은 앱 내부 값만 바꾸며 이번 변경과 무관(서버 필터링을 흉내 내는 `isNotificationEnabled` 는 순수 함수로만 존재하고 UI 에 연결하지 않는다).

## Tests Required

JVM 단위 테스트(JUnit4), 기존 58개 통과 유지:
- `PushModelTest`:
  - `PushKind.fromValue`: `"topic"`, `"poke"`, `"friend_done"`, `"comment"` → 각 종류, `"friendDone"`/`""`/null/`"unknown"` → null
  - `parsePushDestination`: 올바른 값(`group_id`, `submission_id` 유지), 알 수 없는 type → null, type null → null, 빈 문자열 id → null 로 정규화
  - `pushTarget`: 그룹 0개 → `None`; topic/poke/friendDone → `Mission(첫 그룹)`; comment + 마지막 미션 내가 올림 → `Results(group, lastMissionId)`; comment + 안 올림 → `Mission`; comment + 미션 없음(`lastMissionId == null`) → `Mission`; `groupId` 가 존재하는 두 번째 그룹과 일치 → 그 그룹; 존재하지 않는 id("mock", 랜덤 UUID) → 첫 그룹
  - `isNotificationEnabled`: `all=false` 면 모든 종류 false, `all=true` 에서 종류별 값(`friendDone` 기본 false), `groupNotify=false` 면 false, `groupNotify=null`/true 는 영향 없음
  - `defaultPushTexts`: 종류별 본문 4개, 그룹 이름이 제목, 이름이 비면 "HA'WE"
  - `pushChannelSpecs`: id 4개(`topic`, `poke`, `friend_done`, `comment`)가 서로 다르고 `PushKind.value` 와 일치

알림 표시·권한 대화상자·`PendingIntent`·내비게이션의 자동 테스트는 만들지 않는다 — 폰 확인으로 대체.

## Validation Steps

Codex 가 직접 실행: `.\gradlew.bat assembleDebug testDebugUnitTest lintDebug installDebug` (JDK 17). 기준: 빌드 성공, 테스트 전부 통과, lint 새 오류 0(경고 건수 보고), 폰 설치 후 `adb shell am start -n com.sapphire.hawe/.MainActivity`.

사용자가 폰에서 확인할 시나리오 — 보고서에 **adb 명령까지 그대로** 적을 것 (`adb` = `C:\Android\Sdk\platform-tools\adb.exe`):
1. 가입(A2→A3)해서 01 에 처음 들어오면 **알림 권한 대화상자**가 뜬다(Android 13+). "허용". 03 에서 `K7Q2PX` 로 그룹에 참여해 홈에 그룹이 있는 상태로 만든다.
2. 앱을 홈으로 보낸 뒤(프로세스는 살려 둔다) 알림을 보낸다:
   - `adb shell am broadcast -a com.sapphire.hawe.debug.NOTIFY --es type topic` → 알림 "컴공 동기"가 아닌 기본 제목(그룹 id 를 안 주면 제목 "HA'WE") + "오늘의 주제가 도착했어요".
   - `--es type poke`, `--es type friend_done`, `--es type comment` 도 각각 표시되고, 알림 설정에서 채널이 4개(오늘의 주제 / 콕 찌르기 / 친구 완료 / 댓글)로 보인다 (앱 정보 → 알림).
3. 알림을 누르면: topic/poke/friend_done → **05 오늘의 미션**(뒤로 가면 홈). comment → 오늘 미션을 **안 올렸다면** 05, **올렸다면** 05 위에 **결과 공개**(뒤로 → 05 → 홈). 05 에 이미 있는 상태에서 눌러도 `Home → 05` 로 정리.
4. 직접 이동 확인(알림 없이): `adb shell am start -n com.sapphire.hawe/.MainActivity --es type comment --es group_id mock` (앱이 켜져 있으면 `onNewIntent` 경로, 꺼져 있으면 로그인 후 처리).
5. 권한을 거부하거나 설정에서 알림을 꺼 둔 상태에서 브로드캐스트를 보내면 아무 알림도 표시되지 않고 앱은 크래시하지 않는다.
6. 로그아웃(17) 상태에서 알림을 보내 누르면 로그인 화면이 열리고, 로그인 후(그룹이 있으면 해당 화면, 없으면 이동 없음) 크래시 없음.
7. 이전 단계 회귀: 사진 선택기/카메라 권한 대화상자에서 돌아왔을 때 앱이 새로 시작(재생성)되지 않는다, 홈 → 05 → 수행 → 결과 공개, 마이페이지 흐름.
8. 릴리스 빌드 확인은 불가하므로 디버그 수신기 등록이 `FLAG_DEBUGGABLE` 가드 안에만 있음을 코드로 확인했다고 보고서에 적기.

## Potential Merge Conflicts

- `android/` 만 수정. 백엔드·iOS·Cordova 와 겹치지 않는다.
- 공유 파일 수정: `AndroidManifest.xml`, `MainActivity.kt`, `app/AppViewModel.kt`, `app/RootScreen.kt`, `app/MainNavigation.kt`. 서버 연결 단계(FCM 서비스, 토큰 등록, 서버 알림 설정 저장)가 `PushNotifier.show` 와 `PushCenter` 를 그대로 호출하므로 이 단계를 먼저 병합한다.
- 작업 트리에 이전 단계들의 미커밋 변경(15 기록 캘린더 포함)이 남아 있을 수 있다. `git status` 로 확인만 하고 커밋하지 않는다.
- `.agent/report.md` 는 Codex 소유. 기준 iOS 커밋(`git log -1 --format=%h -- ios/`), 빌드·테스트·lint·설치 결과, iOS 와 다르게 한 부분(그룹 id 일치 시 해당 그룹 사용, 싱글턴 대신 ViewModel 소유, 디버그 브로드캐스트), 폰 확인 체크리스트(adb 명령 포함), 남은 위험(FCM/토큰/서버 필터링 미구현, 앱이 완전히 종료되면 메모리 상태가 사라져 알림 탭 시 로그인 화면)을 적는다.
