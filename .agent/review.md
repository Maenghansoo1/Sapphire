# B12 단계 0 (Spring AI + Bedrock PoC) 리뷰

대상: `.agent/plan.md`(PoC 계획), `.agent/report.md`, `.agent/poc-b12/`, `docs/BACKEND/MISSION_AI_SPRING_AI_BEDROCK.md` §9 추가분, 작업 트리.
PoC 테스트(9개 중 8 통과 1 skip)와 앱 복사본 `HaweApplicationTests` 는 직접 재실행하지 않았다. 보고서의 핵심 주장 중 직접 대조 가능한 것은 대조했다.

## 직접 대조한 것 (모두 일치)

- **저장소 production 코드 불변**: `pom.xml` diff 는 B11 의 Lucene Nori 속성·의존성뿐이고 Spring AI 관련 줄이 없다. `src/main` 변경은 B4/B11 의 기존 미커밋분이며 PoC 가 추가한 것으로 보이는 변경 없음. `application.properties`/스키마 불변.
- **좌표·호환 근거**: Maven Central 의 `spring-ai-starter-model-bedrock-converse:2.0.1` POM 이 `spring-boot-starter:4.1.1` 에 직접 의존함을 확인(보고서와 일치). 보고서가 "배포된 `spring-ai-parent` POM 에는 Boot 버전 속성이 없어 스타터 POM 과 해결된 트리로 확인했다"고 한계를 밝힌 점도 적절.
- **도구 강제 불가 결론**: 소스 JAR(`spring-ai-bedrock-converse-sources.jar`)에서 `toolChoice`/`ToolChoice` 문자열이 어디에도 없고, `BedrockProxyChatModel` 의 `ToolConfiguration.builder().tools(bedrockTools).build()` 한 곳만 확인됨 → "특정 도구 강제 없음" 판정은 근거가 있다.
- **설계 문서 추가분**: §9 아래 결과 표의 판정(확인됨/막힘/미검증)이 보고서와 일치하고, 미검증 항목(5 효과, 6 전체)은 거짓 통과 없이 "미검증"으로 남음. 재실행 명령 포함. 다른 절은 건드리지 않음.
- 자격증명 평문 없음(`AKIA`, `aws_secret` 검색 결과 없음). 실제 AWS 호출 없음.

## P0

없음.

## P1

없음.

## P2

### `.agent/poc-b12/app-copy/.env` 에 실제 로컬 DB 비밀번호·JWT 시크릿 사본이 남아 있음
#### Problem
앱 공존 시험을 위해 저장소 `.env` 를 복사한 파일이 `.agent/poc-b12/app-copy/.env` 에 있다. 보고서도 이를 밝혔고 `.agent/` 는 gitignore 대상이라 커밋될 위험은 낮다.
#### Why It Matters
의도치 않은 공유(폴더 압축·복사)나 `.gitignore` 변경 시 비밀값이 노출될 수 있다. 시험이 끝났으므로 보관 이유가 없다.
#### Recommended Fix
PoC 결과 확인이 끝나면 `.agent/poc-b12/app-copy/` 전체(또는 최소 `.env`)를 삭제한다. 라이브 스모크 재실행에는 필요 없다(별도 `pom.xml` 로 실행). 필요하면 사용자가 직접 지운다.

### 결론의 전제: Spring AI 경로의 실제 모델 동작은 미검증
#### Problem
"조건부 진행" 권고는 타당하나, 구조화 출력(`outputConfig.textFormat=json_schema`)이 실제 Bedrock 모델에서 지원·준수되는지, 캐시 히트, 지연·비용, 모델/리전 접근은 모두 AWS 계정이 필요해 미검증이다(보고서가 명시).
#### Why It Matters
현재 계약은 `submit_mission` 도구를 **강제**하는 방식이다. Spring AI 경로는 이를 유지할 수 없으므로(막힘), 구조화 출력으로 바꾸면 응답 안정성이 달라질 수 있다.
#### Recommended Fix
조치 없음(PoC 범위 밖). 아래 "의사결정" 참고.

## 의사결정 (사용자 몫 — 구현으로 넘어가기 전)

PoC 결과에 따른 선택지:

| 선택 | 내용 | 비용/위험 |
|---|---|---|
| **A. AWS SDK `bedrockruntime` 직접 호출** (설계 §11) | 현재와 같은 "도구 강제 호출" 계약을 그대로 유지. `MissionLlm` 경계 뒤에 `BedrockMissionLlm` | Spring AI 를 쓰지 않는다는 요구와 어긋남. 구현 단순, 중괄호 문제 없음 |
| **B. Spring AI 2.0.1 + 구조화 출력** | Spring AI 사용, 도구 강제 대신 JSON schema 출력 | 실제 모델의 schema 준수·캐시·지연을 AWS 에서 먼저 검증해야 함. 검증기·재시도로 보완 가능 |
| **C. 보류** | AWS 접근(모델 접근·자격증명) 확보 후 `LiveBedrockSmokeTests` 로 6번·5번 효과를 먼저 확인 | 일정 지연, 근거는 가장 확실 |

권장: **먼저 AWS 에서 라이브 스모크를 한 번 돌려(C) 구조화 출력의 실제 신뢰성을 본 뒤 A/B 를 정한다.** Spring AI 사용이 확정된 요구라면 B, 아니면 A.

## 병렬 작업과의 충돌
저장소 코드 변경이 없어 충돌 없음. 이후 구현 시 `MissionAiProperties`, `AiMissionGenerator`, `AnthropicClient`, `pom.xml` 이 우준(B8/B9) 영역과 겹친다.

## 결론: 승인 가능
PoC 는 계획한 6개 질문에 근거와 함께 답했고, 미검증 항목을 정직하게 구분했으며 production 코드를 건드리지 않았다. Codex 수정 요청은 필요 없다.
