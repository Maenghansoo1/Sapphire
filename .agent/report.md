# 백엔드 5단계: 콕·똥뿌리기 보고

## 변경 파일과 동작

- `src/main/resources/db/migration/V1__initial_schema.sql`: `pokes.mission_id`의 NOT NULL/FK와 `(from_user_id, to_user_id, mission_id, type)` UNIQUE를 추가했습니다. V2는 변경하지 않았습니다.
- `src/main/java/com/sapphire/hawe/poke/{domain,dao,dto,service}/`: 현재 미션(그룹의 최소 슬롯 번호에서 최신 발행 미션)을 찾아 기록하고, 해당 미션의 대상 제출 여부를 검사합니다. 종류별 중복은 사전 조회와 DB UNIQUE로 막으며 동시 삽입 충돌을 `ALREADY_POKED`로 반환합니다. 응답에 `missionId`를 포함합니다. 현재 미션이 없으면 `NO_OPEN_MISSION`입니다.
- `src/main/java/com/sapphire/hawe/mission/{dto,service}/`: 현재·개별 미션 응답의 각 `members[]`에 요청자가 보낸 종류 `pokedByMe`를 한 번의 조회로 채웁니다. 순서는 `poke`, `fling`이며 본인 항목과 기록 없는 항목은 `[]`입니다.
- `src/main/java/com/sapphire/hawe/group/service/GroupService.java`: 그룹 삭제 때 pokes를 missions보다 먼저 삭제해 새 FK와 충돌하지 않게 했습니다.
- `src/test/java/com/sapphire/hawe/`의 poke·mission·group·time 관련 테스트: 서비스 검사 순서와 중복 충돌, 실 MariaDB의 FK/UNIQUE·미션별 조회, 인증/검증/응답/조회 MockMvc 흐름을 검증했습니다. `PokeControllerTests`를 새로 추가했습니다.
- `docs/BACKEND/{API_DESIGN,BACKEND_DB,MISSIONS_API,CONTRACT_DECISIONS,CONTRACT_WORK_PLAN}.md`와 `docs/BACKEND/postman/HAWE_POKE.postman_collection.json`: 구현 계약, 스키마, 요청 예시와 5단계 상태를 반영했습니다.

## 검증 결과

- Java 25 `mvnw.cmd clean test -q`: 통과. 테스트 108개, 실패 0, 오류 0, 건너뜀 0. 컴파일은 이 명령에 포함됩니다. 별도 lint/typecheck 명령은 이 Maven 프로젝트에 없습니다.
- Postman 컬렉션 `ConvertFrom-Json`: 통과. 한글 대체 문자 없음.
- `rg existsCurrentMissionSubmission src`: 잔여 사용처 없음.
- `git diff --check`: 통과. LF/CRLF 변환 경고만 출력됐습니다. 최종 diff는 백엔드 코드·테스트·문서와 이 보고서로 한정했습니다. 이미 있던 `.agent/plan.md`, `.agent/review.md` 변경 및 Cordova node_modules의 미추적 파일은 건드리지 않았습니다.
- 별도 실행 서버를 이용한 수동 HTTP 스모크는 미검증입니다. 동일 계약의 MockMvc 통합 테스트에서 인증, `poke`/`fling`, 중복 409, `pokedByMe`, 대상 제출 뒤 409를 검증했습니다.

## DB 재생성·남은 위험·가정

- 계획에 따라 로컬 `hawe` 개발 DB를 백업한 뒤 재생성했습니다. 백업: `C:\Users\kimch\AppData\Local\Temp\hawe-before-pokes-20261012-153131.sql` (20,813바이트). 재생성 전 사용자 2명, 미션 2개, 제출 4개가 있었습니다. 새 DB에 Flyway V1/V2가 적용됐고 전체 테스트가 통과했습니다. 이전 개발 데이터가 필요하면 이 백업에서 복원해야 합니다.
- 계획의 V1 직접 수정 결정을 따랐습니다. 이미 V1을 적용한 다른 DB는 이 변경과 checksum이 다르므로 그 환경의 별도 마이그레이션 또는 개발 DB 재생성이 필요합니다. 운영 DB에는 적용하지 않았습니다.
- 알림/FCM/notifications 저장은 9단계, 앱 버튼과 상태 표시는 5A 범위이므로 이번 변경에 넣지 않았습니다. 관련 파일과 Android/iOS/Cordova는 변경하지 않았습니다.
