# A3. Android — 그룹 2차: 그룹 내 프로필(16-1) · 후임자 선택 · 초대 코드 재발급 · 주제 도착 시각 · 비밀번호 변경

> 범위: **Android 앱만**(`android/`). **전제: 백엔드 B1(그룹 내 프로필)·B2(코드 재발급)·B3(비밀번호 변경)가 커밋된 뒤, 그리고 A2 가 끝난 뒤**(A2 가 `ApiClient`/`AuthRepository`/`avatarOf` 공용 변환을 정리). 그룹 알림 토글("알림 준비 중")은 알림 단계라 **그대로 둔다**. iOS 는 A5. 구현자: Claude 또는 Codex. **디자인이 없는 후임자 선택 화면은 임시 화면으로 만든다**(기존 `HListRow`/`HAvatar`/`HButton` 조합, 화면에 "임시" 표시는 하지 않되 코드 주석에 `임시 UI` 명시).
> 빌드/테스트: JDK 21 — `set JAVA_HOME=C:\Users\kimch\.jdks\ms-21.0.10` 후 `android\gradlew.bat :app:testDebugUnitTest --offline`.

## Goal

1. **16-1 그룹 내 프로필**: "기본 프로필 사용" 토글 / 그룹 전용 닉네임+캐릭터(또는 사진) 저장·해제가 서버에 반영되고, 멤버 목록·피드·댓글·홈 미리보기에 그 프로필이 보인다.
2. **그룹장 나가기**: 다른 멤버가 있으면 후임자를 고르는 화면을 거쳐 `DELETE /api/groups/{id}/members/me?successorUserId=` 로 나간다. 혼자인 그룹장은 "그룹이 삭제돼요" 문구.
3. **16 화면**: 그룹장에게 초대 코드 **재발급** 버튼, **주제 도착 시각** 변경(그룹장), 모든 멤버는 코드 복사·공유.
4. **17-2 비밀번호 변경**이 서버(`PATCH /api/me/password`, 응답 새 토큰 쌍)로 동작하고 새 토큰을 저장한다.

## Findings

- 서버 계약:
  - 그룹 내 프로필(B1): `PUT /api/groups/{groupId}/members/me/profile` `{ "nickname", "characterId" }` → 200 프로필 객체 `{userId,nickname,characterKey,profileImageUrl}`; `PUT …/members/me/profile-image` multipart(`file`, `nickname`) → 200 프로필 객체; `DELETE …/members/me/profile` → 204. `GET /groups/{id}/members` 항목에 `hasGroupProfile`. 닉네임 trim 후 2~10자. 사진 415/413. 표시 프로필은 서버가 모든 응답에 적용해 내려준다(앱은 받은 값을 그대로 표시).
  - 재발급(B2): `POST /api/groups/{groupId}/invite-code` → 200 `{ inviteCode, inviteUrl }`(그룹장만, 403 `NOT_GROUP_OWNER`). 코드 조회 과다 실패 시 429 `TOO_MANY_REQUESTS`(미리보기·가입 화면 문구 필요).
  - 설정 변경(기존 서버): `PATCH /api/groups/{groupId}/settings`(그룹장만) 에 `{ "missionSlots": [ { "slotNo": 1, "publishTime": "21:30" } ] }`. 마감 시각은 기본값으로 돌아감(앱에 마감 UI 없음). 적용 시점: 오늘 미션이 이미 열렸으면 내일부터(`MISSIONS_API §2`).
  - 나가기(기존 서버): 일반 멤버 204; 그룹장+다른 멤버 → `successorUserId` 필수(없으면 409 `SUCCESSOR_REQUIRED`, 잘못된 값 400 `INVALID_SUCCESSOR`); 혼자인 그룹장 → 그룹 삭제 204.
  - 비밀번호 변경(B3): `PATCH /api/me/password` `{ currentPassword, newPassword }` → **200 `{ accessToken, refreshToken }`**(계약 보완: 204 아님). 틀린 현재 비밀번호는 **400 `WRONG_CURRENT_PASSWORD`**(401 아님 — 로그아웃되면 안 됨). 변경 후 이전 토큰은 모두 무효.
- 앱 현재 상태:
  - `GroupProfileScreen`(`feature/groups/GroupProfileScreen.kt`): 토글 `useDefault`, `CharacterPicker`, 닉네임 필드 UI 완성. **저장 버튼이 `toast.show(COMING_SOON)`** 만 호출. `GroupProfileState.from(me)`/`canSave`/`preview`(`GroupProfileState.kt`). `DataStore.setMyGroupProfile(profile: Profile?, groupID)` 는 로컬 갱신만.
  - `GroupInfoScreen`(`feature/groups/GroupInfoScreen.kt`): 초대 코드 카드(복사·"초대 링크 공유하기"), 멤버 목록의 "내 프로필 편집" 버튼이 `toast.show(COMING_SOON)`(라우트 `onProfile` 은 받고 있으나 사용 안 함 — 확인), "주제 도착 시간" 행 `toast.show(COMING_SOON)`, "그룹 나가기": `group.amIOwner && members.size > 1` 이면 "준비 중" 토스트, 아니면 `HAlert` 후 `groups.leave(id)`. `leaveMessage(group)`(`GroupFormat.kt`)가 문구 담당 — 기존 문구 "가장 먼저 들어온 멤버에게 넘어가요" 는 새 규칙과 다름.
  - `GroupRepository.leave(groupId, successorUserId: Long? = null)` 는 이미 쿼리 파라미터를 지원. `GroupRepository.detail/loadAll` 이 `GroupDetailDto.toModel`·`GroupMemberDto.toMember`·`GroupMemberProfileDto.toMember` 로 변환 — **`profileImageUrl` 무시, `AvatarContent.Character` 만 생성**.
  - `GroupDetail(…, topicTime: LocalTime = 09:00, …, startDate)`; `GroupDetailDto.missionSlots`(`slotNo, publishTime, deadlineTime, custom`)에서 `topicTime` 계산. `DataStore.applyServerGroup(detail)` 로 갱신.
  - `ChangePasswordScreen`(`feature/mypage/ChangePasswordScreen.kt`): 입력/검증 UI 완성, "변경하기" 클릭 시 `// TODO` 후 `onBack()`.
  - 오류 문구: `core/net/ApiMessages.kt`(`SUCCESSOR_REQUIRED` 문구 "지금은 준비 중이에요" → 수정 필요), `toUserMessage()`.
  - 토스트: `HToastHost`, `rememberHToastState()`; 경고창 `HAlert`; 시간 선택 컴포넌트는 `WheelPicker`(`designsystem/components/WheelPicker.kt`)가 있다(주제 도착 시각 선택에 재사용 가능).
  - 토큰: `TokenStore.save(access, refresh)`(`KeystoreTokenStore`), `AuthRepository` 가 보유. 비밀번호 변경은 `AuthRepository.changePassword` 로 두면 `tokens` 접근이 자연스럽다.

## Root Cause / Key Implementation Points

1. **공용 아바타 변환**: A2 가 만든 `ApiClient.avatarOf(characterKey, profileImageUrl)`(`core/net/AvatarMapping.kt`)를 사용한다. A2 에서 `GroupRepository`·`SubmissionRepository` 적용까지 끝냈다면 이 단계는 확인만 한다(없으면 여기서 적용). 서버가 그룹 내 프로필을 이미 반영해 내려주므로 앱은 변환만 한다.
2. **그룹 내 프로필 저장**(`GroupRepository.saveGroupProfile(groupId, avatar, nickname)` / `clearGroupProfile(groupId)`):
   - 토글 ON(기본 프로필 사용): `DELETE …/profile`(이미 기본이면 호출 생략 또는 멱등 204).
   - 토글 OFF + 캐릭터: 캐릭터 id 를 `GET /characters` 로 매핑(`svgKey`) → `PUT …/profile`.
   - 토글 OFF + 사진(로컬 uri): 이미지 바이트(A2 와 같은 처리·≤10MB) → `PUT …/profile-image`(`file` + `nickname`).
   - 성공 후 `groups.detail(id, myUserId)` 재조회 → `store.applyServerGroup(detail)` (표시 프로필은 서버 값). 실패는 `toUserMessage()` 토스트, 저장 버튼 중복 방지.
   - `GroupProfileState.from(me)` 가 서버의 `hasGroupProfile`(없으면 기존 `groupProfile != null`)로 `useDefault` 를 정하도록 `GroupMember` 에 `groupProfile` 채우기: `GroupMemberDto.hasGroupProfile` 가 true 면 `groupProfile = displayed 프로필`, `defaultProfile` 은 **내 계정 프로필**(`session.account.profile`)을 사용(다른 멤버의 기본 프로필은 서버가 내려주지 않으므로 모름 — 내 것만 필요). DTO 에 `hasGroupProfile: Boolean = false` 추가.
3. **후임자 선택(임시 UI)**: 새 컴포저블 `SuccessorPickerScreen`(또는 `HAlert` 대신 전체 화면/바텀시트, `feature/groups/SuccessorPickerScreen.kt`) — 내(그룹장)를 제외한 멤버를 `HAvatar`+닉네임 행으로 나열, 하나 선택(라디오 표시) → "넘기고 나가기" 버튼(위험 색) → 확인 `HAlert`("OO님에게 그룹장을 넘기고 나갈까요?") → `groups.leave(id, successorId)` → 성공 시 `store.leave(id); onLeft()`. 라우트는 `MainRoute` 에 `SuccessorPicker(groupId)` 추가(`app/MainNavigation.kt`). `GroupInfoScreen` 의 나가기 분기: 그룹장+멤버>1 → 선택 화면으로 이동, 그룹장+혼자 → `leaveMessage` 를 "나가면 이 그룹이 삭제돼요" 로, 일반 멤버 → 기존. 서버 오류 `INVALID_SUCCESSOR`(그 사이 후임자가 나감)·`SUCCESSOR_REQUIRED` → 멤버 목록 새로고침 후 안내.
4. **초대 코드 재발급**: `GroupRepository.reissueInviteCode(groupId): InviteCodeResult(code, url)`. `GroupInfoScreen` 초대 카드에 그룹장에게만 "코드 다시 만들기" 텍스트 버튼 → 확인 `HAlert`("이전 코드는 바로 쓸 수 없게 돼요") → 호출 → `store.applyServerGroup(detail 갱신)`로 코드 즉시 갱신. 429·403·네트워크 오류 문구. `shareInvite` 는 서버가 준 코드를 그대로 사용(이미 `group.inviteCode`). 입력 화면(03 코드 입력/미리보기)에서 `TOO_MANY_REQUESTS` → "잠시 후 다시 시도해 주세요" 문구를 `ApiMessages` 에 추가.
5. **주제 도착 시각**: "주제 도착 시간" 행을 그룹장에게만 탭 가능하게(`showsChevron = group.amIOwner`), 탭 → `WheelPicker` 기반 시각 선택(임시 UI: 시/분 휠 + 확인) → `GroupRepository.updateTopicTime(groupId, LocalTime)` = `PATCH /settings` `{missionSlots:[{slotNo:1, publishTime:"HH:mm"}]}` → 응답/재조회로 `topicTime` 갱신. 변경 안내 문구("오늘 미션이 이미 열렸다면 내일부터 적용돼요"). 그룹장이 아니면 탭 시 "그룹장만 바꿀 수 있어요" 토스트. 하루 미션이 여러 개(B7 이후)면 슬롯 1만 다룬다(다중 UI 는 A6).
6. **비밀번호 변경**: `AuthRepository.changePassword(current, new)` → `PATCH /api/me/password` → 응답 토큰 쌍을 `tokens.save`. 화면: 진행 중 비활성, 성공 토스트 "비밀번호를 바꿨어요" 후 `onBack()`; 400 `WRONG_CURRENT_PASSWORD` → "현재 비밀번호가 맞지 않아요"(입력 필드 에러), `VALIDATION_ERROR` → 새 비밀번호 규칙 문구. **401 로 처리하지 않는다**. 간편 로그인 계정 분기(`canChangePassword`)는 서버가 이메일 계정만 있으므로 항상 활성 유지(기존 결정).
7. **임시 이미지**: 이번 계획에서 새 이미지 필요 없음(후임자 선택은 기존 컴포넌트 조합). 새 아이콘이 필요하면 `HIcon` 의 기존 항목 사용.

## Files / Functions to Change

| 파일 | 변경 |
|---|---|
| `core/net/GroupApi.kt` | `GroupMemberDto.hasGroupProfile`, `InviteCodeDto`, 그룹 프로필 요청 DTO, 설정 변경 DTO |
| `core/net/GroupRepository.kt` | `avatarOf` 적용, `saveGroupProfile`, `clearGroupProfile`, `reissueInviteCode`, `updateTopicTime`, `hasGroupProfile` 매핑 |
| `core/net/SubmissionRepository.kt` (+7A 댓글 작성자 변환) | `avatarOf` 적용 |
| `core/net/AuthRepository.kt`, `AuthApi.kt` | `changePassword`(토큰 저장) |
| `core/net/ApiMessages.kt` | `SUCCESSOR_REQUIRED`/`INVALID_SUCCESSOR`/`NOT_GROUP_OWNER`/`TOO_MANY_REQUESTS`/`WRONG_CURRENT_PASSWORD` 문구(기존 "준비 중" 문구 교체) |
| `feature/groups/GroupProfileScreen.kt`, `GroupProfileState.kt` | 저장 연결, 로딩 상태, `hasGroupProfile` 반영 |
| `feature/groups/GroupInfoScreen.kt`, `GroupFormat.kt`(`leaveMessage`) | 나가기 분기, 재발급 버튼, 주제 시각 변경, "내 프로필 편집" → 그룹 프로필 화면 이동 |
| `feature/groups/SuccessorPickerScreen.kt` (신규), `app/MainNavigation.kt` | 임시 선택 화면·라우트 |
| `feature/mypage/ChangePasswordScreen.kt` | 서버 연결 |
| `core/data/DataStore.kt` | `setMyGroupProfile` 의 로컬 낙관 갱신을 서버 재조회 기반으로 바꾸거나 제거(`applyServerGroup` 사용) |
| 테스트 | `GroupRepositoryTest`(MockWebServer): 프로필 PUT/PUT image/DELETE 경로·본문(multipart `nickname` 파트), `hasGroupProfile` 매핑, 재발급, 설정 PATCH 본문, `leave` 쿼리; `AuthRepositoryTest`: `changePassword`(토큰 저장 확인·400 `WRONG_CURRENT_PASSWORD` 시 토큰 유지); `GroupFormatTest`(`leaveMessage` 세 경우); `GroupProfileStateTest`; `ApiMessagesTest` |

## Implementation Steps

1. `avatarOf` 적용(변환만; 기존 테스트 유지). 2. 비밀번호 변경(독립적이라 쉬움). 3. 그룹 내 프로필 저장. 4. 재발급·주제 시각. 5. 후임자 선택 화면·나가기 분기. 6. 문구 정리. 7. `android\gradlew.bat :app:testDebugUnitTest --offline`. 8. 서버 연동 확인은 사용자(그룹 프로필 설정→다른 계정에서 피드 확인, 그룹장 나가기, 코드 재발급 후 이전 코드 가입 실패, 비밀번호 변경 후 다른 기기 로그아웃).

## Constraints

- `android/` 만. 그룹 알림 토글·푸시·간편 로그인 건드리지 않기(`알림 준비 중` 유지). 서버 성공 후에만 로컬 반영(낙관 반영 금지), 최종 상태는 서버 재조회. 임시 UI 는 기존 디자인 시스템 컴포넌트만 사용(새 라이브러리 금지). 커밋/푸시 금지.

## Edge Cases

- 그룹 프로필: 닉네임만 바꾸고 캐릭터 그대로; 캐릭터→사진→캐릭터 전환; 사진 413/415; 토글 ON 저장 시 서버 프로필 해제; 이미 해제 상태에서 ON 저장(멱등); 네트워크 실패 시 토글 상태 유지.
- 다른 그룹에는 영향이 없다는 안내 문구 유지; 같은 사용자가 여러 그룹에서 다른 프로필 → 그룹별 화면에서 각각 정확.
- 후임자 선택: 멤버가 1명 더 있는 그룹(2인 COUPLE) 에서도 선택 화면(후보 1명) 동작; 선택 중 후임자가 나감 → `INVALID_SUCCESSOR` → 목록 새로고침; 두 번 탭 방지; 나가기 후 홈으로 이동·그룹 목록 갱신.
- 재발급: 그룹장 아님(403) → 안내; 연타 방지; 429 문구; 재발급 직후 공유 버튼이 새 코드를 쓴다.
- 주제 시각: 오늘 이미 열린 경우 안내 문구, 비그룹장 탭, 서버 400 시각 형식(휠이므로 발생하지 않음), 네트워크 실패.
- 비밀번호: 현재 틀림(400 → 로그아웃되지 않음), 규칙 위반 새 비밀번호, 새 토큰 저장 직후 다음 요청이 새 토큰으로 나감, 서버가 다른 기기를 무효화해도 이 기기는 유지.

## Regression Risks

- `GroupMember`/`GroupDetail` 모델 변경과 `avatarOf` 도입이 홈·그룹 정보·결과·05 화면의 아바타 렌더링 전반에 영향 → 사진 URL(원격) 렌더 확인(`HAvatar`).
- `GroupInfoScreen` 나가기 분기 재구성 중 기존 "일반 멤버 나가기" 동작 회귀 금지(기존 테스트/동작 유지).
- `MainRoute` 추가 시 직렬화(`@Serializable`) 누락 → 내비게이션 크래시.
- `ApiMessages` 기존 문구를 참조하는 테스트(`SUCCESSOR_REQUIRED` 문구) 수정 필요.
- 비밀번호 변경 후 토큰 교체 중 진행 중인 다른 요청이 401 을 받을 수 있음 — `ApiClient` 의 refresh/expire 로직이 새 토큰을 읽도록(`tokens.accessToken` 은 매 요청 읽음) 확인.

## Tests Required

위 표의 테스트 + `GroupInfo` 분기 로직을 순수 함수로 분리(`leaveFlowFor(group): LeaveFlow { MEMBER, OWNER_ALONE, OWNER_WITH_MEMBERS }`)해 단위 테스트. 화면은 컴파일 + 수동 확인.

## Validation Steps

`android\gradlew.bat :app:testDebugUnitTest --offline`(JDK 21) 통과 기록. `rg "COMING_SOON" android/app/src/main` 로 남은 "준비 중"이 **알림 관련만**인지 확인(그룹 알림 행, 간편 로그인, 비밀번호 찾기). 서버 연동은 사용자 확인(미검증이면 명시).

## Potential Merge Conflicts

`GroupInfoScreen.kt`, `GroupRepository.kt`, `GroupApi.kt`, `DataStore.kt`, `AuthRepository.kt`/`AuthApi.kt`/`ApiClient.kt`(A2 선행), `MainNavigation.kt`, `ApiMessages.kt`, `SubmissionRepository.kt`(A4 와 겹침).
