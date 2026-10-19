# Photo topic fit review (B10)

For `PHOTO_NOW` and `PHOTO_COLOR`, a committed submission emits `SubmissionCreatedEvent`.
The `visionExecutor` runs the review after commit: load the stored image, read the mission's
`subject` or `color`, call `POST /v1/topic-fit`, judge the returned fit, and write
`submission_ai_reviews`. The submission request does not wait for the review.
See the [vision service contract](../../ml/vision-service/README.md).

The service receives the image, template, and topic. Images go only to our configured vision
service (`hawe.vision.base-url`), not to the mission text AI. Scores stay in `fit_score` in the
database. Once a review is `DONE`, Byeol posts the existing verdict as a comment with
`aiDetail: {"kind":"PHOTO_SCORE","score":73}`. Feed and detail responses omit `aiReview`.
The displayed score maps fit to 0–100: 0 maps to 0, `good` to 60, `perfect` to 80,
and fit at or above 0.5 to 100, interpolating between boundaries. Disabled, pending,
failed, and skipped reviews do not create a score comment. Byeol still adds a random
reaction to supported photo submissions independently of the vision service.

| Template | Perfect badge | Good comment |
| --- | ---: | ---: |
| `PHOTO_NOW` | 0.024 | 0.017 |
| `PHOTO_COLOR` | 0.132 | 0.104 |

Thresholds follow the 2026-10-13, 177-image [evaluation](../../ml/vision-service/EVAL.md).
The backend uses `>=` at each boundary. Perfect yields `PERFECT_FIT` and a positive comment;
good yields a positive comment; lower scores get an encouraging comment without a badge.
Comments are selected deterministically from three variants using the submission ID. They contain
no negative assessment or emoji.

| Property | Default |
| --- | --- |
| `hawe.vision.enabled` | `false` (`HAWE_VISION_ENABLED`) |
| `hawe.vision.base-url` | `http://localhost:8001` (`HAWE_VISION_BASE_URL`) |
| `hawe.vision.connect-timeout` | `3s` |
| `hawe.vision.read-timeout` | `10s` |
| `hawe.vision.retry-delay` | `2s` |
| `hawe.vision.photo-now.perfect-threshold` / `good-threshold` | `0.024` / `0.017` |
| `hawe.vision.photo-color.perfect-threshold` / `good-threshold` | `0.132` / `0.104` |

The listener stops immediately when disabled, so no review row or vision request is created.
The review claims one row with `INSERT IGNORE`; duplicate events do not rerun it. Invalid mission
topics become `SKIPPED`. Image or service failures get one retry, then `FAILED`; neither blocks
submission or feed. Queue overflow logs and discards the task. There is no automatic reprocessing.
Group deletion and account withdrawal delete review rows before deleting submissions.

Changing thresholds does not recalculate completed rows. The color model is less reliable than
the everyday topic model; the thresholds are separate for that reason. A narrow race remains if
group deletion occurs between its review-row deletion and submission deletion while a review
worker inserts a new pending row. This requires a concurrent deletion and review and is not
resolved by this change.
