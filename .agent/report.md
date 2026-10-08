# Implementation Report: Postman Token Refresh

## Files changed

- `docs/BACKEND/postman/HAWE_GROUP.postman_collection.json`: login scripts now save access and refresh tokens plus issue times for users A, B, and C. A collection pre-request script refreshes any saved access token older than 20 minutes through `POST /api/auth/refresh`, updates all three saved values on HTTP 200, and warns on failure. The collection description explains the 30-minute expiry and account recreation when folder 0 is rerun.
- `src/test/java/com/sapphire/hawe/global/security/JwtTokenProviderTests.java`: three unit tests cover expired access tokens (`TOKEN_EXPIRED`), valid access tokens, and refresh tokens rejected as access tokens (`INVALID_TOKEN`).
- `.agent/report.md`: replaced the previous task report with this report.

## Plan verification and adjustment

The existing provider, filter, refresh endpoint, token response, and 30-minute setting match the plan. No server code or TTL was changed. The pre-request script uses `await pm.sendRequest` so token variables are updated before the original request proceeds; the plan's callback form did not explicitly guarantee that order. This is an implementation detail within the planned scope.

## Validation

- JSON parse with Python: passed.
- Postman script syntax with Node's `AsyncFunction`: passed. A local mock run confirmed refresh updates for A and B, including B when A has no saved token.
- `.\mvnw.cmd test -Dtest=JwtTokenProviderTests -q`: passed (3 tests).
- `.\mvnw.cmd test -q`: passed (14 tests, 0 failures, 0 errors).
- `.\mvnw.cmd package -q`: passed, including tests and JAR build.
- `git diff --check`: passed. No separate lint command is configured; Maven compiled the Java code.
- The requested live Postman check with an aged `tokenIssuedAtA` was not run: no Postman or Newman command is available here. The local mock does not replace an app-level run.

## Risks and findings

- The Postman runtime behavior remains unverified in the app. If refresh fails, the script logs a warning and sends the original request with its saved token, as planned.
- Out of scope: `JwtAuthenticationFilter` validates a supplied expired token on routes outside `/api/auth/**`, including permit-all `GET /api/characters`, so that request can return 401 if the client attaches an expired token.
- The final diff contains only the collection change and new test for this task. `.agent/plan.md` was already untracked and was not edited. No merge conflict or unrelated tracked change was found.
