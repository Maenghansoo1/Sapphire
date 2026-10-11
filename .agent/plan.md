# Plan: Mission read APIs (today / single / template catalog)

## Goal
Implement the three read-only mission endpoints in a new `mission` feature package, per `docs/BACKEND/MISSIONS_API.md` section 2 (read "미션 조회" and the template table in section 1-2 first; they are the API contract):
- `GET /api/groups/{groupId}/missions/today`
- `GET /api/missions/{missionId}`
- `GET /api/mission-templates`

No mission creation, scheduler, submission, feed, or AI code. Missions are inserted by a future publisher; for now tests insert rows directly via JDBC.

## Findings
- No `mission` package exists yet. V3 schema is final: `missions(id, group_id, content, category, template, params JSON, slot_no, published_at, deadline_at, mission_date)`, UNIQUE `(group_id, mission_date, slot_no)`; `submissions` has UNIQUE `(mission_id, user_id)`; `group_mission_slots(group_id, slot_no, publish_time, deadline_time)`.
- Reusable existing code (inject, don't copy):
  - `group.dao.GroupMapper.findById(id)` (group existence + `dailyMissionCount`), `group.dao.GroupMemberMapper.existsByGroupIdAndUserId(groupId, userId)`, `group.dao.GroupMissionSlotMapper.findByGroupId(groupId)`.
  - `group.service.GroupErrorCode.GROUP_NOT_FOUND` (404) and `NOT_GROUP_MEMBER` (403) — reuse, as the doc allows.
  - `global.config.MissionProperties(defaultPublishTime, defaultDeadlineTime, maxDailyCount)` for default slot times.
  - `auth.dao.UserMapper.findById(id)` for target nickname lookup.
  - `global.exception.BusinessException` + `ErrorCode` interface; feature enums live in `<feature>/service` (see `group/service/GroupErrorCode.java`, `poke/service/PokeErrorCode.java`).
- Controller style: see `character/controller/CharacterController.java` (`@RequestMapping("/api")`, `@AuthenticationPrincipal Long userId`). All three routes are authenticated by default (not in `SecurityConfig` permitAll) — no security change needed.
- Read-model pattern for MyBatis projections: `group/dao/GroupSummaryRow.java` (plain class with setters, annotation SQL, snake_case -> camelCase mapping already enabled).
- JSON: the project uses Jackson 3 (`tools.jackson.databind.ObjectMapper`, see `JwtAuthenticationFilter`). Inject that `ObjectMapper` bean; do NOT import `com.fasterxml...`. `missions.params` is read from MariaDB as a String.
- "Today" timezone is an open decision (MISSIONS_API.md section 11 item 3). `GroupMapper.findSummariesByUserId` uses SQL `CURDATE()`. Use the same notion here: query with `m.mission_date = CURDATE()` in SQL, and for the response `date` / `nextPublishAt` use `LocalDate.now()` in the service. Add a one-line code comment that the time zone question is open. Do not introduce a `Clock` bean.

## Root Cause / Key Implementation Points
1. **Template catalog (`GET /api/mission-templates`)**: a static in-code enum `MissionTemplate` (package `mission.domain`) with: `code` (enum name, e.g. `TEXT_ACROSTIC`), `media` (`PHOTO|VOICE|TEXT|DRAW|SLIDER`), Korean `name`, `phase` (1), `paramsSchema` (`Map<String,String>`), `payloadSchema` (`Map<String,String>`). Include exactly the 16 phase-1 templates from the section 1-2 table (PHOTO_NOW, PHOTO_GALLERY, PHOTO_COLOR, VOICE_LINE, VOICE_MIMIC, VOICE_IMPRESSION, TEXT_ACROSTIC, TEXT_ONE_WORD, TEXT_REVIEW, TEXT_BALANCE, TEXT_FILL_BLANK, DRAW_SKETCH, DRAW_A_TO_B, SLIDER_POSITION, SLIDER_TASTE, SLIDER_CONSENSUS). The five phase-2 (R type) templates are not designed yet (section 7), so leave them out. Schema value strings follow the doc example style: `"string"`, `"string(3)"`, `"int(0..100, required)"`, `"string(required)"`, etc. `params` column of the table maps to `paramsSchema` keys; `payload` column maps to `payloadSchema` keys (e.g. VOICE_IMPRESSION payload: `file`, `answer`, `alias`). For target-capable templates, `paramsSchema` lists the keys from the table (`targetUserId` is server-assigned: mark it `"long(server-assigned, optional)"` for DRAW_*, and `"long(server-assigned)"` for TEXT_FILL_BLANK).
   Response: `{ "content": [ {template, media, name, phase, paramsSchema, payloadSchema}, ... ] }` in table order.
2. **Mission item (`MissionResponse`)**: `missionId, groupId, slotNo, content, category, template, media, params, publishedAt, deadlineAt, submittedByMe`. `media` is derived from `MissionTemplate.valueOf(template).media`. If a stored `template` code is unknown (future/phase-2 or bad data), do not fail the whole request: use `media = null`-safe fallback (leave `media` null) and return `params` as stored. Dates are ISO-8601 `LocalDateTime` (no zone), matching the doc.
3. **params resolution**: parse the stored JSON string into a `Map<String,Object>` (preserve key order, e.g. `LinkedHashMap`). If it contains `targetUserId`, remove that key and add `target: { userId, nickname }` using `UserMapper.findById`. If the user no longer exists, omit `target`. Empty/`{}` params stays `{}`. A malformed JSON string must not 500: treat as `{}` (and keep it simple; no logging framework changes).
4. **`submittedByMe`**: computed in SQL with `EXISTS (SELECT 1 FROM submissions s WHERE s.mission_id = m.id AND s.user_id = #{userId})`.
5. **Today response**: `{ date, dailyMissionCount, nextPublishAt, missions[] }`.
   - `missions`: missions of the group with `mission_date = CURDATE()` ordered by `slot_no ASC`; empty array if none.
   - `dailyMissionCount`: `Group.getDailyMissionCount()`.
   - `nextPublishAt`: among slot numbers `1..dailyMissionCount` that do NOT yet have a mission today, the earliest effective publish time (`group_mission_slots.publish_time` if non-null, else `MissionProperties.defaultPublishTime`) combined with today's date; `null` when every slot is already published. Do not skip slots whose time has already passed (spec: earliest among unpublished slots).
   - Errors: group missing -> `GROUP_NOT_FOUND` 404 (check first); not a member -> `NOT_GROUP_MEMBER` 403.
6. **Single mission**: `findById`; missing -> new `MissionErrorCode.MISSION_NOT_FOUND` (404). Then membership check on the mission's group -> `NOT_GROUP_MEMBER` 403. Response = one `MissionResponse` (same fields as a `missions[]` item).
7. Order of checks for single mission: existence (404) before membership (403), per spec error list.

## Files / Functions to Change
All new unless noted (package root `com.sapphire.hawe`):
- `mission/controller/MissionController.java`: `@RestController @RequestMapping("/api")`; `GET /groups/{groupId}/missions/today`, `GET /missions/{missionId}`, `GET /mission-templates`.
- `mission/service/MissionService.java`: `getTodayMissions(userId, groupId)`, `getMission(userId, missionId)`, `listTemplates()`; private helpers for params resolution, next-publish calculation, row -> response mapping.
- `mission/service/MissionErrorCode.java`: enum implementing `ErrorCode` (copy structure of `GroupErrorCode`); only `MISSION_NOT_FOUND(HttpStatus.NOT_FOUND, "미션을 찾을 수 없습니다")` now. Do not add the other section-10 codes yet.
- `mission/dao/MissionMapper.java` (`@Mapper`, annotation SQL, no XML): `findTodayByGroupId(groupId, userId)` (returns `List<MissionRow>`, ordered by slot_no), `findById(missionId, userId)` (returns `MissionRow` or null).
- `mission/dao/MissionRow.java`: projection with `id, groupId, content, category, template, params (String), slotNo, publishedAt, deadlineAt, submittedByMe`.
- `mission/domain/MissionTemplate.java`: enum described above (with a `media` enum or String).
- `mission/dto/MissionResponse.java`, `TodayMissionsResponse.java`, `MissionTemplateResponse.java` (records). The templates list wraps `{content: [...]}` — use a small record or the existing `PageResponse` ONLY if it matches `{content: [...]}` without pagination fields; otherwise add a tiny record `MissionTemplateListResponse(List<MissionTemplateResponse> content)`.
- No edits to existing files expected. If `MyBatisConfig` needs the new mapper registered (check whether it scans `@Mapper` packages or lists them), make the minimal change there.

## Implementation Steps
1. `MissionTemplate` enum + `MissionTemplateResponse` + service `listTemplates` + controller route.
2. `MissionErrorCode`, `MissionRow`, `MissionMapper` (SQL), DTO records.
3. `MissionService.getMission` and params resolution (target expansion).
4. `MissionService.getTodayMissions` incl. `nextPublishAt`.
5. Controller wiring; verify mapper is picked up by `MyBatisConfig`.
6. Tests (below), then run validation.

## Constraints
- Read-only: no INSERT/UPDATE/DELETE on missions/submissions in main code. No scheduler, AI, submission, feed, or file-storage code.
- Do not edit migrations, group module, docs, `CLAUDE.md`, `AGENTS.md`. No git commands that change state.
- Follow layering controller -> service -> dao -> dto/domain; annotation-based MyBatis; errors via `BusinessException(ErrorCode)`.
- Never expose answer data (`answer_text`/`answer_aliases`) — not part of these endpoints and they must not be selected.
- Don't add `hawe.mission.*` keys; use existing `MissionProperties`.

## Edge Cases
- Group with no missions today -> `missions: []`, `nextPublishAt` = earliest slot time (today), `dailyMissionCount` from group.
- All slots published -> `nextPublishAt: null`.
- `dailyMissionCount` larger than published count with custom slot rows: slot with only `publish_time` NULL falls back to default publish time.
- A slot_no > `dailyMissionCount` that has a mission today (count was lowered after publishing): still returned in `missions`; it must NOT affect `nextPublishAt` (only slots 1..dailyMissionCount that are unpublished count).
- Mission past `deadlineAt` is still returned (no filtering by time).
- Target user deleted/missing -> no `target` key; `targetUserId` key never leaks in the response.
- Non-member of an existing group -> 403, nonexistent group -> 404 (group existence checked first, so a nonexistent group never yields 403).
- Unknown template code in DB -> request still succeeds with `media` null.
- Midnight/time-zone mismatch between JVM and DB `CURDATE()` can make `date` differ from queried day; accepted for now (open decision), note in report.

## Regression Risks
- New package only; low risk. Make sure a second `@EnableConfigurationProperties`/bean isn't needed: `MissionProperties` is already registered through `GroupService`'s annotation; `MissionService` depends on that bean being present (it is, since `GroupService` is a bean). To avoid a hidden coupling, also add `@EnableConfigurationProperties(MissionProperties.class)` on `MissionService` (duplicate registration is harmless).
- `ObjectMapper` bean type is Jackson 3 — wrong import would fail at startup.
- Mapper scanning: confirm the new `@Mapper` is registered under the manual `MyBatisConfig` setup.

## Tests Required
- `mission/service/MissionServiceTests` (Mockito, same style as `GroupServiceTests`):
  - today: empty list + `nextPublishAt` earliest (default time and custom slot time), all published -> null, lowered-count case, group not found, non-member.
  - single: success with params, `MISSION_NOT_FOUND`, non-member 403, target expansion (`targetUserId` -> `target.nickname`, key removed), missing target user, malformed params JSON -> `{}`.
- `MissionTemplateTests` (plain unit): catalog has 16 entries, unique codes, every code has non-empty `payloadSchema`, order matches doc table; `listTemplates` response shape `{content: [...]}`.
- `mission/dao/MissionMapperTests` (live DB, like `GroupMapperTests`): insert user/group/member/mission(s)/submission via JDBC; check `findTodayByGroupId` ordering by slot_no and `submittedByMe` true/false, `findById` returns null for unknown id. Use `template='PHOTO_NOW'`, `params='{}'`, and distinct `slot_no` per mission.

## Validation Steps
1. `.\mvnw.cmd test -Dtest=MissionServiceTests,MissionTemplateTests` must pass.
2. DB tests (`MissionMapperTests` and the rest of the suite) need a clean `hawe` DB where Flyway can apply V1-V4 (user recreates the DB; do NOT alter Flyway history). If the DB isn't ready, report which tests could not run.
3. `.\mvnw.cmd package -DskipTests` compiles.
4. With a clean DB, run the app, then via Swagger or Postman: register/login, create a group, insert one mission row manually (SQL), call `GET /api/groups/{id}/missions/today`, `GET /api/missions/{id}`, `GET /api/mission-templates`; compare shapes to MISSIONS_API.md section 2 examples; verify 403 as a non-member and 404 for unknown ids.

## Potential Merge Conflicts
- None expected with existing files (new package). Uncommitted changes from the V3 and group tasks are in the worktree (`GroupService`, group DTOs, tests, `application.properties`); this task only reads from group mappers/error codes and must not modify them.
- A future submission/feed task will add to `MissionMapper`/`MissionErrorCode` and likely reuse `MissionTemplate`; keep the enum open to extension (validators later). A future mission publisher will write rows with `template`/`params` shapes this task reads (`targetUserId` in `params`).
