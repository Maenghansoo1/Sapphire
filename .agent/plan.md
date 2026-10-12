# Plan — 계약 반영 1차 / 2A단계: Android 네트워크 계층 · 토큰 저장소 · 로그인/가입 서버 연결

> 범위: **Android 앱만** (`android/`). 백엔드(2단계)는 이미 구현·리뷰 완료 상태이며 이 계획은 그 계약을 소비한다. iOS(`ios/`), 백엔드(`src/`), `frontend/`(Cordova)는 건드리지 않는다. 그룹/미션/제출은 아직 목업 `DataStore` 그대로 둔다(3A 이후 단계).

## Goal

1. 앱에 **서버 통신 기반**(HTTP 클라이언트, 토큰 보관, 401 처리)을 만든다. 이후 3A·4A·6A 단계가 모두 이 위에 올라가므로 여기서 구조를 굳힌다.
2. **A1 로그인 / A2→A3 가입**을 실제 서버(`/api/auth/*`, `/api/me`, `/api/characters`, `/api/me/character`)로 연결한다.
3. 앱을 껐다 켜도 로그인이 유지된다 (저장된 토큰으로 `GET /api/me` 복구).
4. 서버에 아직 없는 인증 관련 기능은 **"준비 중"** 으로 안내한다: 간편 로그인 3종, 비밀번호 찾기, 17-2 로그인 연결/해제·비밀번호 변경, 17-1 프로필 편집, 17-5 회원 탈퇴. 목업 데이터와 서버 데이터를 섞지 않는다.

## Findings

(전부 코드로 확인함. 앱 패키지 루트: `android/app/src/main/java/com/sapphire/hawe/`)

**서버 계약** (`docs/BACKEND/CONTRACT_DECISIONS.md` §1, 백엔드 구현 완료)
- `POST /api/auth/register` `{email, password, nickname, agreeTerms, agreePrivacy}` → 201 `{id, email, nickname}`. 오류: 409 `EMAIL_TAKEN`, 400 `VALIDATION_ERROR`(이메일 형식, 비밀번호 8~100자+영문·숫자, 닉네임 trim 후 2~10자, 약관 둘 다 true). **토큰을 주지 않는다.**
- `POST /api/auth/login` `{email, password}` → 200 `{accessToken, refreshToken}`. 401 `INVALID_CREDENTIALS`(형식이 틀린 이메일도 401).
- `POST /api/auth/refresh` `{refreshToken}` → 200 `{accessToken, refreshToken}` (**refresh 토큰도 회전됨**). 401 `INVALID_TOKEN` / `TOKEN_EXPIRED`.
- `GET /api/me` (Bearer) → `{id, email, nickname, characterKey|null, profileImageUrl|null, createdAt("2026-09-28T09:00:00+09:00")}`. 사용자 없음/잘못된 토큰 401 `INVALID_TOKEN`, 만료 401 `TOKEN_EXPIRED`.
- `GET /api/characters?page=0&size=100` (인증 불필요) → `{content:[{id, name, svgKey}], page, size, totalPages, totalElements}`. 키는 앱 `ProfileCharacter.value` 와 동일(`mongsil, ipsagwi, gomdol, mulbangul, gamja, kkotnim, pilleum`), id 는 `1,2,3,5,6,7,8`(4 없음).
- `PATCH /api/me/character` `{characterId}` (Bearer) → 200 `{characterId, name, svgKey}`.
- 모든 오류 본문: `{"code":"...","message":"..."}`. 시각은 오프셋 ISO-8601. 응답 id 는 숫자.
- 인증 필터는 만료 토큰에 401 `TOKEN_EXPIRED`, 그 밖의 잘못된 토큰에 401 `INVALID_TOKEN` 을 같은 오류 본문 형식으로 준다.

**앱 현재 구조**
- 네트워크 계층 **없음**. `build.gradle.kts` 에 OkHttp/Retrofit 없음, 의존성은 Compose·Navigation·Coil·kotlinx-serialization-json·CameraX·junit 뿐. 매니페스트에 **`INTERNET` 권한 없음**, `android:usesCleartextTraffic="false"`.
- 세션: `core/AppSession.kt` — `State {SIGNED_OUT, SIGNED_IN}`, `var account: MyAccount`(기본 `MyAccount.mock`), `signIn()`, `signOut()`, `withdraw()`. 인스턴스는 `app/AppViewModel.kt` 가 `AppSession()` 으로 만들고, 같은 곳에서 `DataStore(File(cacheDir,"hawe-media"))`, `PushCenter()` 를 가진다. `app/RootScreen.kt` 가 `state` 로 `AuthNavHost`/`MainNavHost` 를 고른다. 토큰·영속 저장 없음 → 앱을 끄면 로그아웃된다.
- A1 `feature/auth/LoginScreen.kt`: 로그인 버튼이 `accountForLogin(...)`(`LoginAccountFactory.kt`, 이메일에서 닉네임 만들어 내는 목업) 후 `store.reset(); store.setMyProfile(...)`, `session.signIn()`. 간편 로그인 버튼 3개는 `onSocial(provider)` → `AuthRoute.SignupProfile(provider, null)` 로 이동. "비밀번호 찾기"는 `onReset(email)` → `PasswordResetScreen`.
- A2 `SignupAccountScreen.kt`: 이메일·약관 체크(`service`, `privacy`)를 화면 `rememberSaveable` 로 갖고, 비밀번호/확인은 `AuthNavHost` 가 들고 있다(`signupPassword`, `signupConfirm`). `HButton("다음") { onNext(email.trim()) }` → `AuthRoute.SignupProfile(EMAIL, email)`. 약관 값은 A3 로 **전달되지 않는다**.
- A3 `SignupProfileScreen.kt`: 캐릭터 선택(`CharacterPicker`, **"내 사진" 칸 포함**)과 닉네임(`maxLength = 10`, 버튼 활성 조건 `trim().length in 2..10`). "시작하기"가 목업으로 `MyAccount(...)` 를 만들어 `session.account = ...; store.reset(); store.setMyProfile(profile); session.signIn()`.
- `core/model/MyAccount.kt`: `MyAccount(profile, email, joinedAt: Instant, signUpProvider: LoginProvider, linkedProviders)`, `canChangePassword = EMAIL in linkedProviders`, `MyAccount.mock`(테스트가 사용 — **유지**). `designsystem/assets/ProfileCharacter.kt`: `enum ProfileCharacter(value, displayName, drawable)`, `DEFAULT = MONGSIL`. `core/model/GroupDetail.kt`: `Profile(avatar: AvatarContent, nickname)`, `AvatarContent.Character(character)`.
- 마이페이지: `MyPageScreen.kt`(프로필 편집 버튼 `onProfileEdit`, "회원 탈퇴" `DangerRow(..., onWithdraw)`, 로그아웃 확인 후 `session.signOut()`), `UserInfoScreen.kt`(닉네임 행 `onProfileEdit`, 로그인 연결 `ProviderRow` 의 `onLink`/`onUnlink`, 비밀번호 변경 행 `onChangePassword`, 활성 조건 `account.canChangePassword`). `MainNavigation.kt` 라우트 `ProfileEdit/UserInfo/ChangePassword/Withdraw` 와 화면은 이미 있음.
- 토스트 패턴: 화면마다 `val toast = rememberHToastState()`, `toast.show("...")`, 루트 `Box` 안에 `HToastHost(toast)` (`designsystem/components/HToastState.kt`, 예: `feature/groups/GroupInfoScreen.kt:52,128`).
- `HTextField(value, onValueChange, placeholder, ..., error: String?, ...)` 로 필드 오류 표시 가능.
- 테스트: `android/app/src/test/java/.../*Test.kt` JUnit4 (`org.junit.Assert.*`, `@Test`). 기존 테스트 중 `feature/auth/LoginAccountFactoryTest.kt` 는 `accountForLogin` 을 테스트한다. `DataStoreTest.kt`, `SignupAccountStateTest.kt`, `MyPageLogicTest.kt`(`MyAccount.mock` 사용) 등은 변경하지 않아야 한다.
- 문서: `docs/FRONTEND/ANDROID.md` 59행 "DI 라이브러리(Hilt 등)·Room·Retrofit 은 넣지 않아요. 서버 연결 단계에서 다시 정해요."
- 개발 환경: JDK 17 로 Gradle 실행 (AGP 8.13.2, Kotlin 2.2.21, `jvmTarget 17`; JDK 25 로는 안 될 수 있음). 에뮬레이터에서 호스트 PC 서버는 `http://10.0.2.2:8080`. 백엔드는 `./mvnw spring-boot:run`, `.env` 에 `DB_*`/`JWT_SECRET`.

## Root Cause / Key Implementation Points

1. **HTTP 스택: OkHttp 4.12.0 (Retrofit 없음, Hilt 없음).** 이미 쓰는 `kotlinx-serialization-json` 으로 JSON. 이유: 앱의 서버 호출이 단순한 REST 이고, 테스트에서 `MockWebServer` 를 쓸 수 있다. 의존성 추가: `com.squareup.okhttp3:okhttp:4.12.0`, `org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2`(+`-android`), 테스트 `com.squareup.okhttp3:mockwebserver:4.12.0`, `org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2` (버전은 `gradle/libs.versions.toml` 에 추가).
2. **401 처리는 OkHttp `Authenticator` 가 아니라 `ApiClient` 안에서 직접** 한다. 코루틴 `Mutex` 로 refresh 를 **single-flight** 로 만들고, 요청 바디가 문자열이라 재시도가 단순하다.
   - 인증이 필요한 호출이 401 + `code == "TOKEN_EXPIRED"` → `refreshOnce(usedAccessToken)`: Mutex 안에서 `tokens.access != usedAccessToken` 이면(다른 요청이 이미 갱신함) 갱신 없이 통과, 아니면 `POST /api/auth/refresh`. 성공하면 두 토큰을 저장하고 원래 요청을 **한 번만** 재시도.
   - 401 + `code == "INVALID_TOKEN"`, 또는 refresh 가 401 로 실패, 또는 재시도도 401 → **토큰 삭제 + `onSessionExpired()`** 호출 후 `ApiException` 던짐.
   - refresh 가 네트워크 오류로 실패한 경우는 **토큰을 지우지 않고** `NetworkException` 만 던진다(일시적 오프라인으로 로그아웃시키지 않기 위해).
   - 로그인/가입 같은 비인증 호출의 401(`INVALID_CREDENTIALS`)은 refresh 로직을 타지 않는다.
3. **토큰 저장**: `TokenStore` 인터페이스 + 운영 구현 `KeystoreTokenStore`(Android Keystore 의 AES-256-GCM 키로 토큰을 암호화해 `SharedPreferences` 에 저장, 외부 라이브러리 없음 — `androidx.security:security-crypto` 는 deprecated 라 쓰지 않는다) + 테스트용 `InMemoryTokenStore`. JVM 단위 테스트에서는 Keystore 가 없으므로 로직 테스트는 `InMemoryTokenStore` 로만 한다.
4. **가입 흐름은 서버 호출 4~5개를 순서대로**: `register → login → (GET /characters → PATCH /me/character) → GET /me`. 서버는 가입 때 토큰을 주지 않고 캐릭터를 따로 받는다(계약 §1-1 "가입 흐름"). 캐릭터 지정 실패는 **치명적이지 않다**(가입·로그인은 이미 됨; 기본 캐릭터로 시작하고 계속 진행). 반면 register 성공 후 login/me 가 실패하면 사용자가 "시작하기"를 다시 눌러도 `EMAIL_TAKEN` 이 나지 않도록, 화면이 `registered` 플래그를 기억해 재시도 때는 register 를 건너뛴다.
5. **세션 복구**: 앱 시작 시 저장된 토큰이 있으면 상태를 `RESTORING`(빈 화면)으로 두고 `GET /me` 로 계정을 복구한다. 성공 → `SIGNED_IN`. 인증 실패(`ApiException` 401, 이미 토큰은 `ApiClient` 가 지움) → `SIGNED_OUT`. 네트워크 오류 → `SIGNED_OUT` 으로 두되 **토큰은 보존**(다음 실행 때 재시도).
6. **서버 도메인 모델 → 앱 모델 변환은 한 곳**(`AuthRepository`): `MeResponse` → `MyAccount(profile = Profile(AvatarContent.Character(character), nickname), email, joinedAt = OffsetDateTime.parse(createdAt).toInstant(), signUpProvider = EMAIL, linkedProviders = {EMAIL})`. `characterKey` 가 null 이거나 모르는 값이면 `ProfileCharacter.DEFAULT`.
7. **기존 목업 인증 경로를 제거**한다: `accountForLogin`/`LoginAccountFactory.kt` 와 그 테스트, A3 의 목업 `MyAccount` 생성. `MyAccount.mock` 은 다른 테스트가 쓰므로 남긴다.

## Files / Functions to Change

경로는 `android/app/src/main/java/com/sapphire/hawe/` 기준(이하 `~/`), 빌드 파일은 `android/` 기준.

### A. 빌드/설정
- `android/gradle/libs.versions.toml`: `okhttp = "4.12.0"`, `coroutines = "1.10.2"` 버전과 라이브러리 `okhttp`, `okhttp-mockwebserver`, `coroutines-core`, `coroutines-android`, `coroutines-test` 추가.
- `android/app/build.gradle.kts`:
  - `implementation(libs.okhttp)`, `implementation(libs.coroutines.android)`, `testImplementation(libs.okhttp.mockwebserver)`, `testImplementation(libs.coroutines.test)`.
  - `buildFeatures { compose = true; buildConfig = true }`.
  - `buildTypes`: `debug { buildConfigField("String","API_BASE_URL", "\"${findProperty("haweApiBaseUrl") ?: "http://10.0.2.2:8080"}\"") }`, `release { buildConfigField("String","API_BASE_URL", "\"${findProperty("haweApiBaseUrl") ?: "https://api.hawe.app"}\"") }` (운영 도메인은 아직 미정 → release 기본값은 임시이며 `-PhaweApiBaseUrl=...` 로 덮어쓴다. 코드/문서에 이 사실을 한 줄 주석으로 남긴다). 값 끝에 `/` 없이.
- `android/app/src/main/AndroidManifest.xml`: `<uses-permission android:name="android.permission.INTERNET" />` 추가. `usesCleartextTraffic="false"` 는 유지.
- **debug 전용 평문 HTTP 허용**: `android/app/src/debug/AndroidManifest.xml`(신규) 에 `<application android:networkSecurityConfig="@xml/network_security_config" />`, `android/app/src/debug/res/xml/network_security_config.xml`(신규) 에 `<base-config cleartextTrafficPermitted="true">`. (release 빌드는 HTTPS 만.)

### B. 네트워크 계층 (신규 패키지 `~/core/net/`)
- `ApiException.kt`:
  ```kotlin
  class ApiException(val status: Int, val code: String, message: String) : Exception(message)
  class NetworkException(cause: Throwable) : Exception(cause)
  ```
- `TokenStore.kt`: `interface TokenStore { val accessToken: String?; val refreshToken: String?; fun save(access: String, refresh: String); fun clear() }` + `class InMemoryTokenStore : TokenStore`(thread-safe, `@Volatile`/synchronized).
- `KeystoreTokenStore.kt`: `class KeystoreTokenStore(context: Context) : TokenStore`. `AndroidKeyStore` 의 별칭 `hawe_token_key` AES/GCM/NoPadding 256비트 키(없으면 생성), 값은 `IV(12바이트) + 암호문` 을 Base64 로 `SharedPreferences("hawe_session")` 에 저장. 복호화 실패(키 소실 등) 시 저장값을 지우고 null 반환. `clear()` 는 SharedPreferences 의 토큰 키만 삭제.
- `ApiClient.kt`:
  ```kotlin
  class ApiClient(
      private val baseUrl: String, private val tokens: TokenStore,
      private val http: OkHttpClient = OkHttpClient.Builder()
          .connectTimeout(10, SECONDS).readTimeout(20, SECONDS).writeTimeout(20, SECONDS).build(),
      val json: Json = Json { ignoreUnknownKeys = true; explicitNulls = false },
      private val onSessionExpired: () -> Unit = {},
  ) {
      suspend inline fun <reified T> get(path: String, auth: Boolean = true): T
      suspend inline fun <reified B, reified T> post(path: String, body: B, auth: Boolean = true): T
      suspend inline fun <reified B, reified T> patch(path: String, body: B, auth: Boolean = true): T
  }
  ```
  (내부 공통 `execute(method, path, bodyJson: String?, auth)`: 문자열 응답을 돌려주고 `inline` 래퍼가 `json.decodeFromString`. 응답 본문이 비어 있으면(204 등) `Unit` 처리용 오버로드는 이번 단계에서 필요 없으므로 만들지 않는다.)
  - 요청은 `Dispatchers.IO` 에서 실행, 취소 시 `call.cancel()`. `IOException` → `NetworkException`. JSON 파싱 실패 → `ApiException(status, "INVALID_RESPONSE", ...)`.
  - 비-2xx: 본문을 `ErrorBody(code, message)` 로 파싱(실패하면 `code = "HTTP_<status>"`) 후 위 Root Cause 2번 규칙 적용.
  - 요청 헤더: `Content-Type: application/json; charset=utf-8`, 인증 호출은 `Authorization: Bearer <access>`.
  - refresh 용 호출은 **같은 `http` 클라이언트**를 쓰되 `ApiClient.execute` 의 인증/재시도 로직을 타지 않는 내부 함수로 직접 호출(무한 재귀 방지).
- `AuthApi.kt` — `@Serializable` DTO 와 얇은 호출 함수:
  `RegisterRequestDto(email, password, nickname, agreeTerms, agreePrivacy)`, `RegisterResponseDto(id, email, nickname)`, `LoginRequestDto`, `RefreshRequestDto`, `TokenResponseDto(accessToken, refreshToken)`, `MeDto(id, email, nickname, characterKey: String? = null, profileImageUrl: String? = null, createdAt)`, `CharacterDto(id, name, svgKey)`, `PageDto<T>(content: List<T>)`, `CharacterUpdateDto(characterId)`, `ErrorBody(code, message)`.
- `AuthRepository.kt`:
  ```kotlin
  class AuthRepository(private val api: ApiClient, private val tokens: TokenStore) {
      suspend fun login(email: String, password: String): MyAccount
      suspend fun signUp(email: String, password: String, nickname: String, character: ProfileCharacter,
                         agreeTerms: Boolean, agreePrivacy: Boolean,
                         skipRegister: Boolean, onRegistered: () -> Unit): MyAccount
      suspend fun restore(): MyAccount            // 저장된 토큰으로 GET /me
      val hasSession: Boolean                     // tokens.refreshToken != null
      fun clearSession()                          // tokens.clear()
  }
  ```
  - `login`: `POST /auth/login`(auth=false) → 토큰 저장 → `GET /me` → 변환. email 은 `trim()`.
  - `signUp`: `!skipRegister` 이면 `POST /auth/register`(auth=false) 후 `onRegistered()`; `login`; 캐릭터 지정(best-effort): `GET /characters?size=100`(auth=false) 에서 `svgKey == character.value` 인 `id` 로 `PATCH /me/character`, `ApiException`/`NetworkException` 은 삼킨다; 마지막 `GET /me` 로 계정 반환(실패는 전파).
  - 이메일·닉네임은 서버도 정규화하지만 앱에서도 `trim()` 해서 보낸다. 비밀번호는 trim 하지 않는다.
- `ApiMessages.kt`: `fun Throwable.toUserMessage(): String` — `NetworkException` → "네트워크 연결을 확인해 주세요", `ApiException` 코드별: `EMAIL_TAKEN` "이미 가입된 이메일이에요", `INVALID_CREDENTIALS` "이메일 또는 비밀번호가 올바르지 않아요", `VALIDATION_ERROR` "입력한 내용을 다시 확인해 주세요", `INVALID_TOKEN`/`TOKEN_EXPIRED` "다시 로그인해 주세요", 그 외 "잠시 후 다시 시도해 주세요".

### C. 세션/앱 연결
- `~/core/AppSession.kt`: `State` 에 `RESTORING` 추가. 생성자에 `private val onCleared: () -> Unit = {}` 추가(기본값 유지 → 기존 호출 호환). `fun signIn(account: MyAccount)`(account 설정 + `SIGNED_IN`; 기존 무인자 `signIn()` 은 **제거**해 목업 로그인이 남지 않게 한다), `signOut()` 과 `withdraw()` 는 `onCleared()` 도 호출. `fun beginRestoring()`/`failRestoring()` 같은 최소 전이만 추가(예: `state = RESTORING`, `state = SIGNED_OUT`).
- `~/app/AppViewModel.kt`: `KeystoreTokenStore(application)`, `ApiClient(BuildConfig.API_BASE_URL, tokens, onSessionExpired = { 메인 스레드에서 appSession.signOut() })`, `AuthRepository`, `AppSession(initialState = if (auth.hasSession) RESTORING else SIGNED_OUT, onCleared = { auth.clearSession(); dataStore.reset() })`. `init` 에서 `RESTORING` 이면 `viewModelScope.launch { restore }`: 성공 → `dataStore.setMyProfile(account.profile); appSession.signIn(account)`; `ApiException` → `appSession.failRestoring()`(토큰은 ApiClient 가 이미 정리); `NetworkException` → `failRestoring()` 하되 토큰 보존. `val auth: AuthRepository` 를 화면에 노출. (`onSessionExpired` 는 IO 스레드에서 호출되므로 `Handler(Looper.getMainLooper()).post` 또는 `viewModelScope.launch(Dispatchers.Main)` 로 상태를 바꾼다.)
- `~/app/RootScreen.kt`: `when (state)` 에 `RESTORING -> Box(Modifier.fillMaxSize().background(HColors.bg))` 추가. `AuthNavHost(session, store, auth)` 로 `auth` 를 전달.
- `~/app/AuthNavigation.kt`: `AuthNavHost(session, store, auth: AuthRepository)`. A2 에서 약관 값도 보관: `var signupAgreeTerms by remember{...}`, `var signupAgreePrivacy ...`. `onNext` 가 `(email, agreeService, agreePrivacy)` 를 넘기도록 `SignupAccountScreen` 시그니처 수정. A3 호출에 `password = signupPassword`, 약관 값, `auth` 전달. `onSocial` 은 더 이상 `SignupProfile` 로 이동하지 않고 LoginScreen 이 토스트를 띄우도록 콜백 없이 처리(아래 D). `onReset` 도 `PasswordResetScreen` 으로 이동하지 않는다. `AuthRoute.SignupProfile(provider, email)`/`PasswordReset` 타입은 남겨 두되 사용하지 않는다(타입 제거는 범위 밖).

### D. 화면
- `~/feature/auth/LoginScreen.kt`: 시그니처에서 `onReset`, `onSocial` 제거하고 `auth: AuthRepository` 추가(`store` 는 계속 사용). 로그인 버튼: `rememberCoroutineScope()` 로 `auth.login(email, password)` 호출 → 성공 시 `store.reset(); store.setMyProfile(account.profile); focus.clearFocus(); session.signIn(account)`, 실패 시 `toast.show(e.toUserMessage())`. 진행 중 `loading` 플래그로 버튼 `enabled=false`/중복 탭 방지, 이메일 또는 비밀번호가 비면 요청하지 않음(`email.isBlank() || password.isEmpty()`). 최상위를 `Box` 로 감싸 `HToastHost(toast)`. "비밀번호 찾기"와 간편 로그인 3개 클릭 → `toast.show(COMING_SOON)`.
- `~/feature/auth/SignupAccountScreen.kt`: `onNext: (String, Boolean, Boolean) -> Unit` 로 변경, 호출은 `onNext(email.trim(), service, privacy)`.
- `~/feature/auth/SignupProfileScreen.kt`: 파라미터에 `password: String, agreeTerms: Boolean, agreePrivacy: Boolean, auth: AuthRepository` 추가. "시작하기": `avatar` 가 `AvatarContent.Character` 인 경우만 사용(사진 칸은 숨김 — 아래 E). `registered` 를 `rememberSaveable` 로 보관, 중복 탭 방지 `loading`. 성공 → `store.reset(); store.setMyProfile(account.profile); session.signIn(account)`. 실패: `ApiException` 코드가 `EMAIL_TAKEN` 이면 토스트 후 `onBack()` 으로 A2 에 돌아가고(`registered` 는 false 인 상태이므로 문제 없음), 그 외는 토스트만. 목업 `MyAccount(...)` 생성/`Instant.now()` 코드 삭제. `provider != EMAIL` 분기(도달 불가)는 두지 않는다.
- `~/feature/auth/LoginAccountFactory.kt` **삭제**, `android/app/src/test/.../feature/auth/LoginAccountFactoryTest.kt` **삭제**.
- `~/designsystem/components/CharacterPicker.kt`: 파라미터 `showPhotoCell: Boolean = true` 추가. `false` 이면 사진 칸을 만들지 않는다(캐릭터 7개만 4열 그리드). 기본값 `true` 라서 기존 호출(그룹 내 프로필 16-1, 갤러리 등)은 변하지 않는다. A3 에서만 `false`.
- "준비 중" 공통 문구: `~/core/ComingSoon.kt` 에 `const val COMING_SOON = "아직 준비 중이에요"` (한 줄 파일).
- `~/feature/mypage/MyPageScreen.kt`: "프로필 편집" 버튼과 "회원 탈퇴" 행의 클릭을 `toast.show(COMING_SOON)` 로 (루트에 `HToastHost`). 로그아웃 확인 후 `session.signOut()` 은 그대로(콜백이 토큰·DataStore 정리). `onProfileEdit`/`onWithdraw` 파라미터는 시그니처 변경 최소화를 위해 남기되 호출하지 않아도 된다(`MainNavigation` 수정 최소화) — 파라미터를 제거할 경우 `MainNavigation.kt` 호출부도 같이 수정.
- `~/feature/mypage/UserInfoScreen.kt`: 닉네임 행(`onProfileEdit`), 로그인 연결 `ProviderRow` 의 `onLink`/`onUnlink`(확인 알림 포함), "비밀번호 변경" 행 클릭을 모두 `toast.show(COMING_SOON)` 로. 비밀번호 변경 행의 활성 표시(`canChangePassword`)는 그대로 둔다(문구 변경 없음).
- 17-1 `ProfileEditScreen`, 17-2 `ChangePasswordScreen`, 17-5 `WithdrawScreen`, `PasswordResetScreen` 과 `MainNavigation` 의 라우트는 **삭제하지 않는다**(도달 경로만 막음).
- 알림 설정(17-3)·앱 정보 등 로컬 전용 화면은 변경하지 않는다.

### E. 문서
- `docs/FRONTEND/ANDROID.md` 59행 문장을 "서버 연결(2A)부터 OkHttp(+kotlinx-serialization) 사용. Retrofit·Hilt·Room 은 쓰지 않아요." 로 갱신하고, §6 근처에 2A 한 단락(토큰 저장 방식, `API_BASE_URL` 설정, debug 평문 HTTP)을 추가.
- `docs/BACKEND/CONTRACT_DECISIONS.md` 는 수정하지 않는다(§1 은 이미 "반영됨").

## Implementation Steps

0. `git status` 로 현재 트리 확인(백엔드 1·2단계 변경이 미커밋일 수 있음 — 건드리지 말 것). JDK 17 로 Gradle 이 도는지 확인(`cd android && .\gradlew.bat --version`). 베이스라인 `.\gradlew.bat testDebugUnitTest` 가 통과하는지 먼저 확인하고 실패하는 것이 있으면 report 에 기록.
1. **의존성/설정(A)**: 카탈로그·gradle·manifest·debug 오버레이 추가 후 `.\gradlew.bat assembleDebug` 로 빌드 확인.
2. **테스트 먼저(RED)**: Tests Required 의 `ApiClientTest`, `KeystoreTokenStore` 제외 로직 테스트, `AuthRepositoryTest`, `AppSession` 전이 테스트를 작성하고 컴파일 실패/RED 확인.
3. `TokenStore`/`InMemoryTokenStore`, `ApiException`, DTO, `ApiClient` → `ApiClientTest` GREEN.
4. `AuthRepository`, `ApiMessages` → `AuthRepositoryTest` GREEN.
5. `KeystoreTokenStore` 구현(JVM 테스트 불가 → 코드 리뷰 + 에뮬레이터 수동 확인).
6. `AppSession` 변경 + `AppViewModel` 연결 + `RootScreen`.
7. 화면 수정(D): `CharacterPicker` 옵션 → A1 → A2/A3 → `AuthNavigation`; `LoginAccountFactory` 와 테스트 삭제; "준비 중" 3곳(MyPage, UserInfo, Login).
8. `.\gradlew.bat testDebugUnitTest assembleDebug lint` 전부 통과.
9. 백엔드가 떠 있는 환경에서 가능하면 에뮬레이터로 수동 시나리오(Validation 3) 실행, 못 하면 report 에 "수동 확인 필요 목록"으로 남긴다.
10. 문서(E) 수정, `.agent/report.md` 작성(변경 파일, 명령·결과, 수동 확인 여부, 미해결).

## Constraints

- **범위 밖 금지**: 그룹/미션/제출 API 연결, `PATCH /me/password`, 프로필 편집·사진·탈퇴 서버 연결, FCM, iOS. `DataStore` 의 그룹/미션 목업 동작은 건드리지 않는다(로그인 후에는 이전처럼 `store.reset()` 로 비어 있는 상태에서 시작).
- 새 라이브러리는 OkHttp·coroutines(+테스트용 mockwebserver·coroutines-test) 만. Retrofit/Hilt/Room/Security-crypto 추가 금지.
- 토큰·비밀번호를 **로그/토스트/예외 메시지에 출력하지 않는다**. OkHttp 로깅 인터셉터를 추가하지 않는다.
- 비밀번호는 `AuthNavHost` 메모리에만 두는 기존 방식 유지(저장·`rememberSaveable` 금지).
- 기존 Kotlin 스타일(들여쓰기 4칸, 한 줄 람다 위주, 한국어 사용자 문구는 "~해요" 체) 유지. 무관한 포맷 변경 금지. `MyAccount.mock`, 기존 테스트 유지.
- UI 문구는 한국어, 토스트 길이는 짧게. 새 화면·디자인 추가 금지(토스트만 사용).
- `git commit`/`push` 금지.

## Edge Cases

- 로그인 요청 중 버튼 연타 → 요청은 한 번만(`loading`).
- 이메일 앞뒤 공백, 대소문자 → 보내기 전 `trim()` (서버가 소문자화).
- 비밀번호에 앞뒤 공백이 있어도 그대로 전송.
- 가입 중간 실패: register 성공 → login 실패(네트워크) → 같은 화면에서 "시작하기" 재탭 → register 를 건너뛰고 login 부터. 앱을 종료했다면 A1 로 로그인(캐릭터는 서버 기본값 null → 앱에서 몽실이).
- 가입 중 `EMAIL_TAKEN`: 토스트 + A2 로 복귀(닉네임·캐릭터는 A3 에서 입력 상태가 사라지는 것 허용).
- 캐릭터 목록 호출/지정 실패는 무시하고 진행(마이페이지에 기본 캐릭터로 표시됨).
- `characterKey` 가 서버에서 모르는 값 → `DEFAULT`.
- 동시 요청 N 개가 같은 만료 토큰으로 401 → refresh 는 **정확히 1회**, 나머지는 새 토큰으로 재시도.
- refresh 토큰 회전: refresh 응답의 새 refresh 토큰을 반드시 저장(저장 안 하면 다음 갱신이 실패).
- 재시도 후에도 401 → 무한 루프 없이 종료하고 세션 만료 처리.
- refresh 중 네트워크 오류 → 로그아웃하지 않음, 토큰 보존, `NetworkException`.
- 복구(`restore`) 중 사용자 삭제/토큰 무효 → `SIGNED_OUT`, 토큰 삭제.
- 기기 키스토어 키 손상/백업 복원으로 복호화 실패 → 토큰 폐기 후 로그아웃 상태로 시작(크래시 금지).
- 서버가 꺼져 있을 때: 로그인/가입은 "네트워크 연결을 확인해 주세요" 토스트, 앱이 죽지 않음. 응답 본문이 JSON 이 아닌 경우(프록시 오류 등)도 `ApiException("HTTP_<status>")` 로 처리.
- 화면 회전은 매니페스트에서 세로 고정이지만 프로세스 종료·복구 시 `registered` 는 `rememberSaveable` 로 보존.
- 로그아웃 시 토큰 삭제 + `DataStore.reset()` (이전 사용자의 목업 상태가 남지 않게).

## Regression Risks

- `AppSession` 시그니처/`signIn()` 제거: `LoginScreen`, `SignupProfileScreen` 외 호출처가 없는지 `Grep "signIn("` 로 확인(현재는 두 곳뿐). `AppSession()` 을 직접 만드는 곳은 `AppViewModel` 뿐(테스트 포함 확인됨).
- `AuthNavHost`/`LoginScreen`/`SignupAccountScreen`/`SignupProfileScreen` 시그니처 변경 → `ComponentGalleryScreen` 등에서 이 화면들을 직접 호출하지 않는지 확인(컴파일러가 잡음).
- `CharacterPicker` 에 기본값 있는 파라미터만 추가 → 기존 호출 불변. `showPhotoCell=false` 일 때 `rowIndex==1 && columnIndex==3` 로직(사진 칸 판별)이 7개 그리드에서 오동작하지 않는지 확인(사진 칸이 없으면 판별도 비활성).
- `LoginAccountFactoryTest` 삭제로 테스트 수가 줄어드는 것은 의도. 다른 테스트가 `accountForLogin` 을 쓰지 않는지 확인(현재 없음).
- 매니페스트 변경(`INTERNET`)과 debug 오버레이 병합 오류 → `assembleDebug`/`assembleRelease`(가능하면) 모두 빌드 확인. release 에서 평문 HTTP 가 막혀 있는지 확인.
- `RESTORING` 추가로 `when (state)` 가 exhaustive 하지 않게 되는 곳은 컴파일 에러로 드러남(RootScreen 외에는 없어야 함).
- `kotlinx-coroutines` 버전이 Compose/Lifecycle 이 끌어오는 버전과 충돌하지 않는지 `./gradlew :app:dependencies --configuration debugRuntimeClasspath | findstr coroutines` 로 확인(충돌 시 가장 높은 버전으로 정렬).

## Tests Required

JVM 단위 테스트 (`android/app/src/test/java/com/sapphire/hawe/...`, JUnit4, `MockWebServer`, `runBlocking`/`runTest`)

1. `core/net/ApiClientTest`
   - 2xx 응답 파싱(`ignoreUnknownKeys`), 요청 JSON 본문·`Content-Type` 확인.
   - 비-2xx → `ApiException(status, code, message)`; 본문이 JSON 이 아니면 `code == "HTTP_<status>"`.
   - 서버 종료/연결 실패 → `NetworkException`.
   - `auth=true` 요청에 `Authorization: Bearer <access>`, `auth=false` 에는 헤더 없음.
   - 401 `TOKEN_EXPIRED` → refresh 호출(본문 `{refreshToken}`) → 새 토큰 저장(두 토큰 모두) → 원래 요청 재시도 성공.
   - **single-flight**: MockWebServer `Dispatcher` 가 "옛 access 토큰이면 401 TOKEN_EXPIRED, 새 토큰이면 200, `/api/auth/refresh` 는 호출 횟수를 센다" 로 동작할 때 동시에 3개 요청 → 모두 성공, refresh 호출 **1회**.
   - refresh 가 401 → 토큰 삭제 + `onSessionExpired` 정확히 1회 + `ApiException`.
   - 401 `INVALID_TOKEN`(인증 호출) → 토큰 삭제 + `onSessionExpired`, refresh 호출 없음.
   - 비인증 호출의 401 `INVALID_CREDENTIALS` → 토큰/콜백 무변화, refresh 호출 없음.
   - refresh 중 네트워크 오류 → 토큰 보존, `onSessionExpired` 미호출, `NetworkException`.
   - 재시도 후 다시 401 → 루프 없이 세션 만료 처리.
2. `core/net/AuthRepositoryTest`
   - `login`: 호출 순서 login → me, 토큰 저장, 변환 결과(`email`, `nickname`, `characterKey` null → `ProfileCharacter.DEFAULT`, `createdAt` → `Instant`).
   - `signUp`: 순서 register → login → characters → PATCH(`characterId` 가 응답의 해당 `svgKey` 의 id) → me. 요청 본문에 `agreeTerms/agreePrivacy`, 정규화된 email/nickname.
   - `signUp(skipRegister=true)` 는 register 를 호출하지 않는다; `onRegistered` 는 register 성공 직후에만 호출.
   - 캐릭터 목록/PATCH 실패(`ApiException`/`NetworkException`)는 무시되고 `MyAccount` 반환.
   - register 가 409 `EMAIL_TAKEN` → 예외 전파, 토큰 저장 없음. login 401 → `INVALID_CREDENTIALS` 전파.
   - `restore`: me 성공/401(토큰 삭제됨)/네트워크 오류(토큰 보존).
3. `core/AppSessionTest`: `RESTORING` 전이(`beginRestoring` → `signIn(account)` / `failRestoring`), `signOut()`/`withdraw()` 가 `onCleared` 를 호출, `signIn(account)` 가 `account` 와 `SIGNED_IN` 설정.
4. `core/net/ApiMessagesTest`: 코드별 사용자 문구 매핑(`NetworkException` 포함).
5. (선택) `designsystem` 쪽 `CharacterPicker(showPhotoCell=false)` 는 UI 라 단위 테스트 대상 아님 → 수동 확인.
수정/삭제
6. `LoginAccountFactoryTest.kt` 삭제. 나머지 기존 테스트는 변경 없이 통과해야 한다.

## Validation Steps

1. `cd android` 후(PowerShell) `.\gradlew.bat testDebugUnitTest assembleDebug lint` 모두 통과. 실패 시 로그 요약을 report 에. (JDK 17 사용)
2. 가능하면 `.\gradlew.bat assembleRelease` 도 빌드 확인(서명 설정이 없어 실패하면 `assembleRelease` 대신 `lintRelease` 또는 `compileReleaseKotlin` 로 대체하고 사유를 기록).
3. **수동 확인 (사람 또는 에뮬레이터 접근 가능 시)** — 백엔드: 루트에서 `.\mvnw.cmd spring-boot:run`(MariaDB 실행 중, 2단계 마이그레이션 적용된 DB). 앱: 에뮬레이터에서 debug 실행.
   - 가입: A1 → 회원가입 → A2(이메일·비밀번호·약관) → A3(캐릭터 선택, "내 사진" 칸 없음, 닉네임) → 시작하기 → 홈(01/04) 진입. 마이페이지에서 닉네임·이메일·캐릭터가 서버 값과 일치(서버에서 `GET /api/me` 와 대조).
   - 앱을 완전히 종료 후 재실행 → 로그인 화면 없이 홈으로 복귀.
   - 로그아웃 → A1. 틀린 비밀번호 → "이메일 또는 비밀번호가 올바르지 않아요" 토스트. 같은 이메일로 다시 가입 → "이미 가입된 이메일이에요" 후 A2 로 복귀.
   - 서버 중지 후 로그인/가입 → "네트워크 연결을 확인해 주세요", 앱이 죽지 않음.
   - 간편 로그인 3개, 비밀번호 찾기, 마이페이지 "프로필 편집"/"회원 탈퇴", 유저 정보의 닉네임·로그인 연결·비밀번호 변경 → 모두 "아직 준비 중이에요" 토스트.
   - (선택) 만료 처리: 서버 `jwt.access-token-validity-seconds` 를 일시적으로 10 으로 낮춰 재기동 → 로그인 후 10초 뒤 화면 복귀/재진입에서도 로그아웃되지 않고 자동 갱신되는지(토큰 갱신은 이번 단계에서 호출처가 `GET /me` 복구뿐이므로, 앱 재실행으로 확인).
4. `Grep` 으로 잔존 확인: `accountForLogin`, `session.signIn()` 무인자 호출, `MyAccount(` 목업 생성(`MyAccount.mock` 제외)이 `feature/auth` 에 없음.
5. report 에 수동 확인 수행 여부를 정직하게 적는다(못 했으면 "미확인" 으로).

## Potential Merge Conflicts

- **백엔드 변경과는 파일이 겹치지 않는다.** 현재 트리의 1·2단계 백엔드 변경(`src/**`, `docs/BACKEND/**`)은 미커밋일 수 있으니 건드리지 않는다. 계약 입력은 위 Findings 의 JSON 을 사용(백엔드가 커밋되기 전에도 Android 작업 가능).
- `docs/FRONTEND/ANDROID.md`: 이번 단계에서만 수정. iOS 쪽 문서/코드와 충돌 없음.
- **이후 단계(3A: 그룹)**는 `DataStore.createGroup/lookUpInvite/join/leave`, `MainNavigation.kt`, 홈/그룹 화면을 고친다. 이 단계에서 `AppViewModel`/`AppSession`/`RootScreen`/`AuthNavigation` 을 확정해 두므로 3A 는 `ApiClient`/`AuthRepository` 를 그대로 재사용한다. `MainNavigation.kt` 는 이번 단계에서 최소한만(필요 시 호출부 시그니처) 건드린다 — 3A 와 겹치는 파일이므로 불필요한 수정 금지.
- `android/app/build.gradle.kts`, `libs.versions.toml` 는 이후 단계에서도 의존성이 추가될 수 있는 공유 파일이다(FCM 등). 이번 변경은 최소로.
