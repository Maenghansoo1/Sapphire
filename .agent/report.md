# Review Implementation Report

## Scope and files changed

The review concerns the group API. No `.agent/plan.md` was present, so I checked each finding against the implementation and API design before editing.

- `src/main/resources/db/migration/V2__unique_group_members.sql`: removes duplicate membership pairs, retaining the earliest row, then adds a unique key on `(group_id, user_id)`.
- `src/main/java/com/sapphire/hawe/group/service/GroupService.java`: maps a duplicate membership insert to `ALREADY_MEMBER`, calculates page offsets as `long`, and checks group existence before leaving.
- `src/main/java/com/sapphire/hawe/group/dao/GroupMapper.java` and `GroupMemberMapper.java`: accept `long` offsets.
- `src/test/java/com/sapphire/hawe/group/service/GroupServiceTests.java` and `dao/GroupMapperTests.java`: add service and MariaDB integration tests.
- `.agent/report.md`: replaced the stale contributor-guide report with this review report.

## Review decisions

| Finding | Decision and reason | Change and validation |
| --- | --- | --- |
| Duplicate joins | Accepted: the check before insert was racy and the database had no pair constraint. | V2 cleanup and unique key; `DuplicateKeyException` becomes `ALREADY_MEMBER`. Service and database tests cover both paths. |
| Missing group tests | Accepted: only a context test existed. | Added service tests for owner creation, token expiry, duplicate join, authorization, partial settings, missing group, and pagination; mapper tests cover SQL mapping, counts, uniqueness, and large offsets. |
| Page offset overflow | Accepted: `int * int` can overflow. | Widened the multiplication and mapper parameter to `long`; tested at `Integer.MAX_VALUE` page. |
| Database time zone | Deferred: `CURDATE()` can disagree with a future mission scheduler, but the spec does not define a shared clock or zone. | No time policy was invented; decide the mission date time zone before changing these queries. |
| Publish/deadline ordering | Deferred: the API permits two times but defines no same-day or overnight deadline rule. | No validation rule was invented; define the intended schedule first. |
| Leaving a missing group | Accepted: other group endpoints return `GROUP_NOT_FOUND` for this case. | Added `requireGroup` and a service test. |
| Deleted commit guide / stale report | Report accepted; file restoration deferred because `docs/COMMIT_MESSAGE.md` was already deleted before this task and its owner or intent is unknown. | Replaced the stale report. Did not alter the unrelated deletion. |

## Verification

- `.\mvnw.cmd test -q`: passed; the migration applied to local MariaDB and all tests passed.
- `.\mvnw.cmd package -q`: passed, including 11 tests (0 failures, 0 errors) and JAR packaging.
- `git diff --check`: passed. No lint command is configured.
- Inspected the final Git diff and new files. The pre-existing `docs/COMMIT_MESSAGE.md` deletion remains outside this task.

## Remaining risks and merge conflicts

- V2 retains the lowest membership `id` when duplicate pairs exist. Review any unusual duplicate rows with differing roles before deploying to shared data.
- Integration tests require the configured local MariaDB and `JWT_SECRET`, as does the existing context test.
- `AGENTS.md` is ignored by `.gitignore`; it was not edited in this review task. The unrelated commit-guide deletion should be reconciled with the other worktree participant before merging.
