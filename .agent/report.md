# Mission reroll implementation report

## Files changed

- Backend: `MissionPublisher`, `MissionMapper`, `DevMapper`, `DevMissionService`, `DevDtos`, and `DevController` under `src/main/java/com/sapphire/hawe`.
- Android: `DevApi.kt`, `DevRepository.kt`, `DataStore.kt`, `MissionScreen.kt`, and `DevToolsScreen.kt` under `android/app/src/main/java/com/sapphire/hawe`.
- Tests: `MissionPublisherTests.java`, new `DevMissionServiceTests.java`, `DevRepositoryTest.kt`, and `DataStoreTest.kt`.
- This report. `.agent/plan.md` was already modified when work began and was not edited by me.

## What changed

- Added `POST /api/dev/missions/{missionId}/reroll`. It validates the optional template and current mission, deletes mission submissions and their dependent records plus pokes, then updates generated fields on the same mission row. The mission ID, date, slot, publication time, and deadline remain unchanged. An unspecified template is randomly selected from the configured pool, excluding the current template when another exists. Generation uses a random seed and the existing context, recent mission, and target selection rules.
- Added Android repository support and confirmed reroll actions in the mission screen and developer tools. The developer tools offer random and selected template rerolls and log the returned content.
- Cleared local answer, feed, poke, member, and summary state on reroll. Current mission refresh also replaces stale cached state when the content, template, or spec changes despite the stable ID.

## Verification

- `mvnw.cmd clean test -q`: passed with local MariaDB. This was run after the backend implementation and initial regression tests.
- `mvnw.cmd -q '-Dtest=MissionPublisherTests,DevMissionServiceTests' test`: passed after the final target parameter test.
- `mvnw.cmd package -DskipTests -q`: passed.
- `gradlew.bat :app:testDebugUnitTest :app:assembleDebug`: passed after the final Android UI edit.
- `gradlew.bat :app:lintDebug`: passed.
- `git diff --check`: passed. Inspected the final diff; implementation edits are limited to the files listed above. Git reported line ending conversion notices only.
- MariaDB API smoke test: reroll kept the ID, explicit `TEXT_REVIEW` and subsequent random templates worked, content changed, a submitted answer changed from one row to `[]`, and `submittedByMe` became false. Invalid template returned 400; missing mission returned 404. Temporary seeded groups were deleted. The test backend remains running on port 8080; no `adb reverse` setting was changed.

## Plan adjustment and remaining risks

- The plan requested `REQUIRES_NEW` on `MissionPublisher.reroll` while also requiring deletion and update in one transaction. Those conflict: `REQUIRES_NEW` would commit the update separately. I used default `@Transactional` propagation so publisher joins `DevMissionService.reroll` and both operations commit or roll back together.
- Uploaded photo or drawing files from deleted submissions are left in storage as the plan directs. Rerolling a closed mission retains its deadline and does not reopen submissions.
- Device UI behavior was not manually checked. Check developer mode → group → mission → `[개발자] 미션 리롤` → confirm; verify the card and submit state refresh. In developer tools, select a mission and a template, reroll, and verify the result log and card.
- Potential merge conflicts are limited to the touched backend mission/developer files and Android developer mode, mission screen, and repository files. No parallel edits were observed during this task.
