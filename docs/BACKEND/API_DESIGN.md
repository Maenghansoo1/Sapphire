# HA'WE — API 설계

`docs/BACKEND/BACKEND_DB.md`(DB 설계)를 기준으로 작성한 API 계약 문서다. 구현 상태는 각 절의 설명을 따른다.

## 0. 공통 규칙

- **Base path**: `/api`
- **인증**: `Authorization: Bearer <accessToken>` 헤더. 회원가입/로그인/토큰 재발급/캐릭터 목록 조회를 제외한 모든 엔드포인트에 필수
- **응답 형식**: 리소스를 별도 래퍼로 감싸지 않고 그대로 반환. 목록 응ㅍ답은 페이지네이션 봉투 사용:
  ```json
  { "content": [ ... ], "page": 0, "size": 20, "totalPages": 5, "totalElements": 93 }
  ```
- **에러 형식**: 항상 아래 모양 + 적절한 HTTP 상태 코드
  ```json
  { "code": "GROUP_NOT_FOUND", "message": "그룹을 찾을 수 없습니다" }
  ```
- **공통 에러 코드**: `404 NOT_FOUND`, `400 VALIDATION_ERROR`(경로·쿼리 타입 오류, 필수 파라미터 누락 포함), `405 METHOD_NOT_ALLOWED`, `413 FILE_TOO_LARGE`, `500 INTERNAL_ERROR`
- **필드명**: JSON은 camelCase (DB는 snake_case, DAO 매핑 시 변환)
- **시각 표현**: 오프셋을 붙인 ISO-8601 (예: `2026-09-28T09:00:00+09:00`). 서버 시간대는 Asia/Seoul 고정. 날짜는 `yyyy-MM-dd`, 슬롯 시각은 `HH:mm`
- **페이지네이션**: 모든 목록 API는 `page`(0-base) / `size` 쿼리 파라미터의 offset/limit 방식
- **로그아웃 API 없음**: 인증 토큰이 stateless이므로, 클라이언트가 저장된 토큰을 삭제하는 것으로 로그아웃 처리

## 0-1. 이번 설계에서 확정한 것들

| 항목 | 결정 | 사유 |
|---|---|---|
| 응답 포맷 | 래퍼 없이 리소스 그대로 + 표준 에러 필드 | 가장 단순, 프론트 파싱 쉬움 |
| accessToken/refreshToken | Stateless (서명 검증만, DB 미저장) | MVP 단순화. 로그아웃 강제 무효화는 지원 안 함 |
| accessToken 만료 | 30분 | — |
| refreshToken 만료 | 14일, refresh 시마다 회전(rotate) 발급 | — |
| 그룹 초대 | 그룹별 6자리 코드(전체 유일, 만료 없음) | 미리보기와 정원 검사에 사용 |
| 스티커 반응 | `heart`, `laugh`, `wow`, `sad`, `fire` 5종, 종류별 토글 (이미 누른 반응 다시 누르면 취소) | 무한정 중복 반응 방지 |
| 댓글 멘션 | 프론트가 `mentionedUserIds`를 명시적으로 전달 | 서버가 `@닉네임` 텍스트 파싱하는 것보다 안전 (닉네임 중복/특수문자 회피) |
| 그룹장(owner) 탈퇴 | 후임 멤버 지정 후 나가기, 마지막 멤버라면 그룹 삭제 | 소유권을 한 명에게 유지 |

---

## 1. Auth — `/api/auth`

| Method | Path | 인증 | 설명 |
|---|---|---|---|
| POST | `/auth/email-check` | ✗ | 가입 전 이메일 중복 확인 |
| POST | `/auth/register` | ✗ | 회원가입 |
| POST | `/auth/login` | ✗ | 로그인 |
| POST | `/auth/refresh` | ✗ | accessToken 재발급 |
| GET | `/me` | ✓ | 내 계정 정보 |

### POST /auth/email-check
```json
// Request
{ "email": "a@b.com" }

// Response 200
{ "available": true }
```
가입 화면의 "중복 확인" 버튼이 부른다. 이메일은 register 와 같게 앞뒤 공백 제거·소문자 변환 후 확인한다. 이미 가입된 이메일이면 `available: false`. 확인한 뒤 그사이 다른 사람이 가입할 수 있어서 register 도 `EMAIL_TAKEN` 을 다시 검사한다.

에러: `VALIDATION_ERROR` (400, 이메일 형식·최대 255자)

### POST /auth/register
```json
// Request
{ "email": "a@b.com", "password": "abcd1234", "nickname": "몽실이", "agreeTerms": true, "agreePrivacy": true }

// Response 201
{ "id": 1, "email": "a@b.com", "nickname": "몽실이" }
```
이메일은 앞뒤 공백 제거·소문자 변환 후 저장한다. 이메일 형식·최대 255자, 비밀번호 8~100자(영문·숫자 포함), 닉네임 앞뒤 공백 제거 후 2~10자, 필수 약관 동의 2개를 검증한다. 동의 시각은 서버에 저장한다.

에러: `EMAIL_TAKEN` (409), `VALIDATION_ERROR` (400)

### POST /auth/login
```json
// Request
{ "email": "a@b.com", "password": "abcd1234" }

// Response 200
{ "accessToken": "...", "refreshToken": "..." }
```
에러: `INVALID_CREDENTIALS` (401)

이메일은 앞뒤 공백 제거·소문자 변환 후 조회한다. 형식이 틀린 이메일과 존재하지 않는 이메일은 모두 `INVALID_CREDENTIALS`로 처리한다.

### GET /me
```json
// Response 200
{ "id": 1, "email": "a@b.com", "nickname": "몽실이", "characterKey": "gomdol", "profileImageUrl": null, "createdAt": "2026-09-28T09:00:00+09:00" }
```
캐릭터가 선택되지 않았으면 `characterKey`는 `null`이다. 에러: `INVALID_TOKEN` (401).

`PATCH /me/password`는 인증이 필요하다. 요청은 `{ "currentPassword": "abcd1234", "newPassword": "efgh5678" }`이며, 성공 시 200 `{ "accessToken": "...", "refreshToken": "..." }`를 반환한다. 새 비밀번호는 8~100자이며 영문과 숫자를 포함해야 한다. 규칙 위반은 400 `VALIDATION_ERROR`, 현재 비밀번호 불일치는 400 `WRONG_CURRENT_PASSWORD`, 토큰 누락·무효는 401이다. 변경 시 기존 액세스·리프레시 토큰은 모두 `INVALID_TOKEN`으로 거절된다. 요청 기기는 응답의 새 토큰을 저장해야 한다.

### 내 프로필과 회원 탈퇴

| Method | Path | 설명 |
|---|---|---|
| PATCH | `/me/profile` | 기본 닉네임 변경, 응답은 `GET /me`와 동일 |
| PUT | `/me/profile-image` | 기본 프로필 사진 등록·교체 (§8) |
| DELETE | `/me/profile-image` | 기본 프로필 사진 삭제, 204 |
| DELETE | `/me` | 회원 탈퇴, 204 |

`PATCH /me/profile` 요청은 `{ "nickname": "하늘" }`이며, 앞뒤 공백을 제거한 뒤 2~10자를 허용한다(400 `VALIDATION_ERROR`). 탈퇴 전에 다른 멤버가 있는 그룹의 그룹장은 후임자를 정해 그룹을 나가야 한다(409 `OWNER_MUST_TRANSFER`). 탈퇴는 내 제출물·댓글·반응·정답 시도·콕·알림과 계정을 삭제한다. 내 제출물에 달린 다른 사람의 댓글·반응도 삭제한다. 혼자 소유한 그룹은 삭제된다. 삭제된 계정의 액세스 토큰과 리프레시 토큰은 401 `INVALID_TOKEN`으로 거절한다.

### POST /auth/refresh
```json
// Request
{ "refreshToken": "..." }

// Response 200 (refreshToken도 회전 발급)
{ "accessToken": "...", "refreshToken": "..." }
```
에러: `INVALID_TOKEN` (401), `TOKEN_EXPIRED` (401)

---

## 2. Group — /api/groups, /api/invites

모든 엔드포인트는 로그인 필요. 초대 코드는 그룹별 6자리이며 만료되지 않는다. 문자 집합은 ABCDEFGHJKLMNPQRSTUVWXYZ23456789이고, 입력 앞뒤 공백과 소문자는 정규화한다. 그룹장은 코드를 재발급할 수 있으며 이전 코드는 즉시 무효다.

| Method | Path | 설명 |
|---|---|---|
| GET | /groups | 내 그룹 목록, 내가 참여한 순서 |
| POST | /groups | 그룹 생성 |
| GET | /groups/{groupId} | 그룹 상세(멤버만) |
| PATCH | /groups/{groupId}/settings | 그룹 설정 변경(그룹장만) |
| GET | /groups/{groupId}/members | 멤버 목록 |
| PUT | /groups/{groupId}/members/me/profile | 내 그룹 프로필을 캐릭터로 설정·교체 |
| PUT | /groups/{groupId}/members/me/profile-image | 내 그룹 프로필을 사진으로 설정·교체 |
| DELETE | /groups/{groupId}/members/me/profile | 내 그룹 프로필 해제 |
| GET | /invites/{code} | 참여 전 미리보기 |
| POST | /groups/join | 코드로 가입 |
| POST | /groups/{groupId}/invite-code | 초대 코드 재발급(그룹장만) |
| DELETE | /groups/{groupId}/members/me | 나가기. 마지막 멤버가 나가 그룹이 삭제되면 관련 업로드 파일도 커밋 후 삭제 |

### GET /groups

응답의 각 그룹은 id, name, relationType, memberCount, maxMembers, members, startDate, submittedToday, todayMission, todayMissions를 가진다. members의 각 항목은 userId, nickname, characterKey, profileImageUrl(null)이다. 목록은 내가 참여한 순서, 멤버는 그룹 참여 순서로 정렬한다. todayMission은 현재 미션의 `{missionId, media, content, submittedCount, submittedByMe}` 요약이며 미발행이면 null이다. 하루 미션이 여러 개면 todayMission은 첫 슬롯(현재 미션 중 가장 작은 slotNo)이고, todayMissions(B7)는 현재 미션 요약 전부를 slotNo 순으로 담는다(없으면 빈 배열). submittedToday는 `todayMission.submittedByMe`와 같고 미발행이면 false다.

### POST /groups

요청 예시: {"name":"우리 둘","relationType":"COUPLE","startDate":"2026-06-02"}. 201 응답은 상세와 같은 형태다. name은 trim 후 1~16자. relationType 생략 시 CASUAL. startDate는 COUPLE에서만 허용하며 서울 기준 미래 날짜는 400 VALIDATION_ERROR. dailyMissionCount(1~5, 기본 1)와 missionSlots는 기존 선택 필드로 유지한다.

### GET /groups/{groupId}

응답 예시:

    {"id":1,"name":"우리 둘","ownerId":1,"relationType":"COUPLE",
     "createdAt":"2026-06-02T10:00:00+09:00","memberCount":2,"maxMembers":2,
     "inviteCode":"K7Q2PX","inviteUrl":"https://hawe.app/invite/K7Q2PX",
     "startDate":"2026-06-02","dailyMissionCount":1,
     "missionSlots":[{"slotNo":1,"publishTime":"09:00","deadlineTime":"23:00","custom":false}],
     "missionCount":0}

에러: 404 GROUP_NOT_FOUND, 403 NOT_GROUP_MEMBER. 초대 URL 도메인은 임시 설정이다.

### PATCH /groups/{groupId}/settings

부분 수정: name, relationType, startDate, dailyMissionCount, missionSlots. 응답은 상세와 같다. name은 trim 후 1~16자이며 빈 이름은 400. startDate는 결과 관계가 COUPLE일 때만 보낼 수 있다. 다른 관계로 바꾸면 저장된 날짜는 지운다. 새 관계의 정원보다 현재 인원이 많으면 409 GROUP_FULL. missionSlots는 전체 교체이며 기존 기본 시각 규칙을 따른다. dailyMissionCount는 1~5(`hawe.mission.max-daily-count`)이고, 줄이면 없어진 슬롯에 이미 열린 미션은 그날 유지되다가 다음 날 첫 발행에서 닫힌다(`MISSIONS_API.md` §2). 에러: 403 NOT_GROUP_OWNER.

### GET /groups/{groupId}/members

멤버 응답은 userId, nickname, characterKey, profileImageUrl, hasGroupProfile, role, submissionCountThisMonth, stampedToday를 가진다. 사진이 없으면 `profileImageUrl`은 null이다. `hasGroupProfile`은 그룹 내 프로필 설정 여부다. `submissionCountThisMonth`는 이번 달 제출한 날 수(하루에 여러 개 내도 1), `stampedToday`는 현재 미션 중 하나라도 제출했는지다(B7).

### 그룹 내 프로필

`PUT /groups/{groupId}/members/me/profile`은 JSON `{ "nickname": "과대표 하늘", "characterId": 3 }`을 받아 200 프로필 객체 `{ userId, nickname, characterKey, profileImageUrl }`를 반환한다. `PUT /groups/{groupId}/members/me/profile-image`는 multipart `file`(이미지 1개)과 `nickname`을 받아 같은 객체를 반환한다. 닉네임은 trim 후 2~10자, 이미지는 10MB 이하의 지원 형식이어야 한다. `DELETE /groups/{groupId}/members/me/profile`은 기본 프로필로 되돌리고 204를 반환하며 반복 호출할 수 있다. 없는 그룹은 404 `GROUP_NOT_FOUND`, 비멤버는 403 `NOT_GROUP_MEMBER`다.

그룹 프로필은 닉네임과 캐릭터 또는 사진을 한 벌로 선택한다. 설정된 그룹에서는 멤버 목록, 그룹 목록·상세, 초대 미리보기, 피드·제출 상세, 댓글의 사람 표시가 그룹 프로필을 쓴다. 설정되지 않은 그룹은 기본 프로필을 쓴다. 기본 프로필을 변경해도 설정된 그룹 프로필은 유지된다. 작성자가 그룹을 나가면 남아 있는 피드·댓글에는 기본 프로필이 표시된다. 나간 멤버의 그룹 프로필 사진은 커밋 후 삭제하며 작품·댓글 파일은 그룹에 남는다. 마지막 멤버가 나가 그룹이 삭제되면 제출물·댓글 이미지·그룹 프로필 사진도 커밋 후 삭제한다(B4).

### GET /invites/{code}

응답은 groupId, name, relationType, memberCount, maxMembers, members(각 userId, nickname, characterKey, profileImageUrl)를 가진다. 형식 오류·없는 코드는 404 INVITE_NOT_FOUND. 이미 멤버면 409 ALREADY_MEMBER, 정원이 차면 409 GROUP_FULL.

미리보기와 코드 가입은 사용자별로 코드 조회 실패를 공유해 센다. 형식 오류와 없는 코드는 실패이며, 올바른 코드의 `ALREADY_MEMBER`·`GROUP_FULL`은 실패로 세지 않는다. 10분 안에 실패 10회가 쌓이면 다음 요청부터 429 `TOO_MANY_REQUESTS`를 반환한다. 유효한 코드로 요청해도 차단되며, 차단 요청은 실패 횟수를 늘리지 않는다. 성공은 실패 횟수를 초기화하지 않는다. 제한기는 서버 메모리에 있으므로 단일 인스턴스를 가정하며, 여러 인스턴스로 확장할 때 재검토해야 한다.

### POST /groups/{groupId}/invite-code

그룹장만 재발급할 수 있다. 200 응답은 `{ "inviteCode": "M8R3TQ", "inviteUrl": "https://hawe.app/invite/M8R3TQ" }`이며 이전 코드는 즉시 404 `INVITE_NOT_FOUND`가 된다. 없는 그룹은 404 `GROUP_NOT_FOUND`, 비멤버는 403 `NOT_GROUP_MEMBER`, 일반 멤버는 403 `NOT_GROUP_OWNER`다.

### POST /groups/join

요청: {"code":"K7Q2PX"}. 200 응답: {"groupId":1,"name":"우리 둘"}. 미리보기와 같은 오류를 반환하며, 마지막 자리 동시 가입은 그룹 행 잠금으로 직렬화한다.

### DELETE /groups/{groupId}/members/me

일반 멤버는 204. 그룹장에게 다른 멤버가 있으면 successorUserId 쿼리 인자로 같은 그룹의 다른 사용자 ID가 필수다. 누락은 409 SUCCESSOR_REQUIRED, 잘못된 후임자는 400 INVALID_SUCCESSOR. 마지막 멤버가 나가면 그룹과 의존 데이터를 삭제한다. 멤버가 아니면 403 NOT_GROUP_MEMBER.
## 3. Mission & Submission

미션/제출 API는 미션 템플릿(틀) 구조를 도입하면서 별도 문서로 분리했다. 이 문서에서는 다루지 않는다.

- 미션/제출 API 명세: `docs/BACKEND/MISSIONS_API.md`
- 미션 템플릿 목록: `docs/FEATURE/MISSIONS.md`
- B5(2026-10-13): 새 틀(한 단어·한 줄 후기·밸런스, 슬라이더 3종은 2026-10-19 삭제) 제출 규칙은 `MISSIONS_API.md` §3-2, 집계 결과 `GET /missions/{missionId}/result`는 §5, 발행 풀 설정 `hawe.mission.enabled-templates`는 §6-1
- B7(2026-10-13): 하루 미션 여러 개(1~5) — 설정·현재 미션 규칙은 `MISSIONS_API.md` §2, 같은 날 틀·문장 겹침 방지는 §6·§6-1, 결정 사항은 §11
- B8(2026-10-13): 그룹 맞춤 AI 미션 문장(Claude API, 기본 꺼짐) — `MISSIONS_API.md` §6·§6-2. HTTP API 모양은 바뀌지 않는다
- B9(2026-10-13): AI 주제 vs 랜덤 주제 비교 실험(기본 꺼짐)과 내부 리포트 `GET /internal/experiments/topic`(헤더 `X-Admin-Token`) — `MISSIONS_API.md` §6-3, `EXPERIMENT_TOPIC.md`. 앱용 API 모양은 바뀌지 않는다

---

## 4. Comment & Reaction

| Method | Path | 설명 |
|---|---|---|
| GET | `/submissions/{id}/comments` | 댓글 목록 |
| POST | `/submissions/{id}/comments` | 댓글 작성 |
| DELETE | `/comments/{commentId}` | 내 댓글 삭제 |
| POST | `/submissions/{id}/reactions` | 스티커 반응 (토글) |

### GET /submissions/{id}/comments
`page` 기본 0, `size` 기본 20(최대 100). 작성 시각, id 오름차순.
```json
{ "content": [
  { "id": 1, "author": { "userId": 3, "nickname": "잎사귀", "characterKey": "ipsagwi", "profileImageUrl": null },
    "content": "...", "imageUrl": null, "mentionedUserIds": [1], "createdAt": "...", "mine": false }
], "page": 0, "size": 20, "totalPages": 1, "totalElements": 2 }
```

### POST /submissions/{id}/comments
`multipart/form-data`: `content`(trim 후 1~300자), `mentionedUserIds`(반복 필드), `file`(선택, jpg/png/webp ≤ 10MB). 본문 또는 이미지가 필요하며 이미지만 있으면 `content`는 빈 문자열이다. 멘션은 같은 그룹의 멤버만 지정할 수 있다.
```json
// Response 201
{ "id": 2, "author": { "userId": 3, "nickname": "잎사귀", "characterKey": "ipsagwi", "profileImageUrl": null },
  "content": "@몽실이 이거 뭐야", "imageUrl": null, "mentionedUserIds": [1], "createdAt": "...", "mine": true }
```

### DELETE /comments/{commentId}
본인 댓글만 삭제할 수 있다. 응답 204. 첨부 이미지도 삭제한다.

### POST /submissions/{id}/reactions
```json
// Request
{ "stickerType": "heart" }

// Response 200 (토글 후 이 제출의 전체 반응; 0개인 종류는 생략)
{ "reactions": [
  { "stickerType": "heart", "count": 2, "reactedByMe": true, "userIds": [1, 3] },
  { "stickerType": "fire", "count": 1, "reactedByMe": false, "userIds": [5] }
] }
```
피드·상세의 `reactions`도 같은 배열이다. 종류 순서는 `heart`, `laugh`, `wow`, `sad`, `fire`이다. 피드·상세의 `commentCount`는 실제 댓글 수다. 작성·목록·반응은 모두 해당 미션에 제출한 그룹 멤버만 접근할 수 있다(403 `NOT_GROUP_MEMBER`, 423 `FEED_LOCKED`). 없는 제출은 404 `SUBMISSION_NOT_FOUND`, 없는 댓글은 404 `COMMENT_NOT_FOUND`, 남의 댓글 삭제는 403 `NOT_COMMENT_OWNER`이다. 잘못된 스티커·본문·멘션은 400 `VALIDATION_ERROR`, 파일 형식과 크기 오류는 각각 415 `UNSUPPORTED_FILE_TYPE`, 413 `FILE_TOO_LARGE`이다.

---

## 5. Poke — `/api/groups/{groupId}/pokes`

| Method | Path | 설명 |
|---|---|---|
| POST | `/groups/{groupId}/pokes` | 콕찌르기 전송 |

```json
// Request
{ "toUserId": 7, "type": "poke", "missionId": 10 }   // type: "poke"만 (똥뿌리기 "fling"은 2026-10-19 삭제, 보내면 400), missionId: 선택

// Response 201
{ "id": 1, "toUserId": 7, "type": "poke", "missionId": 10, "sentAt": "2026-09-28T15:41:48+09:00" }
```
`missionId`를 보내지 않으면 그룹의 현재 미션 중 가장 작은 슬롯 번호의 미션에 보낸다. 보내면(B7) 그 미션이 그 그룹의 현재 미션이어야 한다. 현재 미션이 없거나 보낸 `missionId`가 현재 미션이 아니면 409 `NO_OPEN_MISSION`. 같은 보낸 사람·받는 사람·미션마다 콕은 한 번만 보낼 수 있다. 알림 발송은 9단계에서 연결한다.

에러: `GROUP_NOT_FOUND` (404), `NOT_GROUP_MEMBER` (403), `CANNOT_POKE_SELF` (400), `TARGET_NOT_GROUP_MEMBER` (404), `NO_OPEN_MISSION` (409), `TARGET_ALREADY_SUBMITTED` (409), `ALREADY_POKED` (409), `VALIDATION_ERROR` (400).

---

## 6. Notification — `/api/notifications`

| Method | Path | 설명 |
|---|---|---|
| GET | `/notifications` | 내 알림 목록 |
| GET | `/notifications/unread-count` | 안 읽은 알림 개수 |
| PATCH | `/notifications/{id}/read` | 읽음 처리 |
| PATCH | `/notifications/read-all` | 전체 읽음 처리 |

### GET /notifications
```json
{ "content": [
  { "id": 1, "type": "poke", "groupId": 1, "groupName": "컴공 동기",
    "fromUser": { "userId": 3, "nickname": "서연", "characterKey": "mulbangul", "profileImageUrl": null },
    "missionId": 10, "submissionId": null, "body": "서연님이 콕 찔렀어요",
    "isRead": false, "createdAt": "2026-09-28T15:41:48+09:00" }
], "page": 0, "size": 20, "totalPages": 1, "totalElements": 6 }
```
모든 그룹의 알림을 최신순으로 합친다. `page`는 0 이상, `size`는 1~100으로 조정한다.
종류는 `topic`, `poke`, `comment`, `mention`이다. `topic`의 `fromUser`는 `null`이다.
`friend_done`·푸시·기기 토큰·알림 설정·그룹별 알림은 아직 구현하지 않았다.

### GET /notifications/unread-count
```json
{ "unreadCount": 3 }
```

### PATCH /notifications/{id}/read
Response 204.
남의 알림이나 없는 id는 `NOTIFICATION_NOT_FOUND` (404). 이미 읽은 알림도 204.

### PATCH /notifications/read-all
Response 204.

---

## 7. Calendar · 활동 요약 — `/api/groups/{groupId}/calendar`, `/api/me/stats`

달력은 **그룹별**이다 (마이페이지 통합 달력 `/me/calendar`는 폐지, `CONTRACT_DECISIONS.md` §9).

| Method | Path | 설명 |
|---|---|---|
| GET | `/groups/{groupId}/calendar?month=2026-09` | 그룹 달력 (멤버만) |
| GET | `/me/stats` | 마이페이지 활동 요약 |

### GET /groups/{groupId}/calendar?month=2026-09
```json
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
- 미션이 있었던 날만 `days`에 넣는다. `date`는 `mission_date`. 하루 여러 개를 대비해 `missions`는 배열이다.
- `content`는 `[대상]` 자리를 대상 닉네임으로 채운 문장이다 (미션 조회와 같음).
- `stamp`는 **요청한 사람의 도장**이다. 저장하지 않고 조회할 때 계산한다.

| 상황 | `stamp` |
|---|---|
| 마감 전에 제출 | `GOOD` |
| 마감 뒤에 제출 (`isLate`) | `LATE` |
| 미제출 + 미션이 닫힘(같은 슬롯에 다음 미션이 열림) + 미션이 열릴 때 이미 멤버였음 | `PENALTY` |
| 그 밖 (아직 제출할 수 있는 현재 미션, 미션이 열린 뒤에 들어옴) | `null` |

- `memberCount` = 미션이 열릴 때 이미 들어와 있던 **현재 멤버** + 그 미션에 **제출한 사람**(나간 사람 포함). 나가면 멤버 행이 지워지므로, 제출하지 않고 나간 사람은 셀 수 없다.
- "미션이 열릴 때 멤버였음"은 `joined_at <= published_at + 1분`으로 판단한다. 그룹 생성 직후 첫 미션의 발행 시각과 그룹장 가입 시각이 몇 초 어긋날 수 있기 때문이다.
- 에러: 404 `GROUP_NOT_FOUND`, 403 `NOT_GROUP_MEMBER`, 400 `VALIDATION_ERROR`(month 누락·형식 오류)

### GET /me/stats
```json
{ "completedMissions": 42, "streakDays": 5, "groupCount": 3,
  "byMedia": { "DRAW": 10, "PHOTO": 15, "TEXT": 9 } }
```
- `completedMissions`: 내 제출 수 (지각 포함, 나간 그룹의 제출도 포함)
- `streakDays`: 오늘(오늘 아직 안 냈으면 어제)부터 거꾸로 하루도 빠짐없이 제출한 날 수. 모든 그룹 합산, `mission_date` 기준
- `groupCount`: 지금 속한 그룹 수
- `byMedia`: 유형별 제출 수. 세 유형(`DRAW`, `PHOTO`, `TEXT`)은 0이어도 항상 들어 있다. 음성 제거(2026-10-12)·슬라이더 제거(2026-10-19)로 `VOICE`·`SLIDER` 키는 없어졌고, 지워진 틀의 예전 제출은 `completedMissions`에만 세고 `byMedia`에서는 빠진다

---

## 8. Character

| Method | Path | 설명 |
|---|---|---|
| GET | `/characters` | 선택 가능한 캐릭터 목록 (인증 불필요) |
| PATCH | `/me/character` | 내 프로필 캐릭터 변경 |

### GET /characters
```json
{ "content": [ { "id": 1, "name": "몽실이", "svgKey": "mongsil" } ], "page": 0, "size": 20, "totalPages": 1, "totalElements": 7 }
```
- 7종: `mongsil`(몽실이), `ipsagwi`(잎사귀), `gomdol`(곰돌이), `mulbangul`(물방울), `gamja`(감자), `kkotnim`(꽃님), `pilleum`(필름이). `svgKey`는 iOS·Android 앱의 캐릭터 키(`ProfileCharacter`)와 같다 (V5 마이그레이션). 목록은 `id` 오름차순이며, 앱은 자체 표시 순서를 쓰고 `svgKey`로 대응한다.
- 캐릭터를 고르지 않은 사용자(`users.character_id` NULL)는 앱에서 기본 캐릭터 `mongsil`로 보인다.

### PATCH /me/character
```json
// Request
{ "characterId": 3 }

// Response 200
{ "characterId": 3, "name": "곰돌이", "svgKey": "gomdol" }
```
에러: `CHARACTER_NOT_FOUND` (404)

### 프로필 사진 (백엔드 구현됨)
앱은 프로필로 캐릭터 대신 **내 사진**을 고를 수 있다 (A3 가입, 17-1 프로필 편집).

| Method | Path | 설명 |
|---|---|---|
| PUT | `/me/profile-image` | 프로필 사진 등록·교체 (`multipart/form-data`, 필드 `file`, 이미지 ≤ 10MB `jpg`/`png`/`webp`) |
| DELETE | `/me/profile-image` | 프로필 사진 삭제 (캐릭터로 돌아감) |

```json
// PUT 응답 200
{ "profileImageUrl": "/files/profiles/uuid.jpg" }
```
- DELETE 응답은 204이며 사진이 없어도 성공한다. `file` 파트가 없거나 비어 있으면 400 `VALIDATION_ERROR`.
- DB: `users.profile_image_url VARCHAR(500) NULL`에 파일 key를 저장한다(V1 스키마).
- 표시 규칙: `profileImageUrl`이 있으면 사진, 없으면 캐릭터. 사진이 있어도 `characterId`는 유지하며 사진을 지우면 그 캐릭터로 돌아간다. `PATCH /me/character`는 사진을 지우지 않는다 — 앱에서 캐릭터를 고르면 `DELETE /me/profile-image`도 함께 호출한다.
- 사용자 정보를 내려주는 모든 응답(멤버 목록, 피드, 댓글, `GET /me`)에 `characterKey`와 `profileImageUrl`을 함께 넣는다.
- 에러: `UNSUPPORTED_FILE_TYPE` (415), `FILE_TOO_LARGE` (413)

---

## 9. Group insights — `/api/groups/{groupId}/insights`

`GET /groups/{groupId}/insights?month=YYYY-MM` is member only. Month defaults to this month in Asia/Seoul and may be up to 12 months old. It returns 202 `{groupId, month, status:"GENERATING", retryAfterSeconds:3}` during generation and 200 READY with `stale`, `empty`, `generatedAt`, `activityIndex`, `fastest`, `topWords`, `mostReacted`, and `highlights`. A current month expired cache is returned as `stale:true` while refreshing. Invalid month is 400, nonmember 403, unknown group 404. See [GROUP_INSIGHTS.md](GROUP_INSIGHTS.md) for the metric and privacy contract.

## 10. 참고

- DB 스키마: `docs/BACKEND/BACKEND_DB.md`
- 서비스 컴포넌트 책임: `docs/BACKEND/BACKEND_DB.md`
- 프로젝트·기능 개요: `docs/FEATURE/PROJECT_OVERVIEW.md`
- 패키지 구조(기능별 controller/service/dto/domain/dao): `CLAUDE.md`
