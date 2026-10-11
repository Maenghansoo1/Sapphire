# Android 9단계 구현 보고서

## 변경 사항

- `android/app/src/main/java/com/sapphire/hawe/core/push/`에 payload 파싱과 이동 대상 결정, 알림 설정 필터 함수, 4개 채널, 알림 표시기, 인텐트 상태 홀더, 디버그 브로드캐스트 수신기를 추가했다. `android/app/src/test/java/com/sapphire/hawe/core/push/PushModelTest.kt`에 순수 로직 테스트를 추가했다.
- `HaweApplication.kt`가 앱 시작 때 채널을 만들고 `FLAG_DEBUGGABLE`인 경우에만 exported 동적 수신기를 등록한다. `ic_stat_hawe.xml`은 기존 로고의 4개 타일 경로를 흰색 단색으로 사용한다.
- `AndroidManifest.xml`에 `POST_NOTIFICATIONS`, Application 클래스, Activity `singleTop`을 추가했다. `MainActivity.kt`는 최초 인텐트와 `onNewIntent`를 `AppViewModel.push`에 전달한다.
- `RootScreen.kt`와 `MainNavigation.kt`는 로그인 뒤 권한을 요청하고, 알림 목적지를 `Home → Mission → Results` 규칙에 맞춰 소비한다. `group_id`가 실제 그룹에 일치하면 해당 그룹을 쓰고, 없거나 잘못되면 첫 그룹을 쓴다.
- iOS 기준 커밋: `45a4996`. FCM, 토큰, 서버 연동, 설정 영속화는 추가하지 않았다.

## 검증

- JDK 17로 `android/.\gradlew.bat --no-daemon assembleDebug testDebugUnitTest lintDebug installDebug`: **성공**.
- JVM 테스트 **61개 통과, 실패 0개**(작업 전 58개 + 신규 3개).
- lint **오류 0개, 경고 24개, 힌트 1개**; 새 push 코드 경고 없음. 별도 typecheck 명령은 없고 Kotlin 컴파일을 수행했다.
- `SM-S937N`에 APK 설치, `adb shell am start -n com.sapphire.hawe/.MainActivity` 실행 및 프로세스 확인. 시작 직후 fatal 로그 없음.
- 권한이 없을 때 디버그 `topic` 브로드캐스트는 알림을 띄우지 않고 로그 한 줄을 남겼다. 임시로 권한을 부여한 뒤 재발송하여 시스템에 `topic` 채널, 단색 리소스 아이콘, `#67A0C3` 색, `HA'WE` 제목과 "오늘의 주제가 도착했어요" 본문이 게시된 것을 확인했다. 이후 권한을 원래 상태(미허용, `USER_SET` 없음)로 복구했다.
- `adb shell am start -n com.sapphire.hawe/.MainActivity --es type comment --es group_id mock`는 실행 중인 최상위 Activity로 인텐트가 전달됐다고 보고했다(`onNewIntent` 경로). 현재 기기는 로그아웃 상태이므로 로그인 후 실제 05/결과 공개 화면 이동은 수동 확인이 남았다.
- `git diff --check -- android` 공백 오류 없음. 새 파일 UTF-8 BOM 및 trailing whitespace 없음. 기존 미커밋 7·8·15단계 변경을 보존했고 `.agent/plan.md`, `.agent/review.md`는 수정하지 않았다. 커밋하지 않았다.

## 계획 조정과 남은 위험

- 디버그 브로드캐스트는 4개 종류의 알림을 각각 시험할 수 있도록 앱 내부 `NotificationPrefs` 값은 적용하지 않는다. `isNotificationEnabled` 순수 함수는 미래 서버/FCM 수신 흐름에서 사용할 수 있다. 시스템 권한과 채널 차단은 `PushNotifier.show`가 검사한다.
- 릴리스 빌드를 실행하지 않았다. 디버그 exported 수신기 등록은 `HaweApplication`의 `FLAG_DEBUGGABLE` 조건 안에만 있음을 코드로 확인했다.
- FCM, 기기 토큰 등록, 서버 설정 필터링은 미구현이다. 앱이 완전히 종료되면 메모리 상태가 사라지므로 알림 탭 후 로그인 화면이 열린다. 채널 이름/설명 변경은 Android에서 기존 채널에 즉시 반영되지 않을 수 있다.
- 푸시 서버 연결 단계는 `PushNotifier.show`와 `PushCenter`를 재사용한다. `AndroidManifest.xml`, `MainActivity.kt`, `AppViewModel.kt`, `RootScreen.kt`, `MainNavigation.kt`는 이후 작업의 병합 접점이다.

## 폰 확인 체크리스트

`adb` = `C:\Android\Sdk\platform-tools\adb.exe`.

1. 가입(A2→A3)해서 01 에 처음 들어오면 **알림 권한 대화상자**가 뜬다(Android 13+). "허용". 03 에서 `K7Q2PX` 로 그룹에 참여해 홈에 그룹이 있는 상태로 만든다.
2. 앱을 홈으로 보낸 뒤(프로세스는 살려 둔다) 알림을 보낸다:
   - `adb shell am broadcast -a com.sapphire.hawe.debug.NOTIFY --es type topic` → 알림 "컴공 동기"가 아닌 기본 제목(그룹 id 를 안 주면 제목 "HA'WE") + "오늘의 주제가 도착했어요".
   - `adb shell am broadcast -a com.sapphire.hawe.debug.NOTIFY --es type poke`, `adb shell am broadcast -a com.sapphire.hawe.debug.NOTIFY --es type friend_done`, `adb shell am broadcast -a com.sapphire.hawe.debug.NOTIFY --es type comment`도 각각 표시되고, 알림 설정에서 채널이 4개(오늘의 주제 / 콕 찌르기 / 친구 완료 / 댓글)로 보인다 (앱 정보 → 알림).
3. 알림을 누르면: topic/poke/friend_done → **05 오늘의 미션**(뒤로 가면 홈). comment → 오늘 미션을 **안 올렸다면** 05, **올렸다면** 05 위에 **결과 공개**(뒤로 → 05 → 홈). 05 에 이미 있는 상태에서 눌러도 `Home → 05` 로 정리.
4. 직접 이동 확인(알림 없이): `adb shell am start -n com.sapphire.hawe/.MainActivity --es type comment --es group_id mock` (앱이 켜져 있으면 `onNewIntent` 경로, 꺼져 있으면 로그인 후 처리).
5. 권한을 거부하거나 설정에서 알림을 꺼 둔 상태에서 브로드캐스트를 보내면 아무 알림도 표시되지 않고 앱은 크래시하지 않는다.
6. 로그아웃(17) 상태에서 알림을 보내 누르면 로그인 화면이 열리고, 로그인 후(그룹이 있으면 해당 화면, 없으면 이동 없음) 크래시 없음.
7. 이전 단계 회귀: 사진 선택기/카메라 권한 대화상자에서 돌아왔을 때 앱이 새로 시작(재생성)되지 않는다, 홈 → 05 → 수행 → 결과 공개, 마이페이지 흐름.
8. 릴리스 빌드 확인은 불가하므로 디버그 수신기 등록이 `FLAG_DEBUGGABLE` 가드 안에만 있음을 코드로 확인했다.
