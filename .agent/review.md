# 개발자 모드 ② Android — 리뷰

대상: 미커밋 변경(`core/dev/*`, `core/net/Dev*`, `feature/dev/DevToolsScreen.kt` 신규 + `AppViewModel`/`RootScreen`/`MainNavigation`/`ApiClient`/`AppInfoScreen`/`MyPageScreen`/`MissionScreen` 및 시간 사용처 교체), `.agent/plan.md`(앱 계획), `.agent/report.md`. A2·A3·A9·A6 미커밋 변경이 같은 worktree 에 있어 개발자 모드 범위만 확인했다.

## 요구사항 충족 확인
- **10탭 진입**: `DevUnlock.registerTap`(탭 간격 3초 초과 시 리셋, 5회부터 "개발자 모드까지 N번", 10회 해제, 이미 켜짐 안내)을 순수 클래스로 분리해 테스트. `AppInfoScreen` 의 "버전 …" 텍스트에 연결, 해제 시 `DevMode.changeEnabled(true)`(SharedPreferences `hawe_dev` 에 유지, 토큰 저장소와 분리).
- **안전장치 없음(결정대로)**: `BuildConfig.DEBUG`/빌드 타입 게이트 없음, release 에서도 10탭만으로 열림. 꺼져 있을 때는 오프셋 0·인터셉터 pass-through·개발자 UI 미노출(`DevMode.enabled` 조건).
- **앱 시계**: `AppClock.now()=Instant.now()+DevMode.offsetSeconds`, 표시/판정용 시간 사용처(요약·홈·인사이트·달력·결과 상대시간·05 화면 대기/카운트다운 등)를 교체. 타이머·AI 시간 측정은 보존. 개발자 모드 시작/켜짐/도구 진입 시 `GET /api/dev/status` 로 서버 오프셋 동기, 실패해도 마지막 오프셋 유지(앱 시작을 막지 않음, 타임아웃 5초). 끄면 오프셋 0.
- **원클릭 유저 전환**: `AppViewModel.switchUser` — `login-as` → 새 토큰 저장 → `beginRestoring()` → `dataStore.reset()` → `auth.restore()` → `signIn` 으로 `MainNavHost` 재구성(백스택·데이터 초기화). 이전 토큰/계정을 보관해 실패 시 복구하고, 전환 중에는 `onSessionExpired` 의 자동 로그아웃을 억제(`switching`), 중복·같은 유저 전환 차단. 마이페이지에 시드 유저 칩 + "개발자 도구" 행.
- **개발자 도구**: 상태 줄(서버 주소·유저·서버 시간·빌드), 서버 시계(날짜/시각 선택·+1시간/+1일/+7일/다음 발행 시각/초기화, 변경 후 그룹·미션 새로고침), 대상 그룹·미션 선택, 미션 생성(틀 13종 선택)·마감 당기기, 대리 제출/전원 제출/콕·똥/반응/댓글(제출 목록 선택), 시드 생성·`[DEV]` 그룹 전체 삭제(`HAlert` 확인), 인사이트 재생성·열기, 그룹 설정(하루 미션 수·슬롯 시각), 로컬 캐시 초기화·로그아웃·그룹 삭제(확인), 오류 주입, 개발자 모드 끄기, 최근 결과 20줄(서버 오류 `code` 원문 표시). 모든 서버 호출은 `busy` 로 직렬화·`CancellationException` 재던짐.
- **05 화면 "미션 생성"**: 개발자 모드일 때만 `dev` 가 전달되어 미션 없음/미션 카드 아래에 버튼 표시, 후 `refresh++`.
- **오류 주입**: `DevFaults` 인터셉터(`INSIGHT_GENERATING` 202 본문에 groupId/month 채움, `FEED_LOCKED` 423, `SERVER_ERROR` 500, `NETWORK_DOWN` IOException), "다음 N회" 카운터, `/api/dev/**` 제외, 꺼져 있으면 통과. `AppViewModel` 의 `ApiClient` http 에 동일 타임아웃으로 추가(업로드용 파생 클라이언트에도 상속).
- **서버 계약 일치**: `DevRepository` 가 서버 구현의 경로·본문과 맞음(`auth=false`, `{}` 본문 POST, `deleteReturning` 추가, `DevClockRequest(now|plusSeconds)`, 시드 기본값 생략 시 서버 기본값과 동일). 시계 설정 값 `…T09:00+09:00` 은 서버 `OffsetDateTime.parse` 로 파싱 가능.
- 보고된 검증: 단위 테스트 185개·assemble·androidTest 패키징·lint·release Kotlin 컴파일 통과(JDK 17). 백엔드·iOS·문서 변경 없음.

## P2
### File / Location
`android/app/src/main/java/com/sapphire/hawe/app/AppViewModel.kt` `switchUser`
### Problem
유저 전환의 토큰 보관/복구 순서가 ViewModel 안에 인라인이라 단위 테스트가 없다(계획은 "분리했다면" 테스트였고 필수는 아니었음). 실패 경로(`auth.restore()` 실패 → 이전 토큰 복구 → `signIn(oldAccount)`)가 코드로만 확인된다.
### Why It Matters
복구 경로가 틀리면 전환 실패 시 앱이 빈 화면(RESTORING)에 갇힐 수 있다. 다만 catch 블록이 `RESTORING` 일 때만 `signIn` 하고 `restore()` 실패 시에도 `oldAccount` 로 폴백하는 구조라 코드상 위험은 낮다.
### Recommended Fix
선택: 전환 절차를 `suspend` 함수(토큰 저장소·세션·API 를 인자로)로 빼서 성공/`login-as` 실패/`restore` 실패 세 경우를 JVM 테스트로 고정.

### File / Location
기기 확인 전체 (보고서: 기기 화면이 잠겨 있어 워크스루 불가)
### Problem
10탭 → 유지 → 유저 전환 → 시드 → 달력 도장 → 시계 이동 → 미션 생성 → 대리 행동 → 인사이트 → 그룹 설정 → 오류 주입 흐름이 실제 서버/기기에서 검증되지 않았다. Codex 가 임시로 띄운 서버와 `adb reverse` 를 정리했으므로 **현재 폰의 `adb reverse` 매핑도 사라진 상태**다.
### Why It Matters
UI 연동(특히 시계 변경 후 홈/05 새로고침, 유저 전환 후 홈 재로딩)은 컴파일·단위 테스트로 잡히지 않는다.
### Recommended Fix
(코드 수정 아님) 백엔드 실행 + `adb reverse tcp:8080 tcp:8080` 재설정 + 최신 debug APK(`-PhaweApiBaseUrl=http://localhost:8080`) 설치 후 계획의 수동 체크리스트를 한 번 수행.

### File / Location
`/api/dev/**` + 앱 개발자 모드 (결정 재확인)
### Problem
release 에서도 앱 정보 10탭만으로 열리고 서버 `/api/dev/**` 는 인증이 없다.
### Why It Matters
사용자 결정이며 구현·보고서에 경고가 남아 있다. 앱을 외부에 배포하면 누구나 개발자 모드를 켜서 연결된 서버의 dev 엔드포인트를 호출할 수 있다.
### Recommended Fix
(결정 사항이므로 코드 변경 없음) 외부 배포 전 서버 `permitAll` 한 줄·`dev` 패키지와 앱 진입점(`AppInfoScreen` 탭 처리, `MainRoute.DevTools`)을 제거/차단.

## 확인했지만 문제 없음
- 오류 주입 칩의 이름 칩("FAULT (n)")을 누르면 해당 규칙이 0 으로 초기화되는 동작은 직관적이지 않지만 기능상 문제 없음.
- 오프셋은 서버·앱 모두 "실제 시각 + 오프셋" 모델이라 기기와 서버의 시계가 달라도 서로 일관(앱은 서버의 `offsetSeconds` 만 사용).
- 미션 관련 신규 파일의 타이머/AI 시간 측정 보존, `DataStore` 데모 시계 변경 없음.
- 공유 파일(`MainNavigation.kt`, `MyPageScreen.kt`, `MissionScreen.kt`, `AppViewModel.kt`)은 A2·A3·A9·A6 미커밋 변경과 같은 파일이며 충돌은 없었음.

결론: **승인 가능** (기기 수동 확인 전에 `adb reverse` 재설정 필요)
