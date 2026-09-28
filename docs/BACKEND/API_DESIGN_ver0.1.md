# HA'WE — API 설계 (ver0.1)

`docs/DB/DB_DESIGN_ver0.1.md`(DB 설계)를 기준으로 API를 처음부터 다시 설계한 문서. 이 문서는 **설계 전용**이며, 아직 어떤 컨트롤러/서비스 코드도 구현되지 않았다.

## 0. 공통 규칙

- **Base path**: `/api`
- **인증**: `Authorization: Bearer <accessToken>` 헤더. 회원가입/로그인/토큰 재발급/캐릭터 목록 조회를 제외한 모든 엔드포인트에 필수
- **응답 형식**: 리소스를 별도 래퍼로 감싸지 않고 그대로 반환. 목록 응답은 페이지네이션 봉투 사용:
  ```json
  { "content": [ ... ], "page": 0, "size": 20, "totalPages": 5, "totalElements": 93 }
  ```
- **에러 형식**: 항상 아래 모양 + 적절한 HTTP 상태 코드
  ```json
  { "code": "GROUP_NOT_FOUND", "message": "그룹을 찾을 수 없습니다" }
  ```
- **필드명**: JSON은 camelCase (DB는 snake_case, DAO 매핑 시 변환)
- **시각 표현**: ISO-8601 문자열 (예: `2026-09-28T15:41:48`)
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
| 제출 | 미션당 1인 1회만 허용 (`ALREADY_SUBMITTED`) | 도장 계산 시 중복 방지 |
| 스티커 반응 | 토글 방식 (이미 누른 반응 다시 누르면 취소) | 무한정 중복 반응 방지 |
| 댓글 멘션 | 프론트가 `mentionedUserIds`를 명시적으로 전달 | 서버가 `@닉네임` 텍스트 파싱하는 것보다 안전 (닉네임 중복/특수문자 회피) |
| 그룹장(owner) 탈퇴 | 일단 금지 (`OWNER_CANNOT_LEAVE`) | 소유권 이전 정책 미정 — 추후 별도 설계 필요 |
| 제출 이미지 업로드 | `POST .../submissions`가 계속 파일을 직접 받음 (multipart) | 개발=로컬 저장, 운영 전환 시 내부적으로 S3 업로드로 스위칭. API 계약 자체는 안 바뀌게 해서 presigned URL 전환에 따른 프론트 변경을 피함 |

---

## 1. Auth — `/api/auth`

| Method | Path | 인증 | 설명 |
|---|---|---|---|
| POST | `/auth/register` | ✗ | 회원가입 |
| POST | `/auth/login` | ✗ | 로그인 |
| POST | `/auth/refresh` | ✗ | accessToken 재발급 |

### POST /auth/register
```json
// Request
{ "loginId": "hawe_user", "password": "pw1234", "nickname": "몽실이", "email": "a@b.com" }

// Response 201
{ "id": 1, "loginId": "hawe_user", "nickname": "몽실이" }
```
에러: `LOGIN_ID_TAKEN` (409)

### POST /auth/login
```json
// Request
{ "loginId": "hawe_user", "password": "pw1234" }

// Response 200
{ "accessToken": "...", "refreshToken": "..." }
```
에러: `INVALID_CREDENTIALS` (401)

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
{ "name": "우리집", "missionPublishTime": "09:00", "missionDeadlineTime": "23:00" }

// Response 201
{ "id": 1, "name": "우리집", "ownerId": 1, "missionPublishTime": "09:00", "missionDeadlineTime": "23:00" }
```

### GET /groups/{groupId}
```json
{ "id": 1, "name": "우리집", "ownerId": 1, "missionPublishTime": "09:00", "missionDeadlineTime": "23:00" }
```
에러: `GROUP_NOT_FOUND` (404), `NOT_GROUP_MEMBER` (403)

### PATCH /groups/{groupId}/settings
`name`/`missionPublishTime`/`missionDeadlineTime` 중 일부만 보내도 됨 (부분 수정).
```json
// Request (예시: 이름만 변경)
{ "name": "새 이름" }

// Response 200 — 변경된 전체 그룹 정보
{ "id": 1, "name": "새 이름", "ownerId": 1, "missionPublishTime": "09:00", "missionDeadlineTime": "23:00" }
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

| Method | Path | 설명 |
|---|---|---|
| GET | `/groups/{groupId}/missions/today` | 오늘의 미션 + 내 제출 여부 |
| POST | `/missions/{missionId}/submissions` | 제출 (multipart/form-data) |
| GET | `/missions/{missionId}/submissions` | 그룹 피드 |
| GET | `/submissions/{id}` | 게시물 상세 |

### GET /groups/{groupId}/missions/today
```json
{ "missionId": 10, "content": "오늘 하루 감사했던 일 하나 남기기", "category": "감성", "submittedByMe": false }
```
에러: `MISSION_NOT_PUBLISHED_YET` (404) — 아직 발행 시각 전

### POST /missions/{missionId}/submissions
`multipart/form-data`
| 필드 | 타입 | 설명 |
|---|---|---|
| `image` | file | 첨부 이미지 |
| `caption` | text | 캡션 |

```json
// Response 201
{ "id": 100, "imageUrl": "...", "caption": "...", "submittedAt": "2026-09-28T21:00:00", "isLate": false }
```
에러: `ALREADY_SUBMITTED` (409), `MISSION_NOT_FOUND` (404)

### GET /missions/{missionId}/submissions
- 내가 아직 미제출: **423** `FEED_LOCKED`
- 제출 완료:
```json
{ "content": [
  { "id": 100, "userId": 1, "nickname": "몽실이", "imageUrl": "...", "caption": "...",
    "reactionCount": 3, "commentCount": 2, "submittedAt": "..." }
], "page": 0, "size": 20, "totalPages": 1, "totalElements": 4 }
```

### GET /submissions/{id}
```json
{ "id": 100, "userId": 1, "nickname": "몽실이", "imageUrl": "...", "caption": "...",
  "submittedAt": "...", "isLate": false,
  "reactions": [ { "stickerType": "heart", "count": 3 } ],
  "commentCount": 5 }
```
에러: `SUBMISSION_NOT_FOUND` (404)

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
{ "content": [ { "id": 1, "name": "몽실이", "svgKey": "monsili" } ], "page": 0, "size": 20, "totalPages": 1, "totalElements": 9 }
```

### PATCH /me/character
```json
// Request
{ "characterId": 3 }

// Response 200
{ "characterId": 3, "name": "곰돌이", "svgKey": "bear" }
```
에러: `CHARACTER_NOT_FOUND` (404)

---

## 9. 참고

- DB 스키마: `docs/DB/DB_DESIGN_ver0.1.md`
- 서비스 컴포넌트 책임: `docs/BACKEND/BACKEND_DB_ver0.1.md`
- 기능/화면 매핑: `docs/FEATURE/FEATURE_SPEC_ver0.1.md`
- 패키지 구조(기능별 controller/service/dto/domain/dao): `CLAUDE.md`
