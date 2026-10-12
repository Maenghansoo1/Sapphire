# B3. 비밀번호 변경 `PATCH /api/me/password` + 토큰 버전 (백엔드)

> 범위: 백엔드만(앱 연결은 A3). 알림 무관. **전제: 8단계 커밋 후**(8단계가 `JwtAuthenticationFilter` 에 사용자 존재 확인을 넣는다 — 이 계획은 그 확인을 "존재 + 토큰 버전 일치" 로 확장한다). 결정 근거: `CONTRACT_DECISIONS.md` §1-1 "비밀번호 변경", §1-2 `PATCH /api/me/password`, §1-4 미결(변경 후 기존 토큰 무효화). **계약 보완 1건**: 아래 Key Point 4(응답을 204 → 200 + 새 토큰)를 문서에 반영한다.

## Goal

1. `PATCH /api/me/password` — 현재 비밀번호 확인 후 새 비밀번호로 변경.
2. 변경 후 **이전에 발급된 모든 액세스/리프레시 토큰이 무효**가 된다(다른 기기 로그아웃). 요청한 기기는 응답으로 받은 새 토큰으로 계속 쓴다.

## Findings

- `auth/service/AuthService`: `register`(BCrypt `passwordEncoder.encode`), `login`(`passwordEncoder.matches`), `refresh`(`jwtTokenProvider.validateRefreshTokenAndGetUserId` → `findById` null 이면 `INVALID_TOKEN` → `issueTokens`), `getMe`. `issueTokens(user)` = `createAccessToken(user.getId(), user.getNickname())` + `createRefreshToken(user.getId())`.
- `global/security/JwtTokenProvider`: 클레임 `type`(access/refresh), `nickname`(access 에만). `validateAndGetUserId(token)`(access), `validateRefreshTokenAndGetUserId(token)`. `TokenErrorCode.INVALID_TOKEN`(401)/`TOKEN_EXPIRED`(401).
- 8단계 이후 `JwtAuthenticationFilter(JwtTokenProvider, ObjectMapper, UserMapper)` 가 유효한 액세스 토큰마다 `userMapper.existsById` 로 사용자 존재를 확인하고 없으면 `INVALID_TOKEN`.
- `auth/dto/RegisterRequest` 의 비밀번호 규칙: `@NotBlank @Size(min=8,max=100) @Pattern("^(?=.*[A-Za-z])(?=.*\\d).+$")`. `AuthErrorCode`(EMAIL_TAKEN, INVALID_CREDENTIALS 등). `auth/controller/MeController`(`GET /me`) 와 8단계의 `profile`/`withdraw` 패키지.
- `users` 에 토큰 버전 컬럼 없음. 토큰 생성 호출부: `AuthService`, 테스트 8곳(`createAccessToken(userId, "Test")` 등).
- Android `ApiClient`: 401 `TOKEN_EXPIRED` → refresh 후 재시도, `INVALID_TOKEN` 이면 로그아웃. 즉 서버가 무효 토큰을 `INVALID_TOKEN` 으로 거절하면 앱은 알아서 로그아웃한다.

## Root Cause / Key Implementation Points

1. **토큰 버전**: `users.token_version int NOT NULL DEFAULT 0` (V1 직접 수정). 두 토큰 모두에 클레임 `tv`(정수)를 넣는다. 클레임이 **없는 기존 토큰은 0 으로 간주**(구버전 호환; 로컬 DB 는 어차피 재생성).
2. **검증 위치**: 필터(액세스)와 `AuthService.refresh`(리프레시) 모두 "토큰의 `tv` == DB 의 `token_version`" 확인. 불일치/사용자 없음 → `INVALID_TOKEN`(401). 필터는 8단계의 `existsById` 호출을 **`Integer findTokenVersion(Long id)`**(없으면 null) 한 번으로 대체한다(요청당 PK 조회 1회 유지).
   - `JwtTokenProvider` 에 새 메서드 `AccessClaims parseAccess(String token)` / `RefreshClaims`(또는 하나의 `TokenClaims(userId, tokenVersion)`)를 추가하고 기존 `validateAndGetUserId`/`validateRefreshTokenAndGetUserId` 는 **유지**(테스트·호환). 생성 메서드는 오버로드 `createAccessToken(userId, nickname, tokenVersion)` / `createRefreshToken(userId, tokenVersion)` 를 추가하고 기존 시그니처는 버전 0 으로 위임(기존 테스트 8곳 무수정).
3. **변경 처리**: `@Transactional changePassword(userId, request)` — `findById`(없으면 `INVALID_TOKEN`) → `matches(currentPassword, hash)` 아니면 **400 `WRONG_CURRENT_PASSWORD`**(401 금지: 앱이 토큰 문제로 보고 로그아웃함) → 새 비밀번호 규칙 위반은 요청 DTO 검증으로 400 `VALIDATION_ERROR` → `UPDATE users SET password_hash=?, token_version = token_version + 1 WHERE id=?` → 증가된 버전으로 새 토큰 쌍 발급.
4. **응답(계약 보완)**: `200 { "accessToken": "...", "refreshToken": "..." }`(`TokenResponse` 재사용). 원래 계약은 204 였지만 요청한 기기도 기존 토큰이 무효가 되므로 새 토큰을 돌려줘야 앱이 재로그인 없이 이어진다. 앱은 새 토큰을 저장소에 저장한다. 문서(`CONTRACT_DECISIONS.md` §1-2, `API_DESIGN.md`)의 204 를 이 내용으로 교체하고 이유를 한 줄 적는다.
5. 새 비밀번호가 현재와 같아도 허용(별도 거절 규칙 만들지 않음). 비밀번호 로그를 남기지 않는다.
6. 새 패키지 없이 **`auth`** 에 둔다: `auth/controller/MeController` 에 `@PatchMapping("/me/password")` 추가(또는 `auth/controller/PasswordController`), 서비스는 `AuthService.changePassword`.

## Files / Functions to Change

| 파일 | 변경 |
|---|---|
| `V1__initial_schema.sql` | `users.token_version int(11) NOT NULL DEFAULT 0` |
| `auth/domain/User.java`, `auth/dao/UserMapper.java` | `tokenVersion` 필드, 두 SELECT 컬럼 추가, `@Select Integer findTokenVersion(id)`, `@Update int changePassword(id, passwordHash)`(해시 갱신+버전 증가) — 8단계의 `existsById` 는 필터에서 쓰지 않게 되면 제거해도 되지만, 다른 사용처가 있으면 유지 |
| `global/security/JwtTokenProvider.java` | `tv` 클레임, 오버로드 생성 메서드, `parse…` 메서드 |
| `global/security/JwtAuthenticationFilter.java` | 버전 비교(위 2). 생성자 시그니처는 8단계 그대로 `(JwtTokenProvider, ObjectMapper, UserMapper)` |
| `auth/service/AuthService.java` | `issueTokens` 가 `user.getTokenVersion()` 사용, `refresh` 가 버전 확인, `changePassword` |
| `auth/dto/PasswordChangeRequest.java` (신규) | `record PasswordChangeRequest(@NotBlank String currentPassword, @NotBlank @Size(min=8,max=100) @Pattern(...) String newPassword)` — 패턴은 `RegisterRequest` 와 동일하게(상수로 뽑을 수 있으면 공유, 안 되면 복제) |
| `auth/service/AuthErrorCode.java` | `WRONG_CURRENT_PASSWORD(BAD_REQUEST, "현재 비밀번호가 올바르지 않습니다")` |
| `auth/controller/MeController.java` | `PATCH /api/me/password` → 200 `TokenResponse` |
| 문서 | `API_DESIGN.md` Me 절(8단계가 만든 절에 추가), `CONTRACT_DECISIONS.md` §1-2·§1-4 해결 표기(토큰 버전, 200+새 토큰), `BACKEND_DB.md`(users.token_version), Postman `HAWE_AUTH`/`HAWE_ME` 에 요청 추가 |

## Implementation Steps

1. V1 수정 → **로컬 DB 백업·DROP/CREATE·Flyway 재적용**(보고서에 경로·행 수). 2. 프로바이더 → 도메인/매퍼 → 서비스 → 필터. 3. 컨트롤러·DTO·에러 코드. 4. 테스트 → `.\mvnw.cmd clean test -q`. 5. 문서.

## Constraints

- 토큰 블랙리스트 테이블·Redis 금지(버전 비교만). 비밀번호 해시 알고리즘·정책(8~100자, 영문+숫자) 변경 금지. 소셜 로그인/비밀번호 찾기(메일) 만들지 않음.
- `PATCH /me/password` 실패를 401 로 내리지 않는다(400 `WRONG_CURRENT_PASSWORD`).
- 기존 토큰 생성 시그니처 유지(오버로드 추가). 커밋/푸시·지침 파일 수정 금지.

## Edge Cases

- 현재 비밀번호 틀림 → 400 `WRONG_CURRENT_PASSWORD`, 버전/해시 불변. 새 비밀번호 7자·숫자만·영문만 → 400 `VALIDATION_ERROR`. 필드 누락 → 400. 토큰 없음 401.
- 변경 후: 이전 액세스 토큰으로 어떤 API → 401 `INVALID_TOKEN`; 이전 리프레시 토큰으로 `/auth/refresh` → 401 `INVALID_TOKEN`; 응답으로 받은 새 토큰은 정상; 새 비밀번호로 로그인 OK, 옛 비밀번호 로그인 `INVALID_CREDENTIALS`.
- 연속 두 번 변경 → 버전이 2씩 증가, 첫 변경의 새 토큰도 무효.
- 동시 변경 2건 → 한쪽의 `matches` 이후 다른 쪽이 먼저 커밋돼도 해시/버전은 일관(마지막 승리); 두 번째 요청의 `currentPassword` 가 이미 바뀐 비밀번호와 달라 400 이 날 수 있음(정상).
- `tv` 클레임이 없는 토큰(구버전)은 버전 0 으로 취급 → DB 버전이 0 이면 통과(초기 상태 호환).
- 탈퇴(8단계)된 사용자의 토큰: `findTokenVersion == null` → `INVALID_TOKEN`.

## Regression Risks

- **필터가 모든 요청의 인증 경로**: 버전 비교 실수는 전 API 401 로 번진다 → 기존 통합 테스트(실제 INSERT 한 사용자 + 토큰 생성)가 `tokenVersion` 0 과 일치하는지 확인(오버로드가 0 으로 위임하므로 통과해야 함).
- `JwtAuthenticationFilterTests`, `JwtTokenProviderTests`, `AuthServiceTests`(생성자·`issueTokens` 목 설정) 수정 필요할 수 있음.
- `UserMapper` SELECT 컬럼 추가 → 매핑 누락 시 버전이 항상 0 으로 읽히는 조용한 버그 → 매퍼 테스트로 왕복 확인.
- V1 수정 → checksum 불일치(로컬 재생성).

## Tests Required

- `JwtTokenProviderTests`: `tv` 클레임 포함/파싱, 클레임 없는 토큰 → 0, 기존 메서드 호환.
- `JwtAuthenticationFilterTests`: 버전 일치 통과, 불일치/사용자 없음 → 401 JSON `INVALID_TOKEN`, 제외 경로 무DB.
- `AuthServiceTests`: `changePassword` 성공(해시·버전 증가·새 토큰 버전), 현재 비밀번호 불일치, `refresh` 버전 불일치 거절.
- `UserMapperTests`: `token_version` 왕복, `changePassword` 쿼리가 해시를 바꾸고 버전을 +1.
- MockMvc 통합: 가입→로그인→`PATCH /me/password`→(옛 access `GET /me` 401, 옛 refresh 401, 새 토큰 200, 새 비밀번호 로그인 200, 옛 비밀번호 로그인 401 `INVALID_CREDENTIALS`), 틀린 현재 비밀번호 400 `WRONG_CURRENT_PASSWORD`(토큰 계속 유효).

## Validation Steps

`.\mvnw.cmd clean test -q` 통과 기록. `rg "existsById" src/main`(필터 외 사용처 확인), Postman 파싱·한글 확인, `git diff --check`. 서버 스모크 가능하면 수행(두 기기 시나리오). 보고서에 DB 재생성·계약 보완(204→200) 명시.

## Potential Merge Conflicts

`JwtAuthenticationFilter`/`SecurityConfig`/`JwtTokenProvider`/`UserMapper`/`User`/`AuthService`(8단계 변경 위, B 계열 다른 계획과는 겹침 적음), `V1`, `docs/BACKEND/API_DESIGN.md`·`CONTRACT_DECISIONS.md`. 앱 A3 가 새 토큰 저장을 처리해야 함.
