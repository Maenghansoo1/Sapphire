# B1. 그룹 내 프로필 (백엔드)

> 범위: 백엔드(`src/`, `docs/BACKEND/`)만. 앱은 A3 에서. **전제: 7·8단계가 커밋된 뒤 시작**(8단계가 `users.profile_image_url` 과 프로필 사진 표시 4곳을 이미 추가했다 — 이 계획은 그 4곳의 쿼리를 "그룹 내 프로필 우선" 으로 확장한다). 결정 근거: `docs/BACKEND/CONTRACT_DECISIONS.md` §2-1 "그룹 내 프로필", §2-2 "그룹 내 프로필 (16-1)". 알림과 무관.

## Goal

그룹마다 **닉네임 + (캐릭터 또는 사진)** 한 벌의 "그룹 내 프로필"을 설정·교체·해제할 수 있다. 설정돼 있으면 그 그룹의 **모든 사람 표시 응답**(그룹 멤버 목록·그룹 목록의 멤버 미리보기·초대 미리보기·피드·상세·댓글)이 그 프로필로 보이고, 없으면 기본 프로필로 보인다.

```text
PUT    /api/groups/{groupId}/members/me/profile         JSON { "nickname": "과대표 하늘", "characterId": 3 }  → 200 프로필 객체
PUT    /api/groups/{groupId}/members/me/profile-image   multipart: file(이미지) + nickname(text)              → 200 프로필 객체
DELETE /api/groups/{groupId}/members/me/profile         → 204 (기본 프로필로 복귀, 사진 파일 삭제)
// 프로필 객체
{ "userId": 3, "nickname": "과대표 하늘", "characterKey": "gomdol", "profileImageUrl": null }
```
`GET /api/groups/{groupId}/members` 항목에 `hasGroupProfile`(boolean) 추가.

## Findings

- `group_members(id, group_id, user_id, role, joined_at)` — 그룹 내 프로필 컬럼 없음. `UNIQUE(group_id, user_id)`.
- 사람을 보여 주는 쿼리(8단계 이후 모두 `u.profile_image_url` 포함 상태여야 함 — 아니면 8단계 결과 확인):
  1. `GroupMapper.findMemberPreviewsByGroupIds`(그룹 목록·그룹 상세·초대 미리보기의 멤버 프로필; Row `GroupMemberPreviewRow`) — 이미 `group_members gm JOIN users u LEFT JOIN characters c`.
  2. `GroupMemberMapper.findMembersByGroupId`(멤버 목록; Row `GroupMemberRow`; DTO `GroupMemberResponse(userId, nickname, characterKey, profileImageUrl, role, submissionCountThisMonth, stampedToday)`) — 이미 `gm` 사용.
  3. `SubmissionMapper.FIELDS/JOINS`(`s`→`m`→`u`→`c`; `gm` 없음) → `SubmissionRow` → `SubmissionService.response` 의 `MemberProfileResponse`.
  4. `CommentMapper.FIELDS`(댓글→`u`→`c`) → `CommentRow` → `CommentService`.
  - 3·4 는 **작성자가 이미 그룹을 나갔을 수 있다** → `LEFT JOIN group_members gm ON gm.group_id = m.group_id AND gm.user_id = <작성자>` 로 붙이고, `gm` 이 없거나 `nickname_override IS NULL` 이면 기본 프로필.
- 캐릭터: `characters(id, name, svg_key)`, `CharacterMapper.findById`. 기본 프로필 변경 `PATCH /me/character`(`CharacterErrorCode.CHARACTER_NOT_FOUND` 404).
- 파일: `FileStorage.store(dir,bytes,ext)`/`delete`/`publicUrl`, `FileTypeDetector.detect`(IMAGE만 허용), 커밋 후 삭제·롤백 시 정리 패턴은 7단계 `CommentService`·8단계 `ProfileService` 에 있음 — **같은 패턴**.
- 닉네임 규칙: trim 후 2~10자(`RegisterRequest`, 8단계 `ProfileUpdateRequest`).
- `GroupService.leaveGroup`/`deleteGroupCascade`: 멤버십 행 삭제 시 그룹 내 프로필 컬럼도 함께 사라진다(별도 처리 불필요). 단 **그룹 내 프로필 사진 파일은 고아가 된다** → B4(파일 정리)에서 처리. 이 계획에서는 `DELETE .../profile` 과 교체 시의 파일 삭제만 한다.
- 대상 지정 미션의 `[대상]` 치환은 `params` 의 닉네임 스냅샷/`users.nickname` 을 쓴다(`MissionService`, `CalendarMapper`) — **이번 범위에서 바꾸지 않는다**(그룹 내 프로필 닉네임으로 치환하는 개선은 별도).

## Root Cause / Key Implementation Points

1. **스키마(V1 직접 수정)**: `group_members` 에 추가
   - `nickname_override varchar(50) DEFAULT NULL`
   - `character_id_override bigint(20) DEFAULT NULL` + FK `fk_group_members_character` → `characters(id)` (+ KEY)
   - `profile_image_url_override varchar(500) DEFAULT NULL` (파일 key)
   - 불변식(서비스가 보장, DB CHECK 는 넣지 않음): `nickname_override` 가 NULL 이면 나머지 둘도 NULL(= 그룹 내 프로필 없음). NULL 이 아니면 `character_id_override` 와 `profile_image_url_override` 중 **정확히 하나**만 NOT NULL.
2. **표시 프로필 규칙(모든 쿼리 공통)**: `gm.nickname_override IS NOT NULL` 이면 `(nickname_override, 그 캐릭터의 svg_key, profile_image_url_override)`, 아니면 `(u.nickname, 기본 캐릭터 svg_key, u.profile_image_url)`. 즉 **세 값을 한 묶음으로** 고른다(닉네임만 그룹 값이고 사진은 기본 값이 되는 혼합 금지). SQL 은 `CASE WHEN gm.nickname_override IS NOT NULL THEN … ELSE … END` 를 세 컬럼에 반복하되, 반복을 줄이려면 `LEFT JOIN characters gc ON gc.id = gm.character_id_override` 를 추가해 `CASE` 안에서 `gc.svg_key` 를 쓴다. 이 CASE 조각은 **한 곳(상수 문자열, 예: `GroupMemberMapper` 안의 `DISPLAY_PROFILE_COLUMNS`)** 에 두고 4개 쿼리가 재사용한다(문자열 상수가 매퍼 인터페이스를 넘나들어도 되면 `global` 이 아니라 `group/dao` 의 한 인터페이스 상수로 두고 다른 매퍼가 참조).
3. **API 서비스**: `group/service/GroupProfileService`(신규) + `group/controller/GroupProfileController`(신규) — `GroupController` 를 더 키우지 않는다. 공통 검사: 그룹 없음 404 `GROUP_NOT_FOUND` → 멤버 아님 403 `NOT_GROUP_MEMBER`. 모든 변경은 `@Transactional`, 기존 사진 파일 삭제는 커밋 후.
   - `PUT …/profile`: `characterId` 존재 확인(404 `CHARACTER_NOT_FOUND`), 닉네임 검증 → `nickname_override`, `character_id_override` 설정, `profile_image_url_override = NULL`. 이전 사진 key 가 있었으면 커밋 후 삭제.
   - `PUT …/profile-image`: 파일 검증(IMAGE만, ≤10MB; 415 `UNSUPPORTED_FILE_TYPE`, 413 `FILE_TOO_LARGE`, 파일 없음/0바이트 400), 닉네임 검증 → 새 파일 `storage.store("group-profiles", …)`, `nickname_override`, `profile_image_url_override` 설정, `character_id_override = NULL`. 롤백 시 새 파일 삭제, 커밋 후 이전 파일 삭제.
   - `DELETE …/profile`: 세 컬럼 NULL, 이전 사진 파일 커밋 후 삭제, 멱등 204.
   - 응답은 위 "프로필 객체"(그룹 내 프로필 적용 후 값). 새 DTO 를 만들지 말고 `group/dto/MemberProfileResponse` 재사용.
4. **`hasGroupProfile`**: `GroupMemberRow` 에 `boolean groupProfile`(= `gm.nickname_override IS NOT NULL`), `GroupMemberResponse` 에 `hasGroupProfile` 추가(레코드 컴포넌트 추가 → 생성 호출 위치 수정).
5. **본인 외 접근 금지**: 모든 엔드포인트는 `members/me` 로 본인 프로필만 다룬다.
6. 기본 프로필이 바뀌어도(`PATCH /me/profile`, `PATCH /me/character`, 사진) 그룹 내 프로필이 있는 그룹에는 반영되지 않는다 — 쿼리 규칙(2)이 그대로 보장하므로 추가 코드 없음.

## Files / Functions to Change

| 파일 | 변경 |
|---|---|
| `V1__initial_schema.sql` | `group_members` 컬럼 3개 + FK/KEY (위 1). `V2` 불변. |
| `group/domain/GroupMember.java` | 필드 3개 + getter/setter(`nicknameOverride`, `characterIdOverride`, `profileImageUrlOverride`). `findByGroupIdAndUserId` SELECT 에도 포함. |
| `group/dao/GroupMemberMapper.java` | `@Update setGroupProfile(groupId, userId, nickname, characterId, imageKey)`(세 값을 한 번에 갱신 — 해제는 모두 NULL), `findMembersByGroupId` 에 표시 프로필 CASE + `has_group_profile`. |
| `group/dao/GroupMemberRow.java`, `GroupMemberPreviewRow.java` | `groupProfile`(Row), 표시 값 매핑 필드는 기존 그대로(쿼리가 CASE 결과를 같은 별칭 `nickname/character_key/profile_image_url` 로 반환). |
| `group/dao/GroupMapper.java` (`findMemberPreviewsByGroupIds`) | CASE 적용. |
| `submission/dao/SubmissionMapper.java` (`FIELDS/JOINS`) | `LEFT JOIN group_members gm ON gm.group_id = m.group_id AND gm.user_id = s.user_id` + `LEFT JOIN characters gc` 추가, 표시 CASE 적용. |
| `comment/dao/CommentMapper.java` | 댓글→제출→미션 경로로 `m.group_id` 를 얻어 같은 `LEFT JOIN gm` 적용(현재 FIELDS 에 미션 조인이 없으면 `JOIN submissions s ON s.id = co.submission_id JOIN missions m ON m.id = s.mission_id` 추가). |
| `group/dto/GroupMemberResponse.java`, `group/service/GroupService.java` | `hasGroupProfile` 추가·전달. |
| `group/controller/GroupProfileController.java`, `group/service/GroupProfileService.java`, `group/dto/GroupProfileRequest.java` (신규) | Key Point 3. `GroupProfileRequest(@NotBlank @Size(2..10) String nickname, @NotNull Long characterId)` + trim 하는 compact ctor. 이미지 PUT 은 `@RequestParam("file") MultipartFile`, `@RequestParam("nickname") String`(서비스에서 trim·2~10 검증 → 400). |
| 테스트 | 아래 |
| 문서 | `API_DESIGN.md` §2(그룹 내 프로필 3개·`hasGroupProfile`·표시 프로필 규칙), `BACKEND_DB.md`(group_members 컬럼), `CONTRACT_DECISIONS.md` §2 "그룹 내 프로필" 상태 반영·§1 목록의 "그룹 내 프로필은 2차" 문구 정리, `CONTRACT_WORK_PLAN.md`, Postman `HAWE_GROUP` 에 3개 요청 추가. |

## Implementation Steps

1. V1 수정 후 **로컬 DB 백업(`mysqldump` → `%TEMP%`) → DROP/CREATE `hawe` → 테스트로 Flyway 재적용**. 백업 경로·행 수를 보고서에. `flyway clean/repair` 코드 금지.
2. 도메인/매퍼(컬럼, `setGroupProfile`, CASE 상수) → 4개 쿼리에 적용.
3. `GroupMemberResponse.hasGroupProfile`.
4. `GroupProfileService/Controller` 3개 API.
5. 테스트 → `.\mvnw.cmd clean test -q`. 6. 문서·Postman.

## Constraints

- 백엔드+문서만. 앱 미수정. 알림 없음. `GroupService.leaveGroup`/`deleteGroupCascade` 동작 변경 금지(고아 파일은 B4).
- 새 마이그레이션(V3) 금지, V1 직접 수정. `CharacterService`/기본 프로필 API 변경 금지.
- 표시 CASE 는 한 곳에 정의(복붙 금지). 닉네임/캐릭터/사진 3값은 항상 한 묶음.
- 커밋/푸시·`CLAUDE.md`·`AGENTS.md` 수정 금지. 패키지 규칙(5개 서브패키지).

## Edge Cases

- 그룹 내 프로필 없음 → 기본 프로필(8단계 사진 포함). 닉네임 override 만 있고 캐릭터/사진이 둘 다 NULL 인 행은 만들지 않는다(불변식).
- 사진→캐릭터로, 캐릭터→사진으로 전환 시 이전 사진 파일 삭제(커밋 후), 롤백 시 새 파일 삭제.
- 작성자가 그룹을 **나간 뒤** 피드/댓글: `gm` 행이 없으므로 기본 프로필로 보임(그룹 내 프로필은 멤버십과 함께 사라진 것으로 본다 — 문서화).
- 같은 그룹 두 멤버가 같은 그룹 닉네임을 써도 허용(유니크 아님). 기본 닉네임과 같아도 허용.
- 닉네임 1자/11자/공백뿐 → 400. 존재하지 않는 `characterId` → 404. 비멤버 403, 그룹 없음 404, 토큰 없음 401, 비이미지 415, 10MB 초과 413, multipart 아님(JSON 으로 PUT image) 415.
- 한 사용자가 여러 그룹에서 각각 다른 그룹 프로필을 쓸 수 있다 — 그룹 A 의 설정이 그룹 B 응답에 새지 않는지 확인.
- `GET /api/groups` 의 `members` 미리보기와 `GET /api/invites/{code}` 미리보기도 그룹 내 프로필로 보인다(초대 미리보기는 아직 멤버가 아닌 사람이 보지만 멤버들의 표시 프로필을 보여주는 것이 앱 기대).

## Regression Risks

- 4개 쿼리의 SELECT/JOIN 변경 → 기존 `GroupMapperTests`, `GroupContract*`, `SubmissionServiceTests`/`SubmissionIntegrationTests`/`SubmissionControllerTests`, `CommentMapperTests`/`CommentControllerTests` 가 그대로 통과해야 한다(그룹 프로필이 없는 경우 결과 동일).
- 제출/댓글 쿼리에 `LEFT JOIN group_members` 추가로 행이 **중복되지 않는지**(`UNIQUE(group_id,user_id)` 라 안전하지만 테스트로 확인), 정렬·페이징·`COUNT` 가 변하지 않는지.
- `GroupMemberResponse` 레코드 시그니처 변경 → 직접 생성하는 테스트 수정.
- V1 수정 → Flyway checksum 불일치(로컬 DB 재생성 필수, 공유 DB 는 별도).

## Tests Required

- `GroupMemberMapperTests`(실 MariaDB): 그룹 프로필 있음/없음 표시 값(세 값 묶음), 사진형/캐릭터형, `has_group_profile`.
- `GroupProfileServiceTests`(Mockito): 검증(닉네임·캐릭터 없음·비멤버·그룹 없음), 사진↔캐릭터 전환 시 이전 key 삭제 예약, 파일 검증 실패 시 저장·갱신 없음, DELETE 멱등.
- `GroupProfileControllerTests`(MockMvc 통합, 임시 업로드 디렉터리): 401/403/404/400/415/413 각각, PUT 후 `GET /groups/{id}/members`·`GET /groups`·`GET /invites/{code}`·피드 `GET /missions/{id}/submissions`·`GET /submissions/{id}`·댓글 목록에서 표시 프로필 확인, DELETE 후 기본 프로필 복귀, **다른 그룹에는 새지 않음**, 기본 프로필 변경(`PATCH /me/profile`)이 그룹 프로필 있는 그룹에 반영되지 않음.
- 기존 위 회귀 테스트 전체 통과.

## Validation Steps

`.\mvnw.cmd clean test -q` 통과(테스트 수·실패 0 기록) → `rg "nickname_override|DISPLAY_PROFILE"` 로 CASE 상수 한 곳 정의·4곳 사용 확인 → Postman JSON 파싱·한글 확인 → `git diff --check`. 가능하면 서버 기동 스모크(그룹 생성→프로필 PUT→피드/멤버 확인), 못 했으면 "미검증" 명시. 보고서에 DB 재생성·백업, 나간 멤버 표시 규칙, 범위 밖(B4 파일 정리, 앱 A3) 기재.

## Potential Merge Conflicts

`GroupMapper`/`GroupMemberMapper`/`GroupService`(B2·B4 와 겹침 — 순차), `SubmissionMapper`/`SubmissionRow`/`CommentMapper`(B5·B6 과 겹침 — B5 시작 전 B1 커밋), `V1__initial_schema.sql`, `docs/BACKEND/*`. 앱 `GroupMember.groupProfile`(Android `GroupProfileScreen`)은 A3 에서 연결.
