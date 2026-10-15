# B12 단계 0 — Spring AI + Bedrock PoC 보고 (2026-10-15)

## 결과와 권고

**조건부 진행.** Spring AI 2.0.1은 이 프로젝트의 Boot 4.1.1/JDK 25와 함께 컴파일·기동하고, 중괄호를 보존하는 호출 경로·구조화 출력 요청·시스템 프롬프트 캐시 포인트를 제공한다. 다만 특정 `submit_mission` 도구를 강제하는 `toolChoice`는 Spring AI Bedrock Converse 경로에 없다. 강제 도구 계약을 유지해야 한다면 설계 문서 §11의 AWS SDK `bedrockruntime` 직접 호출을 선택한다. 구조화 출력으로 대체한다면 실제 모델의 지원·응답 안정성·캐시 효과·지연을 AWS 계정에서 검증한 뒤 전환을 결정한다. Production 전환은 하지 않았다.

| # | 판정 | 근거와 재현 |
|---|---|---|
| 1. 버전/좌표 | **확인됨** | Maven Central의 `spring-ai-starter-model-bedrock-converse:2.0.1` POM은 Boot 4.1.1을 직접 의존한다. `.agent/poc-b12/pom.xml`의 BOM + 스타터로 JDK 25 `clean test` 성공. `dependency-tree.txt`: Jackson 3.1.5, AWS SDK 2.51.2, Netty 4.2.17.Final. 배포된 `spring-ai-parent` POM에는 Boot/Framework/Jackson/AWS 버전 속성이 없어 스타터 POM과 해결된 트리로 확인했다. |
| 2. 자격증명 없는 기동/공존 | **확인됨** | 기본 PoC 컨텍스트에서 `ChatModel` 1개 생성, `spring.ai.model.chat=none`에서는 0개. AWS 없이 최초 호출은 `software.amazon.awssdk.core.exception.SdkClientException`. 원본 `src/`·`pom.xml`을 건드리지 않은 `.agent/poc-b12/app-copy/`의 `HaweApplicationTests`가 스타터 켜짐·꺼짐 모두 통과. 앱 복사본 트리에는 기존 Jackson 2.21.5와 Spring AI Jackson 3.1.5가 공존하므로 전환 시 직렬화 경계 테스트가 필요하다. |
| 3. 중괄호 | **확인됨** | `BedrockWireTests`가 로컬 HTTP 스텁의 `system`/`messages[].content[].text`를 원문과 비교. `ChatClient.system(String).user(String)`, 매개변수 없는 `system(spec -> spec.text(...))`, `ChatModel.call(new Prompt(...))` 모두 `{변수}`, `{{x}}`, `{0}`, 중첩 JSON, 한글·이모지·개행을 보존. `.param(...)`을 사용한 템플릿 경로는 `IllegalArgumentException`으로 HTTP 전에 실패. 안전한 경로는 직접 `Prompt` 또는 매개변수 없는 문자열 호출. 샘플: `wire-samples/braces.json`. |
| 4. 도구 강제/구조화 출력 | **막힘(도구 강제), 확인됨(요청·스텁 파싱), 실제 모델 미검증** | `BedrockChatOptions`에 특정 도구 선택 설정이 없고 `BedrockProxyChatModel.createRequest`는 `ToolConfiguration.builder().tools(...)`만 호출한다. `wire-samples/tool.json`에는 기존 스키마 모양의 `toolSpec.inputSchema`가 있지만 `toolChoice`가 없다. SDK 2.51.2에는 `SpecificToolChoice`가 있다. 스텁 `toolUse`의 인자는 `ChatResponse.getResults()`에서 읽혔고 `ChatModel.call`은 콜백을 실행하지 않았다. `ChatClient`에서는 `AdvisorParams.toolCallingAdvisorAutoRegister(false)`로 자동 실행을 끄는 테스트가 통과했다. `.entity(..., useProviderStructuredOutput())`는 `outputConfig.textFormat.type=json_schema`를 전송하고 스텁 JSON을 객체로 변환했다(`wire-samples/structured.json`). 실제 모델이 이를 지원하고 준수하는지는 미검증. |
| 5. 캐시 포인트 | **확인됨(요청), 미검증(효과)** | `BedrockCacheOptions`의 `SYSTEM_ONLY` 설정으로 시스템 텍스트 뒤에 독립 `cachePoint:{type:"default"}` 블록 전송(`wire-samples/cache.json`). 실제 캐시 히트·토큰 절감은 AWS 호출 필요. |
| 6. 모델/리전/권한/쿼터/지연 | **미검증** | `HAWE_POC_BEDROCK_MODEL`, `AWS_REGION`, AWS 자격증명이 없어 `LiveBedrockSmokeTests` 1개 정상 skip. 모델 ID는 추측하지 않았다. |

## 변경 파일과 실행 결과

- 저장소 production 코드·설정·스키마는 변경하지 않았다. `.agent/poc-b12/`에 독립 `pom.xml`, 로컬 와이어 스텁 테스트, 선택적 AWS 라이브 테스트, 요청 JSON 4개, 의존성 트리, 앱 복사본을 만들었다. `docs/BACKEND/MISSION_AI_SPRING_AI_BEDROCK.md` §9에 결과만 추가했다.
- `.\mvnw.cmd -f .agent/poc-b12/pom.xml clean test -q` (`AWS_EC2_METADATA_DISABLED=true`): **9개 중 8개 통과, 1개 skip, 실패/오류 0**. `context.log`, `poc-test.log` 및 Surefire XML에 결과가 있다.
- `.\mvnw.cmd -f .agent/poc-b12/app-copy/pom.xml '-Dtest=HaweApplicationTests' test`: 켜짐 통과. `'-Dspring.ai.model.chat=none'` 추가 실행도 통과. 두 실행 모두 로컬 MariaDB/Flyway 기존 스키마로 컨텍스트 로드만 수행했다.
- `git diff --check`: 오류 없음. 별도 lint/typecheck/build 게이트는 PoC 프로젝트에 없으며 Maven compile/test가 Java 컴파일을 포함한다.

## 재실행과 한계

PowerShell에서 라이브 확인을 하려면 `HAWE_POC_BEDROCK_MODEL`, `AWS_REGION`과 표준 AWS 자격증명을 설정한 뒤 `.\mvnw.cmd -f .agent/poc-b12/pom.xml '-Dtest=LiveBedrockSmokeTests' test`를 실행한다. 테스트는 가짜 샘플 5회만 보내고 지연(p50/p95)·토큰 사용량을 출력한다. 캐시 효과와 출력 안정성은 별도 반복 실험이 필요하다. 실제 모델·리전·권한·쿼터·비용은 이번 PC에서 검증하지 못했다.

`.agent/poc-b12/app-copy/.env`는 복사본의 로컬 DB 접속을 위한 파일이며 `.agent/` 전체가 gitignore 대상이다. AWS 키·토큰은 파일/로그/보고서에 기록하지 않았다. 작업 트리의 기존 B4/B11 미커밋 변경, `.agent/plan.md`, `.agent/review.md`, `docs/해야될거/**`는 건드리지 않았다. 커밋/푸시는 하지 않았다.
