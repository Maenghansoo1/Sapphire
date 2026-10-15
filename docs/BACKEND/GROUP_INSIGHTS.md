# Group insights (B11)

`GET /api/groups/{groupId}/insights?month=YYYY-MM` returns a monthly summary to a group member. `month` defaults to the current month in Asia/Seoul; the current month and previous 12 months are allowed. Unknown groups return 404, nonmembers 403, and invalid or out of range months 400.

## Generation and cache

The request claims a `group_insights` row and submits work to `insightExecutor`. A missing or failed cache returns 202 `{groupId, month, status:"GENERATING", retryAfterSeconds:3}`. Another request can read a completed result as 200 with `status:"READY"`. Results expire after 30 minutes by default until 00:00 Asia/Seoul on the third day of the following month (two full days after month end). A result becomes permanent only after that time and when no mission from that month remains open; a delayed next publication keeps the TTL. Early cache expiry is capped at the finalization time. The first request after expiry returns its prior payload with `stale:true` while a replacement is generated. A failed refresh with a retained payload also returns stale 200 while retrying. An abandoned `GENERATING` row can be reclaimed after five minutes. The scheduler starts the previous month at 00:10 Asia/Seoul on the first of each month for groups with missions. At that point the last mission may still be open, so this early result can omit it; a request after TTL expiry recomputes it. `hawe.insight.enabled=false` disables the scheduled job.

## Metrics

Only closed missions on a month's `mission_date` count. A current mission can still accept submissions, so including it would make the denominator and ranking unstable. `empty:true` means no closed mission or no eligible participants. A closed mission with eligible participants but no submissions yields score 0.

Activity index = round(100 × (0.6 × participation + 0.2 × on time + 0.2 × interaction)), clamped to 0–100. Participation is submissions divided by the sum of the mission time member counts. On time is on time submissions divided by submissions. Interaction is min(1, (reactions + comments) / (2 × submissions)). A missing denominator gives 0. `deltaFromPreviousMonth` appears only when the preceding month has a READY score in the cache.

`fastest` counts who submitted first per mission, using submission ID to break timestamp ties. The winner has the most firsts; ties use the lower average response time across all their submissions, then the lower user ID. `mostReacted` uses reactions + 2 × comments; ties use earlier submission time then lower submission ID. Its `topSticker` uses the most frequent sticker, with enum order for ties.

Top words use `answer_data.text` from `TEXT_%` submissions. Lucene Nori 10.5.2 uses `DecompoundMode.DISCARD`, retains Korean common and proper nouns, and also retains Latin foreign word tokens so English terms can be lowercased. URLs, one character words, numbers and stopwords are removed. A word counts at most once per submission; `memberCount` is unique authors. The default is top 10 words with at least two submissions. Captions are outside this count.

## Response and privacy

READY has `groupId`, `month`, `status`, `stale`, `empty`, `generatedAt`, `activityIndex`, `fastest`, `topWords`, `mostReacted`, and `highlights`; absent sections may be omitted. The cache stores numeric results and user IDs, while display names, profile image URL, and positive highlight text are assembled at read time. It never stores submission text, comments, captions, or submission media URLs. A group's aggregated words are visible to every current member, including members who did not submit. Withdrawal deletes affected group caches before source rows are removed; group deletion removes its cache before deleting the group.
