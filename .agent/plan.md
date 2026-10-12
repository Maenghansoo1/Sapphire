# 계약 반영 2차 / 8단계(백엔드): Me 나머지 — 닉네임 변경 · 프로필 사진 · 회원 탈퇴

> 범위: **백엔드만** (`src/`, `docs/BACKEND/`). Android/iOS 는 건드리지 않는다(17-1 프로필 편집·17-5 탈퇴 화면 연결은 8A 로 별도). 결정 근거: `docs/BACKEND/CONTRACT_DECISIONS.md` §5 (사용자 결정 완료) + 이번 계획에서 §5-4 미결 사항을 아래 "Key Points"대로 확정.
>
> **범위에서 제외(사용자 결정, 이번 질의)**: **그룹 내 프로필**(`group_members` 의 `*_override` 컬럼, `PUT/DELETE /groups/{id}/members/me/profile*`, 모든 응답에 그룹 내 프로필 우선 적용)은 8단계에서 **뺀다**. 별도 단계로 분리한다. 기본 프로필 사진·닉네임 변경이 그룹 내 프로필이 있는 그룹에 어떻게 보일지는 그 단계에서 다룬다(지금은 그룹 내 프로필이 없으므로 항상 기본 프로필).
>
> **전제**: 작업 트리에 7단계(반응·댓글, `comment/`·`reaction/` 패키지와 `SubmissionService` 변경)가 **커밋 전 상태로 남아 있다**. 8단계는 그 위에서 진행한다 — 7단계 파일(`CommentMapper`/`CommentService`/`SubmissionService`/`SubmissionMapper`)을 수정하는 부분은 현재 작업 트리 내용을 기준으로 할 것(되돌리거나 다시 쓰지 말 것). Android 쪽 변경(`android/`)도 uncommitted 로 있으나 이번 작업과 무관하니 건드리지 않는다.

## Goal

1. `PATCH /api/me/profile` — 기본 프로필 닉네임 변경(trim 후 2~10자). 응답은 `GET /api/me` 와 같은 모양.
2. `PUT /api/me/profile-image`(multipart `file`, 이미지 ≤10MB jpg/png/webp), `DELETE /api/me/profile-image` — 기본 프로필 사진 등록·교체·삭제. 사진이 있으면 모든 사람 표시 응답의 `profileImageUrl` 에 URL 이 내려간다.
3. `DELETE /api/me` — 회원 탈퇴(204). 내 제출물(+파일)·댓글을 삭제하고, 그룹에서 나가고, 계정을 지운다. 다른 멤버가 있는 그룹의 그룹장이면 409 `OWNER_MUST_TRANSFER`.
4. 탈퇴한 사용자의 **남은 액세스/리프레시 토큰이 더 이상 통하지 않게** 한다(§5-4 미결 해결).

## Findings

### 이미 있는 것
- `auth/controller/MeController` — `GET /api/me` → `AuthService.getMe(userId)`(`MeResponse(id, email, nickname, characterKey, profileImageUrl, createdAt)`; 지금 `profileImageUrl` 은 항상 `null`). 사용자 없으면 `TokenErrorCode.INVALID_TOKEN`(401).
- `auth/dao/UserMapper`: `findByEmail`, `findById`, `existsByEmail`, `insert` (SELECT 컬럼 목록이 `id, email, password_hash, nickname, character_id, terms_agreed_at, privacy_agreed_at, created_at` 로 **두 곳에 반복**). `auth/domain/User`(일반 getter/setter). `AuthService` 생성자: `(UserMapper, PasswordEncoder, JwtTokenProvider, CharacterMapper)`. `AuthService.refresh` 는 이미 `findById == null` 이면 `INVALID_TOKEN`.
- `auth/dto/RegisterRequest`: `@NotBlank @Size(min = 2, max = 10) String nickname`, compact constructor 에서 `trim()`. **닉네임 규칙은 이것과 같게** 쓴다(앞뒤 공백 제거 후 2~10자).
- `character/`: `PATCH /api/me/character`(`CharacterService.changeMyCharacter`, `CharacterMapper.updateUserCharacterId`) — 이미 있음, 변경 없음. **계약 규칙: 캐릭터를 바꿔도 사진은 지워지지 않는다**(앱이 `DELETE /me/profile-image` 를 따로 호출) → `CharacterService` 를 건드리지 말 것.
- `global/security/JwtAuthenticationFilter`: `@Component` 가 **아니며** `SecurityConfig.securityFilterChain` 안에서 `new JwtAuthenticationFilter(jwtTokenProvider, objectMapper)` 로 생성한다. 토큰이 유효하면 사용자 존재 확인 없이 인증을 세운다. `shouldNotFilter`: `/api/auth/**`, `GET /api/characters`. 테스트 `global/security/JwtAuthenticationFilterTests` 가 생성자를 직접 호출(23·39행).
- `JwtTokenProvider.createAccessToken(userId, nickname)` — 닉네임이 **클레임으로만** 들어가고 서버 코드에서 읽는 곳이 없다 → 닉네임을 바꿔도 기존 토큰의 클레임이 낡을 뿐 문제 없음(앱이 `GET /me` 로 읽음).
- `global/storage`: `FileStorage{store(dir, bytes, ext), delete(key), publicUrl(key)}`, `FileTypeDetector.detect(bytes)`(`Detected(mediaType "IMAGE"|"AUDIO", extension)`), `LocalFileStorage`(디렉터리명 `[a-z0-9_-]+`). `/files/**` GET 공개. multipart 한도 `max-file-size=10MB`, `max-request-size=12MB`. 7단계 `CommentService` 가 이미지 저장·롤백 시 삭제(`TransactionSynchronization.afterCompletion`)·삭제 시 커밋 후 파일 삭제(`afterCommit`+try/catch+log)를 구현했다 — **같은 패턴**을 따른다(현재 작업 트리의 `comment/service/CommentService.java` 참고).
- 사람을 보여 주는 응답에서 `profileImageUrl` 이 하드코딩 `null` 인 곳(4곳, 모두 `u.nickname` + `characters.svg_key` 조인 쿼리를 쓴다):
  1. `group/service/GroupService.memberProfiles → toMemberProfile`(약 276~281행; 데이터는 `GroupMapper.findMemberPreviewsByGroupIds` → `GroupMemberPreviewRow`) — 그룹 상세/미리보기/목록의 멤버 프로필.
  2. `group/service/GroupService` 멤버 목록(약 196행 `new GroupMemberResponse(..., null, ...)`; 데이터는 `GroupMemberMapper.findMembersByGroupId` → `GroupMemberRow`).
  3. `submission/service/SubmissionService.response`(`new MemberProfileResponse(row.getUserId(), row.getNickname(), row.getCharacterKey(), null)`; 데이터는 `SubmissionMapper.FIELDS/JOINS` → `SubmissionRow`).
  4. `comment/service/CommentService` 의 `CommentResponse` 조립(약 125행; 데이터는 `CommentMapper.FIELDS` → `CommentRow`). (7단계 신규 파일)
- DB `users`: `id, email, password_hash, nickname, character_id, terms_agreed_at, privacy_agreed_at, created_at` — **`profile_image_url` 컬럼이 없다.** 프로젝트 결정: **V1 을 직접 수정하고 로컬 DB 를 재생성**한다(V1 은 "최종 스키마"; 5·7단계에서 같은 방식 사용, `V2__seed_characters.sql` 은 그대로).
- 사용자를 참조하는 FK 전부(`V1__initial_schema.sql`): `groups.owner_id`, `group_members.user_id`, `submissions.user_id`, `submission_guesses.user_id`(+`submission_id`→submissions), `comments.user_id`(+`submission_id`), `comment_mentions.mentioned_user_id`(+`comment_id`), `reactions.user_id`(+`submission_id`), `pokes.from_user_id/to_user_id`(+`mission_id`,`group_id` FK), `notifications.from_user_id(NULL 허용)/to_user_id`. `missions` 테이블은 사용자 FK 가 없다(대상 지정 미션은 `params` JSON 에 `targetUserId`·닉네임 스냅샷만 있고 FK 없음 → 탈퇴 시 그대로 둔다; `CalendarMapper` 는 서브쿼리가 null 이면 "친구"로 대체, `MissionService` 는 스냅샷 닉네임 사용 — 변경 불필요).
- 탈퇴에 쓸 수 있는 기존 로직: `GroupService.leaveGroup(userId, groupId, successorUserId)` — 일반 멤버는 멤버십 삭제, **그룹장이면서 혼자인 그룹은 `deleteGroupCascade`(댓글·멘션·반응·추측·제출·콕·미션·슬롯·멤버·그룹 삭제)**, 그룹장이면서 다른 멤버가 있으면 `successorUserId == null` 일 때 409 `SUCCESSOR_REQUIRED`. 그룹을 `findByIdForUpdate` 로 잠근다. (`GroupMapper` 에 "사용자가 속한 group id 목록" 쿼리는 없다 — `findSummariesByUserId` 는 페이지용 요약.)
- 7단계에서 만든 `GroupMapper.deleteCommentMentionsByGroupId` 등 그룹 단위 삭제 쿼리는 그대로 재사용 가능.
- 앱 쪽 참고: Android `ApiClient` 는 401 `INVALID_TOKEN` 이면 세션 만료로 처리(로그아웃) → 탈퇴 후 토큰이 `INVALID_TOKEN` 으로 거절되는 것이 앱 기대 동작과 맞다. `ignoreUnknownKeys=true`.

## Root Cause / Key Implementation Points

1. **프로필 사진 저장 방식**: `users.profile_image_url varchar(500) NULL` 에 **파일 key**(예: `profiles/uuid.jpg`)를 저장하고, 응답에서 `storage.publicUrl(key)` 로 URL 을 만든다(`submissions.media_url`, `comments.image_url` 과 같은 방식). 새 마이그레이션이 아니라 V1 `users` 정의에 컬럼 추가.
2. **새 기능 패키지 2개**(`CONTRACT_DECISIONS` §5-3: "새 기능 패키지에서"; 기존 `auth.MeController` 는 `GET /me` 전용으로 그대로 둔다 — 같은 클래스명을 쓰지 말 것):
   - `com.sapphire.hawe.profile` — `ProfileController`(`PATCH /api/me/profile`, `PUT/DELETE /api/me/profile-image`), `ProfileService`, DTO.
   - `com.sapphire.hawe.withdraw` — `WithdrawController`(`DELETE /api/me`), `WithdrawService`, `WithdrawMapper`(삭제용 SQL 전부), `WithdrawErrorCode`.
   각 패키지는 `controller/service/dto/domain/dao` 5개 서브패키지 규칙을 따른다(필요한 것만 만든다).
3. **`GET /me` 가 사진 URL 을 내려준다**: `AuthService.getMe` 가 `user.getProfileImageUrl()` 이 있으면 `storage.publicUrl(...)`, 없으면 `null`. → `AuthService` 생성자에 `FileStorage` 추가(테스트 `AuthServiceTests` 생성자 호출 수정). `PATCH /me/profile` 은 닉네임을 바꾼 뒤 **`authService.getMe(userId)` 를 재사용**해 응답을 만든다(중복 조립 금지).
4. **사진 변경 트랜잭션**: `PUT`: 검증(이미지 매직바이트·크기) 통과 → `storage.store("profiles", bytes, ext)` → (롤백 시 새 파일 삭제 등록) → `UPDATE users SET profile_image_url=?` → 이전 key 가 있으면 **커밋 후** 삭제. `DELETE`: 컬럼을 NULL 로 → 이전 key 가 있으면 커밋 후 삭제. 사진이 없을 때 `DELETE` 도 멱등(204). 사용자 행이 없으면(영향 행 0 + `findById==null`) `INVALID_TOKEN`.
5. **표시 응답 4곳에 `profileImageUrl` 반영**: 위 Findings 의 4개 쿼리에 `u.profile_image_url AS profile_image_url`(별칭은 camelCase 매핑 규칙대로 `profile_image_url`)을 추가하고 Row 클래스에 필드(`profileImageUrl`, key)를 더한 뒤, 서비스에서 `key == null ? null : storage.publicUrl(key)` 로 변환한다. `GroupService`, `CommentService` 는 `FileStorage` 주입이 필요할 수 있다(`SubmissionService` 는 이미 가짐; `CommentService` 도 이미 가짐). `FileStorage` 인터페이스에 default 메서드를 추가하지 말 것(Mockito 목에서 null 이 되어 테스트를 가림) — 서비스에서 인라인 null 체크.
6. **탈퇴 처리 순서(한 트랜잭션, `WithdrawService.withdraw(userId)`)**:
   1. 사용자 존재 확인(없으면 `INVALID_TOKEN`).
   2. **그룹장 검사**: `groups.owner_id = userId` 이면서 멤버가 2명 이상인 그룹이 하나라도 있으면 → 409 `OWNER_MUST_TRANSFER`("그룹장인 그룹에서 먼저 나가 주세요"). 어떤 데이터도 지우기 전에 검사한다.
   3. **삭제할 파일 key 수집**(DB 삭제 전): ① 내 제출물의 `media_url`(NULL 제외) ② **내 제출물에 달린 모든 댓글**의 `image_url` ③ 내가 쓴 댓글의 `image_url` ④ 내 `users.profile_image_url`. 중복 제거.
   4. **그룹에서 나가기**: 내가 속한 모든 group id 를 읽어(예: `WithdrawMapper.findGroupIdsByUserId`) 각각 `groupService.leaveGroup(userId, groupId, null)`. (혼자인 그룹장 그룹은 이 호출이 그룹 전체를 cascade 삭제한다 — 그 안의 내 제출물·댓글 행도 함께 지워진다. 일반 멤버는 멤버십만 지운다.) 동시성 틈으로 `leaveGroup` 이 `SUCCESSOR_REQUIRED` 를 던지면(검사 후 다른 사람이 가입한 경우) **`OWNER_MUST_TRANSFER` 로 바꿔서 던진다**(트랜잭션은 롤백됨).
   5. **남은 내 데이터 삭제**(순서 중요, FK 때문): 
      - 내 제출물에 달린 **다른 사람 댓글의 멘션 행** → 그 댓글들 → 내 제출물에 달린 **반응** → 내 제출물의 **추측(`submission_guesses`)**
      - 내가 쓴 댓글의 멘션 행, **나를 멘션한 다른 사람 댓글의 멘션 행**(`mentioned_user_id = 나`; 댓글은 그대로 두고 멘션 행만 삭제), 내가 쓴 댓글
      - 내가 누른 반응, 내가 한 추측
      - 내 제출물
      - 내가 보내거나 받은 콕(`pokes.from_user_id = 나 OR to_user_id = 나`)
      - 내가 보낸/받은 알림(`notifications.from_user_id = 나 OR to_user_id = 나`)
   6. 마지막에 `users` 행 삭제.
   7. **커밋 후 수집한 파일 삭제**(각 key `storage.delete`, 실패는 `log.warn` 만 — 응답은 204 유지; 한 파일 실패가 나머지 삭제를 막지 않게 key 별 try/catch).
   - 같은 이메일로 다시 가입 가능(행이 지워지므로 자동).
   - "기기 토큰·알림 설정" 은 9단계 테이블이 아직 없으므로 **이번에 다루지 않는다**(9단계에서 탈퇴 삭제 목록에 추가해야 함을 문서에 한 줄 기록).
7. **탈퇴 후 토큰 무효화(§5-4 확정)**: 토큰 버전 컬럼을 두지 않는다. 대신 **`JwtAuthenticationFilter` 가 유효한 액세스 토큰마다 `UserMapper.existsById(userId)` 로 사용자 존재를 확인**하고 없으면 401 `TokenErrorCode.INVALID_TOKEN` 을 쓴다(`writeError` 재사용; 인증을 세우지 않는다). 리프레시는 이미 `AuthService.refresh` 가 `findById==null → INVALID_TOKEN` 이므로 추가 변경 없음. 비용: 인증 요청당 PK 조회 1회(허용). 필터 생성자 `JwtAuthenticationFilter(JwtTokenProvider, ObjectMapper, UserMapper)` 로 바꾸고 `SecurityConfig` 가 `UserMapper` 를 주입받아 넘긴다(필터는 `@Component` 로 만들지 말 것 — 주석의 이유 그대로). 이로써 탈퇴한 사용자의 남은 토큰으로 FK 오류(500)가 나는 경로도 막힌다.
   - 비밀번호 변경 후 토큰 무효화(§1-4)는 **이번 범위가 아님**(`PATCH /me/password` 자체가 2차).
8. **API 응답/에러 계약**
   ```json
   // PATCH /api/me/profile   Request
   { "nickname": "하늘" }
   // Response 200 — GET /api/me 와 같은 모양
   { "id": 1, "email": "sky@email.com", "nickname": "하늘", "characterKey": "mongsil",
     "profileImageUrl": null, "createdAt": "2026-06-02T10:00:00+09:00" }
   ```
   - 닉네임: `@NotBlank @Size(min=2,max=10)` + compact constructor `trim()` → 위반 400 `VALIDATION_ERROR`. 그룹 내 프로필이 있는 그룹에는 반영되지 않는다(아직 그룹 내 프로필이 없으므로 항상 모두 반영).
   ```json
   // PUT /api/me/profile-image   multipart/form-data, 파트 file     Response 200
   { "profileImageUrl": "http://host/files/profiles/uuid.jpg" }
   // DELETE /api/me/profile-image  → 204
   // DELETE /api/me                → 204
   ```
   - 사진 오류: 415 `UNSUPPORTED_FILE_TYPE`(이미지가 아님/가짜 확장자/오디오 — 기존 `SubmissionErrorCode.UNSUPPORTED_FILE_TYPE` 재사용), 413 `FILE_TOO_LARGE`(기존 `CommonErrorCode`), `file` 파트 없음/빈 파일 → 400 `VALIDATION_ERROR`.
   - 탈퇴 오류: 409 `OWNER_MUST_TRANSFER`(신규 `WithdrawErrorCode`, 메시지 "그룹장인 그룹에서 먼저 나가 주세요"). 인증 없음 401.
   - `WithdrawErrorCode`/신규 코드는 기존 `GroupErrorCode`/`SubmissionErrorCode` 와 같은 구조(`ErrorCode` 구현: `code()/message()/httpStatus()`).

## Files / Functions to Change

| 파일 | 변경 |
|---|---|
| `src/main/resources/db/migration/V1__initial_schema.sql` | `users` 에 `` `profile_image_url` varchar(500) DEFAULT NULL `` 추가(`character_id` 다음 정도). V2 는 건드리지 않는다. |
| `auth/domain/User.java` | `String profileImageUrl` + getter/setter(저장 값은 key). |
| `auth/dao/UserMapper.java` | 두 SELECT 에 `profile_image_url` 추가, `@Select boolean existsById(@Param("id") Long id)` 추가(`SELECT COUNT(*) > 0 FROM users WHERE id = #{id}`), `@Update int updateNickname(id, nickname)`, `@Update int updateProfileImageUrl(id, profileImageUrl)`(NULL 허용), `@Delete int deleteById(id)` — 탈퇴 삭제 SQL 을 한 곳에 모으려면 `deleteById` 는 `WithdrawMapper` 에 둬도 된다(둘 중 하나로 일관되게). |
| `auth/service/AuthService.java` | 생성자에 `FileStorage` 추가, `getMe` 에서 `profileImageUrl` 변환. |
| `global/security/JwtAuthenticationFilter.java` | 생성자에 `UserMapper` 추가, 토큰 검증 직후 `existsById` 확인 → 없으면 `BusinessException(TokenErrorCode.INVALID_TOKEN)` 로 `writeError`. |
| `global/security/SecurityConfig.java` | `UserMapper` 주입(생성자 또는 `securityFilterChain` 파라미터), 필터 생성 시 전달. |
| `profile/controller/ProfileController.java` (신규) | `PATCH /api/me/profile`(`@Valid @RequestBody`), `PUT /api/me/profile-image`(`consumes = MULTIPART_FORM_DATA_VALUE`, `@RequestParam("file") MultipartFile`, 200), `DELETE /api/me/profile-image`(204). |
| `profile/dto/ProfileUpdateRequest.java`, `ProfileImageResponse.java` (신규) | `record ProfileUpdateRequest(@NotBlank @Size(min=2,max=10) String nickname)` + trim 하는 compact ctor; `record ProfileImageResponse(String profileImageUrl)`. |
| `profile/service/ProfileService.java` (신규) | `updateNickname(userId, request)` → `userMapper.updateNickname`(+ 사용자 없음 처리) → `authService.getMe(userId)`; `uploadImage(userId, MultipartFile)`; `deleteImage(userId)`. 파일 처리/커밋 후 삭제는 Key Point 4. |
| `withdraw/controller/WithdrawController.java` (신규) | `DELETE /api/me` → 204. |
| `withdraw/service/WithdrawService.java` (신규) | Key Point 6. 의존: `UserMapper`, `WithdrawMapper`, `GroupService`, `FileStorage`. `@Transactional`. |
| `withdraw/service/WithdrawErrorCode.java` (신규) | `OWNER_MUST_TRANSFER(CONFLICT, "그룹장인 그룹에서 먼저 나가 주세요")`. |
| `withdraw/dao/WithdrawMapper.java` (신규) | 애노테이션 SQL: `findGroupIdsByUserId`, `existsOwnedGroupWithOtherMembers`(또는 id 목록), 파일 key 수집 쿼리 4개(`findSubmissionMediaKeys`, `findCommentImageKeysOnMySubmissions`, `findMyCommentImageKeys`, 프로필은 `UserMapper.findById` 로 읽어도 됨), 삭제 쿼리들(Key Point 6-5 순서대로: 내 제출물 댓글의 멘션·댓글·반응·추측, 내 댓글 멘션·나를 멘션한 멘션 행·내 댓글, 내 반응, 내 추측, 내 제출물, 콕, 알림), `deleteUserById`. 다중 테이블 DELETE 는 기존 `GroupMapper` 의 `DELETE cm FROM ... JOIN ...` 스타일을 따른다. |
| `group/dao/GroupMemberPreviewRow.java`, `GroupMapper.java`(`findMemberPreviewsByGroupIds`) | `u.profile_image_url` 추가 + Row 필드. |
| `group/dao/GroupMemberRow.java`, `GroupMemberMapper.java`(`findMembersByGroupId`) | 〃 |
| `group/service/GroupService.java` | `toMemberProfile` 과 멤버 목록 변환에서 `profileImageUrl` 변환 사용(`FileStorage` 주입 필요하면 생성자에 추가 — **`GroupService` 를 직접 `new` 하는 테스트 생성자 호출을 함께 수정**). `leaveGroup`/`deleteGroupCascade` 로직은 변경하지 않는다. |
| `submission/dao/SubmissionMapper.java`(`FIELDS`), `SubmissionRow.java`, `submission/service/SubmissionService.java`(`response`) | `u.profile_image_url` 추가 후 변환. |
| `comment/dao/CommentMapper.java`(`FIELDS`), `CommentRow.java`, `comment/service/CommentService.java` | 〃 |
| 테스트 | 아래 "Tests Required" |
| 문서 | 아래 "Documentation" |

### Documentation (코드와 같은 변경에서 갱신)
- `docs/BACKEND/API_DESIGN.md`: **Me 절 신설**(`GET /me`, `PATCH /me/profile`, `DELETE /me`, 프로필 사진 PUT/DELETE; `PATCH /me/password` 는 "2차" 로 표기), §8 "프로필 사진 (설계만, 미구현)" → 구현됨으로, 프로필 사진 응답·오류·`DELETE` 204 반영. 탈퇴 시 삭제 범위 요약.
- `docs/BACKEND/BACKEND_DB.md`: `users.profile_image_url`(key 저장) 추가.
- `docs/BACKEND/CONTRACT_DECISIONS.md`: §5 상태를 "백엔드 반영됨 (그룹 내 프로필은 별도 단계, 앱은 8A)" 로; §5-4 미결 중 토큰 처리(필터가 사용자 존재 확인)·"삭제된 사용자의 작품에 달린 다른 사람 댓글·반응은 작품과 함께 삭제" 를 확정으로 정리; 9단계가 탈퇴 삭제 목록(기기 토큰·알림 설정)에 항목을 추가해야 한다는 한 줄.
- `docs/BACKEND/CONTRACT_WORK_PLAN.md`: 8단계 행에 비고 한 줄(백엔드 완료·그룹 내 프로필은 분리·앱 8A 대기).
- `docs/FEATURE/PROJECT_OVERVIEW.md` §10 "현재 진행 상태": 이번에 건드리는 김에 **인증/그룹/미션·제출/콕/반응·댓글/Me 행을 현재 구현 상태로 갱신**(지금은 "미션·제출·집계 API: 설계만" 등 낡아 있음). 다른 내용은 건드리지 않는다.
- 신규 `docs/BACKEND/postman/HAWE_ME.postman_collection.json` (`HAWE_POKE.postman_collection.json` 형식, UTF-8, 한글 깨짐 없음): 닉네임 변경, 사진 PUT/DELETE, 탈퇴.

## Implementation Steps

1. **스키마**: V1 `users` 에 `profile_image_url` 추가. **로컬 DB 재생성 필요**(Flyway checksum 변경): 로컬 개발 DB(`localhost:3306/hawe`)만, 먼저 `mysqldump`/`mariadb-dump` 로 `%TEMP%` 에 백업 → `DROP DATABASE hawe; CREATE DATABASE hawe CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;` → 테스트로 Flyway 재적용. 백업 경로와 재생성 전 행 수를 보고서에 적는다. `flyway clean/repair` 설정을 코드에 넣지 말 것.
2. `User`/`UserMapper`/`AuthService.getMe` 에 `profile_image_url`, `existsById`, 업데이트 쿼리 추가 + `AuthServiceTests` 생성자 수정.
3. **표시 응답 4곳**에 프로필 사진 반영(Mapper SELECT·Row·Service 변환) + 관련 테스트 생성자/목 수정.
4. **토큰 무효화**: `JwtAuthenticationFilter`+`SecurityConfig` 수정, `JwtAuthenticationFilterTests` 수정/추가.
5. **프로필 패키지**: 닉네임 변경, 사진 PUT/DELETE.
6. **탈퇴 패키지**: `WithdrawMapper`, `WithdrawService`, 컨트롤러, 오류 코드.
7. 테스트 작성 → `.\mvnw.cmd clean test -q` 통과.
8. 문서·Postman 갱신.

## Constraints

- 범위는 **백엔드 + 문서**. `android/`, `ios/`, `frontend/`, `V2__seed_characters.sql` 은 건드리지 않는다. 그룹 내 프로필 관련 코드(컬럼·API·응답 우선 적용)를 **만들지 않는다**.
- `PATCH /me/password`, 알림/FCM/기기 토큰/알림 설정 테이블은 만들지 않는다(9단계·2차).
- 새 마이그레이션(V3)을 만들지 말고 V1 을 직접 수정한다(프로젝트 결정).
- `CharacterService`/`PATCH /me/character` 동작 변경 금지(사진을 지우지 않는다).
- `GroupService.leaveGroup`/`deleteGroupCascade` 시그니처·동작 변경 금지(탈퇴가 `leaveGroup` 을 재사용). `GroupService` 의 변경은 프로필 사진 변환과 `FileStorage` 주입에 한정.
- `FileStorage` 인터페이스에 메서드를 추가하지 않는다. 파일 삭제는 항상 **커밋 후**, 실패는 로그만(응답을 깨지 말 것).
- 패키지 규칙(`controller/service/dto/domain/dao`만), 기존 스타일(애노테이션 MyBatis, 한 줄 getter/setter Row, 짧은 서비스 메서드), 주석 최소. 커밋/푸시 금지, `CLAUDE.md`/`AGENTS.md` 수정 금지.

## Edge Cases

- 닉네임: 앞뒤 공백 제거 후 2~10자(" a " → "a" 1자 → 400), 공백뿐/누락/null → 400, 같은 닉네임으로 변경(영향 행 0) → 200 정상(JDBC 가 0을 보고해도 오류로 보지 말 것 — 사용자 존재는 `findById`/`existsById` 로 확인), 중복 닉네임 허용(유니크 아님).
- 프로필 사진: 이미지가 아닌 파일·가짜 확장자·오디오 → 415; 10MB 초과 → 413; `file` 파트 없음/0바이트 → 400; 파트가 2개 이상이면 400(또는 첫 번째만 쓰되 일관되게 문서화); 교체 시 이전 파일이 커밋 후 삭제되고, 트랜잭션 롤백이면 새 파일이 삭제되고 이전 파일은 남는다; 사진 삭제 후 `GET /me` 의 `profileImageUrl` 은 `null`; 사진이 없는데 DELETE → 204. 사진이 있어도 `characterKey` 는 유지되어 `GET /me` 에 계속 내려간다.
- 사진이 있는 사용자의 프로필이 그룹 멤버 목록·미리보기·피드·댓글 응답에 `profileImageUrl`(전체 URL) 로 나오고, 없으면 `null`(JSON `null` 유지; 댓글 응답은 `@JsonInclude(NON_NULL)` 이 없으므로 `"profileImageUrl": null`, 제출 응답 `SubmissionResponse` 는 `NON_NULL` 이라도 `author` 안의 `MemberProfileResponse` 는 영향 없음 — 기존 동작 확인 후 유지).
- 탈퇴: 
  - 혼자인 그룹의 그룹장 → 그룹 전체 삭제 후 탈퇴 성공(그 그룹의 미션·제출·콕 등도 사라짐).
  - 일반 멤버로만 속함 → 멤버십 삭제, 남은 그룹 데이터 보존, **내 제출물·댓글은 삭제**(그룹에 남기지 않음 — 그룹 나가기와 다름).
  - 다른 멤버가 있는 그룹의 그룹장 → 409 `OWNER_MUST_TRANSFER`, **어떤 데이터도 삭제되지 않음**(여러 그룹 중 하나만 걸려도 전체 롤백/사전 거절).
  - 그룹이 하나도 없는 사용자 → 정상.
  - 내 제출물에 다른 멤버의 댓글·반응·추측이 달려 있음 → 모두 삭제, 댓글 이미지 파일도 삭제.
  - 다른 멤버가 쓴 댓글이 나를 멘션 → 댓글은 남고 멘션 행만 삭제.
  - 내가 보낸/받은 콕·알림 삭제(`pokes.mission_id` FK 때문에 콕이 있는 채로 미션/제출만 지우면 안 됨 — 순서 확인).
  - 같은 이메일 재가입 가능, 재가입 계정은 새 id.
  - 두 번 호출/이미 삭제된 계정의 토큰 → 필터에서 401 `INVALID_TOKEN`.
  - 동시 가입으로 `leaveGroup` 이 `SUCCESSOR_REQUIRED` → `OWNER_MUST_TRANSFER` 로 변환, 전체 롤백.
- 필터: 탈퇴한 사용자의 액세스 토큰으로 어떤 보호된 API 를 호출해도 401 `INVALID_TOKEN`; 존재하는 사용자는 기존과 동일; `/api/auth/**`·`GET /api/characters` 는 여전히 필터 제외.
- 대상 지정 미션의 대상이 탈퇴한 경우: 미션 `params` 의 스냅샷은 그대로 두며 `CalendarMapper` 서브쿼리는 null → "친구" 처리(변경 없음, 회귀 확인).

## Regression Risks

- **`JwtAuthenticationFilter` 가 매 요청 DB 를 조회** → 존재하지 않는 사용자 id 로 토큰을 만들어 호출하는 테스트가 있으면 실패한다. 현재 `CommentControllerTests`/`ReactionControllerTests`/`PokeControllerTests`/`SubmissionControllerTests` 는 실제로 INSERT 한 사용자 id 로 토큰을 만들지만, **전체 테스트를 돌려 확인하고** 실패하는 테스트가 있으면 그 테스트가 실제 사용자 행을 만들도록 고친다(필터 쪽을 완화하지 말 것). `JwtAuthenticationFilterTests` 는 `UserMapper` 목을 넘기도록 수정.
- **생성자 시그니처 변경**: `AuthService`(+`FileStorage`), `JwtAuthenticationFilter`(+`UserMapper`), `GroupService`(+`FileStorage`, 필요한 경우) — 직접 `new` 하는 단위 테스트(`AuthServiceTests`, `JwtAuthenticationFilterTests`, `GroupContractServiceTests`/`GroupServiceTests` 류)를 모두 찾아 수정. `@SpringBootTest` 기반은 영향 없음.
- **쿼리 SELECT 컬럼 추가**: `UserMapper` 두 SELECT 와 Row 매핑(camelCase 변환) 누락 시 `profileImageUrl` 이 항상 null 로 보이는 조용한 버그 → 매퍼 테스트로 값 왕복 검증.
- **V1 수정 → 이미 V1 을 적용한 DB 는 Flyway checksum 불일치.** 로컬 DB 재생성 필수. 공유 개발 DB 는 별도 조치 필요(보고서에 명시).
- 탈퇴 삭제 SQL 의 FK 순서 오류는 **런타임 FK 위반(500)** 으로만 드러남 → 통합 테스트에서 모든 종속 테이블에 행이 있는 사용자로 탈퇴를 실제로 수행해 검증.
- 파일 삭제를 트랜잭션 안에서 하면 롤백 시 파일만 사라진다 → 반드시 커밋 후.
- 7단계 미커밋 파일과 같은 파일을 수정(아래 Merge Conflicts). 7단계 코드를 되돌리지 말 것.

## Tests Required

- `auth/dao/UserMapperTests`(기존 확장, 실 MariaDB): `profile_image_url` 저장·조회, `updateNickname`, `updateProfileImageUrl`(NULL 포함), `existsById`.
- `auth/service/AuthServiceTests`(기존 수정): 생성자 변경 반영 + `getMe` 가 키가 있으면 `publicUrl` 결과를, 없으면 `null` 을 반환.
- `global/security/JwtAuthenticationFilterTests`(기존 수정+추가): 존재하는 사용자 → 인증 세워짐; **존재하지 않는 사용자의 유효한 액세스 토큰 → 401 `INVALID_TOKEN` JSON**, 인증 미설정, 체인 미진행; `/api/auth/**`·`GET /api/characters` 는 DB 조회 없이 통과.
- `profile/service/ProfileServiceTests`(Mockito): 닉네임 변경이 `getMe` 결과를 반환; 사진 PUT 의 검증 실패(비이미지 415 / 0바이트 400 / 10MB 초과 413)에서 저장·DB 갱신이 일어나지 않음; 교체 시 이전 key 삭제 예약; DELETE 멱등.
- `profile/controller/ProfileControllerTests`(MockMvc 통합, `SubmissionControllerTests` 스타일 — 임시 업로드 디렉터리 `@DynamicPropertySource`, 실제 INSERT 한 사용자 + `tokens.createAccessToken`): 401(토큰 없음); `PATCH` 정상/trim/1자 400/누락 400; `PUT` 이미지 200 + `profileImageUrl` 이 `/files/profiles/...` 로 실제 접근 가능 + `GET /me` 반영 + 그룹 멤버 목록·피드·댓글 응답에 `profileImageUrl` 반영(그룹·제출·댓글을 만들어 확인); 교체 시 이전 파일이 임시 디렉터리에서 사라짐; `DELETE` 후 `null`; 비이미지 415, `file` 없음 400; JSON 으로 PUT 하면 415 `UNSUPPORTED_MEDIA_TYPE`.
- `withdraw/service/WithdrawServiceTests`(Mockito): 그룹장 검사가 삭제보다 먼저 실행(`OWNER_MUST_TRANSFER` 시 삭제 쿼리 호출 없음, `InOrder`), 모든 그룹에 `leaveGroup(userId, groupId, null)` 호출, `SUCCESSOR_REQUIRED` → `OWNER_MUST_TRANSFER` 변환, 삭제 쿼리 순서(InOrder), 파일 삭제는 커밋 후(트랜잭션 동기화 활용 방식에 맞게 검증), 파일 삭제 실패해도 예외 전파 없음.
- `withdraw/dao/WithdrawMapperTests`(실 MariaDB, `@SpringBootTest @Transactional`): 각 종속 테이블에 내 행·남의 행을 섞어 넣고 삭제 쿼리 후 **내 것만 사라지고 남의 것은 남는지** 검증(특히 나를 멘션한 남의 댓글은 남고 멘션 행만 삭제, 내 제출물에 달린 남의 댓글은 삭제).
- `withdraw/controller/WithdrawControllerTests`(MockMvc 통합): ① 그룹 2명(내가 일반 멤버) + 내 제출·댓글·반응·콕·(가능하면 알림 행) → `DELETE /api/me` 204, 사용자·제출·댓글 사라짐, 상대의 제출·그룹 유지, 제출/댓글 이미지·프로필 사진 파일이 임시 디렉터리에서 삭제, 이후 같은 토큰으로 `GET /api/me` → 401 `INVALID_TOKEN`, 같은 이메일로 재가입 201 ② 다른 멤버가 있는 그룹의 그룹장 → 409 `OWNER_MUST_TRANSFER` 이고 사용자·데이터 그대로 ③ 혼자인 그룹장 → 204 + 그룹 삭제 ④ 인증 없음 401.
- 그룹 응답 테스트(기존 `GroupContract*`, `GroupServiceTests` 류)·`SubmissionServiceTests`·`CommentServiceTests` 가 새 필드/생성자 변경 후에도 통과하는지 확인하고, 필요하면 `profileImageUrl` 변환 케이스(키 있음/없음)를 한 곳씩 추가.

## Validation Steps

1. `.\mvnw.cmd clean test -q` (Java 25, 로컬 MariaDB 필요) — 전체 통과, 테스트 수·실패 0 기록. 별도 lint/typecheck 명령은 없다.
2. `rg "profileImageUrl|\), null\)" src/main` 로 `MemberProfileResponse(..., null)`/`GroupMemberResponse(..., null, ...)` 형태의 하드코딩 `null` 이 남은 곳이 없는지 확인(4곳 모두 변환 사용).
3. `rg "new JwtAuthenticationFilter\(" src` — 모든 호출부가 3-인자.
4. Postman JSON 을 `ConvertFrom-Json` 으로 파싱, 한글 대체 문자(�) 없음 확인.
5. `git diff --check` — 공백 오류 없음(LF/CRLF 경고는 무시).
6. 서버 기동 수동 HTTP 스모크가 가능하면 수행(가입→로그인→사진 PUT→`GET /me`→탈퇴→같은 토큰 401), 못 했으면 보고서에 "미검증" 명시 + MockMvc 대체 범위 기재.
7. 보고서(`.agent/report.md`)에 포함: 변경 파일과 동작, 검증 결과, DB 재생성 여부·백업 경로·재생성 전 행 수, 남은 위험(공유 DB checksum, 그룹 삭제 시 파일 고아 — 혼자인 그룹 cascade 로 지워지는 **남의 아닌 내** 파일은 수집해 삭제하지만 그룹 전체 정리는 기존 후속 항목), 범위 밖으로 둔 것(그룹 내 프로필, `PATCH /me/password`, 9단계 기기 토큰 정리, 앱 8A).

## Potential Merge Conflicts

- **7단계 미커밋 변경과 겹침**: `submission/service/SubmissionService.java`, `submission/dao/SubmissionMapper.java`, `SubmissionRow.java`, `comment/dao/CommentMapper.java`, `CommentRow.java`, `comment/service/CommentService.java`, `V1__initial_schema.sql`(reactions 유니크 키 + 이번 users 컬럼), `docs/BACKEND/{API_DESIGN,BACKEND_DB,CONTRACT_DECISIONS,CONTRACT_WORK_PLAN}.md`. 같은 작업 트리이므로 코드 충돌은 없지만 **7단계가 커밋되기 전에 8단계 diff 가 섞인다** → 사용자가 7단계를 먼저 커밋하거나, 보고서에서 8단계 변경 파일 목록을 7단계와 분리해서 적을 것.
- `group/service/GroupService.java`, `GroupMapper.java`, `GroupMemberMapper.java`: 이후 "그룹 내 프로필" 단계가 같은 파일·쿼리를 다시 건드린다(표시 프로필 우선 적용) — 이번엔 `profile_image_url` 컬럼 추가에 한정해 변경을 최소로 유지.
- `global/security/SecurityConfig.java`, `JwtAuthenticationFilter.java`: 9단계(알림)·`PATCH /me/password` 단계(토큰 무효화)가 인증 경로를 건드릴 수 있음.
- 앱(Android/iOS)은 건드리지 않으므로 앱 병렬 작업과 겹치지 않는다. 단, 8A 에서 `GET /me`·멤버/피드/댓글의 `profileImageUrl` 이 실제 URL 로 채워지는 것을 앱이 처리하는지 확인할 것.
