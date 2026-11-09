# HA'WE 프로젝트 기술 분석 (논문 작성용)

> 분석 기준: 2026-11-04, `main` 브랜치 `e1f6316` (작업 트리 clean).
> 근거는 저장소 안의 코드·문서로만 적었다. 근거가 약한 항목은 **[추가 확인 필요]**로 표시했다.
> `.env`, 키, 비밀번호, 서버 주소(IP·RDS 엔드포인트·버킷명) 같은 민감정보는 적지 않았다.
> 표기: **[코드 확인]** 소스코드에서 직접 확인 / **[문서 확인]** 저장소 문서에 기록된 값(재현하지 않음) / **[해석]** 분석자의 연구적 해석.

---

## 0. 먼저 알아 둘 점 — 오래된 문서와 실제 코드의 차이

| 문서의 서술 | 실제 상태 | 근거 |
|---|---|---|
| 루트 `CLAUDE.md`: 백엔드는 `HaweApplication.java`만 있는 빈 골격, 프론트는 Cordova 정적 프로토타입 | **오래된 설명.** 백엔드는 기능 15개 패키지·Java 약 10,300줄로 구현됨. Cordova는 폐기, iOS(SwiftUI)·Android(Compose) 네이티브 앱으로 대체 | `README.md`, `src/main/java/com/sapphire/hawe/**` |
| `docs/FEATURE/PROJECT_OVERVIEW.md` §8: 스케줄은 EventBridge, 푸시는 SNS | **설계만.** 실제 발행은 Spring `@Scheduled`(매 분), 알림은 DB 저장 + 앱 내 알림 목록. 원격 푸시(FCM/APNs/SNS)는 미구현 | `mission/service/MissionScheduler.java`, `notification/**`, `android/.../core/push/DebugNotificationReceiver.kt`("FCM 연결 단계에서 삭제") |
| 같은 문서 §10: 알림 API "설계만" | **구현됨** (`/api/notifications` 4개 엔드포인트, 커밋 `560b082`) | `notification/controller/NotificationController.java` |
| 같은 문서 §8: 앱은 API 연결 없음 | **연결됨.** 두 앱에 Repository/API 계층 존재 | `android/.../core/net/*`, `ios/HAWE/Core/Net/*` |
| `docs/INFRA/DEPLOY_RUNBOOK.md`: DB는 RDS MySQL | 로컬 개발은 MariaDB, 운영은 MySQL(RDS). 드라이버를 URL로 선택 | `pom.xml`(mariadb·mysql 드라이버 둘 다), `application.properties` |

논문에는 **코드 기준의 최신 상태**만 써야 한다.

---

## 1. 프로젝트 개요

| 항목 | 내용 | 근거 |
|---|---|---|
| 이름 | HA'WE(하웨) — "하루, 날것 그대로(today, raw as it is)" | `docs/FEATURE/PROJECT_OVERVIEW.md` §1 |
| 한 줄 요약 | 그룹마다 매일 미션이 열리고, **내가 먼저 답해야 그룹원의 답을 볼 수 있는** 모바일 그룹 앱 | `README.md` |
| 대상 사용자 | 이미 아는 소규모 그룹(연인 2명, 가족·친구·웬수·그저그런사이 최대 6명). 성인 | `PROJECT_OVERVIEW.md` §2, `group/service/GroupService.java:110,182` |
| 해결하려는 문제 | 소규모 그룹은 연락이 뜸해지고, 일상을 먼저 공유하는 일이 부담스럽다 | `PROJECT_OVERVIEW.md` §2 |
| 해결 방식 | 정해진 시각에 가벼운 미션(사진·글·그림)이 열리고, 제출해야 서로의 답이 보인다. "남의 답이 궁금해서" 참여하게 하는 구조 | 같은 문서 §2·§3 |
| 개발 형태 | 4인 팀(문서상 담당 표기), 사람(검토·git) + AI 코딩 에이전트(구현, 리뷰·디버깅) 분업 | `docs/해야될거/*`, `CLAUDE.md` Workflow |
| 기간 | Git 첫 커밋 ~ 2026-11 초, 커밋 163개 | `git log` |

### 1-1. 기존 방식과의 차이 [해석]

- 일반 메신저 단톡방: 대화를 시작할 계기가 없고 먼저 올리는 사람이 부담을 진다 → HA'WE는 **서버가 매일 미션(계기)을 제공**하고, **제출 전 열람 잠금**으로 "먼저 올리는 부담"을 모두에게 같게 만든다.
- 시간 제한 동시 촬영형 SNS(예: 사진 한 장 공유형 서비스)와 비슷한 "제출해야 열람" 구조를 쓰지만, HA'WE는 (1) **미리 정한 소규모 그룹 단위**이고, (2) 매체가 사진뿐 아니라 **글(삼행시·빈칸 채우기)·그림**이며, (3) 미션 문장을 **그룹 관계·기념일·참여 기록을 반영해 LLM이 생성**한다. 외부 서비스와의 비교는 문헌·서비스 확인 후 서술해야 한다 **[추가 확인 필요 — 외부 인용]**.

### 1-2. 활용 시나리오 [문서 확인]

1. 그룹 생성(관계 유형·하루 미션 수·슬롯별 발행/마감 시각) → 6자리 초대 코드 공유 → 미리보기 후 가입.
2. 발행 시각(기본 09:00)에 서버가 미션을 발행하고 "미션 도착" 알림을 만든다.
3. 사용자는 미션 형식(사진 촬영·갤러리·색깔 찾기 / 삼행시·빈칸 채우기 / 30초 스케치·A를 B로)에 맞게 제출한다.
4. 제출하면 잠겨 있던 그룹 피드가 열리고, 스티커 반응·댓글(@멘션)을 남긴다. 미제출자에게 "콕찌르기"를 보낸다.
5. AI 마스코트 "별"이 사진 적합도 점수·그림 추측 결과를 댓글로 남긴다.
6. 그룹 달력에 미션별 도장(참잘했어요/지각/벌점), 월별 인사이트(활동 지수·자주 쓴 단어·하이라이트)를 본다.

---

## 2. 기술 스택 (역할 중심)

| 영역 | 기술 | 프로젝트에서의 역할 | 근거 |
|---|---|---|---|
| 백엔드 | Spring Boot 4.1.1, Java 25, Maven | REST API 서버, 스케줄러, 이벤트 기반 비동기 처리 | `pom.xml` |
| 데이터 접근 | MyBatis(어노테이션 SQL), Spring JDBC | 기능별 Mapper 인터페이스로 SQL 직접 관리 | `*/dao/*Mapper.java` |
| DB | MariaDB(로컬), MySQL(AWS RDS) / Flyway | 스키마 버전 관리(V1~V4), 15개 테이블 | `db/migration/V1~V4`, `DEPLOY_RUNBOOK.md` |
| 인증 | Spring Security + JJWT | Stateless JWT(액세스 30분, 리프레시 14일), 토큰 버전으로 비밀번호 변경 시 무효화 | `global/security/*`, `application.properties`, `docs/해야될거/1번_찬희/B3(완료)-…` |
| 파일 저장 | 로컬 디스크 / AWS S3(SDK v2) | 설정으로 전환. S3는 비공개 버킷 + 1시간 presigned URL | `global/storage/*FileStorage.java`, `DEPLOY_RUNBOOK.md` §4 |
| 한국어 처리 | Lucene Nori 형태소 분석기 | 월간 인사이트의 "자주 쓴 단어" 명사 추출 | `insight/service/NoriKeywordExtractor.java`, `GROUP_INSIGHTS.md` |
| 문서화 | springdoc-openapi | Swagger UI | `OpenApiConfig.java` |
| LLM | Anthropic Messages API 형식(직접 HTTP 호출, Spring AI 미사용), 운영은 LiteLLM 게이트웨이 → Amazon Bedrock의 Claude Haiku 계열 | 미션 문장·빈칸 생성 | `mission/service/AnthropicClient.java`, `DEPLOY_RUNBOOK.md` §0 (정확한 모델 버전 **[추가 확인 필요]**) |
| 비전 서비스 | Python 3.12, FastAPI(uvicorn), ONNX Runtime, CLIP ViT-B/32 + 다국어 텍스트 인코더 | 사진이 미션 주제에 맞는지 0~1 점수 계산 | `ml/vision-service/app.py`, `README.md` |
| 그림 인식 | TensorFlow/Keras 학습 → TFLite int8, Android LiteRT / iOS Core ML | 기기 안에서 그림 50개 범주 분류 | `ml/quickdraw/*`, `android/.../feature/draw/QuickDrawClassifier.kt`, `ios/HAWE/Features/Draw/*` |
| Android | Kotlin, Jetpack Compose, OkHttp, kotlinx.serialization, CameraX, Coil, minSdk 26 | 네이티브 앱(약 11,000줄) | `android/app/build.gradle.kts` |
| iOS | SwiftUI(iOS 17+), XcodeGen | 네이티브 앱(약 14,000줄) | `ios/project.yml`, `README.md` |
| 인프라 | AWS EC2(Amazon Linux 2023), RDS MySQL, S3, IAM Role | 실습용 단일 서버 배포 | `docs/INFRA/DEPLOY_RUNBOOK.md` |

---

## 3. 시스템 아키텍처

### 3-1. 구성도 (텍스트)

```
[iOS 앱(SwiftUI)]  [Android 앱(Compose)]
   │  └ 온디바이스 그림 분류(Core ML / LiteRT, int8 CNN 158KB)
   │ HTTPS/HTTP REST + JWT (JSON, multipart)
   ▼
[Spring Boot API 서버 — 기능별 패키지(auth, group, mission, submission, comment, reaction,
   poke, notification, calendar, insight, character, profile, mascot, withdraw, dev)]
   ├─ MyBatis ──▶ [MariaDB / MySQL(RDS)]  (Flyway V1~V4, 15 테이블)
   ├─ FileStorage ──▶ [로컬 디스크 | S3 (presigned URL)]
   ├─ @Scheduled(매 분) MissionScheduler → MissionPublisher → MissionGenerator
   │        └─ AnthropicClient ──HTTP──▶ [LLM 게이트웨이(LiteLLM) → Bedrock Claude]
   ├─ 트랜잭션 커밋 후 이벤트(@TransactionalEventListener AFTER_COMMIT)
   │        ├─ NotificationListener → notifications 테이블
   │        ├─ TopicFitReviewListener(@Async visionExecutor) ──multipart──▶ [Python 비전 서비스 /v1/topic-fit]
   │        └─ MascotListener(@Async) → AI 마스코트 "별" 댓글·반응
   └─ InsightScheduler / insightExecutor → group_insights 캐시
```

### 3-2. 계층 구조 [코드 확인]

- 기능별 수직 분할 패키지 `com.sapphire.hawe.<feature>` 안에 `controller → service → dao(MyBatis) → domain/dto`.
- 공통: `global/exception`(기능별 ErrorCode enum → `BusinessException` → `GlobalExceptionHandler`가 `{code, message}`), `global/security`(JWT 필터), `global/storage`, `global/time/SeoulTime`(시간대 Asia/Seoul 고정), `global/text/KoreanJosa`(받침에 맞는 조사), `global/ratelimit/AttemptLimiter`.
- 목록 응답은 `{content, page, size, totalPages, totalElements}` 봉투(`global/dto/PageResponse.java`).

### 3-3. API 표면 [코드 확인]

사용자용 약 45개 + 개발자 도구 19개 + 내부 실험 리포트 1개(총 64개 매핑). 주요 엔드포인트:

| 기능 | 엔드포인트 |
|---|---|
| 인증 | `POST /api/auth/register · login · refresh · email-check` |
| 내 정보 | `GET /api/me`, `GET /api/me/stats`, `PATCH /api/me/profile · password · character`, `PUT/DELETE /api/me/profile-image`, `DELETE /api/me`(탈퇴) |
| 그룹 | `POST/GET /api/groups`, `GET /api/groups/{id}`, `PATCH /{id}/settings`, `GET /{id}/members`, `POST /{id}/invite-code`, `GET /api/invites/{code}`, `POST /api/groups/join`, `DELETE /{id}/members/me`, 그룹 내 프로필 `PUT/DELETE /{id}/members/me/profile…` |
| 미션 | `GET /api/groups/{id}/missions/today`, `GET /api/missions/{id}`, `GET /api/mission-templates` |
| 제출·피드 | `POST /api/missions/{id}/submissions`(multipart), `GET /api/missions/{id}/submissions`(**미제출 시 423 Locked**), `GET /api/submissions/{id}`, `GET /api/missions/{id}/result` |
| 소셜 | `POST /api/submissions/{id}/reactions`, `GET/POST /api/submissions/{id}/comments`, `DELETE /api/comments/{id}`, `POST /api/groups/{id}/pokes` |
| 알림 | `GET /api/notifications`, `GET /unread-count`, `PATCH /{id}/read`, `PATCH /read-all` |
| 기록 | `GET /api/groups/{id}/calendar`, `GET /api/groups/{id}/insights?month=` |
| 캐릭터 | `GET /api/characters` |
| 실험(관리자) | `GET /internal/experiments/topic` (`X-Admin-Token`, 없으면 404) |

### 3-4. 데이터베이스 [코드 확인]

`V1__initial_schema.sql`의 15개 테이블: `users`, `characters`, `groups`, `group_members`, `group_mission_slots`, `missions`, `submissions`, `submission_ai_reviews`, `group_insights`, `submission_guesses`(미사용 잔재), `comments`, `comment_mentions`, `reactions`, `pokes`, `notifications`. V2 캐릭터 시드, V3 알림 참조, V4 AI 마스코트 계정.
DB 고유 제약으로 막는 것: 같은 그룹 중복 가입, **같은 (그룹, 날짜, 슬롯) 중복 발행**, **한 미션에 한 사람 중복 제출** (`PROJECT_OVERVIEW.md` §9, `MissionPublisher`의 `DuplicateKeyException` 무시 처리).

### 3-5. 인증·보안 [코드 확인]

- JWT 액세스/리프레시, 비밀번호 변경 시 토큰 버전 증가로 기존 토큰 무효화. Android는 Keystore 기반 토큰 저장(`KeystoreTokenStore.kt`).
- 초대 코드 입력 실패 제한(10분 10회, `hawe.invite.*`, `AttemptLimiter`).
- 업로드 파일 형식 판별(`FileTypeDetector`), 크기 제한 10MB.
- 실험 리포트는 JWT와 별도 관리자 토큰, 미설정·불일치 시 404로 숨김.
- **한계**: `/api/dev/**`가 `permitAll`이고 프로필 조건이 없다(`SecurityConfig.java:49`) → 운영 배포 시 개발자 API 노출 위험 **[추가 확인 필요: 운영에서 차단 여부]**. 운영 서버가 HTTP(8080)로 노출되고 iOS ATS 전체 허용(커밋 `7f68013`).

### 3-6. 비동기 처리 [코드 확인]

- 트랜잭션 커밋 **이후**에만 후속 작업 실행(`@TransactionalEventListener(AFTER_COMMIT)`) → 제출이 롤백되면 알림·AI 리뷰도 생기지 않는다.
- 사진 적합도 리뷰·마스코트는 별도 스레드 풀(`visionExecutor`)에서 수행 → **제출 응답이 AI를 기다리지 않는다.**
- 리뷰 행은 `INSERT IGNORE`로 선점해 중복 이벤트가 재실행되지 않음, 실패 시 1회 재시도 후 `FAILED`(`VISION_TOPIC_FIT.md`).
- 실시간 통신(WebSocket 등)은 없다. 앱은 요청 시 조회한다.

---

## 4. 핵심 기능 (중요도순)

### F1. 제출 후 열람(잠금) 규칙 — 서비스의 핵심 규칙

| 항목 | 내용 |
|---|---|
| 필요성 | "먼저 올리는 부담"을 없애고, 남의 답이 궁금해서 참여하게 하는 동기 구조 |
| 사용자 관점 | 미션에 답하기 전에는 그룹원의 답이 블러 처리되고, 제출하면 열린다. 지난 날짜도 내가 안 낸 미션은 볼 수 없다 |
| 구현 원리 | 피드·상세·집계 결과 조회 시 서버가 `submissions`에 (미션, 사용자) 행이 있는지 검사하고 없으면 `FEED_LOCKED`(HTTP **423 Locked**). 클라이언트 블러는 표시용이고 **판정은 서버가 한다** |
| 코드 | `submission/service/SubmissionService.java`(`requireSubmitted`, `feed`, `requireAccessible`), `MissionResultService.java`, `SubmissionErrorCode.FEED_LOCKED` |
| 테스트 | `SubmissionControllerTests`, `MissionResultControllerTests` 등 |
| 논문 가치 | 서비스 정책을 서버 접근 제어로 강제한 설계. 미션 단위 잠금(하루 단위 아님) 결정 근거(B7) |

### F2. 그룹 맞춤형 미션 자동 발행 + LLM 미션 생성 파이프라인 (AI 핵심 1)

| 항목 | 내용 |
|---|---|
| 필요성 | 매일 새롭고, 그룹 관계에 맞고, 안전한 미션을 사람이 만들 수 없다 |
| 사용자 관점 | 정해진 시각에 미션이 자동으로 열린다. 연인·가족·친구 등 관계에 맞는 톤 |
| 구현 원리 | ① `MissionScheduler`가 매 분(Asia/Seoul) 모든 그룹에 대해 `publishIfDue` 호출 → ② 슬롯별 발행 시각이 지났고 아직 없으면 발행. ③ **틀(템플릿)은 서버가 결정**: 결정적 시드(그룹·날짜·슬롯 해시)로 고르고, 하루 1개면 어제와 다른 매체, 여러 개면 같은 날 매체가 겹치지 않게 순환(`MissionTemplateSelector`). 대상 지정 틀은 서버가 대상 멤버를 지정. ④ **LLM은 정해진 틀 안에서 `{content, category, params}`만 생성**: tool use를 강제(`tool_choice`)해 구조화된 JSON으로 받고, 시스템 프롬프트는 캐시(`cache_control: ephemeral`). ⑤ **서버 재검증**(`GeneratedMissionValidator`): 키 구성, 길이(문장 60자), 카테고리 목록, 금지어, 삼행시 제시어 한글 3글자·문장 포함, 빈칸 `___` 정확히 1개, `[대상]` 자리표시자, 최근 미션과 **문자 bigram 유사도 0.8 이상이면 거절**. ⑥ 실패 시 재시도(총 제한 시간 안), 계속 실패하면 **고정 문장 폴백**(`FallbackMissionGenerator`) → AI 장애가 발행을 막지 않음. 출처를 `generation_source`(AI/AI_FALLBACK/PLACEHOLDER)로 기록. ⑦ 닉네임은 LLM에 보내지 않고 `[대상]`을 서버가 표시 시 치환하며 받침에 맞게 조사 보정(`KoreanJosa`) |
| LLM 입력(개인정보 최소화) | 날짜·요일, 관계 유형, 틀과 빈칸 정의, 대상 유무, 최근 미션 문장, 카테고리 목록, 특별한 날(연인 100일 단위·주년 등), 미션 단위 참여율·반응 수. **멤버 이름·그룹 이름·제출 내용·댓글은 보내지 않음**(`AiMissionGenerator.input`, 클래스 주석) |
| 안전 규칙 | 관계별 금지 주제·공통 금지 목록을 프롬프트로 지시 + 서버 금지어 검사(`PROJECT_OVERVIEW.md` §7, `src/main/resources/prompts/*`) |
| 동시성 | 발행은 `REQUIRES_NEW` 트랜잭션, (그룹·날짜·슬롯) 고유 제약 위반 시 무시 → 중복 발행 방지 |
| 코드 | `mission/service/MissionScheduler.java`, `MissionPublisher.java`, `FallbackMissionGenerator.java`, `AiMissionGenerator.java`, `AnthropicClient.java`, `PlaceholderMissionGenerator.java`, `GroupContextBuilder.java`, `mission/domain/MissionTemplateSelector.java`, `GeneratedMissionValidator.java`, `AnniversaryResolver.java` |
| 테스트 | `AiMissionGeneratorTests`, `AnthropicClientTests`, `GeneratedMissionValidatorTests`, `MissionTemplateSelectorTests`, `FallbackMissionGeneratorTests`, `MissionPublisherTests`, `AiMissionPublishTests` 등 |
| 실행 확인 | AWS EC2에서 AI 생성 시 `generation_source=AI` 확인 절차와, 실제 생성 예시 "지금 내 손 닿는 곳에서 제일 만만한 물건 찍어 와"가 문서에 기록됨 [문서 확인] (`DEPLOY_RUNBOOK.md` §3-7, `문제점_해결.md`) |
| 논문 가치 | **"서버가 구조를 정하고 LLM은 내용만 채운 뒤 서버가 검증·폴백"**하는 제한형 생성 구조. 개인정보 최소 입력. 결정적 시드로 재현 가능한 틀 선택 |

### F3. 온디바이스 그림 인식 "AI가 맞혀볼게요" (AI 핵심 2)

| 항목 | 내용 |
|---|---|
| 필요성 | 그림 미션에 놀이 요소와 즉시 피드백을 주되, 그림을 외부로 보내지 않기 |
| 사용자 관점 | 그림을 그리고 "AI에게 보여주기"를 누르면 기기가 무엇인지 맞힌다. 확신이 낮으면 "잘 모르겠어요" |
| 구현 원리 | Google Quick, Draw! 획 데이터 50개 범주 × 30,000장(학습 120만/검증 15만/테스트 15만)으로 **소형 CNN(파라미터 146,258개)을 처음부터 학습**. 학습·앱이 같은 전처리 명세(`PREPROCESS_SPEC.md`: 잉크 경계 상자 → 정사각 중앙 배치 → 20×20 면적 평균 → 28×28)를 공유하고, 골든 입력 10개로 Python·Android 결과가 칸당 1e-3 이내인지 단위 테스트. TFLite float32/float16/int8 비교 후 "원본 대비 top-1 1%p 이내 중 최소 크기" 규칙으로 int8 선택. 불확실 임계값 0.55는 팀원 그림으로 점수(맞힘+1, 틀림−1)가 최대인 값으로 선택. "A를 B로" 미션은 A·B 중 하나를 맞히면 성공 처리(`DrawSubjectMatcher`). 상위 3개 추측(라벨·확률)만 제출과 함께 서버로 보내고, 서버의 AI 마스코트가 "○○인가요?" 댓글로 남김 |
| 결과 [문서 확인] | 테스트셋 top-1 **93.1%**, top-3 **98.4%**, ECE 0.076 / int8 **158KB**(float32 573KB의 28%), int8 top-1 유지 / 팀원 3명이 앱과 같은 그림판으로 그린 206장: top-1 **86.4%**, top-3 96.1% (도메인 격차 6.7%p) / PC 추론 p50 1.16ms(int8) |
| 코드 | `ml/quickdraw/{download,rasterize,dataset,train,export,evaluate}.py`, `android/.../feature/draw/{QuickDrawClassifier,InkPreprocessor,DrawSubjectMatcher,AiGuess}.kt`, `ios/HAWE/Features/Draw/*.swift`, `ios/Tools/quickdraw_coreml.py`, `mascot/service/MascotService.commentDrawGuess` |
| 테스트 | Python pytest(전처리·골든·내보내기), Android 단위 테스트 + 기기 테스트(`androidTest/.../feature/draw`) |
| 논문 가치 | 학습–앱 전처리 일치 검증, 경량화 선택 규칙, 실제 앱 그림으로 도메인 격차 측정, 거절 임계값 근거 |

### F4. 사진–주제 적합도 평가 서비스 (AI 핵심 3)

| 항목 | 내용 |
|---|---|
| 필요성 | 사진 미션("창밖 풍경을 찍어줘", "파란색 물건")에 긍정적 피드백 제공 |
| 사용자 관점 | 사진을 올린 뒤 몇 초 후 AI 마스코트 "별"이 칭찬 댓글·점수를 남기고, 잘 맞으면 "찰떡상" 배지. **부정 평가는 보여 주지 않고 도장은 제출 여부로만 결정** |
| 구현 원리 | 별도 Python 서비스(FastAPI)가 CLIP 이미지 인코더 + 다국어 텍스트 인코더로 사진과 "주제 1개 + 대조 주제(일상 72개 또는 색 10개)"의 유사도를 softmax해 **이 주제일 확률 `fit`(0~1)**만 반환. 백엔드가 틀별 임계값(PHOTO_NOW 0.024/0.017, PHOTO_COLOR 0.132/0.104)으로 배지·문구 결정. 제출 커밋 후 비동기 실행, 실패 시 1회 재시도 후 FAILED(제출·피드에 영향 없음). 사진은 내부 비전 서비스로만 보내고 저장하지 않음. ONNX fp16 변환으로 PyTorch 제거 |
| 결과 [문서 확인] | 팀원 2명 사진 177장, 짝 1,028개: ROC-AUC PHOTO_NOW **0.958**, PHOTO_COLOR **0.959**. 임계값은 정밀도 ≥95%(배지)·≥85%(칭찬) 기준으로 선택. ONNX fp16: 설치 약 2.3GB→0.7GB, 시작 36초→2초, PyTorch 대비 점수 차 최대 0.00045, 판정 변화 0(5,140쌍). CPU 지연 p50 418ms·p95 835ms |
| 실제 버그와 해결 [문서 확인] | Java HTTP 클라이언트의 h2c 업그레이드 요청을 uvicorn이 400으로 거절 → HTTP/1.1 고정, 실제 클라이언트로 재현하는 회귀 테스트 추가(`문제점_해결.md`, 커밋 `436cd87`) |
| 코드 | `ml/vision-service/{app,scoring,eval,export_onnx}.py`, `submission/service/{TopicFitReviewListener,TopicFitService,TopicFitJudge,VisionClient}.java`, `mascot/service/MascotService.commentPhotoScore` |
| 논문 가치 | 경량 서빙(ONNX fp16)과 정확도 유지 검증, 정밀도 우선 임계값, "긍정 피드백만" UX 정책 |

### F5. 그룹·초대·미션 슬롯 설정

- 관계 5종(연인·가족·친구·웬수·그저그런사이), 정원(연인 2, 그 외 6), 6자리 초대 코드 미리보기·재발급, 그룹장 나가기 시 후임자 지정, 하루 미션 1~5개와 슬롯별 발행·마감 시각, 그룹 내 프로필.
- 미션이 열릴 때 발행·마감 시각을 **미션 행에 고정 저장** → 이후 설정 변경이 지각 판정을 바꾸지 않음.
- 코드: `group/**`, `mission/domain/MissionTiming.java`.

### F6. 제출(형식 검증)

- multipart 제출, 틀별 허용 필드만 받음. 삼행시는 빈 줄을 뺀 3줄·각 줄이 제시어 글자로 시작, 빈칸 채우기 100자 등 **서버 검증**. 마감 후 다음 미션 전까지 지각 제출 허용(`late`).
- 코드: `submission/service/SubmissionService.java`.

### F7. 도장(달력) — 저장하지 않고 계산

- `Stamp.of(submitted, late, closed, memberWhenPublished)`: 제출=참잘했어요/지각, 미제출+닫힘+발행 당시 멤버=벌점, 그 외 없음. 미션별 도장.
- 코드: `calendar/domain/Stamp.java`, `CalendarService.java` / 테스트 `StampTests`, `CalendarServiceTests`.
- 가치: 파생 상태를 저장하지 않아 정합성 문제를 줄임 [해석].

### F8. 소셜 상호작용·알림

- 스티커 반응(토글), 댓글(@멘션, 사진), 콕찌르기(이미 낸 사람·중복 재촉 금지).
- 알림 유형 `topic`(미션 도착), `poke`, `comment`, `mention` — 커밋 후 이벤트로 DB에 생성, 앱의 알림 목록·안 읽음 수.
- **원격 푸시는 미구현**(앱의 로컬 알림 표시·디버그 수신기만 존재).
- 코드: `comment/**`, `reaction/**`, `poke/**`, `notification/**`.

### F9. 그룹 월간 인사이트

- 활동 지수 = round(100 × (0.6×참여율 + 0.2×정시율 + 0.2×상호작용)), 가장 빠른 제출자, 반응이 많은 글, **Nori 형태소 분석으로 글 미션의 자주 쓴 명사**, 하이라이트.
- 닫힌 미션만 집계, 결과 캐시(`group_insights`, 비동기 생성 → 202 GENERATING / 200 READY / stale), 제출 원문은 캐시에 저장하지 않음.
- 코드: `insight/**`, 문서 `GROUP_INSIGHTS.md`. Android 화면 `feature/insight/GroupInsightScreen.kt`.

### F10. AI 주제 vs 랜덤 주제 비교 실험 프레임워크

- (그룹, 날짜) 단위로 `SHA-256(salt|그룹ID|날짜)` 결정적 배정, 블라인드, 틀·대상·시각은 두 군이 같고 **문장 출처만 다름**. 그룹 내 차이(t 95% CI, 부트스트랩, 부호 검정), Wilson CI, z 검정, ITT/per-protocol, 표본 부족 경고를 통계 라이브러리 없이 구현.
- 코드: `mission/service/{ExperimentAssigner,ExperimentReportService}.java`, `mission/domain/ExperimentStats.java`, 문서 `EXPERIMENT_TOPIC.md`.
- **주의: 기본 꺼짐이며 실제 사용자 데이터로 실험한 결과는 저장소에 없다.** 문서의 "AI 92.3% vs 랜덤 50.0%"는 **테스트 데이터 예시**이므로 논문 결과로 쓰면 안 된다.

### F11. 개발자 도구

- 가상 시계, 시드 데이터, 미션 강제 발행·리롤, 마감 즉시 처리, 사용자 전환 → 시간 의존 기능(발행·마감·도장)을 수동 검증하기 위한 도구. 논문 본문에는 "테스트 지원 도구" 정도로만 언급 권장.

---

## 5. 기술적 특징과 차별성

| 특징 | 코드로 확인 | 추가 검증 필요 |
|---|---|---|
| 서버 주도 틀 선택 + LLM 내용 생성 + 서버 재검증 + 폴백 (제한형 생성) | ✅ F2 | 생성 품질(재미·안전성)의 정량 평가 없음 |
| LLM 입력에서 개인 식별 정보 제외, `[대상]` 자리표시자 후 서버 치환 | ✅ | — |
| 결정적 시드 기반 틀 선택 → 재현 가능, 매체 다양성 보장 | ✅ `MissionTemplateSelectorTests` | — |
| 커밋 후 이벤트 + 별도 스레드 풀로 AI를 제출 경로에서 분리 | ✅ | 부하 테스트 없음 |
| 3종 AI를 배치 위치별로 분리: LLM(외부 API), 사진 비전(내부 서버), 그림 CNN(기기) | ✅ | 이 선택이 비용·지연·프라이버시에 주는 효과는 정량 비교 없음 |
| 학습–앱 전처리 일치를 골든 테스트로 고정 | ✅ | iOS Core ML 골든 확인 [추가 확인 필요] |
| 경량화: int8 TFLite 158KB, ONNX fp16로 비전 서비스 크기·시작 시간 감소 | ✅ (문서 수치) | 휴대폰 실기기 추론 시간 미측정 |
| 파생 상태(도장) 비저장 계산 | ✅ | — |
| 실험 설계(블라인드 A/B, 그룹 내 비교)를 서비스에 내장 | ✅ | **실제 실험 결과 없음** |
| "참여율 향상", "사용자 만족" | ❌ | 사용자 연구 없음 → 주장 금지 |

---

## 6. 구현 결과와 한계

### 6-1. 구분

| 구분 | 항목 |
|---|---|
| 구현 확인 | F1~F11 백엔드 전부, Android 앱(인증·그룹·미션 수행 3매체·결과/피드·댓글·반응·콕찌르기·알림 목록·달력·인사이트·마이페이지·그림 AI), iOS 앱(같은 화면 구성, API 연결), Python 비전 서비스, 그림 모델 학습·내보내기 파이프라인 |
| 테스트 코드 존재 | 백엔드 `@Test` 364개(테스트 클래스 약 95개), Android `@Test` 215개(단위+기기), ML pytest 함수 64개, iOS `HAWETests` |
| 테스트 결과 확인 | 백엔드: 로컬 빌드 산출물 `target/surefire-reports`(2026-10-20)에서 **363개 실행, 실패·오류 0** (git 미추적 로컬 기록). 문서: 2026-10-19 Android 단위 192개·서버 345개 통과(`문제점_해결.md`). 그림 모델·비전 평가 수치(`ml/*/EVAL.md`, `REPORT.md`). 이번 분석에서 테스트를 다시 실행하지는 않았다 |
| 일부 구현 | iOS: Android에서 고친 5개 항목 미반영(`문제점.md` 4번), Windows 환경이라 빌드 확인 제한. 알림: DB·앱 목록만, 원격 푸시 없음. AWS 배포: 최신 커밋 미반영 가능(`문제점.md` 6번), 비전 서비스 AWS 설치 여부 [추가 확인 필요] |
| 향후 예정(설계만) | FCM/APNs/SNS 푸시, systemd·고정 IP·HTTPS, 순서·역할형 미션(이어쓰기 등), Qdrant 회고 추천(MVP 제외), 실제 사용자 대상 B9 실험 |
| 제거된 기능 | 음성 미션(2026-10-12), 슬라이더 미션(2026-10-19), 똥뿌리기(`ee4f6a8`), 그룹원 제출 알림(`fc38aa1`), Cordova 앱 |

### 6-2. 한계

1. 사용자 연구·현장 배포 데이터 없음 → 참여율·만족도 효과 미검증.
2. 그림 평가 206장(3명), 사진 평가 177장(2명)으로 표본이 작다. 사진 "안 맞음" 짝이 무작위 주제라 실제보다 쉬울 수 있다.
3. 휴대폰 실기기 추론 시간 미측정, iOS Core ML 검증 상태 불명확.
4. 원격 푸시 없음 → 미션 도착을 앱을 열어야 안다.
5. 단일 EC2 + HTTP + 개발자 API 공개 → 운영 보안 미완.
6. AI 미션 발행이 발행 트랜잭션 안에서 외부 호출을 기다린다(총 제한 시간으로 제한) → 그룹 수가 많아지면 스케줄러 지연 가능 [해석].
7. 그림 모델 학습 데이터가 범주 순으로 저장되어 섞기가 부족했던 점(보고서가 스스로 기록).

---

## 7. 주요 주장별 근거 파일

| 주장 | 근거 |
|---|---|
| 제출 전 열람 시 423 | `src/main/java/com/sapphire/hawe/submission/service/SubmissionService.java`(requireSubmitted), `SubmissionErrorCode.java:8` |
| 매 분 스케줄 발행 | `mission/service/MissionScheduler.java` |
| 틀은 서버가 정함, 결정적 시드 | `mission/domain/MissionTemplateSelector.java`, `MissionPublisher.java` |
| LLM tool use 강제·프롬프트 캐시 | `mission/service/AnthropicClient.java` |
| LLM 입력 항목·개인정보 제외 | `mission/service/AiMissionGenerator.java`(input, 클래스 주석) |
| 서버 재검증·유사도 0.8 | `mission/domain/GeneratedMissionValidator.java` |
| 폴백 | `mission/service/FallbackMissionGenerator.java` |
| 도장 계산 | `calendar/domain/Stamp.java` |
| 비동기 AI 리뷰 | `submission/service/TopicFitReviewListener.java`, `mascot/service/MascotListener.java`, `global/config/AsyncConfig.java` |
| 비전 임계값 | `application.properties`(`hawe.vision.*`), `ml/vision-service/EVAL.md` |
| 그림 모델 수치 | `ml/quickdraw/REPORT.md`, `ml/quickdraw/EVAL.md`, `ml/quickdraw/export/model_card.md` |
| ONNX 효과 | `ml/vision-service/README.md`, 커밋 `7e1f4e1` |
| 인사이트 공식 | `insight/service/InsightCalculator.java`, `docs/BACKEND/GROUP_INSIGHTS.md` |
| 실험 설계 | `mission/service/ExperimentAssigner.java`, `mission/domain/ExperimentStats.java`, `docs/BACKEND/EXPERIMENT_TOPIC.md` |
| 배포 구성 | `docs/INFRA/DEPLOY_RUNBOOK.md` |
| 테스트 기록 | `target/surefire-reports/`(로컬), `docs/테스트_문제점_발견/문제점_해결.md` |
