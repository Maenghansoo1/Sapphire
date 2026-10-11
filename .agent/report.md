# Group module V3 follow-up report

## Files changed

- `src/main/java/com/sapphire/hawe/global/config/MissionProperties.java` and `src/main/resources/application.properties`: added shared, bound default slot times and the maximum daily mission count.
- `src/main/java/com/sapphire/hawe/group/domain/Group.java`, `GroupMissionSlot.java`, `src/main/java/com/sapphire/hawe/group/dao/GroupMapper.java`, and `GroupMissionSlotMapper.java`: mapped V3 group fields and added slot insert, lookup, replace, and trim operations.
- `src/main/java/com/sapphire/hawe/group/dto/GroupCreateRequest.java`, `GroupSettingsUpdateRequest.java`, `GroupResponse.java`, `MissionSlotRequest.java`, and `MissionSlotResponse.java`: replaced old group time fields with relation type, daily mission count, and per-slot request and response fields.
- `src/main/java/com/sapphire/hawe/group/service/GroupService.java`: applies create defaults, validates relation type/count/slot numbers/times, atomically stores slot settings, expands all response slots with configured time fallbacks, and preserves owner/member permissions.
- `src/test/java/com/sapphire/hawe/group/service/GroupServiceTests.java` and `src/test/java/com/sapphire/hawe/group/dao/GroupMapperTests.java`: updated old fixtures; added service cases for defaults, custom slots, replacement, clearing, trimming, partial updates, and invalid settings, plus a mapper round trip case.
- `docs/BACKEND/postman/HAWE_GROUP.postman_collection.json`: updated request bodies and assertions to the V3 group contract.
- `.agent/report.md`: this report.

## Validation

- Java 25: `.\mvnw.cmd test '-Dtest=GroupServiceTests'` passed: 12 tests, no failures or errors.
- Java 25: `.\mvnw.cmd test` ran 38 tests: 29 passed and nine errored during Spring context startup. `CharacterMapperTests` (3), `GroupMapperTests` (3), `HaweApplicationTests` (1), and `PokeMapperTests` (2) could not start because the local `hawe` database already has V4 applied and Flyway rejects the new, unapplied V3. No test assertion failures were reported.
- Java 25: `.\mvnw.cmd package -DskipTests` passed, compiling main and test sources and building the JAR.
- `git diff --check` passed with only Git line ending notices. Python's JSON parser accepted the updated Postman collection. A source search found old group time names only in V1/V3 migration SQL.
- No lint or separate typecheck task is configured.

## Remaining risks and assumptions

- Database backed mapper tests and live create/update/get API smoke checks remain unverified until `hawe` is recreated with V1/V2 followed by V3/V4. I did not reset the database, alter Flyway history, or force an out-of-order migration.
- The V3 migration still assumes an empty `missions` table, as documented in the earlier migration report.
- The previously uncommitted V3 migration and `PokeMapperTests` changes, plus unrelated documentation and prompt changes, were left untouched. `GroupMapperTests` was edited on top of the V3 task's existing changes. The Postman collection is the only documentation file changed here.

## Plan adjustments

- Slot trimming runs only when the daily mission count decreases and `missionSlots` is omitted. A full slot replacement already removes out-of-range rows; increasing or retaining the count does not delete rows.
