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
| 그룹 초대 | Stateless 토큰(그룹ID를 서명해서 링크에 담음), 만료 7일 | DB 테이블 불필요, 구현 단순 |
| 스티커 반응 | 토글 방식 (이미 누른 반응 다시 누르면 취소) | 무한정 중복 반응 방지 |
| 댓글 멘션 | 프론트가 `mentionedUserIds`를 명시적으로 전달 | 서버가 `@닉네임` 텍스트 파싱하는 것보다 안전 (닉네임 중복/특수문자 회피) |
| 그룹장(owner) 탈퇴 | 일단 금지 (`OWNER_CANNOT_LEAVE`) | 소유권 이전 정책 미정 — 추후 별도 설계 필요 |

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

## 2. Group — `/api/groups`

| Method | Path | 설명 |
|---|---|---|
| GET | `/groups` | 내 그룹 목록 |
| POST | `/groups` | 그룹 생성 |
| GET | `/groups/{groupId}` | 그룹 상세 |
| PATCH | `/groups/{groupId}/settings` | 그룹 설정 변경 (owner만) |
| GET | `/groups/{groupId}/members` | 멤버 목록 |
| POST | `/groups/{groupId}/invite` | 초대 링크 발급 (owner만) |
| POST | `/groups/join` | 초대 토큰으로 가입 |
| DELETE | `/groups/{groupId}/members/me` | 그룹 나가기 |

### GET /groups
```json
// Response 200
{ "content": [ { "id": 1, "name": "우리집", "submittedToday": false } ], "page": 0, "size": 20, "totalPages": 1, "totalElements": 3 }
```

### POST /groups
```json
// Request
{ "name": "우리집", "relationType": "FAMILY", "dailyMissionCount": 1 }   // relationType(생략 시 CASUAL), dailyMissionCount, missionSlots 모두 선택 (생략 시 하루 1개, 설정 파일의 기본 시각)

// Response 201
{ "id": 1, "name": "우리집", "ownerId": 1, "relationType": "FAMILY", "dailyMissionCount": 1, "missionSlots": [ { "slotNo": 1, "publishTime": "09:00", "deadlineTime": "23:00", "custom": false } ] }
```

### GET /groups/{groupId}
```json
{ "id": 1, "name": "우리집", "ownerId": 1, "relationType": "FAMILY", "dailyMissionCount": 1, "missionSlots": [ { "slotNo": 1, "publishTime": "09:00", "deadlineTime": "23:00", "custom": false } ] }
```
에러: `GROUP_NOT_FOUND` (404), `NOT_GROUP_MEMBER` (403)

### PATCH /groups/{groupId}/settings
`name`/`relationType`/`dailyMissionCount`/`missionSlots` 중 일부만 보내도 됨 (부분 수정).
발행·마감 시각은 그룹이 아니라 슬롯이 가진다. `missionSlots`로 슬롯별 시각을 지정하고, 지정하지 않은 슬롯/시각은 설정 파일(`hawe.mission.*`)의 기본 시각(발행 09:00, 마감 23:00 — 초안)을 쓴다. 하루 미션 수, 슬롯 규칙, 검증은 `docs/BACKEND/MISSIONS_API.md` §2 "그룹 설정 연동" 참고.
```json
// Request (예시: 이름만 변경)
{ "name": "새 이름" }

// Response 200 — 변경된 전체 그룹 정보
{ "id": 1, "name": "새 이름", "ownerId": 1, "relationType": "FAMILY", "dailyMissionCount": 1, "missionSlots": [ { "slotNo": 1, "publishTime": "09:00", "deadlineTime": "23:00", "custom": false } ] }
```
에러: `NOT_GROUP_OWNER` (403)

### GET /groups/{groupId}/members
```json
{ "content": [
  { "userId": 1, "nickname": "몽실이", "role": "owner", "submissionCountThisMonth": 12, "stampedToday": true }
], "page": 0, "size": 20, "totalPages": 1, "totalElements": 4 }
```

### POST /groups/{groupId}/invite
```json
// Response 200
{ "inviteUrl": "hawe://join?token=<서명된 JWT, exp=7일>" }
```
에러: `NOT_GROUP_OWNER` (403)

### POST /groups/join
```json
// Request
{ "token": "..." }

// Response 200
{ "groupId": 1, "name": "우리집" }
```
에러: `INVALID_INVITE_TOKEN` (400), `INVITE_EXPIRED` (400), `ALREADY_MEMBER` (409)

### DELETE /groups/{groupId}/members/me
Response 204.
에러: `OWNER_CANNOT_LEAVE` (409)

---

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
{ "id": 1, "toUserId": 7, "type": "poke", "sentAt": "..." }
```
에러: `TARGET_ALREADY_SUBMITTED` (409), `TARGET_NOT_GROUP_MEMBER` (404)

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
