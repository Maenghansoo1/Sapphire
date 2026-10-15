# AI 미션 생성 재구성 계획 — Spring Boot + Spring AI + AWS Bedrock

작성: 2026-10-15 · 상태: **계획(미착수)** · 담당 영역: 미션/AI (B8 확장)
이 문서는 현재 구현(B8, Claude API 직접 호출)을 Spring AI + AWS Bedrock 구조로 바꾸는 계획이다. 구현 전에 §9 의 확인 항목과 §10 의 결정이 필요하다. 코드를 아직 바꾸지 않았다.

> 이 문서의 Spring AI·Bedrock 관련 클래스/속성 이름은 작성자가 알고 있는 범위의 것이며, **Spring Boot 4.1.1 + 선택한 Spring AI 버전에서 그대로 맞는지는 확인하지 않았다.** §9 의 사전 검증(PoC)을 통과하기 전에는 확정으로 보지 않는다.

## 1. 목표

1. 미션 문장 생성(문장·카테고리·빈칸 값)을 **Spring AI 의 `ChatClient`/`ChatModel` + AWS Bedrock(Converse)** 로 호출한다.
2. 인증은 **IAM 역할/기본 자격증명 체인**을 쓰고 API 키를 코드·설정에 두지 않는다.
3. **바뀌지 않는 것**: AI 는 미션 생성만 한다(제출 평가 없음), 틀(template)·대상은 서버가 정한다, 결과는 서버가 다시 검증한다, 실패하면 고정 문장으로 폴백해 발행을 막지 않는다, LLM 에는 개인 식별 정보를 보내지 않는다.

## 2. 현재 구조 (코드 확인)

```
MissionPublisher
  └ MissionGenerator (interface)            ← 발행기가 쓰는 유일한 경계
      └ FallbackMissionGenerator (@Primary) ← AI 불가/실패/랜덤군이면 PlaceholderMissionGenerator
          └ AiMissionGenerator              ← 입력 JSON 조립, 재시도, 서버 재검증
              └ AnthropicClient.callTool()  ← RestClient 로 Anthropic Messages API 직접 호출, 도구 submit_mission 강제
```

- `AiMissionGenerator.input(...)`: 날짜·요일, 그룹 관계, 틀(코드·이름·빈칸), 대상 유무, 최근 미션, 카테고리 목록, 특별한 날, 그룹 기록(미션 단위 집계)만 JSON 으로 보낸다. 이름·그룹 이름·답·댓글은 보내지 않는다.
- 시스템 프롬프트 = 팀원 프롬프트 파일들(`hawe.mission.ai.prompt-paths`) + 서버 안내(`mission-ai/server-guide.txt`). 팀원 프롬프트는 저장소에 없고(깃 제외) 없으면 `mission-ai/default-prompt.txt`.
- 결과 검증: `GeneratedMissionValidator`(길이·카테고리·빈칸·`[대상]`·금칙어·최근 중복). 실패는 `AiGenerationException`, 최대 `max-attempts` 회 재시도, 총 대기 `total-timeout`(발행 트랜잭션 안에서 호출하므로 짧게).
- 설정: `MissionAiProperties`(`hawe.mission.ai.*`: `enabled`, `api-key`, `model`, `max-tokens`, `temperature`, 타임아웃, 프롬프트 경로 등). `usable() = enabled && apiKey 있음`.
- 실험(B9): 랜덤군은 AI 가 가능해도 고정 문장. `GenerationSource`(AI / AI_FALLBACK / PLACEHOLDER)를 `missions` 에 기록.

## 3. 목표 구조

```
MissionPublisher
  └ MissionGenerator (interface, 변경 없음)
      └ FallbackMissionGenerator (@Primary, 변경 없음)
          └ AiMissionGenerator            ← 입력 조립·재시도·검증 로직 유지, 호출부만 교체
              └ MissionLlm (신규 작은 인터페이스)   ← 이 한 줄이 교체 지점
                  └ SpringAiMissionLlm    ← ChatClient(Bedrock Converse) 호출
```

핵심은 `AiMissionGenerator` 와 그 아래 로직을 **그대로 두고** `AnthropicClient.callTool(system, user, tool)` 한 곳만 `MissionLlm` 으로 추상화해 바꾸는 것이다. 이렇게 하면 발행·폴백·실험·검증·테스트 대부분이 변하지 않는다.

- `MissionLlm`: `Map<String,Object> generate(String systemPrompt, String userMessage)` 정도의 좁은 시그니처(구현이 도구 호출이든 구조화 출력이든 같은 `Map`(content/category/params)을 돌려준다). 실패는 `AiGenerationException` 으로 통일(기존 폴백이 그대로 동작).
- `SpringAiMissionLlm`: `ChatClient` 를 주입받아 호출. 응답을 `Map` 으로 변환.
- `AnthropicClient`: 전환 기간에는 남겨 두고 `hawe.mission.ai.provider=anthropic|bedrock` 로 선택하거나, 전환 확정 후 제거(§10 결정 2).

## 4. 작업 범위

| 영역 | 변경 |
|---|---|
| `pom.xml` | Spring AI BOM(`dependencyManagement`) + Bedrock Converse 스타터. **Boot 4.1.1 을 지원하는 Spring AI 버전** 확인 후 속성(`<spring-ai.version>`)으로 명시. 다른 의존성 변경 금지 |
| `mission/service` | `MissionLlm` 인터페이스, `SpringAiMissionLlm` 구현, `AiMissionGenerator` 가 `AnthropicClient` 대신 `MissionLlm` 사용 |
| `global/config` | `MissionAiProperties` 에서 `apiKey`/`baseUrl` 의존 제거 또는 provider 별 분리, `usable()` 재정의(키 대신 Bedrock 사용 가능 여부 — 자격증명은 코드에서 확인 불가이므로 `enabled` + 모델 ID 존재로 판단하고 호출 실패는 폴백) |
| `application.properties` | `spring.ai.bedrock.aws.region`, `spring.ai.bedrock.converse.chat.options.model`(모델 ID), `max-tokens`, `temperature`. 로컬/CI 에서 AWS 자격증명이 없어도 컨텍스트가 떠야 한다(§7) |
| 테스트 | `AiMissionGeneratorTests`(호출부 목 교체), `MissionLlm` 단위 테스트, 컨텍스트 로드 테스트, 폴백 동작 테스트 |
| 문서 | 이 문서, `CONTRACT_DECISIONS.md` #5(제공자 결정 변경), `MISSIONS_API.md` §6-2·§8, `PROJECT_OVERVIEW.md` §6, `docs/해야될거/00-README.md` 결정 표 #5, `CLAUDE.md`의 "Spring AI 가 중괄호를 변수로 읽는다" 문단은 **이미 맞는 제약**이므로 유지 |

바꾸지 않는 것: `FallbackMissionGenerator`, `PlaceholderMissionGenerator`, `GeneratedMissionValidator`, `GroupContextBuilder`, `MissionPublisher` 트랜잭션 구조, B9 실험, 틀 선택 규칙.

## 5. 구현 접근

### 5-1. 호출 방식 (두 갈래, PoC 로 결정)

| 방식 | 장점 | 확인 필요 |
|---|---|---|
| **A. 도구 호출 강제** (현재와 같음: `submit_mission` 을 반드시 호출) | 응답 형식 안정, 기존 도구 스키마 재사용 | Spring AI Bedrock Converse 가 **특정 도구를 강제**(tool choice)하는 옵션을 제공하는지 |
| **B. 구조화 출력** (`ChatClient...entity(MissionDto.class)`) | 코드 단순, 스프링 AI 관용 방식 | 프롬프트에 형식 지시가 덧붙는 방식이라 **서버 안내 문서·팀원 프롬프트와 충돌 여부**, 응답 안정성 |

권장: A 가 되면 A. 안 되면 B 로 가되 `GeneratedMissionValidator` 와 재시도를 그대로 둔다(이미 형식 오류를 걸러냄).

### 5-2. 프롬프트의 중괄호 `{ }` 문제 (가장 중요한 함정)

- `ChatClient` 의 `.system(String)` / `.user(String)` 은 문자열을 **템플릿으로 렌더링**할 수 있고, 이 경우 `{ }` 가 변수로 읽혀 팀원 프롬프트·JSON 입력(`objectMapper.writeValueAsString(input)` 은 중괄호 투성이)이 깨진다.
- 대응: 템플릿 렌더링을 거치지 않는 경로(메시지 객체 `SystemMessage`/`UserMessage` 를 직접 담은 `Prompt`)로 호출한다. **사용자 메시지(JSON)와 시스템 프롬프트 둘 다** 중괄호가 있는 문자열로 테스트해 변수 치환이 일어나지 않음을 확인한다(§8 필수 테스트).
- 프롬프트에 중괄호를 쓰지 않는 기존 규칙(팀원 프롬프트 계약)은 호환을 위해 유지한다.

### 5-3. 프롬프트 캐시

- 현재는 `cache_control` 로 긴 시스템 프롬프트를 캐시해 두 번째 호출부터 비용을 줄인다. Bedrock Converse 의 캐시 포인트는 **모델·리전·Spring AI 버전에 따라 지원이 다르다.** 지원되지 않으면 호출당 입력 토큰 비용이 늘어난다 → PoC 에서 확인하고 §10 에 결정한다. 미션 발행량(하루 그룹 수 × 슬롯 수)으로 월 비용을 먼저 추정한다.

### 5-4. 인증·권한·리전

- 서버(EC2/ECS 등)에 IAM 역할을 붙이고 Bedrock 호출 권한만 최소로 부여한다(`bedrock:InvokeModel`, `bedrock:Converse` 계열 — 정확한 액션 이름은 AWS 문서 확인). 로컬은 `aws configure`/SSO 프로필. 키를 `.env` 나 저장소에 두지 않는다.
- Bedrock 콘솔에서 사용할 Claude 모델의 **모델 접근을 켜야** 한다. 모델 ID 는 Anthropic 직접 호출의 ID 와 **다르며**, 리전별 inference profile ID 를 써야 하는 모델이 있다(콘솔에서 확인). 서울 리전 제공 여부 또는 타 리전 사용 시 지연·데이터 위치를 확인한다.

### 5-5. 타임아웃·재시도

- 호출은 **발행 트랜잭션 안**이다(`totalTimeout` 40초 기본). Bedrock 클라이언트의 연결·읽기 타임아웃과 SDK 자체 재시도 횟수를 명시해 `readTimeout × 시도 수` 가 `totalTimeout` 을 넘지 않게 한다(SDK 재시도와 `AiMissionGenerator` 재시도가 곱해지지 않게 한쪽만 쓰거나 SDK 재시도를 0~1 로).
- 스로틀링(429 계열)·모델 접근 오류·타임아웃은 모두 `AiGenerationException` 으로 변환 → 폴백.

## 6. 개인정보·안전

- LLM 으로 보내는 정보 범위는 **현재와 동일**(미션 단위 집계·관계·날짜·최근 미션 문장·기념일). 사용자 글·댓글·사진·닉네임·그룹 이름은 보내지 않는다. Bedrock 으로 바꿔도 이 원칙을 바꾸지 않는다. 데이터가 AWS 계정 안에서 처리된다는 점은 개인정보 처리방침 문구에 반영할 수 있다(문구는 사용자가 확정).
- 모델 호출 로그에 프롬프트·응답 본문·자격증명을 남기지 않는다(`FallbackMissionGenerator` 처럼 사유 요약만).

## 7. 로컬·테스트 환경에서의 기동

- Spring AI 스타터는 `ChatModel` 빈을 자동 구성하며 AWS 자격증명/리전이 없으면 **컨텍스트 기동이 실패**할 수 있다. 이 프로젝트의 테스트(`HaweApplicationTests` 등 `@SpringBootTest`)가 AWS 없이 통과해야 한다.
- 대응(PoC 에서 확인): `hawe.mission.ai.enabled=false`(기본)일 때 Bedrock 빈이 만들어지지 않거나 지연 초기화되도록 조건(`@ConditionalOnProperty`, `@Lazy`, 스타터의 자동 구성 제외 등)을 둔다. `SpringAiMissionLlm` 은 `ObjectProvider<ChatClient>` 로 받아 꺼져 있으면 사용하지 않는다.
- 이 프로젝트는 Boot 4 에서 `mybatis-spring-boot-starter` 자동 구성 순서 문제로 직접 구성한 전례가 있다(`MyBatisConfig`). Spring AI 자동 구성도 같은 종류의 순서/호환 문제가 없는지 컨텍스트 로드 테스트로 확인한다.

## 8. 테스트 계획

- **필수(함정 방지)**: 중괄호 `{ }` 가 들어 있는 시스템 프롬프트와 JSON 사용자 메시지가 **치환 없이 그대로** 모델 호출 직전 메시지에 담기는지(`ChatModel` 목에 전달된 `Prompt` 검사).
- `SpringAiMissionLlm` 단위 테스트(`ChatModel`/`ChatClient` 목): 정상 응답 → `Map` 변환, 형식 오류/빈 응답/예외 → `AiGenerationException`, 스로틀링·타임아웃 변환.
- `AiMissionGeneratorTests`: 호출부가 `MissionLlm` 목으로 바뀌어도 기존 시나리오(재시도, 총 제한시간, 검증 실패 후 재시도, 로그) 유지.
- `FallbackMissionGeneratorTests`: 변경 없이 통과해야 한다.
- 컨텍스트 로드: AWS 자격증명 없이 `@SpringBootTest` 통과, `enabled=true` 인데 호출 실패 시 폴백.
- 수동 스모크(필수, 자동화 불가): 실제 Bedrock 한 번 호출해 틀 7종(`PHOTO_*`, `DRAW_*`, `TEXT_*`)별로 검증기를 통과하는 결과가 나오는지, 지연 시간, 토큰 비용을 기록한다.
- 기존 전체 테스트: `.\mvnw.cmd clean test -q`.

## 9. 사전 검증(PoC) — 구현 전에 반드시

작은 별도 브랜치/테스트로 아래를 확인하고 결과를 이 문서에 기록한다. 하나라도 막히면 §11 대안으로 간다.

| # | 확인 | 막히면 |
|---|---|---|
| 1 | Spring Boot 4.1.1 에서 동작하는 Spring AI 버전과 Bedrock Converse 스타터 좌표 | AWS SDK 직접 호출(§11) |
| 2 | 컨텍스트 로드가 AWS 자격증명 없이 성공(꺼짐 조건) | 조건부 구성/자동 구성 제외 |
| 3 | 중괄호가 템플릿으로 치환되지 않는 호출 경로 | AWS SDK 직접 호출 |
| 4 | 특정 도구 강제 또는 신뢰할 수 있는 구조화 출력 | 구조화 출력 + 재시도/검증으로 보완 |
| 5 | 프롬프트 캐시 포인트 지원 여부와 비용 영향 | 비용 추정으로 수용 여부 결정 |
| 6 | 사용할 모델 ID·리전·접근 권한·쿼터·지연 | 모델/리전 변경 |

### PoC 결과 (2026-10-15 실행)

저장소 코드는 변경하지 않고 `.agent/poc-b12/`의 별도 Maven 프로젝트와 앱 복사본으로 시험했다. 로컬 HTTP 스텁에 더미 자격증명만 사용했으며, 실제 AWS 호출은 하지 않았다. 요청 본문 샘플은 `.agent/poc-b12/wire-samples/{braces,tool,cache,structured}.json`에 있다.

| # | 판정 | 실행 근거 |
|---|---|---|
| 1 | **확인됨** | `org.springframework.ai:spring-ai-bom:2.0.1` + `spring-ai-starter-model-bedrock-converse:2.0.1`이 Boot 4.1.1/JDK 25에서 컴파일·컨텍스트 로드된다. 스타터의 Maven Central POM은 Boot 4.1.1을 직접 의존한다. 의존성 트리: Jackson 3.1.5, AWS SDK 2.51.2, Netty 4.2.17.Final. 기존 앱 복사본에는 Jackson 2.21.5도 공존한다. |
| 2 | **확인됨** | 자격증명·리전 환경변수가 없는 상태에서도 기본 컨텍스트가 뜨고 `ChatModel` 빈이 1개 생성됐다. `spring.ai.model.chat=none`이면 빈이 0개다. 첫 호출은 `SdkClientException`으로 실패한다. 스타터를 추가한 앱 복사본의 `HaweApplicationTests`는 켜짐·꺼짐 모두 통과했다. 기동 중 Bedrock 요청은 없었다. |
| 3 | **확인됨** | `ChatClient.prompt().system(String).user(String)`, 매개변수 없는 `system(spec -> spec.text(...))`, `ChatModel.call(new Prompt(SystemMessage, UserMessage))` 세 경로 모두 중괄호, JSON, 한글, 이모지, 개행을 원문 그대로 HTTP 요청에 보냈다. 반면 `system(spec -> spec.text(...).param(...))`은 `IllegalArgumentException`으로 HTTP 전 단계에서 실패했다. 안전한 경로는 직접 `Prompt` 또는 템플릿 매개변수 없는 문자열 호출이다. |
| 4 | **막힘(도구 강제), 확인됨(요청·스텁 파싱), 실제 모델 미검증** | `BedrockChatOptions`에 특정 도구 선택 API가 없고 `BedrockProxyChatModel.createRequest`는 `ToolConfiguration.builder().tools(...)`만 호출한다. 캡처된 `toolConfig.tools[0].toolSpec.inputSchema`는 정상이나 `toolChoice`는 없다. 스텁 `toolUse` 인자는 `ChatResponse.getResults()`의 도구 호출에서 읽혔다. `ChatModel.call`은 도구를 실행하지 않았고 `ChatClient`는 `AdvisorParams.toolCallingAdvisorAutoRegister(false)`로 자동 실행을 끌 수 있다. `.entity(..., useProviderStructuredOutput())`는 `outputConfig.textFormat.type=json_schema`를 보내고 스텁 JSON을 객체로 변환했다. 실제 Bedrock 모델의 강제/구조화 출력 신뢰성은 미검증이다. 특정 도구 강제가 필수면 §11의 AWS SDK 직접 호출(`SpecificToolChoice`)을 택한다. |
| 5 | **확인됨(요청), 미검증(효과)** | `BedrockCacheOptions.builder().strategy(SYSTEM_ONLY)`가 시스템 텍스트 뒤에 별도 `cachePoint` 블록을 보냈다. 실제 캐시 히트·토큰 절감은 AWS 자격증명과 지원 모델이 없어 미검증이다. |
| 6 | **미검증** | `HAWE_POC_BEDROCK_MODEL`, `AWS_REGION`, 표준 AWS 자격증명이 없어 라이브 테스트 1개가 정상 skip됐다. 모델 ID·접근 권한·쿼터·지연·캐시 효과는 사용자 계정에서 확인해야 한다. |

재실행(PowerShell): `$env:AWS_EC2_METADATA_DISABLED='true'`를 설정하고 `.\mvnw.cmd -f .agent/poc-b12/pom.xml clean test`(로컬 9개 중 8개 통과, 라이브 1개 skip). 라이브는 `HAWE_POC_BEDROCK_MODEL`, `AWS_REGION`, 표준 AWS 자격증명을 설정한 뒤 `.\mvnw.cmd -f .agent/poc-b12/pom.xml '-Dtest=LiveBedrockSmokeTests' test`를 실행한다. 가짜 입력 5회에 대해 지연과 토큰 사용량을 출력한다. 앱 공존 시험은 `.agent/poc-b12/app-copy/pom.xml`에서 `'-Dtest=HaweApplicationTests' test`로 재현한다.

**권고: 조건부.** 강제 `submit_mission` 계약을 유지해야 하면 §11의 AWS SDK 직접 호출을 선택한다. 구조화 출력으로 대체할 수 있다면 Spring AI 2.0.1을 후보로 두되, 실제 모델의 JSON schema 지원·캐시 효과·지연·비용을 AWS 계정에서 검증한 뒤 진행 여부를 결정한다. 이번 PoC 결과만으로 production 전환은 하지 않는다.

## 10. 결정이 필요한 것

| # | 질문 | 권장 |
|---|---|---|
| 1 | Bedrock 모델 ID 와 리전 | 콘솔에서 접근 가능한 Claude 모델 중 비용·지연 균형이 좋은 것(기존 기본값은 haiku 급). PoC 후 확정 |
| 2 | `AnthropicClient` 를 남길지 | 전환 검증이 끝날 때까지 `provider` 설정으로 병행, 확정 후 제거 |
| 3 | 호출 방식 A(도구 강제) vs B(구조화 출력) | PoC 4번 결과에 따라 |
| 4 | 프롬프트 캐시 미지원 시 | 호출량 기반 비용 확인 후 수용 또는 시스템 프롬프트 축소 |
| 5 | 이 구조를 미션 생성 외(예: 인사이트 서술)로 넓힐지 | 이번 범위 아님 — 미션 생성만 |

## 11. 대안 (Spring AI 가 막힐 때)

AWS SDK for Java v2 `bedrockruntime` 의 **Converse API 를 직접 호출**하는 `BedrockMissionLlm` 을 `MissionLlm` 구현으로 둔다. 도구 강제(`toolChoice`)와 캐시 포인트를 API 가 명시적으로 지원하고 중괄호 문제도 없다. 이 문서의 `MissionLlm` 경계 덕분에 Spring AI 구현과 교체 가능하다.

## 12. 단계

1. PoC(§9) → 결과 기록, §10 결정.
2. `MissionLlm` 추출 + `AiMissionGenerator` 호출부 교체(동작 불변 리팩터링, 기존 테스트 통과).
3. `SpringAiMissionLlm` + 설정 + 의존성 + 꺼짐 조건.
4. 테스트 추가, 전체 테스트.
5. 실제 Bedrock 스모크(틀 7종), 비용·지연 기록.
6. 문서·결정 표 갱신, 운영 IAM 권한·모델 접근 설정(코드 밖, 사용자).

## 13. 위험

- Spring AI 와 Boot 4 호환(§9-1), 자동 구성 기동 실패(§7), 중괄호 치환(§5-2), 도구 강제 불가로 응답 안정성 저하(§5-1), 캐시 미지원으로 비용 증가(§5-3), SDK/서비스 재시도 곱셈으로 발행 트랜잭션 지연(§5-5), 운영 권한·모델 접근 미설정으로 항상 폴백만 동작(코드로는 알아차리기 어려움 → 폴백 비율을 `generation_source` 로 모니터링).

## 14. 병렬 작업과의 겹침

`MissionAiProperties`, `AiMissionGenerator`, `AnthropicClient`, `application.properties`, `pom.xml`, 결정 문서들. 미션/AI 영역은 우준(B8/B9)이 맡았으므로 시작 전에 알리고 같은 파일을 동시에 고치지 않는다. 팀원 프롬프트는 이 저장소에 없으므로 만들거나 수정하지 않는다.
