# Review: Group module V3 follow-up

Reviewed: working-tree diff for `group/*`, `MissionProperties`, `application.properties`, tests, Postman collection, against `.agent/plan.md`, `.agent/report.md`, and `MISSIONS_API.md` section 2.

## Verified
- `GroupMapper` SQL uses only existing V3 columns; no remaining references to `mission_publish_time`/`mission_deadline_time` outside V1/V3.
- Create defaults (`CASUAL`, 1), update keeps unspecified fields, owner check runs before validation and before any slot write.
- Slot rules match the spec: `missionSlots` non-null = delete-all then insert; `[]` clears; null keeps; both-null slots are not stored; lowering the count without `missionSlots` trims `slot_no > count`; with `missionSlots` the full replace already covers it; slotNo validated against the effective (new or current) count; duplicates, bad time format, out-of-range count and bad `relationType` all map to `VALIDATION_ERROR`.
- Response always expands slots 1..N with config fallbacks and a correct `custom` flag.
- `create`/`updateSettings` are `@Transactional`, so group + slot writes are atomic. `MissionProperties` placed in `global/config`, registered like `JwtProperties`; only the three needed keys were added.
- No migrations, docs, or unrelated modules were changed (deleted prompt files and docs edits pre-date this task).
- Reported: `GroupServiceTests` 12 pass, package compiles.

## P0
None.

## P1
None.

## P2
### File / Location
Validation coverage (report "Validation") / local `hawe` DB
### Problem
DB-backed tests (`GroupMapperTests` incl. the new slot round trip, `PokeMapperTests`, `CharacterMapperTests`, `HaweApplicationTests`) and the live API smoke test have not run: the local DB has V4 applied before V3, so Flyway rejects V3 (9 context errors). V3 DDL, the new slot mapper SQL, and the `relation_type`/`daily_mission_count` mapping are therefore unverified against MariaDB.
### Why It Matters
Mapper SQL or type-mapping mistakes (e.g. `TINYINT UNSIGNED` -> `int`, `TIME` -> `LocalTime`) would only surface at runtime.
### Recommended Fix
No code change. Recreate the `hawe` database, then run `.\mvnw.cmd test` and the create/patch/get smoke test from the plan before merging.

## 결론: 승인 가능
(코드 수정 불필요. DB를 비운 뒤 전체 테스트와 API 스모크 테스트로 확인한 다음 merge.)
