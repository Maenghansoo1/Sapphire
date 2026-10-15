# B12 단계 0 — Spring AI + AWS Bedrock PoC (개념 검증)

> 설계 정본: `docs/BACKEND/MISSION_AI_SPRING_AI_BEDROCK.md` §9, 실행 계획: `docs/해야될거/1번_찬희/B12-mission-ai-spring-ai-bedrock.md` 의 "Implementation Steps 0". 이전 `.agent/plan.md`(B11)는 덮어쓴다.
> **이 작업은 PoC(버려도 되는 시험)이며 production code 를 바꾸지 않는다.** 목적은 "Spring AI 로 Bedrock 을 쓰는 구현을 시작해도 되는가"에 대한 근거 수집이다. 결과에 따라 사용자가 (a) Spring AI 로 진행 / (b) AWS SDK 직접 호출로 대체 / (c) 보류 를 정한다.
> 이 PC 사실(확인함): **AWS 자격증명 없음**(`~/.aws` 없음, `AWS_*` 환경변수 없음, `aws` CLI 없음), Maven Central 접속 가능, JDK 25(`C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot`), 로컬 MariaDB 있음. 따라서 **실제 Bedrock 호출이 필요한 항목은 "미검증"으로 남기는 것이 정상 결과**다(거짓으로 통과시키지 말 것).

## Goal

설계 문서 §9 의 6개 질문에 근거(실행 결과·로그·요청 JSON)와 함께 답한다. 판정은 항목마다 `확인됨 / 막힘 / 미검증` 셋 중 하나이며, 막힘이면 원인과 우회안(AWS SDK 직접 호출 등)을 쓴다.

| # | 질문 | AWS 없이 확인 가능? |
|---|---|---|
| 1 | Spring Boot **4.1.1** 에서 동작하는 Spring AI 버전과 Bedrock Converse 스타터 좌표 | 가능 |
| 2 | **AWS 자격증명·리전 없이** 컨텍스트가 정상 기동하는가 (꺼짐 조건), 이 프로젝트에 넣어도 기존 `@SpringBootTest` 가 깨지지 않는가 | 가능 |
| 3 | 프롬프트의 **중괄호 `{ }`** 가 템플릿 변수로 치환되지 않는 호출 경로 | 가능 (모델 스텁 + 와이어 캡처) |
| 4 | 특정 도구(`submit_mission`) **강제 호출** 또는 신뢰할 수 있는 **구조화 출력**이 가능한가 | 요청 형태는 가능, 실제 모델 동작은 AWS 필요 |
| 5 | **프롬프트 캐시 포인트**를 보낼 수 있는가 / 실제 효과 | 요청 형태는 가능, 효과(토큰 절감)는 AWS 필요 |
| 6 | 모델 ID·리전·접근 권한·쿼터·지연 | **AWS 필요** (자격증명 있을 때만) |

## Findings

(확인한 사실)

- Maven Central 의 `org.springframework.ai:spring-ai-bom` 버전 목록에 `2.0.0-M2 … 2.0.0-RC1, 2.0.0-RC2, 2.0.0, 2.0.1, 2.1.0-M1` 이 있다. `2.0.1` 의 BOM 이 `spring-ai-starter-model-bedrock-converse`, `spring-ai-bedrock-converse`, `spring-ai-autoconfigure-model-bedrock-ai`, `spring-ai-starter-model-bedrock`(구형 Invoke 계열) 을 관리한다. 스타터 `spring-ai-starter-model-bedrock-converse:2.0.1` 은 `spring-boot-starter`, `spring-ai-autoconfigure-model-bedrock-ai`, `spring-ai-bedrock-converse`, `spring-ai-client-chat` 등에 의존한다. **어느 Spring AI 버전이 Boot 4.1.1 과 맞는지는 아직 모른다** — 후보(`2.0.1`, 필요하면 `2.0.0`/`2.1.0-M1`)의 `spring-ai-parent` POM 이 가리키는 Spring Boot/Spring Framework/Jackson/AWS SDK 버전을 직접 읽어 확인한다.
- 이 프로젝트: Spring Boot `4.1.1` parent, `java.version=25`, **Jackson 3(`tools.jackson.*`)** 사용(`ObjectMapper` import 가 `tools.jackson.databind`). Spring AI 가 Jackson 2(`com.fasterxml.jackson.*`)에 의존한다면 공존 시 충돌/이중 직렬화 문제가 있을 수 있다 → 확인 대상.
- `pom.xml` 현재: 의존성 약 10개(actuator, security, validation, webmvc, jdbc, mariadb/mysql, flyway, mybatis-spring-boot-starter 3.0.4, springdoc, jjwt, **lucene-analysis-nori**(B11)). 이 프로젝트는 Boot 4 에서 `mybatis-spring-boot-starter` 의 자동 구성 순서 문제 때문에 `global/config/MyBatisConfig` 가 `SqlSessionFactory` 를 직접 구성한다(`CLAUDE.md`). 새 자동 구성 스타터가 비슷한 문제를 일으키는지 확인해야 한다.
- 현재 LLM 호출: `mission/service/AiMissionGenerator.java` → `AnthropicClient.callTool(system, user, TOOL)` (도구 `submit_mission`: `content`/`category`/`params`, `input_schema` 포함). 시스템 프롬프트는 팀원 프롬프트 + `mission-ai/server-guide.txt`, 사용자 메시지는 `objectMapper.writeValueAsString(input)` 의 **중괄호가 많은 JSON**. 이 두 문자열이 템플릿 렌더링 없이 그대로 가야 한다(`CLAUDE.md` 의 "프롬프트에 `{ }` 금지" 제약의 이유).
- 현재 캐시: `AnthropicClient` 가 시스템 프롬프트에 `cache_control: ephemeral` 을 붙인다.
- 테스트 환경: `HaweApplicationTests` 는 `@SpringBootTest` 하나(`contextLoads`)이며 로컬 MariaDB·`.env`(`DB_USERNAME`/`DB_PASSWORD`/`JWT_SECRET`)가 필요하다.
- `.agent/` 는 `.gitignore` 대상이다(새 파일은 무시됨). PoC 산출물을 거기에 둔다.

## Root Cause / Key Implementation Points

### 방법 (핵심 설계 — 변경하지 말 것)

1. **프로젝트와 분리된 PoC 프로젝트**를 `.agent/poc-b12/` 에 만든다(자체 `pom.xml`: `spring-boot-starter-parent` 4.1.1, `java.version` 25, 후보 Spring AI BOM + `spring-ai-starter-model-bedrock-converse` + `spring-boot-starter-test`). 저장소의 `pom.xml`/`src/` 는 건드리지 않는다. `mvnw` 가 없으므로 저장소 루트의 `mvnw.cmd` 를 `-f .agent/poc-b12/pom.xml` 로 재사용하거나 설치된 `mvn` 사용.
2. **AWS 없이 와이어 수준 검증**: Bedrock Runtime SDK 가 보내는 HTTP 요청을 직접 본다. 로컬 `com.sun.net.httpserver.HttpServer`(추가 의존성 없음)를 스텁 서버로 띄워 SDK 클라이언트의 `endpointOverride` 를 그 주소로 지정하고, 더미 자격증명(`aws.accessKeyId`/`aws.secretAccessKey` 시스템 속성 또는 정적 자격증명 공급자 — **실제 키 아님**), 리전 `us-east-1` 같은 값을 준다. 스텁은 Converse 응답(고정 JSON, 도구 사용 응답 형태 포함)을 돌려주고 **요청 본문(JSON)을 캡처**한다. 이 방식으로 3·4·5 번의 "요청 형태"를 AWS 없이 검증한다. Spring AI 모델이 SDK 클라이언트를 외부에서 주입받는지(빌더/자동 구성 빈 교체 가능 여부) 또는 `endpointOverride` 를 설정 속성으로 받는지는 코드/소스 JAR 로 확인하고, 불가하면 가장 가까운 방법(자동 구성된 `BedrockRuntimeClient` 빈 교체 등)을 쓰고 그 사실을 기록한다.
3. **이 프로젝트와의 공존은 임시 복사본에서 시험**한다(2번 항목): 저장소를 직접 고치지 않고, 저장소 루트의 `pom.xml`, `src/`, `mvnw*`, `.mvn/` 를 `%TEMP%` 아래 새 디렉터리(스크래치패드 또는 `.agent/poc-b12/app-copy/`)로 복사한 뒤 **그 복사본의** `pom.xml` 에만 BOM·스타터를 추가하고 `.\mvnw.cmd clean test`(전체 또는 최소 `HaweApplicationTests`)를 실행한다. `.env` 도 복사해 DB 접속이 되게 한다(복사본이 같은 로컬 DB 를 쓰므로 Flyway 는 이미 적용된 상태에서 no-op). **복사본의 결과만 근거로 삼고 저장소 원본은 변경하지 않는다.** 끝나면 복사본은 `.agent/poc-b12/` 안에 남겨도 되고 지워도 된다(둘 다 ignore 대상).
4. **실제 AWS 호출(6번, 5번 효과)은 선택적**이다: 환경변수 `HAWE_POC_BEDROCK_MODEL`(모델 또는 inference profile ID)과 `AWS_REGION`, 표준 자격증명(환경변수/프로필)이 **모두 있을 때만** 실행되는 테스트(`Assumptions.assumeTrue` 로 건너뜀)로 만든다. 지금 이 PC 에는 자격증명이 없으므로 건너뛰는 것이 정상이다. 자격증명을 파일·로그·보고서에 쓰지 않는다. 실제 호출에 보내는 내용은 **가짜 샘플 입력**(실제 그룹/사용자 데이터 금지, 예: 저장소 `AiMissionGenerator.input(...)` 형태를 흉내 낸 하드코딩 JSON).

### 항목별 구체 검증

**① 버전·좌표** — (a) 후보 버전(`2.0.1` 먼저; 막히면 `2.0.0`, `2.1.0-M1`, `1.x` 최신)의 `spring-ai-parent` POM 에서 `spring-boot`, `spring-framework`, `jackson`, `awssdk` 버전 속성을 읽어 표로 정리한다. (b) PoC 프로젝트(Boot 4.1.1)에서 BOM + 스타터로 `compile` 과 빈 `@SpringBootTest` 컨텍스트 로드가 되는지 실행한다. (c) `mvnw dependency:tree` 로 Jackson 2/3 공존, AWS SDK(`software.amazon.awssdk`) 버전, Netty 등 눈에 띄는 충돌을 기록한다. 결론: "Boot 4.1.1 + Spring AI X.Y.Z 에서 컴파일·기동 확인됨 / 불가". 불가하면 오류 메시지 요약.

**② 자격증명 없는 기동 + 프로젝트 공존** — (a) PoC 프로젝트에서 `AWS_*` 환경변수·프로필 없이 컨텍스트가 뜨는지(기본 설정), (b) 뜨지 않으면 꺼짐 방법을 찾는다: Spring AI 의 모델 선택 속성(예: `spring.ai.model.chat=none` 류 — **정확한 속성 이름은 해당 버전의 자동 구성 클래스/문서에서 확인**), `@ConditionalOnProperty`, 자동 구성 제외(`spring.autoconfigure.exclude`) 중 실제로 동작한 것을 기록. (c) 임시 복사본(방법 3번)에서 전체 테스트 또는 `HaweApplicationTests` 가 통과하는지, MyBatis/Flyway/Security 자동 구성과 충돌하는 로그가 없는지. (d) `ChatModel`/`ChatClient` 빈이 켜졌을 때(자격증명 없음) 기동만으로 AWS 를 호출하지 않는지(네트워크 호출 없음), 첫 호출 시점에 어떤 예외 타입이 나오는지(→ `AiGenerationException` 으로 변환할 대상).

**③ 중괄호** — (a) 시스템 메시지와 사용자 메시지에 `{ }`, `{"a":{"b":1}}`, `{변수}`, `{{x}}`, `{0}` 를 섞은 문자열을 만든다(실제 `server-guide.txt` 내용과 JSON 입력 모양 포함). (b) 후보 호출 경로 세 가지를 각각 시도: ① `ChatClient.prompt().system(String).user(String)` ② `ChatClient.prompt().system(s -> s.text(..))`/`PromptTemplate` 류 ③ `new Prompt(List.of(new SystemMessage(text), new UserMessage(text)))` 를 `ChatModel.call(prompt)` 또는 `ChatClient.prompt(prompt)` 로 직접 호출. (c) 각 경로에서 모델에 **실제로 전달된 텍스트**를 캡처한다 — `ChatModel` 스텁(전달받은 `Prompt` 기록)과, 가능하면 와이어 캡처(요청 JSON 의 `system`/`messages[].content[].text`)로 이중 확인. (d) 표로 정리: 경로별 치환 여부(원문과 바이트 동일한지), 예외 발생 여부(예: 템플릿 변수 누락 오류). 결론: "치환 없이 전달되는 경로 = ○○" 또는 "없음".

**④ 도구 강제/구조화 출력** — (a) Spring AI Bedrock Converse 의 옵션 클래스(`BedrockChatOptions` 류)·소스 JAR 에서 **tool choice** 를 지정하는 API 가 있는지 확인(있으면 클래스/메서드명 기록, 없으면 "없음"). (b) 와이어 캡처에서 `toolConfig.tools[].toolSpec.inputSchema`(기존 `AiMissionGenerator.TOOL` 의 `input_schema` 를 그대로 옮긴 JSON)와 `toolConfig.toolChoice`(특정 도구 지정, 예: `{"tool":{"name":"submit_mission"}}`)가 요청에 실리는지. (c) 응답 스텁이 도구 사용 응답(`toolUse` 블록의 `input` 객체)을 돌려줄 때 Spring AI 가 이를 앱 코드가 읽을 수 있는 형태(도구 호출 인자 JSON)로 노출하는지, 자동 도구 실행(함수 호출 루프)이 개입해 서버 코드를 호출하려 하지 않는지(우리는 **도구를 실행하지 않고 인자만 받는다**) — "도구 실행 비활성/내부 실행 없이 인자만 얻기"가 가능한지가 핵심. (d) 대안으로 구조화 출력(`.entity(...)`)을 시도: 요청에 어떤 지시문이 덧붙는지(와이어 캡처)와, 응답 스텁이 JSON 문자열을 줄 때 파싱되는지. 결론: "A(도구 강제) 가능 / 불가능, B(구조화 출력) 가능 / 불가능, 권장 = ○".

**⑤ 프롬프트 캐시** — (a) 옵션/메시지 API 에 Bedrock 캐시 포인트(`cachePoint`) 또는 캐시 전략 옵션이 있는지 확인(클래스/메서드명). (b) 와이어 캡처에서 시스템 메시지 뒤에 `cachePoint` 블록이 실리는지. (c) 실제 효과(응답 `usage` 의 `cacheReadInputTokens`)는 AWS 호출이 있어야 하므로 자격증명이 없으면 **미검증**. 현재 구현(`cache_control`)과 동등한 기능이 가능한지 결론.

**⑥ 모델·리전·접근·쿼터·지연** — AWS 자격증명과 `HAWE_POC_BEDROCK_MODEL`/`AWS_REGION` 이 있을 때만: 샘플 입력으로 5회 호출해 성공 여부·지연(p50/p95)·토큰 사용량·오류 유형(접근 거부, 모델 없음, 스로틀링)을 기록. 없으면 **"미검증 — 사용자가 모델 접근 활성화 후 재실행 필요"** 로 쓰고, 재실행 방법(환경변수 이름, 실행 명령)을 보고서에 적는다. 모델 ID 를 추측해서 하드코딩하지 않는다.

## Files / Functions to Change

저장소 production 파일은 **하나도 바꾸지 않는다.**

| 위치 | 내용 |
|---|---|
| `.agent/poc-b12/pom.xml` (신규) | Boot 4.1.1 parent + 후보 Spring AI BOM + 스타터 + test 스타터 |
| `.agent/poc-b12/src/test/java/...` (신규) | 항목 ①~⑥ 검증 테스트(스텁 HTTP 서버 포함). 이름 예: `ContextWithoutAwsTests`, `BraceTemplatingTests`, `ToolChoiceWireTests`, `PromptCacheWireTests`, `LiveBedrockSmokeTests`(환경변수 있을 때만) |
| `.agent/poc-b12/app-copy/` (신규, 선택) | 저장소 복사본 + 스타터 추가 (공존 시험용) |
| `docs/BACKEND/MISSION_AI_SPRING_AI_BEDROCK.md` | §9 표 아래에 "PoC 결과 (2026-10-15 실행)" 절 추가: 항목별 판정(확인됨/막힘/미검증)과 핵심 근거, 확정된 Spring AI 버전·좌표·꺼짐 속성·호출 경로·도구 강제 방법·캐시 지원 여부, 미검증 항목의 재실행 방법. 다른 절은 바꾸지 않는다 |
| `.agent/report.md` | 상세 결과(아래 형식) |

## Implementation Steps

1. 후보 버전 조사: `spring-ai-parent` POM 으로 Boot/Framework/Jackson/AWS SDK 버전 확인(항목 ① a). 표로 정리.
2. `.agent/poc-b12/pom.xml` 작성, `compile` + 빈 컨텍스트 로드 테스트(① b·c, ② a). 막히면 다른 후보 버전으로 반복, 어떤 버전에서 어떤 오류가 났는지 기록.
3. 꺼짐 조건 탐색(② b), 자격증명 없는 상태에서의 기동·첫 호출 예외 타입(② d).
4. 스텁 HTTP 서버 구축 + SDK `endpointOverride`/클라이언트 주입 경로 확보(Root Cause 방법 2). 이 단계가 막히면 대체 캡처 방법(자동 구성 `BedrockRuntimeClient` 빈 교체, `ChatModel` 스텁)을 쓰고 그 한계를 기록.
5. 항목 ③ 테스트(경로 3종 × 중괄호 문자열), ④ 테스트(도구 강제·인자 수신·구조화 출력), ⑤ 테스트(캐시 포인트).
6. 임시 복사본에서 공존 시험(② c): BOM·스타터 추가 후 `HaweApplicationTests`(필요하면 전체 `clean test`) 실행, 의존성 트리 전후 비교(`dependency:tree`) 요약.
7. 항목 ⑥ 라이브 스모크 테스트 코드 작성(환경변수 없으면 skip 확인). 자격증명이 없으므로 skip 결과만 기록.
8. 설계 문서 §9 에 PoC 결과 절 추가, `.agent/report.md` 작성.

## Constraints

- **저장소의 `pom.xml`, `src/**`, `application.properties`, 스키마를 수정하지 않는다.** 모든 코드는 `.agent/poc-b12/` 안(또는 임시 복사본)에만 둔다. 예외: `docs/BACKEND/MISSION_AI_SPRING_AI_BEDROCK.md` 에 결과 절 추가.
- 거짓 통과 금지: AWS 가 필요한 항목은 근거 없이 "확인됨"으로 쓰지 않는다. 와이어 캡처로 확인한 "요청 형태"와 AWS 로 확인한 "실제 동작"을 구분해서 쓴다.
- AWS 자격증명·키·토큰을 파일, 로그, 보고서, 커밋 대상에 남기지 않는다. 스텁에는 더미 값만 쓴다.
- 실제 사용자·그룹 데이터를 어디에도 쓰지 않는다. 샘플 입력은 가짜 데이터.
- 외부 네트워크는 Maven Central 접속 외에 사용하지 않는다(라이브 스모크는 환경변수가 있을 때만 AWS 접속).
- 커밋/푸시/브랜치/스태시 금지. `CLAUDE.md`/`AGENTS.md`/`ml/`/`android`/`ios` 수정 금지. 로컬 DB 스키마·데이터를 바꾸지 않는다(복사본 테스트가 기존 DB 에 접속하지만 Flyway 는 이미 적용 상태).
- 이 PoC 의 결론으로 B12 구현을 이어서 진행하지 않는다. 결과 보고에서 멈춘다.

## Edge Cases

- 가장 최신 버전이 Boot 4.1.1 과 안 맞을 수 있다 → 여러 후보를 시도하고 각각의 결과를 표로 남긴다. 맞는 버전이 없으면 "막힘"이며 우회안은 AWS SDK `bedrockruntime` 직접 호출(Converse API).
- Spring AI 가 Jackson 2 와 3 를 모두 끌어와 직렬화 설정이 어긋날 수 있다 → 와이어 캡처 JSON 이 정상(한글·중괄호 보존, 이스케이프 정상)인지 눈으로 확인.
- 한글/이모지/개행/따옴표가 포함된 프롬프트가 UTF-8 로 정확히 전달되는지(와이어 JSON 의 문자열이 원문과 같은지).
- 모델/옵션 클래스 이름이 버전마다 다르다 → 소스 JAR 또는 `javap` 로 실제 존재 여부를 확인하고 추측으로 쓰지 않는다.
- 스텁 응답 형식이 SDK 가 기대하는 Converse 응답과 어긋나면 파싱 오류가 난다 → AWS 문서의 Converse 응답 구조(`output.message.content[]`, `stopReason`, `usage`)를 따른다.
- 첫 호출이 자격증명 해석 단계에서 오래 걸리거나(인스턴스 메타데이터 조회 타임아웃) 테스트가 매달릴 수 있다 → 테스트에 타임아웃을 걸고 환경변수 `AWS_EC2_METADATA_DISABLED=true` 를 설정한 상태에서 시험해 그 영향도 기록.
- 임시 복사본이 로컬 DB 를 공유한다 → 쓰기 테스트가 기존 데이터를 건드릴 수 있으니 복사본에서는 **전체 테스트 대신 `HaweApplicationTests`(컨텍스트 로드) 위주**로 실행하고, 전체를 돌리면 보고서에 명시(기존 테스트는 자체 데이터를 만들고 지운다).

## Regression Risks

저장소를 수정하지 않으므로 회귀 위험은 없다. 단 `.agent/poc-b12/` 의 `target/`, 임시 복사본이 디스크를 차지하고(수백 MB), 복사본 테스트가 로컬 DB 에 접속한다. 포트(8080 등)를 점유하는 서버를 띄운 경우 종료한다.

## Tests Required

(PoC 용 시험이며 저장소 테스트 스위트에는 추가하지 않는다.)

- ① 컴파일 + 컨텍스트 로드 성공 로그.
- ② 자격증명 없는 기동 성공/실패와 꺼짐 방법, 임시 복사본에서 `HaweApplicationTests` 결과.
- ③ 경로 3종 × 중괄호 문자열 → 전달된 텍스트와 원문 일치 여부 표.
- ④ 요청 JSON 의 `toolConfig`/`toolChoice`, 응답의 `toolUse` 인자 수신, 구조화 출력 요청 모양.
- ⑤ 요청 JSON 의 `cachePoint`.
- ⑥ 환경변수 없을 때 skip 되는지(실패가 아니라 skipped).

## Validation Steps

1. `git status` — 저장소에서 변경된 production 파일이 없는지 확인(변경이 있으면 `docs/BACKEND/MISSION_AI_SPRING_AI_BEDROCK.md` 한 파일뿐이어야 함. 이 PoC 이전부터 있던 B4/B11 미커밋 변경은 건드리지 않았는지만 확인).
2. `rg -n "AKIA|aws_secret|accessKey" .agent/poc-b12 docs/BACKEND/MISSION_AI_SPRING_AI_BEDROCK.md` — 실제 키로 보이는 문자열 없음.
3. 보고서(`.agent/report.md`)에 항목 ①~⑥ 표(판정·근거·실행 명령·재현 방법)와 최종 권고 한 줄: **"Spring AI 로 진행 가능 / 불가(→ AWS SDK 직접 호출 권장) / 조건부(조건 나열)"**.
4. 미검증 항목은 `HAWE_POC_BEDROCK_MODEL`, `AWS_REGION`, 자격증명 설정 후 재실행하는 명령을 그대로 적는다.
5. 설계 문서 §9 의 "막히면" 열과 비교해 어떤 대안을 택해야 하는지 명시.

## Potential Merge Conflicts

저장소 코드 변경이 없어 충돌 없음. `docs/BACKEND/MISSION_AI_SPRING_AI_BEDROCK.md` 는 이 세션에서 만든 새 파일이라 다른 작업과 겹치지 않는다. 작업 트리에 B4·B11 미커밋 변경이 있으니 `git add`/`stash`/`checkout` 등 어떤 git 변경 명령도 쓰지 않는다.
