# B10 Spring 연동 — 사진-주제 적합도 결과 저장·응답(`aiReview`)·삭제 연쇄 (백엔드)

> 원본 계획: `docs/해야될거/2번_우준/B10(Python완료)-photo-topic-fit-ai.md` 의 **Key Points 7~15**(Spring 연동)를 현재 코드와 대조해 구체화한 것. 이전 `.agent/plan.md`(B2)는 덮어쓴다.
> 범위: 백엔드(`src/`, `docs/`)만. **`ml/vision-service/`(Python) 수정 금지** — 이미 완료됐고 우준 소유. 앱 표시(A7)·알림·배포 무관. **V1 직접 수정 → 로컬 DB 재생성 필요.**
> 작업 트리: 시작 시 clean(HEAD `fc4295e`). B1·B2·B3·B5·B7 등 선행 작업은 모두 커밋됨.

## Goal

1. 사진 틀(`PHOTO_NOW`, `PHOTO_COLOR`) 제출이 **커밋된 뒤 비동기로** 비전 서비스(`POST /v1/topic-fit`)를 호출해 점수를 받고, 판정(찰떡상 배지 / 칭찬 한 줄 / 표시 없음)을 `submission_ai_reviews` 에 저장한다. 제출 응답·지연·성공 여부에는 영향이 없다.
2. 피드·상세 응답(`SubmissionResponse`)에 `aiReview { badge, comment }` 를 (있을 때만) 내려준다. **점수(`fit`)는 API 로 절대 노출하지 않는다.**
3. 그룹 삭제·회원 탈퇴 시 새 테이블 행도 같이 지워 FK 위반(500)이 나지 않게 한다.
4. 기본값은 **비활성**(`hawe.vision.enabled=false`) — 비활성이면 기존 동작·응답·테스트가 그대로다.

## Findings

(모두 코드로 확인함)

- **제출 흐름**: `SubmissionService.submit`(`@Transactional`, `submission/service/SubmissionService.java:59`)이 사진 틀에서 `storage.store("submissions", bytes, ext)` 로 key 를 만들고(L133) `submissions.insert(row)`(L147, `row.getId()` 채워짐) 후 `response(submissions.findDetailById(...), List.of(), 0)` 반환(L152). 롤백 시 파일 삭제는 `TransactionSynchronization.afterCompletion` 으로 이미 등록(L135~140). `mission` 은 `MissionRow`(`getTemplate()`, `getParams()` = JSON 문자열).
- **응답 조립**: `feed`(L156)/`detail`(L170) → `enrich(rows, userId)`(L183)가 id 목록으로 반응(`ReactionMapper.findBySubmissionIds`)·댓글 수(`CommentMapper.countBySubmissionIds`)를 **배치 조회** 후 `response(row, reactions, commentCount)`(L272)로 만든다. `SubmissionResponse` 는 `record(... int commentCount, OffsetDateTime submittedAt, @JsonProperty("isLate") boolean isLate)` + `@JsonInclude(NON_NULL)`. `new SubmissionResponse(` 호출은 `SubmissionService.response` 1곳 + `SubmissionResponseTests` 2곳뿐(`MissionResultService` 는 쓰지 않음).
- **`FileStorage`**(`global/storage`): `store`/`delete`/`publicUrl` 만 있다 — **읽기 메서드 없음**. 구현은 `LocalFileStorage` 하나(루트 `hawe.storage.local-dir`, `delete` 는 `root.resolve(key).normalize().startsWith(root)` 검사). 이 인터페이스를 `mock(FileStorage.class)` 로 쓰는 테스트가 많아 메서드 추가는 컴파일에 영향 없음(익명 구현은 없음, grep 확인).
- **이벤트 패턴**: `GroupCreatedEvent`(record) → `MissionPublishListener`(`@TransactionalEventListener(phase = AFTER_COMMIT)` + 메서드 전체 try/catch + `log.error`). `@Async`/`@EnableAsync`/`TaskExecutor` 는 **없다**(`HaweApplication` 에 `@EnableScheduling` 만).
- **설정 패턴**: `global/config/*Properties`(record + `@ConfigurationProperties`, compact constructor 에서 기본값·검증) + 쓰는 `@Component`/`@Service` 위에 `@EnableConfigurationProperties(XxxProperties.class)` (`ExperimentProperties`/`ExperimentAssigner`, `MissionAiProperties`/`AiMissionGenerator`). 외부 HTTP: `mission/service/AnthropicClient` 가 `RestClient.builder().requestFactory(JdkClientHttpRequestFactory…)` 로 타임아웃을 설정하고, 테스트용 package-private 생성자로 `RestClient` 를 주입받아 `MockRestServiceServer.bindTo(builder)` 로 테스트한다(`AnthropicClientTests`). **같은 구조로 따른다.**
- **미션 주제**: `missions.params` JSON — `PHOTO_NOW{subject}`(예: "창밖 풍경"), `PHOTO_COLOR{color}`(예: "파란색"). `PHOTO_GALLERY` 는 제외.
- **비전 서비스 계약**(`ml/vision-service/app.py`, `README.md`, 변경 금지): `POST /v1/topic-fit` multipart — `image`(파일), `topic`(1~60자), `template`(`PHOTO_NOW`|`PHOTO_COLOR`). 200 `{"fit":0.19,"cosine":0.27,"model":"clip-ViT-B-32+multilingual-v1","version":"1"}`. 오류: 400(`INVALID_TOPIC`/`UNSUPPORTED_TEMPLATE`/`INVALID_IMAGE`/`VALIDATION_ERROR`), 413, 서비스 다운. 지연 p50 ≈ 0.4s, p95 ≈ 0.8s.
- **임계값은 틀마다 다르다**(원본 계획의 "하나의 perfect/good" 보다 우선): 소프트맥스 기준값이 일상 약 0.014, 색 약 0.09 라서. `ml/vision-service/EVAL.md`·`README.md` 추천값(2026-10-13, 사진 177장):

  | 틀 | 찰떡상(`perfect`) | 칭찬 한 줄(`good`) |
  |---|---|---|
  | `PHOTO_NOW` | **0.024** | **0.017** |
  | `PHOTO_COLOR` | **0.132** | **0.104** |

- **삭제 경로**(제출 행을 지우는 곳은 정확히 2곳): `GroupService.deleteGroupCascade`(`group/service/GroupService.java:320`: 멘션→댓글→반응→추측→**제출**→콕→미션→…), `WithdrawService.withdraw`(`withdraw/service/WithdrawService.java:55~61`: …→`deleteMyGuesses`→**`deleteMySubmissions`**→…). 둘 다 새 테이블 삭제를 **제출 삭제 앞**에 넣어야 FK 가 안 깨진다. 순서 검증 테스트가 이미 있다: `GroupContractServiceTests.lastOwnerDeletesDependentsInForeignKeyOrder`(InOrder), `WithdrawServiceTests`(`inOrder(groups, mapper)`).
- **DB**: `V1__initial_schema.sql` 직접 수정이 프로젝트 방식(CLAUDE.md: 이미 적용된 마이그레이션은 건드리지 말라고 하지만 V1 직접 수정+로컬 DB 재생성은 5·7·8단계부터의 팀 방식이며 B10 원본 계획이 명시). 새 `V#` 파일 만들지 않는다. 엔진/문자셋은 기존 테이블과 동일(`ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci`).
- MariaDB(로컬)·MySQL(RDS) 모두 지원해야 한다 → 업서트는 `INSERT IGNORE` + `UPDATE` 같은 이식 가능한 SQL 사용.

## Root Cause / Key Implementation Points

### 설계 결정 (이미 정함 — Codex 가 바꾸지 말 것)

1. **심사 시작 = 이벤트**: `SubmissionService.submit` 이 사진 틀(`PHOTO_NOW`/`PHOTO_COLOR`)이고 `key != null` 이면 `SubmissionCreatedEvent(submissionId, template)` 를 `ApplicationEventPublisher` 로 발행. 발행 자체는 try/catch(`log.warn`) — 발행 실패가 제출을 롤백시키면 안 된다. `hawe.vision.enabled` 확인은 **리스너에서** 한다(SubmissionService 는 VisionProperties 를 모르게 두어 생성자 변경을 최소화: 추가 인자는 `ApplicationEventPublisher` 하나).
2. **비동기 = `@Async("visionExecutor")` 리스너**: `TopicFitReviewListener.onSubmissionCreated` 에 `@TransactionalEventListener(phase = AFTER_COMMIT)` + `@Async("visionExecutor")`. 본문은 `if (!properties.enabled()) return;` 후 `TopicFitService.review(event.submissionId())` 를 try/catch(`log.error`)로 감싼다. 트랜잭션 없이 발행되면(단위 테스트 등) `@TransactionalEventListener` 는 호출되지 않는다 — 통합 테스트는 반드시 `@Transactional` 서비스 경로로 제출한다.
3. **실행기 `visionExecutor`**: `global/config/AsyncConfig`(`@Configuration @EnableAsync`)에 `ThreadPoolTaskExecutor` 빈(core 2, max 4, queue 100, `threadNamePrefix="vision-"`). **거부 정책은 "로그 후 버림"** — `setRejectedExecutionHandler((r, e) -> log.warn(...))` 커스텀 핸들러. `AbortPolicy` 는 쓰지 않는다: `@Async` 인터셉터가 호출 스레드(=제출 요청 스레드의 afterCommit)에서 `TaskRejectedException` 을 던지기 때문에 리스너 본문 try/catch 로 못 막는다. 버려진 작업은 행이 만들어지지 않으므로 `aiReview` 가 영원히 안 나올 뿐 제출은 정상. (B11 이 같은 클래스에 `insightExecutor` 를 추가할 예정 — `AsyncConfig` 파일명·`@EnableAsync` 위치를 이대로 두면 된다.)
4. **`VisionClient`**(`@Component`, 새 클래스): `TopicFitResult call(String template, String topic, byte[] image, String filename)`. `RestClient` 로 multipart 전송. 실패는 모두 `VisionException`(RuntimeException)으로 변환(`AnthropicClient`/`AiGenerationException` 방식: `RestClientResponseException` → "HTTP n", `ResourceAccessException` → 연결 실패/시간 초과, 기타 `RestClientException`, 응답 JSON 이 깨졌거나 `fit` 이 없거나 [0,1] 밖이면 예외). **주의 — multipart 에서 파일 파트는 `filename` 이 있어야 한다**: `ByteArrayResource` 를 `getFilename()` 오버라이드(예: `"photo.jpg"`, 확장자는 key 에서 가져오거나 고정 `photo.jpg`)해 `MultiValueMap<String,Object>` 에 `image` 로 넣고, `topic`/`template` 은 문자열 파트. 한글 `topic` 이 UTF-8 로 나가는지 테스트로 확인한다(아래 Tests). 로그에 이미지·topic 문자열을 남기지 않는다.
5. **임계값은 틀별 설정**: 아래 `VisionProperties` 참고. 서비스(Python)는 점수만, 판정은 백엔드(`TopicFitJudge`).
6. **한 번만 처리(멱등)**: `TopicFitService.review` 는 먼저 `INSERT IGNORE`(PENDING)로 행을 선점한다. 영향 행 0(이미 존재: 중복 이벤트/이미 DONE·FAILED/제출이 이미 삭제돼 FK 로 무시됨)이면 조용히 종료. 재처리 스케줄러는 만들지 않는다.
7. **점수 비노출**: `fit_score` 는 DB 컬럼에만 있다. `AiReviewRow` 는 mapper 내부 타입, `AiReviewResponse` 에는 `badge`, `comment` 두 필드만.

### 판정 `TopicFitJudge` (순수 클래스, 상태 없음)

`judge(double fit, Threshold t, long submissionId)` → `Verdict(String badge, String comment)`:
- `fit >= t.perfect()` → `badge = "PERFECT_FIT"`, comment = 칭찬 풀 A 에서 `submissionId % 풀크기` 로 **결정적 선택**.
- `fit >= t.good()` → `badge = null`, comment = 풀 B(예: "주제를 잘 담았어요") 에서 결정적 선택.
- 그 미만 → `badge = null, comment = null`(표시 없음). **낮은 점수·부정 평가 문구 금지.** 코멘트에 이모지 금지(앱이 임시 아이콘을 붙임).
- 풀 A 예: "주제랑 딱 맞는 사진이에요!", "오늘 주제를 제대로 담았어요", "이 사진, 주제 그 자체예요". 풀 B 예: "주제를 잘 담았어요", "오늘 주제가 잘 느껴져요", "주제와 잘 어울리는 사진이에요". 각 풀 3개 이상, 100자 이하(`comment varchar(100)`).
- 경계는 `>=` (임계값과 같으면 올라간다).

## Files / Functions to Change

### 신규

| 파일 | 내용 |
|---|---|
| `global/config/VisionProperties.java` | `@ConfigurationProperties(prefix = "hawe.vision")` record: `boolean enabled`, `String baseUrl`, `Duration connectTimeout`, `Duration readTimeout`, `Duration retryDelay`, `Threshold photoNow`, `Threshold photoColor`; 중첩 `record Threshold(double perfect, double good)`. compact constructor: null/blank 기본값(`baseUrl=http://localhost:8001`, connect 3s, read 10s, retryDelay 2s, `photoNow=0.024/0.017`, `photoColor=0.132/0.104`), `baseUrl` 끝 `/` 제거, 검증(`0 < good <= perfect <= 1`, 타임아웃/지연 음수 금지 → `IllegalArgumentException`, `ExperimentProperties` 와 같은 스타일). 편의 메서드 `Threshold thresholdFor(String template)`(그 외 틀은 `null`). |
| `global/config/AsyncConfig.java` | 위 3번. 빈 이름 정확히 `visionExecutor`. |
| `submission/domain/SubmissionCreatedEvent.java` | `record SubmissionCreatedEvent(Long submissionId, String template)`. (`group/domain/GroupCreatedEvent` 와 같은 위치 관례.) |
| `submission/service/TopicFitReviewListener.java` | 위 2번. |
| `submission/service/TopicFitService.java` | `void review(Long submissionId)` — 아래 "review 흐름". |
| `submission/service/TopicFitJudge.java` | 위 판정. |
| `submission/service/VisionClient.java` + `VisionException.java` + `TopicFitResult`(record `fit`, `model`, `version`) | 위 4번. `VisionClient` 는 `AnthropicClient` 처럼 public `@Autowired` 생성자(`VisionProperties`, `ObjectMapper` → `JdkClientHttpRequestFactory` 로 connect/read 타임아웃)와 테스트용 package-private 생성자(`VisionProperties, ObjectMapper, RestClient`). `@EnableConfigurationProperties(VisionProperties.class)` 를 `VisionClient`(또는 `TopicFitService`) 위에 둔다. `tools.jackson.databind.ObjectMapper` (Jackson 3) 사용 — 기존 코드와 동일 import. |
| `submission/dao/AiReviewMapper.java` + `AiReviewRow.java` | 아래 SQL. `AiReviewRow`: `submissionId, status, fitScore(Double), badge, comment, modelVersion, attempts` getter/setter(기존 Row 클래스 스타일). |
| `submission/dto/AiReviewResponse.java` | `record AiReviewResponse(String badge, String comment)` + `@JsonInclude(NON_NULL)`. |
| `docs/BACKEND/VISION_TOPIC_FIT.md` | 구조도(제출→이벤트→비동기→비전 서비스→저장→피드), 비전 서비스 계약 링크(`ml/vision-service/README.md`), 틀별 임계값과 근거(`EVAL.md`), 판정·문구 규칙, 설정 표, 실패/재시도/삭제 연쇄, 개인정보(사진은 우리 비전 서비스로만, 점수 비노출), 한계(재처리 없음, 임계값 변경 시 기존 DONE 유지, 색 판정 약함). |

### 수정

| 파일 | 변경 |
|---|---|
| `src/main/resources/db/migration/V1__initial_schema.sql` | `submissions` 테이블 뒤(FK 대상 뒤)에 `submission_ai_reviews` 추가: `id bigint(20) NOT NULL AUTO_INCREMENT`, `submission_id bigint(20) NOT NULL`, `kind varchar(20) NOT NULL DEFAULT 'TOPIC_FIT'`, `status varchar(10) NOT NULL`, `fit_score double DEFAULT NULL`, `badge varchar(30) DEFAULT NULL`, `comment varchar(100) DEFAULT NULL`, `model_version varchar(60) DEFAULT NULL`, `attempts tinyint(3) unsigned NOT NULL DEFAULT 0`, `created_at datetime NOT NULL DEFAULT current_timestamp()`, `updated_at datetime NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp()`, `PRIMARY KEY (id)`, `UNIQUE KEY uk_ai_reviews_submission (submission_id, kind)`(원본은 `submission_id` UNIQUE 였으나 `kind` 컬럼이 있으므로 `(submission_id, kind)` — 현재 `kind` 는 `TOPIC_FIT` 하나뿐이라 동작 동일), `CONSTRAINT fk_ai_reviews_submission FOREIGN KEY (submission_id) REFERENCES submissions (id)`. 같은 파일의 기존 `submission_guesses` 정의 스타일을 따른다. |
| `global/storage/FileStorage.java` | `byte[] load(String key);` 추가. |
| `global/storage/LocalFileStorage.java` | `load` 구현: `root.resolve(key).normalize()` 가 `root` 로 시작하지 않으면 `IllegalArgumentException("Invalid storage key")`; 읽기 실패/없는 파일은 `UncheckedIOException`. |
| `submission/service/SubmissionService.java` | 생성자에 `ApplicationEventPublisher events` 추가(마지막 인자). 사진 틀 분기에서 insert 성공 후, `return` 직전에 이벤트 발행(`"PHOTO_NOW".equals(template) \|\| "PHOTO_COLOR".equals(template)` — `PHOTO_GALLERY`, 그림 틀 제외; try/catch). `enrich` 에서 `AiReviewMapper.findBySubmissionIds(ids)` **배치 1회** 조회 → `Map<Long, AiReviewRow>`; `response(...)` 에 `AiReviewResponse aiReview` 인자 추가(제출 직후 응답은 항상 `null`). `DONE` 이고 badge 또는 comment 가 있을 때만 `new AiReviewResponse(badge, comment)`, 아니면 `null`. **`hawe.vision.enabled=false` 이면 조회도 하지 않고 항상 `null`**(스펙: 비활성 시 응답에 항상 생략) → `AiReviewMapper` 와 함께 `VisionProperties` 를 `SubmissionService` 에 주입(생성자 인자 `AiReviewMapper aiReviews, VisionProperties vision` 추가). |
| `submission/dto/SubmissionResponse.java` | `AiReviewResponse aiReview` 컴포넌트를 **`commentCount` 바로 뒤, `submittedAt` 앞**에 추가(JSON 필드 순서·기존 필드 이름 불변, `NON_NULL` 로 null 이면 생략). |
| `application.properties` | `hawe.vision.*` 블록 추가(주석 한국어, 기존 `hawe.experiment.topic.*` 스타일): `hawe.vision.enabled=${HAWE_VISION_ENABLED:false}`, `base-url=${HAWE_VISION_BASE_URL:http://localhost:8001}`, `connect-timeout=3s`, `read-timeout=10s`, `retry-delay=2s`, `photo-now.perfect-threshold=0.024`, `photo-now.good-threshold=0.017`, `photo-color.perfect-threshold=0.132`, `photo-color.good-threshold=0.104`. 임계값 근거 주석("ml/vision-service/EVAL.md 2026-10-13 평가, 틀마다 기준값이 달라 따로"). |
| `group/dao/GroupMapper.java` | `@Delete("DELETE ar FROM submission_ai_reviews ar JOIN submissions s ON s.id = ar.submission_id JOIN missions m ON m.id = s.mission_id WHERE m.group_id = #{groupId}") void deleteAiReviewsByGroupId(Long groupId)` (기존 `deleteSubmissionGuessesByGroupId` 바로 옆, 같은 스타일). |
| `group/service/GroupService.java` | `deleteGroupCascade` 에서 `deleteSubmissionGuessesByGroupId` 와 `deleteSubmissionsByGroupId` **사이**에 `groupMapper.deleteAiReviewsByGroupId(groupId);`. |
| `withdraw/dao/WithdrawMapper.java` | `@Delete("DELETE ar FROM submission_ai_reviews ar JOIN submissions s ON s.id = ar.submission_id WHERE s.user_id = #{userId}") void deleteAiReviewsOnMySubmissions(Long userId)`. |
| `withdraw/service/WithdrawService.java` | `deleteMyGuesses` 와 `deleteMySubmissions` **사이**(실제로는 `deleteGuessesOnMySubmissions` 계열 옆이어도 됨 — 반드시 `deleteMySubmissions` 앞)에 `withdraw.deleteAiReviewsOnMySubmissions(userId);`. |

### review 흐름 (`TopicFitService.review(Long submissionId)`)

1. `row = submissions.findDetailById(submissionId)`; `null` 이면(이미 삭제됨) 로그 없이 return. (`SubmissionMapper` 의 기존 메서드 재사용 — 템플릿·`mediaUrl`·`missionId` 가 들어 있음. `params` 는 없으므로 `missions` 쪽이 필요: `MissionMapper.findById(missionId, null)` 은 `userId` 를 쓰는 `submitted_by_me` 서브쿼리가 있으니, **`params` 만 읽는 단순 쿼리가 필요하면 `MissionMapper` 에 `findParamsById` 같은 최소 메서드 추가 대신 `findById(missionId, row.getUserId())` 를 재사용**해 새 쿼리를 늘리지 않는다.)
2. `template` 이 `PHOTO_NOW`/`PHOTO_COLOR` 가 아니거나 `mediaUrl == null` → 행을 만들지 않고 return.
3. `reviews.insertPendingIfAbsent(submissionId)` (`INSERT IGNORE INTO submission_ai_reviews (submission_id, kind, status, attempts) VALUES (?, 'TOPIC_FIT', 'PENDING', 0)`) 영향 행 0 → return (멱등 가드).
4. 주제 추출: `params` JSON 파싱 → `PHOTO_NOW`→`subject`, `PHOTO_COLOR`→`color`; 문자열이 아니거나 `strip()` 후 길이 < 2(한 글자 포함) 또는 > 60 → `complete(SKIPPED)` return. 파싱 실패도 SKIPPED.
5. `bytes = storage.load(row.getMediaUrl())`.
6. 비전 호출: 최대 2회 시도. 1회 실패하면 `Thread.sleep(retryDelay)` 후 1회 재시도(같은 비동기 작업 안에서). `InterruptedException` 이면 인터럽트 플래그 복원 후 FAILED 처리 중단. 호출 실패/`load` 실패/`VisionException` 모두 시도 횟수에 포함. 둘 다 실패 → `complete(FAILED, attempts=2, null...)` (예외는 던지지 않고 `log.warn`, 메시지에 submissionId 와 예외 종류만 — **이미지·topic 로그 금지**).
7. 성공 → `verdict = judge.judge(fit, vision.thresholdFor(template), submissionId)`; `complete(DONE, fit_score=fit, badge, comment, model_version = model + "/" + version, attempts = 시도 횟수)`.
8. `complete` = `UPDATE submission_ai_reviews SET status=?, fit_score=?, badge=?, comment=?, model_version=?, attempts=? WHERE submission_id=? AND kind='TOPIC_FIT'` (행이 이미 지워졌으면 0행 — 정상, 무시).
9. 최상위에서 `RuntimeException` 을 잡아 `log.error` 후 종료(PENDING 으로 남는 것은 허용 — 응답에서 `DONE` 만 노출).

`AiReviewMapper` 메서드:
- `int insertPendingIfAbsent(@Param("submissionId") Long)` — `INSERT IGNORE`.
- `int complete(@Param("submissionId") Long, @Param("status") String, @Param("fitScore") Double, @Param("badge") String, @Param("comment") String, @Param("modelVersion") String, @Param("attempts") int)`.
- `List<AiReviewRow> findBySubmissionIds(@Param("ids") List<Long> ids)` — `<script>` + `<foreach>` (기존 `ReactionMapper.findBySubmissionIds` 와 동일 패턴), `WHERE kind='TOPIC_FIT' AND status='DONE' AND submission_id IN (...)` 만 반환(응답에 필요한 DONE 만; 컬럼은 `submission_id, badge, comment` 만 선택해도 되지만 `AiReviewRow` 재사용을 위해 전체 컬럼 OK — 단 `fit_score` 를 응답으로 옮기지 말 것).
- 테스트용 단건 조회 `findBySubmissionId(Long)` 가 필요하면 추가(상태 PENDING/FAILED/SKIPPED 검증용).

## Implementation Steps

1. `VisionProperties`, `application.properties` 블록, `AsyncConfig` 작성. (`HaweApplicationTests` 같은 컨텍스트 로드 테스트가 깨지지 않는지 먼저 확인할 수 있도록 단독으로 먼저 컴파일.)
2. `V1__initial_schema.sql` 에 `submission_ai_reviews` 추가. **로컬 DB 백업 후 재생성**(보고서에 명시 — 아래 Validation). DB 접속이 안 되면 `*MapperTests`/통합 테스트 실행 불가이므로 보고서에 "DB 미연결로 미검증" 이라고 적는다.
3. `FileStorage.load` + `LocalFileStorage.load`.
4. `AiReviewRow`, `AiReviewMapper`, `AiReviewResponse`, `SubmissionCreatedEvent`, `TopicFitJudge`, `VisionClient`/`VisionException`/`TopicFitResult`, `TopicFitService`, `TopicFitReviewListener`.
5. `SubmissionService` 수정(이벤트 발행 + `enrich` 배치 + `response` 인자) 과 `SubmissionResponse` 컴포넌트 추가. 기존 단위 테스트 컴파일 수정(`SubmissionServiceTests` 생성자 호출: `new SubmissionService(missions, members, submissions, storage, new ObjectMapper(), reactions, comments, aiReviews, vision, events)` 형태로 인자 추가 — 목/기본 `VisionProperties`(`new VisionProperties(false, null, …)` 또는 기본값 생성)로; `SubmissionResponseTests` 의 `new SubmissionResponse(` 2곳에 `null` 추가).
6. 삭제 연쇄: `GroupMapper`/`GroupService`, `WithdrawMapper`/`WithdrawService`; 순서 검증 테스트(`GroupContractServiceTests`, `WithdrawServiceTests`)에 새 호출 추가.
7. 테스트 작성(아래), `.\mvnw.cmd clean test -q`.
8. 문서 갱신(아래 Documentation).

## Documentation (코드와 함께 갱신)

- 신규 `docs/BACKEND/VISION_TOPIC_FIT.md` (위 표).
- `docs/FEATURE/PROJECT_OVERVIEW.md` §6 "AI의 역할"(L67~69) 및 §10(진행 상태): **원칙을 좁게 수정** — "AI는 제출물을 평가하지 않는다" → "미션 생성 + (사진 틀 한정) 주제 적합도 배지. 사진은 우리가 운영하는 비전 서비스로만 보내고, 점수·순위·낮은 평가는 사용자에게 보이지 않으며 긍정 배지·한 줄 코멘트만 보인다. 도장·제출 횟수 등 기존 규칙에는 영향 없음". 같은 파일 L29 표의 "AI의 역할" 행도 일관되게 고친다.
- `docs/BACKEND/MISSIONS_API.md`: §3-3 제출 응답/피드 응답 예시에 `aiReview`(옵션 필드) 추가, L29 "AI의 역할" 표 행·L542 "AI는 제출을 평가하지 않는다" 항목·§8/§11-1 정책 문구를 위 원칙으로 갱신.
- `docs/BACKEND/BACKEND_DB.md`: `submission_ai_reviews` 테이블 추가(컬럼·UNIQUE·FK·삭제 순서). `docs/BACKEND/CONTRACT_DECISIONS.md`: B10 결정(#15, #16 — 점수 비노출·틀별 임계값) 한 줄 반영 + §0 상태. `docs/BACKEND/postman/HAWE_SUBMISSION.postman_collection.json`: 피드 응답 예시에 `aiReview` 가 있으면 추가(한글 깨짐 없이 JSON 유효해야 함). `CONTRACT_WORK_PLAN.md`/`docs/해야될거/**` 진행 표시는 **건드리지 않는다**(사용자/Claude 가 직접 정리).

## Constraints

- **기본 비활성**: `hawe.vision.enabled=false` 면 `submission_ai_reviews` 행도 만들지 않고, 응답에 `aiReview` 도 항상 없음, 비전 서비스에 어떤 요청도 가지 않는다. 비활성 기본값에서 **기존 테스트가 하나도 바뀌지 않고(생성자 인자 추가 제외) 통과**해야 한다.
- 비전 서비스 장애·지연이 제출/피드 응답을 실패·지연시키지 않는다.
- 사용자에게 **점수(`fit`, `fit_score`, `cosine`)·순위·낮은 평가 문구를 노출하지 않는다.** 도장/지각/제출 가능 여부에 AI 판정을 쓰지 않는다. 제3자 AI API 로 사진을 보내지 않는다(비전 서비스 `hawe.vision.base-url` 만).
- 대상 틀은 `PHOTO_NOW`, `PHOTO_COLOR` 뿐(`PHOTO_GALLERY`·그림·글·슬라이더 제외).
- 새 마이그레이션 파일 금지(V1 직접 수정). `ml/` 수정 금지. `Awaitility` 등 새 의존성 추가 금지(`pom.xml` 불변). Spring AI 미사용.
- `CLAUDE.md`/`AGENTS.md` 수정 금지. 커밋/푸시/브랜치/스태시 금지. 불필요한 리팩터링·기존 포맷 변경 금지.
- 로그에 사진 바이트·`topic` 문자열을 남기지 않는다.

## Edge Cases

- 비전 서비스 다운/타임아웃/500/400: 재시도 1회 후 `FAILED`(attempts=2), 응답에 `aiReview` 없음, 제출 정상. 재처리 스케줄러는 만들지 않는다.
- 같은 제출에 이벤트가 두 번 와도 `INSERT IGNORE` 가 영향 행 0 → 한 번만 처리. 이미 `DONE`/`FAILED` 인 행은 다시 건드리지 않는다.
- `params` 가 비었거나 JSON 아님/`subject`·`color` 가 2자 미만 또는 60자 초과 → `SKIPPED`(비전 호출 없음).
- 심사 도중 그룹 삭제/탈퇴로 제출이 사라짐: ① `findDetailById` null → 조용히 종료 ② `INSERT IGNORE` 가 FK 위반을 무시해 영향 행 0 → 종료 ③ 이미 삽입된 뒤 삭제되면 `complete` 가 0행 갱신(정상) — 삭제 쪽이 새 테이블을 먼저 지우므로 FK 위반 없음. **알려진 극소 경합**: 삭제 트랜잭션이 `deleteAiReviews…` 를 실행한 직후, `deleteSubmissions…` 전에 심사 작업이 PENDING 행을 삽입하면 제출 삭제가 FK 로 실패(500)할 수 있다. 심사 시작은 제출 커밋 직후라 현실적으로 거의 불가능 → 방지 코드는 넣지 말고 `VISION_TOPIC_FIT.md` 한계에만 적는다.
- 임계값 설정을 나중에 바꿔도 이미 `DONE` 인 행의 저장된 `badge`/`comment` 는 유지(재판정 안 함, `fit_score` 는 보관됨).
- `PENDING` 은 응답에 안 나온다(`DONE` 만). 점수가 임계값 미만인 `DONE` 은 badge·comment 모두 null → 응답에서 `aiReview` 생략.
- 코멘트 선택은 `submissionId` 로 결정적 → 새로고침해도 문구가 안 바뀐다.
- `hawe.vision.enabled=false` 로 껐다 켜도 이미 저장된 `DONE` 행은 비활성 동안 응답에서 숨겨진다(Constraints 의 "항상 생략").
- `LocalFileStorage.load` 에 `../` 키 → `IllegalArgumentException`; 없는 파일 → `UncheckedIOException` → 시도 실패로 처리.
- 큐 가득 참 → 로그 후 버림(행 없음, `aiReview` 없음).
- 시간대: `created_at/updated_at` 은 DB 시각.

## Regression Risks

- `FileStorage` 메서드 추가 → 구현체는 `LocalFileStorage` 하나라 컴파일 영향 없음, 목 테스트 영향 없음.
- `SubmissionService` 생성자 시그니처 변경(`aiReviews`, `vision`, `events` 추가) → `SubmissionServiceTests`(`:40`) 외에 직접 `new SubmissionService(` 하는 곳이 없는지 grep 으로 재확인. Spring 컨텍스트 테스트는 주입이라 무관.
- `SubmissionResponse` 레코드 컴포넌트 추가 → `SubmissionResponseTests` 2곳 + `SubmissionService.response` 1곳. JSON 에 `aiReview` 가 null 일 때 나타나지 않는지(`NON_NULL`) 기존 JSON 단언 테스트(`SubmissionControllerTests`, `SubmissionResponseTests`)가 유지되는지 확인.
- `submit` 은 핵심 경로: 이벤트 발행은 try/catch, `AFTER_COMMIT` 리스너는 `@Async` 라 요청 스레드를 막지 않음. 롤백 시에는 리스너가 호출되지 않는다(`AFTER_COMMIT`).
- `@EnableAsync` 도입: 현재 `@Async` 사용처가 없어 다른 동작에 영향 없음. 단 `@EnableAsync` 가 켜진 컨텍스트에서 `TaskExecutor` 빈이 **둘 이상**이 되면 `@Async` 기본 실행기 탐색에 영향을 줄 수 있으므로, 모든 `@Async` 에 실행기 이름을 명시한다(이번엔 `visionExecutor` 하나).
- 삭제 연쇄 누락은 런타임 FK 위반 → 그룹 삭제/탈퇴 테스트에 `submission_ai_reviews` 행을 포함해 검증. `GroupContractServiceTests` InOrder 순서: `deleteSubmissionGuessesByGroupId` → **`deleteAiReviewsByGroupId`** → `deleteSubmissionsByGroupId`.
- V1 수정 → **로컬 DB 를 새로 만들지 않으면 컨텍스트/통합/Mapper 테스트가 전부 실패**한다(Flyway 체크섬 불일치). 팀 공유 사항으로 보고서에 명시.
- 비동기 테스트 불안정: 통합 테스트는 폴링 헬퍼(아래), `Thread.sleep` 단발 대기 금지.

## Tests Required

(모두 비활성 기본값에서 기존 테스트와 함께 통과해야 한다. 새 외부 의존성 금지.)

- **`TopicFitJudgeTests`** (순수): 각 틀의 임계값 경계 — `fit == perfect`→`PERFECT_FIT`, `perfect - ε`→badge null+칭찬 풀 B, `fit == good`→풀 B, `good - ε`→둘 다 null; 같은 `submissionId` 로 두 번 → 같은 문구; 다른 id 들로 풀이 실제로 순환; 모든 문구 풀에 이모지(코드포인트 `> 0xFFFF` 또는 `Character.getType == OTHER_SYMBOL`)·부정 단어("별로","아쉽","실패","낮") 없음, 길이 ≤ 100.
- **`VisionPropertiesTests`**: 기본값(임계값 0.024/0.017, 0.132/0.104, `enabled=false`, 타임아웃), `good > perfect` 거부, `baseUrl` 끝 `/` 제거. `MissionPropertiesTests`/`MissionAiPropertiesTests` 스타일.
- **`VisionClientTests`** (`AnthropicClientTests` 와 같은 `MockRestServiceServer.bindTo(RestClient.Builder)` 방식): 200 응답 파싱(`fit`, `model`, `version`); 요청이 multipart 이고 `image` 파트에 `filename=`, `topic` 파트에 **한글 문자열이 UTF-8 로 그대로** 들어가며 `template` 파트가 있음(본문 문자열에 `창밖 풍경` 포함 단언); 500/400/연결 오류 → `VisionException`; 깨진 JSON/`fit` 누락/`fit` 범위 밖 → `VisionException`.
- **`TopicFitServiceTests`** (Mockito: `SubmissionMapper`, `MissionMapper`, `AiReviewMapper`, `FileStorage`, `VisionClient`, 기본 `VisionProperties(retryDelay=0)`): 정상 PHOTO_NOW(DONE+badge+`fit_score` 저장+`model_version`), PHOTO_COLOR 는 `color` 와 color 임계값 사용, 첫 호출 실패 후 두 번째 성공(attempts=2, DONE), 두 번 다 실패(FAILED, 예외 전파 없음, `vision` 정확히 2회 호출), `params` 비었음/한 글자/JSON 아님 → SKIPPED·`vision` 0회, 제출 없음(`findDetailById` null) → 아무 mapper 쓰기 없음, `insertPendingIfAbsent` 0 반환 → `vision`/`complete` 0회, `PHOTO_GALLERY`/글 틀 → 행 안 만듦, `storage.load` 예외 → 재시도 후 FAILED.
- **`TopicFitReviewListenerTests`**: `enabled=false` → `TopicFitService` 호출 0회; `enabled=true` → 1회; 서비스가 예외를 던져도 리스너가 삼킴.
- **`AiReviewMapperTests`** (실 DB, `@SpringBootTest` + `JdbcTemplate`, 다른 `*MapperTests` 처럼 테스트 데이터 직접 생성·정리): `insertPendingIfAbsent` 첫 호출 1/둘째 0(UNIQUE), 존재하지 않는 `submission_id` 는 0(FK 무시), `complete` 후 `findBySubmissionIds` 가 DONE 만 반환하고 PENDING/FAILED/SKIPPED 는 제외, 빈/여러 id 조합.
- **`LocalFileStorageTests`**(기존 파일에 추가): `load` 정상, `../secret` 거부(`IllegalArgumentException`), 없는 파일 → `UncheckedIOException`.
- **`SubmissionServiceTests`**(기존 파일에 추가): `PHOTO_NOW`/`PHOTO_COLOR` 제출 시 `SubmissionCreatedEvent` 1회 발행(ArgumentCaptor: id·template), `PHOTO_GALLERY`/`DRAW_SKETCH`/글 틀은 미발행, 이벤트 발행이 예외를 던져도 제출 응답 정상, 비활성(`enabled=false`)이면 `feed` 가 `AiReviewMapper` 를 호출하지 않고 `aiReview == null`, 활성이면 `findBySubmissionIds` **한 번만** 호출(N+1 없음)되고 DONE 행의 badge/comment 가 응답에 실린다.
- **`SubmissionResponseTests`**: `aiReview` null → JSON 에 키 없음; 값 있음 → `{"badge":..,"comment":..}` 이고 JSON 어디에도 `fit`/`score`/`cosine` 키 없음.
- **통합** (`@SpringBootTest` + `@TestPropertySource(properties = "hawe.vision.enabled=true")`, `@MockitoBean VisionClient`, 폴링 헬퍼 `waitUntil(BooleanSupplier, timeout)` — 최대 5초, 50ms 간격): 실제 사진 틀 미션을 만들어(`SubmissionIntegrationTests` 방식 + `missions.template='PHOTO_NOW', params='{"subject":"창밖 풍경"}'`, 유효한 JPEG 바이트: `{0xff,0xd8,0xff,…}` 가 `FileTypeDetector` 를 통과하는지 확인하고 기존 컨트롤러/통합 테스트가 쓰는 샘플을 재사용) 제출 → 폴링으로 `submission_ai_reviews.status='DONE'` 확인 → `submissions.feed` 응답의 `aiReview.badge == "PERFECT_FIT"`(목이 `fit=0.5` 반환), `fit` 이 응답 JSON 문자열에 없음. 같은 테스트에서 **그룹 삭제(혼자인 그룹장 나가기)와 회원 탈퇴가 FK 오류 없이 성공**하고 `submission_ai_reviews` 행이 사라짐을 검증(두 시나리오 모두 DONE 행이 있는 상태에서). 비전 서비스 목이 예외를 던지는 변형 → `FAILED`, 피드 정상, `aiReview` 없음. `hawe.vision.enabled` 기본(false) 변형 → 행 0개.
- 순서 검증 갱신: `GroupContractServiceTests.lastOwnerDeletesDependentsInForeignKeyOrder` 에 `deleteAiReviewsByGroupId` (guesses 뒤, submissions 앞), `WithdrawServiceTests.deletesDependenciesInOrderAndFilesAfterCommit` 에 `deleteAiReviewsOnMySubmissions`(submissions 삭제 앞).
- 응답 노출 점검 테스트 또는 grep: `rg -i "fit_score|fitScore|cosine" src/main/java/**/dto` → 결과 없음.

## Validation Steps

1. **DB 재생성(V1 변경)**: 로컬 MariaDB `hawe` 스키마를 백업(덤프) 후 drop/create 하고 앱을 한 번 기동해 Flyway 가 V1·V2 를 다시 적용하게 한다. 방법은 보고서에 기록. DB 가 없으면 DB 의존 테스트(`HaweApplicationTests`, `*MapperTests`, `*IntegrationTests`, `*ControllerTests` 중 `@SpringBootTest`)는 "미검증" 으로 보고(다른 테스트는 통과시킨다).
2. `.\mvnw.cmd clean test -q` — 기본(비전 비활성)에서 전체 통과. 실패 시 보고서에 전체 출력 요약.
3. `rg -n "fit_score|fitScore|cosine|\"fit\"" src/main/java` — 응답 DTO(`submission/dto`)에 점수 필드가 없고 `AiReviewRow`/`AiReviewMapper`/`TopicFitService`/`VisionClient` 내부에만 있음을 확인.
4. `rg -n "EnableAsync|@Async" src/main/java` — `AsyncConfig` 1곳, `TopicFitReviewListener` 1곳, 실행기 이름 `visionExecutor` 명시.
5. `git diff --check`, `git status` 로 의도하지 않은 파일(특히 `ml/`, `CLAUDE.md`, `AGENTS.md`) 변경 없음 확인. Postman JSON 을 고쳤다면 `python -c "import json,sys; json.load(open(r'docs/BACKEND/postman/HAWE_SUBMISSION.postman_collection.json', encoding='utf-8'))"` 로 파싱 확인.
6. **로컬 종단 스모크(가능하면, 불가능하면 "미검증"으로 보고)**: `cd ml\vision-service` 에서 `uvicorn app:app --port 8001` 기동(모델 약 1.1GB 다운로드 필요, 이미 캐시돼 있으면 빠름) → 백엔드를 `HAWE_VISION_ENABLED=true` 로 기동 → 사진 틀 미션에 사진 제출 → 수 초 뒤 `GET /api/missions/{id}/submissions` 에 `aiReview` 가 나타나는지(점수에 따라 안 나올 수도 있으므로 `submission_ai_reviews` 행의 `status=DONE`, `fit_score` 를 DB 로 확인). 비전 서비스를 끈 채 제출 → 제출은 성공, 행은 `FAILED(attempts=2)`.
7. 보고서(`.agent/report.md`)에 포함: 변경 파일 목록, DB 재생성 방법과 결과, 실행한 테스트와 결과(통과/실패/미검증), 설계 결정 중 계획과 달라진 점(예: `UNIQUE(submission_id, kind)`, 틀별 임계값, `AbortPolicy` 대신 로그 후 버림), 한계(재처리 없음, 극소 경합), 모델/버전(`clip-ViT-B-32+multilingual-v1`, version `1`).

## Potential Merge Conflicts

- `SubmissionService`/`SubmissionResponse`: 앞 단계(B0·B1·B5·B7)는 모두 커밋됨. 이후 **B4(파일 정리, 같은 `FileStorage`·`GroupService.deleteGroupCascade`·`WithdrawService` 를 건드림)** 가 이 작업 뒤에 오므로 B10 연동 커밋·푸시 후 시작해야 한다. **A6/A7(Android)** 은 `aiReview` 응답 모양에 의존 — 필드명 `aiReview.badge`/`aiReview.comment` 불변.
- `GroupMapper`/`GroupService`/`WithdrawMapper`/`WithdrawService`: 삭제 연쇄(B4 가 같은 메서드 안에 key 수집을 추가함).
- `V1__initial_schema.sql`: 우준의 B9 등이 V1 에 컬럼을 더한 이력이 있음 — 이 변경은 새 테이블 추가뿐이라 충돌 가능성 낮음. 단톡 공지(로컬 DB 재생성) 필요.
- `application.properties`, `HaweApplication` 불변, 신규 `AsyncConfig` — **B11** 이 `insightExecutor` 를 같은 파일에 추가하므로 파일명·`@EnableAsync` 위치를 바꾸지 않는다.
- 문서: `docs/FEATURE/PROJECT_OVERVIEW.md` §6/§10, `docs/BACKEND/MISSIONS_API.md`, `BACKEND_DB.md`, `CONTRACT_DECISIONS.md`.
