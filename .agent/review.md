# Review — 계약 반영 1차 / 2A단계 (Android 네트워크 계층 · 토큰 저장소 · 로그인/가입 연결)

대상: `android/**`(신규 `core/net/*`, `AppSession`/`AppViewModel`/`RootScreen`/`AuthNavigation`, 인증·마이페이지 화면, 빌드·매니페스트, 테스트) + `docs/FRONTEND/ANDROID.md` + `.agent/report.md`.
검증 근거: 변경 소스 전문, 테스트 목록과 핵심 테스트 본문, 빌드 설정·매니페스트 diff를 읽었다. Gradle 테스트/빌드는 다시 실행하지 않았고 보고서(JDK17 `testDebugUnitTest` 73개 통과, `assembleDebug`/`assembleRelease`/`compileReleaseKotlin` 통과, lint 에러 0)를 신뢰한다. **에뮬레이터/실기기 수동 확인은 보고서대로 수행되지 않았다.**

## 요구사항 충족 확인

| 요구 | 결과 |
|---|---|
| OkHttp + kotlinx-serialization, Retrofit/Hilt 없음 | 충족 (`libs.versions.toml`, `build.gradle.kts`) |
| 401 처리 | `ApiClient.execute`: 인증 호출의 401 `TOKEN_EXPIRED` → `refreshOnce`(Mutex single-flight, 이미 갱신됐으면 새 토큰 재사용) → 한 번만 재시도. `INVALID_TOKEN`/refresh 401/재시도 후 401 → 토큰 삭제 + `onSessionExpired` 1회. refresh 중 네트워크 오류는 토큰 보존. 비인증 호출 401(`INVALID_CREDENTIALS`)은 세션 무변화. 계획의 규칙과 일치하고 각각 테스트(`expiredTokenRefreshesRotatesAndRetries`, `concurrentExpiryRefreshesOnlyOnce`, `invalidTokenAndFailedRefreshClearOnce`, `unauthenticated401KeepsSession`, `refreshNetworkFailureKeepsTokens`)가 있음 |
| refresh 토큰 회전 | 새 access/refresh 둘 다 저장 |
| 토큰 저장 | `KeystoreTokenStore`(AES-256-GCM, IV+암호문 Base64, 복호화 실패 시 폐기). `security-crypto` 미사용 |
| 가입 흐름 | `AuthRepository.signUp`: register → login → characters → PATCH(실패 무시) → me. `skipRegister`/`onRegistered` 로 부분 실패 재시도 처리, `EMAIL_TAKEN` 은 토스트 후 A2 복귀 |
| 약관 전달 | A2 `onNext(email, service, privacy)` → `AuthNavHost` 보관 → A3 → 요청 본문 |
| 세션 복구 | `RESTORING` 상태 + `AppViewModel.init` 에서 `restore()`; 인증 실패/네트워크 오류 모두 `SIGNED_OUT`(토큰은 ApiClient 가 401 에서만 삭제) |
| 로그아웃/만료 시 정리 | `AppSession.signOut()/withdraw()` → `onCleared`(토큰 삭제 + `DataStore.reset()`) |
| "준비 중" | 로그인 화면(비밀번호 찾기·간편 로그인 3), 마이페이지(프로필 편집·회원 탈퇴), 유저 정보(닉네임·연결/해제·비밀번호 변경)에 `COMING_SOON` 토스트. 기존 라우트·화면은 삭제하지 않음 |
| A3 사진 칸 숨김 | `CharacterPicker(showPhotoCell=false)`, 기본값 `true` 라 기존 호출 불변 |
| 목업 로그인 제거 | `LoginAccountFactory.kt` 와 테스트 삭제, A3 목업 `MyAccount` 생성 제거, `MyAccount.mock` 유지 |
| 빌드/네트워크 설정 | `INTERNET` 권한, `BuildConfig.API_BASE_URL`(debug `10.0.2.2:8080`), debug 전용 평문 HTTP 오버레이, release 는 `usesCleartextTraffic=false` 유지(병합 매니페스트 확인했다고 보고) |
| 범위 밖 금지 | 그룹/미션/제출, 비밀번호 변경 API, 프로필 사진, FCM, iOS, 백엔드 파일 변경 없음 |

정확성 문제(경쟁 조건, 토큰 누출, 무한 재시도, 상태 전이 오류)는 찾지 못했다.

## P0

없음.

## P1

없음.

## P2

### `android/app/build.gradle.kts` — release 기본 API 주소가 임시 도메인
#### Problem
`-PhaweApiBaseUrl` 이 없으면 release 빌드가 `https://api.hawe.app` 을 조용히 쓴다. 이 도메인은 계약 문서에서도 "임시 도메인"(초대 링크 `hawe.app`)이며 소유·운영 여부가 확인되지 않았다.
#### Why It Matters
실수로 속성 없이 release 를 만들어 배포하면 앱이 의도하지 않은 호스트로 인증 정보(이메일·비밀번호·토큰)를 보낸다. 지금은 배포 단계가 아니므로 위험은 낮다.
#### Recommended Fix
(선택) release 빌드에서 `haweApiBaseUrl` 이 없으면 Gradle 구성 단계에서 실패하도록 `error("...")`로 바꾸거나, 최소한 `docs/FRONTEND/ANDROID.md` 의 2A 단락에 "release 는 반드시 `-PhaweApiBaseUrl` 지정" 을 명시. (보고서에는 이미 위험으로 적혀 있음.)

### `android/app/src/main/java/com/sapphire/hawe/feature/mypage/MyPageScreen.kt` — 로그아웃 안내 문구가 더 이상 사실이 아님
#### Problem
확인 알림이 "다시 로그인하면 그룹과 기록은 그대로 남아 있어요" 인데, 이제 `signOut()` 이 `DataStore.reset()` 을 호출해 앱 안 목업 그룹/기록이 지워지고, 로그인 때도 `store.reset()` 이 호출된다.
#### Why It Matters
3A 에서 그룹이 서버로 연결되면 문구가 다시 맞게 되지만, 그 전까지는 로컬에서 만든 그룹이 로그아웃으로 사라져 사용자가 오해할 수 있다(테스트 중에 혼동 가능).
#### Recommended Fix
3A 에서 그룹이 서버 데이터가 되기 전까지는 그대로 두고 인지만 해 둔다(문구 수정은 선택). 의도된 변화이며 계획의 "로그아웃 시 DataStore.reset()" 과 일치한다.

### 참고 (조치 불필요)
- **수동 확인 미수행 (병합 전 필요)**: 보고서 그대로 가입·세션 복구·로그아웃·오프라인·"준비 중" 토스트, Keystore 영속성, debug 평문 HTTP 는 기기에서 확인되지 않았다. 코드와 JVM 테스트 범위에서는 문제를 못 찾았지만, `KeystoreTokenStore`(JVM 테스트 불가)와 UI 흐름은 계획의 Validation 3 목록으로 사람이 한 번 돌려 보는 것이 안전하다. 특히 확인할 것: ① 가입 후 앱 강제 종료 → 재실행 시 로그인 유지 ② 서버 중지 상태 로그인 시 토스트(크래시 없음) ③ A3 에 "내 사진" 칸이 없고 7개 캐릭터가 깨지지 않게 배치되는지.
- A3 의 `registered` 는 `rememberSaveable` 로 프로세스 종료를 견디지만 비밀번호는 의도적으로 `remember` 만 쓴다(기존 설계). 가입 도중 프로세스가 죽었다 복원되면 빈 비밀번호로 재시도되어 토스트("이메일 또는 비밀번호가 올바르지 않아요")만 나오고, 이 경우 사용자는 A1 로 로그인하면 된다. 드문 경로라 별도 조치 없음.
- 백엔드가 `allowBackup` 기본값(true)이라 SharedPreferences 의 암호문이 자동 백업될 수 있으나, Keystore 키는 복원되지 않아 새 기기에서는 복호화 실패 → 토큰 폐기 후 로그아웃 상태로 시작한다(코드가 이 경로를 처리함).
- 1·2단계 백엔드 변경과 이번 Android 변경이 같은 작업 트리에 미커밋으로 섞여 있다. 파일이 겹치지 않으므로 커밋을 분리하기 쉽다(백엔드 1단계 / 백엔드 2단계 / Android 2A).

## 결론

**승인 가능.** 코드 리뷰와 JVM 테스트 범위에서 결함은 없고, 남은 위험은 기기에서의 수동 확인(위 3가지)과 release API 주소 설정뿐이다. 수동 확인에서 문제가 나오면 그 결과를 기준으로 수정 요청을 하면 된다. Codex 에게 보낼 수정 사항은 없다.
