# Plan: Group module follow-up for V3 (relationType / dailyMissionCount / missionSlots)

## Goal
Adapt the already-implemented `group` slice to the V3 schema. V3 (`src/main/resources/db/migration/V3__mission_slots_and_submission_answers.sql`, already written) dropped `groups.mission_publish_time` / `mission_deadline_time` and added `groups.relation_type`, `groups.daily_mission_count` and table `group_mission_slots`. Group create / get / settings APIs must stop using the dropped columns and instead handle `relationType`, `dailyMissionCount`, and per-slot times (`missionSlots`) as specified in `docs/BACKEND/MISSIONS_API.md` section 2 "그룹 설정 연동" and "서버 기본 시각 설정" (read those two subsections first; they are the API contract).

## Findings
- Dropped columns are still used by:
  - `group/dao/GroupMapper.java`: `insert` (L16), `findById` (L21), `update` (L25-26).
  - `group/domain/Group.java`: `LocalTime missionPublishTime / missionDeadlineTime`.
  - `group/dto/GroupCreateRequest.java`, `GroupSettingsUpdateRequest.java`, `GroupResponse.java`: `missionPublishTime`, `missionDeadlineTime` (strings, HH:mm pattern).
  - `group/service/GroupService.java`: `createGroup`, `updateSettings`, `toGroupResponse` (uses `TIME_FORMAT` HH:mm).
  - Tests: `GroupServiceTests` (L46-57 create test, L128-129 and L138-139 group fixture + assertions), `GroupMapperTests` (`insertGroup` L74-75 calls `setMissionPublishTime/DeadlineTime`).
  - `docs/BACKEND/postman/HAWE_GROUP.postman_collection.json` (request bodies/tests using the old fields; verify by grep).
- `GroupController` needs no change (it just passes the DTOs through); `GET /api/groups/{id}` goes through `getGroup` -> `toGroupResponse`.
- `application.properties` currently has NO `hawe.mission.*` keys. `JwtProperties` is a `@ConfigurationProperties` record registered via `@EnableConfigurationProperties(JwtProperties.class)` on `JwtTokenProvider`; follow the same pattern for the new properties record.
- DB column types: `daily_mission_count TINYINT UNSIGNED NOT NULL DEFAULT 1`, `relation_type VARCHAR(20) NOT NULL DEFAULT 'CASUAL'`, `group_mission_slots(group_id, slot_no TINYINT UNSIGNED, publish_time TIME NULL, deadline_time TIME NULL)` with UNIQUE `(group_id, slot_no)`.
- Feature `ErrorCode` enums live in `group/service`; validation failures that the spec calls 400 `VALIDATION_ERROR`: look at `global/exception` (`CommonErrorCode`, `GlobalExceptionHandler`) to see how `@Valid` failures are rendered and which code/exception to use for service-level validation failures. Reuse the existing mechanism; add a `GroupErrorCode` constant only if nothing suitable exists.

## Root Cause / Key Implementation Points
Per MISSIONS_API.md section 2:
1. Request fields (create: all optional except `name`; update: all optional, partial update):
   - `relationType`: one of `COUPLE|FAMILY|FRIEND|RIVAL|CASUAL`. Omitted -> `CASUAL` on create, unchanged on update. Invalid -> 400.
   - `dailyMissionCount`: int 1..`hawe.mission.max-daily-count` (5). Omitted -> 1 on create, unchanged on update. Out of range -> 400.
   - `missionSlots`: array of `{slotNo, publishTime?, deadlineTime?}`; times are `HH:mm`.
2. Slot semantics:
   - Sending `missionSlots` is a FULL REPLACE: delete all existing rows for the group, insert the sent ones. `[]` clears all. Field omitted (null) = keep existing rows.
   - `slotNo` must be within 1..(effective dailyMissionCount), where effective = the new `dailyMissionCount` if sent, else the group's current value. Duplicate `slotNo` in one request -> 400. Time format violation -> 400.
   - Do not insert a row whose `publishTime` and `deadlineTime` are both null (equivalent to no row; keeps "row exists only for customized slots").
   - If `dailyMissionCount` is lowered, delete rows with `slot_no > new count` (also when `missionSlots` is omitted).
   - Do NOT validate deadline vs publish ordering or midnight crossing (undecided, MISSIONS_API.md section 11 item 11).
3. Response (create/get/update) = `{id, name, ownerId, relationType, dailyMissionCount, missionSlots}` where `missionSlots` is always expanded to slots 1..dailyMissionCount, each `{slotNo, publishTime, deadlineTime, custom}`: time = row value if non-null else `hawe.mission.default-publish-time` / `default-deadline-time`; `custom` = row exists and at least one of its times is non-null. Times formatted `HH:mm`.
4. Defaults come from config, not DB: add `hawe.mission.default-publish-time=09:00`, `default-deadline-time=23:00`, `max-daily-count=5` to `application.properties`, bound by a `MissionProperties` record (`@ConfigurationProperties(prefix = "hawe.mission")`, `LocalTime` for times, `int` for max). Only add the three keys used now; `draw-target-probability` and `guess-max-attempts` belong to later features, do not add them.
5. Permissions unchanged: only owner may update settings; members may read.

## Files / Functions to Change
- NEW `global/config/MissionProperties.java` (record; location next to `MyBatisConfig`; or follow wherever fits best, but not in the group package since the mission module will share it). Register with `@EnableConfigurationProperties(MissionProperties.class)` on `GroupService` (same pattern as `JwtTokenProvider`) or on a config class — pick the one consistent with existing code.
- EDIT `src/main/resources/application.properties`: add the three `hawe.mission.*` keys. Check `.env.example` is not needed.
- EDIT `group/domain/Group.java`: remove time fields; add `String relationType`, `int dailyMissionCount` (use `Integer`/`int` consistently with mapper mapping).
- NEW `group/domain/GroupMissionSlot.java` (`groupId`, `slotNo`, `LocalTime publishTime`, `LocalTime deadlineTime`; id optional).
- NEW `group/dao/GroupMissionSlotMapper.java` (`@Mapper`, annotation SQL, same style as `GroupMemberMapper`): `findByGroupId` (ORDER BY slot_no), `insert`, `deleteByGroupId`, `deleteByGroupIdAndSlotNoGreaterThan`.
- EDIT `group/dao/GroupMapper.java`: `insert` -> `(name, owner_id, relation_type, daily_mission_count)`; `findById` select `id, name, owner_id, relation_type, daily_mission_count, created_at`; `update` -> `name, relation_type, daily_mission_count`. Make sure `relation_type`/`daily_mission_count` map to `relationType`/`dailyMissionCount` (check how `owner_id` -> `ownerId` is mapped today in `MyBatisConfig`).
- NEW `group/dto/MissionSlotRequest.java` (`@NotNull Integer slotNo`, optional `publishTime`/`deadlineTime` with the existing `HH:mm` `@Pattern`) and `group/dto/MissionSlotResponse.java` (`int slotNo, String publishTime, String deadlineTime, boolean custom`).
- EDIT `group/dto/GroupCreateRequest.java`: `name` (`@NotBlank @Size(max=100)`), `relationType` (optional, `@Pattern` on the 5 codes), `dailyMissionCount` (`Integer`, optional, `@Min(1)`), `missionSlots` (`@Valid List<MissionSlotRequest>`, optional). Remove the two time fields.
- EDIT `group/dto/GroupSettingsUpdateRequest.java`: same optional fields plus `name`; remove the two time fields. Keep the "all optional / partial update" doc comment accurate.
- EDIT `group/dto/GroupResponse.java`: `(Long id, String name, Long ownerId, String relationType, int dailyMissionCount, List<MissionSlotResponse> missionSlots)`.
- EDIT `group/service/GroupService.java`: inject `GroupMissionSlotMapper` and `MissionProperties`; rework `createGroup`, `updateSettings`, `toGroupResponse` (now needs the group's slot rows and returns the expanded list); add private helpers for validation (max count, slotNo range, duplicates), slot replacement, and slot expansion. `getGroup` loads slots too. Keep `@Transactional` on create/update so group + slots are atomic.
- EDIT `src/test/java/.../group/service/GroupServiceTests.java` and `.../group/dao/GroupMapperTests.java` (see Tests Required).
- EDIT `docs/BACKEND/postman/HAWE_GROUP.postman_collection.json`: replace `missionPublishTime`/`missionDeadlineTime` usage with the new fields (create with `{ "name": ... }`, settings with `dailyMissionCount`/`missionSlots`), update any test scripts asserting old fields.

## Implementation Steps
1. Add `hawe.mission.*` keys + `MissionProperties` record + registration.
2. `Group` domain, `GroupMapper` SQL, new `GroupMissionSlot` + `GroupMissionSlotMapper`.
3. DTOs (request/response/slot).
4. `GroupService` logic (create, update, get, response expansion, validation).
5. Update `GroupMapperTests.insertGroup` and `GroupServiceTests`; add new tests.
6. Update Postman collection.
7. Run validation steps.

## Constraints
- Do not edit any Flyway migration (V1-V4). V3 is final.
- Do not touch `docs/` except the Postman collection (docs are already updated by the owner). Do not edit `CLAUDE.md`/`AGENTS.md`. No git state changes or commits.
- Do not implement missions/scheduling/submission features. No scheduler, no `MissionScheduleService`; only the group settings API.
- Follow the existing layering (controller -> service -> dao -> domain/dto), annotation-based MyBatis SQL, `BusinessException` + feature `ErrorCode`. Don't introduce XML mappers or ENUM types for `relationType` unless clearly consistent with existing code (a `String` with validation is the doc's decision).
- Keep `submittedToday`/`stampedToday` queries and member/invite/join/leave logic unchanged.

## Edge Cases
- Create with only `{"name": "..."}` -> relationType `CASUAL`, count 1, one slot expanded with default times, `custom=false`.
- Update with only `dailyMissionCount` lowered (5 -> 2) and no `missionSlots` -> rows with `slot_no > 2` deleted, rows 1-2 kept.
- Update with `dailyMissionCount: 2` and `missionSlots` containing `slotNo: 3` -> 400.
- Update with `missionSlots` containing `slotNo: 3` but no `dailyMissionCount` while current count is 1 -> 400.
- Duplicate `slotNo` -> 400. `slotNo` 0 or negative -> 400. `dailyMissionCount` 0 or 6 -> 400. Bad `relationType` (e.g. lowercase `family`) -> 400. Bad time `9:00` -> 400.
- Slot with only `publishTime` -> `deadlineTime` falls back to default, `custom=true`.
- `missionSlots: []` -> all rows deleted, all slots `custom=false`.
- Partial update with `name` only must not reset `relationType`/`dailyMissionCount`/slots (the `update` SQL writes the loaded group's current values, so load first then overwrite only provided fields, as `updateSettings` does today).
- MariaDB `TINYINT UNSIGNED` -> Java `int`/`Integer` mapping is fine; no overflow with 1-5.
- `LocalTime` <-> `TIME` mapping in MyBatis for slot rows (the old `Group` already did this with `LocalTime`, so the handler works).
- Non-owner update -> 403 `NOT_GROUP_OWNER` still checked before validation side effects (no slot writes before the owner check).

## Regression Risks
- API contract break (intended): `missionPublishTime` / `missionDeadlineTime` removed from create/update/response. Frontend isn't wired; Postman collection is the only consumer.
- `GroupService` constructor signature changes -> `GroupServiceTests` setup and any other direct construction must be updated (grep for `new GroupService(`).
- `PokeMapperTests`, `CharacterMapperTests`, `HaweApplicationTests` need a Flyway-clean DB; they currently fail only because the local DB has V4 applied before V3 (see Validation Steps).
- Slot replacement is delete-then-insert; make sure it runs inside the same transaction as the group update.

## Tests Required
Update:
- `GroupMapperTests.insertGroup`: stop setting times (new columns have DB defaults; optionally set `relationType`/`dailyMissionCount` explicitly).
- `GroupServiceTests`: fix constructor (mock `GroupMissionSlotMapper`, build a real `MissionProperties(LocalTime.of(9,0), LocalTime.of(23,0), 5)`), replace the two time-based assertions, fix the `Group` fixture helper.
Add (Mockito unit tests in `GroupServiceTests`, same style as existing):
- create with defaults returns relationType `CASUAL`, count 1, 1 expanded slot with `09:00`/`23:00`, `custom=false`; owner membership still inserted.
- create with `dailyMissionCount=3` + `missionSlots` (slot 2 both times, slot 3 publish only) -> 3 expanded slots with correct `custom` and fallback times; slot rows inserted for 2 and 3 only.
- update `missionSlots` full replace: `deleteByGroupId` called, new rows inserted; `[]` clears; `null` leaves slots untouched (no delete/insert).
- lowering `dailyMissionCount` calls `deleteByGroupIdAndSlotNoGreaterThan`.
- validation failures: slotNo out of range, duplicate slotNo, `dailyMissionCount` above max, invalid `relationType` (if validated in the service) -> expected error; non-owner gets `NOT_GROUP_OWNER` with no slot mapper writes.
Add (DB test, like `GroupMapperTests`, needs live MariaDB): `GroupMissionSlotMapper` insert/find/delete round trip and `GroupMapper` insert+findById returns `relationType`/`dailyMissionCount` defaults.
DTO validation (`@Pattern` on relationType / HH:mm) can be covered by the existing validation approach if the repo has one; otherwise skip.

## Validation Steps
1. Prerequisite (user action, not Codex): the local `hawe` DB has V4 applied, so Flyway rejects V3. If V3 is not yet applied there, ask the user to drop/recreate the `hawe` database (or report that DB-backed tests could not run). Do NOT alter Flyway history or force out-of-order migration.
2. `.\mvnw.cmd test` — all unit tests must pass; DB tests must pass once the DB is clean. Report exactly which tests could not run and why.
3. `.\mvnw.cmd package -DskipTests` compiles.
4. If the DB is clean: `.\mvnw.cmd spring-boot:run`, then via Swagger/Postman: register+login, `POST /api/groups {"name":"x"}`, `PATCH /api/groups/{id}/settings` with `dailyMissionCount: 3` and `missionSlots`, `GET /api/groups/{id}`; compare responses with the example in MISSIONS_API.md section 2.
5. `grep -rn "missionPublishTime\|missionDeadlineTime\|mission_publish_time\|mission_deadline_time" src docs/BACKEND/postman` returns only `V1__initial_schema.sql`/`V3` hits.

## Potential Merge Conflicts
- Uncommitted docs edits (`docs/BACKEND/*.md`) are in the worktree; not touched here except the Postman JSON.
- `PokeMapperTests` / `GroupMapperTests` were just edited by the V3 task (uncommitted); this task edits `GroupMapperTests` again — apply on top of the current working copy.
- A future `mission/` module will also read `hawe.mission.*` and `group_mission_slots`; `MissionProperties` should live in a shared place (`global/config`) so it does not need to move later.
- Sequence: this task must land together with / right after V3; the group API is broken without it.
