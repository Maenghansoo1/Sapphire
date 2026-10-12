# 계약 반영 2차 / 7단계(백엔드): 반응(스티커) · 댓글 (Reactions · Comments)

> 범위: **백엔드만** (`src/`, `docs/BACKEND/`). Android/iOS 는 이번에 건드리지 않는다(스티커·멘션·이미지 UI 는 Figma 디자인 후 7A 로 별도). 알림(`comment`/`mention` 알림, FCM, `notifications` 행)은 9단계 몫이므로 **이번에는 만들지 않는다**(빈 인터페이스·이벤트도 만들지 않는다). 결정 근거: `docs/BACKEND/CONTRACT_DECISIONS.md` §7 (사용자 결정 완료). 이번 계획에서 §7-4 미결이던 "이미지만 있는 댓글"은 **허용**으로 확정됐다.

## Goal

1. `POST /api/submissions/{id}/reactions` — 스티커 **토글**(같은 종류를 다시 누르면 취소). 응답은 토글 후 이 작품의 **반응 전체**.
2. 댓글 API 3개: `POST /api/submissions/{id}/comments`(multipart, 멘션·이미지), `GET /api/submissions/{id}/comments`(페이지네이션, 작성 순), `DELETE /api/comments/{commentId}`(본인만, 204, 첨부 이미지 삭제).
3. 피드·상세(`SubmissionResponse`)의 `reactions`, `commentCount` 를 **실제 값**으로 채운다(지금은 `List.of()`, `0` 고정).

## Findings

### 이미 있는 것
- 테이블(`src/main/resources/db/migration/V1__initial_schema.sql` 116~151행): `comments(id, submission_id, user_id, content text NOT NULL, image_url varchar(500) NULL, created_at)`, `comment_mentions(id, comment_id, mentioned_user_id)`, `reactions(id, submission_id, user_id, sticker_type varchar(50))`. **`reactions` 에 유니크 키가 없다.** `comments.image_url` 은 파일 **key**(예: `comments/uuid.jpg`)를 담는 용도로 쓴다 — `submissions.media_url` 이 key 를 저장하고 응답 때 `storage.publicUrl(key)` 로 URL 을 만드는 것과 같은 방식.
- 그룹 삭제 `GroupService.deleteGroupCascade`(약 283행)는 이미 `comment_mentions → comments → reactions → ... → submissions` 순으로 지운다(`GroupMapper.deleteCommentMentionsByGroupId` 등). **변경 불필요.** (그룹 삭제 뒤 댓글 이미지 파일 정리는 §6 에서 후속으로 미룬 항목 — 이번에도 하지 않는다.)
- DTO 껍데기: `submission/dto/ReactionSummary(String stickerType, int count, boolean reactedByMe, List<Long> userIds)` — 이미 계약 모양. `SubmissionResponse(... List<ReactionSummary> reactions, int commentCount, ...)` — 필드도 이미 있음. `SubmissionService.response(SubmissionRow)`(맨 아래)가 `List.of(), 0` 을 넣고 있다.
- `submission/service/SubmissionService`:
  - `submit`(multipart, `FileTypeDetector.detect` 로 매직바이트 검사, `storage.store("submissions", bytes, ext)`, **트랜잭션 롤백 시 파일 삭제**하는 `TransactionSynchronization.afterCompletion` 패턴, `DuplicateKeyException` 처리) — 댓글 이미지 저장에 같은 패턴을 쓴다.
  - `feed(userId, missionId, page, size)`: 미션 404 → 멤버 403 → `requireSubmitted` 423 → `findFeedByMissionId` 결과를 `this::response` 로 변환(항목별 쿼리 금지 규칙을 이번에 지켜야 함: 아래 참고).
  - `detail(userId, submissionId)`: `findDetailById` null 이면 404 `SUBMISSION_NOT_FOUND` → `requireMember(row.groupId)` 403 `NOT_GROUP_MEMBER` → `requireSubmitted(row.missionId)` 423 `FEED_LOCKED`. **반응·댓글의 접근 규칙(404/403/423)이 이것과 정확히 같다.**
  - `private requireMember`, `private requireSubmitted`.
- `SubmissionErrorCode`: `FEED_LOCKED`(423), `SUBMISSION_NOT_FOUND`(404), `UNSUPPORTED_FILE_TYPE`(415), `ALREADY_SUBMITTED`, `INVALID_SUBMISSION`. `GroupErrorCode.NOT_GROUP_MEMBER`(403). `CommonErrorCode.VALIDATION_ERROR`(400), `FILE_TOO_LARGE`(413). `BusinessException(ErrorCode)` / `BusinessException(ErrorCode, String message)`.
- `global/storage`: `FileStorage{store(dir, bytes, ext), delete(key), publicUrl(key)}`, `FileTypeDetector.detect(bytes)` → `Detected(mediaType IMAGE|AUDIO, extension jpg|png|webp|m4a|webm|mp3|aac)`. `/files/**` GET 은 이미 공개(`SecurityConfig`). `application.properties`: `spring.servlet.multipart.max-file-size=10MB`, `max-request-size=12MB` — 댓글 이미지 10MB 에 맞는다.
- `GlobalExceptionHandler`: `MaxUploadSizeExceededException` → 413 `FILE_TOO_LARGE`, `MultipartException`/`MissingServletRequestPartException` → 400, `MethodArgumentTypeMismatchException`/`MissingServletRequestParameterException` → 400, `HttpMediaTypeNotSupportedException` → 415 `UNSUPPORTED_MEDIA_TYPE`, `MethodArgumentNotValidException` → 400 `VALIDATION_ERROR`.
- 컨트롤러 스타일: `@RestController @RequestMapping("/api")`, `@AuthenticationPrincipal Long userId`, 생성자 주입. `PokeController`(JSON body + `@Valid` request record) 와 `SubmissionController`(multipart) 가 참고 예.
- 멤버 프로필 응답: `group/dto/MemberProfileResponse(userId, nickname, characterKey, profileImageUrl)`. 제출 쿼리(`SubmissionMapper.FIELDS/JOINS`)가 `users u` + `LEFT JOIN characters c ON c.id = u.character_id`, `c.svg_key AS character_key` 로 가져온다. `profileImageUrl` 은 아직 항상 `null`(8단계).
- MyBatis 는 애노테이션 매퍼(`@Select/@Insert/@Delete`), 목록 IN 은 `@Select("<script>... <foreach ...>")`(예: `MissionMapper`, `GroupMapper.findMemberPreviewsByGroupIds`). `map-underscore-to-camel-case` 가 켜져 있다(다른 Row 클래스들이 그렇게 쓴다 — `SubmissionRow`, `MissionMemberRow`). Row 클래스는 getter/setter 스타일(record 아님).
- 테스트 스타일: Mockito 서비스 단위(`SubmissionServiceTests` — 31행 `new SubmissionService(missions, members, submissions, storage, new ObjectMapper())`), 실 MariaDB `@SpringBootTest` + `JdbcTemplate`(`SubmissionIntegrationTests`, 매퍼 테스트), MockMvc 통합(`SubmissionControllerTests`: `@SpringBootTest @AutoConfigureMockMvc`, `@DynamicPropertySource` 로 임시 업로드 디렉터리, `tokens.createAccessToken(userId, nickname)` 으로 `Authorization: Bearer`, 사용자·그룹을 직접 INSERT/`GroupService.createGroup` 로 만들고 `finally` 에서 정리).
- 최신 Flyway 는 V1·V2 뿐. 프로젝트 결정(5단계에서 확정, `.agent/plan.md` 이전 버전): **개발 DB 를 지우고 Flyway 를 V1 부터 다시 쓴다 — V1 은 "최종 스키마"이므로 새 마이그레이션이 아니라 V1 을 직접 수정**한다.

### 계약 (CONTRACT_DECISIONS §7 — 그대로 구현)

스티커 5종(서버가 enum 한 곳에서 관리, 목록 밖은 400): `heart`, `laugh`, `wow`, `sad`, `fire`.

```json
// POST /api/submissions/{id}/reactions    Request
{ "stickerType": "heart" }
// Response 200 — 토글 후 이 작품의 반응 전체
{ "reactions": [
  { "stickerType": "heart", "count": 2, "reactedByMe": true,  "userIds": [1, 3] },
  { "stickerType": "fire",  "count": 1, "reactedByMe": false, "userIds": [5] } ] }
```
- 0개인 종류는 응답에서 뺀다. 순서는 **enum 선언 순서**(heart→laugh→wow→sad→fire) 고정, `userIds` 는 반응을 누른 순서(reactions.id 오름차순). 피드·상세의 `reactions` 도 같은 배열(요청자 기준 `reactedByMe`).
- 오류: 404 `SUBMISSION_NOT_FOUND`, 403 `NOT_GROUP_MEMBER`, 423 `FEED_LOCKED`, 400 `VALIDATION_ERROR`(목록 밖 종류·빈 값). 내 작품에 반응 허용. 그룹을 나간 사람의 반응은 그대로 남는다(삭제 로직 만들지 말 것).

```text
POST /api/submissions/{id}/comments   multipart/form-data
  content          text, 선택. 있으면 trim 후 1~300자(코드포인트 기준)
  mentionedUserIds 반복 필드, 선택. 같은 그룹 멤버만(아니면 400)
  file             선택. 이미지 jpg/png/webp ≤ 10MB
```
**확정(이번 질의 결과): content 와 file 중 하나는 반드시 있어야 한다.** content 가 비었고(없음/공백뿐) file 도 없으면 400 `VALIDATION_ERROR`. 이미지만 있는 댓글은 `content` 를 빈 문자열 `""` 로 저장하고 응답도 `""`(null 아님).
```json
// Response 201 — 목록 항목도 같은 모양
{ "id": 2,
  "author": { "userId": 3, "nickname": "잎사귀", "characterKey": "ipsagwi", "profileImageUrl": null },
  "content": "@몽실이 이거 뭐야", "imageUrl": null, "mentionedUserIds": [1],
  "createdAt": "2026-09-28T21:10:00+09:00", "mine": true }
```
- 목록 `GET /api/submissions/{id}/comments?page=0&size=20`: `PageResponse`(content/page/size/totalPages/totalElements), 정렬 `created_at ASC, id ASC`, page 음수→0, size 1~100 보정(피드와 같은 보정 규칙). `mentionedUserIds` 는 멘션 행 id 오름차순.
- `DELETE /api/comments/{commentId}` — 204. 없으면 404 `COMMENT_NOT_FOUND`, 남의 댓글이면 403 `NOT_COMMENT_OWNER`. 첨부 이미지 파일도 삭제(커밋 후). 멘션 행도 삭제.
- 댓글 작성·조회·삭제 모두 피드 잠금 규칙(404 `SUBMISSION_NOT_FOUND`, 403 `NOT_GROUP_MEMBER`, 423 `FEED_LOCKED`)을 따른다. 삭제는 "댓글 존재(404) → 작품 접근(403/423) → 소유자(403 `NOT_COMMENT_OWNER`)" 순서.
- `mine` = `comment.user_id == 요청자`. 작성자는 `author`(§2 프로필 객체).
- 이 단계 에러 코드 신규: `CommentErrorCode { COMMENT_NOT_FOUND(404), NOT_COMMENT_OWNER(403) }`. 잘못된 입력은 `CommonErrorCode.VALIDATION_ERROR` + 메시지(`new BusinessException(CommonErrorCode.VALIDATION_ERROR, "...")`), 파일 형식은 기존 `SubmissionErrorCode.UNSUPPORTED_FILE_TYPE`(415), 크기는 `CommonErrorCode.FILE_TOO_LARGE`(413) 재사용.

## Root Cause / Key Implementation Points

1. **접근 검사 중복 방지**: 반응·댓글 작성·조회·삭제가 모두 `SubmissionService.detail` 과 같은 404/403/423 검사를 요구한다. `SubmissionService` 에 **`public SubmissionRow requireAccessible(Long userId, Long submissionId)`** (기존 `detail` 앞부분을 그대로 뽑아낸 것; `detail` 도 이것을 쓰게)을 두고, 새 `ReactionService`/`CommentService` 가 이를 주입받아 쓴다. `SubmissionService` 는 새 서비스에 의존하지 않고 **매퍼에만** 의존하므로 순환이 없다. 새 서비스마다 `requireMember`/`requireSubmitted` 를 복붙하지 말 것.
2. **피드 N+1 금지**: `feed` 는 페이지 항목 id 목록으로 반응 1회 + 댓글 수 1회 쿼리(IN)로 모아서 채운다. 항목마다 쿼리 금지. `detail`/`submit` 은 단건 id 로 같은 쿼리를 재사용(리스트 1개). `submit` 응답은 새 작품이므로 쿼리 없이 `reactions=[]`, `commentCount=0` 으로 둬도 된다(단, 한 곳의 헬퍼를 거쳐도 무방).
3. **반응 집계는 한 곳**: `ReactionService` 에 `public static List<ReactionSummary> summarize(List<ReactionRow> rows, Long viewerId)`(rows 는 한 작품의 것, enum 순서·0개 제외·`userIds` id 오름차순)를 두고 `SubmissionService` 와 토글 응답이 같이 쓴다.
4. **토글과 동시성**: `reactions` 에 `UNIQUE (submission_id, user_id, sticker_type)` 를 추가한다. 토글은 `DELETE ... WHERE submission_id=? AND user_id=? AND sticker_type=?` 의 영향 행 수가 0 이면 `INSERT`. 두 요청이 동시에 INSERT 해 `DuplicateKeyException` 이 나면 **이미 반응된 상태로 간주하고 그대로 진행**(예외를 삼키고 현재 상태를 읽어 응답; 500 금지). 토글 전체는 `@Transactional`.
5. **멘션 검증**: `mentionedUserIds` 를 중복 제거(입력 순서 유지) 후, 각 id 가 **그 작품이 속한 그룹의 멤버**(`GroupMemberMapper.existsByGroupIdAndUserId`)인지 검사. 하나라도 아니면 400 `VALIDATION_ERROR`(저장·파일 저장 전에 검사). 자기 자신 멘션은 허용(금지 규칙 없음). 개수 상한은 두지 않는다(그룹 정원이 상한).
6. **댓글 이미지 저장**: `submit` 과 동일한 패턴 — 검증 모두 통과 후 `storage.store("comments", bytes, ext)`, `TransactionSynchronizationManager.isSynchronizationActive()` 면 `afterCompletion(status != STATUS_COMMITTED)` 에서 파일 삭제 등록. 이미지만 허용(`Detected.mediaType()` 이 `"IMAGE"` 아니면 `UNSUPPORTED_FILE_TYPE`). `FileTypeDetector` 는 jpg/png/webp 를 모두 `IMAGE` 로 판별한다 — 그대로 사용(gif 등은 null → 415).
7. **삭제 시 파일**: 댓글 행/멘션 행 삭제는 트랜잭션 안, 파일 삭제는 **커밋 후**(`afterCommit`) — 롤백됐는데 파일만 사라지는 것을 막는다. 파일 삭제 실패(`UncheckedIOException`)는 삼키지 말고 로그만 남기고 넘어가도 되나, 최소한 응답을 500 으로 만들지는 말 것(커밋 후라 이미 데이터는 삭제됨) — `afterCommit` 안에서 try/catch + `log.warn`.
8. **작성자/응답 조립**: 목록은 `comments` ⟕ `users` ⟕ `characters` 한 쿼리로 `author` 를 만들고, 멘션은 해당 페이지 comment id 들로 IN 쿼리 1회. 댓글 단건 응답(작성 직후)은 방금 만든 id 로 같은 단건 조회 쿼리를 재사용하거나 값을 직접 조립.

## Files / Functions to Change

신규 패키지는 프로젝트 규칙대로 **feature 단위 + 5개 서브패키지(controller/service/dto/domain/dao)만** 쓴다.

| 파일 | 변경 |
|---|---|
| `src/main/resources/db/migration/V1__initial_schema.sql` | `reactions` 에 `UNIQUE KEY uk_reactions_submission_user_sticker (submission_id, user_id, sticker_type)` 추가(기존 `fk_reactions_*` KEY·CONSTRAINT 는 유지). `comments`·`comment_mentions` 는 변경 없음. `V2` 는 건드리지 않는다. |
| `reaction/domain/StickerType.java` (신규) | enum `HEART("heart"), LAUGH("laugh"), WOW("wow"), SAD("sad"), FIRE("fire")`. `String value()`, `static StickerType fromValue(String)`(없으면 null 또는 Optional — 호출부에서 400). **스티커 종류의 유일한 정의처**. |
| `reaction/dao/ReactionMapper.java` (신규) | `int delete(submissionId, userId, stickerType)`, `void insert(submissionId, userId, stickerType)`, `List<ReactionRow> findBySubmissionId(submissionId)`, `List<ReactionRow> findBySubmissionIds(@Param("ids") List<Long> ids)` (`<script><foreach>`; 정렬 `submission_id, id`). |
| `reaction/dao/ReactionRow.java` (신규) | `submissionId`, `userId`, `stickerType` (getter/setter 스타일). |
| `reaction/dto/ReactionRequest.java` (신규) | `record ReactionRequest(@NotBlank String stickerType)` |
| `reaction/dto/ReactionsResponse.java` (신규) | `record ReactionsResponse(List<ReactionSummary> reactions)` (`ReactionSummary` 는 `submission/dto` 의 기존 타입을 그대로 import — 옮기지 말 것) |
| `reaction/service/ReactionService.java` (신규) | `@Transactional ReactionsResponse toggle(Long userId, Long submissionId, ReactionRequest)`: ① `StickerType.fromValue` 실패 → 400 `VALIDATION_ERROR`(DB 조회 전) ② `submissionService.requireAccessible(userId, submissionId)` ③ delete 0행이면 insert(`DuplicateKeyException` 은 삼킴) ④ `findBySubmissionId` → `summarize` 로 응답. `public static List<ReactionSummary> summarize(List<ReactionRow>, Long viewerId)`. |
| `reaction/controller/ReactionController.java` (신규) | `POST /api/submissions/{submissionId}/reactions` → 200, `@Valid @RequestBody ReactionRequest`. |
| `comment/dao/CommentMapper.java` (신규) | `insert(CommentInsertRow)`(`useGeneratedKeys`), `insertMention(commentId, userId)`, `findDetailById(id)`(author 조인 → `CommentRow`), `findByIdForDelete`(필요하면 같은 `findDetailById` 재사용 + `submission_id`), `findPageBySubmissionId(submissionId, offset, limit)`, `countBySubmissionId`, `findMentionsByCommentIds(@Param("ids") List<Long>)`(`List<CommentMentionRow{commentId, mentionedUserId}>`, 정렬 `comment_id, id`), `deleteMentionsByCommentId`, `deleteById`, **`List<CommentCountRow> countBySubmissionIds(@Param("ids") List<Long>)`**(`submissionId`, `count`; 댓글 0개인 작품은 행이 없을 수 있으니 호출부에서 기본값 0). |
| `comment/dao/CommentRow.java`, `CommentInsertRow.java`, `CommentMentionRow.java`, `CommentCountRow.java` (신규) | Row 클래스(getter/setter). `CommentRow`: `id, submissionId, userId, nickname, characterKey, content, imageUrl(key), createdAt(LocalDateTime)`. |
| `comment/dto/CommentResponse.java` (신규) | `record CommentResponse(Long id, MemberProfileResponse author, String content, String imageUrl, List<Long> mentionedUserIds, OffsetDateTime createdAt, boolean mine)` — **`@JsonInclude(NON_NULL)` 쓰지 말 것**(계약 예시에 `"imageUrl": null`). `createdAt` 은 `SeoulTime.toOffset(...)`. |
| `comment/service/CommentErrorCode.java` (신규) | `COMMENT_NOT_FOUND(NOT_FOUND, "댓글을 찾을 수 없습니다")`, `NOT_COMMENT_OWNER(FORBIDDEN, "본인 댓글만 삭제할 수 있습니다")` (`SubmissionErrorCode` 와 같은 구조로 `ErrorCode` 구현). |
| `comment/service/CommentService.java` (신규) | `@Transactional create(userId, submissionId, String content, List<Long> mentionedUserIds, MultipartFile file)`, `@Transactional(readOnly=true) PageResponse<CommentResponse> list(userId, submissionId, page, size)`, `@Transactional delete(userId, commentId)`. 접근 검사는 `SubmissionService.requireAccessible`. 위 Key Point 5·6·7·8. 생성자 의존: `CommentMapper`, `SubmissionService`, `SubmissionMapper`(delete 시 댓글→작품→그룹 필요하면), `GroupMemberMapper`, `FileStorage`. |
| `comment/controller/CommentController.java` (신규) | `POST /api/submissions/{submissionId}/comments`(`consumes = MULTIPART_FORM_DATA_VALUE`, `@RequestParam(required=false) String content`, `@RequestParam(required=false) List<Long> mentionedUserIds`, `@RequestPart(required=false)`/`@RequestParam(required=false) MultipartFile file`; 201), `GET .../comments`(`page` 기본 0, `size` 기본 20), `DELETE /api/comments/{commentId}`(204). |
| `submission/service/SubmissionService.java` | ① `public SubmissionRow requireAccessible(Long userId, Long submissionId)` 추가(= 기존 `detail` 의 404→403→423 검사, 행 반환), `detail` 이 이것을 사용. ② 생성자에 `ReactionMapper`, `CommentMapper` 추가. ③ `response(row)` 를 `response(row, List<ReactionSummary> reactions, int commentCount)` 형태로 바꾸고 `feed` 는 `findBySubmissionIds`/`countBySubmissionIds` 를 **페이지당 1회씩** 호출해 submissionId 별로 묶어 채움(요청자 `userId` 로 `reactedByMe`), `detail` 은 단건, `submit` 은 `List.of()`/`0`. |
| 테스트 | 아래 "Tests Required" |
| 문서 | 아래 "Documentation" |

### Documentation (코드와 같은 변경에서 갱신)
- `docs/BACKEND/API_DESIGN.md` §4: 댓글을 multipart/`author`/`mine`/`DELETE /comments/{commentId}`/`imageUrl` 응답으로, 반응 응답을 `{ reactions: [...] }` 로, 스티커 5종과 오류 코드 표 반영(요청 본문의 `imageUrl` 필드 삭제). §0-1 스티커 결정 보완.
- `docs/BACKEND/MISSIONS_API.md`: 피드·상세의 `reactions` 모양과 `commentCount` 실제 값 설명(약 256~282행, "후속 단계에서 채운다" 문구 정리). **319행 근처 `reactionCount` 가 있는 "정답 맞히기" 예시는 이번 범위 밖이므로 손대지 말 것.**
- `docs/BACKEND/BACKEND_DB.md`: `reactions` 유니크 키, 댓글 `image_url` 은 key 저장·`content` 빈 문자열 허용.
- `docs/BACKEND/CONTRACT_DECISIONS.md`: §7 제목 상태를 "반영됨 (백엔드; 알림은 9단계, 앱은 7A)" 로, §7-4 의 "이미지만 있는 댓글" 을 "허용으로 확정" 으로, §6-2 의 "1차는 `reactions: []`, `commentCount: 0` 고정" 문구에 "7단계에서 채워짐" 추가.
- `docs/BACKEND/CONTRACT_WORK_PLAN.md`: 7단계 행 비고에 한 줄(백엔드 완료, 앱 7A 대기).
- 신규 `docs/BACKEND/postman/HAWE_REACTION_COMMENT.postman_collection.json` (기존 `HAWE_POKE.postman_collection.json` 형식 참고, UTF-8, 한글 깨짐 없음): 토글, 댓글 작성(텍스트/이미지/멘션), 목록, 삭제.

## Implementation Steps

1. **스키마**: V1 `reactions` 에 유니크 키 추가. **로컬 DB 재생성 필요**(Flyway checksum 변경): 먼저 `mysqldump`/`mariadb-dump` 로 로컬 `hawe` DB 를 `%TEMP%` 에 백업하고(사용자 데이터가 있는 운영성 DB 는 절대 건드리지 말 것 — 로컬 개발 DB `localhost:3306/hawe` 만), `DROP DATABASE hawe; CREATE DATABASE hawe CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;` 후 테스트로 Flyway 재적용. 백업 경로·재생성 전 행 수를 보고서에 적는다. `flyway clean/repair` 설정을 코드에 넣지 말 것.
2. **반응 모듈**: `StickerType`, `ReactionRow`, `ReactionMapper`, `ReactionRequest`, `ReactionsResponse`, `ReactionService`(+ static `summarize`), `ReactionController`. 이 시점에 `SubmissionService.requireAccessible` 가 필요하므로 3번을 먼저/같이 한다.
3. **`SubmissionService` 수정**: `requireAccessible` 추출, 생성자에 `ReactionMapper`·`CommentMapper` 추가, `feed`/`detail`/`submit` 의 `reactions`·`commentCount` 채우기. (`CommentMapper` 의 `countBySubmissionIds` 가 먼저 필요 → 4번의 매퍼를 먼저 작성.) `SubmissionServiceTests` 생성자 호출 수정.
4. **댓글 모듈**: Row 클래스, `CommentMapper`, `CommentErrorCode`, `CommentResponse`, `CommentService`, `CommentController`.
5. **테스트 작성**(아래) 후 `mvnw.cmd clean test -q` 통과 확인.
6. **문서·Postman 갱신**.

## Constraints

- 범위는 **백엔드 + 문서**. `frontend/`·Android·iOS 소스, `V2__seed_characters.sql`, 인증/그룹 로직은 `GroupService` 를 포함해 건드리지 않는다. (그룹 삭제 cascade 는 이미 댓글·반응을 지운다.)
- 알림 훅·`notifications` 삽입·FCM 호출·이벤트 인터페이스를 만들지 않는다(9단계).
- 새 마이그레이션(V3)을 만들지 말고 V1 을 직접 수정한다(프로젝트 결정).
- 스티커 종류는 `StickerType` enum 한 곳에서만 정의하고 DB 에 CHECK/ENUM 컬럼 제약을 추가하지 않는다(`sticker_type varchar(50)` 유지).
- `ReactionSummary` 를 `submission/dto` 에서 옮기거나 필드를 바꾸지 않는다(이미 계약 모양).
- 항목마다 쿼리를 도는 구현(N+1) 금지. 접근 검사 코드 복붙 금지(`requireAccessible` 재사용).
- 패키지 규칙: `controller/service/dto/domain/dao` 5개 서브패키지만 사용(`model`/`repo` 금지). 코드 스타일은 주변(애노테이션 매퍼, 한 줄 getter/setter Row, 짧은 서비스 헬퍼) 에 맞추고 주석은 최소로.
- 커밋/푸시는 하지 않는다. 지시 파일(`CLAUDE.md`, `AGENTS.md`)은 수정하지 않는다.

## Edge Cases

- 반응: 같은 사용자가 같은 작품에 **다른 종류**를 여러 개 누를 수 있다(각각 별개 행). 같은 종류를 두 번 누르면 취소. 마지막 반응을 취소하면 `reactions: []`.
- 반응: 목록 밖 값(`"HEART"` 대문자 포함, 공백, 빈 문자열, `null`, 필드 누락, 잘못된 JSON) → 400 `VALIDATION_ERROR`. 대소문자 구분: 소문자만 허용.
- 반응·댓글: 작품 없음 404 / 그룹 비멤버 403 / 내가 그 미션에 아직 미제출이면 423 — **내가 제출하지 않았으면 내 것이 아닌 작품도 접근 불가**(기존 `requireSubmitted(missionId, userId)` 와 동일). 인증 없음 401.
- 반응: 내 작품에 반응 허용. 그룹을 나간 사용자의 기존 반응은 남아 집계에 포함된다(`userIds` 에 나간 사람 id 가 있을 수 있음 — 앱 몫).
- 댓글 `content`: 앞뒤 공백 trim, 코드포인트 301자 이상 → 400. 빈/공백뿐 + 파일 없음 → 400. 빈/공백뿐 + 파일 있음 → 저장 `""`.
- 댓글 `mentionedUserIds`: 중복 id 는 하나로 합침; 다른 그룹 사용자·존재하지 않는 id → 400 (이때 파일은 저장하지 않는다 — 검증 후 저장); 숫자가 아닌 값 → 400(`MethodArgumentTypeMismatchException` 처리기로 이미 400).
- 댓글 `file`: 빈 파일(0바이트)은 "파일 없음" 이 아니라 400 으로 처리하거나, 최소한 이미지만 있는 댓글 규칙에서 통과시키지 말 것; 음성/텍스트/가짜 확장자 → 415 `UNSUPPORTED_FILE_TYPE`; 10MB 초과 → 413 `FILE_TOO_LARGE`(요청 크기 초과는 기존 처리기가 413 으로 변환, 서비스 검사도 방어적으로 유지). 요청에 `file` 이 2개 이상이면 400.
- 댓글: DB 삽입 중 예외 → 트랜잭션 롤백 시 저장한 파일 삭제(`afterCompletion`). 삭제는 커밋 후 파일 삭제, 실패는 로그만.
- 댓글 삭제: 존재하지 않는 id → 404; 남의 댓글 → 403 `NOT_COMMENT_OWNER`; 이미 삭제된 댓글 재삭제 → 404; 그룹을 나간 작성자는 접근 검사에서 403 `NOT_GROUP_MEMBER`(계약대로 — 삭제 불가).
- 목록: 댓글 0개 → `content: []`, `totalPages: 0`; `size=1000` → 100 으로 보정; 다른 작품의 댓글이 섞이지 않는다.
- 피드 `commentCount`: 해당 작품 댓글 수(멘션 수 아님). 피드 페이지 안에 반응/댓글이 전혀 없는 작품도 `reactions: []`, `commentCount: 0`.
- 동시성: 같은 사용자의 동일 종류 토글 동시 요청 → 500 이 나면 안 된다(Key Point 4).
- 시간: `createdAt` 은 `OffsetDateTime`(+09:00, `SeoulTime.toOffset`). DB 는 `datetime`.

## Regression Risks

- **`SubmissionService` 생성자 시그니처 변경** → `SubmissionServiceTests` 와 `SubmissionService` 를 직접 생성하는 다른 테스트가 컴파일 실패. `@SpringBootTest` 기반 테스트는 자동 주입이라 영향 없음.
- **`response(...)` 시그니처 변경**이 `feed`/`detail`/`submit` 세 곳 모두에 영향. 기존 테스트(`SubmissionResponseTests`, `SubmissionControllerTests`, `SubmissionIntegrationTests`)가 `reactions == []`, `commentCount == 0` 을 가정하는 곳은 데이터가 없으면 여전히 참이어야 한다.
- 피드/상세 JSON 에 `reactions` 가 채워지면 Android `ApiClient.json` 은 `ignoreUnknownKeys=true` 라서 깨지지 않는다(참고). 앱은 `[]` 를 이미 받고 있다.
- **V1 수정 → 이미 V1 을 적용한 DB 는 Flyway checksum 불일치.** 로컬 DB 재생성 필수, 다른 환경(공유 개발 DB 등)은 별도 조치가 필요함을 보고서에 명시.
- 그룹 삭제 cascade 는 변경하지 않지만, 새 `reactions` 유니크 키가 `deleteReactionsByGroupId` 에 영향 없음을 `GroupContract*Tests` 통과로 확인.
- 댓글 이미지 파일은 그룹 삭제 시 고아로 남는다(§6 에서 후속 처리로 미룬 기존 한계 — 이번에 새로 만든 문제는 아님, 보고서에 한 줄 언급).

## Tests Required

- `reaction/service/ReactionServiceTests` (Mockito): 목록 밖 종류 → 400 (매퍼 호출 없음); 접근 검사 실패 전파; delete 0행 → insert 호출 / delete 1행 → insert 안 함; `DuplicateKeyException` 삼킴 → 정상 응답; `summarize` 순서(enum 순)·0개 제외·`reactedByMe`·`userIds` 순서.
- `reaction/dao/ReactionMapperTests` (실 MariaDB, `@SpringBootTest @Transactional`, `JdbcTemplate`): insert/delete/조회, 유니크 키 위반 시 `DuplicateKeyException`, `findBySubmissionIds` 정렬.
- `comment/service/CommentServiceTests` (Mockito): content/파일 조합(둘 다 없음 400 / 이미지만 OK 가 `""` 저장 / 301자 400 / trim); 멘션 중복 제거·비멤버 400 시 파일 미저장; 이미지가 아닌 파일 415; 10MB 초과 413; 삭제 순서(404 → 접근 → 소유자 403)와 멘션·댓글 삭제.
- `comment/dao/CommentMapperTests` (실 MariaDB): 삽입/조회/멘션/페이지/정렬/`countBySubmissionIds`(0개 작품 포함 여부)/삭제.
- `submission/service/SubmissionServiceTests` 수정 + 케이스 추가: `feed` 가 `findBySubmissionIds`·`countBySubmissionIds` 를 **한 번씩만** 호출(항목 수와 무관, `verify(times(1))`), `reactedByMe` 가 요청자 기준.
- MockMvc 통합 `reaction/controller/ReactionControllerTests`, `comment/controller/CommentControllerTests`(`SubmissionControllerTests` 스타일, 임시 업로드 디렉터리, 사용자 2명 이상 그룹): 
  - 401(토큰 없음), 423(미제출자), 403(비멤버), 404, 400(잘못된 스티커);
  - 토글 on→off 와 JSON 모양(`reactions[].stickerType/count/reactedByMe/userIds`);
  - 다른 사용자가 같은 작품 피드/상세를 볼 때 `reactions`·`commentCount` 반영(요청자별 `reactedByMe` 다름);
  - 댓글 multipart 작성(텍스트+멘션 반복 필드), 이미지만, 이미지 파일이 `/files/comments/...` 로 실제 접근, 목록 페이지네이션·작성 순, `mine`, 삭제 204 후 파일 삭제 확인(임시 디렉터리에서 파일 없음), 남의 댓글 삭제 403 `NOT_COMMENT_OWNER`, 없는 댓글 404 `COMMENT_NOT_FOUND`, JSON 으로 보내면 415;
  - 테스트 데이터는 `finally` 에서 정리(기존 테스트처럼 `groups.leaveGroup`/`deleteGroup` + users 삭제; 반응·댓글 행이 FK 로 사용자 삭제를 막지 않도록 정리 순서 주의).
- 기존 `GroupContractServiceTests`/`GroupContractMapperTests`(그룹 삭제 cascade)가 그대로 통과하는지 확인.

## Validation Steps

1. `.\mvnw.cmd clean test -q` (Java 25, 로컬 MariaDB 필요) — 전체 통과, 테스트 수·실패 0 을 보고서에 기록. 별도 lint/typecheck 명령은 없다.
2. `rg "reactions\(\)|List.of\(\), 0" src/main` 등으로 `SubmissionService` 에 하드코딩된 빈 반응/0 이 `submit` 외에 남지 않았는지 확인.
3. Postman JSON 을 `ConvertFrom-Json` 으로 파싱, 한글 대체 문자(�) 없음 확인.
4. `git diff --check` — 공백 오류 없음(LF/CRLF 경고는 무시).
5. 서버를 띄운 수동 HTTP 스모크가 가능하면 수행(가입→그룹→미션 제출→반응 토글→댓글 작성/삭제), 못 했으면 보고서에 "미검증" 으로 명시하고 MockMvc 로 대체한 범위를 적는다.
6. 보고서(`.agent/report.md`)에 포함: 변경 파일과 동작, 검증 결과, DB 재생성 여부·백업 경로, 남은 위험(공유 DB checksum, 그룹 삭제 후 댓글 이미지 고아 파일), 범위 밖으로 둔 것(알림 9단계, 앱 7A).

## Potential Merge Conflicts

- `submission/service/SubmissionService.java`, `SubmissionServiceTests.java`: 6단계 이후 가장 많이 수정되는 파일. 8단계(프로필 사진: `MemberProfileResponse` 의 `profileImageUrl` 채움)와 9단계(알림 훅)가 같은 파일/응답 조립부를 건드릴 가능성이 높다 → **8·9단계와 동시에 진행하지 말 것**, 7단계 커밋 뒤에 진행.
- `V1__initial_schema.sql`: 다른 단계가 V1 을 같이 수정하면 충돌/체크섬 문제. 현재 작업 트리는 깨끗하다(`git status` 변경 없음; node_modules 미추적 제외).
- `docs/BACKEND/CONTRACT_DECISIONS.md`, `CONTRACT_WORK_PLAN.md`, `API_DESIGN.md`, `MISSIONS_API.md`, `BACKEND_DB.md`: 다른 단계(8·9)가 같은 문서를 갱신 → 7단계에서는 §7 / 해당 절만 수정.
- 앱(Android/iOS)은 이번에 건드리지 않으므로 앱 쪽 병렬 작업과 겹치지 않는다. 단, 응답에 `reactions` 가 채워지므로 앱이 `[]` 가정으로 파싱하는 곳이 없는지는 7A 에서 확인.
