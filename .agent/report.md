# B10 implementation report

## Files changed

- Added vision configuration and executor: `VisionProperties`, `AsyncConfig`, and `application.properties`.
- Added the review path: `SubmissionCreatedEvent`, `TopicFitReviewListener`, `TopicFitService`, `TopicFitJudge`, `VisionClient`, `VisionException`, `TopicFitResult`, `AiReviewMapper`, and `AiReviewRow`.
- Extended `SubmissionService`, `SubmissionResponse`, `AiReviewResponse`, `FileStorage`, and `LocalFileStorage` for event publication, optional positive review response, and stored-image loading.
- Added `submission_ai_reviews` to V1 and removed dependent review rows in group deletion and withdrawal.
- Updated backend and product contract docs; added `docs/BACKEND/VISION_TOPIC_FIT.md`.
- Added focused tests for properties, judgment, multipart client, service retries, storage, response serialization, async end-to-end review, mapper claims and status filtering, listener behavior, event publication, feed batching, and both deletion paths; updated affected existing tests.

## Validation

- `./mvnw.cmd clean package -q`: passed after review fixes. 303 tests, 0 failures, 0 errors, 0 skipped; JAR built.
- `git diff --check`: passed. Postman collection JSON parsed successfully; no change was needed there because the collection has no response examples.
- No separate lint or typecheck task is configured; Maven compilation and package passed.
- Verified `fit`/`fitScore`/`cosine` do not appear in response DTOs. The integration test confirms `aiReview` appears after commit and group deletion removes its row.

## Local DB

The first test run found stale migration resources in `target/classes`; `clean` removed them. The next run found existing V1/V2 Flyway checksum mismatches. The local `hawe` database contained zero users, groups, or submissions. I backed it up to `.agent/hawe-before-b10.sql`, dropped its tables, and let Flyway apply the current V1/V2 from scratch. Full tests then passed. This rebuild is local only and was required by the V1 edit in the plan.

## Plan adjustments and limits

- The table key is `UNIQUE(submission_id, kind)` as specified in the plan, and thresholds are separate for each photo template. Queue rejection logs and discards work; it does not throw into the submission request.
- The Python service was not started for a live model smoke test. The multipart contract and async path were verified with a mock client. The intended model is `clip-ViT-B-32+multilingual-v1`, version `1`.
- There is no retry scheduler. A narrow race remains if a review row is inserted between deleting a group's reviews and its submissions; this is documented in `VISION_TOPIC_FIT.md` as required by the plan.
- `.agent/plan.md` and `.agent/review.md` were already modified at review-task start and were not edited. No `ml/`, `AGENTS.md`, or `CLAUDE.md` files changed.

## Potential merge conflicts

Future B4 file cleanup may touch `FileStorage`, group deletion, and withdrawal. B11 may extend `AsyncConfig`. Android A7 should consume only optional `aiReview.badge` and `aiReview.comment`.

## Review feedback decisions

| Item | Decision and reason | Change and validation |
| --- | --- | --- |
| Missing mapper tests | Accepted. The integration test covered only a successful DONE row. | Added `AiReviewMapperTests` for first/duplicate claims, missing FK, DONE-only batch results, and FAILED/SKIPPED/PENDING state reads. Passed in focused and full runs. |
| Missing listener tests | Accepted. The enable guard and exception containment are separate from the async integration path. | Added `TopicFitReviewListenerTests` for disabled, enabled, and service-failure behavior. Passed. |
| Missing submission event and feed tests | Accepted. These conditions protect the main submission path and disabled response contract. | Added photo-only event selection, event-publisher failure, disabled mapper bypass, and one batch lookup for an enabled feed with two rows. Passed. |
| DONE write failure retries vision | Accepted. The old catch covered both the vision call and database completion. | Moved verdict and DONE completion after the retry catch, preserving retries for image loading and vision failures. Added a test proving a failed DONE write calls vision once and does not write FAILED. Passed. |
| Integration timeout gives an NPE | Accepted. A direct assertion identifies an unfinished review. | Added `assertNotNull` with a five-second timeout message before reading the badge. Passed. |
| DB reset and deletion race notes | No code change. The review accepts the documented local DB rebuild and the planned race limitation. | Kept the backup and limitation in this report and `VISION_TOPIC_FIT.md`. |
