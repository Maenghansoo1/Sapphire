# B9. AI 주제 vs 랜덤 주제 — 참여율 비교 실험 (백엔드)

> 범위: 백엔드(`src/`)와 문서. 앱 변경 없음(사용자에게 어느 쪽 주제인지 보이지 않는 **블라인드 실험**). 알림·배포 무관. **전제: B8(그룹 맞춤 LLM 주제 생성)과 B7(하루 여러 개) 커밋 후** — 이 계획은 B8 이 만든 `FallbackMissionGenerator`/`GeneratedMission.source` 위에서 동작한다. 사용자 요구: "AI 주제 vs 랜덤 주제 참여율 비교처럼 효과를 숫자로 보여줄 수 있어야 한다."

## Goal

1. 같은 시스템 안에서 일부 (그룹, 날짜) 미션은 **AI(LLM) 주제**, 나머지는 **랜덤(= 고정 문장 풀 `PlaceholderMissionGenerator`) 주제**로 발행하는 **무작위 배정 실험**을 켜고 끌 수 있다.
2. 발행된 미션마다 어떤 방식으로 만들어졌는지(`generation_source`)와 실험 군(`experiment_arm`)을 기록한다.
3. 두 군의 **참여율·정시 제출률·반응/댓글 참여도·첫 제출까지 걸린 시간**을 비교하는 리포트(JSON/Markdown)를 낸다. 신뢰구간과 검정 결과까지 포함해 "AI 주제가 참여율을 N%p 높였다(95% CI …, p=…)" 같은 문장으로 보고할 수 있게 한다.

## Findings

- 생성기 구조(B8 이후): `MissionGenerator`(Primary = `FallbackMissionGenerator`) → `AiMissionGenerator` 또는 `PlaceholderMissionGenerator`. **틀(template)·대상(target)·시각은 서버가 정하고 AI 는 문장·카테고리·params 만 만든다**(MISSIONS_API §6-1·§8) → 두 군에서 틀 분포가 같으므로 **주제 문장의 효과만 비교**되는 깨끗한 실험이 가능하다(틀 선택은 배정과 독립이어야 한다는 점이 핵심 제약).
- 발행: `MissionPublisher.publishIfDue(groupId, now)` 가 슬롯별로 틀을 고르고 `generator.generate(template, seed, context, avoidContents, withTarget)` 를 호출한 뒤 `MissionInsertRow` 를 `missions.insert` 로 저장. `missions` 컬럼: `content, category, mission_date, template, params, slot_no, published_at, deadline_at, group_id` (V1). 생성 출처·실험 군 컬럼은 **없다**.
- 참여 지표 재료는 이미 있다: `submissions(mission_id, user_id, submitted_at, is_late)`; 미션이 열릴 때의 대상 인원은 `CalendarMapper` 가 이미 정의했다(`member_count` = **미션이 열릴 때 그룹에 있던 현재 멤버** + 그 미션에 냈지만 지금은 멤버가 아닌 사람, `JOINED_BY_PUBLISH = gm.joined_at <= m.published_at + INTERVAL 1 MINUTE`). 반응/댓글은 `reactions`, `comments`(7단계). 제출 시각 지표는 `submitted_at - published_at`.
- 권한/관리자: 현재 보안은 JWT 사용자 단위뿐이며 **관리자 역할이 없다**(`SecurityConfig`: `/api/auth/**`, `GET /api/characters`, `/files/**`, `/actuator/**`, swagger 만 permitAll). 리포트 접근 방식은 결정이 필요(Constraints 참고, 기본값: 토큰 헤더 보호 내부 엔드포인트).
- 규모의 한계: 현재 사용자 수가 작아 통계 검정력이 낮다 → 리포트가 **표본 크기와 한계를 함께 출력**해야 한다(과장 방지).
- 스키마 정책: V1 직접 수정 + 로컬 DB 재생성(프로젝트 방식).

## Root Cause / Key Implementation Points

1. **무작위 배정 단위 = (그룹, 날짜)**: 같은 그룹이 날마다 AI/랜덤을 번갈아 받으므로 그룹 간 차이(친밀도 등)를 상쇄하는 **그룹 내 대응(paired) 비교**가 가능하다. 배정은 결정적 해시: `arm = hash(salt, groupId, missionDate) < aiRatio ? AI : RANDOM`. 같은 날 여러 슬롯(B7)은 **같은 군**으로 묶는다(날짜 단위 배정). 틀 선택(`MissionTemplateSelector`)은 배정과 무관하게 기존대로 — 배정에 영향을 받으면 안 된다.
2. **실험 비활성/AI 불가 시 배정하지 않는다**: `hawe.experiment.topic.enabled=false`(기본)이거나 AI 가 사용 불가(키 없음·비활성)면 `experiment_arm = NULL`, 기존 동작(Fallback 이 Placeholder). 실험 중 AI 호출이 실패해 Placeholder 로 폴백한 미션은 **군은 AI 로 기록(intention-to-treat)**하고 `generation_source = AI_FALLBACK` 으로 표시한다. 리포트는 (a) ITT(배정대로 분석, 기본) (b) per-protocol(실제 `AI` 출처만) 두 값을 모두 낸다.
3. **스키마(V1 직접 수정)**: `missions` 에 `generation_source varchar(20) DEFAULT NULL`(`AI` / `PLACEHOLDER` / `AI_FALLBACK`), `experiment_arm varchar(10) DEFAULT NULL`(`AI` / `RANDOM`), 인덱스 `idx_missions_experiment (experiment_arm, mission_date)`. 앱 응답에는 노출하지 않는다(블라인드). `GeneratedMission` 의 `source` 는 B8 이 이미 도입했다(없으면 이 계획에서 추가).
4. **발행 로직 변경(최소)**: `MissionPublisher` 가 슬롯 루프 앞에서 날짜 단위 군을 한 번 계산(`ExperimentAssigner.assign(groupId, date)` → `Optional<Arm>`), 군이 `RANDOM` 이면 `PlaceholderMissionGenerator` 를 직접 호출(Fallback 우회), `AI` 이면 AI 우선 경로(Fallback 의 기존 동작)를 쓰되 결과의 출처를 기록, 군이 없으면 기존 경로. `MissionInsertRow` 에 `generationSource`, `experimentArm` 추가 후 `MissionMapper.insert` 에 컬럼 추가. `MissionGenerator` 인터페이스를 더 바꾸지 않으려면 `FallbackMissionGenerator` 에 `generate(…, ForcedArm arm)` 오버로드 또는 `GenerationContext.forcedArm` 필드를 쓴다(B8 의 `GenerationContext` 에 `Optional<Arm> forcedArm` 추가 권장).
5. **지표 정의** (`ExperimentReportService`, 순수 계산부는 정적 메서드로 분리해 단위 테스트): 미션 단위 행 `(mission_id, group_id, mission_date, arm, eligible, submitted, onTime, reactionsOnSubmissions, commentsOnSubmissions, firstSubmitSeconds)` 를 한 쿼리로 모은다.
   - **참여율** = `Σ submitted / Σ eligible` (군별). `eligible` 은 위 `CalendarMapper` 정의와 동일하게 계산(쿼리 재사용/복제 시 같은 SQL 조건 사용).
   - **정시 제출률** = `Σ onTime / Σ submitted`, **미션당 반응+댓글 수**(제출물 합 기준), **첫 제출까지 중앙값(분)**.
   - **추론**: (a) 미션 단위 합산 비율의 **Wilson 95% 신뢰구간**과 두 비율 차이의 **z 검정(p-value)** — 단순하지만 같은 그룹 미션이 상관되므로 과신 금지 경고 포함. (b) **그룹 내 대응 비교(권장 주 지표)**: 각 그룹에서 `mean(participation | AI일) − mean(participation | RANDOM일)` 을 구하고 두 군을 모두 경험한 그룹에 한해 평균 차이·95% CI(t 분포, 그룹 수 < 30 이면 부트스트랩 5,000회 보조)와 부호 검정. 표본이 너무 작으면(그룹 수 < 5 또는 군별 미션 < 30) 결과 상단에 `underpowered: true` 와 경고 문구.
6. **리포트 출력**: `GET /internal/experiments/topic?from=YYYY-MM-DD&to=YYYY-MM-DD&format=json|md`. 접근 제어: 요청 헤더 `X-Admin-Token` 이 설정 `hawe.admin.token`(환경변수 `HAWE_ADMIN_TOKEN`, 비어 있으면 엔드포인트 비활성=404)과 일치해야 함. `SecurityConfig` 에서 `/internal/**` 는 permitAll 로 열고 컨트롤러에서 토큰 검증(JWT 인증 체계와 분리), 토큰 비교는 상수 시간 비교. Markdown 형식은 발표/포트폴리오 문서에 바로 붙일 수 있는 표를 만든다("표본 N, 참여율 AI 71.2% vs 랜덤 63.8%, 차이 +7.4%p (95% CI …), 대응 비교 …"). 사용자 식별 정보(닉네임/ID)는 리포트에 포함하지 않는다.
7. **윤리/고지**: 사용자 개인 단위 실험이 아니라 그룹-날짜 단위로 문장 생성 방식만 다르며 서비스 품질에 해가 없다. 그래도 개인정보 처리방침/약관 문구에 "서비스 개선을 위한 주제 생성 방식 비교 분석(비식별 집계)" 한 줄을 넣을 것을 문서에 남긴다(문구 확정은 사용자).

## Files / Functions to Change

| 파일 | 변경 |
|---|---|
| `V1__initial_schema.sql` | `missions.generation_source`, `missions.experiment_arm`, 인덱스 |
| `mission/dao/MissionInsertRow.java`, `MissionMapper.java` | 컬럼 insert, 리포트용 집계 쿼리(`findExperimentRows(from, to)`) — `MissionMapper` 가 비대하면 `mission/dao/ExperimentMapper.java` 신규 |
| `mission/domain/GenerationContext.java` (B8) | `forcedArm` 추가(또는 발행기에서 직접 Placeholder 호출) |
| `mission/service/ExperimentAssigner.java` (신규, 순수) | 해시 배정, 설정(`enabled`, `aiRatio`, `salt`) |
| `mission/service/MissionPublisher.java` | 군 계산·강제 경로·출처 기록 (틀 선택/대상 지정 로직 불변) |
| `mission/service/FallbackMissionGenerator.java` (B8) | 출처(`AI`/`AI_FALLBACK`/`PLACEHOLDER`) 반환 보장 |
| `mission/service/ExperimentReportService.java`, `mission/domain/ExperimentStats.java` (신규, 순수 통계 계산) | 지표·검정·부트스트랩(시드 고정 가능하게 `RandomGenerator` 주입) |
| `mission/controller/ExperimentReportController.java`, `mission/dto/ExperimentReport*.java` (신규) | 내부 엔드포인트 |
| `global/config/ExperimentProperties.java`(신규) + `application.properties` | `hawe.experiment.topic.enabled=false`, `ai-ratio=0.5`, `salt`, `hawe.admin.token=${HAWE_ADMIN_TOKEN:}` |
| `global/security/SecurityConfig.java` | `/internal/**` permitAll |
| 테스트 | 아래 |
| 문서 | `MISSIONS_API.md` §6(실험 군·출처 기록, 블라인드), `PROJECT_OVERVIEW.md` §6(AI 효과 측정), `BACKEND_DB.md`(missions 컬럼), 신규 `docs/BACKEND/EXPERIMENT_TOPIC.md`(실험 설계서: 가설·배정·지표·분석·한계·리포트 읽는 법), Postman 에 내부 엔드포인트 요청 |

## Implementation Steps

1. V1 수정 → **로컬 DB 백업·DROP/CREATE·Flyway 재적용**(보고서에 경로·행 수). 2. `ExperimentAssigner` + 단위 테스트. 3. 발행기 연결(기본 비활성에서 기존 테스트 전부 통과 확인). 4. 통계 계산부(순수 함수) + 단위 테스트. 5. 리포트 서비스·컨트롤러. 6. 통합 테스트(시드 데이터로 군별 제출을 만들어 기대 값 검증). 7. `.\mvnw.cmd clean test -q`. 8. 문서.

## Constraints

- **기본 비활성**: `enabled=false` 일 때 발행 동작·DB 값(`experiment_arm`)이 이전과 같아야 한다(`generation_source` 는 기록 가능). 틀·대상 지정·시각·`avoidContents` 로직에 배정이 영향을 주면 안 된다. AI 와 랜덤의 **틀 분포가 같다**는 것을 테스트로 보장.
- 앱·사용자에게 군/출처를 노출하지 않는다(응답 DTO 불변). 리포트에 개인 식별 정보 금지.
- 관리자 인증 체계(역할)는 이번에 만들지 않는다 — 토큰 헤더 보호 엔드포인트가 기본값. **결정 대기(README 결정 표)**: 역할 기반 관리자/별도 CLI 가 필요하면 별도 계획.
- 외부 통계 라이브러리 추가 금지(Wilson/z/t/부트스트랩은 직접 구현 — 단, t 분포 분위수는 작은 df 표 + 정규근사로 처리하고 오차 한계를 문서화; Apache Commons Math 도입은 의존성 추가라 결정 대기).
- 스키마는 V1 직접 수정. 커밋/푸시·지침 파일 수정 금지.

## Edge Cases

- 실험 중 새로 생긴 그룹/나간 멤버/미션 중 가입: `eligible` 계산이 `JOINED_BY_PUBLISH` 규칙을 따른다(미션이 열린 뒤 가입한 사람은 분모에서 제외).
- 하루 여러 슬롯: 같은 날짜 군 통일, 슬롯별 행을 모두 집계. 슬롯 수가 군마다 다르면 가중 평균이 달라질 수 있으므로 그룹-날짜 단위 평균도 함께 출력.
- AI 호출 실패(폴백): ITT 와 per-protocol 값이 다를 수 있음을 리포트에 표시. 실험 도중 AI 비활성으로 바뀐 날은 `experiment_arm = NULL` → 분석 제외.
- 같은 그룹이 한쪽 군만 경험(`aiRatio` 가 극단이거나 기간이 짧음) → 대응 비교에서 제외하고 개수 표시.
- 미션이 마감되기 전(제출이 더 들어올 수 있음)은 분석 제외: 기본으로 **닫힌 미션만**(`deadline_at < now()` 이고 같은 슬롯의 다음 미션이 발행된 것) 집계, 쿼리 파라미터로 변경 불가.
- 표본 0: 0 으로 나누기 없이 `null` + 경고.
- 토큰 없음/불일치 → 404(존재 숨김) 또는 401 — 한 가지로 통일, 토큰 설정 없음 → 엔드포인트 비활성.
- 해시 배정의 균등성: `aiRatio=0.5` 에서 대표 입력 10,000개로 비율 ±2%p 이내(단위 테스트).

## Regression Risks

- `MissionInsertRow`/`missions.insert` 변경이 발행 전 경로를 건드림 → 기존 `MissionPublisherTests`/통합 발행 테스트 전체 통과 필수(`enabled=false` 경로).
- `GenerationContext`·`GeneratedMission` 시그니처 변경(B8 위) 연쇄 수정.
- `SecurityConfig` 변경은 인증 경로 → `/internal/**` 외 경로는 영향 없음을 기존 보안 테스트로 확인.
- V1 수정 → checksum 불일치(로컬 재생성 필수, 공유 DB 별도).
- 통계 구현 오류는 숫자를 조용히 틀리게 만든다 → 알려진 값(손계산/외부 도구로 계산한 값)으로 단위 테스트.

## Tests Required

- `ExperimentAssignerTests`: 결정성, 비율 근사, `enabled=false`/AI 불가 시 `empty`, 같은 날짜 같은 그룹은 같은 군.
- `MissionPublisher` 통합: 실험 켬 + AI 모킹 → AI 군 미션 `generation_source=AI`·`experiment_arm=AI`, 랜덤 군 `PLACEHOLDER`·`RANDOM`, 틀 분포가 배정과 무관(같은 시드에서 군을 바꿔도 같은 틀), AI 실패 시 `AI_FALLBACK` + 군 `AI`; 실험 끔 → `experiment_arm` NULL.
- `ExperimentStatsTests`: Wilson 구간·z 검정·대응 평균 차이·부트스트랩(고정 시드)의 알려진 값, 경계(0/1 비율, 표본 1).
- `ExperimentReportServiceTests`/MockMvc: 시드 데이터(두 군, 제출 수 조정)에서 리포트 JSON/MD 값 검증, 닫힌 미션만 포함, 개인 식별 정보 미포함, 토큰 보호(없음/틀림/맞음).

## Validation Steps

`.\mvnw.cmd clean test -q` 통과 기록, 기본 설정에서 기존 발행 테스트 불변 확인, `rg "experiment_arm|generation_source" src/main` 으로 응답 DTO 에 노출되지 않음 확인, Postman 파싱·한글, `git diff --check`. 보고서에 DB 재생성, 통계 구현 한계, 리포트 샘플(테스트 데이터)을 포함.

## Potential Merge Conflicts

`MissionPublisher`/`MissionInsertRow`/`MissionMapper`/`FallbackMissionGenerator`/`GenerationContext`(B7·B8 과 순차 — B8 이후), `SecurityConfig`, `V1__initial_schema.sql`, `docs/BACKEND/MISSIONS_API.md`·`PROJECT_OVERVIEW.md`.
