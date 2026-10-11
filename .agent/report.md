# Mission read APIs implementation report

## Files changed

- Added `src/main/java/com/sapphire/hawe/mission/controller/MissionController.java` with authenticated today, single mission, and template catalog GET routes.
- Added `mission/service/MissionService.java` and `MissionErrorCode.java` for group membership checks, mission responses, params and target resolution, unpublished slot timing, and mission 404 errors.
- Added `mission/dao/MissionMapper.java` and `MissionRow.java` for read-only MyBatis queries and `submittedByMe` projection.
- Added `mission/domain/MissionTemplate.java` with the 16 phase-1 templates in document order.
- Added `mission/dto/MissionResponse.java`, `TodayMissionsResponse.java`, `MissionTemplateResponse.java`, and `MissionTemplateListResponse.java`.
- Added `src/test/java/com/sapphire/hawe/mission/service/MissionServiceTests.java`, `mission/domain/MissionTemplateTests.java`, and `mission/dao/MissionMapperTests.java`.
- Overwrote `.agent/report.md` for this task. No existing production source, migration, group code, or docs were changed.

## Validation

- Java 25: `.\mvnw.cmd test '-Dtest=MissionServiceTests,MissionTemplateTests'` passed (11 tests).
- Java 25: `.\mvnw.cmd test` passed (50 tests, 0 failures, 0 errors), including the new MariaDB mapper test. Flyway validated all four migrations; the local schema is at V4.
- Java 25: `.\mvnw.cmd package -DskipTests` passed and built the JAR.
- `git diff --check` passed. No lint or separate typecheck task is configured.
- Manual HTTP smoke checks through Swagger/Postman were not run. Spring Boot context and mapper registration were exercised by database tests.

## Remaining risks and assumptions

- Today uses MariaDB `CURDATE()` for row selection and JVM `LocalDate.now()` for response date and `nextPublishAt`. Different database and JVM time zones can disagree near midnight; the API document leaves this open.
- The catalog contains only the 16 documented phase-1 templates. Unknown stored template codes remain readable with `media: null`.
- Mission publication is a future feature; tests insert mission rows directly.

## Potential merge conflicts and plan adjustments

- New `mission` package only; `.agent/plan.md` had an existing uncommitted change and was left untouched. No plan design changes were needed.
