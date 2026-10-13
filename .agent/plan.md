# B1. 그룹 내 프로필 (백엔드)

> 원본 계획: `docs/해야될거/1번_찬희/B1-group-profile.md` (결정 근거: `docs/BACKEND/CONTRACT_DECISIONS.md` §2-1 "그룹 내 프로필", §2-2 "그룹 내 프로필 (16-1)"). 아래는 현재 코드를 확인해 구체화한 구현 계획이다. 이전 `.agent/plan.md`(8단계)는 이 파일로 덮어쓴다.
> 범위: 백엔드(`src/`, `docs/BACKEND/`)만. 앱(`android/`, `frontend/`)·알림·`ml/` 은 건드리지 않는다.

## Goal

그룹마다 **닉네임 + (캐릭터 또는 사진)** 한 벌의 "그룹 내 프로필"을 설정·교체·해제할 수 있다. 설정돼 있으면 그 그룹의 **사람 표시 응답 전부**(그룹 멤버 목록, 그룹 목록/상세의 멤버 미리보기, 초대 미리보기, 피드, 제출 상세, 댓글)가 그 프로필로 보이고, 없으면 기본 프로필(users 의 닉네임/캐릭터/사진)로 보인다.

```text
PUT    /api/groups/{groupId}/members/me/profile         JSON { "nickname": "과대표 하늘", "characterId": 3 }  → 200 프로필 객체
PUT    /api/groups/{groupId}/members/me/profile-image   multipart: file(이미지) + nickname(text)              → 200 프로필 객체
DELETE /api/groups/{groupId}/members/me/profile         → 204 (기본 프로필로 복귀, 사진 파일 삭제, 멱등)
// 프로필 객체 (= 기존 group/dto/MemberProfileResponse)
{ "userId": 3, "nickname": "과대표 하늘", "characterKey": "gomdol", "profileImageUrl": null }
```
`GET /api/groups/{groupId}/members` 항목에 `hasGroupProfile`(boolean) 추가.

## Findings

(모두 코드로 확인함. 작업 트리 clean, 7·8단계 커밋 완료 — `profile/` 패키지, `users.profile_image_url`, 4개 쿼리의 `u.profile_image_url` 존재.)

- `group_members`(V1 라인 42~53): `id, group_id, user_id, role, joined_at`, `UNIQUE(group_id,user_id)`. 그룹 내 프로필 컬럼 없음. Flyway 는 `V1__initial_schema.sql`, `V2__seed_characters.sql` 두 개뿐(V1 직접 수정 방침).
- 사람을 보여 주는 쿼리는 **정확히 4곳**(`grep "u.nickname|svg_key|profile_image_url"` 로 확인; `CalendarMapper` 의 `target_nickname` 은 대상 지정 미션 치환용이라 범위 밖):
  1. `GroupMapper.findMemberPreviewsByGroupIds` (`group/dao/GroupMapper.java:57`) — `group_members gm JOIN users u LEFT JOIN characters c`. 사용처 `GroupService.listMyGroups`(L86~98), `GroupService` 그룹 상세/초대 미리보기(`memberProfiles` L278, `toMemberProfile` L283) → `GroupMemberPreviewRow`.
  2. `GroupMemberMapper.findMembersByGroupId` (`GroupMemberMapper.java:37`) — 이미 `gm` 기반. `GroupMemberRow` → `GroupService.listMembers`(L190~206)에서 `new GroupMemberResponse(...)`; 이미지 key 는 여기서 `storage.publicUrl` 로 변환.
  3. `SubmissionMapper.FIELDS/JOINS` (`submission/dao/SubmissionMapper.java:12`) — `submissions s JOIN missions m JOIN users u LEFT JOIN characters c`, `gm` 없음. `findDetailById`, `findFeedByMissionId` 가 사용. `SubmissionRow` → `SubmissionService.response`(L207~215)가 `MemberProfileResponse` 생성. `FIELDS/JOINS` 를 다른 곳에서 참조하지 않음(확인함).
  4. `CommentMapper.FIELDS/JOINS` (`comment/dao/CommentMapper.java:13`) — `comments co JOIN users u LEFT JOIN characters c`. `m.group_id` 가 없어 `submissions s`, `missions m` 조인이 필요. `CommentRow` → `CommentService`(L125).
- 3·4 의 작성자는 **이미 그룹을 나갔을 수 있다** → `gm` 은 반드시 `LEFT JOIN`, `gm` 행이 없거나 `nickname_override IS NULL` 이면 기본 프로필.
- 캐릭터: `CharacterMapper.findById(Long)` → `ProfileCharacter(id, name, svg_key)`. `CharacterErrorCode.CHARACTER_NOT_FOUND`(404).
- 오류 코드: `GroupErrorCode.GROUP_NOT_FOUND`(404), `NOT_GROUP_MEMBER`(403); `CommonErrorCode.FILE_TOO_LARGE`(413), `CommonErrorCode.VALIDATION_ERROR`(400); `SubmissionErrorCode.UNSUPPORTED_FILE_TYPE`(415).
- 파일: `FileStorage.store(dir, bytes, ext)` / `delete(key)` / `publicUrl(key)`, `FileTypeDetector.detect(bytes)`(IMAGE 만 허용). **복사할 패턴**: `profile/service/ProfileService.uploadImage/deleteImage` — 검증(null/empty → 400, >10MB → 413, 비이미지 → 415) → `storage.store` → 롤백 시 새 파일 삭제(`afterCompletion != COMMITTED`) → 이전 파일은 `deleteAfterCommit`. `profile/controller/ProfileController.upload` — `@PutMapping(consumes = MULTIPART_FORM_DATA_VALUE)`, `@RequestParam(value="file", required=false) List<MultipartFile>`, 크기 != 1 이면 400.
- 닉네임 규칙: `ProfileUpdateRequest` = `@NotBlank @Size(min=2,max=10)` + compact ctor 에서 trim.
- `GlobalExceptionHandler` 가 `MissingServletRequestParameterException`/`HttpMediaTypeNotSupportedException`/`MaxUploadSizeExceededException`/`MultipartException` 이미 처리 → 별도 핸들러 불필요.
- `GroupService.leaveGroup`/`deleteGroupCascade` 는 멤버십 행을 지우므로 그룹 내 프로필 컬럼도 함께 사라진다. 그룹 내 프로필 **사진 파일**은 고아가 된다 → B4 범위. 이번에는 `DELETE …/profile` 과 교체 시 파일 삭제만.
- `GroupMember` 도메인은 `findByGroupIdAndUserId` 한 곳에서만 채워진다(서비스/테스트의 setter 호출은 영향 없음). `GroupMemberResponse` 는 main 에서 `GroupService:200` 한 곳에서만 생성, 테스트에서 직접 생성하는 곳 없음(grep 확인).
- MyBatis 는 underscore→camelCase 매핑(기존 `character_key` → `characterKey`)이므로 새 별칭은 `group_profile`.

## Root Cause / Key Implementation Points

1. **스키마(V1 직접 수정)** — `group_members` 에 추가:
   - `nickname_override varchar(50) DEFAULT NULL`
   - `character_id_override bigint(20) DEFAULT NULL` + `KEY fk_group_members_character (character_id_override)` + `CONSTRAINT fk_group_members_character FOREIGN KEY (character_id_override) REFERENCES characters (id)` (`characters` 테이블이 V1 에서 `group_members` 보다 **앞에** 생성되는지 확인하고, 뒤라면 FK 를 그 뒤로 옮기거나 `ALTER TABLE` 로 추가 — 파일 내 순서 확인 필수)
   - `profile_image_url_override varchar(500) DEFAULT NULL` (파일 key)
   - 불변식(서비스가 보장, DB CHECK 없음): `nickname_override` NULL ⇒ 나머지 둘도 NULL. NOT NULL 이면 `character_id_override` / `profile_image_url_override` 중 **정확히 하나만** NOT NULL.
2. **표시 프로필 규칙(4개 쿼리 공통, 한 묶음)**: `gm.nickname_override IS NOT NULL` 이면 `(nickname_override, gc.svg_key, profile_image_url_override)`, 아니면 `(u.nickname, c.svg_key, u.profile_image_url)`. 세 값을 같은 조건으로 고른다(닉네임만 그룹 값·사진은 기본 값 같은 혼합 금지). 사진형 override 는 `character_id_override` 가 NULL 이라 `gc.svg_key` 가 NULL → `characterKey` null + `profileImageUrl` 값. 정상.
   - `GroupMemberMapper` 인터페이스에 public 상수 문자열을 **한 곳에만** 정의하고 나머지 3곳이 `GroupMemberMapper.XXX` 로 참조:
     ```java
     String DISPLAY_NICKNAME   = "CASE WHEN gm.nickname_override IS NOT NULL THEN gm.nickname_override ELSE u.nickname END AS nickname";
     String DISPLAY_CHARACTER  = "CASE WHEN gm.nickname_override IS NOT NULL THEN gc.svg_key ELSE c.svg_key END AS character_key";
     String DISPLAY_IMAGE      = "CASE WHEN gm.nickname_override IS NOT NULL THEN gm.profile_image_url_override ELSE u.profile_image_url END AS profile_image_url";
     String GROUP_CHARACTER_JOIN = "LEFT JOIN characters gc ON gc.id = gm.character_id_override ";
     ```
     (상수 이름은 자유. 전제 별칭: `gm`=group_members, `u`=users, `c`=기본 characters, `gc`=그룹 characters. 네 쿼리 모두 이 별칭과 충돌하지 않음을 확인함.)
3. **API**: 신규 `GroupProfileController`(`/api/groups/{groupId}/members/me`) + `GroupProfileService` — `GroupController`/`GroupService` 를 더 키우지 않는다. 공통 선행 검사: `groupMapper.findById` null → 404 `GROUP_NOT_FOUND`, `groupMemberMapper.findByGroupIdAndUserId` null → 403 `NOT_GROUP_MEMBER` (이 호출이 이전 override 값 조회도 겸함). 변경은 모두 `@Transactional`, 이전 사진 파일 삭제는 커밋 후.
   - `PUT …/profile`: 닉네임은 DTO 검증, `characterMapper.findById(characterId)` null → 404 `CHARACTER_NOT_FOUND`. `setGroupProfile(groupId, userId, nickname, characterId, null)`. 이전 `profileImageUrlOverride` 가 있었으면 커밋 후 삭제. 응답 `characterKey = character.getSvgKey()`, `profileImageUrl = null`.
   - `PUT …/profile-image`: 닉네임(trim, 2~10자, null/공백/범위 밖 → 400 `VALIDATION_ERROR`) → 파일 검증(ProfileService.uploadImage 와 동일, 디렉터리 `"group-profiles"`) → `storage.store` + 롤백 시 삭제 등록 → `setGroupProfile(groupId, userId, nickname, null, key)` → 이전 key 커밋 후 삭제. 응답 `characterKey = null`, `profileImageUrl = storage.publicUrl(key)`.
   - `DELETE …/profile`: `setGroupProfile(groupId, userId, null, null, null)`; 이전 key 있으면 커밋 후 삭제; 이미 프로필이 없어도 204(멱등).
   - 응답 DTO 는 새로 만들지 않고 `group/dto/MemberProfileResponse` 재사용.
4. **`hasGroupProfile`**: `GroupMemberRow` 에 `boolean groupProfile`(별칭 `group_profile` = `gm.nickname_override IS NOT NULL`), `GroupMemberResponse` 레코드에 `hasGroupProfile` 추가(JSON 필드 순서는 계약 예시대로 `profileImageUrl` 뒤·`role` 앞), `GroupService.listMembers` 생성 호출 수정.
5. 모든 엔드포인트는 `members/me` — 본인 프로필만 다룬다. 다른 사람 프로필 수정 API 없음.
6. 기본 프로필 변경(`PATCH /me/profile`, `PATCH /me/character`, `/me/profile-image`)이 그룹 프로필 있는 그룹에 반영되지 않는 것은 쿼리 규칙(2)이 보장 → 추가 코드 없음.

## Files / Functions to Change

| 파일 | 변경 |
|---|---|
| `src/main/resources/db/migration/V1__initial_schema.sql` | `group_members` 컬럼 3개 + KEY/FK (Key Point 1). `V2` 불변. 새 마이그레이션 금지 |
| `group/domain/GroupMember.java` | `nicknameOverride`, `characterIdOverride`, `profileImageUrlOverride` 필드 + getter/setter |
| `group/dao/GroupMemberMapper.java` | ① 표시 CASE 상수 4개(Key Point 2) ② `findByGroupIdAndUserId` SELECT 에 새 컬럼 3개 추가 ③ `@Update int setGroupProfile(@Param groupId, userId, nickname, characterId, imageKey)` — `UPDATE group_members SET nickname_override=#{nickname}, character_id_override=#{characterId}, profile_image_url_override=#{imageKey} WHERE group_id=#{groupId} AND user_id=#{userId}` (null 파라미터는 `jdbcType` 문제 없는지 확인, 필요 시 `#{x,jdbcType=VARCHAR}`/`BIGINT`) ④ `findMembersByGroupId` 를 상수 사용으로 교체 + `GROUP_CHARACTER_JOIN` + `(gm.nickname_override IS NOT NULL) AS group_profile` (기존 `u.nickname AS nickname, u.profile_image_url, c.svg_key AS character_key` 대체). 기존 `LEFT JOIN characters c ON c.id = u.character_id` 는 유지 |
| `group/dao/GroupMemberRow.java` | `boolean groupProfile` + `isGroupProfile/setGroupProfile` |
| `group/dao/GroupMapper.java` (`findMemberPreviewsByGroupIds`) | `GroupMemberMapper` 상수 사용 + `GROUP_CHARACTER_JOIN`. `GroupMemberPreviewRow` 는 변경 없음(별칭 동일) |
| `submission/dao/SubmissionMapper.java` (`FIELDS/JOINS`) | `FIELDS` 의 `u.nickname, u.profile_image_url, c.svg_key AS character_key` 를 표시 상수로 교체. `JOINS` 에 `LEFT JOIN group_members gm ON gm.group_id = m.group_id AND gm.user_id = s.user_id ` + `GROUP_CHARACTER_JOIN` 추가 (`LEFT JOIN characters c` 뒤). `SubmissionRow` 변경 없음 |
| `comment/dao/CommentMapper.java` (`FIELDS/JOINS`) | `FROM comments co JOIN submissions s ON s.id = co.submission_id JOIN missions m ON m.id = s.mission_id JOIN users u ON u.id = co.user_id LEFT JOIN characters c ON c.id = u.character_id LEFT JOIN group_members gm ON gm.group_id = m.group_id AND gm.user_id = co.user_id ` + `GROUP_CHARACTER_JOIN`; FIELDS 에 표시 상수. 작성자는 **댓글 작성자** `co.user_id` 기준(제출 작성자 아님). `CommentRow` 변경 없음 |
| `group/dto/GroupMemberResponse.java` | `boolean hasGroupProfile` 추가 |
| `group/service/GroupService.java` (`listMembers`) | `row.isGroupProfile()` 전달 |
| `group/dto/GroupProfileRequest.java` (신규) | `record GroupProfileRequest(@NotBlank @Size(min=2,max=10) String nickname, @NotNull Long characterId)` + compact ctor 로 nickname trim |
| `group/service/GroupProfileService.java` (신규) | Key Point 3. 의존: `GroupMapper`, `GroupMemberMapper`, `CharacterMapper`, `FileStorage`. `ProfileService` 의 파일 검증·롤백/커밋 후 삭제 로직을 **복사해 쓴다**(공통 유틸로 추출하는 리팩터링은 하지 않는다) |
| `group/controller/GroupProfileController.java` (신규) | `@RequestMapping("/api/groups/{groupId}/members/me")`: `@PutMapping("/profile")`(JSON, `@Valid @RequestBody GroupProfileRequest`), `@PutMapping(value="/profile-image", consumes=MULTIPART_FORM_DATA_VALUE)`(`file` 는 `ProfileController.upload` 와 같은 List 패턴, `nickname` 은 `@RequestParam(value="nickname", required=false)` 후 서비스에서 검증), `@DeleteMapping("/profile")` → 204. `@AuthenticationPrincipal Long userId`. 기존 `GroupController` 의 `DELETE /{groupId}/members/me`(나가기)와 경로가 다르므로(`…/me/profile`) 충돌 없음 |
| 테스트 | 아래 "Tests Required" |
| 문서 | `docs/BACKEND/API_DESIGN.md`(그룹 내 프로필 3개·`hasGroupProfile`·표시 프로필 규칙·나간 멤버 규칙), `BACKEND_DB.md`(group_members 컬럼), `CONTRACT_DECISIONS.md`(§1 목록·§2 헤더의 "그룹 내 프로필은 2차" 문구 정리, §2-2 상태 "반영됨", §5 헤더의 "그룹 내 프로필은 별도 단계" 정리), `CONTRACT_WORK_PLAN.md` 진행 표시, `docs/BACKEND/postman/HAWE_GROUP.postman_collection.json` 에 요청 3개 추가(JSON 유효성·한글 깨짐 확인) |

## Implementation Steps

1. `V1__initial_schema.sql` 수정(테이블 생성 순서 확인). 그 후 **로컬 DB 백업 → DROP/CREATE `hawe` → 테스트로 Flyway 재적용**: `mysqldump hawe > %TEMP%\hawe_backup_B1.sql` 후 DB 재생성. 백업 경로와 백업 시점 주요 테이블 행 수를 보고서에 적는다. `flyway clean/repair` 를 코드·설정에 넣지 않는다.
2. `GroupMember` 필드, `GroupMemberMapper`(상수·`setGroupProfile`·`findByGroupIdAndUserId`) 추가.
3. 4개 쿼리에 표시 규칙 적용(`GroupMemberMapper.findMembersByGroupId`, `GroupMapper.findMemberPreviewsByGroupIds`, `SubmissionMapper`, `CommentMapper`) — 이 시점에 기존 테스트가 전부 통과해야 한다(프로필이 없는 경우 결과 동일).
4. `GroupMemberRow.groupProfile` → `GroupMemberResponse.hasGroupProfile` → `GroupService.listMembers`.
5. `GroupProfileRequest`, `GroupProfileService`, `GroupProfileController`.
6. 테스트 작성 → `.\mvnw.cmd clean test -q`.
7. 문서·Postman 갱신.

## Constraints

- 백엔드+문서만. 앱·알림·`CharacterService`/기본 프로필 API 변경 금지.
- 새 마이그레이션(V3) 금지, V1 직접 수정.
- 표시 CASE 는 **한 곳**(`GroupMemberMapper` 상수)에만 정의. 복붙 금지. 닉네임/캐릭터/사진 3값은 항상 한 묶음.
- `GroupService.leaveGroup`/`deleteGroupCascade` 동작 변경 금지(고아 사진 파일은 B4).
- 대상 지정 미션의 `[대상]` 치환(`MissionService`, `CalendarMapper.target_nickname`)은 이번에 바꾸지 않는다.
- 공통 유틸 추출·기존 서비스 리팩터링 금지(범위 확장 금지). 패키지 규칙(5개 서브패키지: controller/service/dto/domain/dao) 준수.
- 커밋/푸시 금지, `CLAUDE.md`·`AGENTS.md` 수정 금지. 사용자 커밋 사이에 작업 트리가 바뀔 수 있으니 시작 전 `git status` 확인.

## Edge Cases

- 그룹 내 프로필 없음 → 기본 프로필(8단계 사진 포함). `nickname_override` 만 있고 캐릭터/사진이 둘 다 NULL 인 행은 만들지 않는다.
- 사진→캐릭터, 캐릭터→사진 전환 시 이전 사진 파일은 커밋 후 삭제, 트랜잭션 롤백 시 새로 저장한 파일 삭제.
- 같은 사진형→사진형 교체: 이전 key 삭제(새 key 와 다름 — `store` 가 매번 새 key 생성).
- 작성자가 그룹을 **나간 뒤** 피드/댓글: `gm` 행이 없어 기본 프로필로 보인다(그룹 내 프로필은 멤버십과 함께 사라진 것으로 본다). 재가입하면 새 행이라 프로필 없음. 문서에 명시.
- 같은 그룹 내 닉네임 중복 허용, 기본 닉네임과 같아도 허용.
- 닉네임 1자/11자/공백뿐/누락 → 400. 존재하지 않는 `characterId` → 404 `CHARACTER_NOT_FOUND`, `characterId` 누락 → 400. 비멤버 403, 그룹 없음 404, 토큰 없음 401, 비이미지 415, 10MB 초과 413, 파일 없음/0바이트/둘 이상 → 400, JSON 으로 `profile-image` PUT → 415.
- 검증 실패(닉네임·파일) 시 DB 갱신과 파일 저장이 일어나지 않아야 한다(닉네임을 파일 저장 **전에** 검증).
- 사용자가 여러 그룹에서 각각 다른 그룹 프로필 사용 — 그룹 A 설정이 그룹 B 응답에 새지 않는다(조인이 `m.group_id`/`gm.group_id` 로 한정됨).
- `GET /api/invites/{code}` 미리보기도 멤버들의 그룹 내 프로필로 표시(비멤버가 보지만 앱 기대).
- 피드 정렬·페이징·`COUNT`: `LEFT JOIN group_members` 는 `UNIQUE(group_id,user_id)` 라 행 중복 없음 — 테스트로 확인.

## Regression Risks

- 4개 쿼리 SELECT/JOIN 변경 → 기존 `GroupMapperTests`, `GroupContractMapperTests`, `GroupContractServiceTests`, `GroupServiceTests`, `SubmissionServiceTests`/`SubmissionIntegrationTests`/`SubmissionControllerTests`, `CommentMapperTests`/`CommentControllerTests`/`CommentServiceTests`, `ProfileControllerTests` 가 그대로 통과해야 한다.
- 컬럼 별칭 충돌: `CommentMapper` 는 새로 `s`, `m` 별칭을 추가하므로 FIELDS 안의 기존 별칭(`co`, `u`, `c`)과 겹치지 않는지 확인. `SubmissionMapper` 의 `s.is_late AS late`, `m.template` 등 기존 컬럼 유지.
- `GroupMemberResponse` 레코드 시그니처 변경 → 컴파일 오류 위치 전부 수정.
- V1 수정 → 로컬 Flyway checksum 불일치(로컬 DB 재생성 필수; 공유 DB 는 별도 — 팀에 알릴 것).
- `null` 파라미터 MyBatis 바인딩(`setGroupProfile`) — MariaDB 에서 `jdbcType` 오류 가능성, 테스트(DELETE 경로)로 확인.

## Tests Required

- `GroupMemberMapperTests`(실 MariaDB; 기존 `GroupMapperTests` 패턴): `setGroupProfile` 후 `findMembersByGroupId`/`findMemberPreviewsByGroupIds`/`findByGroupIdAndUserId` — ① 프로필 없음=기본값, `group_profile=false` ② 캐릭터형: 그룹 닉네임+그룹 캐릭터 key, 이미지 null ③ 사진형: 그룹 닉네임+이미지 key, characterKey null, 기본 사진이 있어도 그룹 사진이 우선 ④ 해제(모두 NULL) 후 기본값 복귀 ⑤ 다른 그룹 영향 없음.
- `GroupProfileServiceTests`(Mockito): 그룹 없음/비멤버/캐릭터 없음/닉네임 검증/파일 검증 실패 시 저장·갱신 없음, 사진→캐릭터 전환 시 이전 key 삭제 예약, 캐릭터→사진 전환, DELETE 멱등(프로필 없을 때도 예외 없음), 응답 매핑(characterKey/publicUrl).
- `GroupProfileControllerTests`(`@SpringBootTest @AutoConfigureMockMvc`, 임시 업로드 디렉터리 — `ProfileControllerTests` 패턴: `hawe.storage.local-dir` 를 `DynamicPropertySource` 로 지정, JdbcTemplate 로 데이터 직접 INSERT, 정리는 finally): 401/403/404/400/415/413 각각, PUT 후 `GET /groups/{id}/members`(+`hasGroupProfile=true`), `GET /groups`, `GET /invites/{code}`, 피드 `GET /missions/{id}/submissions`, `GET /submissions/{id}`, 댓글 목록에서 표시 프로필 확인, DELETE 후 기본 프로필 복귀·사진 파일 삭제 확인, **다른 그룹에는 새지 않음**, `PATCH /me/profile` 이 그룹 프로필 있는 그룹에 반영되지 않음, 작성자가 그룹을 나간 뒤 피드는 기본 프로필.
- 기존 위 회귀 테스트 전체 통과.

## Validation Steps

1. `.\mvnw.cmd clean test -q` 통과 — 총 테스트 수·실패 0 을 보고서에 기록.
2. `rg "nickname_override|DISPLAY_"` 로 CASE 가 `GroupMemberMapper` 한 곳에 정의되고 나머지 3곳은 참조만 하는지 확인.
3. Postman JSON 파싱(예: `python -c "import json;json.load(open(...,encoding='utf-8'))"`) + 한글 확인.
4. `git diff --check`.
5. 가능하면 서버 기동 스모크(그룹 생성 → 프로필 PUT → 피드/멤버 확인). 못 하면 보고서에 "미검증" 명시.
6. 보고서(`.agent/report.md`)에 DB 백업 경로·행 수, 나간 멤버 표시 규칙, 범위 밖(B4 파일 정리, 앱 A3), 미검증 항목 기재.

## Potential Merge Conflicts

- `GroupMapper`/`GroupMemberMapper`/`GroupService`: 우준 B7(그룹 목록 `todayMissions`, 멤버 지표)·B2·B4 와 겹침 → 순차 진행, 고치는 쪽은 시작 직전 pull·직후 push.
- `SubmissionMapper`/`SubmissionRow`/`CommentMapper`: 우준 B5·B10 과 겹침 → **B5 시작 전에 B1 커밋**.
- `V1__initial_schema.sql`: 우준 B9(`missions` 컬럼), B3, B10 연동, B11 도 수정 → V1 을 고칠 때마다 팀에 알리고 모두 로컬 DB 재생성.
- `docs/BACKEND/*`, `HAWE_GROUP.postman_collection.json`.
- 앱 `GroupMember.groupProfile`(Android `GroupProfileScreen`)은 A3(경환)에서 연결 — 이번 범위 밖.
