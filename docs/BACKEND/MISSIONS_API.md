# HA'WE — 미션 / 제출 API 설계 (ver0.1)

`docs/FEATURE/MISSIONS.md`의 미션 템플릿(틀)을 기준으로 미션·제출 API를 설계한 문서. 이 문서는 **설계 전용**이며 아직 구현되지 않았다.
공통 규칙(Base path, 인증, 에러 형식, 페이지네이션, camelCase, ISO-8601)은 `API_DESIGN_ver0.1.md` §0을 따른다.

## 0. 핵심 개념

- **틀(template)**: 매체(사진/음성/글/그림/슬라이더) + 놀이 방식. 코드값으로 식별한다 (예: `TEXT_ACROSTIC`).
- **빈칸(params)**: 틀이 요구하는 입력값 (삼행시는 3글자 제시어, 슬라이더는 양 끝 키워드 등). 틀마다 모양이 다르며 미션에 JSON으로 저장한다.
- **미션**: 그룹 × 날짜마다 1개. `template` + `params` + 사용자에게 보여줄 문장 `content`로 구성된다.
- **제출**: 틀이 요구하는 형식의 답. 미션당 1인 1회. 틀마다 담는 필드(파일/글/숫자/선택)가 다르다.
- **잠금 규칙**: 내가 제출해야 그날의 그룹 피드와 결과를 볼 수 있다 (423 `FEED_LOCKED`).
- **지각**: 그룹의 마감 시각(`mission_deadline_time`) 이후 제출은 `isLate=true`. 제출 자체는 막지 않는다.
- **도장**: 별도 저장 없이 제출 기록 유무로 계산한다 (`FEATURE_SPEC` 도장 규칙).

## 0-1. 이번 설계에서 확정한 것들

| 항목 | 결정 | 사유 |
|---|---|---|
| 제출 횟수 | 미션당 1인 1회 (`ALREADY_SUBMITTED`) | 도장 계산 시 중복 방지 |
| 제출 엔드포인트 | 모든 틀이 `POST /missions/{id}/submissions` 하나를 쓰고, 서버가 `template`으로 필수 필드를 검증 | 틀이 늘어도 API가 늘지 않음 |
| 파일 업로드 | 제출 API가 파일을 직접 받음 (multipart). 개발=로컬 저장, 운영=내부적으로 S3 | API 계약을 바꾸지 않고 저장소만 교체 |
| 파일 응답 필드 | `mediaUrl` + `mediaType`(`IMAGE`/`AUDIO`)로 통일 (기존 `imageUrl`에서 변경) | 음성도 담아야 함 |
| 틀 확장 방식 | `template` 코드 1개 + 검증기 1개 추가 | 컬럼/엔드포인트 추가 최소화 |
| 1차/2차 구분 | 혼자 제출로 끝나는 틀과 집계 틀은 1차, 순서·역할이 필요한 틀은 2차 | 2차는 별도 설계 필요 (§7) |
| 미션 생성 | HTTP API 없음. 스케줄러가 발행 시각에 서버 내부에서 생성 (§6) | 외부에서 임의로 미션을 만들 수 없게 |

---

## 1. 틀(템플릿) 목록

### 1-1. 분류

| 유형 | 설명 | 상태 |
|---|---|---|
| **S** 단독 제출 | 각자 혼자 제출하면 끝 | 1차 |
| **T** 대상 지정 | S와 같되 미션이 특정 멤버(`target`)를 지목 | 1차 |
| **V** 집계 | 제출값을 모아 결과(평균/비율/순위)를 보여줌 | 1차 |
| **R** 순서·역할 | 앞사람 결과에 이어가거나, 출제자/맞히는 사람 역할이 나뉨 | 2차 (§7) |

### 1-2. 코드표

`media`는 사용자가 쓰는 매체, `payload`는 제출에 필요한 필드 (§3-2 참조).

| 매체 | 틀 | `template` | 유형 | `params` | `payload` | 상태 |
|---|---|---|---|---|---|---|
| 사진 | 지금 이 순간 | `PHOTO_NOW` | S | `subject` | `file`(이미지) | 1차 |
| 사진 | 갤러리 발굴 | `PHOTO_GALLERY` | S | `condition` | `file`(이미지) | 1차 |
| 사진 | 색깔 사냥 | `PHOTO_COLOR` | S | `color` | `file`(이미지) | 1차 |
| 음성 | 한 줄 | `VOICE_LINE` | S | `sentence`, `tone` | `file`(음성) | 1차 |
| 음성 | 소리 흉내 | `VOICE_MIMIC` | S | `subject` | `file`(음성) | 1차 |
| 음성 | 성대모사 | `VOICE_IMPRESSION` | S | `subject` | `file`(음성) | 1차 |
| 글 | 삼행시 | `TEXT_ACROSTIC` | S | `word`(정확히 3글자) | `text`(3줄) | 1차 |
| 글 | 한 단어 일기 | `TEXT_ONE_WORD` | S | 없음 | `text`(단어), `caption`(이유) | 1차 |
| 글 | 한 줄 후기 | `TEXT_REVIEW` | S | 없음 | `number`(별점 1~5), `text`(한 줄) | 1차 |
| 글 | 밸런스 게임 | `TEXT_BALANCE` | V | `optionA`, `optionB` | `choice`(`A`/`B`), `caption`(이유, 선택) | 1차 |
| 글 | 빈칸 채우기 | `TEXT_FILL_BLANK` | T | `sentence`(`___` 1개 포함), `targetUserId` | `text` | 1차 ⚠ §8 |
| 그림 | 30초 스케치 | `DRAW_SKETCH` | S/T | `subject` 또는 `targetUserId`, `seconds` | `file`(이미지) | 1차 |
| 그림 | A를 B로 | `DRAW_A_TO_B` | S/T | `a`(또는 `targetUserId`), `b` | `file`(이미지) | 1차 |
| 슬라이더 | 내 위치는? | `SLIDER_POSITION` | V | `question`, `left`, `right` | `number`(0~100) | 1차 |
| 슬라이더 | 취향 막대 | `SLIDER_TASTE` | V | `topic`, `left`, `right` | `number`(0~100) | 1차 |
| 슬라이더 | 다수결 예측 | `SLIDER_CONSENSUS` | V | `question`, `left`, `right` | `number`(내 위치), `guess`(그룹 평균 예측) | 1차 |
| 글 | 끝말 이어쓰기 | `TEXT_RELAY` | R | `startSentence` | 2차 | 2차 |
| 그림 | 이어 그리기 | `DRAW_RELAY` | R | 시작 그림 | 2차 | 2차 |
| 그림 | 그림 맞히기 | `DRAW_GUESS` | R | `word` | 2차 | 2차 |
| 그림 | 갈틱폰 | `DRAW_TELEPHONE` | R | `startSentence` | 2차 | 2차 |
| 슬라이더 | 제시어 바 | `SLIDER_CLUE_BAR` | R | `left`, `right` | 2차 | 2차 |

- `target`을 쓰는 틀(`TEXT_FILL_BLANK`, `DRAW_SKETCH`, `DRAW_A_TO_B`)은 발행 시 서버가 그룹 멤버 중 1명을 무작위로 뽑아 `targetUserId`를 저장한다. 응답에서는 서버가 닉네임을 풀어 `params.target`으로 내려준다 (§2 참조).
- 성대모사(`VOICE_IMPRESSION`), 소리 흉내(`VOICE_MIMIC`)의 "친구들이 맞히기"는 1차에서는 댓글로 처리한다 (별도 기능 없음).
- `SLIDER_CONSENSUS`의 승자는 `guess`와 실제 평균(`number`들의 평균)의 차이가 가장 작은 멤버다. (MISSIONS.md의 설명을 이렇게 해석했다 — §9 확인 필요)

---

## 2. 미션 조회

| Method | Path | 설명 |
|---|---|---|
| GET | `/groups/{groupId}/missions/today` | 오늘의 미션 + 내 제출 여부 |
| GET | `/missions/{missionId}` | 미션 단건 (알림 payload, 달력에서 진입 시) |
| GET | `/mission-templates` | 틀 목록과 빈칸/제출 형식 (프론트 화면 구성용) |

### GET /groups/{groupId}/missions/today
```json
// Response 200 — TEXT_ACROSTIC
{
  "missionId": 10,
  "groupId": 1,
  "content": "\"퇴근길\"로 삼행시 지어줘",
  "category": "감성",
  "template": "TEXT_ACROSTIC",
  "media": "TEXT",
  "params": { "word": "퇴근길" },
  "publishedAt": "2026-09-28T09:00:00",
  "deadlineAt": "2026-09-28T23:00:00",
  "submittedByMe": false
}
```
```json
// Response 200 — SLIDER_POSITION
{
  "missionId": 11, "groupId": 1, "content": "오늘 컨디션은 어디쯤?", "category": "일상",
  "template": "SLIDER_POSITION", "media": "SLIDER",
  "params": { "question": "오늘 컨디션", "left": "방전", "right": "풀충전" },
  "publishedAt": "2026-09-29T09:00:00", "deadlineAt": "2026-09-29T23:00:00", "submittedByMe": true
}
```
`target`을 쓰는 틀은 `params`에 서버가 풀어준 대상이 추가된다.
```json
"params": { "sentence": "___는 무인도에 가면 제일 먼저 ___ 할 것 같다", "target": { "userId": 3, "nickname": "잎사귀" } }
```
- `deadlineAt`이 지나도 미션은 계속 보이고 제출도 가능하다 (제출 시 `isLate=true`).
- 에러: `GROUP_NOT_FOUND` (404), `NOT_GROUP_MEMBER` (403), `MISSION_NOT_PUBLISHED_YET` (404) — 오늘 발행 시각 전이거나 오늘 미션이 아직 생성되지 않음

### GET /missions/{missionId}
응답 형태는 `/missions/today`와 같다.
에러: `MISSION_NOT_FOUND` (404), `NOT_GROUP_MEMBER` (403) — 미션이 속한 그룹의 멤버가 아님

### GET /mission-templates
```json
{ "content": [
  { "template": "TEXT_ACROSTIC", "media": "TEXT", "name": "삼행시", "phase": 1,
    "paramsSchema": { "word": "string(3)" },
    "payloadSchema": { "text": "string(required)" } },
  { "template": "SLIDER_POSITION", "media": "SLIDER", "name": "내 위치는?", "phase": 1,
    "paramsSchema": { "question": "string", "left": "string", "right": "string" },
    "payloadSchema": { "number": "int(0..100, required)" } }
] }
```
정적 목록이라 페이지네이션 봉투를 쓰지 않아도 되지만 목록 응답 규칙(`content`)은 따른다.

---

## 3. 제출

| Method | Path | 설명 |
|---|---|---|
| POST | `/missions/{missionId}/submissions` | 제출 (`multipart/form-data`) |

### 3-1. 요청

`multipart/form-data`. 아래 필드 중 **틀이 요구하는 것만** 보낸다. 요구하지 않는 필드가 오면 무시하지 않고 거부한다 (`INVALID_SUBMISSION`).

| 필드 | 타입 | 설명 |
|---|---|---|
| `file` | file | 이미지 또는 음성 |
| `text` | text | 글 본문 |
| `number` | int | 별점(1~5) 또는 슬라이더 위치(0~100) |
| `guess` | int | `SLIDER_CONSENSUS`의 예측값(0~100) |
| `choice` | text | `A` 또는 `B` (`TEXT_BALANCE`) |
| `caption` | text | 보조 설명 (최대 200자, 틀에 따라 선택/필수) |

### 3-2. 틀별 필수 필드

| `template` | 필수 | 선택 | 검증 |
|---|---|---|---|
| `PHOTO_*`, `DRAW_*` | `file`(이미지) | `caption` | 이미지 MIME |
| `VOICE_*` | `file`(음성) | `caption` | 음성 MIME, 길이 ≤ 20초 |
| `TEXT_ACROSTIC` | `text` | - | 정확히 3줄, 각 줄이 `params.word`의 해당 글자로 시작 |
| `TEXT_ONE_WORD` | `text`(단어, ≤ 20자) | `caption`(이유, ≤ 100자) | 공백 불가 |
| `TEXT_REVIEW` | `number`(1~5), `text`(≤ 100자) | - | - |
| `TEXT_BALANCE` | `choice` | `caption` | `A`/`B` |
| `TEXT_FILL_BLANK` | `text`(≤ 100자) | - | 공백 불가 |
| `SLIDER_POSITION`, `SLIDER_TASTE` | `number`(0~100) | - | 정수 |
| `SLIDER_CONSENSUS` | `number`, `guess` | - | 둘 다 0~100 정수 |

- 시간 제한이 있는 틀(`DRAW_SKETCH`의 `seconds`)은 **클라이언트가 시간을 강제**한다. 서버는 그림 제작 시간을 신뢰할 수 있는 방식으로 알 수 없으므로 검증하지 않는다.
- 파일 제한(초안): 이미지 ≤ 10MB (`jpg`/`png`/`webp`), 음성 ≤ 5MB (`m4a`/`mp3`/`aac`/`webm`). Spring 기본 업로드 한도가 1MB이므로 구현 시 `spring.servlet.multipart.*` 설정이 필요하다.

### 3-3. 응답

```json
// Response 201 — 사진/그림/음성
{ "id": 100, "missionId": 10, "template": "PHOTO_NOW",
  "mediaUrl": "...", "mediaType": "IMAGE", "caption": "...",
  "submittedAt": "2026-09-28T21:00:00", "isLate": false }

// Response 201 — 글
{ "id": 101, "missionId": 10, "template": "TEXT_ACROSTIC",
  "text": "퇴사하고 싶다\n근무시간 끝났는데\n길이 막힌다", "caption": null,
  "submittedAt": "2026-09-28T23:10:00", "isLate": true }

// Response 201 — 슬라이더
{ "id": 102, "missionId": 11, "template": "SLIDER_POSITION",
  "number": 70, "submittedAt": "2026-09-29T10:00:00", "isLate": false }
```
응답에는 해당 틀에서 쓰는 필드만 포함한다 (쓰지 않는 필드는 생략).

에러: `MISSION_NOT_FOUND` (404), `NOT_GROUP_MEMBER` (403), `ALREADY_SUBMITTED` (409), `INVALID_SUBMISSION` (400), `UNSUPPORTED_FILE_TYPE` (415), `FILE_TOO_LARGE` (413)

---

## 4. 피드 / 게시물

| Method | Path | 설명 |
|---|---|---|
| GET | `/missions/{missionId}/submissions` | 그룹 피드 (잠금 규칙 적용) |
| GET | `/submissions/{id}` | 게시물 상세 |

### GET /missions/{missionId}/submissions
- 내가 아직 미제출: **423** `FEED_LOCKED`
- 제출 완료: 페이지네이션 봉투. 항목은 제출 응답의 필드 + 작성자 + 집계.
```json
{ "content": [
  { "id": 100, "userId": 1, "nickname": "몽실이", "template": "PHOTO_NOW",
    "mediaUrl": "...", "mediaType": "IMAGE", "caption": "...",
    "reactionCount": 3, "commentCount": 2, "submittedAt": "...", "isLate": false },
  { "id": 101, "userId": 2, "nickname": "잎사귀", "template": "TEXT_ACROSTIC",
    "text": "...", "reactionCount": 0, "commentCount": 1, "submittedAt": "...", "isLate": true }
], "page": 0, "size": 20, "totalPages": 1, "totalElements": 4 }
```
- 정렬: 제출 시각 오름차순 (같은 시각이면 `id` 오름차순).
- 집계 틀(V)도 피드에서는 개별 제출을 그대로 보여준다. 집계는 §5의 결과 API에서 본다.
- 에러: `MISSION_NOT_FOUND` (404), `NOT_GROUP_MEMBER` (403), `FEED_LOCKED` (423)

### GET /submissions/{id}
```json
{ "id": 100, "missionId": 10, "userId": 1, "nickname": "몽실이", "template": "PHOTO_NOW",
  "mediaUrl": "...", "mediaType": "IMAGE", "caption": "...",
  "submittedAt": "...", "isLate": false,
  "reactions": [ { "stickerType": "heart", "count": 3 } ],
  "commentCount": 5 }
```
- 게시물이 속한 그룹의 멤버이면서 **해당 미션에 내가 제출했을 때만** 볼 수 있다 (잠금 규칙 동일).
- 에러: `SUBMISSION_NOT_FOUND` (404), `NOT_GROUP_MEMBER` (403), `FEED_LOCKED` (423)

---

## 5. 집계 결과 (V 유형)

| Method | Path | 설명 |
|---|---|---|
| GET | `/missions/{missionId}/result` | 집계 틀의 그룹 결과 |

V 유형 틀(`TEXT_BALANCE`, `SLIDER_POSITION`, `SLIDER_TASTE`, `SLIDER_CONSENSUS`)에서만 의미가 있다. 잠금 규칙이 적용되어 **내가 제출해야** 볼 수 있다. 결과는 현재까지의 제출 기준이며, 제출이 늘면 값이 바뀐다.

```json
// TEXT_BALANCE
{ "template": "TEXT_BALANCE", "totalCount": 4,
  "choices": [ { "choice": "A", "count": 3 }, { "choice": "B", "count": 1 } ],
  "myChoice": "A" }

// SLIDER_POSITION / SLIDER_TASTE
{ "template": "SLIDER_POSITION", "totalCount": 4, "average": 62.5,
  "distribution": [ { "userId": 1, "nickname": "몽실이", "number": 70 }, { "userId": 2, "nickname": "잎사귀", "number": 20 } ],
  "myNumber": 70, "diffFromAverage": 7.5 }

// SLIDER_CONSENSUS
{ "template": "SLIDER_CONSENSUS", "totalCount": 4, "average": 62.5,
  "ranking": [ { "userId": 2, "nickname": "잎사귀", "guess": 60, "diff": 2.5 },
               { "userId": 1, "nickname": "몽실이", "guess": 80, "diff": 17.5 } ],
  "winnerUserIds": [2] }
```
- 에러: `MISSION_NOT_FOUND` (404), `NOT_GROUP_MEMBER` (403), `FEED_LOCKED` (423), `RESULT_NOT_SUPPORTED` (400) — V 유형이 아닌 틀

---

## 6. 미션 생성 (내부 처리, HTTP API 없음)

```
[EventBridge 스케줄러] 그룹별 mission_publish_time
   → MissionService.publishDailyMission(groupId, date)
       1. 같은 그룹·날짜에 미션이 이미 있으면 중단 (중복 발행 방지)
       2. 입력 수집: 오늘 날짜/요일, 그룹 관계, 최근 미션(문장·카테고리·틀), 카테고리 목록
       3. AI 호출 (mission-create 프롬프트) → { content, category, template, params }
       4. 서버 검증 (아래), 실패 시 재시도(최대 N회)
       5. 대상 지정 틀이면 그룹 멤버 중 1명을 무작위로 뽑아 params.targetUserId 저장
       6. missions 저장 + 멤버들에게 mission_open 알림
       실패 시: 예비 미션 풀에서 선택 (미션이 안 열리는 날이 없게)
```

AI 응답에 대한 서버 검증 (AI가 규칙을 어길 수 있으므로 프롬프트와 별개로 반드시 수행):

| 검증 | 내용 |
|---|---|
| 틀 | `template`이 1차 코드표에 있는가 (2차 틀은 구현 전까지 제외) |
| 빈칸 | 틀의 `params`가 모두 채워졌는가, 불필요한 키가 없는가 |
| 삼행시 | `word`가 정확히 한글 3글자인가 |
| 밸런스 | `optionA` ≠ `optionB` |
| 슬라이더 | `left` ≠ `right`, 각각 짧은 단어(≤ 20자) |
| 빈칸 채우기 | `sentence`에 `___`가 정확히 1개 |
| 카테고리 | 주어진 목록에 있는 값인가 |
| 연속 방지 | 바로 전 미션과 같은 `template`/문장이 아닌가 |

---

## 7. 2차 틀 (R 유형) — 별도 설계 필요

`TEXT_RELAY`, `DRAW_RELAY`, `DRAW_GUESS`, `DRAW_TELEPHONE`, `SLIDER_CLUE_BAR`는 이 문서의 단일 제출 구조로는 표현할 수 없다.

| 필요한 것 | 이유 |
|---|---|
| 제출 순서 / 차례 관리 | 앞사람의 결과를 받아야 다음 사람이 제출 가능 (이어쓰기, 이어 그리기, 갈틱폰) |
| 역할 | 출제자와 맞히는 사람이 다름 (그림 맞히기, 제시어 바) |
| 한 미션에 제출이 여러 단계 | 갈틱폰은 문장→그림→문장이 한 사람에게서 연속될 수 있음 |
| 하루 안에 끝나는가 | 순서 대기 때문에 마감까지 못 끝날 수 있음 |
| 응답 필드 | 이전 단계 결과를 함께 내려줘야 함 |

→ 1차 출시 후, `submissions`와 별개의 단계 테이블 도입 여부를 포함해 따로 설계한다.

---

## 8. 정책 충돌 / 주의

- **`TEXT_FILL_BLANK` (랜덤 사람 지정 빈칸 채우기)**: MISSIONS.md의 예시 문장은 특정 멤버를 비하하는 내용이다. 이 문서에는 옮기지 않았다. 이 틀은 구조상 **여러 명이 한 명에 대해 쓰는** 형태라 `mission-create-prompt.md`의 규칙과 충돌한다 (웬수: "여러 명이 한 명을 공격하게 되는 미션" 금지, 친구: "특정 친구 한 명을 지목해서 놀리는 미션" 금지, 공통: 혐오·비하 금지, 외모·몸 금지). 사용하려면 (a) 긍정적 문장만 허용, (b) 그룹 관계별 허용 여부, (c) 신고/삭제 수단 중 무엇을 둘지 먼저 정해야 한다.
- **`DRAW_SKETCH`, `DRAW_A_TO_B`의 대상 지정**: "{사용자이름}을 동물로 그리기"는 외모 비하가 되지 않게 주제를 제한해야 한다 (가족, 그저그런사이는 사용하지 않는 것을 권장).
- **`VOICE_IMPRESSION`**: 특정 인물 지정 시 그룹 멤버나 실존 인물의 목소리를 흉내 내는 것이 괜찮은지 정책이 필요하다. 프롬프트의 "다른 사람 목소리가 나오게 하는 미션 금지"와 구분해야 한다.
- **`PHOTO_GALLERY`**: 갤러리 사진에는 얼굴·위치·개인정보가 있을 수 있다. 조건(`condition`)을 만들 때 해당 주제를 피하도록 프롬프트 규칙을 유지한다.
- **`mission-create-prompt.md` 동기화**: 현재 프롬프트의 답변 방식은 PHOTO/VOICE/TEXT/DRAWING/SLIDER 5가지이고 틀(`template`)과 `params`를 돌려주는 형식이 아니다. 이 문서의 틀 코드표에 맞게 출력 형식을 고쳐야 한다. 규칙 변경 시 두 문서를 함께 갱신한다.

---

## 9. DB 변경 (제안, 마이그레이션 V3 이후)

| 테이블 | 변경 |
|---|---|
| `missions` | `template VARCHAR(40) NOT NULL`, `params JSON NOT NULL` 추가. `(group_id, mission_date)` UNIQUE 추가 (중복 발행 방지) |
| `submissions` | `image_url NOT NULL` → `media_url NULL`로 변경. `media_type`, `text_value`, `number_value`, `guess_value`, `choice_value` 추가 (모두 NULL 허용). `(mission_id, user_id)` UNIQUE 추가 (`ALREADY_SUBMITTED`를 DB가 보장, 동시 제출 방지) |

- 기존 `group_members` UNIQUE 제약처럼 **동시 요청에서의 중복은 DB 제약으로 막고** 서비스에서 `DuplicateKeyException`을 `ALREADY_SUBMITTED`로 변환한다.
- `submissions.caption`의 `TEXT` 길이는 서비스에서 제한한다 (200자).

### 다른 문서에 미치는 영향 (이 문서에서는 수정하지 않음)
- `API_DESIGN_ver0.1.md` §7 (Calendar)의 `submission.imageUrl` → `mediaUrl` 계열로 맞춰야 함.
- `API_DESIGN_ver0.1.md` §4 (Comment/Reaction)는 `submissions` 기준이라 그대로 유효.
- `BACKEND_DB_ver0.1.md`의 엔드포인트 목록(§ 미션/제출)과 `DB_DESIGN_ver0.1.md`의 `submissions` 컬럼 설명을 이 문서에 맞게 갱신해야 함.

## 10. 에러 코드 (`MissionErrorCode`, 새 enum — `mission/service/`)

| 코드 | HTTP | 설명 |
|---|---|---|
| `MISSION_NOT_FOUND` | 404 | 미션 없음 |
| `MISSION_NOT_PUBLISHED_YET` | 404 | 오늘 미션이 아직 발행되지 않음 |
| `NOT_GROUP_MEMBER` | 403 | 그룹 멤버가 아님 (`GroupErrorCode` 재사용 또는 동일 코드) |
| `ALREADY_SUBMITTED` | 409 | 이미 제출함 |
| `FEED_LOCKED` | 423 | 내가 제출하기 전에는 피드·결과·상세를 볼 수 없음 |
| `SUBMISSION_NOT_FOUND` | 404 | 제출물 없음 |
| `INVALID_SUBMISSION` | 400 | 틀이 요구하지 않는 필드가 있거나 필수 필드/형식 위반 (메시지에 사유) |
| `UNSUPPORTED_FILE_TYPE` | 415 | 허용하지 않는 파일 형식 |
| `FILE_TOO_LARGE` | 413 | 파일 크기 초과 |
| `RESULT_NOT_SUPPORTED` | 400 | 집계가 없는 틀에 결과 조회 |

## 11. 미정 사항 (구현 전에 결정 필요)

1. AI 판정(`mission-judge-prompt.md`)이 도장에 영향을 주는가? 현재 도장 규칙은 "제출 기록이 있으면 참잘했어요"라 판정 결과를 담을 필드와 정책이 없다.
2. 하루가 끝난 뒤(예: 자정) 제출을 완전히 막을 것인가? 현재는 마감 후에도 `isLate`로 제출 가능하고 상한이 없다.
3. "오늘"의 기준 시간대. DB `CURDATE()`와 서버 시간대를 `Asia/Seoul`로 통일할지 (그룹 API의 `submittedToday`와 동일 문제).
4. 한 그룹 안에서 틀 중복/순환 규칙 (최근 N일 같은 틀 금지 등)과 그룹 관계별 허용 틀 목록.
5. `SLIDER_CONSENSUS`의 승리 판정 해석 확인 (§1-2).
6. 대상 지정 틀에서 대상 본인이 제출할 수 있는가, 대상이 도중에 탈퇴하면 어떻게 표시하는가.
7. 파일 한도·허용 형식의 확정값, 음성 길이 검증 방식 (서버에서 오디오 길이를 읽을 수 있는지).
