# 7단계 백엔드 구현 보고

## 변경 파일과 내용

- `src/main/resources/db/migration/V1__initial_schema.sql`: 같은 제출·사용자·스티커 조합의 중복을 막는 유니크 키 추가.
- `src/main/java/com/sapphire/hawe/reaction/`: 5종 스티커 토글 API, 종류별 집계와 사용자 ID 목록 구현.
- `src/main/java/com/sapphire/hawe/comment/`: 댓글 작성·목록·본인 삭제 API, 멘션 검증과 중복 제거, 이미지 저장 및 트랜잭션 후 파일 정리 구현.
- `src/main/java/com/sapphire/hawe/submission/service/SubmissionService.java`: 공통 접근 검사 추출, 피드 페이지 단위 반응·댓글 수 일괄 조회, 상세 집계 반영.
- `src/test/java/com/sapphire/hawe/{reaction,comment}/` 및 `submission/service/SubmissionServiceTests.java`: 서비스·매퍼·MockMvc 회귀 테스트와 피드 일괄 조회 검증.
- `docs/BACKEND/{API_DESIGN,BACKEND_DB,CONTRACT_DECISIONS,CONTRACT_WORK_PLAN,MISSIONS_API}.md`와 `docs/BACKEND/postman/HAWE_REACTION_COMMENT.postman_collection.json`: 실제 계약과 Postman 요청 갱신.

## 검증

- Java 25, `mvnw.cmd clean test -q`: 통과. 45개 테스트 스위트, 테스트 145개, 실패·오류 0개.
- 마지막 멘션 중복 제거 정리 후 `mvnw.cmd test -q -Dtest=CommentServiceTests,CommentControllerTests`: 통과, 5개 테스트.
- `mvnw.cmd package -q -DskipTests`: 통과, `target/hawe-0.0.1-SNAPSHOT.jar` 생성. 컴파일과 패키징 확인.
- Postman 컬렉션 JSON 파싱: 통과. `git diff --check -- docs/BACKEND src/main src/test`: 공백 오류 없음.
- 별도 lint/typecheck 스크립트는 없다. Maven 컴파일이 Java 타입 검사를 수행했다.
- 최종 변경 목록과 diff를 확인했다. 기존 `.agent/plan.md` 수정은 유지했고 편집하지 않았다. 커밋·푸시하지 않았다.

## DB 처리 및 남은 위험

- 로컬 개발 DB `localhost:3306/hawe` 재생성 전 사용자·미션·제출·반응·댓글은 각각 0행이었다. 백업: `C:\Users\kimch\AppData\Local\Temp\hawe-before-reactions-20261012-175532.sql` (18,860바이트). 백업 후 로컬 DB를 재생성했고 Flyway V1·V2 적용 및 통합 테스트가 통과했다.
- V1 체크섬이 바뀌었으므로 기존 V1을 적용한 다른 DB는 배포 전 별도 재생성 또는 마이그레이션 전략이 필요하다.
- 그룹 삭제 시 댓글 이미지 파일이 남는 문제는 기존 그룹 삭제 흐름의 후속 과제로, 이번 범위에서 다루지 않았다.
- Android·iOS 스티커·멘션·이미지 UI는 7A, 댓글·멘션 알림은 9단계 작업이다.
- 현재 텍스트 병합 충돌은 없다. 병렬 작업이 `SubmissionService`, V1 스키마, 계약 문서를 수정하면 해당 diff와 V1 체크섬을 함께 검토해야 한다.

## 가정과 계획 조정

- 계획의 백엔드·문서 범위를 유지했다. 댓글의 같은 멘션 ID는 입력 순서대로 한 번만 검증·저장하도록 했다.
