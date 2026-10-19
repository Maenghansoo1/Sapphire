# 백엔드 ↔ Android·iOS 계약 결정 기록

점검 결과(현황)는 `docs/BACKEND/CONTRACT_AUDIT.md`에 있다. 이 문서는 사용자 결정을 받은 항목만 API 단위로 기록한다.
**아직 코드와 기존 설계 문서(`API_DESIGN.md`, `MISSIONS_API.md`, `BACKEND_DB.md`, `PROJECT_OVERVIEW.md`)에는 반영하지 않았다.** 후속 작업에서 API별로 "수정 대상"을 따라 반영한다. 반영이 끝나면 해당 절의 상태를 "반영됨"으로 바꾼다.

- 전체 원칙 (2026-10-12): **영역별로 결정한다.** 앱 기획과 백엔드 설계 중 한쪽을 일괄 기준으로 삼지 않는다.
- 공통 기술 결정은 §0에 있다.
- **후속 작업 권장 순서** (뒤 단계가 앞 단계의 계약을 전제로 함): §0 공통(시간대·오류 처리) → §1 Auth + `GET /me` → §2 Group(초대 코드·정원·나가기·그룹 내 프로필) → §3 미션 생성·현재 미션 → §4 Pokes → §5 Me(프로필·탈퇴) → §6 제출·피드 → §7 반응·댓글 → §8 알림 → §9 달력. 앱 쪽 서버 연결(네트워크 계층·토큰 저장소)은 §1과 함께 시작한다.
- **병행 작업 주의**: 그룹 계약(§2)과 미션 계약(§3)은 같은 `GroupService`·`GroupMapper`·`MissionMapper`를 건드리므로 동시에 진행하지 말고 순서대로 한다. Android 서버 연결은 9단계 푸시 코드(`core/push/`, `MainNavigation.kt`)를 그대로 재사용한다(§8).

---

## 0. 공통 — 상태: 결정 완료 (반영 전)

정책 선택이 없는 기술 결정이다. 근거는 `CONTRACT_AUDIT.md` §2.

| 항목 | 결정 | 수정 대상 |
|---|---|---|
| ID | 서버 id(`Long`, JSON 숫자)가 기준. 앱의 `UUID` id는 서버 값으로 바꾼다 | Android·iOS 모델 |
| 시간대 | 서버 JVM과 DB 세션 시간대를 **Asia/Seoul**로 고정한다. 날짜 경계("오늘", `mission_date`)는 모두 Asia/Seoul 기준 | 백엔드 설정(JVM `-Duser.timezone`, JDBC·DB 세션 time_zone, Jackson time-zone) |
| 시각 표기 | 응답의 시각은 **오프셋을 붙인 ISO-8601**(`2026-09-28T09:00:00+09:00`)로 내려준다. 날짜는 `yyyy-MM-dd`, 슬롯 시각은 `HH:mm`(기존) | 백엔드 DTO의 `LocalDateTime` → `OffsetDateTime`. `API_DESIGN.md` §0 "시각 표현" |
| 처리 안 된 요청 오류 | 없는 경로 404 `NOT_FOUND`, 경로·쿼리 타입 오류·필수 파라미터 누락 400 `VALIDATION_ERROR`, 허용 안 된 메서드 405 `METHOD_NOT_ALLOWED`, 업로드 한도 초과 413 `FILE_TOO_LARGE`. 지금은 모두 500 | `GlobalExceptionHandler`, `CommonErrorCode` |
| 401 처리 (앱) | `TOKEN_EXPIRED` → refresh 후 재시도, `INVALID_TOKEN`·refresh 실패 → 로그아웃 (§1) | Android·iOS 네트워크 계층 |
| 프로필 객체 | 사람을 보여 주는 모든 응답은 `{ userId, nickname, characterKey, profileImageUrl }`를 쓰고, 그룹 안에서는 그룹 내 프로필을 적용한다 (§2-2) | 백엔드 응답 전반 |
| 문서 상태 표기 | `API_DESIGN.md`·`MISSIONS_API.md`의 "아직 구현되지 않았다", `PROJECT_OVERVIEW.md` §10의 상태 표를 코드에 맞춘다 | 설계 문서 |

---

## 1. Auth — 상태: 백엔드 반영됨 (비밀번호 변경 포함)

### 1-1. 결정

| 항목 | 결정 | 이유 |
|---|---|---|
| 로그인 식별자 | **이메일.** `loginId`를 없애고 이메일을 필수·유일 값으로 쓴다 | 앱 화면(A1·A2)이 이메일 기준이고 아이디 입력 칸이 없음. 나중에 비밀번호 찾기를 넣으려면 어차피 이메일이 유일해야 함 |
| 간편 로그인 (Apple·카카오·Google) | **MVP에서 구현하지 않는다. 버튼과 17-2 "로그인 연결"은 화면에 남기고, 누르면 "준비 중" 안내를 띄운다** | 사용자 결정. 서버에 소셜 로그인 API를 만들지 않는다 |
| 비밀번호 변경 | **백엔드 반영됨** — `PATCH /api/me/password` | 메일 인프라 없이 구현 가능 |
| 비밀번호 찾기 (메일) | **MVP 제외.** A1 "비밀번호 찾기" 버튼은 남기고 누르면 "준비 중" 안내 (재설정 메일 화면으로 이동하지 않음) | 메일 발송 수단이 필요함. 간편 로그인과 같은 처리로 통일 (사용자 결정) |
| 약관 동의 | **서버에 동의 시각을 저장한다.** `register`가 두 필수 동의를 받고, 하나라도 `true`가 아니면 400 | 동의 근거를 남김. 나중에 약관 버전 관리로 확장 가능 |
| 가입 흐름 | A2(이메일·비밀번호·약관) → A3(닉네임·캐릭터/사진) 화면은 그대로. A3 "시작하기"에서 `register` → `login` → 캐릭터를 골랐으면 `PATCH /me/character`, 사진이면 `PUT /me/profile-image`(구현 후) 순서로 호출 | 화면 흐름을 바꾸지 않고 백엔드 계약도 단순하게 유지 (`register`는 토큰을 주지 않는다) |
| 입력 규칙 | 서버가 앱과 같은 규칙을 검증한다: 이메일 형식, 비밀번호 8~100자 + 영문·숫자 포함, 닉네임 앞뒤 공백 제거 후 2~10자 | 다른 클라이언트로 규칙 밖의 값이 들어오지 않게 |
| 내 정보 | `GET /api/me` 추가 | 로그인 응답에 토큰만 있어 앱이 닉네임·이메일·가입일·프로필을 알 수 없음 |
| 토큰 처리 (앱) | 401 `TOKEN_EXPIRED` → `/auth/refresh` 후 원래 요청 재시도. `INVALID_TOKEN`이거나 refresh가 실패하면 로그아웃. 토큰은 iOS Keychain / Android Keystore 기반 저장소에 보관 | 현재 서버 계약으로 충분. 앱은 지금 메모리에만 세션을 두어 앱을 끄면 로그아웃됨 |
| 로그아웃 | 서버 API 없음 (기존 결정 유지). 앱이 저장한 토큰을 지운다 | — |

### 1-2. 최종 계약

공통 규칙(`/api`, 에러 형식, camelCase)은 `API_DESIGN.md` §0을 따른다. 이메일은 앞뒤 공백을 제거하고 소문자로 바꿔 저장·비교한다.

```json
// POST /api/auth/register — 인증 불필요
// Request
{ "email": "sky@email.com", "password": "abcd1234", "nickname": "하늘",
  "agreeTerms": true, "agreePrivacy": true }
// Response 201
{ "id": 1, "email": "sky@email.com", "nickname": "하늘" }
```
- 검증 실패: 400 `VALIDATION_ERROR` (이메일 형식, 비밀번호 8~100자 + 영문·숫자, 닉네임 trim 후 2~10자, `agreeTerms`·`agreePrivacy`가 `true`가 아님)
- 서버는 가입 시각을 `users.terms_agreed_at`, `users.privacy_agreed_at`에 저장한다. 응답과 `GET /me`에는 내려주지 않는다.
- 이미 가입된 이메일: 409 `EMAIL_TAKEN` (기존 `LOGIN_ID_TAKEN` 대체)

```json
// POST /api/auth/login — 인증 불필요
// Request
{ "email": "sky@email.com", "password": "abcd1234" }
// Response 200
{ "accessToken": "...", "refreshToken": "..." }
```
- 401 `INVALID_CREDENTIALS` ("이메일 또는 비밀번호가 올바르지 않습니다")

```json
// POST /api/auth/refresh — 변경 없음
{ "refreshToken": "..." }  →  { "accessToken": "...", "refreshToken": "..." }
```
- 401 `INVALID_TOKEN`, `TOKEN_EXPIRED`

```json
// GET /api/me — 인증 필요
// Response 200
{ "id": 1, "email": "sky@email.com", "nickname": "하늘",
  "characterKey": "mongsil", "profileImageUrl": null,
  "createdAt": "2026-06-02T10:00:00+09:00" }
```
- `characterKey`: `users.character_id`가 NULL이면 `null` (앱은 `mongsil`로 표시). 기존 `PATCH /me/character` 응답의 `svgKey`와 같은 값이다.
- `profileImageUrl`: 프로필 사진 구현 전에는 항상 `null`.
- 시각 형식은 §0 공통 규칙(오프셋 포함 ISO-8601)을 따른다.
- 앱의 `MyAccount.signUpProvider`·`linkedProviders`는 서버에서 내려주지 않는다. 간편 로그인이 없으므로 앱은 항상 이메일 가입으로 본다. 17-2에서 "비밀번호 변경"은 모든 사용자에게 활성이다.

```json
// PATCH /api/me/password — 인증 필요
// Request
{ "currentPassword": "abcd1234", "newPassword": "efgh5678" }
// Response 200
{ "accessToken": "...", "refreshToken": "..." }
```
- 400 `VALIDATION_ERROR` (새 비밀번호 규칙), 401 `INVALID_CREDENTIALS` 대신 **400 `WRONG_CURRENT_PASSWORD`** — 401은 앱이 토큰 문제로 보고 로그아웃할 수 있으므로 쓰지 않는다.
- 비밀번호 변경 시 이전 액세스·리프레시 토큰은 모두 무효다(`INVALID_TOKEN`). 요청 기기는 응답의 새 토큰을 저장해 계속 사용한다. 현재 비밀번호가 틀리면 토큰과 비밀번호는 그대로 유지된다.

### 1-3. 수정 대상

| 대상 | 내용 |
|---|---|
| 백엔드 | `RegisterRequest`/`LoginRequest`/`RegisterResponse`의 `loginId` → `email`. `AuthErrorCode.LOGIN_ID_TAKEN` → `EMAIL_TAKEN`, 메시지 수정. 비밀번호·닉네임 검증 강화. `UserMapper`를 이메일 기준으로. 새 마이그레이션: `users.email` NOT NULL + UNIQUE, `login_id` 제거, `terms_agreed_at`·`privacy_agreed_at` DATETIME 추가. `GET /me`, `PATCH /me/password` 추가 (새 `user`/`me` 기능 패키지 또는 `auth`에 둘지 구현 계획에서 정함). `PasswordErrorCode` 등 새 오류 코드 |
| Android | 네트워크 계층·토큰 저장소 신설(서버 연결 단계). A1 로그인, A3 가입 완료 시 API 호출. 간편 로그인 버튼 3개, 17-2 연결/해제, A1 "비밀번호 찾기"를 "준비 중" 안내로 (`PasswordResetScreen`은 진입하지 않음). A3 가입 요청에 A2 약관 동의 값을 넘김. 17-2 비밀번호 변경 활성 조건을 "항상"으로. `accountForLogin`(목업 계정 생성) 제거 |
| iOS | Android와 같음 (`LoginView.logIn`, `LoginView.links`의 비밀번호 찾기, `SignupAccountModel` 동의 값 전달, `SignupProfileView`, `UserInfoView`, `ChangePasswordView`) |
| 설계 문서 | `API_DESIGN.md` §1 Auth 계약 교체, `GET /me`·`PATCH /me/password` 추가. `BACKEND_DB.md` users 테이블. `PROJECT_OVERVIEW.md` §8 "아이디/비밀번호 자체 가입" → 이메일. Postman `HAWE_AUTH` 컬렉션 |

### 1-4. 미결

- 약관 버전 관리 (약관이 바뀌었을 때 재동의) — MVP 이후.
- 기존 개발 DB 처리 — 로컬 개발 DB를 초기화하고 V1 마이그레이션을 재작성하여 해결.
- 비밀번호 변경은 `users.token_version`을 증가시켜 이전 토큰을 무효화한다. 탈퇴는 사용자 행 삭제로 기존 토큰을 거절한다.

---

## 2. Group — 상태: 백엔드 반영됨 (그룹별 알림은 후속 단계)

### 2-1. 결정

| 항목 | 결정 | 이유 |
|---|---|---|
| 관계 코드 | 서버 코드 `COUPLE/FAMILY/FRIEND/RIVAL/CASUAL`을 쓴다. 앱 `lover/friend/family/rival/soso`는 앱 표시용으로만 남기고 서버 값과 1:1 대응 (`lover`↔`COUPLE`, `soso`↔`CASUAL`) | 앱 주석에서도 임시 값이라고 밝힘 |
| 초대 방식 | **그룹마다 6자리 초대 코드**를 DB에 저장한다. 만료 없음. 그룹장이 재발급하면 이전 코드는 즉시 무효. 기존 JWT 초대 토큰(`hawe://join?token=`)은 폐지 | 앱 화면(03 코드 입력·미리보기, 16 코드 카드) 그대로. 정원이 작아(최대 6명) 유출 위험이 제한적이고, 재발급으로 막을 수 있음 (사용자 결정) |
| 초대 권한 | **모든 멤버**가 코드를 보고 복사·공유할 수 있다. **재발급은 그룹장만** | 친한 소규모 그룹에서 멤버가 직접 데려오는 흐름 (사용자 결정). 무효화는 그룹 전체에 영향이 있어 그룹장 권한 |
| 참여 전 미리보기 | 코드로 그룹 이름·관계·인원·멤버 아바타를 미리 본 뒤 참여한다 | 앱 03 화면 흐름 |
| 정원 | **서버가 보장한다.** COUPLE 2명, 그 외 6명. 가득 차면 가입 409 `GROUP_FULL` | 사용자 결정. 앱 규칙(`Relationship.maxMembers`)과 같음 |
| 연인 "우리의 1일" | **서버에 저장한다** (`startDate`, COUPLE 그룹만) | 처음에는 저장하지 않기로 했다가, 앱 기능을 유지하기 위해 서버 저장으로 바꿈 (사용자 결정). 기기에만 저장하면 두 사람의 값이 달라짐 |
| 그룹장 나가기 | **그룹장이 후임자를 골라서 나간다.** 다른 멤버가 있으면 후임자 지정이 필수 | 사용자 결정. 앱의 "가장 먼저 들어온 멤버에게 자동 이전" 문구·동작은 바뀐다 |
| 마지막 멤버가 나가기 | **그룹을 삭제한다.** 초대 코드도 무효 | 혼자 있는 그룹이라 다른 사람에게 영향이 없음 (사용자 결정) |
| 탈퇴하는 그룹장 | 다른 멤버가 있는 그룹의 그룹장이면 먼저 후임자를 골라 나가야 탈퇴할 수 있다 (§5) | 사용자 결정. 나가기 규칙과 일관 |
| 그룹 내 프로필 | **서버에 둔다 — 닉네임 + 캐릭터 + 사진 모두.** 그룹 내 프로필이 있으면 그 그룹의 모든 응답(멤버, 피드, 댓글 등)에서 그 프로필로 보이고, 없으면 기본 프로필 | 앱 16-1 기능 그대로 (사용자 결정) |
| 그룹 이름 | 앞뒤 공백 제거 후 1~16자. 설정 변경에서도 빈 이름 거부 | 앱 입력 제한(16자)과 맞춤. 현재 `PATCH settings`는 빈 이름을 저장하는 버그가 있음 |
| 그룹 목록 정렬 | 내가 참여한 순서(`group_members.joined_at` 오름차순) | 앱이 "참여 순"으로 그리고, 푸시 대상이 없을 때 "첫 그룹"을 쓰는 기준과 같음 |

### 2-2. 최종 계약

모든 그룹 API는 인증 필요. 아래에서 "프로필 객체"는 다음 모양이며, 그룹 안에서는 그룹 내 프로필이 있으면 그것을, 없으면 기본 프로필을 담는다.
```json
{ "userId": 3, "nickname": "서연", "characterKey": "mulbangul", "profileImageUrl": null }
```
- `profileImageUrl`이 있으면 사진, 없으면 `characterKey`(NULL이면 앱에서 `mongsil`)로 표시한다.
- 요청에서 캐릭터는 기존 `PATCH /me/character`와 같이 **`characterId`(Long)** 로 보내고, 응답은 **`characterKey`** 로 내려준다. 앱은 `GET /characters`(인증 불필요)로 키↔id를 대응한다.

#### GET /api/groups — 내 그룹 목록
```json
{ "content": [
  { "id": 1, "name": "컴공 동기", "relationType": "FRIEND",
    "memberCount": 5, "maxMembers": 6,
    "members": [ { "userId": 1, "nickname": "과대표 하늘", "characterKey": "gomdol", "profileImageUrl": null } ],
    "startDate": null,
    "submittedToday": false,
    "todayMission": { "missionId": 10, "media": "PHOTO", "content": "지금 창밖으로 보이는 풍경",
                      "submittedCount": 3, "submittedByMe": false },
    "todayMissions": [ { "missionId": 10, "media": "PHOTO", "content": "지금 창밖으로 보이는 풍경",
                         "submittedCount": 3, "submittedByMe": false } ] }
], "page": 0, "size": 20, "totalPages": 1, "totalElements": 3 }
```
- 정렬: 내가 참여한 순서. `members`는 그룹 안 표시 프로필, 참여 순.
- `todayMission`: 지금 열려 있는 미션(§3 "현재 미션") 요약. 없으면 `null`. 홈 카드의 유형·주제·제출 인원·내 제출 여부에 쓴다. 하루 여러 개면 **첫 슬롯**(현재 미션 중 가장 작은 `slotNo`)이다.
- `todayMissions` (B7 추가): 현재 미션 요약 **전부**, `slotNo` 오름차순. 없으면 빈 배열. 하루 1개면 `[todayMission]`과 같다. 앱의 여러 미션 홈 카드는 A6.
- `submittedToday`는 `todayMission.submittedByMe`와 같은 값(첫 슬롯 기준)이 되므로 반영 시 제거를 검토한다.

#### POST /api/groups — 그룹 만들기
```json
// Request
{ "name": "우리 둘", "relationType": "COUPLE", "startDate": "2026-06-02" }
// Response 201 — GET /api/groups/{id} 와 같은 모양
```
- `name`: trim 후 1~16자. `relationType` 생략 시 `CASUAL`.
- `startDate`: COUPLE일 때만 받는다(선택). 다른 관계에서 보내면 400 `VALIDATION_ERROR`. 미래 날짜(Asia/Seoul 기준)는 400.
- `dailyMissionCount`, `missionSlots`는 기존대로 선택 필드다. 하루 미션 수는 1~5(B7, §3). 앱은 보내지 않는다.
- 생성 직후 오늘 미션을 연다 (§3 "첫 미션").

#### GET /api/groups/{groupId} — 그룹 상세 (멤버만)
```json
{ "id": 1, "name": "컴공 동기", "ownerId": 1, "relationType": "FRIEND",
  "createdAt": "2026-06-02T10:00:00+09:00",
  "memberCount": 5, "maxMembers": 6,
  "inviteCode": "K7Q2PX",
  "inviteUrl": "https://hawe.app/invite/K7Q2PX",
  "startDate": null,
  "dailyMissionCount": 1,
  "missionSlots": [ { "slotNo": 1, "publishTime": "09:00", "deadlineTime": "23:00", "custom": false } ],
  "missionCount": 142,
  "myNotify": true }
```
- 에러: 404 `GROUP_NOT_FOUND`, 403 `NOT_GROUP_MEMBER`.
- `myNotify`: 이 그룹 알림을 내가 켰는지 (§8 그룹별 알림).
- `missionSlots[0].publishTime`이 앱의 "주제 도착 시각"이다. `missionCount`는 지금까지 이 그룹에 열린 미션 수(16 화면 "미션 142개").

#### PATCH /api/groups/{groupId}/settings — 그룹 설정 (그룹장만)
- 부분 수정. `name`(trim 후 1~16자, 공백 거부), `relationType`, `startDate`, `dailyMissionCount`, `missionSlots`.
- 관계를 바꿀 때 현재 인원이 새 정원보다 많으면 409 `GROUP_FULL`. COUPLE이 아닌 관계로 바꾸면 `startDate`는 `null`이 된다.
- 앱의 주제 도착 시각 변경: `{ "missionSlots": [ { "slotNo": 1, "publishTime": "21:30" } ] }` (전체 교체 규칙이라 마감 시각은 기본값으로 돌아간다 — 앱에 마감 설정 UI가 없으므로 문제없음). 적용 시점은 `MISSIONS_API.md` §2 규칙대로: 오늘 미션이 이미 열렸으면 내일부터.
- 에러: 403 `NOT_GROUP_OWNER`, 400 `VALIDATION_ERROR`.

#### GET /api/groups/{groupId}/members — 멤버 목록 (멤버만)
```json
{ "content": [
  { "userId": 1, "nickname": "과대표 하늘", "characterKey": "gomdol", "profileImageUrl": null,
    "hasGroupProfile": true, "role": "owner",
    "submissionCountThisMonth": 12, "stampedToday": true }
], "page": 0, "size": 20, "totalPages": 1, "totalElements": 5 }
```
- 정렬: 참여 순 (기존과 같음). `nickname`·`characterKey`·`profileImageUrl`은 그룹 안 표시 프로필.
- `hasGroupProfile`: 그룹 내 프로필을 쓰는지. 16-1에서 "기본 프로필 사용" 상태를 보여 줄 때 쓴다.
- "나"는 `GET /me`의 `id`와 `userId`를 비교해 판별한다.
- `submissionCountThisMonth`·`stampedToday`는 앱이 쓰지 않는다. 유지·삭제는 반영 시 정한다 (§9-4). 뜻(B7): `submissionCountThisMonth` = 이번 달 제출한 **날 수**, `stampedToday` = 현재 미션 중 하나라도 제출.

#### 초대 코드
```json
// GET /api/invites/{code} — 참여 전 미리보기 (로그인 필요)
// Response 200
{ "groupId": 1, "name": "컴공 동기", "relationType": "FRIEND",
  "memberCount": 5, "maxMembers": 6,
  "members": [ { "userId": 1, "nickname": "과대표 하늘", "characterKey": "gomdol", "profileImageUrl": null } ] }
```
- 에러: 404 `INVITE_NOT_FOUND`, 409 `ALREADY_MEMBER`, 409 `GROUP_FULL` — 앱의 `notFound`/`alreadyMember`/`full`과 1:1.

```json
// POST /api/groups/join — 참여
// Request
{ "code": "K7Q2PX" }
// Response 200
{ "groupId": 1, "name": "컴공 동기" }
```
- 서버는 코드를 대문자로 바꿔 비교한다. 에러는 미리보기와 같다 (동시 가입으로 정원이 찼으면 `GROUP_FULL`).

```json
// POST /api/groups/{groupId}/invite-code — 재발급 (그룹장만, 백엔드 반영됨)
// Response 200
{ "inviteCode": "M8R3TQ", "inviteUrl": "https://hawe.app/invite/M8R3TQ" }
```
- 이전 코드는 즉시 무효. 에러: 403 `NOT_GROUP_OWNER`.
- 코드 형식: 6자리, 문자 집합 `ABCDEFGHJKLMNPQRSTUVWXYZ23456789`(혼동 문자 0·O·1·I 제외), 전체 그룹에서 유일. 그룹 생성 시 서버가 만든다.
- 기존 `POST /api/groups/{groupId}/invite`(JWT 링크 발급)는 삭제.

#### DELETE /api/groups/{groupId}/members/me — 나가기
- 일반 멤버: 204.
- 그룹장 + 다른 멤버 있음: 쿼리 `successorUserId`가 필수. 지정한 멤버에게 그룹장을 넘기고 나간다(한 트랜잭션). 204.
  - 없으면 409 `SUCCESSOR_REQUIRED`, 그 그룹 멤버가 아니거나 나 자신이면 400 `INVALID_SUCCESSOR`.
- 그룹장 혼자: 그룹을 삭제하고 204.
- 내가 올린 작품·댓글은 그룹에 남는다 (앱 확인 문구와 같음). 기존 `OWNER_CANNOT_LEAVE`는 폐지.

#### 그룹 내 프로필 (16-1) — 백엔드 반영됨
```json
// PUT /api/groups/{groupId}/members/me/profile — 그룹 내 프로필 설정·교체 (캐릭터)
{ "nickname": "과대표 하늘", "characterId": 3 }
// Response 200 — 프로필 객체
```
```text
PUT    /api/groups/{groupId}/members/me/profile-image   multipart, 파트 file + nickname — 그룹 내 프로필을 사진으로
DELETE /api/groups/{groupId}/members/me/profile         그룹 내 프로필 해제 → 기본 프로필로 (204)
```
- 그룹 내 프로필은 "닉네임 + (캐릭터 또는 사진)" 한 벌로 저장한다 (앱 `GroupMember.groupProfile`과 같은 단위). 닉네임 규칙은 가입과 같다(trim 후 2~10자).
- 파일 규칙은 기본 프로필 사진(`API_DESIGN.md` §8 설계)과 같다: 이미지 ≤ 10MB, `jpg`/`png`/`webp`, 415 `UNSUPPORTED_FILE_TYPE`, 413 `FILE_TOO_LARGE`.
- 기본 프로필을 바꿔도 그룹 내 프로필이 있는 그룹에는 반영되지 않는다 (앱 동작과 같음).
- 그룹을 나간 작성자의 피드·댓글은 기본 프로필로 보인다. 반영됨(B4): 멤버십이 삭제되면 그 멤버의 그룹 프로필 사진을 커밋 후 삭제한다. 제출물·댓글 파일은 그룹에 남긴다.

### 2-3. 수정 대상

| 대상 | 내용 |
|---|---|
| 백엔드 | 새 마이그레이션: `groups.invite_code CHAR(6) NOT NULL UNIQUE`(기존 그룹 채움), `groups.start_date DATE NULL`, `group_members`에 그룹 내 프로필 컬럼(`nickname_override`, `character_id_override`, `profile_image_url_override`). `GroupSummaryResponse`·`GroupResponse`·`GroupMemberResponse` 확장. 그룹 목록 정렬 변경. 이름 검증(`@NotBlank` + trim, 16자). 정원 검증(가입·관계 변경). 초대: 미리보기·코드 가입·재발급 추가, `JoinRequest.token`→`code`, `InviteResponse`·`createInvite`·`JwtTokenProvider.createInviteToken`/`validateInviteTokenAndGetGroupId`·`jwt.invite-token-validity-seconds` 제거. 나가기: 후임자 지정·혼자면 그룹 삭제. `GroupErrorCode`: `INVITE_NOT_FOUND`, `GROUP_FULL`, `SUCCESSOR_REQUIRED`, `INVALID_SUCCESSOR` 추가, `INVALID_INVITE_TOKEN`·`INVITE_EXPIRED`·`OWNER_CANNOT_LEAVE` 제거. 그룹 내 프로필 API 3개. 멤버를 보여 주는 모든 응답에 표시 프로필 적용 |
| Android | `Relationship`에 서버 코드 대응. `DataStore.createGroup/lookUpInvite/join/leave/setMyGroupProfile`를 API로. 16 화면: 재발급 버튼(그룹장만). 나가기: 그룹장이고 다른 멤버가 있으면 **후임자 선택 화면** 추가, `leaveMessage`의 "가장 먼저 들어온 멤버에게 넘어가요" 문구 변경. 혼자인 그룹장 문구("그룹이 삭제돼요")도 필요. `AppLinks.invite`는 서버 `inviteUrl` 사용 |
| iOS | Android와 같음 (`DataStore`, `GroupInfoView`의 나가기 alert·초대 카드, `JoinGroupView`). 그룹 만들기·나가기 중복 탭 방지 추가 |
| 설계 문서 | `API_DESIGN.md` §0-1(초대·그룹장 탈퇴 결정 교체), §2 Group 전체. `BACKEND_DB.md` groups·group_members. `PROJECT_OVERVIEW.md` §2·§3(정원, 초대). Postman `HAWE_GROUP` |

### 2-4. 미결

- 연인 시작일 변경 UI: 앱에는 생성 때만 고르는 화면이 있다. 서버는 `PATCH settings`로 바꿀 수 있게 열어 두고, 앱 화면 추가는 나중에 정한다.
- **그룹 삭제 방식 (B4 반영)**: 마지막 멤버가 나가면 그룹과 DB 의존 행을 실삭제한다. 제출물 파일·댓글 이미지·그룹 프로필 사진은 트랜잭션 커밋 후 삭제한다. 파일 삭제 실패는 로그만 남긴다.
- **초대 링크 도메인**: 앱이 쓰는 `https://hawe.app/invite/{code}`는 임시 도메인. Universal Links / App Links 검증 파일 호스팅이 필요하다.
- **코드 대입 시도 제한 (확정)**: 사용자별로 미리보기·가입의 `INVITE_NOT_FOUND`를 공유해 센다. 10분 안에 실패 10회면 다음 요청부터 429 `TOO_MANY_REQUESTS`다. 유효한 코드도 차단되며 429는 실패 횟수를 늘리지 않는다. `ALREADY_MEMBER`·`GROUP_FULL`은 세지 않고 성공도 카운트를 초기화하지 않는다. 현재 인메모리·단일 인스턴스 가정이며, 다중 인스턴스 배포 시 재검토한다.

---

## 3. Missions (조회·생성 규칙) — 상태: 반영됨 (1차: MISSION_CLOSED 제외. 발행 풀 설정화는 B5, 하루 여러 개는 B7, AI 문장 생성은 B8 반영)

제출·피드는 §6 이후에서 따로 정한다. 이 절은 미션이 언제 열리고 닫히는지와 조회 응답을 다룬다.

### 3-1. 결정

| 항목 | 결정 | 이유 |
|---|---|---|
| 미션 모델 | **단계적으로 간다.** 서버의 슬롯·틀 구조(`MISSIONS_API.md`)는 유지한다. **MVP는 하루 1개**이고, 앱의 지금 4개 화면(사진/음성/글/그림)으로 제출할 수 있는 틀만 뽑는다(2026-10-12 음성 제거 후 3개 화면: 사진/글/그림). 나머지 틀과 하루 여러 개는 앱 화면이 생기면 켠다 | 사용자 결정. 서버 설계를 버리지 않으면서 앱 작업량을 MVP에 맞춤 |
| MVP 하루 미션 수 | 1로 고정. `hawe.mission.max-daily-count=1`로 설정해 서버 검증으로 막는다. 앱에는 하루 미션 수 설정 UI가 없다 | 코드 변경 없이 설정값으로 나중에 늘릴 수 있음 |
| MVP 틀 풀 | `PHOTO_NOW`, `PHOTO_GALLERY`, `PHOTO_COLOR`(사진 화면), `DRAW_SKETCH`, `DRAW_A_TO_B`(그림 화면), `TEXT_ACROSTIC`, `TEXT_FILL_BLANK`(글 화면)의 7종. **제외**: `TEXT_ONE_WORD`(캡션 칸), `TEXT_REVIEW`(별점), `TEXT_BALANCE`(A/B 선택), `SLIDER_*`(슬라이더). 처음에는 `VOICE_LINE`(음성 화면)을 넣은 8종이었으나 2026-10-12 음성 제거로 빠졌고, 음성 틀 3종은 틀 목록에서도 삭제됐다 | 각 틀이 요구하는 입력이 기존 화면 하나로 끝나는지 기준. 틀 선택 규칙(`MISSIONS_API.md` §6-1, 하루 1개면 어제와 같은 매체 피하기)은 이 풀 안에서 적용한다. 앱의 "같은 유형 3일 연속 금지" 규칙은 서버 규칙으로 대체 |
| 발행 풀 설정 (B5, 2026-10-13) | 풀을 설정 `hawe.mission.enabled-templates`로 바꿀 수 있게 했다. 기본값은 위 7종이라 지금 발행 결과는 그대로다. 제외했던 새 틀 6종은 서버가 제출·결과를 받을 수 있게 됐고, **앱(A4)이 화면을 배포한 뒤** 설정에 추가한다 | 구 앱이 모르는 틀을 받으면 미션을 열 수 없음. 코드 배포 없이 설정만으로 켤 수 있게 |
| AI 문장 생성 (B8, 2026-10-13) | 제공사는 **Claude API**(Spring AI 없이 직접 호출). 그룹 관계·기념일·지난 기록(미션 단위 숫자)을 넣어 문장·빈칸을 만들고 서버가 다시 검증한다. 팀원 프롬프트 2개를 읽고, 없으면 임시 기본 프롬프트. **기본 꺼짐**(키·설정이 있어야 동작), 실패하면 고정 문장. 이름·그룹 이름·답 내용은 보내지 않는다. 그림 주제는 M1 범주로 묶지 않는다(자유) | 사용자 결정(2026-10-13). 상세 `MISSIONS_API.md` §6-2. 앱 응답 모양은 그대로 |
| 주제 비교 실험 (B9, 2026-10-13) | (그룹, 날짜) 단위로 AI 주제와 랜덤 주제를 나눠(비율 0.5) 참여율을 비교한다. 군·출처는 `missions`에만 저장하고 앱에는 내보내지 않는다(블라인드). 리포트는 관리자 토큰 헤더로 보호한 내부 엔드포인트. **기본 꺼짐** | README 결정 #13·#14 기본값. 앱 계약 변화 없음. 상세 `EXPERIMENT_TOPIC.md` |
| 하루 여러 개 (B7, 2026-10-13) | 상한을 5로 열었다(`max-daily-count=5`). 같은 날 슬롯끼리 틀이 겹치지 않게 고르고(`MISSIONS_API.md` §6-1), 문장은 같은 날 앞 슬롯·최근 5개와 겹치지 않게 만든다. **도장·피드 잠금은 미션별**, 그룹 목록에 `todayMissions` 추가, 콕은 `missionId`로 미션을 고를 수 있다(§4). 하루 1개의 선택 결과·응답은 그대로다 | 앱은 `dailyMissionCount`를 보내지 않아 지금은 하루 1개. 앱 화면(A6)이 생기면 그룹장이 설정한다 |
| 유형 표시 | 앱은 서버 `media`(`PHOTO/TEXT/DRAW`. `VOICE`는 음성 제거, `SLIDER`는 슬라이더 제거(2026-10-19)로 없어짐)로 화면을 고르고, `content`를 주제 문장으로 보여 준다 | 앱의 `topic` 자리에 서버 `content`를 그대로 씀 |
| 시간 규칙 | **마감 + 지각, 다음 미션이 열리면 제출 종료.** 발행~마감 전은 정상, 마감~다음 미션 발행 전은 지각(`isLate=true`)으로 제출 가능, 그 뒤로는 제출 불가 | 사용자 결정. 지각 규칙(`PROJECT_OVERVIEW`)을 지키면서 며칠 지난 미션에 제출하는 일을 막음 |
| 첫 미션 | 그룹을 만들 때 오늘 발행 시각이 이미 지났으면 **바로 연다.** 아직 안 지났으면 오늘 발행 시각에 연다 | 사용자 결정. 첫 경험이 비지 않게 (앱 현재 동작) |
| 미션 번호 | 응답에 `number`(그룹에서 몇 번째 미션인지)를 넣는다 | 앱 05 화면 "#142" 유지. 계산 비용이 작음 |
| 대상 멤버 표시 | `content`는 서버가 대상 자리(`[대상]`)를 그 그룹의 표시 닉네임으로 채워서 내려준다. `params.target`도 그대로 둔다 | 앱이 틀마다 문장을 조립하지 않게. 닉네임이 바뀌어도 응답 시점 값으로 보임 |

### 3-2. 시간 계산 규칙 (서버)

모든 시각은 Asia/Seoul 기준이다 (§0).

- **발행 시각** `publishedAt`: 슬롯의 발행 시각(지정 없으면 `hawe.mission.default-publish-time`). 첫 미션을 바로 열 때는 그룹 생성 시각.
- **마감 시각** `deadlineAt`: `publishedAt` 이후 **처음 오는** 슬롯 마감 시각(지정 없으면 `default-deadline-time`). 예: 주제 도착 시각 23:30, 마감 기본 23:00이면 마감은 다음 날 23:00이고, 다음 미션 발행(다음 날 23:30)보다 앞선다. 따라서 마감은 항상 발행 후 24시간 안이다.
- **제출 종료**: 같은 그룹·같은 슬롯에 **다음 미션이 발행되는 순간** 이전 미션은 닫힌다. 닫힌 미션에 제출하면 409 `MISSION_CLOSED`(새 오류 코드). 응답의 `closesAt`은 다음 발행 예정 시각(그룹 설정이 바뀌면 달라질 수 있는 예정값)이다.
- **지각**: `submittedAt > deadlineAt`이면 `isLate=true`.
- **현재 미션**: 슬롯마다 가장 최근에 발행된 미션. "오늘"을 `CURDATE()`로 판단하지 않는다 — 23:30에 열린 미션은 자정이 지나도 다음 발행 전까지 현재 미션이다. 그룹 목록의 `submittedToday`·`todayMission`, 멤버 목록의 `stampedToday`, 콕 찌르기의 "이미 제출함" 판단도 이 기준으로 바꾼다. B7: 하루 미션 수를 줄여 없어진 슬롯의 미션은 그룹의 다음 날 첫 발행에서, 하루 넘게 발행이 없었던 슬롯의 미션은 그 뒤 첫 발행에서 닫힌다(다음 미션 없이도). 달력의 `closed`도 같은 기준이다.
- `mission_date`는 발행 시각의 날짜(Asia/Seoul)다. 달력은 이 날짜로 묶는다.

### 3-3. 최종 계약

#### GET /api/groups/{groupId}/missions/today — 현재 미션 (멤버만)
경로는 유지하지만 의미는 "현재 열려 있는 미션"이다.
```json
{
  "date": "2026-09-28",
  "dailyMissionCount": 1,
  "nextPublishAt": "2026-09-29T09:00:00+09:00",
  "missions": [
    {
      "missionId": 10, "groupId": 1, "slotNo": 1, "number": 142,
      "content": "지금 창밖으로 보이는 풍경", "category": "일상",
      "template": "PHOTO_NOW", "media": "PHOTO",
      "params": { "subject": "창밖 풍경" },
      "publishedAt": "2026-09-28T09:00:00+09:00",
      "deadlineAt": "2026-09-28T23:00:00+09:00",
      "closesAt": "2026-09-29T09:00:00+09:00",
      "submittedByMe": false,
      "members": [ { "userId": 1, "submitted": true }, { "userId": 3, "submitted": false } ]
    }
  ]
}
```
- `date`: 현재 미션의 `mission_date`. 현재 미션이 없으면 오늘 날짜.
- `missions`: 현재 미션이 없으면 빈 배열(첫 발행 전, 생성 실패 등). 앱은 05 화면에 빈 상태("다음 미션까지 N시간", `nextPublishAt` 사용)를 보여 줘야 한다.
- `nextPublishAt`: 다음 미션 발행 예정 시각 (= 현재 미션의 `closesAt`).
- `members`: 그룹 멤버별 이 미션 제출 여부. 이름·아바타는 멤버 목록 API에서 가져온다. 콕 찌르기 상태 필드는 §4 결정에서 추가한다.
- 에러: 404 `GROUP_NOT_FOUND`, 403 `NOT_GROUP_MEMBER`.

#### GET /api/missions/{missionId}
위 `missions[]` 항목 하나와 같은 모양. 에러: 404 `MISSION_NOT_FOUND`, 403 `NOT_GROUP_MEMBER`.

#### GET /api/mission-templates
변경 없음. MVP 풀 밖의 틀도 그대로 목록에 있다 (앱은 사용하지 않음). 풀 여부를 내려줄지는 미결.

### 3-4. 수정 대상

| 대상 | 내용 |
|---|---|
| 백엔드 | **미션 생성 구현**(스케줄러 + 틀 선택 + AI 호출, 현재 전혀 없음) — MVP 틀 풀, 첫 미션 즉시 발행, 마감 계산 규칙(3-2). `MissionResponse`에 `number`, `closesAt`, `members` 추가. "오늘" 판단을 `CURDATE()`에서 "현재 미션"으로 (`MissionMapper.findTodayByGroupId`, `GroupMapper.findSummariesByUserId`, `GroupMemberMapper` 의 `stamped_today`, `PokeMapper.existsTodaySubmission`). `content`의 대상 자리 치환. `application.properties`의 `hawe.mission.max-daily-count=1`. `MissionErrorCode.MISSION_CLOSED`(제출 구현 때) |
| Android | `MissionType`을 서버 `media`로 대응(`TEXT`↔`WRITE`). `TodayMission.topic` → `content`, `opensAt/closesAt` → `publishedAt/closesAt`, `deadlineAt` 추가. 05 화면: 마감 표시, 마감 뒤 "지각" 안내, 미션 없는 빈 상태. 홈 카드는 목록 응답의 `todayMission`. 글 화면: 틀별 길이·형식(`TEXT_FILL_BLANK` ≤ 100자, `TEXT_ACROSTIC` 3줄·각 줄 첫 글자) 검사와 안내. 그림 화면: `DRAW_SKETCH`일 때 `params.seconds` 타이머(설계상 시간 제한은 클라이언트가 강제). 16 화면 주제 도착 시각은 `missionSlots[0].publishTime`. `DataStore.refreshTodayMissions`/`makeMission`/`TopicPool`(앱 안 미션 생성) 제거 |
| iOS | Android와 같음 (`TodayMission`, `MissionView`, `WriteMissionView`, `DrawMissionView`, `GroupInfoView`, `DataStore`) |
| 설계 문서 | `MISSIONS_API.md`: §0 "지각" 규칙에 제출 종료 추가, §2 today 응답(`number`·`closesAt`·`members`·현재 미션 의미), §3 `MISSION_CLOSED`, §6 첫 미션 즉시 발행·마감 계산, §6-1에 MVP 틀 풀, §10 오류 코드. `PROJECT_OVERVIEW.md` §3 마감과 지각, §5(MVP 하루 1개), §11-6(자정 넘는 마감 → 3-2 규칙으로 해결). `CLAUDE.md`의 미션 설명은 사용자가 요청할 때만 수정 |

### 3-5. 미결

- `DRAW_SKETCH` 타이머를 앱에 넣지 못하면 MVP 풀에서 뺀다.
- 임시 생성기는 고정 변형을 사용한다. 발행 오류는 로그를 남기고 스케줄러가 다음 분에 재시도한다. ~~향후 AI 연동 시 재시도·대체 문장 정책은 별도로 정한다.~~ → B8: AI 응답은 검증 실패·오류 시 최대 2번 시도(총 40초 안), 그래도 안 되면 고정 문장 (`MISSIONS_API.md` §6-2).
- `/mission-templates`에 MVP 풀 여부(`enabled`)를 내려줄지.
- 그룹 생성 직후 첫 미션의 `deadlineAt`이 생성 시각과 너무 가까운 경우(예: 22:55 생성, 23:00 마감) 그대로 둘지.
- ~~하루 여러 개를 켤 때 그룹 목록 `todayMission`의 모양~~ — B7에서 `todayMissions` 배열 추가로 정함(§2). 홈 카드·05 화면 구성은 A6.

---

## 4. Pokes — 상태: 반영됨 (백엔드; 앱은 5A, 알림 발송은 9단계)

### 4-1. 결정

| 항목 | 결정 | 이유 |
|---|---|---|
| 횟수 제한 | **현재 미션마다, 받는 사람별로, 종류별 1회.** (보낸 사람, 받는 사람, 미션, 종류) 기준 — 콕 1번과 똥뿌리기 1번까지 보낼 수 있다. 같은 종류 두 번째는 409 `ALREADY_POKED` | 사용자 결정. 콕 뒤에도 안 내면 똥을 뿌리는 단계적 재촉. MVP(하루 1개)에서는 앱의 "하루 1회"와 같고, 자정을 넘는 미션에서도 기준이 흔들리지 않음 |
| 똥뿌리기(`fling`) | ~~앱에 추가한다.~~ **2026-10-19 삭제.** 서버는 `type: "poke"`만 받고 `fling`은 400 `VALIDATION_ERROR`. 예전 `fling` 행은 `pokedByMe`에서 빠진다 | 사용자 결정 (기능 없애기로 함) |
| 대상 조건 | 받는 사람은 같은 그룹 멤버이고 나 자신이 아니며 **현재 미션**에 아직 제출하지 않았어야 한다 | `CURDATE()` 기준(어떤 미션이든 오늘 제출)을 현재 미션 기준으로 바꿈 (§3-2) |
| 콕 상태 조회 | 현재 미션 응답의 `members[]`에 내가 그 멤버에게 보낸 재촉을 넣는다 | 앱 05 화면이 "찔렀어요" 상태를 다시 그릴 수 있어야 함 |
| 알림 | `poke` 푸시를 받는 사람에게 보낸다. 알림 설정의 "콕 찌르기" 항목을 따른다 (§8) | 사용자 결정 |
| 미션 고르기 (B7, 2026-10-13) | 요청에 **선택** 필드 `missionId`. 없으면 첫 슬롯(현재 미션 중 가장 작은 `slotNo`)의 미션, 있으면 그 그룹의 현재 미션이어야 한다(아니면 409 `NO_OPEN_MISSION`). 횟수 제한은 미션마다라 슬롯이 다르면 같은 종류도 따로 보낼 수 있다 | 하루 여러 개일 때 05 화면이 미션별로 재촉. 지금 앱은 보내지 않아 하루 1개와 같음 |

### 4-2. 최종 계약 (현재까지)

```json
// POST /api/groups/{groupId}/pokes — 멤버만
// Request (missionId 는 선택)
{ "toUserId": 7, "type": "poke", "missionId": 10 }
// Response 201
{ "id": 1, "toUserId": 7, "type": "poke", "missionId": 10, "sentAt": "2026-09-28T15:41:48+09:00" }
```
- `missionId`가 없으면 서버가 그 그룹의 현재 미션(하루 여러 개면 첫 슬롯)을 찾아 `pokes.mission_id`에 저장한다. 현재 미션이 없거나, 보낸 `missionId`가 그 그룹의 현재 미션이 아니면(닫힌 미션·남의 그룹·없는 미션) 409 `NO_OPEN_MISSION`.
- 에러: 404 `GROUP_NOT_FOUND`, 403 `NOT_GROUP_MEMBER`, 400 `CANNOT_POKE_SELF`, 404 `TARGET_NOT_GROUP_MEMBER`, 409 `TARGET_ALREADY_SUBMITTED`, 409 `ALREADY_POKED`, 400 `VALIDATION_ERROR`(`type`).
- 현재 미션 응답(§3-3)의 `members[]` 항목: `{ "userId": 7, "submitted": false, "pokedByMe": ["poke"] }` — 내가 이 미션에서 그 멤버에게 보낸 종류 목록(`"poke"`), 안 보냈으면 빈 배열. 앱은 종류별로 버튼을 "보냈어요" 상태로 바꾼다.

### 4-3. 수정 대상

| 대상 | 내용 |
|---|---|
| 백엔드 | V1 최종 스키마에 `pokes.mission_id` + 유니크 키 `(from_user_id, to_user_id, mission_id, type)` 반영. `PokeService` 현재 미션 조회·횟수 검사, `PokeErrorCode.ALREADY_POKED`·`NO_OPEN_MISSION`, `PokeResponse.missionId`, 현재 미션 응답 `members[].pokedByMe` 구현. 알림 발송은 9단계에서 연결 |
| Android | 05 화면 `MissionMemberRow`에 똥뿌리기 버튼, 종류별 보낸 상태, 토스트 문구. `DataStore.poke` → API, `StoredMission.poked` → 서버 `pokedByMe`. 409 `ALREADY_POKED`/`TARGET_ALREADY_SUBMITTED`는 상태를 새로고침하고 조용히 처리. `PushKind`에 `fling` 추가, `poke` 채널로 표시(§8) |
| iOS | Android와 같음 (`MissionView.status(for:)`, `DataStore.poke`, `PushKind`) |
| 설계 문서 | `API_DESIGN.md` §5 Poke(횟수 제한·`missionId`·오류 코드). `BACKEND_DB.md` pokes. `PROJECT_OVERVIEW.md` §3 재촉 |

### 4-4. 미결

- 똥뿌리기 버튼 디자인(아이콘·문구)은 Figma에 없음.

---

## 5. Me (내 정보·프로필·탈퇴) — 상태: 백엔드 반영됨 (앱은 8A)

`GET /me`와 `PATCH /me/password`는 §1 Auth에 반영됐다. 그룹 내 프로필 백엔드는 §2-2에 반영됐다.

### 5-1. 결정

| 항목 | 결정 | 이유 |
|---|---|---|
| 캐릭터 | V5 마이그레이션대로 7종, `svgKey` = 앱 캐릭터 키. 목록 순서는 서버 `id` 순, 앱은 자체 순서로 표시하고 키로 대응 | 이미 정리된 내용을 확정. V5 주석이 이 문서를 참조함 |
| 기본 프로필 닉네임 변경 | `PATCH /api/me/profile`로 바꾼다 (17-1 프로필 편집) | 앱에 화면이 있고 저장할 API만 없음. 정책 선택 없음 |
| 기본 프로필 사진 | `API_DESIGN.md` §8 설계(`PUT/DELETE /me/profile-image`)대로 | 기존 설계 유지 |
| 탈퇴 — 데이터 | **내 작품(제출물·파일)과 댓글을 삭제한다.** 복구 불가 | 사용자 결정. 앱 탈퇴 화면 문구와 같음 (그룹 나가기는 작품이 남는 것과 다름) |
| 탈퇴 — 그룹장 | **다른 멤버가 있는 그룹의 그룹장이면 탈퇴할 수 없다.** 먼저 그 그룹에서 후임자를 골라 나가야 한다. 혼자인 그룹은 탈퇴 때 삭제한다 | 사용자 결정. 나가기 규칙(후임자 선택)과 일관 |

### 5-2. 최종 계약 (현재까지)

```json
// PATCH /api/me/profile — 기본 프로필 닉네임
{ "nickname": "하늘" }
// Response 200 — GET /api/me 와 같은 모양
```
- 닉네임 규칙은 가입과 같다(trim 후 2~10자). 그룹 내 프로필이 있는 그룹에는 반영되지 않는다.

```text
PATCH  /api/me/character        기존 (요청 characterId, 응답 characterId/name/svgKey)
PUT    /api/me/profile-image    구현됨 (multipart file)
DELETE /api/me/profile-image    구현됨 (204)
```
- 앱에서 사진 프로필이던 사용자가 캐릭터를 고르면 `PATCH /me/character` 후 `DELETE /me/profile-image`를 호출한다 (기존 설계 규칙).

```text
DELETE /api/me — 회원 탈퇴, 204
```
- 다른 멤버가 있는 그룹의 그룹장이면 409 `OWNER_MUST_TRANSFER` ("그룹장인 그룹에서 먼저 나가 주세요"). 앱은 내 그룹 목록에서 `ownerId == 내 id && memberCount > 1`인 그룹을 미리 찾아 탈퇴 화면에서 안내한다.
- 처리 순서(한 트랜잭션): 그룹장 제한 검사 → 삭제할 파일 key 수집 → 모든 그룹에서 나감(혼자인 그룹은 삭제) → 내 제출물과 종속 댓글·반응·정답 시도, 내 댓글·반응·정답 시도·콕·알림 삭제 → 계정 삭제 → 커밋 후 파일 삭제. 삭제된 제출물에 달린 다른 사람 댓글·반응도 함께 삭제한다.
- 9단계에서 기기 토큰·알림 설정 테이블을 추가하면 탈퇴 삭제 목록에도 추가한다.
- 같은 이메일로 다시 가입할 수 있다 (새 계정).

### 5-3. 수정 대상

| 대상 | 내용 |
|---|---|
| 백엔드 | `PATCH /me/profile`, `DELETE /me` (새 기능 패키지에서), `OWNER_MUST_TRANSFER` 오류 코드. 탈퇴 시 삭제 범위(제출물·파일·댓글·반응·정답 시도·콕·알림·그룹 멤버십). 캐릭터는 변경 없음 (V5 반영됨) |
| Android | 17-1 프로필 편집 → API(닉네임·캐릭터·사진). 17-5 탈퇴: 그룹장 그룹이 남아 있으면 안내하고 진행 막기, 성공 시 토큰 삭제 후 A1. `AppSession.withdraw`·`DataStore.reset`은 로컬 정리로 남김 |
| iOS | Android와 같음 (`ProfileEditView`, `WithdrawView`, `AppSession.withdraw`) |
| 설계 문서 | `API_DESIGN.md`에 Me 절 신설(`GET /me`, `PATCH /me/profile`, `PATCH /me/password`, `DELETE /me`), §8 캐릭터는 이미 반영됨. `PROJECT_OVERVIEW.md` §3·§11 |

### 5-4. 확정과 후속

- 유효한 액세스 토큰도 보호된 요청마다 필터가 사용자 존재를 확인한다. 삭제된 계정이면 401 `INVALID_TOKEN`이다. refresh도 기존 사용자 조회로 거절한다.
- 삭제된 사용자의 작품에 달린 다른 사람 댓글·반응은 작품과 함께 삭제한다.
- 활동 요약 API는 §9, 알림 설정·기기 토큰 API는 §8에 있다.

---

## 6. Submissions · Feed — 상태: 반영됨 (반응·댓글 집계 포함, 새 틀 6종 제출·집계 결과는 B5 백엔드 반영, 서명 URL 제외)

사진 주제 적합도(B10): `PHOTO_NOW`·`PHOTO_COLOR` 제출 커밋 후 비동기로 판정한다. 기본 비활성이다. 틀별 임계값은 `VISION_TOPIC_FIT.md`를 따른다. 피드·상세의 선택적 `aiReview`에는 배지와 긍정 문구만 있으며 점수는 공개하지 않는다. 재처리 스케줄러는 없다.

백엔드에 제출·피드·상세와 로컬 파일 저장·공개 서빙을 구현했다. `MISSIONS_API.md` §3·§4의 1차 계약을 따른다. B5(2026-10-13)에서 새 틀 6종의 제출과 `GET /api/missions/{missionId}/result`(집계 결과, `MISSIONS_API.md` §5)를 더했다. 앱 화면은 A4.

### 6-1. 결정

| 항목 | 결정 | 이유 |
|---|---|---|
| 제출 엔드포인트·형식 | 설계대로 `POST /api/missions/{missionId}/submissions`, `multipart/form-data`, 틀별 필수 필드 검증, 1인 1회(`ALREADY_SUBMITTED`), 수정·삭제 없음 | 앱도 "한 번 올리면 수정·삭제 불가" — 같음 |
| 제출 기한 | §3-2 규칙: 다음 미션이 열리면 409 `MISSION_CLOSED`, 마감 뒤는 `isLate=true` | §3 결정 |
| **음성 제거 (2026-10-12)** | 음성 미션과 음성 제출을 서비스에서 **완전히 없앤다.** 음성 틀 3종(`VOICE_LINE`·`VOICE_MIMIC`·`VOICE_IMPRESSION`), 음성 파일 업로드·형식 판별, `duration`·`waveform` 필드를 삭제한다. 업로드 파일은 이미지(`jpg/png/webp ≤ 10MB`)만 받고, 오디오 파일은 415 `UNSUPPORTED_FILE_TYPE`, `duration`·`waveform` 필드는 400 `INVALID_SUBMISSION`. 스키마는 바꾸지 않는다(`answer_text`·`answer_aliases`·`submission_guesses`는 미사용 잔재로 유지) | 사용자 결정 ("음성 기능을 아예 제거한다") |
| ~~음성 길이~~ | 폐기됨 (음성 제거). 이전 결정: 최대 30초, 파일 ≤ 5MB | 음성 제거로 해당 없음 |
| ~~음성 길이·파형 전달~~ | 폐기됨 (음성 제거). 이전 결정: 앱이 `duration`·`waveform`을 보내고 서버가 `answer_data`에 저장해 피드에 내려줌 | 음성 제거로 해당 없음 |
| 이미지 | 사진 JPEG(3:4로 잘림), 그림 PNG — 설계의 `jpg/png/webp ≤ 10MB` 안에 들어감 | 변경 없음 |
| 글 길이 | 틀별 규칙을 따른다 (`TEXT_FILL_BLANK` ≤ 100자, `TEXT_ACROSTIC` 3줄). 앱의 일괄 300자 제한은 틀별 제한으로 바꾼다 | 서버 검증과 앱 입력 제한이 같아야 함 |
| 캡션 | 서버는 선택 필드로 받지만 MVP 앱은 보내지 않는다 | MVP 틀 풀에서 캡션이 필수인 틀이 없음 (§3) |
| 새 틀 6종 (B5) — **슬라이더 3종은 2026-10-19 삭제** | `TEXT_ONE_WORD`(단어 ≤ 20자·이유 선택 ≤ 100자), `TEXT_REVIEW`(별점 1~5·한 줄 ≤ 100자), `TEXT_BALANCE`(`A`/`B`·이유 선택), `SLIDER_POSITION`·`SLIDER_TASTE`(0~100), `SLIDER_CONSENSUS`(내 위치 + 그룹 평균 예측, 둘 다 0~100). 숫자는 정수 문자열만 받는다. 응답에 `number`·`choice`·`guess`를 그 틀에서만 넣는다. 결과 API는 내가 제출해야 열리고, 평균·차이는 응답에서만 소수 첫째 자리로 반올림한다 | `MISSIONS_API.md` §3-2·§5 그대로. 스키마 변경 없음(V1에 컬럼이 이미 있음) |
| 피드 잠금 | 설계대로 미션별: 그 미션에 내가 제출해야 피드·상세를 볼 수 있다(423 `FEED_LOCKED`) | 앱 규칙(내가 올린 미션만 결과 공개)과 같음 |
| 피드 작성자 표시 | 피드·상세의 작성자는 §2의 **프로필 객체**(그룹 내 프로필 적용)로 내려준다 | 앱이 아바타를 그려야 함. 설계에는 `nickname`만 있었음 |
| 아직 안 낸 멤버 | 피드에 넣지 않는다. 앱은 현재 미션 응답의 `members[]`(현재 미션일 때)와 멤버 목록으로 계산한다. 지난 미션은 달력 결정(§8)에서 정한다 | 피드는 제출물 목록 — 책임 분리 |
| 지각 표시 | 피드·상세 응답의 `isLate`를 앱 결과 화면에 표시한다 | 지각 규칙을 채택했으므로 |

### 6-2. 최종 계약 (설계와 달라지는 부분만)

```text
POST /api/missions/{missionId}/submissions   (multipart)
  사진·그림 틀: file(jpg/png/webp ≤ 10MB), caption(선택)
  (음성 틀의 file(m4a 등)·duration·waveform 은 2026-10-12 음성 제거로 폐기)
```
```json
// 피드 항목 (GET /api/missions/{missionId}/submissions) — 사진 예
{ "id": 100,
  "author": { "userId": 3, "nickname": "서연", "characterKey": "mulbangul", "profileImageUrl": null },
  "template": "PHOTO_NOW", "mediaUrl": "/files/submissions/uuid.jpg", "mediaType": "IMAGE",
  "reactions": [], "commentCount": 0,
  "submittedAt": "2026-09-28T21:00:00+09:00", "isLate": false }
```
- 설계의 `userId`·`nickname` 최상위 필드는 `author` 객체로 바꾼다. 상세(`GET /api/submissions/{id}`)도 같다.
- 7단계 백엔드부터 `reactions`와 `commentCount`에 실제 데이터를 채운다. 데이터가 없으면 각각 빈 배열과 0이다.
- 오류 코드에 `MISSION_CLOSED`(409) 추가. 나머지는 설계와 같다.

### 6-3. 수정 대상

| 대상 | 내용 |
|---|---|
| 백엔드 | 제출·피드·상세 구현(신규). multipart 한도(`spring.servlet.multipart.*`), 파일 저장소, ~~음성 `duration`·`waveform` 검증·저장~~(음성 제거로 삭제됨), 작성자 프로필 객체, `MISSION_CLOSED` |
| Android | `DataStore.submit`/`result` → API. ~~`SubmissionContent.Voice`의 길이·파형 전송~~(음성 제거로 N/A, A0에서 삭제). 글 화면 틀별 제한. 결과 화면 지각 표시, 423 처리(05로 돌려보냄). `Submission.mediaURL`은 서버 URL |
| iOS | Android와 같음 (`DataStore.submit/result`, `WriteMissionView.maxLength`, `ResultsView`) |
| 설계 문서 | `MISSIONS_API.md` §3-3·§4 응답(`author`, `reactions`), §10 오류 코드. (§3-1 음성 필드·§3-2 음성 30초·응답의 `duration`/`waveform`은 음성 제거로 N/A) |

### 6-4. 미결

- 1차는 로컬 디스크 저장과 `/files/**` 공개 URL로 결정. S3·CDN·서명 URL은 2차에서 검토한다. 그룹 삭제 시 제출물·댓글 이미지·그룹 프로필 사진을 커밋 후 정리한다(B4).
- 사진·그림 파일 크기를 앱에서 줄여 보낼지(현재 원본 품질 그대로).

---

## 7. Reactions · Comments — 상태: 백엔드 반영됨 (앱은 7A, 알림은 9단계)

### 7-1. 결정

| 항목 | 결정 | 이유 |
|---|---|---|
| 반응 | **스티커 여러 종류 토글** (설계 유지). 앱의 하트 좋아요 1종을 스티커 선택 UI로 바꾼다. 한 사람이 한 작품에 여러 종류를 누를 수 있고, 같은 종류를 다시 누르면 취소 | 사용자 결정 |
| 스티커 종류 | **초안 5종**: `heart`(하트), `laugh`(웃김), `wow`(놀람), `sad`(슬픔), `fire`(최고). 서버는 이 목록 밖의 값을 400으로 거부한다 | 사용자 결정. 디자인이 나오면 이름·종류를 바꿀 수 있게 서버는 한 곳(enum)에서 관리 |
| 누른 사람 표시 | **스티커별로 누른 멤버를 보여 준다** | 소규모 친한 그룹에서 누가 반응했는지가 재미 요소 (사용자 결정). 앱의 "○○님 외 N명"을 스티커별로 확장 |
| 내 작품에 반응 | 허용 (설계에 금지 규칙 없음) | 정책 변경 없음 |
| 댓글 멘션 | **MVP에 포함.** 설계대로 앱이 `mentionedUserIds`를 명시적으로 보낸다 | 사용자 결정. 앱에 멤버 선택 UI 필요 |
| 댓글 이미지 | **MVP에 포함** | 사용자 결정. 앱에 사진 선택·표시 UI 필요 |
| 내 댓글 삭제 | **MVP에 포함** — `DELETE /api/comments/{commentId}` (본인만) | 사용자 결정. 앱에 이미 있는 기능, 설계에 API가 없었음 |
| 댓글 이미지 업로드 방식 | 댓글 작성 API가 파일을 직접 받는다 (`multipart/form-data`) | 제출 API와 같은 방식. 별도 업로드 API·고아 파일 정리가 필요 없음 (기술 결정) |
| 댓글 작성자 표시 | §2 프로필 객체 | 피드와 같은 이유 |

### 7-2. 최종 계약

```json
// POST /api/submissions/{id}/reactions — 토글
// Request
{ "stickerType": "heart" }
// Response 200 — 이 작품의 반응 전체(토글 후)
{ "reactions": [
  { "stickerType": "heart", "count": 2, "reactedByMe": true, "userIds": [1, 3] },
  { "stickerType": "fire", "count": 1, "reactedByMe": false, "userIds": [5] }
] }
```
- 피드 항목·상세의 `reactions`도 같은 배열이다 (0개인 종류는 빼고, 앱이 정한 순서로 표시). 이름·아바타는 멤버 목록에서 `userIds`로 찾는다. 그룹을 나간 사람의 반응은 남되 앱은 이름 대신 "나간 멤버"로 표시한다.
- 에러: 404 `SUBMISSION_NOT_FOUND`, 403 `NOT_GROUP_MEMBER`, 423 `FEED_LOCKED`, 400 `VALIDATION_ERROR`(목록 밖 종류).
- 설계의 응답 `{ stickerType, reacted, count }`를 위 모양으로 바꾼다 — 토글 뒤 화면 전체를 다시 그리기 쉽게.


```text
POST /api/submissions/{id}/comments   multipart/form-data
  content          text, trim 후 1~300자 (이미지만 있으면 빈 값 허용)
  mentionedUserIds 반복 필드 (같은 그룹 멤버만, 아니면 400)
  file             선택, 이미지 jpg/png/webp ≤ 10MB
```
```json
// Response 201, 목록 항목도 같은 모양
{ "id": 2,
  "author": { "userId": 3, "nickname": "잎사귀", "characterKey": "ipsagwi", "profileImageUrl": null },
  "content": "@몽실이 이거 뭐야", "imageUrl": null, "mentionedUserIds": [1],
  "createdAt": "2026-09-28T21:10:00+09:00", "mine": true }
```
- 목록 `GET /api/submissions/{id}/comments`: 설계대로 페이지네이션, 정렬은 작성 순.
- `DELETE /api/comments/{commentId}` — 본인 댓글만, 204. 남의 댓글이면 403 `NOT_COMMENT_OWNER`, 없으면 404 `COMMENT_NOT_FOUND`. 첨부 이미지도 삭제.
- 댓글 작성·조회·삭제 모두 피드 잠금 규칙(423 `FEED_LOCKED`)을 따른다.
- `mine`: 앱의 `SubmissionComment.isMine`. 내 id와 비교해도 되지만 응답에 넣어 단순화한다.

### 7-3. 수정 대상

| 대상 | 내용 |
|---|---|
| 백엔드 | 댓글 API 3개, 반응 토글 API, 스티커 종류 enum, 오류 코드(`CommentErrorCode` 등). `reactions` 테이블에 `(submission_id, user_id, sticker_type)` 유니크 키 확인 |
| Android | 결과·상세 화면: 하트 좋아요 → 스티커 선택·집계 UI, 댓글 입력에 멘션(멤버 선택)·이미지 첨부, 이미지 댓글 표시. `DataStore.toggleLike/addComment/deleteComment` → API. `Submission.likeCount/likedByMe/likerNames` 모델 교체 |
| iOS | Android와 같음 (`ResultComponents`의 좋아요, `SubmissionDetailView`, `ResultFeed`) |
| 설계 문서 | `API_DESIGN.md` §0-1(스티커 결정 보완), §4: 댓글 multipart·삭제 API·`author`·`mine`, 반응 응답·스티커 5종. `MISSIONS_API.md` §4 피드·상세의 `reactions` 모양 |

### 7-4. 미결

- 이미지만 있는 댓글은 허용하며 `content`를 빈 문자열로 저장한다.
- 댓글 작성 시 `comment`(작품 작성자)·`mention`(멘션된 멤버) 알림을 보낸다 (§8). 반응에는 알림이 없다.
- 스티커 선택·멘션·이미지 댓글 UI 디자인은 Figma에 없음.

---

## 8. Notifications — 상태: 앱 안 알림 API 반영됨; 푸시·설정·그룹별 알림 미구현

이번 구현은 `topic`·`poke`·`comment`·`mention`의 앱 안 알림 목록과 읽음 처리, 안 읽은 개수만 다룬다.
`friend_done`(그룹원 작품 제출 알림)은 2026-10-19 **없애기로 결정**했다(푸시·앱 안 알림·설정 항목 모두). 아래 푸시·기기 토큰·설정은 후속 작업 범위다.

### 8-1. 결정

| 항목 | 결정 | 이유 |
|---|---|---|
| 전체 목표 | 서버 푸시 발송(FCM·APNs) + 기기 토큰 등록, 알림 설정 저장, 그룹별 알림, 앱 안 알림 목록. 이번 단계는 앱 안 알림만 구현 | 사용자 결정 |
| 알림 종류 | 서버 구현: `topic`(주제 도착), `poke`(콕), `comment`(내 작품에 댓글), `mention`(댓글 멘션) — 4종. **추가 요청(2026-10-19, 서버 미구현)**: `reaction`(내 작품에 스티커 반응) — 앱 알림 목록은 이미 그릴 수 있고, 푸시 발송·설정 항목은 미정. `friend_done`(그룹원 작품 제출)은 2026-10-19 삭제. `fling`(똥뿌리기)은 2026-10-19 삭제 | 이번 구현 범위 + 사용자 추가 요청 |
| 알림 설정 항목 | 앱 17-1 항목 그대로: 전체, 주제 도착, 콕 찌르기, 댓글(**멘션 포함**). 친구 완료 항목은 2026-10-19 삭제 | 사용자 결정 (기존 항목에 묶기). 앱 화면·채널을 늘리지 않음 |
| 설정·그룹 알림 적용 대상 | 설정과 그룹별 알림은 **푸시 발송만** 거른다. 앱 안 알림 목록에는 모두 쌓인다 | 목록은 받은편지함 역할, 푸시는 방해 여부 — 역할 분리 (제안, 이견 있으면 변경) |
| 그룹별 알림 | 멤버마다 저장. 끄면 그 그룹에서 오는 모든 종류의 푸시를 보내지 않는다 | 앱 주석의 서버 필터 규칙(`notification_prefs` + `group_members.notify`)과 같음 |
| 자기 자신 | 내 행동으로 나에게 알림을 보내지 않는다 (내 작품에 내가 댓글 등) | — |
| 중복 | 내 작품 댓글에서 나를 멘션하면 `mention` 하나만 보낸다 | 같은 댓글로 두 번 울리지 않게 |

### 8-2. 발송 규칙

| 종류 | 받는 사람 | 시점 | 설정 항목 |
|---|---|---|---|
| `topic` | 그룹 멤버 전원 | 미션 발행 | 주제 도착 |
| `poke` | 받는 사람 | 전송 즉시 | 콕 찌르기 |
| `comment` | 작품 작성자 | 댓글 작성 | 댓글 |
| `mention` | 멘션된 멤버 | 댓글 작성 | 댓글 |

### 8-3. 최종 계약

```json
// POST /api/me/devices — 기기 토큰 등록 (로그인 직후, 토큰이 바뀔 때)
{ "platform": "ANDROID", "token": "fcm-or-apns-token" }   // platform: ANDROID | IOS
// Response 204
```
```text
DELETE /api/me/devices/{token}    로그아웃 시 해제, 204 (없는 토큰도 204)
```
- 같은 토큰이 다른 계정으로 등록되면 이전 계정에서 떼어 낸다 (한 기기에 한 계정).

```json
// GET /api/me/notification-prefs
{ "all": true, "topic": true, "poke": true, "comment": true }
// PATCH /api/me/notification-prefs — 일부만 보내도 됨, 응답은 전체
{ "poke": false }
```
- 처음 가입한 사용자는 위 기본값. 앱 `NotificationPrefs`와 필드가 같다.

```json
// PATCH /api/groups/{groupId}/members/me/notify — 그룹별 알림 (멤버 누구나)
{ "notify": false }
// Response 200
{ "notify": false }
```
- 그룹 상세(§2) `myNotify`에 이 값이 들어간다.

```json
// 푸시 payload (FCM data / APNs custom keys)
{ "type": "comment", "group_id": "1", "mission_id": "10", "submission_id": "100" }
// 알림 표시: title = 그룹 이름, body = 서버가 만든 문장 (예: "서연님이 내 작품에 댓글을 남겼어요")
```
- 키 이름은 앱이 이미 처리하는 snake_case(`type`, `group_id`, `submission_id`)를 유지하고 `mission_id`를 더한다. 값은 문자열.
- 종류별 포함 키: `topic`·`poke` → `group_id`, `mission_id` / `comment`·`mention` → + `submission_id`.
- 앱 이동 규칙: `topic`·`poke` → 그 그룹 05 화면, `comment`·`mention` → 그 미션 결과 화면(내가 제출했을 때, 아니면 05). 앱의 "그룹을 못 찾으면 첫 그룹" 임시 규칙은 서버 연결 뒤 "못 찾으면 이동하지 않음"으로 바꾼다.

```json
// GET /api/notifications — 앱 안 알림 목록 (최신순, 페이지네이션)
{ "content": [
  { "id": 1, "type": "poke", "groupId": 1, "groupName": "컴공 동기",
    "fromUser": { "userId": 3, "nickname": "서연", "characterKey": "mulbangul", "profileImageUrl": null },
    "missionId": 10, "submissionId": null, "body": "서연님이 콕 찔렀어요",
    "isRead": false, "createdAt": "2026-09-28T15:41:48+09:00" }
], "page": 0, "size": 20, "totalPages": 1, "totalElements": 6 }
```
```text
PATCH /api/notifications/{id}/read     204
PATCH /api/notifications/read-all      204
GET /api/notifications/unread-count    { "unreadCount": 3 }
```
- `type`: `topic` · `poke` · `comment` · `mention` · `reaction`. `reaction`은 `submissionId`·`missionId`를 넣고, `body`에 스티커를 담아도 된다(예: "서준님이 내 작품에 ❤️ 반응을 남겼어요"). 앱은 모르는 `type`을 목록에서 뺀다.
- 앱(2026-10-19): 홈 상단 종 아이콘 → 알림 목록. `body`가 없으면 앱이 종류별 기본 문장을 쓴다. 누르면 읽음 처리 후 `topic`·`poke`는 05, `comment`·`mention`·`reaction`은 내가 낸 미션이면 결과 화면으로 간다.
- 설계의 `payload` 객체 대신 필요한 id를 평평하게 내린다. `fromUser`는 그 그룹의 표시 프로필, `topic`이면 `null`. 설계의 `mission_open`은 `topic`으로 이름을 바꾼다.

### 8-4. 수정 대상

| 대상 | 내용 |
|---|---|
| 백엔드 | FCM·APNs 발송 모듈(새 의존성·자격 증명), 새 테이블: `device_tokens`, `notification_prefs`(또는 users 컬럼), `group_members.notify`. 알림 생성·발송을 미션 발행·콕·제출·댓글에 연결. 알림 목록·읽음 API |
| Android | FCM 연결(`google-services.json`, `FirebaseMessagingService`) → 기존 `PushNotifier.show` 재사용, 토큰 등록/해제. 채널은 4개 유지 — `mention`은 `comment` 채널로. `PushKind`에 `mention` 추가. `pushTarget`이 payload의 `group_id`/`mission_id`를 쓰도록. 17-1 설정·16 그룹 알림 → API(문구에 "멘션 포함" 반영). **알림 목록 화면 신설**과 진입점 |
| iOS | Android와 같음 (`PushNotifications.swift`의 `PushKind`·`PushCenter`, APNs 토큰 등록, `NotificationSettingsView`, `GroupInfoView`, 알림 목록 화면 신설) |
| 설계 문서 | `API_DESIGN.md` §6 Notification 전면 수정(종류·응답·기기 토큰·설정·그룹 알림). `BACKEND_DB.md` 새 테이블·컬럼. `PROJECT_OVERVIEW.md` §3 알림 |

### 8-5. 미결

- 알림 목록 화면 디자인과 진입점(홈 상단 종 아이콘 등) — Figma에 없음.
- 푸시 문장(body) 문구 전체 목록.
- 알림 보관 기간(예: 30일 뒤 삭제).
- Firebase 프로젝트·APNs 인증 키 준비 (`docs/FRONTEND/ANDROID.md` §8 결정 필요 항목).

---

## 9. Calendar · 활동 요약 — 상태: 반영됨 (2026-10-12, iOS는 Mac 빌드 확인 전)

### 9-1. 결정

| 항목 | 결정 | 이유 |
|---|---|---|
| 달력 범위 | **그룹별 달력** (앱 15 화면 기준). 설계의 마이페이지 통합 달력 `/me/calendar`는 폐지 | 사용자 결정 |
| 도장 | 그룹 달력에 **내 도장**을 표시한다: `GOOD`(참잘했어요), `LATE`(지각 전용 도장), `PENALTY`(벌점) | 사용자 결정. 지각은 별도 도장 (디자인 1종 추가 필요) |
| 벌점 범위 | 그 미션 때 내가 그룹에 있었고 **미션이 닫힌** 날만 벌점. 들어오기 전, 아직 제출할 수 있는 현재 미션, 미션이 없던 날은 도장 없음 | 사용자 결정 |
| 미션 도중 가입 (2026-10-12) | 미션이 **열린 뒤에** 들어와서 내지 않았으면 벌점 없음(`null`). "미션 때 그룹에 있었음" = `joined_at <= published_at` (그룹 생성 직후 첫 미션의 시각 차이 때문에 1분 여유) | 사용자 결정. 이미 시작된 미션으로 벌점을 주면 불공평 |
| `memberCount` (2026-10-12) | 미션이 열릴 때 이미 들어와 있던 현재 멤버 + 그 미션에 제출한 사람(나간 사람 포함). 제출하지 않고 나간 사람은 셀 수 없음(멤버 이력이 없음) | 사용자 결정. 현재 인원만 쓰면 "4/3명"이나 나중에 들어온 사람까지 세는 문제가 생김 |
| 도장 계산 | 저장하지 않고 제출 기록·`isLate`·멤버십으로 계산 (기존 원칙) | `PROJECT_OVERVIEW` 핵심 규칙 |
| 활동 요약 (17) | 앱 정의 그대로 서버가 계산: 완료 미션 수(지각 포함 제출 수), 연속 일수(오늘 또는 어제부터 거꾸로 하루도 빠짐없이 제출한 날 수, 모든 그룹 합산), 그룹 수, 유형별 수 | 앱에 화면이 있고 정의도 있음. 정책 변경 없음 |

### 9-2. 최종 계약

```json
// GET /api/groups/{groupId}/calendar?month=2026-09 — 멤버만
{ "month": "2026-09",
  "days": [
    { "date": "2026-09-01",
      "missions": [ { "missionId": 101, "media": "DRAW", "content": "오늘 기분을 날씨로 그려줘",
                      "submittedCount": 4, "memberCount": 5, "stamp": "GOOD" } ] },
    { "date": "2026-09-02",
      "missions": [ { "missionId": 102, "media": "PHOTO", "content": "오늘의 하늘",
                      "submittedCount": 3, "memberCount": 5, "stamp": "PENALTY" } ] }
  ] }
```
- 미션이 있었던 날만 `days`에 넣는다. `date`는 `mission_date`. 하루 1개면 `missions`는 원소 1개이고, 하루 여러 개면 슬롯 순으로 여러 개다 — **도장은 미션마다** 따로다(B7 결정).
- `stamp`: `GOOD` / `LATE` / `PENALTY` / `null`(도장 없음 — 9-1 범위 밖).
- 날짜를 누르면 결과 화면(`GET /missions/{id}/submissions`, 피드 잠금 규칙 적용). 내가 제출하지 않은 날은 결과를 볼 수 없다 (앱 현재 동작과 같음).
- 에러: 404 `GROUP_NOT_FOUND`, 403 `NOT_GROUP_MEMBER`, 400 `VALIDATION_ERROR`(month 형식).

```json
// GET /api/me/stats — 17 마이페이지 활동 요약
{ "completedMissions": 42, "streakDays": 5, "groupCount": 3,
  "byMedia": { "DRAW": 10, "PHOTO": 15, "TEXT": 9 } }
```
- `byMedia`는 `DRAW`·`PHOTO`·`TEXT`를 0이어도 항상 넣는다. 2026-10-12 음성 제거로 `VOICE` 키는 빠졌다(§6-1). 지워진 틀의 예전 제출은 `completedMissions`에만 센다.

### 9-3. 수정 대상

| 대상 | 내용 |
|---|---|
| 백엔드 | 그룹 달력 API, 활동 요약 API, 도장 계산(멤버십 시작 시각·미션 닫힘 기준) |
| Android | 15 `RecordCalendarScreen`: `DataStore.records` → API, 도장 3종 표시(지각 도장 추가). 17 활동 요약 → API (`DataStore.stats` 제거) |
| iOS | Android와 같음 (`RecordCalendarView`, `MyPageView`, `DataStore.records/stats`) |
| 설계 문서 | `API_DESIGN.md` §7 교체(그룹 달력·활동 요약, `/me/calendar` 삭제). `PROJECT_OVERVIEW.md` §3 도장(지각 도장 추가)·§3 마이페이지·§11-8(통합 달력 → 그룹별로 결정) |

### 9-4. 미결

- ~~`memberCount`~~ → 9-1에서 결정 (2026-10-12). 정확한 당시 인원이 필요해지면 나갈 때 행을 지우지 않고 나간 시각을 남기는 방식(그룹 나가기 코드·마이그레이션 변경)을 검토한다.
- 나갔다가 다시 들어온 사용자는 다시 들어온 시점부터 도장이 계산된다 (이전 기간은 `null`).
- ~~하루 여러 미션일 때 날짜 단위 도장 규칙(`PROJECT_OVERVIEW` §11-1)~~ — B7에서 **미션별 도장**으로 정함(날짜 단위로 합치지 않음).
- 도장 3종 디자인 (지각 도장 포함). 지금은 임시 이미지: Android `res/drawable/stamp_good|late|penalty.xml`, iOS `Assets.xcassets/Stamps/stamp-good|late|penalty`. 디자인이 나오면 같은 이름으로 교체한다.
- 멤버 목록의 `submissionCountThisMonth`·`stampedToday`는 앱이 쓰지 않는다. 유지·삭제를 반영 시 정한다. (뜻은 B7에서 정함: 제출한 날 수 / 현재 미션 중 하나라도 제출 — §2)

## 10. Group insights (B11) — implemented

- Decision #19: Monthly word counts aggregate text submissions across the group. Every current member may read the aggregate even if they did not submit. Source bodies, comments, captions, and media are excluded from the cache and response. Withdrawal deletes caches for groups touched by that user's submissions, reactions, or comments.
- Decision #20: A closed mission contributes to monthly metrics by `mission_date`. The activity score uses 60% participation, 20% on time, and 20% interaction. Generation is asynchronous and stored in `group_insights`; results expire after 30 minutes until the third day of the following month, and become permanent only after that time when no mission from the month remains open. The previous month is generated at 00:10 Asia/Seoul on the first day of a month and may initially omit its still-open last mission. API fields and 200/202 states are specified in [GROUP_INSIGHTS.md](GROUP_INSIGHTS.md).
