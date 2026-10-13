# B1 그룹 내 프로필 리뷰

대상: 미커밋 변경(`git diff` + 신규 파일), `.agent/plan.md`, `.agent/report.md`. 코드는 수정하지 않았다.

## 확인한 것

- 스키마: `group_members` 에 컬럼 3개 + KEY/FK 추가, `characters` FK 는 V1 에서 정상 적용(report 상 Flyway 재적용·170 테스트 통과).
- 표시 CASE 는 `GroupMemberMapper` 상수 한 곳에만 정의, 4개 쿼리(`findMembersByGroupId`, `findMemberPreviewsByGroupIds`, `SubmissionMapper`, `CommentMapper`)가 참조. 닉네임·캐릭터·사진이 같은 조건(`nickname_override IS NOT NULL`)으로 한 묶음 선택됨.
- 제출·댓글은 `LEFT JOIN group_members`(UNIQUE(group_id,user_id) 라 행 중복 없음), 댓글은 `co.user_id`(댓글 작성자) 기준 — 계획과 일치. 나간 작성자는 기본 프로필.
- `GroupProfileService`: 그룹 404 → 비멤버 403 순서, 닉네임 검증이 파일 저장보다 앞, 롤백 시 새 파일 삭제 / 커밋 후 이전 파일 삭제, DELETE 멱등, 불변식(캐릭터형은 image NULL, 사진형은 character NULL) 유지. 응답은 기존 `MemberProfileResponse` 재사용.
- `hasGroupProfile` 전달 경로(Row → Response → `GroupService.listMembers`) 정상. 다른 생성 호출처 없음.
- 범위: `GroupService.leaveGroup`/`deleteGroupCascade`, 기본 프로필 API, 앱, 알림 미변경. 불필요한 리팩터링 없음. 문서·Postman 갱신됨.
- 병렬 작업 충돌 지점은 계획의 "Potential Merge Conflicts" 와 동일(V1, group mappers/service, Submission/CommentMapper). 실제 충돌은 현 시점에서 확인 불가(HEAD 기준 변경 없음).

## P0

없음.

## P1

없음.

## P2

### File / Location
`src/main/java/com/sapphire/hawe/group/service/GroupProfileService.java` (`updateImage` 검증 메시지), `group/controller/GroupProfileController.java` (`"Attach one image file"`)

### Problem
`VALIDATION_ERROR` 메시지가 영어("Nickname must be 2 to 10 characters", "Attach an image file", "Attach one image file")다. 같은 로직을 복사한 원본 `ProfileService`/`ProfileController` 와 기존 오류 코드 메시지는 한국어다.

### Why It Matters
클라이언트에 그대로 노출되는 오류 문구라 언어가 섞인다. 기능 문제는 아니다.

### Recommended Fix
원본 `ProfileService` 와 같은 한국어 문구로 교체(예: "사진 파일을 첨부해 주세요", "사진 파일을 하나 첨부해 주세요"; 닉네임은 "닉네임은 2~10자여야 합니다" 등). 인코딩 문제가 이유였다면 UTF-8 로 저장되는지만 확인.

---

### File / Location
`src/test/java/com/sapphire/hawe/group/**` (테스트 보강)

### Problem
계획의 테스트 항목 중 아래가 확인되지 않는다: ① 10MB 초과 → 413 (컨트롤러/서비스 어느 쪽도 해당 케이스 없음; `imageValidationPrecedesStorageAndUpdate` 가 포함하는지 확인 필요), ② 기본 프로필 변경(`PATCH /me/profile`)이 그룹 프로필이 있는 그룹에 반영되지 않음, ③ 컨트롤러 통합 테스트가 단일 멤버 그룹이라 `content[0]` 만 검증 — 다른 멤버의 프로필이 섞이지 않는지(멤버 2명 이상) 미검증.

### Why It Matters
①은 413 경로 회귀를 못 잡고, ②는 계약의 핵심 규칙(§2-2)이다. 쿼리 규칙상 현재 동작은 맞을 가능성이 높지만 보장하는 테스트가 없다.

### Recommended Fix
`GroupProfileServiceTests` 에 `file.getSize() > 10MB` 케이스(저장·갱신 미호출, `FILE_TOO_LARGE`) 추가. `GroupMemberMapperTests` 또는 컨트롤러 테스트에 "그룹 프로필 설정 후 `users.nickname` 변경 → 그룹 응답 불변, 다른 그룹은 변경 반영" 케이스 추가. 멤버 2명 케이스는 `GroupMemberMapperTests` 가 이미 일부 다룬다면 생략 가능.

## 미검증(보고서에도 명시됨)

- 실서버 기동 스모크 미실행(MockMvc 로 대체). 공유 DB 는 V1 변경 때문에 별도 재생성 필요 — 병합 시 팀 공지.

## 결론

**승인 가능** — P0/P1 없음. P2 두 건은 선택적 후속 정리(병합 차단 아님). 병합 전에 B5 시작 전 커밋 순서(V1·SubmissionMapper·CommentMapper 충돌)만 지킬 것.
