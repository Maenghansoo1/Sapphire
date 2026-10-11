# Review — Android 9단계: 푸시 알림 기반 (권한 · 채널 · 알림 눌림 처리)

기준: `.agent/plan.md`, `.agent/report.md`, 작업 트리의 `android/` 미커밋 변경. 범위 밖 파일 변경 없음, FCM/Firebase·새 라이브러리 없음, `DataStore`/모델/`AppSession` 시그니처 변경 없음 확인.
Codex 가 `assembleDebug` / `testDebugUnitTest`(61개 통과) / `lintDebug`(오류 0, 새 push 코드 경고 없음) / `installDebug` 를 직접 실행했고, 권한을 임시로 부여해 디버그 브로드캐스트로 `topic` 알림이 시스템에 게시되는 것(채널·단색 아이콘·색·제목·본문)을 확인한 뒤 권한을 원상 복구했다고 보고했다. 아래는 코드를 읽어 확인한 내용이며 폰에서 알림을 직접 눌러 보지는 않았다.

## 확인한 항목 (문제 없음)
- **인텐트 연결**: `MainActivity` 가 `savedInstanceState == null` 일 때만 최초 인텐트를 `AppViewModel.push.handle` 에 넘기고(재생성 시 재처리 방지), `onNewIntent` 에서 `setIntent` 후 처리한다. 매니페스트 `launchMode="singleTop"`, 알림 `PendingIntent` 는 `FLAG_IMMUTABLE | FLAG_UPDATE_CURRENT`, 알림마다 고유 `requestCode`(증가 카운터), `NEW_TASK | CLEAR_TOP` — 켜져 있는 앱에서 알림을 누르면 새 Activity 가 아니라 `onNewIntent` 로 들어온다. 일반 런처 실행(extras 없음)은 `parsePushDestination` 이 null 을 돌려 무시된다.
- **목적지 처리**: `MainNavHost` 의 `LaunchedEffect(destination)` 안에서만 이동한다. `pushTarget` 으로 계산 → `push.consume()` 으로 비움(같은 알림 중복 이동 방지) → `popBackStack<Home>(inclusive = false)` 후 `Mission`(+ comment 이고 내가 올렸으면 `Results`). 그룹이 없으면 소비만 한다. 로그아웃 상태에서 누른 알림은 `pendingDestination` 이 `PushCenter` 에 남아 로그인 후 `MainNavHost` 가 뜰 때 처리된다(iOS 동일). 화면 컴포저블이 사라지는 중에 호출되는 내비게이션이 없다(4단계 버그 규칙 준수). `Results` 진입 방어는 이미 제출된 미션에서만 통과하므로 comment 흐름과 충돌하지 않는다.
- **`pushTarget`**: payload `group_id` 가 실제 그룹과 일치하면 그 그룹, 아니면(없음/`"mock"`/잘못된 UUID) 첫 그룹 — iOS 규칙 + 계획한 확장. comment + (마지막 미션에 내가 제출 && 미션 id 있음) 일 때만 `Results`.
- **권한/채널**: 33+ 에서만 `POST_NOTIFICATIONS` 요청(`rememberSaveable` 플래그, 결과 무시), `PushChannels.ensure` 는 멱등하며 `HaweApplication.onCreate` 에서 4채널 생성(`topic`/`poke`/`friend_done`/`comment` = `PushKind.value`). `PushNotifier.show` 는 권한/전체 알림 차단 시 `false` 를 돌려 디버그 수신기가 로그만 남기고 크래시 없음.
- **디버그 수신기**: 등록이 `FLAG_DEBUGGABLE` 조건 안(`HaweApplication`)에만 있고 `// 개발용(디버그 빌드 전용)…` 주석이 있다. 알 수 없는 `type` 은 무시. payload extras 는 신뢰하지 않고 id 는 UUID 파싱 실패 시 첫 그룹으로 폴백한다.
- **알림 아이콘**: `ic_stat_hawe.xml` 이 로고 타일 4개를 흰색 단색으로 만든 벡터이고 `setColor(0xFF67A0C3)`.

문제를 찾지 못했다. (참고: `PushNotifier.show` 는 앱 수준 알림이 켜져 있으나 **채널만 사용자가 껐을 때**도 `true` 를 돌려주고 시스템이 조용히 표시하지 않는다 — 디버그 수신기의 로그 문구가 이 경우에는 맞지 않지만 영향 없는 로그 한 줄이다.)

---

## 결론

**승인 가능** — P0/P1/P2 없음. 폰에서 9단계 시나리오(최초 진입 시 권한 대화상자, `adb shell am broadcast -a com.sapphire.hawe.debug.NOTIFY --es type …` 4종, 알림 누르면 05 / 05 → 결과 공개, 로그아웃 상태 알림, 사진 선택기·권한 대화상자에서 돌아올 때 `singleTop` 으로 인한 앱 재시작 없음)를 확인한 뒤 서버 연결 단계로 넘어가도 좋다.
