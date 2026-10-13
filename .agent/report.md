# B1 group profile implementation report

## Files changed

- Schema and backend: `src/main/resources/db/migration/V1__initial_schema.sql`; `src/main/java/com/sapphire/hawe/group/{controller,service,dto,domain,dao}`; `src/main/java/com/sapphire/hawe/{submission,comment}/dao`.
- Tests: `src/test/java/com/sapphire/hawe/group/{controller,service,dao}/GroupProfile*Tests.java` and `GroupMemberMapperTests.java`.
- Contract docs and Postman: `docs/BACKEND/API_DESIGN.md`, `BACKEND_DB.md`, `CONTRACT_DECISIONS.md`, `CONTRACT_WORK_PLAN.md`, `postman/HAWE_GROUP.postman_collection.json`.
- `.agent/plan.md` was already modified when this task started and was not edited.

## Changes

- Added nullable group member nickname, character, and image overrides to V1, including a character FK.
- Added self-service character and image profile PUT endpoints and an idempotent DELETE endpoint. Image uploads validate content and size, delete new files after transaction rollback, and delete replaced files after commit.
- Applied one shared display-profile SQL rule to member lists, group/guest previews, submissions, and comments. `GET /groups/{groupId}/members` now returns `hasGroupProfile`.
- A former member's submission or comment displays the basic user profile because the group member join is optional.
- Updated API/database/contract docs and added three Postman requests.

## Validation

- Local database had 0 rows in `users`, `groups`, `group_members`, `missions`, `submissions`, and `comments` before reset. Backed up on 2026-10-13 to `C:\Users\kimch\AppData\Local\Temp\hawe_backup_B1_20261013.sql` (19,194 bytes), then recreated local `hawe`. Flyway V1/V2 reapplied successfully.
- `mvnw.cmd clean test -q` using Java 25: 170 tests, 0 failures, 0 errors, 0 skipped. New integration tests ran against MariaDB.
- `mvnw.cmd package -q -DskipTests`: passed; `target/hawe-0.0.1-SNAPSHOT.jar` created. Java compilation served as typecheck. No separate lint command is configured.
- Postman JSON parsed successfully, Korean request text checked, and three requests are in folder 6.
- `git diff --check`: passed. The four queries reference CASE expressions defined only in `GroupMemberMapper`.
- A live-server manual smoke test was not run; MockMvc covered the HTTP flow.

## Risks and scope

- V1 changed, so other local databases that already applied V1 need a backup and controlled reset. Shared databases need separate migration coordination.
- Images left by membership or group deletion remain for B4 cleanup. Android connection remains for A3.
- Likely merge conflicts: V1 schema, group mappers/service, submission/comment mappers, backend contracts, and Postman collection.
- Plan adjustment: repository guidance's `docs/FEATURE_SPEC.md` and `docs/BACKEND_DB.md` paths do not exist; used current `docs/FEATURE/` and `docs/BACKEND/` documents. Existing `.agent/plan.md` change was preserved.
