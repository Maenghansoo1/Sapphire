# B2 invite code reissue and lookup limit report

## Files changed

- Backend: `GroupController`, `GroupService`, `GroupMapper`, new `InviteCodeResponse`, new `global/ratelimit/AttemptLimiter`, `CommonErrorCode`, and `application.properties`.
- Tests: new `AttemptLimiterTests` and `InviteCodeControllerTests`; updated `GroupMapperTests`, `GroupContractServiceTests`, and `GroupServiceTests`.
- Docs/Postman: Group sections of `docs/BACKEND/API_DESIGN.md`, `CONTRACT_DECISIONS.md`, `CONTRACT_WORK_PLAN.md`, and `postman/HAWE_GROUP.postman_collection.json`.
- Pre-existing uncommitted B3 auth/security/schema changes, `.agent/plan.md`, `.agent/review.md`, and unrelated workspace files were preserved. The B1/B3 planning document renames appeared during this task and were not made by this implementation.

## Changes

- Added `POST /api/groups/{groupId}/invite-code` for owners. The service locks the group row, checks group/membership/ownership in order, generates a new code, retries uniqueness collisions up to five attempts, and returns the code and URL. Updating the row invalidates the old code immediately.
- Added a per-user, in-memory sliding-window limit shared by invite preview and join: 10 unknown/invalid-code failures in 10 minutes block the next lookup with 429 `TOO_MANY_REQUESTS`. A blocked request does not extend the window. Valid-code `ALREADY_MEMBER` and `GROUP_FULL` responses do not count, and success does not reset failures. Expired state is pruned on access and periodically during failure recording.
- Added settings with defaults `hawe.invite.max-failures=10` and `hawe.invite.failure-window=10m`. No schema change or database reset was needed.

## Validation

- `mvnw.cmd clean test -q` with Java 25: 185 tests, 0 failures, 0 errors, 0 skipped. Includes MariaDB mapper and MockMvc tests for rotation, permissions, old-code rejection, joining with the new code, and shared/per-user limiting.
- `mvnw.cmd package -q -DskipTests`: passed; `target/hawe-0.0.1-SNAPSHOT.jar` created. Java compilation served as typecheck. No separate lint command is configured.
- Postman JSON parsed successfully, with three new Korean-labeled requests in folder 7. `git diff --check` passed. No deprecated JWT invite method was found in main code.
- A separate live-server smoke test was not run; the MockMvc integration test covers the HTTP flow.

## Risks and scope

- The limiter assumes one server instance. Multiple instances would count failures separately and require a shared limiter design.
- Android A3 still needs the group screen reissue button and use of the returned invite URL.
- Likely merge conflicts: `GroupService`, `GroupController`, `GroupMapper`, and Group contract/Postman files with B4/B7 or parallel work. B3 remains uncommitted in the shared worktree and its code was not changed by B2.
- The plan matched the current Group code, so no architectural adjustment was needed. Existing repository guidance refers to old documentation paths; current docs are under `docs/FEATURE/` and `docs/BACKEND/`.
