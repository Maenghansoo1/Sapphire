# B6. 미션 틀 확장 ② — 정답 맞히기(G) 틀: 소리 흉내·성대모사 + 정답 시도 API (백엔드)

> 범위: 백엔드만. 앱은 A4. 알림 무관(정답을 맞혀도 알림 없음 — MISSIONS_API §11-19). **전제: B5 커밋 후**(B5 가 제출 검증 구조·`MissionProperties`·선택기 풀 설정을 바꾼다). 근거: `MISSIONS_API.md` §1-2(`VOICE_MIMIC`, `VOICE_IMPRESSION`: G 유형), §3-1/3-2(`answer`, `alias`), §4-1(정답 맞히기), §10(에러 코드), §11-17·18.

## Goal

1. `VOICE_MIMIC`, `VOICE_IMPRESSION` 제출(음성 + 정답 + 별칭).
2. `POST /api/submissions/{id}/guesses` — 다른 멤버가 정답을 시도(최대 3회).
3. 피드·상세·제출 응답에 `answer`(공개 조건 충족 시)와 `myGuess {solved, attemptsLeft}` 노출. **별칭은 절대 노출하지 않는다.**

## Findings

- 두 틀은 `MissionTemplate` enum 에 이미 있다(`VOICE_MIMIC`, `VOICE_IMPRESSION`, params 없음, payload `file`, `answer`, `alias[]`). 발행 풀에는 없고(B5 이후 `enabled-templates` 설정으로 제어), `PlaceholderMissionGenerator` 도 처리하지 않는다.
- DB(이미 있음, V1): `submissions.answer_text varchar(100)`, `submissions.answer_aliases longtext JSON(체크)`, 그리고 `submission_guesses(id, submission_id, user_id, guess_text varchar(100), is_correct, created_at)` + 인덱스 `(submission_id, user_id)`(UNIQUE 아님). `GroupMapper.deleteSubmissionGuessesByGroupId`(그룹 삭제 cascade)·8단계 탈퇴 삭제가 이미 이 테이블을 지운다. **스키마 변경 불필요.**
- `SubmissionService.submit`: `audio`(VOICE_LINE) 분기 — `file`/`duration`/`waveform`/`caption` 허용, 음성 길이 ≤30초, 파형 ≤64개(0~1), `answer_data = {duration, waveform}` 저장. 중복 필드는 일괄 거부(`for (values) if (size != 1) invalid("중복된 필드입니다")`) — **`alias` 는 반복이 허용되어야 하므로 이 검사에 예외가 필요**.
- 접근 검사: 7단계 이후 `SubmissionService.requireAccessible(userId, submissionId)`(404/403/423; 행 `SubmissionRow` 반환)가 있다 — 정답 시도에서 재사용. 미션 마감 시각은 `missions.deadline_at`; `SubmissionMapper.FIELDS` 에는 아직 없음(`m.deadline_at` 추가 필요).
- `MissionProperties`(B5 이후): `defaultPublishTime, defaultDeadlineTime, maxDailyCount, enabledTemplates`. 여기에 `guessMaxAttempts`(기본 3), `maxAliases`(기본 5) 추가.
- 에러 코드(§10): `GUESS_NOT_SUPPORTED`(400), `CANNOT_GUESS_OWN_SUBMISSION`(409), `ALREADY_SOLVED`(409), `GUESS_LIMIT_EXCEEDED`(409), 빈 값은 400 `VALIDATION_ERROR`. 현재 `MissionErrorCode` 에는 `MISSION_NOT_FOUND`/`MISSION_CLOSED`(+B5 의 `RESULT_NOT_SUPPORTED`)뿐.
- 응답 `SubmissionResponse` 는 `@JsonInclude(NON_NULL)` → `answer == null` 이면 JSON 에서 생략됨(계약 예시의 `"answer": null` 과 의미 동일 — 앱은 키 부재를 null 로 처리, Android `ApiClient.json` 은 `explicitNulls=false`). 이 점을 문서에 명시.

## Root Cause / Key Implementation Points

1. **제출 규칙 (두 틀 공통)**: 허용 필드 `file`, `duration`, `waveform`, `answer`, `alias`(반복), `caption`. 필수 `file`(음성), `duration`, `waveform`(VOICE_LINE 과 같은 이유 — 앱의 재생 UI), `answer`. 검증: 음성 규칙은 VOICE_LINE 과 동일(≤5MB, ≤30초, 파형), `answer` trim 후 1~50 코드포인트(공백뿐 → 400), `alias` 각 1~50 코드포인트·최대 5개·빈 값 무시 불가(빈 문자열이면 400), 정규화 후 `answer` 와 같거나 서로 같은 별칭은 **조용히 제거**. 저장: `answer_text = answer`, `answer_aliases = JSON 배열`, `answer_data = {duration, waveform}`(기존 형식 유지).
2. **정규화 함수**(한 곳): `normalizeAnswer(s)` = Unicode NFC → 모든 공백 문자 제거 → `toLowerCase(Locale.ROOT)`. 정답 비교는 `normalize(guess) ∈ {normalize(answer_text)} ∪ {normalize(alias)…}` **완전 일치만**(오타 허용 없음). 위치: `submission/domain/AnswerNormalizer`(순수 static) — 제출 저장 시 별칭 중복 제거와 시도 비교가 같은 함수를 쓴다.
3. **시도 API** `POST /api/submissions/{submissionId}/guesses` (JSON `{ "guess": "유재석" }`, 새 DTO `GuessRequest(@NotBlank @Size(max=100) String guess)` + trim): 새 `submission/service/GuessService` + `submission/controller/GuessController` + `submission/dao/GuessMapper`.
   - 순서: `submissionService.requireAccessible`(404/403/423) → 틀이 G 가 아니면 400 `GUESS_NOT_SUPPORTED` → 본인 제출물이면 409 `CANNOT_GUESS_OWN_SUBMISSION` → **`SELECT … FROM submissions WHERE id=? FOR UPDATE`** 로 제출 행 잠금(동시 시도로 횟수 초과 방지; `@Transactional`) → 이미 맞혔으면 409 `ALREADY_SOLVED` → 시도 횟수 ≥ `guessMaxAttempts` 면 409 `GUESS_LIMIT_EXCEEDED` → 비교 → `submission_guesses` 에 기록(`guess_text` 는 trim 한 원문 100자 이내, `is_correct`) → 응답.
   - 응답 200: 틀림 `{ "correct": false, "attemptsLeft": N }`, 맞힘 `{ "correct": true, "attemptsLeft": N, "answer": "<대표 정답>" }` (`attemptsLeft` = 최대 − 지금까지 시도 수(이번 포함)). DTO `GuessResponse` 는 `@JsonInclude(NON_NULL)`.
   - 맞히면 더 시도할 수 없다(다음 시도는 `ALREADY_SOLVED`). 시도는 제출이 아니므로 도장/제출 수에 영향 없음.
4. **응답 필드**: `SubmissionResponse` 에 `String answer`, `MyGuess myGuess`(record `MyGuess(boolean solved, int attemptsLeft)` — `submission/dto`)를 추가(B5 의 `number/choice/guess` 뒤). 계산(`SubmissionService.response`):
   - `answer` 공개 조건: **작성자 본인**이거나, 내가 **맞혔거나**, **지금 시각 > `deadline_at`**. 아니면 null(생략). 공개 시 값은 `answer_text`(대표 정답), **`answer_aliases` 는 어떤 응답에도 넣지 않는다.**
   - `myGuess`: G 틀이고 본인 제출물이 아닐 때만(그 외 null/생략). `solved` = 내가 맞힌 기록 존재, `attemptsLeft` = 최대 − 내 시도 수(0 미만 없음).
   - 피드는 **배치 조회**: 페이지의 G 제출 id 목록으로 `GuessMapper.findStatusBySubmissionIds(ids, viewerId)` → `(submissionId, attempts, solved)` 1회(IN 쿼리, 항목마다 쿼리 금지). 단건(detail/submit)은 같은 쿼리를 id 1개로.
   - 이를 위해 `SubmissionMapper.FIELDS` 에 `s.answer_text AS answer_text`, `m.deadline_at AS deadline_at` 추가(+`SubmissionRow` 필드 `answerText`, `deadlineAt`). **`answer_aliases` 는 SELECT 하지 않는다**(실수로 노출되는 경로 자체를 없앤다). 제출 직후 응답에서 본인은 `answer` 를 본다.
5. **발행**: `PlaceholderMissionGenerator` 에 `VOICE_MIMIC`("소리 흉내를 맞춰보세요" 계열 3개 이상), `VOICE_IMPRESSION`("성대모사를 맞춰보세요" 계열 3개 이상) 추가, `params` 는 빈 맵. 문장에 흉내 낼 대상을 넣지 않는다(제출자가 정함 — MISSIONS_API §1-2). 기본 설정(`enabled-templates`)은 그대로 두어 앱(A4) 준비 후 켠다.
6. **회귀 방지**: 기존 `VOICE_LINE`·다른 틀의 필드 규칙·중복 필드 거부 동작은 그대로(`alias` 만 예외). G 틀이 아닌 제출 응답에는 `answer`/`myGuess` 가 나오지 않는다.

## Files / Functions to Change

| 파일 | 변경 |
|---|---|
| `global/config/MissionProperties.java`, `application.properties` | `guessMaxAttempts`(3), `maxAliases`(5) (`hawe.mission.guess-max-attempts`, `hawe.mission.max-aliases`) |
| `mission/service/MissionErrorCode.java` | `GUESS_NOT_SUPPORTED`, `CANNOT_GUESS_OWN_SUBMISSION`, `ALREADY_SOLVED`, `GUESS_LIMIT_EXCEEDED` (문서 §10 의 이름·상태·메시지) |
| `submission/domain/AnswerNormalizer.java` (신규) | Key Point 2 |
| `submission/service/SubmissionService.java` | G 틀 제출 분기(alias 반복 허용), 저장 필드(`answer_text`, `answer_aliases`), `response` 에 `answer`/`myGuess` |
| `submission/dao/SubmissionInsertRow.java`, `SubmissionMapper.java`, `SubmissionRow.java` | `answerText`, `answerAliases(JSON)` insert, select 확장(`answer_text`, `deadline_at`) |
| `submission/dto/SubmissionResponse.java`, `MyGuess.java`(신규) | 필드 추가 |
| `submission/dao/GuessMapper.java`, `GuessStatusRow.java`, `submission/service/GuessService.java`, `submission/controller/GuessController.java`, `submission/dto/GuessRequest.java`, `GuessResponse.java` (신규) | Key Point 3 |
| `mission/service/PlaceholderMissionGenerator.java` | 두 틀 변형 |
| 테스트·문서 | 아래 / `MISSIONS_API.md` §3·§4-1·§10 "구현됨"+ NON_NULL 생략 설명, `CONTRACT_DECISIONS.md` §6 상태, `API_DESIGN.md`, Postman `HAWE_SUBMISSION` 에 G 제출·시도 요청 |

## Implementation Steps

1. 정규화 유틸 + 단위 테스트. 2. 제출 분기·저장·응답 필드(본인 응답에서 확인). 3. 시도 서비스·API(행 잠금). 4. 피드 배치 `myGuess`. 5. 임시 생성기. 6. 테스트 → `.\mvnw.cmd clean test -q`. 7. 문서·Postman. (스키마 변경 없음 — DB 재생성 불필요.)

## Constraints

- 스키마 변경 없음. **별칭은 어떤 응답/로그에도 노출 금지**(SELECT 자체에서 제외). 오타 허용·퍼지 매칭 금지(완전 일치). 시도 횟수/별칭 개수는 설정값.
- 정답 맞힘 알림 금지, 제출자에게 맞힌 사람 목록 제공 금지(MISSIONS_API §4-1).
- 기본 `enabled-templates` 불변. 커밋/푸시·지침 파일 수정 금지.

## Edge Cases

- 제출: `answer` 누락/공백/51자 → 400; `alias` 6개 → 400; 별칭이 정답과 같음/서로 같음 → 저장 시 제거(오류 아님); `alias` 에 빈 문자열 → 400; `answer` 에 이모지·공백 포함 가능; 파일이 이미지/비음성 → 415; 30초 초과 → 400.
- 시도: 빈 `guess` → 400 `VALIDATION_ERROR`; 공백·대소문자·NFC/NFD 차이는 정답 처리(`"유 재석"` == `"유재석"`, `"Mimi"` == `"mimi"`); 부분 일치(`"유재"`)는 오답; 별칭 일치는 정답이며 응답 `answer` 는 **대표 정답**(별칭 아님); 3회 오답 후 4번째 → 409 `GUESS_LIMIT_EXCEEDED`; 3번째에 맞히면 허용; 맞힌 뒤 시도 → 409 `ALREADY_SOLVED`; 본인 제출물 → 409 `CANNOT_GUESS_OWN_SUBMISSION`; G 가 아닌 제출물 → 400 `GUESS_NOT_SUPPORTED`; 내가 그 미션을 미제출 → 423; 다른 그룹 → 403; 없는 id → 404; 동시 2요청이 마지막 1회를 놓고 경합 → 하나만 성공(행 잠금).
- 공개: 마감 시각 전 비작성자·미해결 → `answer` 생략; 마감 후 → 공개; 작성자 본인은 항상 공개; 맞힌 사람은 마감 전에도 공개.
- 피드에 G 제출이 여러 개일 때 각 항목의 `myGuess` 가 독립적으로 정확하고 쿼리 수가 항목 수와 무관.
- 탈퇴(8단계)·그룹 삭제가 `submission_guesses` 를 지우는 기존 경로가 계속 동작.

## Regression Risks

- `SubmissionService.submit` 의 중복 필드 검사 수정이 다른 틀의 "중복 필드 거부"를 약화하지 않게(**`alias` 에만 예외**) — 기존 테스트로 보호.
- `SubmissionMapper.FIELDS` 확장 → 모든 제출 응답 쿼리에 영향. `answer_aliases` 를 SELECT 에 넣지 않는 것이 핵심 안전장치 — 리뷰 포인트.
- `SubmissionInsertRow` 생성자/`SubmissionResponse` 레코드 변경 → 호출부·테스트 수정(B5 와 같은 종류).
- `MissionProperties` 컴포넌트 추가 → 직접 생성하는 테스트 수정.
- 잠금 쿼리(`FOR UPDATE`)가 트랜잭션 없이 호출되면 의미 없음 → `GuessService` 메서드는 `@Transactional`.

## Tests Required

- `AnswerNormalizerTests`: 공백/대소문자/NFC, 한글 자모 분리형 입력.
- `SubmissionServiceTests`(Mockito): G 제출 성공/검증 실패 매트릭스, 별칭 중복 제거, 저장 JSON, 응답 공개 조건(본인/맞힘/마감 전/마감 후), 일반 틀 응답에 `answer`/`myGuess` 없음.
- `GuessServiceTests`(Mockito + 실 DB 통합 일부): 순서별 에러(404→403→423→400→409…), 정답/오답 응답, 횟수 소진, 별칭 정답에서 대표 정답 반환, 동시성(스레드 2개 경합 시나리오는 통합 테스트로).
- `GuessMapperTests`(실 DB): 기록/집계/배치 상태 조회.
- MockMvc 통합: 세 명(작성자 A, B, C) — A 제출 → B 가 오답 2번 후 정답 → 피드에서 B 는 `answer` 보임·C 는 숨김·A 는 항상 보임, 마감 시각을 과거로 UPDATE 하면 C 도 보임, **응답 JSON 어디에도 `alias`/`answerAliases` 문자열이 없음**(테스트에서 별칭 값으로 grep), 미제출 사용자 423.
- 기존 `VOICE_LINE` 제출 회귀.

## Validation Steps

`.\mvnw.cmd clean test -q` 통과 기록, `rg "answer_aliases|answerAliases" src/main` 로 SELECT/응답에 별칭이 노출되는 경로가 없음을 확인(저장 insert 와 비교용 조회 쿼리만 허용), Postman 파싱·한글, `git diff --check`, 서버 스모크 가능 시 수행. 보고서에 "별칭 비노출 확인 방법" 명시.

## Potential Merge Conflicts

`SubmissionService`/`SubmissionMapper`/`SubmissionRow`/`SubmissionResponse`(B5 이후, B1 이후), `MissionProperties`/`MissionErrorCode`/`PlaceholderMissionGenerator`(B5·B8), `docs/BACKEND/MISSIONS_API.md`.
