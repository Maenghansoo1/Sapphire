# B8. AI 미션 생성기 (백엔드)

> 범위: 백엔드만. 알림 무관. **전제: B7 커밋 후**(B7 이 `MissionGenerator` 시그니처를 `avoidContents` 집합으로 바꾼다 — 이 계획은 그 시그니처를 구현한다). 근거: `docs/BACKEND/MISSIONS_API.md` §6(서버 검증 표), §6-1(틀은 서버가 정함), §8(프롬프트 계약), `docs/FEATURE/PROJECT_OVERVIEW.md` §6·§7(AI 역할·안전 규칙).
>
> **가정(README 결정 표 #5·#6 — 다르게 정하면 알려 달라)**: 제공자 = Claude API(Anthropic Messages API)를 `RestClient` 로 직접 호출(Spring AI 미사용), 모델/키는 설정·환경변수, 키가 없으면 **자동으로 임시 고정 문장(폴백)**. 프롬프트 본문은 팀원 소유 — 저장소 밖 파일 경로를 설정으로 받고, 없을 때를 위한 **임시 기본 프롬프트**만 리소스로 둔다.

## Goal

발행 시 `PlaceholderMissionGenerator` 대신(또는 앞서) **AI 가 만든 `{content, category, params}`** 를 쓴다. 서버는 AI 결과를 **반드시 재검증**하고, 실패하면 재시도 후 임시 고정 문장으로 폴백한다. 틀은 항상 서버가 정한다(AI 는 틀을 고르거나 바꾸지 않는다). 제출물·사용자 개인정보는 AI 로 보내지 않는다.

## Findings

- `MissionGenerator.generate(MissionTemplate template, long seed, Collection<String> avoidContents /*B7 이후*/, boolean withTarget)` → `GeneratedMission(String content, String category, Map<String,Object> params)`. 구현체는 `PlaceholderMissionGenerator`(고정 문장, 틀마다 변형 3개+) 하나. `MissionPublisher.publishIfDue` 가 호출하고, `withTarget` 이면 서버가 `params.targetUserId` 를 따로 넣는다(`generated.params()` 는 AI/생성기 몫: 대상 지정 틀의 문장에는 이름 대신 **`[대상]`** 을 쓰고 서버가 응답 시 닉네임으로 치환 — `MissionService.toResponse`).
- 생성 예외는 `MissionScheduler` 가 그룹별로 기록하고 다음 분에 재시도한다. `MissionPublisher.publishIfDue` 는 `@Transactional(REQUIRES_NEW)` — **트랜잭션 안에서 외부 HTTP 를 호출하게 된다**(아래 위험).
- 검증 기준 문서: MISSIONS_API §6 표 — 틀(응답이 서버가 정한 틀의 `params` 형식), 빈칸(모두 채워짐·불필요한 키 없음), 삼행시 `word` = 한글 정확히 3글자, 밸런스 `optionA ≠ optionB`, 슬라이더 `left ≠ right`·각 ≤20자, 빈칸 채우기 `sentence` 에 `___` 정확히 1개, 카테고리 = 목록 값, 연속 방지(최근 미션/같은 날과 같거나 거의 같은 문장 금지). `MissionTemplate.paramsSchema()` 에 틀별 params 키가 정의돼 있다(서버가 넣는 `targetUserId` 키는 AI 출력에서 제외해야 함).
- 안전 규칙(`PROJECT_OVERVIEW.md` §7): 공통 금지(폭력·성적·혐오·위험·음주도박·외모몸체중·돈·위치개인정보·몰래촬영·무거운 주제·정치종교·욕설·**이모지**), 좋은 미션 조건(오늘 안에 가능·1~2분·한 문장 30자 안팎), 관계별 주의표, 핵심 원칙(평가 대상이 그룹 내 누군가가 만든/준 것일 수 있으면 나쁘게 평가하는 미션 금지). `TEXT_FILL_BLANK`/`DRAW_*` 대상 지정은 §8 에 따라 긍정적·가벼운 방향.
- 카테고리 값: 임시 생성기가 `일상`, `감성`, `추억`, `유머` 를 쓴다. 공식 목록 상수는 아직 없다.
- 프롬프트 계약(§8): 제출 평가 안 함 / 틀은 서버 입력 / 입력 = 날짜·요일, 그룹 관계(톤·금지 주제용), 선택된 틀과 빈칸 정의, 대상 유무, 최근 미션(같은 날 포함), 카테고리 목록 / 출력 = `content`, `category`, `params` / 대상이 있으면 `[대상]` / 성대모사·소리 흉내는 "~를 맞춰보세요" / 프롬프트에 중괄호 금지(Spring AI 용 — 직접 HTTP 호출에서는 무관하지만 팀원 프롬프트 호환을 위해 **치환 변수 방식이 아니라 입력을 별도 JSON 메시지로 전달**한다).
- 현재 의존성: `spring-boot-starter-webmvc`(→ `RestClient` 사용 가능), `jackson`(tools.jackson `ObjectMapper`). 별도 HTTP/AI 라이브러리 없음. 테스트 의존: `spring-boot-starter-webmvc-test`(`MockRestServiceServer` 사용 가능).
- 모델 ID(최신): Fable 5.1 `claude-fable-5-1`, Opus 5.5 `claude-opus-5-5`, Sonnet 5.5 `claude-sonnet-5-5`, Haiku 5.5 `claude-haiku-5-5`. 짧은 JSON 생성이라 기본 `claude-haiku-5-5`.

## Root Cause / Key Implementation Points

1. **구성(빈 3개)**: 
   - `PlaceholderMissionGenerator`(기존, 그대로) — 폴백.
   - `AiMissionGenerator`(신규, `mission/service`) — `hawe.mission.ai.enabled=true` **이고** API 키가 비어 있지 않을 때만 활성(`@ConditionalOnProperty` + 키 검사, 또는 생성 시 `available()`).
   - `FallbackMissionGenerator`(신규, `@Primary @Component`, `MissionGenerator` 구현) — `AiMissionGenerator` 가 사용 가능하면 먼저 호출하고, 예외/검증 실패 시 `PlaceholderMissionGenerator` 로 폴백. `MissionPublisher` 는 `MissionGenerator`(=Primary)만 주입받아 **변경 없음**.
2. **AI 호출**(`AnthropicClient`, `mission/service/ai` 같은 하위 패키지 금지 → `mission/service` 에 둔다; 패키지 규칙 5개 서브패키지 유지): `RestClient` 로 `POST {base-url}/v1/messages`, 헤더 `x-api-key`, `anthropic-version: 2023-06-01`, `content-type: application/json`. 본문: `model`, `max_tokens`(기본 400), `temperature`(기본 0.9), `system`(프롬프트 텍스트), `messages: [{role:"user", content: <입력 JSON 문자열>}]`. 응답에서 첫 `content[].text` 를 꺼내 **JSON 객체 하나**로 파싱(앞뒤 코드펜스 ```json 제거 허용). 연결 10초·읽기 20초 타임아웃. 키는 **절대 로그에 남기지 않는다**. 설정(`@ConfigurationProperties("hawe.mission.ai")` 레코드 `MissionAiProperties`): `enabled`(false), `baseUrl`(`https://api.anthropic.com`), `apiKey`(`${ANTHROPIC_API_KEY:}`), `model`(`claude-haiku-5-5`), `maxTokens`, `temperature`, `maxAttempts`(2), `promptPath`(빈 값 허용), `categories`(`일상,감성,추억,유머`), `blockedWords`(리스트, 기본 빈 목록).
3. **프롬프트 로딩**: `promptPath` 가 있으면 그 파일(UTF-8)을 system 으로 사용(기동 시 1회 읽고 캐시; 파일이 없거나 읽기 실패면 임시 기본 프롬프트로 폴백하고 WARN). 파일이 없을 때 쓸 **임시 기본 프롬프트**를 `src/main/resources/prompts/mission-generator.default.txt` 로 추가한다: 위 프롬프트 계약(미션 생성만, 틀 고정, 출력 JSON 스키마, `[대상]` 규칙, 안전 규칙 요약, 한 문장 30자 안팎, 이모지 금지, 맞춰보세요 문장)을 한국어로 쓴 내용 — **파일 첫 줄에 "임시 기본 프롬프트 — 팀원 프롬프트가 있으면 `hawe.mission.ai.prompt-path` 로 교체"를 명시**. 프롬프트에 중괄호 `{ }` 를 쓰지 않는다(예시 JSON 은 대괄호/따옴표 설명으로 풀어 쓰거나 입력 메시지 쪽에 둔다).
4. **입력 메시지(user, JSON 문자열)**: `{ "date": "2026-10-12", "weekday": "월요일", "relation": "FRIEND", "template": "TEXT_ACROSTIC", "media": "TEXT", "params": { "word": "string(3)" }, "hasTarget": false, "recent": ["…", …], "categories": ["일상","감성","추억","유머"] }`. 여기서 `params` 는 `MissionTemplate.paramsSchema()` 에서 서버 할당 키(`targetUserId`)를 뺀 것. `recent` = `avoidContents`. **멤버 이름·닉네임·이메일·그룹 이름·사진/음성 데이터는 넣지 않는다.** `relation` 은 `COUPLE/FAMILY/FRIEND/RIVAL/CASUAL` 코드 그대로 — 이를 위해 `generate(...)` 호출에 그룹 관계가 필요하다: `MissionGenerator` 에 파라미터를 더하는 대신 **`GenerationContext`(record: `LocalDate date, String relationType`)를 인자로 추가**하는 시그니처 변경이 필요하다(B7 의 `avoidContents` 와 함께 `generate(template, seed, GenerationContext context, Collection<String> avoidContents, boolean withTarget)`). `MissionPublisher` 가 `group.getRelationType()` 과 `date` 로 만든다. Placeholder 구현은 context 를 무시.
5. **재검증** (`mission/domain/GeneratedMissionValidator`, 순수 static, 단위 테스트 용이): 모두 통과해야 채택, 하나라도 실패하면 그 시도는 버리고 재시도(최대 `maxAttempts`), 소진 시 폴백.

   | 검증 | 규칙 |
   |---|---|
   | JSON 형태 | 객체, `content`(문자열), `category`(문자열), `params`(객체) 외 키는 무시하지 않고 **거부** |
   | content | trim 후 1~60 코드포인트, 줄바꿈 없음, 이모지/픽토그램 없음, `blockedWords` 포함 시 거부, 중괄호 없음 |
   | category | `categories` 목록에 있는 값 |
   | params 키 | 정확히 `paramsSchema` 키 집합(서버 할당 `targetUserId` 제외) — 부족/초과 모두 거부. 선택 키(`optional` 표기)는 있어도 되고 없어도 됨 |
   | 값 형식 | 문자열 값은 trim 후 1~(틀별 한도, 기본 40) 코드포인트·이모지 없음; `seconds`/숫자 키는 정수 범위(`DRAW_SKETCH.seconds`: 10~60) |
   | 삼행시 | `word` 가 **한글 음절 정확히 3글자** |
   | 밸런스 | `optionA` ≠ `optionB` (정규화 후), 각 ≤20자 |
   | 슬라이더 | `left` ≠ `right`, 각 ≤20자 |
   | 빈칸 채우기 | `sentence` 에 `___` 정확히 1개, `content` 와 `sentence` 동일 |
   | 대상 | `withTarget` 이면 `content` 에 `[대상]` 이 **정확히 1번 이상 포함**, 아니면 `[대상]` 금지 |
   | G 틀 | `VOICE_MIMIC`/`VOICE_IMPRESSION` 은 `params` 빈 객체이고 content 가 "맞춰보세요"를 포함 |
   | 연속 방지 | 정규화(공백·구두점 제거, 소문자) 후 `avoidContents` 와 **동일**하거나 문자 2-gram 자카드 유사도 ≥ 0.8 이면 거부 |

   `content`/`params` 는 DB 에 저장되기 전 서버가 만든 `GeneratedMission` 으로 변환(`params` 값 타입 보존: 숫자는 정수).
6. **폴백/가용성**: `Fallback` 은 AI 가 비활성/키 없음/예외/타임아웃/검증 실패 소진 어느 경우든 **예외 없이** Placeholder 결과를 돌려준다(단, 폴백 사유는 WARN 로그: 틀·시도 횟수·원인 분류, **응답 본문 전체나 키는 로그 금지**). 스케줄러 재시도 루프에 AI 장애가 번지지 않게 한다.
7. **트랜잭션 위험 완화**: `publishIfDue` 가 DB 트랜잭션(REQUIRES_NEW) 안에서 AI 를 호출하므로 총 대기 시간을 제한한다 — `maxAttempts × 읽기 타임아웃` 이 ≤ 45초 이내가 되도록 기본값(2회×20초) 유지, 설정에 `totalTimeout`(기본 40초)을 두고 초과 시 즉시 폴백. (발행 로직 구조 변경은 이번 범위가 아님 — 위험을 문서에 기록.)
8. **결정성 파괴 주의**: AI 출력은 비결정적이므로 같은 `(그룹,날짜,슬롯)` 재실행 멱등성은 **DB UNIQUE 와 `existsByGroupIdAndDateAndSlot`** 이 보장한다(이미 그렇다). 선택기(틀·대상)의 결정성은 그대로이며 AI 는 문장·params 만 만든다.

## Files / Functions to Change

| 파일 | 변경 |
|---|---|
| `mission/service/MissionGenerator.java`, `PlaceholderMissionGenerator.java`, `MissionPublisher.java` | `GenerationContext` 인자 추가(B7 의 시그니처 위에), 호출부 수정 |
| `mission/domain/GenerationContext.java` (신규) | `record GenerationContext(LocalDate date, String relationType)` |
| `mission/service/AiMissionGenerator.java`, `FallbackMissionGenerator.java`, `AnthropicClient.java` (신규) | Key Point 1·2·6 |
| `global/config/MissionAiProperties.java` (신규) + `@EnableConfigurationProperties`(기존 설정 클래스 패턴 확인 후) | Key Point 2 |
| `mission/domain/GeneratedMissionValidator.java` (신규) | Key Point 5 |
| `src/main/resources/prompts/mission-generator.default.txt` (신규) | 임시 기본 프롬프트 (Key Point 3) |
| `src/main/resources/application.properties` | `hawe.mission.ai.*` 기본값(`enabled=false`, 키는 환경변수), 카테고리 |
| 테스트·문서 | 아래 / `MISSIONS_API.md` §6(AI 연동 구현됨·검증 표·폴백·입력 JSON 스키마·프롬프트 파일 위치), §8 프롬프트 계약에 "직접 HTTP 호출·치환 변수 없음" 반영, `PROJECT_OVERVIEW.md` §6(제공자 확정), `CONTRACT_DECISIONS.md` §3, **운영 설정 가이드**(환경변수 `ANTHROPIC_API_KEY`, `HAWE_MISSION_AI_ENABLED`) |

## Implementation Steps

1. 시그니처 확장(`GenerationContext`) + 기존 테스트 수정(동작 불변). 2. `GeneratedMissionValidator` + 단위 테스트(가장 먼저 — 안전의 핵심). 3. `MissionAiProperties`·프롬프트 로딩. 4. `AnthropicClient`(RestClient) + `MockRestServiceServer` 테스트. 5. `AiMissionGenerator`(입력 JSON 구성·파싱·재시도) + `FallbackMissionGenerator`(Primary). 6. 통합 테스트(AI 비활성 기본값에서 기존 전체 테스트 통과). 7. 문서. (스키마 변경 없음 — DB 재생성 불필요.)

## Constraints

- **기본값은 AI 비활성**(`enabled=false`, 키 없음) → 기본 설정에서 모든 기존 동작·테스트 불변. 실 API 호출을 하는 테스트 금지(항상 모킹). API 키를 저장소에 커밋 금지(.env 도 금지), 로그에 출력 금지.
- AI 에 멤버 이름/그룹 이름/제출물 데이터 전송 금지. AI 가 틀·대상·`targetUserId` 를 결정하게 하지 않는다. 제출 평가·판정 용도로 사용 금지.
- Spring AI 의존성 추가 금지. 외부 라이브러리 추가 최소화(없이 구현). 발행 로직(`MissionPublisher`)의 트랜잭션 구조 변경 금지.
- 커밋/푸시·지침 파일 수정 금지.

## Edge Cases

- 키/활성 설정 없음 → 항상 Placeholder. 키는 있는데 `enabled=false` → Placeholder. 네트워크 오류/타임아웃/HTTP 4xx·5xx/빈 응답/JSON 아님/코드펜스 포함 JSON → 재시도 후 폴백(코드펜스는 정상 파싱).
- AI 가 틀을 바꿔 다른 params 를 내놓음, `targetUserId` 를 직접 포함, 이모지·금칙어·너무 긴 문장, 삼행시 word 2글자/영문, 밸런스 A=B, 슬라이더 left=right, `___` 0개·2개, 대상이 필요한데 `[대상]` 없음/불필요한데 있음, 직전 미션과 거의 같은 문장 → 모두 검증 실패로 재시도/폴백.
- 같은 날 여러 슬롯(B7): 앞 슬롯 문장이 `avoidContents` 에 들어가 서로 다른 문장이 된다.
- 폴백 후에도 Placeholder 가 변형 부족으로 예외를 던지지 않는다(B7 에서 완화됨).
- 응답에 추가 키(`explanation` 등) → 거부(재시도). 응답 지연이 총 제한(40초)을 넘음 → 즉시 폴백.
- 프롬프트 파일 경로가 잘못됨 → 임시 기본 프롬프트 + WARN, 서버 기동은 성공.

## Regression Risks

- `MissionGenerator` 시그니처 2차 변경(B7 → B8) → `PlaceholderMissionGenerator`/`MissionPublisher`/테스트 연쇄 수정. 컴파일 오류가 곧 누락 신호.
- `@Primary` 빈 추가로 `MissionGenerator` 주입이 바뀜 → 테스트에서 `PlaceholderMissionGenerator` 를 직접 쓰는 곳은 영향 없음, `@SpringBootTest` 는 Primary 를 받아도 기본 설정이 폴백이므로 동일 결과.
- 트랜잭션 안 HTTP 호출: AI 가 느려지면 DB 커넥션을 오래 잡는다(위 제한으로 완화, 문서에 위험 기록).
- 설정 바인딩 실패(레코드 기본값)로 기동 실패하지 않게 모든 속성에 기본값.

## Tests Required

- `GeneratedMissionValidatorTests`: 표의 모든 규칙 × 통과/실패(틀 16종 중 대표 + 모든 특수 규칙), 이모지·금칙어·유사도 경계(0.79/0.8), 대상 규칙, 추가 키 거부.
- `AnthropicClientTests`(`MockRestServiceServer`): 요청 헤더(`x-api-key`, `anthropic-version`)·본문 모양(model/system/messages), 정상 응답 파싱, 코드펜스 제거, 4xx/5xx/타임아웃/빈 본문 → 예외, **로그/예외 메시지에 키 미포함**.
- `AiMissionGeneratorTests`: 입력 JSON 에 이름·그룹명이 없음, 대상 유무 반영, 재시도 로직(첫 응답 검증 실패→둘째 성공), 소진 시 예외.
- `FallbackMissionGeneratorTests`: 비활성→Placeholder, AI 성공→AI 결과, AI 실패→Placeholder(예외 없음), 폴백 사유 로깅(키·본문 미포함).
- `MissionPublisher` 통합: AI 비활성 기본 설정으로 기존 발행 테스트 전부 통과, AI 활성(모킹) 설정으로 발행된 미션의 `content/params` 가 AI 값이고 `targetUserId` 는 서버 값.

## Validation Steps

`.\mvnw.cmd clean test -q` 통과 기록(AI 비활성 기본 상태), `rg "ANTHROPIC|api-key|apiKey" src` 로 키가 코드/리소스에 하드코딩되지 않았는지 확인(플레이스홀더 `${ANTHROPIC_API_KEY:}` 만), `git diff --check`. 실제 API 호출 스모크는 키가 있는 환경에서 **사용자가** 수행(`ANTHROPIC_API_KEY` 설정 + `hawe.mission.ai.enabled=true` 로 기동 → 그룹 생성 → 첫 미션 문장 확인) — 못 했으면 "미검증" 명시.

## Potential Merge Conflicts

`mission/service/MissionGenerator.java`·`PlaceholderMissionGenerator.java`·`MissionPublisher.java`(B5·B7 이후 순차 필수), `application.properties`, `docs/BACKEND/MISSIONS_API.md`·`PROJECT_OVERVIEW.md`. 프롬프트 파일(팀원 소유)은 저장소 밖 경로로 주입하므로 충돌 없음.
