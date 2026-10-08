# Plan: 401 "만료된 토큰" (TOKEN_EXPIRED) — Postman 테스트 중 access token 만료 대응

## 증상
`DELETE {{baseUrl}}/api/groups/{{groupId}}/members/me` 호출 시 401 + 토큰 만료 메시지.

## 조사 결과 (이미 코드로 확인됨 — 재조사 불필요)
- 서버 로직 버그가 **아니다**. access token 유효기간은 설계상 30분이다
  (`application.properties`: `jwt.access-token-validity-seconds=1800`, `docs/BACKEND/API_DESIGN_ver0.1.md` §0-1에서 확정).
- 만료 경로: `JwtAuthenticationFilter.doFilterInternal` → `JwtTokenProvider.validateAndGetUserId` → `parseClaims`가
  `ExpiredJwtException`을 `BusinessException(TokenErrorCode.TOKEN_EXPIRED)`(HTTP 401, 메시지 "만료된 토큰입니다")로 변환 → 필터가 직접 JSON 응답.
  `GroupController/GroupService`까지 도달하지도 않는다. `/members/me` 엔드포인트 자체와는 무관.
- 원인: Postman 컬렉션(`docs/BACKEND/postman/HAWE_GROUP.postman_collection.json`)은 0번 폴더의 로그인 시 한 번만
  `tokenA/B/C`(access)를 collection variable에 저장하고 **refresh/재발급을 하지 않는다**. 로그인 후 30분이 지나 같은 변수를
  재사용하면(수동 실행, 중간에 멈췄다 재개, 일부 요청만 재실행) 이 오류가 난다.
- 사용자가 적은 메시지("토큰이 만료되었습니다")는 현재 코드의 문자열("만료된 토큰입니다")과 다르다. 사용자가 의역했을 가능성이 크지만,
  구현 후 실제 응답 `code`가 `TOKEN_EXPIRED`인지만 확인할 것 (메시지 문구는 바꾸지 말 것).

## 결정
- **서버 코드와 토큰 유효기간(30분)은 변경하지 않는다.** 설계 확정 사항이고 문제는 테스트 도구 쪽이다.
- 수정은 Postman 컬렉션에 **만료 임박 시 자동 재발급**을 넣는 것으로 한정한다. 서버의 `POST /api/auth/refresh`가 이미 있으므로 새 API 불필요.
- 이 동작이 의도된 것임을 고정하는 단위 테스트를 1개 추가한다.

## 구현 범위

### 1. `docs/BACKEND/postman/HAWE_GROUP.postman_collection.json` (JSON 직접 수정, 유효한 JSON 유지)

a. 0번 폴더의 로그인 3개(A/B/C)의 test 스크립트에서 access 외에 아래도 저장:
   - `refreshTokenA/B/C` = `pm.response.json().refreshToken`
   - `tokenIssuedAtA/B/C` = `Date.now()` (문자열로 저장됨)

b. 컬렉션 최상위 `event`에 **prerequest** 스크립트 추가 (모든 요청 전에 실행):
   - 사용자 `A`,`B`,`C` 각각에 대해, `tokenX`와 `refreshTokenX`가 있고 `Date.now() - Number(tokenIssuedAtX) > 20 * 60 * 1000` 이면
     `pm.sendRequest`로 `POST {{baseUrl}}/api/auth/refresh` (body `{"refreshToken": "<refreshTokenX>"}`, JSON) 호출.
   - 성공(200) 시 `tokenX`, `refreshTokenX`, `tokenIssuedAtX`를 갱신. 실패 시 콘솔에 경고만 남기고 요청은 그대로 진행(테스트가 원인을 드러내게).
   - 로그인 요청(`/api/auth/*`) 자체 전에는 불필요하지만 실행되어도 무해해야 한다 (변수가 없으면 건너뜀).
   - 임계값 20분은 상수 하나로 스크립트 상단에 둔다.
   - 주의: `/api/auth/refresh`는 이전 refresh 토큰을 무효화하지 않는다(stateless). 그래도 응답의 새 refresh 토큰으로 교체한다.

c. 컬렉션 `info.description`에 한 줄 추가: access token 30분 만료 시 자동 재발급되며, 0번 폴더를 다시 실행하면 새 계정이 생성된다는 점.

### 2. 단위 테스트: `src/test/java/com/sapphire/hawe/global/security/JwtTokenProviderTests.java` (신규)
- 순수 단위 테스트(스프링 컨텍스트/DB 불필요). `new JwtProperties(secret(32바이트 이상), accessSeconds, refreshSeconds, inviteSeconds)`로 직접 생성.
- 케이스:
  1. `accessTokenValiditySeconds = -1`로 만든 provider에서 `createAccessToken` 후 `validateAndGetUserId` →
     `BusinessException`의 `errorCode()`가 `TokenErrorCode.TOKEN_EXPIRED`.
  2. 정상 유효기간(예: 1800)에서는 만든 userId가 그대로 반환된다.
  3. refresh 토큰을 `validateAndGetUserId`(access 전용)에 넣으면 `INVALID_TOKEN`.
- 기존 테스트 스타일(JUnit5, `assertThrows`, `BusinessException.errorCode()`)을 따른다. 기존 `GroupServiceTests` 참고.

## 변경하지 말 것
- `JwtTokenProvider`, `JwtAuthenticationFilter`, `SecurityConfig`, `application.properties`의 TTL, `TokenErrorCode` 문구.
- `AGENTS.md`, `CLAUDE.md`. 이번 작업과 무관한 `docs/COMMIT_MESSAGE.md` 삭제 상태도 건드리지 말 것.
- git 커밋/푸시 금지.

## 검증
1. `python -c "import json; json.load(open('docs/BACKEND/postman/HAWE_GROUP.postman_collection.json', encoding='utf-8'))"`로 JSON 유효성 확인.
2. `.\mvnw.cmd test -Dtest=JwtTokenProviderTests` 통과. 이어서 전체 `.\mvnw.cmd test` 통과 (MariaDB, `.env` 필요).
3. 가능하면 수동 확인: 서버를 띄우고 컬렉션에서 `tokenIssuedAtA`를 임의로 과거 값(예: `1`)으로 바꾼 뒤 1-3(그룹 상세)을 실행 →
   콘솔에 refresh 호출이 일어나고 200이 나오는지. (Postman 실행이 불가능하면 report에 "미검증"으로 명시)
4. 실제 만료 확인: `jwt.access-token-validity-seconds`를 임시로 바꿔 재현하지 말 것(설정 변경 금지). 단위 테스트 1번으로 대체.

## 위험 / 참고
- Postman 컬렉션 스크립트는 Postman 앱에서만 실행되므로 자동 테스트가 없다. 수동 확인 결과를 report에 기록할 것.
- (범위 밖, 수정하지 말고 report의 "발견 사항"에만 적을 것) `JwtAuthenticationFilter`는 `/api/auth/**` 외의 모든 경로에서
  만료 토큰을 거부하므로 `permitAll`인 `GET /api/characters`도 만료된 토큰이 붙어 있으면 401이 된다. 프론트 연동 시 재검토 필요.

## 보고
`.agent/report.md`에 변경 파일, 검증 결과(실행한 명령과 결과, 미검증 항목), 위 "범위 밖" 발견 사항을 기록.
