# B10. 사진이 오늘 주제에 맞는지 AI가 판단하기 — CLIP 유사도 + 찰떡상 배지 (백엔드 + 비전 서비스)

> 범위: 백엔드(`src/`), 신규 비전 서비스(`ml/vision-service/`, Python), 문서. 앱 표시는 A7. 알림·배포 무관(비전 서비스는 **로컬에서 띄우는 별도 프로세스**로 개발·평가하며 운영 배포는 나중에). **전제: 7·8단계 + B0(음성 제거) 커밋 후**(B0 이후 매체 3종, 사진 틀 3종). 사용자 요구: 순위가 아니라 **CLIP 같은 이미지-텍스트 모델로 사진과 주제 문장의 유사도를 계산**하고, 점수 대신 **"오늘의 주제 찰떡상" 같은 배지나 한 줄 코멘트**로 보여 준다. "주제 적합도 판정 정확도 몇 %" 같은 수치 평가도 낸다.
>
> **정책 변경 주의**: 지금까지 문서(`PROJECT_OVERVIEW.md` §6, `MISSIONS_API.md` §11-1·§8)는 "AI 는 제출을 평가하거나 판정하지 않으며 사용자 데이터를 외부 AI 로 보내지 않는다"로 되어 있다. 이 기능은 그 원칙을 **좁게 수정**한다: ① 외부 3자 AI 가 아니라 **우리가 운영하는 비전 서비스**로만 사진을 보낸다, ② 점수·순위·낮은 평가는 사용자에게 **절대 보이지 않고** 긍정 배지/코멘트만 보인다, ③ 도장·제출 횟수 등 기존 규칙에는 영향이 없다. 이 변경을 문서에 반영하는 것이 이 계획의 일부다(Documentation).

## Goal

1. 사진 틀(`PHOTO_NOW`, `PHOTO_COLOR`) 제출 시, 제출이 끝난 뒤 **비동기로** 사진-주제 유사도를 계산해 `찰떡상` 배지 또는 한 줄 코멘트를 붙인다(`PHOTO_GALLERY` 는 "가장 오래된 사진" 같은 시각 판정 불가 조건이라 제외).
2. 피드·상세 응답에 `aiReview { badge, comment }` 를 (있을 때만) 내려준다. 점수(`fit`)는 내부 기록·평가용이며 **API 로 노출하지 않는다.**
3. 판정 정확도를 재현 가능하게 평가한다(`ml/vision-service/eval.py` + 보고서): 정확도·정밀도·재현율·F1·ROC-AUC, 임계값 선택 근거.

## Findings

- 제출 흐름: `SubmissionService.submit`(`@Transactional`) 이 파일을 `storage.store("submissions", bytes, ext)` 로 저장하고 `submissions.insert` 후 `response(...)` 를 돌려준다. 파일 key 는 `submissions.media_url`. 바이트는 요청 처리 중 메모리에 있다. 커밋 후 처리(롤백 시 파일 삭제 등록)는 이미 `TransactionSynchronization.afterCompletion` 패턴이 있다.
- `FileStorage` 인터페이스: `store`, `delete`, `publicUrl` — **읽기 메서드가 없다**(`LocalFileStorage` 는 루트 디렉터리 `hawe.storage.local-dir` 아래 key 파일). 비동기 후처리가 key 로 바이트를 읽으려면 `byte[] load(String key)` 추가가 필요하다.
- 사진 틀의 주제 정보: `missions.params` JSON — `PHOTO_NOW{subject}`, `PHOTO_COLOR{color}`, `PHOTO_GALLERY{condition}`; `missions.content` 는 사람이 읽는 지시문("지금 창밖으로 보이는 풍경을 찍어줘" — CLIP 에는 명령문보다 **명사구**가 적합). `MissionTemplate` 에 params 스키마가 있다.
- 비동기/이벤트 인프라: `@EnableScheduling` 은 있고(`HaweApplication`) `@Async`/`@EnableAsync`/`TaskExecutor` 는 없다. `ApplicationEventPublisher` 는 이미 쓰인다(`GroupCreatedEvent` → `MissionPublishListener`, `@TransactionalEventListener` 패턴 참고).
- 응답 조립: `SubmissionService.feed` 는 페이지 항목 id 목록으로 **반응/댓글 수를 배치 조회**(7단계) — 같은 방식으로 `aiReview` 를 배치 조회해야 한다(N+1 금지). `SubmissionResponse` 는 `@JsonInclude(NON_NULL)`.
- 삭제 경로: 그룹 삭제 `GroupService.deleteGroupCascade`(`GroupMapper.delete*ByGroupId` 연쇄)와 8단계 탈퇴(`WithdrawMapper`)가 제출 관련 행을 지운다 → 새 테이블도 같이 지워야 FK 위반(500)이 나지 않는다.
- 한국어 주제 문장: OpenAI 원본 CLIP 텍스트 인코더는 영어 중심. **다국어 CLIP**(Sentence-Transformers `clip-ViT-B-32` 이미지 인코더 + `clip-ViT-B-32-multilingual-v1` 텍스트 인코더, 한국어 포함 50+ 언어)를 쓰면 한국어 명사구를 그대로 넣을 수 있다. (모델 가용성·라이선스는 구현 시 확인 — 막히면 아래 대안.)
- 서버 언어는 Java 25 / Spring Boot; ML 추론은 Python 이 현실적 → **별도 프로세스(FastAPI)** 로 분리하고 Spring 이 HTTP 로 호출한다.

## Root Cause / Key Implementation Points

### 비전 서비스 (`ml/vision-service/`)
1. **구성**: `ml/vision-service/{app.py, scoring.py, requirements.txt, README.md, eval.py, tests/}` — FastAPI + `sentence-transformers` + `torch`(CPU). 모델은 기동 시 1회 로드(수 초), 요청당 추론은 CPU 에서 수백 ms 목표.
2. **API 계약**:
   - `GET /healthz` → `{ "status": "ok", "model": "<이름>", "version": "<서비스 버전>" }`.
   - `POST /v1/topic-fit` — multipart: `image`(파일, jpg/png/webp ≤10MB), 폼 필드 `topic`(한국어 명사구 문자열, 1~60자), `template`(예: `PHOTO_NOW`, 선택). 응답 200 `{ "fit": 0.0~1.0, "cosine": -1.0~1.0, "model": "…", "version": "…" }`; 오류 400(이미지 디코딩 실패/빈 topic), 413, 500.
3. **점수 산출(핵심)**: 원시 코사인은 값 범위가 좁아 해석이 어렵다 → **대조 후보 소프트맥스**: 주제 문장 `T`(=`"{topic} 사진"` 형태 프롬프트 템플릿)와 서비스가 가진 **대조 문장 집합 D**(일상 사진 주제 약 60개: 풍경, 음식, 사람 얼굴, 실내, 동물, 도로, 책상, 하늘, 신발 … + 색 이름 계열 — 파일 `ml/vision-service/distractors.ko.txt`)를 함께 인코딩해 `softmax(logit_scale * cos(image, [T ∪ D]))[T]` 를 `fit` 으로 삼는다(`logit_scale` 은 모델 기본값 ≈100, 설정 가능). `D` 에서 주제와 사실상 같은 문장(코사인 유사도 > 0.9)은 제외. `PHOTO_COLOR` 는 `"{color} 사진"`·`"{color} 물건"` 등 프롬프트 앙상블(평균) 사용.
4. **프롬프트 앙상블**: 템플릿별 프롬프트 문자열 목록을 코드(`scoring.py`)에 상수로 둔다(예: `PHOTO_NOW`: `["{t}", "{t} 사진", "{t}이(가) 보이는 사진"]`) → 텍스트 임베딩 평균. 대안 경로(다국어 모델 사용 불가 시): `topic` 을 영어로 매핑한 사전 또는 LLM 번역으로 영어 CLIP 사용 — 구현 시 모델 가용성을 보고 선택하고 보고서에 기록.
5. **임계값 `τ`**: 평가셋(아래)에서 정밀도 우선 지점으로 선택해 설정값(`PERFECT_THRESHOLD`, `GOOD_THRESHOLD`)으로 두고 `/healthz` 응답이 아니라 **백엔드 설정**(`hawe.vision.perfect-threshold`, `good-threshold`)으로 보관한다(서비스는 점수만, 판정은 백엔드). 초깃값은 평가 전 임시(예: 0.6/0.35)이며 **평가 결과로 교체**한다.
6. **평가 도구** `eval.py`: 입력 CSV `path,topic,label`(label ∈ {fit, notfit}; 최소 200장 — 팀원 본인 사진·동의된 사진, 얼굴/위치 정보가 포함된 사진은 저장소에 커밋하지 않고 `ml/eval-data/`(gitignore)에 둠) → 출력: 혼동행렬, accuracy, precision/recall/F1, ROC-AUC, 임계값별 곡선 CSV, 추천 `τ`, **지연 시간(p50/p95)**. 결과를 `ml/vision-service/EVAL.md` 로 저장(발표용 "적합도 판정 정확도 N%").

### 백엔드
7. **스키마(V1 직접 수정)**: 신규 테이블 `submission_ai_reviews`
   `id bigint PK AI, submission_id bigint NOT NULL UNIQUE, kind varchar(20) NOT NULL DEFAULT 'TOPIC_FIT', status varchar(10) NOT NULL ('PENDING','DONE','SKIPPED','FAILED'), fit_score double NULL, badge varchar(30) NULL, comment varchar(100) NULL, model_version varchar(60) NULL, attempts tinyint NOT NULL DEFAULT 0, created_at datetime NOT NULL DEFAULT current_timestamp(), updated_at datetime NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(), FK submission_id → submissions(id)`.
8. **흐름**: `SubmissionService.submit` 가 사진 틀(`PHOTO_NOW`, `PHOTO_COLOR`)이고 `hawe.vision.enabled=true` 이면 커밋 후 `SubmissionCreatedEvent(submissionId)` 를 발행(`@TransactionalEventListener(phase = AFTER_COMMIT)`). 리스너 `TopicFitReviewListener` 가 `@Async("visionExecutor")` 로 `TopicFitService.review(submissionId)` 를 호출: ① 행을 `PENDING` 으로 upsert ② 미션 `params` 에서 주제 명사구 추출(`PHOTO_NOW`→`subject`, `PHOTO_COLOR`→`color`; 없으면 `SKIPPED`) ③ `storage.load(key)` 로 바이트 읽기 ④ 비전 서비스 호출(타임아웃 연결 3초/읽기 10초) ⑤ 판정 → 행 `DONE` 저장. 실패는 `attempts++` 후 `FAILED`(재시도 1회: 같은 비동기 작업 안에서 2초 후 1회). **제출 응답/지연에는 영향이 없다.**
9. **판정과 문구** (`TopicFitJudge`, 순수 클래스): `fit ≥ perfectThreshold` → `badge = "PERFECT_FIT"`(앱 표시: "오늘의 주제 찰떡상"), 코멘트 후보 풀에서 **제출 id 기반 결정적 선택**(예: "주제랑 딱 맞는 사진이에요!", "오늘 주제를 제대로 담았어요", "이 사진, 주제 그 자체예요"); `fit ≥ goodThreshold` → `badge = null`, 코멘트 "주제를 잘 담았어요" 류; 그 미만 → 둘 다 `null`(표시 없음 — **낮은 점수·부정 평가 문구 절대 금지**). 코멘트에 이모지를 쓰지 않는다(앱이 임시 아이콘을 붙임).
10. **응답**: `SubmissionResponse` 에 `AiReviewResponse aiReview`(record `{ String badge, String comment }`, `DONE` 이고 badge 또는 comment 가 있을 때만; 나머지 `null` → 생략) 추가. 피드/상세는 **배치 조회**(`AiReviewMapper.findBySubmissionIds(ids)`), 제출 직후 응답에는 항상 생략(비동기 미완). 접근 규칙은 기존 잠금과 동일(제출한 사람만 피드를 보므로 별도 처리 불필요).
11. **FileStorage.load**: 인터페이스에 `byte[] load(String key)` 추가, `LocalFileStorage` 구현(루트 밖 접근 방지 `normalize().startsWith(root)`), 테스트용 목 영향 확인. S3 전환 시 같은 메서드를 구현하면 된다.
12. **비동기 인프라**: `@EnableAsync` + `ThreadPoolTaskExecutor` 빈 `visionExecutor`(core 2, max 4, queue 100, 거부 시 `CallerRunsPolicy` 금지 → 로그 후 버림: 제출 요청 스레드를 막지 않기 위해 `AbortPolicy` + 리스너 try/catch 로 `SKIPPED` 기록). 기동 시 비전 서비스가 죽어 있어도 서버는 정상 기동(요청 시에만 호출).
13. **설정**: `hawe.vision.enabled=false`(기본), `base-url=http://localhost:8001`, `perfect-threshold`, `good-threshold`, `connect-timeout`, `read-timeout`, `enabled-templates=PHOTO_NOW,PHOTO_COLOR`. 비활성 시 `submission_ai_reviews` 행도 만들지 않고 응답 `aiReview` 도 항상 생략.
14. **삭제 연쇄**: `GroupMapper` 에 `deleteAiReviewsByGroupId` 추가 후 `GroupService.deleteGroupCascade` 에서 `submissions` 삭제 **앞**에 호출(FK), 8단계 `WithdrawMapper` 의 내 제출물 삭제 앞에도 동일 삭제 추가.
15. **개인정보**: 사진은 우리 비전 서비스(내부)로만 전송, 비전 서비스는 **사진을 디스크에 저장하지 않고** 로그에 이미지·주제 문자열을 남기지 않는다(요청 id 와 지연만). 제3자 API 호출 금지.

## Files / Functions to Change

| 파일 | 변경 |
|---|---|
| `ml/vision-service/*` (신규) | FastAPI 앱, 점수 산출, `distractors.ko.txt`, `eval.py`, `tests/`(pytest: 점수 단조성·입력 검증·이미지 디코딩 실패), `requirements.txt`(버전 고정), `README.md`(실행: `python -m venv`, `uvicorn app:app --port 8001`), `EVAL.md`(평가 결과 템플릿) |
| `.gitignore` | `ml/eval-data/`, `ml/**/.venv/`, 모델 캐시 |
| `V1__initial_schema.sql` | `submission_ai_reviews` |
| `global/storage/FileStorage.java`, `LocalFileStorage.java` | `load(key)` |
| `submission/service/SubmissionService.java` | 사진 틀 제출 후 이벤트 발행, `feed/detail` 에 `aiReview` 배치 |
| `submission/domain/SubmissionCreatedEvent.java` (신규), `submission/service/TopicFitReviewListener.java`, `TopicFitService.java`, `TopicFitJudge.java` (신규) | 비동기 심사 |
| `submission/dao/AiReviewMapper.java`, `AiReviewRow.java` (신규) | upsert/조회 |
| `submission/dto/SubmissionResponse.java`, `AiReviewResponse.java` | 필드 |
| `global/config/VisionProperties.java`, `AsyncConfig.java` (신규), `application.properties` | 설정·실행기 |
| `group/dao/GroupMapper.java`, `group/service/GroupService.java`, `withdraw/dao/WithdrawMapper.java`, `withdraw/service/WithdrawService.java` | 삭제 연쇄 |
| 테스트 | 아래 |
| 문서 | `PROJECT_OVERVIEW.md` §6 AI 역할(**원칙 수정 + 이 기능 설명**), `MISSIONS_API.md` §3-3 응답에 `aiReview`·§8/§11-1 정책 갱신, `CONTRACT_DECISIONS.md` §6, `BACKEND_DB.md`, 신규 `docs/BACKEND/VISION_TOPIC_FIT.md`(구조도·계약·임계값·평가 방법·한계·개인정보), Postman |

## Implementation Steps

1. 비전 서비스 먼저(단독으로 `curl -F image=@x.jpg -F topic=창밖 풍경`). 2. `eval.py` 로 평가셋을 만들어 `τ` 산출 → `EVAL.md`. 3. 백엔드: V1 수정 → **로컬 DB 백업·재생성**, `FileStorage.load`, 이벤트/리스너/서비스/판정. 4. 응답 배치 조회, 삭제 연쇄. 5. 비활성 기본값에서 전체 테스트 통과. 6. `.\mvnw.cmd clean test -q`. 7. 문서.

## Constraints

- **기본 비활성**(`hawe.vision.enabled=false`) — 비활성이면 기존 동작·응답·테스트 불변. 비전 서비스 장애가 제출/피드를 절대 실패시키지 않는다.
- 사용자에게 **점수·순위·낮은 평가를 노출하지 않는다**. 도장/지각/제출 가능 여부에 AI 판정을 쓰지 않는다. 제3자 AI API 로 사진 전송 금지.
- 지정된 사진 틀만 대상(`PHOTO_GALLERY`·그림·글 제외). 새 마이그레이션 금지(V1 직접 수정). 평가용 개인 사진을 저장소에 커밋하지 않는다. 커밋/푸시·지침 파일 수정 금지.

## Edge Cases

- 비전 서비스 다운/타임아웃/500: `FAILED`(attempts=2), 응답에 `aiReview` 없음, 제출은 정상. 앱이 이후 새로고침해도 계속 없음(재처리 스케줄러는 만들지 않음 — 필요 시 후속).
- 사진이 아닌 파일은 이미 제출 단계에서 415. 손상 이미지 → 비전 서비스 400 → `FAILED`.
- 같은 제출에 이벤트가 두 번 와도 `UNIQUE(submission_id)` + upsert 로 한 행.
- 제출 후 곧바로 그룹 삭제/탈퇴(심사 중): 심사 작업이 사라진 제출을 만나면(`findDetailById` null / FK 위반) 조용히 종료.
- 같은 주제에 매우 일반적인 사진(검은 화면, 흐림) → 낮은 점수 → 표시 없음.
- `params` 가 비었거나 `subject` 가 한 글자 → `SKIPPED`.
- 코멘트 후보 선택은 제출 id 로 결정적(같은 제출 새로고침 시 문구가 바뀌지 않음).
- 임계값 변경 시 이미 `DONE` 인 행은 저장된 `badge` 를 유지(재판정 안 함) — `fit_score` 는 저장돼 있으므로 필요 시 후속 배치로 재판정 가능.
- 시계/타임존: `created_at/updated_at` 은 DB 시각.

## Regression Risks

- `FileStorage` 인터페이스에 메서드 추가 → 구현 클래스/Mockito 목/익명 구현 테스트 컴파일 수정.
- `SubmissionService.submit` 은 핵심 경로 — 이벤트 발행 실패가 제출을 롤백시키면 안 됨(`AFTER_COMMIT` 이후 리스너 예외는 제출 응답에 영향 없음; 발행 자체는 try/catch).
- 삭제 연쇄 누락은 런타임 FK 위반 → 그룹 삭제/탈퇴 통합 테스트에 `submission_ai_reviews` 행 포함.
- `SubmissionResponse` 레코드 필드 추가 → 호출부/테스트 수정.
- `@EnableAsync` 도입이 다른 `@Async` 사용처(현재 없음)에 영향 없음. 테스트에서 비동기가 불안정하면 `visionExecutor` 를 동기 실행기로 대체하는 테스트 설정 제공.

## Tests Required

- 비전 서비스 pytest: 같은 이미지에서 주제 일치 > 불일치 점수 단조성(소수 샘플), 입력 검증(빈 topic/손상 이미지), `fit ∈ [0,1]`.
- `TopicFitJudgeTests`: 임계값 경계(≥/<), 코멘트 결정성, 이모지·부정 문구 없음.
- `TopicFitServiceTests`(Mockito + `MockRestServiceServer`): 정상(DONE, badge), 다운(FAILED, 재시도 1회), `params` 없음(SKIPPED), 비활성 시 아무것도 안 함.
- `AiReviewMapperTests`(실 DB): upsert/배치 조회/UNIQUE.
- MockMvc/통합: 사진 제출 → (비동기 완료 대기: `Awaitility` 의존성을 쓰지 말고 폴링 헬퍼) → `GET /missions/{id}/submissions` 에 `aiReview.badge` 가 나타남(비전 서비스 모킹), 비활성이면 필드 없음, 응답 JSON 어디에도 `fit`/`score` 키가 없음, 그룹 삭제/탈퇴에서 FK 오류 없음.
- `LocalFileStorageTests`: `load` 정상/루트 밖 key 거부/없는 파일.

## Validation Steps

`.\mvnw.cmd clean test -q` 통과 기록(비활성 기본), 비전 서비스 `pytest` 통과, `EVAL.md` 에 평가 수치(데이터 수, 정확도, AUC, 선택한 τ, p50/p95 지연) 기록, `rg -i "fit_score|\"fit\"" src/main/java/**/dto` → 응답 DTO 에 점수 필드 없음 확인, Postman 파싱·한글, `git diff --check`. 로컬 종단 스모크(비전 서비스 기동 → 사진 제출 → 피드에 배지)는 가능하면 수행, 아니면 "미검증". 보고서에 DB 재생성·모델/버전·한계(다국어 CLIP 색 판정 약함 등) 명시.

## Potential Merge Conflicts

`SubmissionService`/`SubmissionResponse`/`SubmissionMapper`(B0·B1·B5·B10 순차 — 사진 심사는 B0 뒤), `FileStorage`/`LocalFileStorage`(B4 와 겹침), `GroupMapper`/`GroupService`/`WithdrawMapper`(삭제 연쇄), `V1`, `application.properties`, `docs/FEATURE/PROJECT_OVERVIEW.md`.
