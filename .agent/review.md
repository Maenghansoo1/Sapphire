# B10 Spring 연동 리뷰

대상: 미커밋 작업 트리(HEAD `fc4295e` 기준), `.agent/plan.md`, `.agent/report.md`.
보고서상 `./mvnw.cmd clean package -q` 296 tests 통과. 직접 재실행은 하지 않았고 코드 정독으로 확인했다.

## 요구사항 충족 (확인됨)

- 이벤트 → `@Async("visionExecutor")` + `AFTER_COMMIT` 리스너 → `TopicFitService.review`. `INSERT IGNORE` 멱등 가드, 재시도 1회, 로그 후 버림 거부 핸들러, 틀별 임계값(0.024/0.017, 0.132/0.104), 점수 비노출(`AiReviewResponse` 는 badge/comment 만), `enabled=false` 면 응답에서 조회도 생략.
- 삭제 연쇄: `deleteAiReviewsByGroupId`(guesses 뒤, submissions 앞), `deleteAiReviewsOnMySubmissions`(submissions 앞). 순서 테스트와 DB 테스트 갱신됨.
- `FileStorage.load` 루트 이탈 방지, V1 테이블, 문서 갱신(`VISION_TOPIC_FIT.md`, PROJECT_OVERVIEW §6 등) 있음. `ml/`, `CLAUDE.md`, `AGENTS.md`, `pom.xml` 변경 없음.

## P0

없음.

## P1

없음.

## P2

### 테스트 / 계획 대비 누락 (`src/test/.../submission/`)
#### Problem
계획의 Tests Required 중 아래가 없다.
- `AiReviewMapperTests`(실 DB): `findBySubmissionIds` 가 DONE 만 반환하는지, `insertPendingIfAbsent` 둘째 호출 0 / 없는 submission_id(FK 무시) 0 인지 직접 검증하는 테스트가 없다(통합 테스트가 DONE 한 경로만 간접 확인).
- `TopicFitReviewListenerTests`: `enabled=false` 면 서비스 미호출, 서비스 예외를 삼키는지.
- `SubmissionServiceTests`: 생성자 인자만 바뀌었고, 사진 틀 제출 시 `SubmissionCreatedEvent` 발행 / `PHOTO_GALLERY`·그림·글 틀 미발행 / 발행 예외가 제출을 깨지 않음 / `enabled=true` 일 때 `findBySubmissionIds` 가 `feed` 에서 **한 번만** 호출(N+1 없음) / `enabled=false` 면 호출 0회 테스트가 없다.
#### Why It Matters
핵심 경로인 `submit` 에 추가된 이벤트 발행 조건(`key != null && 사진 2종`)과 비활성 시 조회 생략은 이 작업의 주요 회귀 가드인데 단위 수준에서 보호되지 않는다. 나중에 B4·A7 작업 중 조건이 깨져도 통과할 수 있다.
#### Recommended Fix
위 세 군데에 최소 테스트를 추가한다(각 2~4개). 새 의존성 불필요.

### `TopicFitService.review` — DONE 저장 실패가 비전 재호출로 이어짐 (`TopicFitService.java`, 재시도 루프)
#### Problem
`reviews.complete(DONE, …)` 가 같은 `try`(`catch RuntimeException`) 안에 있어, DB 오류로 DONE 저장이 실패하면 "비전 실패"로 취급되어 재시도(두 번째 비전 호출)와 FAILED 저장을 시도한다.
#### Why It Matters
DB 장애 시 불필요한 추론 호출 1회와 오해를 부르는 로그/상태가 생긴다. 영향은 작다(발생 드묾, 데이터 손상 없음).
#### Recommended Fix
`client.call` 과 `load` 만 재시도 대상 `try` 에 두고, 판정·`complete(DONE)` 은 try 밖으로 뺀다(바깥 catch 가 로그 처리). 필수는 아님.

### 참고 (조치 불필요)
- Codex 가 로컬 `hawe` DB 테이블을 drop 후 Flyway 로 재생성했다(사용자·그룹·제출 0건 확인, `.agent/hawe-before-b10.sql` 백업, `.agent/` 는 gitignore). 보고서에 근거가 있어 수용. 다른 사람은 pull 후 각자 DB 를 재생성해야 한다.
- 극소 경합(삭제 직후 PENDING 삽입으로 FK 실패)은 계획대로 문서화만 됨.
- `TopicFitIntegrationTests` 는 5초 타임아웃 후 `aiReview()` 가 null 이면 NPE 로 실패한다(느린 CI 에서만 영향). 실패 원인이 명확하도록 `assertNotNull` 한 줄이면 충분.

## 병렬 작업과의 충돌
B4 가 이 변경 뒤에 같은 `FileStorage`·`GroupService.deleteGroupCascade`·`WithdrawService` 를 건드린다 → 이 작업 커밋·푸시 후 시작. B11 은 `AsyncConfig` 에 `insightExecutor` 를 추가하면 된다. A7 은 `aiReview.badge/comment` 만 사용.

## 결론: 수정 후 재리뷰 필요 (경미)

기능·안전성에는 막는 결함이 없다. 위 P2 의 누락 테스트(특히 `SubmissionServiceTests` 이벤트 발행/비활성 조회 생략, `AiReviewMapperTests`, 리스너 테스트)를 추가하면 승인 가능. 테스트만 보강하면 되므로 사용자가 이 상태로 커밋하기로 한다면 그것도 수용 가능한 수준이다.
