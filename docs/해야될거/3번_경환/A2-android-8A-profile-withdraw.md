# A2. Android 8A — 프로필 편집(17-1)·가입 사진·회원 탈퇴(17-5) 서버 연결

> 범위: **Android 앱만**(`android/`). **전제: 백엔드 8단계(닉네임 `PATCH /me/profile`, 프로필 사진 `PUT/DELETE /me/profile-image`, 탈퇴 `DELETE /me`)가 커밋된 뒤.** iOS 는 A5. 알림 무관. 구현자: Claude 또는 Codex. 디자인 없는 부분은 임시 문구/이미지로 채운다.
> 빌드/테스트: JDK 21 — `set JAVA_HOME=C:\Users\kimch\.jdks\ms-21.0.10` 후 `android\gradlew.bat :app:testDebugUnitTest --offline`.

## Goal

1. 17-1 프로필 편집이 서버에 저장된다: 닉네임, 캐릭터, **사진**(로컬에서 고른 사진을 업로드, 사진→캐릭터 전환 시 서버 사진 삭제).
2. 가입(A3 프로필 화면)에서 **사진 프로필**을 고를 수 있고 가입 완료 시 업로드된다(지금은 `showPhotoCell = false`).
3. 17-5 탈퇴가 서버 `DELETE /api/me` 를 호출한다. 다른 멤버가 있는 그룹의 그룹장이면 진행을 막고 안내한다. 성공 시 토큰 삭제 → 로컬 정리 → 로그인 화면.

## Findings

- 서버 계약(`CONTRACT_DECISIONS.md` §5, 8단계 계획·`API_DESIGN.md` Me 절):
  - `PATCH /api/me/profile` `{ "nickname": "하늘" }` → 200 `GET /me` 모양(`MeDto`). 닉네임 trim 후 2~10자(400 `VALIDATION_ERROR`).
  - `PATCH /api/me/character` `{ "characterId": 3 }`(기존, 앱이 이미 사용: `ApiClient.updateCharacter`). **캐릭터를 바꿔도 서버는 사진을 지우지 않는다** → 캐릭터를 골랐고 서버에 사진이 있었다면 앱이 `DELETE /api/me/profile-image` 도 호출.
  - `PUT /api/me/profile-image` multipart `file`(jpg/png/webp ≤10MB) → 200 `{ "profileImageUrl": "…/files/profiles/….jpg" }`; 오류 415 `UNSUPPORTED_FILE_TYPE`, 413 `FILE_TOO_LARGE`, 400. `DELETE /api/me/profile-image` → 204.
  - `DELETE /api/me` → 204. 409 `OWNER_MUST_TRANSFER`("그룹장인 그룹에서 먼저 나가 주세요"). 탈퇴 후 이전 토큰은 401 `INVALID_TOKEN`.
  - `GET /api/me` 의 `profileImageUrl` 이 있으면 사진, 없으면 `characterKey`.
- 앱 현재 상태:
  - `ProfileEditScreen`(`feature/mypage/ProfileEditScreen.kt`): `ProfileEditState(avatar, nickname)`, `CharacterPicker`(사진 셀 포함 — 로컬 사진 선택 UI 있음), 저장 시 `session.account = …copy(profile = …); store.setMyProfile(profile)` — **로컬만**.
  - `SignupProfileScreen`(`feature/auth/SignupProfileScreen.kt`): `CharacterPicker(avatar, …, showPhotoCell = false)`, `character` 만 허용(`(avatar as? AvatarContent.Character)?.character ?: return`), `auth.signUp(email, password, nickname, character, agreeTerms, agreePrivacy, registered, onRegistered)`.
  - `AuthRepository`(`core/net/AuthRepository.kt`): `login`, `signUp`(register → login → characters 조회 후 `updateCharacter`), `restore()` = `api.me().toAccount()` — `toAccount()` 는 `profileImageUrl` 을 무시하고 `AvatarContent.Character` 만 만든다.
  - `AuthApi.kt`: `ApiClient.register/login/me/characters/updateCharacter` 확장, `MeDto(…, profileImageUrl, …)`.
  - `AvatarContent`(`core/model/GroupDetail.kt`): `Character(character)` / `Photo(uri: android.net.Uri)` — 로컬 uri 용이지만 http URL 도 `Uri.parse` 로 담을 수 있다(`HAvatar` 가 Coil `AsyncImage` 로 그리는지 구현 확인 후, 원격 URL 이 안 그려지면 `HAvatar` 의 Photo 분기를 URL 도 지원하게 최소 수정).
  - 사진 처리 유틸: `feature/mission/PhotoProcessing.kt`(제출용 JPEG 처리) — 재사용 가능한지 확인(리사이즈/압축).
  - `WithdrawScreen`(`feature/mypage/WithdrawScreen.kt`): 동의 체크 → `store.reset(); session.withdraw()` **로컬만**. 안내 문구에 "방장인 그룹은 가장 오래된 멤버에게 방장이 넘어가요" 가 있다 — **서버 규칙과 다르므로 수정 필요**. `AppSession.withdraw()` = `onCleared(); account = mock; …; state = SIGNED_OUT`.
  - `AppSession`(`core/AppSession.kt`), `AuthRepository.clearSession()` = `tokens.clear()`.
  - 그룹 소유 판별: `store.groups` 의 `StoredGroup.detail`(`GroupDetail.members: List<GroupMember(isMe,isOwner)>`, `amIOwner`, `memberCount` 상당 `members.size`) — 탈퇴 전 "내가 그룹장이고 멤버가 2명 이상인 그룹" 목록을 앱이 미리 찾을 수 있다(CONTRACT §5-2).
  - 오류 문구 매핑 `core/net/ApiMessages.kt`(예: `SUCCESSOR_REQUIRED` 문구가 이미 있음).

## Root Cause / Key Implementation Points

1. **레포지토리 확장**(`core/net/ProfileRepository.kt` 신규 또는 `AuthRepository` 확장 — 기존 패턴상 `AuthRepository` 에 `updateProfile`, `withdraw` 를 추가하는 쪽이 간단): 
   - `suspend fun saveProfile(current: AvatarContent, nickname: String, avatar: AvatarContent, serverHasPhoto: Boolean): MyAccount` 
     순서: ① 닉네임이 바뀌었으면 `PATCH /me/profile` ② 아바타가 `Character` 이면 → 캐릭터 id 조회(`characters()` → `svgKey` 매칭) 후 `PATCH /me/character`, 서버에 사진이 있었으면 `DELETE /me/profile-image` ③ 아바타가 새 `Photo`(로컬 uri)이면 이미지 바이트 읽기(+`PhotoProcessing` 압축, ≤10MB) 후 `PUT /me/profile-image` ④ 마지막에 `restore()`(= `GET /me`)로 계정 갱신. 부분 실패 시: 이미 성공한 단계는 서버에 반영돼 있으므로 **`restore()` 로 현재 서버 상태를 다시 읽어** 화면에 반영하고 에러를 보여 준다(되돌리기 시도 금지).
   - `suspend fun withdraw()` = `DELETE /api/me`(`ApiClient.delete`), 성공하면 `clearSession()`.
   - `MeDto.toAccount()` 에 `profileImageUrl` 이 있으면 `AvatarContent.Photo(Uri.parse(api.absoluteUrl(url)))` 로 매핑(캐릭터 키는 유지하되 표시는 사진 우선). `MyAccount` 에 `hasServerPhoto` 판단용으로 `profile.avatar.isPhoto` 를 쓰면 충분.
1-b. **공용 아바타 변환(A3 가 재사용)**: `core/net/AvatarMapping.kt` 에 `internal fun ApiClient.avatarOf(characterKey: String?, profileImageUrl: String?): AvatarContent` 를 추가한다(`profileImageUrl != null` → `AvatarContent.Photo(Uri.parse(absoluteUrl(url)))`, 아니면 `Character(ProfileCharacter.fromKey(characterKey))`). 이 계획에서는 `AuthRepository.toAccount()` 에만 적용하되, 8단계 서버가 그룹 멤버·피드·댓글 응답에도 `profileImageUrl` 을 채워 내려주므로 **같은 변환을 `GroupRepository`(`GroupMemberProfileDto.toMember`, `GroupMemberDto.toMember`)와 `SubmissionRepository`(`SubmissionDto.toModel` 작성자, 7A `CommentDto.toModel`)에도 적용**한다(변환 호출만 바꾸는 작은 수정, 테스트 1~2개). `AvatarContent.Photo(uri)` 의 원격 URL 렌더(`HAvatar`)를 확인한다.
2. **UI/상태**:
   - `ProfileEditScreen`: 저장 버튼 클릭 → 진행 중 상태(`saving`)로 버튼 비활성+중복 클릭 방지 → 성공 시 `session.account = account; store.setMyProfile(account.profile); onBack()`; 실패 시 토스트/인라인 에러(`ApiMessages` 매핑: 400 닉네임, 413/415 사진, 네트워크). 닉네임·아바타 변경이 없으면 저장 버튼 비활성(기존 `state.canSave` 유지).
   - `SignupProfileScreen`: `showPhotoCell = true` 로 열고, 사진이면 `signUp(…)` 이후 업로드 단계 추가. `AuthRepository.signUp` 시그니처를 `avatar: AvatarContent` 로 바꾸고 사진이면 `PUT /me/profile-image`(실패해도 가입은 유지하고 기본 캐릭터로 시작 — 기존 캐릭터 실패 처리와 같은 정책, 나중에 프로필 편집에서 재시도 안내).
   - `WithdrawScreen`: 진입 시 `store.groups` 에서 **소유+멤버 2명 이상 그룹 목록**을 계산(`withdrawBlockers(groups)` 순수 함수로 분리해 단위 테스트). 있으면 안내 박스("먼저 후임자를 지정해 나가야 하는 그룹이 있어요: A, B")를 보여 주고 `탈퇴하기` 비활성(또는 눌렀을 때 안내), 각 그룹으로 이동하는 링크는 선택(A3 의 후임자 선택 화면이 생긴 뒤 연결 — 이번에는 안내만). 없으면 동의 체크 후 `withdraw()` 호출 → 성공 시 `store.reset(); session.withdraw()`(기존 로컬 정리 유지) → 로그인 화면. 서버 409 `OWNER_MUST_TRANSFER` 도 같은 안내로 처리(서버가 최종 권위). 안내 문구 수정: "다른 멤버가 있는 그룹의 그룹장이라면 먼저 다음 그룹장을 정해 나가야 해요. 혼자 있는 그룹은 함께 삭제돼요."
3. **토큰/세션**: 탈퇴 성공 후 `AuthRepository.clearSession()`(토큰 삭제) 이 `session.withdraw()` 보다 먼저. 이후 서버가 `INVALID_TOKEN` 을 주면 `ApiClient` 가 세션 만료 처리하므로(`onSessionExpired`) 중복 처리가 일어나도 한 번만 로그아웃되게 이미 있는 `expireSession` 의 `hadSession` 가드를 신뢰.
4. **임시 UI**: 사진 업로드 중 표시는 기존 `HButton` 의 `enabled=false` + 임시 문구("저장 중…")로 충분(별도 이미지 불필요).

## Files / Functions to Change

| 파일 | 변경 |
|---|---|
| `core/net/AuthApi.kt` | `ProfileUpdateDto(nickname)`, `ProfileImageResponseDto`, `ApiClient.updateNickname`, `uploadProfileImage`(multipart: `ApiClient.postMultipart` 는 POST 만 지원 → **PUT multipart 지원**이 필요: `executeMultipart` 에 method 파라미터 추가 또는 `putMultipart` 추가), `deleteProfileImage`, `withdraw` |
| `core/net/ApiClient.kt` | multipart 메서드를 `POST` 고정에서 `method` 인자로 일반화(기존 `postMultipart` 호출부 불변) |
| `core/net/AuthRepository.kt` | `saveProfile`, `withdraw`, `signUp` 아바타 일반화, `toAccount()` 사진 매핑 |
| `core/net/ApiMessages.kt` | `OWNER_MUST_TRANSFER`, 프로필 관련 문구 |
| `feature/mypage/ProfileEditScreen.kt`, `ProfileEditState`(있다면 `MyPageLogic.kt`) | 저장 흐름·진행 상태 |
| `feature/auth/SignupProfileScreen.kt` | 사진 셀 활성화·업로드 |
| `feature/mypage/WithdrawScreen.kt`, `MyPageLogic.kt`(`withdrawBlockers`) | 차단 안내·서버 호출 |
| `designsystem/components/HAvatar.kt` | 원격 URL 사진 표시 확인(필요 시 최소 수정) |
| `app/MainNavigation.kt` | `WithdrawScreen`/`ProfileEditScreen` 에 `AuthRepository` 전달(이미 `auth` 참조가 있는지 확인) |
| 테스트 | `core/net/AuthRepositoryTest`(MockWebServer) 확장: 저장 순서/조건부 호출/부분 실패 시 `restore`, 사진→캐릭터 시 `DELETE`, `signUp` 사진, `toAccount` 사진 매핑; `ApiClientTest`: PUT multipart; `feature/mypage/MyPageLogicTest`: `withdrawBlockers`; 탈퇴 성공/409/네트워크 |

## Implementation Steps

1. `ApiClient` PUT multipart 일반화 + 테스트. 2. DTO·확장 함수. 3. `AuthRepository` 기능 + 테스트. 4. 프로필 편집 화면 연결. 5. 가입 화면 사진. 6. 탈퇴 화면(차단 안내 + 서버 호출). 7. `android\gradlew.bat :app:testDebugUnitTest --offline` 통과. 8. 서버 연동 확인은 사용자(사진 업로드→재로그인 후 유지, 탈퇴→로그인 화면→같은 이메일 재가입).

## Constraints

- `android/` 만 수정. 백엔드/iOS 수정 금지. 알림·간편 로그인·비밀번호 변경(A3) 범위 밖. 그룹 내 프로필(A3) 건드리지 않기.
- 서버 실패를 낙관 처리하지 않는다(서버 성공 확인 뒤 로컬 반영, 항상 `GET /me` 로 최종 상태 동기화). 목업 계정(`MyAccount.mock`)과 서버 계정을 섞지 않는다. 새 라이브러리 금지. 커밋/푸시 금지.

## Edge Cases

- 닉네임만 변경 / 캐릭터만 변경 / 사진만 변경 / 셋 다 / 아무것도 안 바꿈(저장 비활성).
- 사진→캐릭터 전환: `PATCH character` 후 `DELETE profile-image`; 서버에 사진이 없는데 캐릭터 선택 시 `DELETE` 호출 생략(또는 멱등 204 허용 — 호출해도 무방).
- 사진 10MB 초과/비이미지/읽기 실패 → 앱에서 먼저 막고 서버 오류도 문구 처리. 사진 업로드 중 화면 이탈(뒤로가기) → 요청 취소 시 중간 상태가 남을 수 있음 → 다음 진입 시 `GET /me` 값이 진실.
- 닉네임 1자/11자/공백뿐 → 클라이언트 검증(기존) + 서버 400 문구.
- 탈퇴: 그룹장+멤버 있음 → 차단 안내; 그룹이 한 개도 없는 사용자; 네트워크 오류 → 토스트·계속 사용 가능; 서버는 성공했는데 응답 유실 후 재시도 → 401 `INVALID_TOKEN` → 세션 만료 처리로 로그인 화면(정상).
- 탈퇴 후 같은 이메일 재가입이 가능한지(사용자 확인).

## Regression Risks

- `AuthRepository.signUp` 시그니처 변경 → `SignupProfileScreen`, `AuthRepositoryTest`, 가입 흐름의 `registered` 재시도 로직(register 성공 후 login/업로드 실패 시 `skipRegister`)을 깨지 않게.
- `MeDto.toAccount()` 변경 → 홈·마이페이지·그룹 화면의 내 아바타 표시가 사진/캐릭터 양쪽에서 정상인지.
- `ApiClient.executeMultipart` 일반화가 제출 업로드(`postMultipart`)·7A 댓글 업로드에 영향 없도록 기본값 POST 유지.
- `store.setMyProfile(profile)` 가 그룹 멤버 표시(내 프로필)에 쓰이므로, 서버가 내려주는 멤버 프로필과 어긋나지 않는지(다음 `loadAll` 에서 서버 값으로 덮임).

## Tests Required

- `AuthRepositoryTest`: `saveProfile` 시나리오 매트릭스(MockWebServer 요청 순서·경로·본문 검증), 부분 실패(닉네임 성공→사진 413)에서 `GET /me` 재조회, `withdraw` 204/409/401.
- `ApiClientTest`: PUT multipart 전송, 기존 POST 불변.
- `MyPageLogicTest`: `withdrawBlockers`(소유+멤버≥2 / 소유+혼자 / 비소유 / 빈 목록).
- `ProfileEditState` 변경 감지·저장 가능 조건.
- (UI 테스트 없음 — 컴파일 + 수동 확인.)

## Validation Steps

`android\gradlew.bat :app:testDebugUnitTest --offline`(JDK 21) 통과 기록. `rg "session.withdraw|store.reset" android` 로 탈퇴 경로가 서버 호출 뒤에만 실행되는지 확인. 서버+에뮬레이터 확인은 사용자(미검증이면 보고서에 명시).

## Potential Merge Conflicts

`AuthRepository.kt`, `AuthApi.kt`, `ApiClient.kt`(A3 비밀번호 변경이 같은 파일을 건드림 — A2 먼저), `SignupProfileScreen.kt`, `ProfileEditScreen.kt`, `WithdrawScreen.kt`, `MainNavigation.kt`, `HAvatar.kt`.
