# B12. AI 미션 생성을 Spring AI + AWS Bedrock 으로 재구성 (백엔드)

> 상세 설계·근거: `docs/BACKEND/MISSION_AI_SPRING_AI_BEDROCK.md` (이 계획은 그 문서를 `.agent/plan.md` 형식으로 옮긴 실행용 요약이다. 둘이 다르면 설계 문서가 정본이다).
> 범위: 백엔드(`src/`, `pom.xml`, `docs/`)만. 앱·`ml/` 무관. **스키마 변경 없음 → DB 재생성 불필요.** 팀원 프롬프트는 저장소에 없고 우준이 따로 관리한다 — 만들거나 수정하지 않는다.
> **전제**: B8(미션 AI 생성)·B9(실험)가 커밋돼 있음(완료). **이 작업은 단계 0(PoC)을 통과해야 단계 1 이후로 간다.** 영역 소유는 우준(B8/B9)이므로 시작 전에 우준에게 알린다. 실제 Bedrock 호출(스모크)에는 AWS 계정·모델 접근·IAM 자격증명이 필요하다(코드 밖, 사용자).
> **주의**: Spring AI·Bedrock 의 클래스/속성 이름은 작성 시점에 Spring Boot 4.1.1 과의 호환을 확인하지 못했다. PoC 로 확인하기 전에는 확정이 아니다.

## Goal

1. 미션 문장 생성(문장·카테고리·빈칸 값)의 LLM 호출을 **Spring AI(`ChatClient`/`ChatModel`) + AWS Bedrock(Converse)** 로 바꾼다. 인증은 IAM 역할/기본 자격증명 체인(키를 코드·설정에 두지 않음).
2. **바뀌지 않는 것**: AI 는 미션 생성만 한다, 틀·대상은 서버가 정한다, 결과는 서버가 재검증한다(`GeneratedMissionValidator`), 실패하면 고정 문장으로 폴백해 발행을 막지 않는다, LLM 으로 보내는 정보 범위(미션 단위 집계·관계·날짜·최근 미션 문장·기념일)는 그대로다.
3. 로컬/CI 에서 **AWS 자격증명 없이도** 애플리케이션과 기존 테스트가 정상이어야 한다(`hawe.mission.ai.enabled=false` 기본).

## Findings

(코드로 확인함)

- 호출 흐름: `MissionPublisher → MissionGenerator(@Primary FallbackMissionGenerator) → AiMissionGenerator → AnthropicClient.callTool(system, user, tool)`. `FallbackMissionGenerator` 는 AI 불가/실패/B9 랜덤군이면 `PlaceholderMissionGenerator` 로 대체하고 `GenerationSource`(AI / AI_FALLBACK / PLACEHOLDER)를 남긴다.
- `AiMissionGenerator`: 입력 JSON 조립(`input(...)`), 최대 `max-attempts` 재시도와 `total-timeout`(발행 트랜잭션 안에서 호출), `GeneratedMissionValidator` 재검증, 도구 `submit_mission`(`TOOL`, 스키마 content/category/params) 강제 호출. LLM 호출은 `client.callTool(systemPrompt, input, TOOL)` **한 곳**이다 → 교체 지점.
- `AnthropicClient`: `RestClient` 로 Anthropic Messages API 호출, `cache_control` 로 시스템 프롬프트 캐시, 실패는 `AiGenerationException`.
- `MissionAiProperties`(`hawe.mission.ai.*`): `enabled`, `apiKey`, `baseUrl`, `model`, `maxTokens`, `temperature`, `maxAttempts`, 타임아웃, 프롬프트 경로 등. `usable() = enabled && apiKey 있음`.
- 시스템 프롬프트 = 팀원 프롬프트 파일(`prompt-paths`, 없으면 `mission-ai/default-prompt.txt`) + `mission-ai/server-guide.txt`.
- `CLAUDE.md`: "프롬프트에 중괄호 `{ }` 를 쓰지 않는다(Spring AI 가 템플릿 변수로 읽음)". 이 제약은 유효하며 유지한다. 입력 JSON 자체는 중괄호투성이라 **템플릿 렌더링을 피하는 호출 경로**가 필수다.
- 이 프로젝트는 Boot 4 에서 `mybatis-spring-boot-starter` 자동 구성 순서 문제로 `MyBatisConfig` 를 직접 구성한 전례가 있다 → Spring AI 자동 구성도 컨텍스트 로드로 검증한다.
- 프롬프트 캐시는 현재 사용 중(`cache_control`). Bedrock 캐시 포인트 지원은 모델·리전·Spring AI 버전에 따라 다르다.

## Root Cause / Key Implementation Points

1. **교체 경계 `MissionLlm`**: `Map<String,Object> generate(String systemPrompt, String userMessage)` 같은 좁은 인터페이스. 구현이 도구 호출이든 구조화 출력이든 `content/category/params` 맵을 돌려준다. 모든 실패는 `AiGenerationException`. `AiMissionGenerator` 는 `AnthropicClient` 대신 `MissionLlm` 에 의존(나머지 로직 불변).
2. **구현체**: `SpringAiMissionLlm`(신규) — `ChatClient`/`ChatModel` 로 Bedrock Converse 호출. 필요 시 `AnthropicClient` 도 `MissionLlm` 을 구현해 `hawe.mission.ai.provider=anthropic|bedrock` 로 선택(전환 기간 병행). 확정 후 제거 여부는 결정 2.
3. **중괄호 함정**: `.system(String)`/`.user(String)` 같은 문자열 템플릿 경로 금지. `SystemMessage`/`UserMessage` 객체를 직접 담은 `Prompt` 로 호출하고, 중괄호가 든 시스템 프롬프트·JSON 사용자 메시지가 **치환 없이 그대로** 모델에 전달됨을 테스트로 고정한다.
4. **호출 방식(PoC 로 결정)**: A) 특정 도구 강제(현재와 동일, 가능하면 A), B) 구조화 출력(`.entity(...)`). B 면 `GeneratedMissionValidator` + 재시도로 보완. 어느 쪽이든 서버 안내(`server-guide.txt`)와 결과 형식이 모순되지 않아야 한다.
5. **꺼짐 조건**: `enabled=false`(기본)일 때 Bedrock 빈이 만들어지지 않거나 지연 초기화되어 AWS 자격증명/리전이 없어도 `@SpringBootTest` 가 통과. `usable()` 은 키 대신 `enabled` + 모델 ID 존재로 판단하고, 실제 자격증명 문제는 호출 실패 → 폴백으로 처리.
6. **타임아웃·재시도**: Bedrock 클라이언트의 연결/읽기 타임아웃과 SDK 재시도를 명시해 `readTimeout × 시도 수` 가 `totalTimeout` 을 넘지 않게 한다. SDK 재시도와 `AiMissionGenerator` 재시도가 곱해지지 않게 한쪽을 줄인다. 스로틀링·모델 접근 오류·타임아웃 → `AiGenerationException`.
7. **로깅**: 프롬프트·응답 본문·자격증명을 로그에 남기지 않는다(`FallbackMissionGenerator` 처럼 사유 요약만).

## Files / Functions to Change

| 파일 | 변경 |
|---|---|
| `pom.xml` | Spring AI BOM(`dependencyManagement`) + Bedrock Converse 스타터, 버전은 속성(`<spring-ai.version>`) — Boot 4.1.1 지원 버전 확인 후. 다른 의존성·플러그인 변경 금지 |
| `mission/service/MissionLlm.java` (신규) | 위 1번 |
| `mission/service/SpringAiMissionLlm.java` (신규) | 위 2~4번 |
| `mission/service/AnthropicClient.java` | (병행 시) `MissionLlm` 구현 추가. 동작 불변 |
| `mission/service/AiMissionGenerator.java` | `AnthropicClient` → `MissionLlm` 로 주입 교체. 재시도·검증·입력 조립 불변 |
| `global/config/MissionAiProperties.java` | `provider`(기본 `anthropic` 또는 결정에 따름), Bedrock 모델 ID, `usable()` 재정의. 기존 `apiKey` 는 provider=anthropic 일 때만 의미. 검증 로직은 기존 스타일 유지 |
| `application.properties` | `spring.ai.bedrock.aws.region`, `spring.ai.bedrock.converse.chat.options.*`, `hawe.mission.ai.provider`. 한국어 주석 |
| 테스트 | 아래 |
| 문서 | `docs/BACKEND/MISSION_AI_SPRING_AI_BEDROCK.md` 에 PoC 결과 기록, `CONTRACT_DECISIONS.md` #5(제공자 결정 변경), `MISSIONS_API.md` §6-2·§8 의 "Spring AI 없이" 문구, `PROJECT_OVERVIEW.md` §6, `docs/해야될거/00-README.md` 결정 표 #5 |

바꾸지 않는 것: `FallbackMissionGenerator`, `PlaceholderMissionGenerator`, `GeneratedMissionValidator`, `GroupContextBuilder`, `MissionPublisher` 트랜잭션 구조, B9 실험, 틀 선택 규칙.

## Implementation Steps

0. **PoC (필수 관문)** — 별도 테스트/임시 브랜치에서 다음을 확인하고 결과를 설계 문서에 기록. 하나라도 막히면 **구현을 멈추고 보고** 하고 대안(AWS SDK `bedrockruntime` Converse 직접 호출 `BedrockMissionLlm`)을 사용자에게 제안한다(같은 `MissionLlm` 경계라 교체 가능).
   1) Boot 4.1.1 에서 동작하는 Spring AI 버전·스타터 좌표, 2) AWS 자격증명 없이 컨텍스트 로드 성공(꺼짐 조건), 3) 중괄호가 치환되지 않는 호출 경로, 4) 도구 강제 또는 신뢰할 수 있는 구조화 출력, 5) 프롬프트 캐시 포인트 지원 여부, 6) 모델 ID·리전·접근 권한·쿼터·지연(5·6 은 실제 AWS 접근이 있어야 확인 가능 — 불가하면 "미검증"으로 남긴다).
1. `MissionLlm` 추출 + `AiMissionGenerator` 호출부 교체(동작 불변 리팩터링). 기존 `AiMissionGeneratorTests`/`FallbackMissionGeneratorTests`/`AnthropicClientTests` 가 그대로 통과.
2. `SpringAiMissionLlm` + 설정 + 의존성 + 꺼짐 조건.
3. 테스트 추가, `.\mvnw.cmd clean test -q`.
4. 실제 Bedrock 스모크(틀 7종: `PHOTO_*`, `DRAW_*`, `TEXT_*` 별로 검증기 통과 여부, 지연, 토큰 비용) — **사용자 환경에서만 가능**, 못 했으면 "미검증".
5. 문서·결정 표 갱신. 운영 IAM 권한(`bedrock` 호출 권한 최소화)·모델 접근 활성화는 코드 밖(사용자).

## Constraints

- AI 는 미션 생성만 한다. 제출물 평가·판정 금지. LLM 으로 보내는 정보 범위 확대 금지(사용자 글·댓글·사진·닉네임·그룹 이름 전송 금지).
- API 키·자격증명을 코드·설정·`.env.example` 에 넣지 않는다(IAM 역할/기본 체인).
- 기본값은 AI 꺼짐. 꺼져 있거나 실패해도 발행이 막히면 안 된다(폴백 유지).
- 팀원 프롬프트 파일을 만들거나 수정하지 않는다. 프롬프트에 중괄호를 쓰지 않는 기존 규칙 유지.
- 스키마 변경 금지. 새 테스트 라이브러리 금지. 커밋/푸시·지침 파일 수정 금지.
- PoC 가 통과하지 않으면 단계 1 이후를 진행하지 않는다.

## Edge Cases

- AWS 자격증명/리전 없음 + `enabled=false` → 기동 성공, Bedrock 호출 없음. `enabled=true` 인데 자격증명 없음 → 호출 시점에 `AiGenerationException` → 폴백(기동은 성공).
- 스로틀링(429 계열)·모델 접근 미허용·타임아웃·빈 응답·형식 오류 → 모두 `AiGenerationException`.
- 응답이 검증을 통과하지 못함 → 기존 재시도 후 폴백.
- 입력 JSON·시스템 프롬프트의 중괄호/따옴표/한글/개행 → 치환 없이 전달.
- SDK 재시도 × 서비스 재시도로 발행 트랜잭션이 `totalTimeout` 을 초과하지 않도록.
- 도구 강제가 안 되는 모델/버전 → 구조화 출력 경로와 검증으로 대응(결정 3).
- 운영에서 IAM/모델 접근이 비활성이면 항상 폴백만 나온다 → `generation_source` 의 `AI_FALLBACK` 비율을 확인할 수 있어야 한다(코드 변경 없이 DB 로 확인 가능).

## Regression Risks

- Spring AI 자동 구성이 `@SpringBootTest` 전체를 깨뜨릴 수 있음(자격증명/리전 누락, 빈 순서). 컨텍스트 로드 테스트로 반드시 확인.
- `AiMissionGenerator` 생성자 시그니처 변경 → 테스트의 `new AiMissionGenerator(...)` 수정.
- BOM 추가가 다른 의존성 버전(Jackson 등)을 바꿀 수 있음 → 의존성 트리 비교(`mvnw dependency:tree` 전후) 후 변경 없음 확인, 있으면 보고.
- 프롬프트 캐시 미지원 시 호출당 비용 증가.
- 템플릿 치환으로 JSON 입력이 깨져 항상 폴백이 나오는 조용한 실패 → 중괄호 테스트가 막는다.

## Tests Required

- **중괄호 테스트(필수)**: 중괄호가 든 시스템 프롬프트와 JSON 사용자 메시지가 `ChatModel`(목)에 **그대로** 전달됨.
- `SpringAiMissionLlm` 단위 테스트(목): 정상 응답 → `Map` 변환, 빈 응답/형식 오류/예외/스로틀링/타임아웃 → `AiGenerationException`.
- `AiMissionGeneratorTests`: 호출부가 `MissionLlm` 목이어도 기존 시나리오(재시도, 총 제한시간, 검증 실패 후 재시도) 유지.
- `FallbackMissionGeneratorTests`: 변경 없이 통과.
- 컨텍스트 로드: AWS 자격증명 없이 `@SpringBootTest`(`HaweApplicationTests` 등) 통과, `provider`/`enabled` 조합별 빈 구성.
- `MissionAiProperties` 검증 테스트(새 설정값).
- 전체: `.\mvnw.cmd clean test -q`.

## Validation Steps

1. PoC 결과표(1~6)를 설계 문서에 기록(미검증 항목은 "미검증"으로 명시).
2. `.\mvnw.cmd clean test -q` 통과 기록(AWS 없이).
3. `mvnw dependency:tree` 전후 비교 요약.
4. `rg -n "api-key|apiKey|accessKey|secret" src/main` — 새 코드에 자격증명 하드코딩 없음.
5. `git diff --check`, 의도하지 않은 파일(팀원 프롬프트, `CLAUDE.md`, `AGENTS.md`, `ml/`) 변경 없음 확인.
6. 가능하면 실제 Bedrock 스모크 결과(틀 7종 통과 여부·지연·토큰) 기록.

## Potential Merge Conflicts

`MissionAiProperties`, `AiMissionGenerator`, `AnthropicClient`, `application.properties`, `pom.xml`, 결정 문서(`CONTRACT_DECISIONS.md`, `MISSIONS_API.md`, `PROJECT_OVERVIEW.md`, `docs/해야될거/00-README.md`). 미션/AI 영역은 우준(B8/B9) 소유 — 시작 전 알리고 같은 파일 동시 수정 금지.

## 결정이 필요한 것

| # | 질문 | 기본값 |
|---|---|---|
| 1 | Bedrock 모델 ID·리전 | 콘솔에서 접근 가능한 Claude 모델 중 비용·지연 균형이 좋은 것(기존 기본은 haiku 급). PoC 후 확정 — **사용자가 정해야 함** |
| 2 | `AnthropicClient` 병행 여부 | 전환 검증이 끝날 때까지 `provider` 설정으로 병행, 확정 후 제거 |
| 3 | 호출 방식 A(도구 강제) vs B(구조화 출력) | PoC 4번 결과에 따라 A 우선 |
| 4 | 프롬프트 캐시 미지원 시 | 호출량(하루 그룹 수 × 슬롯 수) 기반 비용 확인 후 수용 또는 시스템 프롬프트 축소 |
| 5 | 미션 생성 외 기능(인사이트 서술 등)으로 확장 | 이번 범위 아님 |
