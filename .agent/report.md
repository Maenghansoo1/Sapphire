# Backend Phase 4 implementation report

## Files changed

- New mission timing, deterministic MVP template selection, placeholder generator, publisher, minute scheduler, and after-commit group creation listener under `src/main/java/com/sapphire/hawe/mission/`; new `GroupCreatedEvent` under `group/domain/`.
- Updated mission, group, member, and poke mappers and services for latest mission per slot; added mission number, closesAt, member statuses, resolved target name, and group `todayMission` summary. Enabled scheduling, restricted MyBatis scanning to `@Mapper`, and set `hawe.mission.max-daily-count=1`.
- Added and updated unit and MariaDB integration tests under `src/test/java/com/sapphire/hawe/`.
- Updated `docs/BACKEND/MISSIONS_API.md`, `API_DESIGN.md`, `CONTRACT_DECISIONS.md`, `docs/FEATURE/PROJECT_OVERVIEW.md`, and both Group and Mission Postman collections. Overwrote this report.

## Verification

- JDK 25 `./mvnw.cmd clean test -q`: passed, 92 tests, 0 failures/errors.
- `./mvnw.cmd -q -DskipTests package`: passed. No backend lint or separate typecheck command is configured; Maven compilation passed.
- `git diff --check`: passed. Both Postman collections parse as UTF-8 JSON.
- Live `spring-boot:run` smoke: registered a temporary user, created a group with slot 1 at 00:00, and fetched the current mission, single mission, group list, and template catalog. The mission was number 1, `VOICE_LINE`/`VOICE`, with one member; `publishedAt`, `deadlineAt`, and `closesAt` had `+09:00`; `nextPublishAt == closesAt`; group `todayMission` matched. A group request with `dailyMissionCount=2` returned 400. The server was stopped and the temporary group and user were removed.
- Final diff inspected. Existing uncommitted backend, Android, plan, and review changes were preserved; no commit or push.

## Remaining risks and plan adjustments

- The scheduler scans all group IDs every minute; scaling that scan remains future work. A real future-minute tick was not observed, and the Postman runner was not executed. Scheduler exception isolation and after-commit publishing were covered by tests, and immediate publishing was covered by a committed DB integration test and HTTP smoke.
- Group creation response `missionCount` can be 0 because first publication runs after commit. Submission close enforcement (`MISSION_CLOSED`), AI generation, notifications, and client integration remain later phases.
- The plan described 15 template catalog entries, but the actual enum and live endpoint contain 16. All 16 remain readable; only the specified eight are eligible for automatic MVP publication. Documentation reflects the code.
- This phase touches uncommitted Phase 3 group files and the mission contract needed by future Android 4A and submission Phase 6. Keep those changes together when merging. No schema migration was changed.
