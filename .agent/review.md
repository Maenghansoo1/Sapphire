# Review: Mission read APIs

Reviewed: new `mission` package (controller, service, dao, domain, dto) and its tests against `.agent/plan.md`, `.agent/report.md`, and `MISSIONS_API.md` section 2.

## Verified
- Routes, response fields, and error behavior match the spec: group existence (404) is checked before membership (403); single-mission checks existence (404) before membership (403); `missions[]` ordered by `slot_no`; past-deadline missions still returned.
- `nextPublishAt` only considers slots `1..dailyMissionCount` that have no mission today, uses slot `publish_time` else the configured default, and returns `null` when all are published; a mission with `slot_no > dailyMissionCount` does not affect it.
- `targetUserId` is removed from `params` and replaced by `target {userId, nickname}`; a missing user omits `target`; malformed or null JSON yields `{}`; unknown template codes leave `media` null instead of failing.
- Mapper SQL selects no answer columns (`answer_text`/`answer_aliases`), is read-only, and `submitted_by_me` is computed per requesting user. Jackson 3 `ObjectMapper` is used.
- No existing production files, migrations, or docs were modified; only new files under `mission/` and its tests.
- Reported: full `.\mvnw.cmd test` passed (50 tests, 0 failures) against a DB where Flyway applied V1-V4, including the new `MissionMapperTests`; this also confirms the earlier V3/group work (DB tests, slot mapper, `relation_type` mapping) now runs green.

## P0
None.

## P1
None.

## P2
### File / Location
`mission/domain/MissionTemplate.java` (`paramsSchema` / `payloadSchema` built with `Map.of(...)`)
### Problem
`Map.of` has no guaranteed iteration order (it varies per JVM run), so keys inside `paramsSchema`/`payloadSchema` in `GET /api/mission-templates` can come back in a different order between restarts.
### Why It Matters
Only cosmetic (JSON object key order carries no meaning), but it makes responses non-deterministic for snapshot tests or diffs and for anyone reading the payload.
### Recommended Fix
Optional: build these with an insertion-ordered map (e.g. a small helper returning `LinkedHashMap`/`Collections.unmodifiableMap`). Not required for merge.

## Notes (no action)
- Manual HTTP smoke test (Swagger/Postman) was not run; worth a quick check before relying on it from the frontend.
- `MissionService` `@EnableConfigurationProperties(MissionProperties.class)` duplicates the registration on `GroupService`; harmless and as planned.
- Time-zone mismatch between SQL `CURDATE()` and JVM `LocalDate.now()` remains an open decision (MISSIONS_API.md section 11 item 3).

## 결론: 승인 가능
