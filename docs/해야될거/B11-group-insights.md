# B11. 그룹 활동 인사이트 대시보드 (백엔드)

> 범위: 백엔드(`src/`)와 문서. 앱 화면은 A9. 알림·배포 무관. **전제: 7·8단계 + B0 커밋 후, B1(그룹 내 프로필) 커밋 후 권장**(표시 닉네임 규칙을 재사용). 사용자 요구: 그룹이 쌓은 기록에서 "우리 그룹의 이야기"를 만든다 — **가장 빨리 낸 사람, 가장 자주 쓰인 단어(글 미션), 가장 반응이 많았던 글, 월말 하이라이트 자동 생성, 그룹 활동 지수, 텍스트 키워드 추출(형태소 분석)**. 보여 줄 기술: 텍스트 처리, 집계, 캐시, 비동기 생성. 7단계(반응·댓글)가 쌓일수록 풍부해진다.

## Goal

1. `GET /api/groups/{groupId}/insights?month=YYYY-MM` — 그룹 멤버가 월별 인사이트를 조회한다(기본 `month` = 이번 달).
2. 인사이트 구성: **활동 지수(0~100)**, **가장 빨리 낸 사람**, **많이 쓰인 단어 Top N**, **가장 반응이 많았던 글**(메타데이터), **하이라이트 문장 목록**.
3. 계산은 **비동기로 생성 → DB 캐시**: 지난달은 매월 1일 새벽에 모든 그룹에 대해 자동 생성(월말 하이라이트), 이번 달은 요청 시 캐시가 없거나 만료면 비동기 갱신.

## Findings

- 원천 데이터(이미 있음): 
  - `missions(id, group_id, mission_date, template, content, published_at, deadline_at, slot_no)`.
  - `submissions(mission_id, user_id, media_url, caption, submitted_at, is_late, answer_data JSON, number_value, choice_value)` — 글 미션의 본문은 `answer_data.text`(`TEXT_*`; B5 이후 `TEXT_ONE_WORD`, `TEXT_REVIEW` 포함 — 글 틀의 `text`, 선택 `caption`).
  - `reactions(submission_id, user_id, sticker_type)`, `comments(submission_id, user_id, …)` (7단계).
  - 멤버십 `group_members(joined_at, role)`, 닉네임 표시는 `SubmissionMapper.FIELDS/JOINS` 의 `users`/`characters`(B1 이후 그룹 내 프로필 우선 CASE).
  - 달력의 "열릴 때 그룹에 있던 사람" 규칙 `CalendarMapper.JOINED_BY_PUBLISH` — 참여율 분모에 재사용.
- **닫힌 미션** 개념: `MissionMapper.CURRENT`(슬롯별 최신 미션 조건)의 반대 = 같은 슬롯에 더 나중 미션이 발행된 미션. 열려 있는 오늘 미션의 내용이 인사이트로 새지 않게 **닫힌 미션만 집계**한다(피드 잠금 규칙의 취지: 제출 전에는 남의 글을 볼 수 없음).
- 인프라: `@EnableScheduling` 있음, `@Async`/`TaskExecutor` 없음(B10 이 `@EnableAsync` 와 실행기 설정을 도입 — 같은 설정 클래스를 공유하거나, B10 이 먼저 나오지 않았다면 이 계획에서 `AsyncConfig` 를 만든다. 둘 다 `AsyncConfig` 를 만들면 충돌하므로 **먼저 병합되는 쪽이 만들고 다른 쪽이 실행기 빈만 추가**).
- 한국어 형태소 분석: JVM 에서 쓸 수 있는 선택지 — **Apache Lucene Nori**(`org.apache.lucene:lucene-analysis-nori`, 사전 내장, 순수 Java, Lucene 10 은 Java 21+), KOMORAN, OpenKoreanText. 기본 선택은 **Lucene Nori**(유지보수 활발, 품사 태그 접근 가능). 정확한 Maven 좌표/버전과 Java 25 호환은 구현 시 Maven Central 에서 확인하고 보고서에 기록한다(막히면 KOMORAN 으로 대체, 최후에는 조사 제거 휴리스틱 + 불용어 폴백).
- 캐시: 외부 캐시 서버·Caffeine 의존성은 없다. 이 규모에서는 **DB 캐시 테이블**이 단순하고 재시작에도 유지된다.
- 스키마 정책: V1 직접 수정 + 로컬 DB 재생성.

## Root Cause / Key Implementation Points

1. **캐시 테이블(V1 직접 수정)** `group_insights`: `id bigint PK AI, group_id bigint NOT NULL, month char(7) NOT NULL ('YYYY-MM'), status varchar(10) NOT NULL ('GENERATING','READY','FAILED'), payload longtext JSON NULL, generated_at datetime NULL, expires_at datetime NULL, attempts tinyint NOT NULL DEFAULT 0, UNIQUE (group_id, month), FK group_id → groups(id)`.
2. **조회 흐름** `GroupInsightService.get(userId, groupId, month)`: 그룹 404 → 멤버 403 → `month` 형식·범위 검증(과거 12개월 이내·미래 달 400) → 캐시 행 조회:
   - `READY` 이고 (지난 달이거나 `expires_at > now`) → 200 payload.
   - 없음/만료/`FAILED`(재시도 한도 이내) → 행을 `GENERATING` 으로 upsert 후 `@Async("insightExecutor")` 로 생성 시작, 응답은 **202** `{ "status": "GENERATING", "retryAfterSeconds": 3 }`(이번 달에 이전 `READY` payload 가 있으면 그것을 `stale: true` 로 200 으로 즉시 반환하고 뒤에서 갱신).
   - `GENERATING` → 202.
   - 지난 달은 만료 없음(확정), 이번 달 TTL = 설정 `hawe.insight.current-month-ttl=30m`.
   - 해당 월에 닫힌 미션이 0개면 `READY` + `empty: true`(앱이 "아직 기록이 없어요"를 표시).
3. **월말 자동 생성**: `@Scheduled(cron = "0 10 0 1 * *", zone = "Asia/Seoul")` — 직전 월에 미션이 있었던 모든 그룹의 인사이트를 순차 생성(그룹 단위 예외 격리·로그, 이미 `READY` 면 건너뜀, 한 번에 실행기 큐 폭주 방지를 위해 그룹 단위로 제출). 놓친 경우를 위해 기동 시 보충 실행 옵션은 만들지 않는다(요청 시 비동기 생성으로 충분).
4. **지표 정의** (`InsightCalculator`, 입력 = 미션/제출 행 목록 → 출력 = `InsightPayload`, **순수 함수로 분리해 단위 테스트**):
   - **대상 미션**: 해당 월(`mission_date`)의 **닫힌 미션**.
   - **활동 지수(0~100)** `= round(100 × (0.6·참여율 + 0.2·정시율 + 0.2·교류도))`. 참여율 = 제출 수 / 대상 인원합(미션마다 `JOINED_BY_PUBLISH` 규칙의 대상 인원). 정시율 = `is_late = 0` 제출 / 제출 수. 교류도 = `min(1, (반응 수 + 댓글 수) / (2 × 제출 수))`. 제출 0 → 지수 0 이 아니라 `empty`. 직전 달 지수가 있으면 `deltaFromPreviousMonth` 도 제공.
   - **가장 빨리 낸 사람**: 미션별 최초 제출자의 횟수 집계 → 최다 횟수(동률이면 평균 응답 시간(`submitted_at − published_at`)이 짧은 사람, 그래도 같으면 `userId` 오름차순), 응답: `{ userId, nickname, characterKey, profileImageUrl(표시 프로필), firstCount, averageResponseSeconds, fastestSingleSeconds }`.
   - **많이 쓰인 단어 Top 10**: 해당 월 닫힌 미션 중 **글 매체(TEXT)** 제출의 본문(`answer_data.text`, 선택 `caption`)을 형태소 분석 → **일반명사·고유명사**(Nori `NNG`/`NNP`)만, 길이 ≥2, 불용어(설정 파일 `insight-stopwords.txt`)·숫자·URL 제외, 한 제출에서의 중복은 1회로 셈(도배 방지). 응답: `[ { word, count, memberCount } ]` (`memberCount` = 그 단어를 쓴 서로 다른 멤버 수). 최소 노출 기준: `count ≥ 2` (표본이 작을 때 빈 배열 허용).
   - **가장 반응이 많았던 글**: 점수 = `반응 수 + 2 × 댓글 수`(같은 제출의 합), 최고 1건(동률이면 더 이른 제출). 응답은 **본문 없이 메타데이터만**: `{ submissionId, missionId, missionContent, template, authorUserId, authorNickname, reactionCount, commentCount, topSticker }` — 본문을 인사이트에 싣지 않는다(잠금 규칙 우회 방지). 앱은 상세로 이동하고, 요청자가 그 미션에 제출하지 않았으면 기존 `FEED_LOCKED` 423 이 그대로 적용된다. 그래서 응답에 요청자 기준 `viewable` 를 **포함하지 않는다**(payload 는 그룹 공통 캐시) — 앱이 상세 호출 결과로 처리한다.
   - **하이라이트 문장**: 규칙 기반 템플릿 문장 목록(예: "9월의 빠른 손은 {닉네임}님! 가장 먼저 낸 횟수 {n}번", "9월에 가장 자주 나온 단어는 '{word}'({count}번)", "가장 뜨거웠던 글은 ‘{미션 문장}’에 달린 {n}개의 반응", "우리 그룹 활동 지수 {score}점"). 조건 미충족 문장은 건너뜀. 문장 템플릿은 코드 상수. **LLM 서술 생성은 이번 범위가 아니다**(필요하면 B8 의 클라이언트로 후속 확장 — 결정 대기, 기본 꺼짐).
5. **표시 프로필**: 닉네임/캐릭터/사진은 payload 에 **스냅샷으로 넣지 않고 `userId` 만 저장**하고, 응답 조립 시점에 현재 표시 프로필(B1 CASE 쿼리)로 채운다(닉네임 변경·탈퇴 반영). 탈퇴한 사용자는 "탈퇴한 멤버"로 표시. 하이라이트 문장은 닉네임을 포함하므로 **문장은 저장하지 않고 응답 시점에 생성**(payload 에는 구조화 지표만 저장).
6. **무효화**: 새 제출/반응/댓글이 생길 때마다 지우지 않는다(TTL 로 충분). 단 ① 8단계 **탈퇴** 시 그 사용자가 속했던 그룹들의 `group_insights` 행을 삭제(개인 단어/수치 제거), ② 그룹 삭제 cascade 에서 삭제, ③ 제출 삭제 기능은 없음.
7. **API 계약**:
   ```json
   // 200 READY
   { "groupId": 1, "month": "2026-09", "status": "READY", "stale": false, "empty": false,
     "generatedAt": "2026-10-01T00:12:03+09:00",
     "activityIndex": { "score": 82, "deltaFromPreviousMonth": 5, "participationRate": 0.78, "onTimeRate": 0.91, "interactionRate": 0.64 },
     "fastest": { "userId": 3, "nickname": "서연", "characterKey": "mulbangul", "profileImageUrl": null, "firstCount": 9, "averageResponseSeconds": 1260, "fastestSingleSeconds": 95 },
     "topWords": [ { "word": "퇴근", "count": 12, "memberCount": 4 } ],
     "mostReacted": { "submissionId": 105, "missionId": 40, "missionContent": "…", "template": "TEXT_ACROSTIC", "authorUserId": 2, "authorNickname": "잎사귀", "reactionCount": 7, "commentCount": 3, "topSticker": "laugh" },
     "highlights": [ "9월의 빠른 손은 서연님! …", "…" ] }
   // 202 생성 중
   { "groupId": 1, "month": "2026-09", "status": "GENERATING", "retryAfterSeconds": 3 }
   ```
   오류: 404 `GROUP_NOT_FOUND`, 403 `NOT_GROUP_MEMBER`, 400 `VALIDATION_ERROR`(month 형식/범위). 시각은 `+09:00` 오프셋.

## Files / Functions to Change

| 파일 | 변경 |
|---|---|
| `V1__initial_schema.sql` | `group_insights` 테이블 |
| `insight/controller/GroupInsightController.java`, `insight/service/GroupInsightService.java`, `InsightGenerator.java`(비동기 생성), `InsightCalculator.java`(순수), `KeywordExtractor.java`(Nori 래퍼, 인터페이스로 분리해 테스트 대체 가능), `InsightScheduler.java`, `insight/dao/InsightMapper.java`(집계 쿼리·캐시 CRUD), `insight/dao/*Row.java`, `insight/dto/*`, `insight/domain/InsightPayload.java` (신규 feature 패키지 `insight`, 5개 서브패키지 규칙) | 본체 |
| `pom.xml` | Nori 의존성(좌표·버전은 구현 시 확인), 테스트 변경 없음 |
| `src/main/resources/insight/stopwords.txt` (신규) | 불용어 목록 |
| `global/config/AsyncConfig.java` | `insightExecutor`(core 1, max 2, queue 50) — B10 과 공유 |
| `global/config/InsightProperties.java`, `application.properties` | `hawe.insight.enabled=true`, `current-month-ttl=30m`, `top-words=10`, `min-word-count=2` |
| `group/dao/GroupMapper.java`, `group/service/GroupService.java` | `deleteGroupCascade` 에 `group_insights` 삭제, `withdraw` 에서 사용자의 그룹들 캐시 삭제(`WithdrawMapper`) |
| 테스트·문서 | 아래 / `API_DESIGN.md`(새 절), `BACKEND_DB.md`, `CONTRACT_DECISIONS.md`(신규 절), 신규 `docs/BACKEND/GROUP_INSIGHTS.md`(지표 정의·공식·형태소 분석 방식·캐시/비동기·한계), `PROJECT_OVERVIEW.md`(기능 소개), Postman |

## Implementation Steps

1. V1 수정 → **로컬 DB 백업·재생성**(보고서에 경로·행 수). 2. `InsightCalculator`(순수) + 단위 테스트(손계산 기대값). 3. `KeywordExtractor`(Nori) + 테스트. 4. 집계 쿼리(`InsightMapper`) + 매퍼 테스트(실 DB). 5. 캐시/비동기 서비스·202 흐름. 6. 스케줄러. 7. 컨트롤러·보안 테스트. 8. 삭제 연쇄. 9. `.\mvnw.cmd clean test -q`. 10. 문서.

## Constraints

- **본문/개인 콘텐츠를 외부로 보내지 않는다**(형태소 분석은 서버 내부 라이브러리, 외부 API 금지). 인사이트에 제출 본문·댓글 본문·사진을 싣지 않는다(단어 집계와 메타데이터만).
- **닫힌 미션만** 집계(진행 중 미션 내용 유출 금지). 그룹 멤버만 조회. 요청 시 계산을 동기로 오래 붙잡지 않는다(항상 비동기 생성 + 202).
- 새 마이그레이션 금지(V1 직접 수정). 외부 캐시(Redis 등) 금지. LLM 서술 생성 금지(후속). 커밋/푸시·지침 파일 수정 금지.
- 하이라이트 문장에서 낮은 활동을 비난하는 표현 금지(긍정/중립 문구만, "가장 늦게 낸 사람" 같은 지표를 만들지 않는다).

## Edge Cases

- 닫힌 미션 0개/제출 0건/글 미션 0개: `empty: true` 또는 해당 섹션만 `null`/빈 배열(앱이 숨김). 0 나누기 방지.
- 멤버 1명 그룹, 하루 여러 개 미션(B7), 월 중 가입/탈퇴: 참여율 분모는 `JOINED_BY_PUBLISH` 규칙 + 제출자 보정(달력과 동일).
- 같은 단어의 변형(조사 포함 "퇴근길에서"): Nori 가 `퇴근길`(복합명사 분해 모드 `NONE`/`MIXED` 선택은 구현 시 평가 — 기본 `MIXED` 로 원형 보존 + 분해 둘 다 셈하지 않도록 한 가지 모드만 사용) 으로 정규화되는지 샘플 테스트(삼행시·한 줄 후기 문장)로 검증.
- 이모지·URL·영문 대소문자: 영문은 소문자 정규화 후 집계, URL/숫자/이모지 제외.
- 탈퇴한 멤버가 최다 응답자/최다 반응 작성자였던 달: 탈퇴 시 캐시를 지우므로 재생성 때는 해당 사용자의 제출물이 이미 삭제돼 지표에서 빠진다(문서화).
- 동시 요청이 같은 (group, month) 를 동시에 생성 시작: `UNIQUE` + 조건부 갱신(`UPDATE … WHERE status <> 'GENERATING'`)으로 한 번만 시작.
- 생성 도중 서버 재시작: `GENERATING` 이 영구히 남는 문제 → `generated_at` 이 아니라 `updated` 기준 5분 초과 `GENERATING` 은 요청 시 `FAILED` 로 보고 재시작(타임스탬프 컬럼 `started_at` 을 두거나 `attempts`+`expires_at` 활용 — 구현 시 컬럼 하나 추가 허용).
- `month` 가 입력되지 않으면 이번 달(Asia/Seoul 기준), 월 경계 시각(1일 00:00 직후)에 이번 달 미션이 아직 없을 때 `empty`.
- 대량 그룹에서 월말 일괄 생성이 DB 를 오래 붙잡음 → 그룹 단위 짧은 쿼리, 순차 실행.

## Regression Risks

- 삭제 연쇄에 새 테이블 추가 누락 → FK 위반 500(그룹 삭제/탈퇴 통합 테스트로 방지).
- `@EnableAsync`/실행기 설정 공유(B10)로 인한 빈 이름 충돌.
- Nori 의존성 추가가 빌드 크기/Java 25 호환/네이티브 이슈를 일으킬 수 있음 → 빌드·테스트로 확인, 문제 시 대체 구현(`KeywordExtractor` 인터페이스 덕분에 교체 용이).
- 집계 쿼리 성능(제출·반응·댓글 조인) — 월 단위 + 인덱스(`missions(group_id, mission_date)`, `submissions(mission_id)`, `reactions(submission_id)`) 확인, 필요하면 V1 에 인덱스 추가.
- 스케줄러가 매월 1일 00:10 에 실행되므로 테스트에서는 서비스 메서드를 직접 호출(크론 검증 금지).

## Tests Required

- `InsightCalculatorTests`: 손계산 시나리오(3명·5미션)로 활동 지수·가장 빨리 낸 사람(동률 처리)·최다 반응 글·단어 집계 값 일치, 빈 입력, 극단값(참여율 100%/0%).
- `KeywordExtractorTests`: 조사/어미 제거, 길이 필터, 불용어, 도배(한 제출 중복 1회), 영문 소문자, 이모지/URL 제외 — 한국어 샘플 문장 10개.
- `InsightMapperTests`(실 DB): 집계 쿼리가 닫힌 미션만, 월 경계, 다른 그룹 제외, 반응/댓글 조인.
- `GroupInsightServiceTests`(Mockito): 캐시 `READY`/만료/`GENERATING`/`FAILED` 상태별 200/202, stale 반환, 월 검증, 접근 규칙.
- MockMvc 통합: 그룹 2명·미션·제출·반응·댓글을 만들고(마감시켜 닫힘 상태로 조작) 비동기 완료 대기(폴링 헬퍼) 후 `GET …/insights` 200 내용 확인, 첫 호출 202, 비멤버 403, 없는 그룹 404, `month` 오류 400, 응답에 본문 텍스트가 없음.
- 스케줄러 서비스 메서드 직접 호출 테스트(월말 일괄 생성, 그룹별 예외 격리).
- 삭제 연쇄 테스트(그룹 삭제·탈퇴 후 `group_insights` 행 소멸).

## Validation Steps

`.\mvnw.cmd clean test -q` 통과 기록, Nori 좌표/버전·Java 25 호환 확인 결과 보고, `rg "answer_data" src/main/java/**/insight` 로 본문이 응답 DTO 에 실리지 않음 확인, Postman 파싱·한글, `git diff --check`. 서버 스모크(제출 몇 건 후 `/insights` 두 번 호출: 202 → 200)는 가능하면 수행, 아니면 "미검증".

## Potential Merge Conflicts

`AsyncConfig`(B10), `GroupMapper`/`GroupService`/`WithdrawMapper`(삭제 연쇄 — B4·B10 과 순차), `SubmissionMapper` 표시 프로필 CASE(B1), `V1`, `pom.xml`(의존성), `application.properties`, 문서 3종(`API_DESIGN`, `PROJECT_OVERVIEW`, `CONTRACT_DECISIONS`).
