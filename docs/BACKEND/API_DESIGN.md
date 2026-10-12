# HA'WE — API 설계

`docs/BACKEND/BACKEND_DB.md`(DB 설계)를 기준으로 API를 처음부터 다시 설계한 문서. 이 문서는 **설계 전용**이며, 아직 어떤 컨트롤러/서비스 코드도 구현되지 않았다.

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
| 스티커 반응 | 토글 방식 (이미 누른 반응 다시 누르면 취소) | 무한정 중복 반응 방지 |
| 댓글 멘션 | 프론트가 `mentionedUserIds`를 명시적으로 전달 | 서버가 `@닉네임` 텍스트 파싱하는 것보다 안전 (닉네임 중복/특수문자 회피) |
| 그룹장(owner) 탈퇴 | 후임 멤버 지정 후 나가기, 마지막 멤버라면 그룹 삭제 | 소유권을 한 명에게 유지 |

---

## 1. Auth — `/api/auth`

| Method | Path | 인증 | 설명 |
|---|---|---|---|
| POST | `/auth/register` | ✗ | 회원가입 |
| POST | `/auth/login` | ✗ | 로그인 |
| POST | `/auth/refresh` | ✗ | accessToken 재발급 |
| GET | `/me` | ✓ | 내 계정 정보 |

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

`PATCH /me/password`는 2차 범위이며 아직 구현되지 않았다.

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

모든 엔드포인트는 로그인 필요. 초대 코드는 그룹별 6자리이며 만료되지 않는다. 문자 집합은 ABCDEFGHJKLMNPQRSTUVWXYZ23456789이고, 입력 앞뒤 공백과 소문자는 정규화한다. 코드 재발급은 2차 범위(미구현)이다.

| Method | Path | 설명 |
|---|---|---|
| GET | /groups | 내 그룹 목록, 내가 참여한 순서 |
| POST | /groups | 그룹 생성 |
| GET | /groups/{groupId} | 그룹 상세(멤버만) |
| PATCH | /groups/{groupId}/settings | 그룹 설정 변경(그룹장만) |
| GET | /groups/{groupId}/members | 멤버 목록 |
| GET | /invites/{code} | 참여 전 미리보기 |
| POST | /groups/join | 코드로 가입 |
| DELETE | /groups/{groupId}/members/me | 나가기 |

### GET /groups

응답의 각 그룹은 id, name, relationType, memberCount, maxMembers, members, startDate, submittedToday, todayMission을 가진다. members의 각 항목은 userId, nickname, characterKey, profileImageUrl(null)이다. 목록은 내가 참여한 순서, 멤버는 그룹 참여 순서로 정렬한다. todayMission은 현재 미션의 `{missionId, media, content, submittedCount, submittedByMe}` 요약이며 미발행이면 null이다. submittedToday는 `todayMission.submittedByMe`와 같고 미발행이면 false다.

### POST /groups

요청 예시: {"name":"우리 둘","relationType":"COUPLE","startDate":"2026-06-02"}. 201 응답은 상세와 같은 형태다. name은 trim 후 1~16자. relationType 생략 시 CASUAL. startDate는 COUPLE에서만 허용하며 서울 기준 미래 날짜는 400 VALIDATION_ERROR. dailyMissionCount와 missionSlots는 기존 선택 필드로 유지한다.

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

부분 수정: name, relationType, startDate, dailyMissionCount, missionSlots. 응답은 상세와 같다. name은 trim 후 1~16자이며 빈 이름은 400. startDate는 결과 관계가 COUPLE일 때만 보낼 수 있다. 다른 관계로 바꾸면 저장된 날짜는 지운다. 새 관계의 정원보다 현재 인원이 많으면 409 GROUP_FULL. missionSlots는 전체 교체이며 기존 기본 시각 규칙을 따른다. 에러: 403 NOT_GROUP_OWNER.

### GET /groups/{groupId}/members

멤버 응답은 userId, nickname, characterKey, profileImageUrl(null), role, submissionCountThisMonth, stampedToday를 가진다. 그룹 내 프로필과 hasGroupProfile은 2차 범위다.

### GET /invites/{code}

응답은 groupId, name, relationType, memberCount, maxMembers, members(각 userId, nickname, characterKey, profileImageUrl)를 가진다. 형식 오류·없는 코드는 404 INVITE_NOT_FOUND. 이미 멤버면 409 ALREADY_MEMBER, 정원이 차면 409 GROUP_FULL.

### POST /groups/join

요청: {"code":"K7Q2PX"}. 200 응답: {"groupId":1,"name":"우리 둘"}. 미리보기와 같은 오류를 반환하며, 마지막 자리 동시 가입은 그룹 행 잠금으로 직렬화한다.

### DELETE /groups/{groupId}/members/me

일반 멤버는 204. 그룹장에게 다른 멤버가 있으면 successorUserId 쿼리 인자로 같은 그룹의 다른 사용자 ID가 필수다. 누락은 409 SUCCESSOR_REQUIRED, 잘못된 후임자는 400 INVALID_SUCCESSOR. 마지막 멤버가 나가면 그룹과 의존 데이터를 삭제한다. 멤버가 아니면 403 NOT_GROUP_MEMBER.
## 3. Mission & Submission

미션/제출 API는 미션 템플릿(틀) 구조를 도입하면서 별도 문서로 분리했다. 이 문서에서는 다루지 않는다.

- 미션/제출 API 명세: `docs/BACKEND/MISSIONS_API.md`
- 미션 템플릿 목록: `docs/FEATURE/MISSIONS.md`

---

## 4. Comment & Reaction

| Method | Path | 설명 |
|---|---|---|
| GET | `/submissions/{id}/comments` | 댓글 목록 |
| POST | `/submissions/{id}/comments` | 댓글 작성 |
| POST | `/submissions/{id}/reactions` | 스티커 반응 (토글) |

### GET /submissions/{id}/comments
```json
{ "content": [
  { "id": 1, "userId": 3, "nickname": "잎사귀", "content": "...", "imageUrl": null,
    "mentionedUserIds": [1], "createdAt": "..." }
], "page": 0, "size": 20, "totalPages": 1, "totalElements": 2 }
```

### POST /submissions/{id}/comments
```json
// Request
{ "content": "@몽실이 이거 뭐야", "mentionedUserIds": [1], "imageUrl": null }

// Response 201
{ "id": 2, "userId": 3, "nickname": "잎사귀", "content": "@몽실이 이거 뭐야", "imageUrl": null,
  "mentionedUserIds": [1], "createdAt": "..." }
```

### POST /submissions/{id}/reactions
```json
// Request
{ "stickerType": "heart" }

// Response 200 (토글 결과)
{ "stickerType": "heart", "reacted": true, "count": 4 }
```

---

## 5. Poke — `/api/groups/{groupId}/pokes`

| Method | Path | 설명 |
|---|---|---|
| POST | `/groups/{groupId}/pokes` | 콕찌르기/똥뿌리기 전송 |

```json
// Request
{ "toUserId": 7, "type": "poke" }   // type: "poke" | "fling"

// Response 201
{ "id": 1, "toUserId": 7, "type": "poke", "missionId": 10, "sentAt": "2026-09-28T15:41:48+09:00" }
```
현재 미션은 그룹의 가장 작은 슬롯 번호에서 가장 최근 발행된 미션이다. 현재 미션이 없으면 보낼 수 없다. 같은 보낸 사람·받는 사람·미션·종류마다 한 번만 보낼 수 있으므로 `poke`와 `fling`은 각각 한 번 가능하다. 알림 발송은 9단계에서 연결한다.

에러: `GROUP_NOT_FOUND` (404), `NOT_GROUP_MEMBER` (403), `CANNOT_POKE_SELF` (400), `TARGET_NOT_GROUP_MEMBER` (404), `NO_OPEN_MISSION` (409), `TARGET_ALREADY_SUBMITTED` (409), `ALREADY_POKED` (409), `VALIDATION_ERROR` (400).

---

## 6. Notification — `/api/notifications`

| Method | Path | 설명 |
|---|---|---|
| GET | `/notifications` | 내 알림 목록 |
| PATCH | `/notifications/{id}/read` | 읽음 처리 |
| PATCH | `/notifications/read-all` | 전체 읽음 처리 |

### GET /notifications
```json
{ "content": [
  { "id": 1, "type": "poke", "fromUser": { "id": 3, "nickname": "잎사귀" }, "payload": { "groupId": 1 },
    "isRead": false, "createdAt": "..." },
  { "id": 2, "type": "mission_open", "fromUser": null, "payload": { "groupId": 1, "missionId": 10 },
    "isRead": false, "createdAt": "..." }
], "page": 0, "size": 20, "totalPages": 1, "totalElements": 6 }
```
`fromUser`는 시스템 알림(예: `mission_open`)일 경우 `null`.

### PATCH /notifications/{id}/read
Response 204.

### PATCH /notifications/read-all
Response 204.

---

## 7. Calendar (마이페이지) — `/api/me/calendar`

| Method | Path | 설명 |
|---|---|---|
| GET | `/me/calendar?month=2026-09` | 이번 달 도장 현황 |
| GET | `/me/calendar/{date}` | 특정 날짜 상세 |

### GET /me/calendar?month=2026-09
```json
{ "month": "2026-09", "days": [
  { "date": "2026-09-01", "stamp": "GOOD" },
  { "date": "2026-09-02", "stamp": "PENALTY" }
] }
```

### GET /me/calendar/{date}
```json
{ "date": "2026-09-28", "groups": [
  { "groupId": 1, "groupName": "우리집",
    "mission": { "id": 10, "content": "..." },
    "submission": { "id": 100, "imageUrl": "...", "caption": "..." },
    "stamp": "GOOD" }
] }
```

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

### 프로필 사진 (설계만, 미구현)
앱은 프로필로 캐릭터 대신 **내 사진**을 고를 수 있다 (A3 가입, 17-1 프로필 편집). 서버 설계는 아래와 같고 아직 구현하지 않았다.

| Method | Path | 설명 |
|---|---|---|
| PUT | `/me/profile-image` | 프로필 사진 등록·교체 (`multipart/form-data`, 필드 `file`, 이미지 ≤ 10MB `jpg`/`png`/`webp`) |
| DELETE | `/me/profile-image` | 프로필 사진 삭제 (캐릭터로 돌아감) |

```json
// PUT 응답 200
{ "profileImageUrl": "https://.../profile/1.jpg" }
```
- DB: `users.profile_image_url VARCHAR(500) NULL` 추가 (새 마이그레이션).
- 표시 규칙: `profileImageUrl`이 있으면 사진, 없으면 캐릭터. 사진이 있어도 `characterId`는 유지하며 사진을 지우면 그 캐릭터로 돌아간다. `PATCH /me/character`는 사진을 지우지 않는다 — 앱에서 캐릭터를 고르면 `DELETE /me/profile-image`도 함께 호출한다.
- 사용자 정보를 내려주는 모든 응답(멤버 목록, 피드, 댓글, `GET /me`)에 `characterKey`와 `profileImageUrl`을 함께 넣는다.
- 에러: `UNSUPPORTED_FILE_TYPE` (415), `FILE_TOO_LARGE` (413)

---

## 9. 참고

- DB 스키마: `docs/BACKEND/BACKEND_DB.md`
- 서비스 컴포넌트 책임: `docs/BACKEND/BACKEND_DB.md`
- 프로젝트·기능 개요: `docs/FEATURE/PROJECT_OVERVIEW.md`
- 패키지 구조(기능별 controller/service/dto/domain/dao): `CLAUDE.md`
