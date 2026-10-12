# 8단계 백엔드 구현 보고

## 변경 파일·동작

- `src/main/resources/db/migration/V1__initial_schema.sql`, `auth/domain/User.java`, `auth/dao/UserMapper.java`, `auth/service/AuthService.java`: 기본 프로필 사진 key 컬럼·조회·업데이트와 `GET /api/me` 사진 URL 응답을 추가했다.
- `src/main/java/com/sapphire/hawe/profile/`: 닉네임 변경(`PATCH /api/me/profile`), 사진 등록·교체·삭제(`PUT/DELETE /api/me/profile-image`) API를 추가했다. 이미지 검증, 롤백 시 새 파일 삭제, 커밋 후 이전 파일 삭제를 적용했다.
- `src/main/java/com/sapphire/hawe/withdraw/`: 회원 탈퇴(`DELETE /api/me`)와 `OWNER_MUST_TRANSFER` 오류를 추가했다. 그룹장 제한을 먼저 검사하고, 그룹 나가기 후 FK 순서에 맞춰 제출물·댓글·멘션·반응·정답 시도·콕·알림·계정을 삭제한다. 파일 key는 삭제 전 수집하여 커밋 후 정리한다.
- `global/security/JwtAuthenticationFilter.java`, `SecurityConfig.java`: 보호된 요청에서 토큰 사용자 존재를 확인한다. 탈퇴한 계정의 액세스 토큰은 401 `INVALID_TOKEN`; refresh는 기존 사용자 조회로 거절된다.
- `group/dao/`, `group/service/GroupService.java`, `submission/dao/`, `submission/service/SubmissionService.java`, `comment/dao/`, `comment/service/CommentService.java`: 멤버 목록·그룹 목록/미리보기·피드·댓글 작성자 프로필에 기본 사진 URL을 반영했다.
- `src/test/java/com/sapphire/hawe/{auth,global/security,group,profile,withdraw}/`: 매퍼·서비스·필터·MockMvc 검증을 추가하거나 생성자 변경을 반영했다.
- `docs/BACKEND/{API_DESIGN,BACKEND_DB,CONTRACT_DECISIONS,CONTRACT_WORK_PLAN}.md`, `docs/FEATURE/PROJECT_OVERVIEW.md`, `docs/BACKEND/postman/HAWE_ME.postman_collection.json`: 8단계 계약·현황·Postman 요청을 갱신했다.

## 검증 결과

- Java 25, `mvnw.cmd clean test -q`: **49개 스위트, 156개 테스트, 실패 0, 오류 0**. 실 MariaDB·MockMvc 테스트가 프로필 URL 노출, 사진 교체·삭제, 탈퇴 종속 데이터 삭제·파일 정리·그룹장 제한·탈퇴 후 토큰 거부·같은 이메일 재가입을 확인했다.
- 그룹 목록 사진 URL 검증 추가 후 `mvnw.cmd test -q -Dtest=ProfileControllerTests`: 통과.
- `mvnw.cmd package -q -DskipTests`: 통과. 별도 lint/typecheck 명령은 없으며 Maven 컴파일이 타입을 검증했다.
- Postman JSON `ConvertFrom-Json` 파싱 및 대체 문자(`�`) 검색: 통과. `git diff --check -- src docs/BACKEND docs/FEATURE/PROJECT_OVERVIEW.md`: 공백 오류 없음.
- `new JwtAuthenticationFilter(...)` 호출부가 모두 3인자인지, 네 곳의 사람 표시 응답이 사진 key를 URL로 변환하는지 검색 확인했다. 최종 변경 파일 목록을 확인했고 커밋·푸시는 하지 않았다.
- 별도 서버를 띄워 수동 HTTP 요청은 하지 않았다. 위 MockMvc 테스트가 실제 보안 필터·컨트롤러·DB·로컬 파일 저장소 경로를 검증했다.

## DB·남은 위험

- 로컬 개발 DB `localhost:3306/hawe` 재생성 전 사용자·미션·제출·댓글·반응은 각각 **0행**이었다. 백업: `C:\Users\kimch\AppData\Local\Temp\hawe-before-me-20261012-190917.sql` (19,478바이트). 백업 후 로컬 DB를 재생성했고 Flyway V1·V2 적용을 전체 테스트로 검증했다.
- V1 체크섬이 다시 변경되었다. 기존 V1을 적용한 다른 DB에는 별도 재생성 또는 마이그레이션 계획이 필요하다.
- 혼자인 그룹을 탈퇴로 삭제할 때 그 그룹에 남아 있던 이전 멤버의 댓글 이미지 등은 기존 그룹 cascade의 파일 정리 범위 밖이다. 이번 탈퇴는 내 파일과 내 제출물에 달린 댓글 이미지를 수집해 삭제한다.
- 그룹 내 프로필, `PATCH /me/password`, 9단계 기기 토큰·알림 설정 테이블과 탈퇴 시 그 테이블 정리, Android/iOS 8A 연결은 이번 범위 밖이다.

## 가정·계획 조정·병합 가능 지점

- 계획의 기본 프로필·탈퇴 범위를 따랐다. 그룹 내 프로필은 만들지 않았고 `GroupService.leaveGroup`/`deleteGroupCascade`, `CharacterService`, V2 스키마는 변경하지 않았다.
- 파일 중복 파트는 400으로 처리한다. 테스트에서는 실제 그룹 역할 값 `owner`/`member`를 사용했다.
- 이번 시작 시 작업 트리에는 `.agent/plan.md` 변경만 있었다. 그 파일은 수정하지 않았다. 이전 단계가 커밋된 상태여서 7단계 미커밋 코드와 변경이 섞이지 않았다. 이후 그룹 내 프로필·인증·V1 스키마를 수정하는 작업과는 같은 파일에서 병합 검토가 필요하다.
