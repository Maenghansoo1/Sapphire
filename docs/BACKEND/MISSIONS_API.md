# HA'WE — 미션 / 제출 API 설계

`docs/FEATURE/MISSIONS.md`의 미션 템플릿(틀)을 기준으로 미션·제출 API를 설계한 문서. 미션 조회와 임시 생성기는 구현되었고 제출·피드는 아직 설계 단계다.
공통 규칙(Base path, 인증, 에러 형식, 페이지네이션, camelCase, ISO-8601)은 `API_DESIGN.md` §0을 따른다.

## 0. 핵심 개념

- **틀(template)**: 매체(사진/글/그림/슬라이더) + 놀이 방식. 음성 매체는 2026-10-12 결정으로 제거되었다. 코드값으로 식별한다 (예: `TEXT_ACROSTIC`).
- **빈칸(params)**: 틀이 요구하는 입력값 (삼행시는 3글자 제시어, 슬라이더는 양 끝 키워드 등). 틀마다 모양이 다르며 미션에 JSON으로 저장한다.
- **미션**: 그룹 × 날짜 × 슬롯마다 1개. `template` + `params` + 사용자에게 보여줄 문장 `content`로 구성된다.
- **슬롯(slot)**: 하루에 열리는 미션의 순번 (`slotNo` 1, 2, 3 …). 그룹이 정한 "하루 미션 수"(`dailyMissionCount`)만큼 슬롯이 있다.
- **발행 시각 규칙**: 슬롯마다 발행·마감 시각은 `group_mission_slots`(그룹 설정의 `missionSlots`)가 정한다. **지정하지 않은 슬롯(행이 없거나 값이 NULL)은 설정 파일(`hawe.mission.*`)의 기본 시각(발행 09:00, 마감 23:00 — 초안)을 쓴다.** 그룹 자체에는 발행·마감 시각 필드가 없다 (§2 "그룹 설정 연동").
- **제출**: 틀이 요구하는 형식의 답. 미션당 1인 1회. 틀마다 담는 필드(파일/글/숫자/선택)가 다르다.
- **잠금 규칙**: 내가 제출해야 그날의 그룹 피드와 결과를 볼 수 있다 (423 `FEED_LOCKED`).
- **지각**: 마감 시각(`deadlineAt`) 이후, 같은 슬롯의 다음 미션이 발행되기 전까지 제출하면 `isLate=true`. 다음 미션 발행 순간 이전 미션은 닫힌다. 제출 종료 판정은 6단계에서 구현한다. 하루 미션 수를 줄여 없어진 슬롯의 미션은 그룹의 다음 날 첫 발행에서 닫힌다 (§2 "그룹 설정 연동", B7).
- **도장**: 별도 저장 없이 제출 기록 유무로 계산한다 (`PROJECT_OVERVIEW.md` 핵심 규칙). 하루에 미션이 여러 개면 **미션마다** 도장을 계산한다 — 달력의 그날 `missions[]`에 미션별 도장 (§11-8, B7 결정).

## 0-1. 이번 설계에서 확정한 것들

| 항목 | 결정 | 사유 |
|---|---|---|
| 제출 횟수 | 미션당 1인 1회 (`ALREADY_SUBMITTED`) | 도장 계산 시 중복 방지 |
| 제출 엔드포인트 | 모든 틀이 `POST /missions/{id}/submissions` 하나를 쓰고, 서버가 `template`으로 필수 필드를 검증 | 틀이 늘어도 API가 늘지 않음 |
| 파일 업로드 | 제출 API가 파일을 직접 받음 (multipart). 개발=로컬 저장, 운영=내부적으로 S3 | API 계약을 바꾸지 않고 저장소만 교체 |
| 파일 응답 필드 | `mediaUrl` + `mediaType`로 통일 (기존 `imageUrl`에서 변경). 음성 제거 후 `mediaType`은 `IMAGE`만 쓴다 | 매체가 늘어도 같은 필드로 담기 위해 |
| 틀 확장 방식 | `template` 코드 1개 + 검증기 1개 추가 | 컬럼/엔드포인트 추가 최소화 |
| 1차/2차 구분 | 혼자 제출로 끝나는 틀과 집계 틀은 1차, 순서·역할이 필요한 틀은 2차 | 2차는 별도 설계 필요 (§7) |
| 미션 생성 | HTTP API 없음. 스케줄러가 슬롯별 발행 시각에 서버 내부에서 생성 (§6) | 외부에서 임의로 미션을 만들 수 없게 |
| AI의 역할 | 미션 문장을 생성한다. 사진 틀은 별도 비전 서비스가 적합도 점수를 만들고 서버가 긍정 배지·문구를 판정한다 | 점수는 비공개이며 도장은 제출 기록만으로 계산한다. `VISION_TOPIC_FIT.md` 참고 |
| ~~성대모사·소리 흉내~~ | **폐기됨 (2026-10-12, 음성 기능 제거).** 정답 맞히기 틀이 없어져 정답 시도 API도 만들지 않는다 | 음성 기능을 서비스에서 완전히 제거하기로 결정 |
| 하루 미션 수 | 그룹이 1~5개로 정한다 (`max-daily-count=5`, 기본 1 — B7 2026-10-13) | 지금 앱은 이 값을 보내지 않아 하루 1개. 앱의 여러 미션 화면은 A6 |
| 발행·마감 시각 | 슬롯별 시각(`group_mission_slots`)만 사용. 지정이 없으면 설정 파일(`hawe.mission.*`)의 기본 시각(발행 09:00, 마감 23:00 — 초안) | 시각의 출처를 슬롯 한 곳으로 단순화 (그룹의 기본 시각 컬럼 제거) |
| 서버 기본 시각 | 설정 파일 `hawe.mission.*`에 둔다 (발행 09:00, 마감 23:00 — 초안). 코드 상수가 아님 | 코드 수정 없이 환경별로 조정 가능 (JWT 설정과 같은 방식) |
| 시각 고정 | 미션 발행 시 `published_at`, `deadline_at`을 미션에 저장 | 이후 그룹 설정을 바꿔도 이미 열린 미션과 지각 판정이 흔들리지 않음 |

---

## 1. 틀(템플릿) 목록

### 1-1. 분류

| 유형 | 설명 | 상태 |
|---|---|---|
| **S** 단독 제출 | 각자 혼자 제출하면 끝 | 1차 |
| **T** 대상 지정 | S와 같되 미션이 특정 멤버(`target`)를 지목 | 1차 |
| **G** 정답 맞히기 | 현재 틀 없음. 성대모사·소리 흉내가 음성 제거로 폐기되었다 (§4-1). 그림 맞히기 AI 게임은 서버 틀이 아니라 기기 안에서 동작한다 (M1/A8 계획) | 폐기 |
| **V** 집계 | 제출값을 모아 결과(평균/비율/순위)를 보여줌 | 1차 |
| **R** 순서·역할 | 앞사람 결과에 이어가거나, 출제자/맞히는 사람 역할이 나뉨 | 2차 (§7) |

### 1-2. 코드표

`media`는 사용자가 쓰는 매체, `payload`는 제출에 필요한 필드 (§3-2 참조).

| 매체 | 틀 | `template` | 유형 | `params` | `payload` | 상태 |
|---|---|---|---|---|---|---|
| 사진 | 지금 이 순간 | `PHOTO_NOW` | S | `subject` | `file`(이미지) | 1차 |
| 사진 | 갤러리 발굴 | `PHOTO_GALLERY` | S | `condition` | `file`(이미지) | 1차 |
| 사진 | 색깔 사냥 | `PHOTO_COLOR` | S | `color` | `file`(이미지) | 1차 |
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

- `target`을 쓰는 틀은 발행 시 서버가 그룹 멤버 중 1명을 결정적 시드로 뽑아 `targetUserId`를 저장한다. `TEXT_FILL_BLANK`는 항상, `DRAW_SKETCH`/`DRAW_A_TO_B`는 코드 상수 확률 0.3으로 지정한다 (§6-1). 응답에서는 서버가 닉네임을 풀어 `params.target`으로 내려준다 (§2 참조).
- 음성 틀(한 줄·소리 흉내·성대모사)은 2026-10-12 음성 기능 제거로 코드표에서 삭제했다. DB에 예전 음성 미션 행이 남아 있어도 조회는 `media: null`로 응답하고 제출은 `INVALID_SUBMISSION`으로 거부한다.
- `SLIDER_CONSENSUS`의 승자는 `guess`와 실제 평균(`number`들의 평균)의 차이가 가장 작은 멤버다. (MISSIONS.md의 설명을 이렇게 해석했다 — §11 확인 필요)

---

## 2. 미션 조회

| Method | Path | 설명 |
|---|---|---|
| GET | `/groups/{groupId}/missions/today` | 오늘 열린 미션 목록 + 내 제출 여부 |
| GET | `/missions/{missionId}` | 미션 단건 (알림 payload, 달력에서 진입 시) |
| GET | `/mission-templates` | 틀 목록과 빈칸/제출 형식 (프론트 화면 구성용) |

### GET /groups/{groupId}/missions/today
슬롯별 가장 최근 발행된 현재 미션을 `slotNo` 오름차순으로 돌려준다. 전날 발행되어 자정을 넘긴 미션도 다음 발행 전에는 현재 미션이다. 아직 하나도 발행되지 않았으면 `missions`가 빈 배열이다. 하루 미션 수를 줄여 없어진 슬롯, 하루 넘게 발행이 없었던 슬롯의 미션은 현재 미션이 아니다 (§2 "그룹 설정 연동").
```json
// Response 200
{
  "date": "2026-09-28",
  "dailyMissionCount": 1,
  "nextPublishAt": "2026-09-29T09:00:00+09:00",
  "missions": [
    {
      "missionId": 10,
      "groupId": 1,
      "slotNo": 1,
      "number": 142,
      "content": "\"퇴근길\"로 삼행시 지어줘",
      "category": "감성",
      "template": "TEXT_ACROSTIC",
      "media": "TEXT",
      "params": { "word": "퇴근길" },
      "publishedAt": "2026-09-28T09:00:00+09:00",
      "deadlineAt": "2026-09-28T23:00:00+09:00",
      "closesAt": "2026-09-29T09:00:00+09:00",
      "submittedByMe": false,
      "members": [{ "userId": 1, "submitted": false, "pokedByMe": [] }]
    }
  ]
}
```
슬라이더 미션 항목 예시:
```json
{
  "missionId": 11, "groupId": 1, "slotNo": 2, "content": "오늘 컨디션은 어디쯤?", "category": "일상",
  "template": "SLIDER_POSITION", "media": "SLIDER",
  "params": { "question": "오늘 컨디션", "left": "방전", "right": "풀충전" },
  "publishedAt": "2026-09-28T14:00:00", "deadlineAt": "2026-09-28T23:00:00", "submittedByMe": true
}
```
`target`을 쓰는 틀은 `params`에 서버가 풀어준 대상이 추가된다.
```json
"params": { "sentence": "[대상]은 무인도에 가면 제일 먼저 ___ 할 것 같다", "target": { "userId": 3, "nickname": "잎사귀" } }
```
- `date`: 현재 미션의 `mission_date`. 없으면 서울 기준 오늘 날짜다.
- `dailyMissionCount`: 그룹 설정의 하루 미션 수(1~5, 기본 1). 하루 미션 수를 줄인 날에는 이미 열린 미션이 남아 `missions`가 이 수보다 많을 수 있다.
- `nextPublishAt`: 슬롯별 다음 발행 예정 시각 중 가장 이른 값. 현재 미션이 있으면 해당 미션의 `closesAt`을 사용한다.
- `number`: 해당 그룹의 누적 미션 번호. `members`: 참여 순서의 멤버별 해당 미션 제출 여부와 요청자가 이 미션에서 보낸 재촉 종류 `pokedByMe` (`poke`, 없으면 `[]`). 그룹 목록의 `todayMission` 요약에는 재촉 정보가 없다.
- 마감 이후에도 다음 미션 발행 전까지 현재 미션으로 보인다. 6단계 1차에서 지각 제출과 종료 판정을 구현했다.
- 에러: `GROUP_NOT_FOUND` (404), `NOT_GROUP_MEMBER` (403). (이전의 `MISSION_NOT_PUBLISHED_YET`는 폐지 — 빈 배열로 표현)

### GET /missions/{missionId}
응답은 위 `missions[]`의 항목 1개와 같다 (`number`, `closesAt`, `members` 포함).
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

### 그룹 설정 연동 (하루 미션 수 · 발행 시각)
그룹 설정 API(`POST /groups`, `PATCH /groups/{groupId}/settings`, `GET /groups/{groupId}`; 기준 문서 `API_DESIGN.md` §2)에 아래 항목이 있다. **그룹에는 발행·마감 시각 필드가 없고, 시각은 모두 슬롯(`missionSlots`)이 가진다.**

| 필드 | 타입 | 설명 |
|---|---|---|
| `relationType` | string | 그룹 관계: `COUPLE`(연인) / `FAMILY`(가족) / `FRIEND`(친구) / `RIVAL`(웬수) / `CASUAL`(그저그런사이). 생략하면 `CASUAL` (생성 시) / 변경 없음 (수정 시). AI가 미션의 톤과 금지 주제를 정하는 입력으로만 쓰이며 틀 선택에는 영향이 없다 (§6-1) |
| `dailyMissionCount` | int (1~5) | 하루에 열 미션 수. 생략하면 1 (생성 시) / 변경 없음 (수정 시) |
| `missionSlots` | array (선택) | 슬롯별 시각 지정. 항목: `slotNo`, `publishTime`(선택), `deadlineTime`(선택) |

```json
// POST /groups — 시각을 지정하지 않은 그룹 (하루 1개, 설정 파일의 기본 시각 사용)
{ "name": "우리집" }

// PATCH /groups/1/settings — MVP 1번 슬롯을 오후 시각으로 지정
{ "missionSlots": [ { "slotNo": 1, "publishTime": "14:00", "deadlineTime": "22:00" } ] }

// Response 200 — 슬롯마다 실제로 적용되는 시각을 항상 내려준다
{ "id": 1, "name": "우리집", "ownerId": 1, "relationType": "FAMILY",
  "dailyMissionCount": 1,
  "missionSlots": [
    { "slotNo": 1, "publishTime": "14:00", "deadlineTime": "22:00", "custom": true }
  ] }
```
- **기본값 규칙:** 지정하지 않은 슬롯, 그리고 지정에서 생략한 시각은 설정 파일(`hawe.mission.*`)의 기본 시각(발행 09:00, 마감 23:00 — 초안)을 쓴다. 아무것도 지정하지 않으면 모든 슬롯이 기본 시각에 열린다. `custom`은 해당 슬롯에 지정된 값이 하나라도 있는지를 뜻한다.
- **`missionSlots`를 보내면 전체 교체**다. 보낸 슬롯만 지정 상태로 남고 나머지는 기본 시각으로 돌아간다. 빈 배열 `[]`은 지정을 모두 지운다. 필드를 생략하면 기존 지정을 유지한다.
- **적용 시점:** 이미 발행된 미션은 유지한다. 아직 발행되지 않은 슬롯의 새 발행 시각이 지났다면 다음 분 실행에서 오늘 미션을 발행한다.
- **하루 미션 수를 줄이면 (B7):** 없어진 슬롯에 이미 열린 미션은 그날 계속 열려 있고(제출·콕 가능), 그룹의 **다음 날 첫 발행**에서 닫힌다. 그 뒤 다시 늘려도 하루 넘게 발행이 없었던 슬롯의 옛 미션은 다시 열리지 않고, 그 슬롯의 발행 시각에 새 미션이 열린다.
- **같은 시각 슬롯:** 슬롯 간 최소 간격은 없다. 모든 슬롯이 같은 시각이면 한 번에 N개가 열린다. 마감이 발행보다 이른 시각이면 다음 날 그 시각이 마감이다.
- **검증 (400 `VALIDATION_ERROR`):** `relationType`이 5종 코드가 아님, `dailyMissionCount`가 1~`max-daily-count`(5) 밖, `slotNo`가 1~`dailyMissionCount` 밖, 같은 `slotNo` 중복, 시각 형식(`HH:mm`) 위반.
- **기존 API와의 차이(호환성 깨짐):** 이미 구현된 그룹 API의 `missionPublishTime`, `missionDeadlineTime` 필드는 폐지된다. 프론트가 아직 연결되지 않아 영향은 작지만 Postman 컬렉션과 그룹 테스트는 고쳐야 한다.
- 권한은 기존과 같다 (owner만 변경, 멤버는 조회).

### 서버 기본 시각 설정 (`hawe.mission.*`)
슬롯에 시각이 지정되지 않았을 때만 쓰는 값이며 `application.properties`에 둔다.
```properties
hawe.mission.default-publish-time=09:00
hawe.mission.default-deadline-time=23:00
hawe.mission.max-daily-count=5
```
- `@ConfigurationProperties(prefix = "hawe.mission")` 레코드(`MissionProperties`)로 바인딩한다. 기존 `JwtProperties`와 같은 방식이다.
- `max-daily-count`는 `dailyMissionCount`의 상한 검증에 쓴다.
- 그림 틀의 대상 지정 확률 0.3은 현재 코드 상수다.
- 환경변수로 덮어쓸 수 있다 (예: `HAWE_MISSION_DEFAULT_PUBLISH_TIME`). 개발 환경에서는 확인이 쉽도록 가까운 시각으로 바꿔 쓸 수 있다.
- 값을 바꾸고 **재시작**하면 시각을 지정하지 않은 모든 그룹에 적용된다. 이미 열린 미션은 `published_at`/`deadline_at`에 고정되어 있어 바뀌지 않는다. 실행 중에는 바꿀 수 없다.
- 형식이 잘못된 값(예: `9시`)이면 애플리케이션이 시작되지 않는다 (바인딩 실패).

---

## 3. 제출

6단계 1차 구현됨, B5(2026-10-13)에서 새 틀 6종(`TEXT_ONE_WORD`·`TEXT_REVIEW`·`TEXT_BALANCE`·`SLIDER_*`)까지 받는다. 같은 슬롯의 다음 미션이 발행되면 이전 미션 제출은 409 `MISSION_CLOSED`로 거부한다. 2차 틀과 예전 음성 틀은 받지 않는다. 새 틀이 실제로 발행되는지는 발행 풀 설정이 정한다(§6-1).

| Method | Path | 설명 |
|---|---|---|
| POST | `/missions/{missionId}/submissions` | 제출 (`multipart/form-data`) |

### 3-1. 요청

`multipart/form-data`. 아래 필드 중 **틀이 요구하는 것만** 보낸다. 요구하지 않는 필드가 오면 무시하지 않고 거부한다 (`INVALID_SUBMISSION`).

| 필드 | 타입 | 설명 |
|---|---|---|
| `file` | file | 이미지 |
| `text` | text | 글 본문 |
| `number` | int | 별점(1~5) 또는 슬라이더 위치(0~100) |
| `guess` | int | `SLIDER_CONSENSUS`의 예측값(0~100) |
| `choice` | text | `A` 또는 `B` (`TEXT_BALANCE`) |
| `caption` | text | 보조 설명 (최대 200자, 틀에 따라 선택/필수) |

음성 제거(2026-10-12)로 `duration`·`waveform`(음성 길이·파형)과 `answer`·`alias`(정답 맞히기) 필드는 없어졌다. 보내면 요구하지 않는 필드로 `INVALID_SUBMISSION` (400).

### 3-2. 틀별 필수 필드

| `template` | 필수 | 선택 | 검증 |
|---|---|---|---|
| `PHOTO_NOW`, `PHOTO_GALLERY`, `PHOTO_COLOR`, `DRAW_SKETCH`, `DRAW_A_TO_B` | `file`(이미지) | `caption` | 파일 헤더로 이미지 판정, ≤ 10MB |
| `TEXT_ACROSTIC` | `text` | - | 정확히 3줄, 각 줄이 `params.word`의 해당 글자로 시작 |
| `TEXT_ONE_WORD` | `text`(단어, ≤ 20자) | `caption`(이유, ≤ 100자) | 단어 안에 공백·줄바꿈 불가 |
| `TEXT_REVIEW` | `number`(1~5), `text`(≤ 100자) | - | 별점은 정수 |
| `TEXT_BALANCE` | `choice` | `caption`(≤ 200자) | 정확히 `A` 또는 `B`(대문자) |
| `TEXT_FILL_BLANK` | `text`(≤ 100자) | - | 공백 불가 |
| `SLIDER_POSITION`, `SLIDER_TASTE` | `number`(0~100) | - | 정수 |
| `SLIDER_CONSENSUS` | `number`, `guess` | - | 둘 다 0~100 정수 |

- 시간 제한이 있는 틀(`DRAW_SKETCH`의 `seconds`)은 **클라이언트가 시간을 강제**한다. 서버는 그림 제작 시간을 신뢰할 수 있는 방식으로 알 수 없으므로 검증하지 않는다.
- 파일 제한: 이미지 ≤ 10MB (`jpg`/`png`/`webp`). 그 밖의 형식(예전 음성 형식 포함)은 415 `UNSUPPORTED_FILE_TYPE`. 서버가 파일 헤더로 종류와 확장자를 판정하며 원본 파일명·Content-Type은 신뢰하지 않는다. 로컬 저장 키로 저장한 파일은 인증 없이 `/files/<key>`로 공개된다.

**1차 구현 규칙:** 사진·그림 5종은 `file` 필수·`caption` 선택이다. `TEXT_FILL_BLANK`는 공백 제거 후 1~100 grapheme, `TEXT_ACROSTIC`은 정확히 3줄이며 `params.word`가 3글자일 때 각 줄은 해당 글자로 시작해야 한다. 삼행시·빈칸 채우기에서 `caption`은 받지 않는다.

**B5 구현 규칙 (새 틀 6종):**
- 글자 수는 grapheme(이모지·결합 문자를 한 글자로) 기준, `caption`은 코드포인트 기준이다. 글은 앞뒤 공백을 지우고 센다.
- 숫자 필드(`number`, `guess`)는 부호·소수점·공백 없는 정수 문자열만 받는다(`50.0`, ` 5`, `-1`, `+5`, 빈 값은 400). 그다음 틀의 범위를 확인한다.
- 새 틀의 `caption`은 앞뒤 공백을 지우고, 비면 저장하지 않는다(`null`).
- 새 틀은 파일을 받지 않는다(`file`이 오면 요구하지 않는 필드).
- 저장 위치: `number` → `submissions.number_value`, `choice` → `choice_value`, `text` → `answer_data.text`, `guess` → `answer_data.guess`. 스키마는 바꾸지 않았다(V1에 이미 있던 컬럼).

### 3-3. 응답

1차의 생성·피드 항목·상세 응답은 동일한 `SubmissionResponse` 모양이다. 사용하지 않는 필드는 생략한다. `mediaUrl`은 `/files/submissions/<UUID>.<확장자>` 형식의 루트 상대 경로이며, 저장 DB 값은 `submissions/...` 키다. `submittedAt`은 `+09:00` 오프셋이다.

```json
{ "id": 100, "missionId": 10,
  "author": { "userId": 3, "nickname": "서연", "characterKey": "mulbangul", "profileImageUrl": null },
  "template": "PHOTO_NOW", "mediaUrl": "/files/submissions/uuid.jpg", "mediaType": "IMAGE",
  "caption": "창밖", "reactions": [], "commentCount": 0,
  "submittedAt": "2026-09-28T21:00:00+09:00", "isLate": false }
```

글 제출은 `text`가 포함되고 `mediaUrl`·`mediaType`은 생략된다. 사진·그림은 `mediaUrl`과 `mediaType: "IMAGE"`가 포함된다. 음성 제거로 `duration`·`waveform` 필드는 응답에서 없어졌다.

새 틀(B5)은 해당 틀에서만 아래 필드가 들어간다(`text` 다음, `reactions` 앞).

| 필드 | 틀 |
|---|---|
| `number` | `TEXT_REVIEW`(별점), `SLIDER_*`(위치) |
| `choice` | `TEXT_BALANCE` |
| `guess` | `SLIDER_CONSENSUS` |
| `text` | `TEXT_ONE_WORD`(단어), `TEXT_REVIEW`(한 줄 후기) |
| `caption` | `TEXT_ONE_WORD`(이유), `TEXT_BALANCE`(이유) — 보냈을 때만 |

```json
{ "id": 101, "missionId": 12, "author": { "userId": 3, "nickname": "서연", "characterKey": "mulbangul", "profileImageUrl": null },
  "template": "SLIDER_CONSENSUS", "number": 80, "guess": 60, "reactions": [], "commentCount": 0,
  "submittedAt": "2026-10-13T21:00:00+09:00", "isLate": false }
```

에러: `MISSION_NOT_FOUND` (404), `NOT_GROUP_MEMBER` (403), `ALREADY_SUBMITTED` (409), `INVALID_SUBMISSION` (400), `UNSUPPORTED_FILE_TYPE` (415), `FILE_TOO_LARGE` (413)

---

## 4. 피드 / 게시물

| Method | Path | 설명 |
|---|---|---|
| GET | `/missions/{missionId}/submissions` | 그룹 피드 (잠금 규칙 적용) |
| GET | `/submissions/{id}` | 게시물 상세 |

### GET /missions/{missionId}/submissions
- `page` 기본 0(음수는 0), `size` 기본 20(1~100으로 보정), 정렬 `submitted_at ASC, id ASC`. 항목은 §3-3의 공통 응답이다. `reactions`는 스티커별 `{ stickerType, count, reactedByMe, userIds }` 배열이고 `commentCount`는 실제 댓글 수다. 반응이 없는 종류는 생략한다. 사진 제출의 비동기 판정이 끝나고 긍정 결과가 있으면 `aiReview: { "badge": "PERFECT_FIT", "comment": "주제랑 딱 맞는 사진이에요!" }`가 추가된다. 점수는 응답하지 않는다.
- 내가 아직 미제출: **423** `FEED_LOCKED`
- 제출 완료: 페이지네이션 봉투. 항목은 §3-3의 공통 제출 응답.
```json
{ "content": [
  { "id": 100, "missionId": 10,
    "author": { "userId": 1, "nickname": "몽실이", "characterKey": "mongsil", "profileImageUrl": null },
    "template": "PHOTO_NOW", "mediaUrl": "/files/submissions/uuid.jpg", "mediaType": "IMAGE",
    "reactions": [], "commentCount": 0, "submittedAt": "2026-09-28T21:00:00+09:00", "isLate": false }
], "page": 0, "size": 20, "totalPages": 1, "totalElements": 1 }
```
- 정렬: 제출 시각 오름차순 (같은 시각이면 `id` 오름차순).
- 집계 틀(V)도 피드에서는 개별 제출을 그대로 보여준다. 집계는 §5의 결과 API에서 본다.
- 에러: `MISSION_NOT_FOUND` (404), `NOT_GROUP_MEMBER` (403), `FEED_LOCKED` (423)

### GET /submissions/{id}
- §3-3의 공통 응답과 실제 반응·댓글 집계를 제공한다. 지난 미션도 같은 잠금 규칙을 적용한다.
응답은 §3-3의 공통 `SubmissionResponse`와 같다.
- 게시물이 속한 그룹의 멤버이면서 **해당 미션에 내가 제출했을 때만** 볼 수 있다 (잠금 규칙 동일).
- 에러: `SUBMISSION_NOT_FOUND` (404), `NOT_GROUP_MEMBER` (403), `FEED_LOCKED` (423)

### 4-1. 정답 맞히기 (G 유형) — 폐기됨
**2026-10-12 음성 기능 제거로 폐기.** 정답 맞히기를 쓰던 틀(성대모사·소리 흉내)이 모두 음성 틀이어서 G 유형 틀이 남지 않았다. 정답 시도 API(`POST /submissions/{id}/guesses`)는 만들지 않는다(B6 계획 폐기). 그림 맞히기 AI 게임은 서버 틀이 아니라 기기 안에서 동작한다(M1/A8 계획).
- DB의 `submissions.answer_text`·`answer_aliases`와 `submission_guesses` 테이블은 지우지 않고 **미사용 잔재로 유지**한다(§9). 나중에 서버 기반 맞히기 게임이 생기면 재사용할 수 있다.
- §10의 `GUESS_*` 에러 코드도 미사용(예약)으로 남긴다.

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
- 에러: `MISSION_NOT_FOUND` (404), `NOT_GROUP_MEMBER` (403), `FEED_LOCKED` (423), `RESULT_NOT_SUPPORTED` (400) — V 유형이 아닌 틀. 검사 순서도 이 순서다.

**B5 구현 규칙 (2026-10-13 구현됨):**
- 지난 미션(이미 다음 미션이 열린 것)의 결과도 볼 수 있다. 마감 뒤 늦은 제출도 결과에 들어간다.
- `totalCount`는 제출 수. `choices`는 0표여도 항상 `A`·`B` 두 항목이다.
- `average`(제출된 `number` 평균), `diffFromAverage`(= 내 값 − 평균), `diff`(= |`guess` − 평균|)는 정확한 값으로 계산·비교하고 **응답에서만 소수 첫째 자리로 반올림**한다(HALF_UP, 음수는 0에서 먼 쪽. 예: 평균 62.25 → 62.3, −32.75 → −32.8).
- `distribution`·`ranking`의 `nickname`은 피드와 같은 표시 닉네임(그룹 내 프로필 우선)이다. `distribution`은 제출 순서다.
- `ranking`은 `diff` 오름차순, 같으면 제출 순서(제출 시각 → id). `winnerUserIds`는 가장 작은 `diff`와 같은 사람 전부(동률이면 여러 명)다.
- `SLIDER_CONSENSUS` 응답에는 `myNumber`·`distribution`이 없다(위 예시대로).

---

## 6. 미션 생성 (내부 처리, HTTP API 없음)

`MissionScheduler`가 서울 시각 매분 모든 그룹을 순회하고 `MissionPublisher.publishIfDue`를 호출한다. 오늘 날짜의 발행 시각이 지났고 `(group_id, mission_date, slot_no)`에 미션이 없으면 발행한다. 서버 중단 중 놓친 오늘 슬롯은 다음 실행에서 따라잡으며 지난 날짜는 소급하지 않는다. UNIQUE 제약으로 중복 발행을 막고, 그룹별 예외를 기록한 뒤 다음 그룹을 계속 처리한다.

그룹 생성 트랜잭션이 커밋되면 첫 발행을 시도한다. 오늘 발행 시각이 이미 지났으면 바로 열린다. 생성 응답의 `missionCount`는 커밋 이후 발행 전 값인 0일 수 있으므로 상세 조회에서 갱신한다. 발행 시각은 예정 시각과 그룹 생성 시각 중 늦은 값이다. `deadlineAt`은 발행 이후 처음 오는 슬롯 마감 시각이며, `closesAt`은 응답 시점에 계산하는 다음 발행 예정 시각이다.

미션 문장은 `MissionGenerator.generate(template, seed, context, withTarget)`가 만든다. 발행기가 주입받는 것은 `FallbackMissionGenerator`(`@Primary`)다. AI 를 쓸 수 있으면 `AiMissionGenerator`(§6-2)를 먼저 부르고, 꺼져 있거나 실패하면 고정 문장 `PlaceholderMissionGenerator`를 쓴다 — 어느 경우에도 예외를 던지지 않아 AI 장애가 발행을 막지 않는다. `context`(`GenerationContext`)에는 날짜·그룹 관계·기념일·지난 기록 신호(§6-2)와 **같은 날 먼저 열린 슬롯 + 그 전 최근 미션 5개**(문장·카테고리·틀, 최근 것부터, 최대 `5 + 하루 미션 수 − 1`개)가 들어 있다. 슬롯 순서대로 같은 트랜잭션에서 저장하므로 같은 시각에 열리는 앞 슬롯 문장도 들어간다. 고정 문장 생성기는 틀마다 3개 이상의 변형 중 최근 문장에 없는 것을 시드 순서로 고르고, 모두 있으면 가장 오래전에 쓴 변형을 고른다(예외 없음, B7). 그 밖의 그룹 정보는 쓰지 않는다. 생성된 미션에는 출처(`GenerationSource`: `AI`·`AI_FALLBACK`·`PLACEHOLDER`)가 붙어 `missions.generation_source`에 저장된다(B9) — 앱 응답에는 내보내지 않는다. 알림 발송은 아직 없다. 발행 중 다른 오류는 기록되며 다음 분 실행에서 재시도한다.

AI 응답에 대한 서버 검증 (`GeneratedMissionValidator`, AI가 규칙을 어길 수 있으므로 프롬프트와 별개로 반드시 수행, B8 구현됨). 하나라도 어기면 그 응답은 버리고 다시 요청한다:

| 검증 | 내용 |
|---|---|
| 결과 형태 | `content`·`category`·`params` 세 항목만 (빠지거나 남는 항목 거부) |
| 문장 | 앞뒤 공백 제거 후 1~60자, 줄바꿈·이모지·중괄호·금칙어(`hawe.mission.ai.blocked-words`) 없음. `↔`·`★` 같은 글자 기호는 허용 |
| 틀 | 응답이 서버가 정한 `template`의 `params` 형식을 따르는가 (AI가 다른 틀로 바꾸지 못함). 2차 틀은 구현 전까지 선택 대상에서 제외 |
| 빈칸 | 틀의 빈칸 이름과 정확히 같다 (빠지거나 남으면 거부, `optional` 빈칸은 없어도 됨). 서버가 정하는 `targetUserId`를 AI가 보내면 거부. 값은 글 1~40자(위 문장 규칙과 같음), `seconds`만 10~60 정수(글 `"30"`도 숫자로 바꿈) |
| 삼행시 | `word`가 정확히 한글 3글자이고 문장에도 들어 있는가 |
| 밸런스 | `optionA` ≠ `optionB` (공백·문장부호 무시), 각 ≤ 20자 |
| 슬라이더 | `left` ≠ `right`, 각 ≤ 20자 |
| 빈칸 채우기 | `sentence`에 `___`가 정확히 1개(밑줄 4개 이상 거부), `sentence`에 `[대상]` 포함 |
| 대상 | 대상이 있으면 문장에 `[대상]` 필수, 없으면 문장·빈칸 어디에도 `[대상]` 금지 |
| 카테고리 | 주어진 목록에 있는 값인가 |
| 연속 방지 | 최근 미션(같은 날 먼저 열린 미션 포함)과 공백·문장부호를 뺀 문장이 같거나, 두 글자씩 끊은 조각의 자카드 유사도가 0.8 이상이면 거부 (틀은 서버가 정하므로 확인 대상이 아님) |

- 분당 전체 그룹을 순회하므로 그룹 수가 늘면 스케줄러 개선이 필요하다. `(group_id, mission_date, slot_no)` UNIQUE 제약이 중복 실행을 막는다.
- 시각 결정: 슬롯 N의 실제 발행 시각 = `group_mission_slots.publish_time`, 행이 없거나 NULL이면 설정 파일의 기본 발행 시각. 마감도 같다.

### 6-1. 틀 선택 규칙 (서버가 정함, AI 개입 없음)

MVP 발행 풀은 `PHOTO_NOW`, `PHOTO_GALLERY`, `PHOTO_COLOR`, `DRAW_SKETCH`, `DRAW_A_TO_B`, `TEXT_ACROSTIC`, `TEXT_FILL_BLANK`의 7종이다. 매체는 PHOTO/TEXT/DRAW 3종이다. `GET /mission-templates`는 1차 틀 13종 전체를 반환한다. (2026-10-12 음성 제거 전에는 풀 8종·매체 4종·목록 16종이었다.)

**발행 풀 설정 (B5, 2026-10-13):** 발행 풀은 `hawe.mission.enabled-templates`(쉼표로 구분한 틀 이름)로 정한다. 기본값은 위 MVP 7종이고, 비어 있으면 서버가 시작하지 않는다.
- 새 틀 6종(`TEXT_ONE_WORD`, `TEXT_REVIEW`, `TEXT_BALANCE`, `SLIDER_POSITION`, `SLIDER_TASTE`, `SLIDER_CONSENSUS`)은 서버가 제출·결과를 받을 수 있다. 하지만 **앱(A4)이 그 화면을 배포한 뒤에** 이 설정에 추가한다. 그 전에 켜면 구 앱이 미션을 열 수 없다.
- 매체는 풀에 틀이 있는 것만 돈다. 순서는 `PHOTO, TEXT, DRAW, SLIDER` 고정 목록에서 거른 뒤 섞는다. 슬라이더 틀을 넣으면 매체가 4종이 된다.
- 풀은 설정에 쓴 순서·중복과 상관없이 틀 정의 순서로 쓴다. 그래서 기본 설정에서는 B5 이전과 **같은 틀**이 골라진다(회귀 테스트로 고정).

| # | 규칙 | 강도 |
|---|---|---|
| 1 | 후보는 1차 틀(§1-2)만 쓴다 | 필수 |
| 2 | **하루 미션이 2개 이상일 때:** 매체 순서를 슬롯별로 순환한다. 슬롯이 매체보다 많아 같은 매체가 다시 오면 그 매체의 **다음 틀**을 쓴다 | 구현됨 (B7) |
| 3 | **하루 미션이 1개일 때:** 어제 같은 그룹이 쓴 매체를 제외한다 (어제와 같은 매체가 이어지지 않게) | 기본, 후보 부족 시 예외 허용 |
| 4 | 그룹 관계에 따른 선호·가중치는 **두지 않는다.** 관계는 AI가 문장의 톤과 금지 주제를 정하는 입력으로만 쓴다 | 확정 |
| 5 | **대상 지정:** `TEXT_FILL_BLANK`는 항상, `DRAW_SKETCH`와 `DRAW_A_TO_B`는 결정적 시드에 따른 확률 0.3으로 지정한다. 다른 틀은 대상을 지정하지 않는다 | 구현됨 |

**후보 부족 시 예외 허용:** 규칙 2·3을 지키면 고를 수 있는 틀이 없을 때만 완화한다.
- 하루 1개: 어제 매체 제외를 풀고 고른다 (예: 어제 3개 매체를 모두 썼을 때).
- 하루 2개 이상: 구현된 틀이 있는 매체가 슬롯 수보다 적을 때만 같은 날 매체 중복을 허용한다. 그때도 틀은 겹치지 않게 그 매체의 다음 틀을 쓰고, 매체에 틀이 하나뿐일 때만 같은 날 같은 틀이 다시 나온다(문장은 연속 방지로 다르게).
- 위로도 후보가 없으면 예비 미션 풀을 쓴다 (§6).

**계산 방식 (결정적, 동시성 문제 없음):**
```
mediaOrder = shuffle(풀에 있는 매체, seed = seed(groupId, 날짜, 0))   // 기본 풀이면 3종
N == 1 : 매체 = mediaOrder에서 어제 미션들이 쓴 매체를 뺀 첫 번째 (모두 빠지면 mediaOrder[0])
         틀   = 그 매체의 풀 틀(틀 정의 순서) L 중 L[Random(seed(groupId, 날짜, 1)).nextInt(|L|)]
N >= 2 : p = (k-1) % 매체 수, r = (k-1) / 매체 수        // 슬롯 k, r = 그 매체가 몇 번째로 다시 나왔는지
         매체 = mediaOrder[p]
         틀   = L[(Random(seed(groupId, 날짜, p+1)).nextInt(|L|) + r) % |L|]
```
- 첫 바퀴(r = 0)의 틀은 B7 전과 같고, 하루 1개 결과도 바뀌지 않는다(회귀 테스트로 고정).
- 읽는 DB 데이터는 **어제의 미션뿐**이다. 오늘 슬롯끼리는 서로 조회하지 않으므로, 같은 시각에 여러 슬롯이 동시에 발행되어도 결과가 같고 경쟁이 없다.
- 같은 `(그룹, 날짜, 슬롯)`을 다시 실행해도 같은 결과가 나온다 (멱등). `(group_id, mission_date, slot_no)` UNIQUE 제약이 중복 저장을 막는다.
- 어제 미션이 없는 그룹(첫날, 발행이 없었던 날)은 제외 없이 고른다.
- 대상을 지정하는 틀의 안전 검토는 §8에서 따로 다룬다.
- AI에게 줄 대상 표기: 대상이 있는 틀에서 AI는 이름 대신 `[대상]`을 문장에 쓰고, 서버가 응답할 때 현재 닉네임으로 바꿔 넣는다 (초안, 닉네임이 바뀌어도 최신 이름이 보이도록).

---

### 6-2. AI 미션 생성 (B8, 2026-10-13 구현됨)

그룹 관계·기념일·지난 기록을 반영해 Claude API(Anthropic Messages API)가 미션 문장·빈칸을 만든다. Spring AI 없이 `RestClient`로 직접 부른다(`AnthropicClient`). 틀·대상은 여전히 서버가 정하고(§6-1), AI 는 문장·카테고리·빈칸 값만 만든다.

- **호출 방식**: 도구(`submit_mission`) 하나를 반드시 쓰게 해서(`tool_choice`) 결과를 JSON 객체로 받는다. 시스템 프롬프트는 프롬프트 캐시(`cache_control`)를 걸어 두 번째 호출부터 싸게 한다. 도구 정의는 틀과 상관없이 하나로 고정하고, 틀별 빈칸은 입력 메시지에 적는다(캐시가 깨지지 않게). 모델 기본값 `claude-haiku-5-5`, `max_tokens` 400, `temperature` 0.9.
- **시스템 프롬프트**: `hawe.mission.ai.prompt-paths`의 파일들을 순서대로 이어 붙인다 — 기본값은 팀원 프롬프트 `classpath:prompts/mission-create-prompt.md`, `classpath:prompts/mission-template-prompt.md`(깃 제외 폴더라 저장소에는 없다). 하나도 읽지 못하면 저장소의 임시 기본 프롬프트 `mission-ai/default-prompt.txt`를 쓴다(WARN). 맨 뒤에는 항상 `mission-ai/server-guide.txt`(입력 항목 설명·특별한 날·그룹 기록 사용법·결과 형식)를 붙인다. 치환 변수는 쓰지 않고, 그때그때 바뀌는 값은 아래 입력 메시지로 보낸다.
- **입력 메시지** (사용자 메시지, 한 줄 안내 + JSON). 키는 팀원 프롬프트에 맞춰 한국어다.
  ```json
  { "오늘": "2026-10-13 화요일", "그룹 관계": "연인",
    "틀": { "코드": "TEXT_ACROSTIC", "이름": "삼행시", "빈칸": { "word": "string(3)" } },
    "대상": "대상 없음",
    "최근 미션": [ { "문장": "오늘 하늘 사진 찍어 줘", "카테고리": "감성", "틀": "지금 이 순간" } ],
    "카테고리 목록": ["일상", "감성", "추억", "유머"],
    "특별한 날": [ { "이름": "우리의 100일", "남은 날": 0 } ],
    "그룹 기록": { "멤버 수": 2, "함께한 날": 83,
      "최근 미션 참여": [ { "틀": "밸런스 게임", "문장": "짜장 vs 짬뽕", "참여율": 1.0, "제출당 반응": 1.5 } ],
      "매체별 참여율": { "글": 0.75 } } }
  ```
  - `빈칸`: 틀의 `paramsSchema`에서 서버가 정하는 `targetUserId`를 뺀 것. `대상`: `"대상 있음"` / `"대상 없음"`.
  - `특별한 날` (`AnniversaryResolver`): 오늘부터 7일 안. 연인의 "우리의 1일"(`groups.start_date`, 1일째부터 셈) 기준 100일 단위·주년, 그룹을 만든 날 기준 100일 단위·주년, 설정한 날(`hawe.mission.ai.special-days`, `MM-dd=이름`). 2월 29일에 시작했으면 평년 주년은 2월 28일. 음력 명절·개인 생일은 넣지 않는다.
  - `그룹 기록` (`GroupContextBuilder`): 최근 30일 안의 **닫힌** 미션(현재 미션이 아닌 것)에서 최근 10개의 참여율(제출 수 ÷ 미션이 열릴 때 있던 인원 — 달력의 인원 계산과 같다, 최대 1)과 제출당 반응+댓글 수, 매체별 평균 참여율, 지금 멤버 수, 그룹을 만든 날부터 센 날 수. 첫 미션이면 비어 있다.
  - **보내지 않는 것**: 멤버 이름·닉네임·이메일, 그룹 이름, 제출한 글·사진·댓글 내용, 대상이 누구인지. 보내는 문장은 서버·AI가 만든 미션 문장뿐이다(대상 자리는 `[대상]`).
- **검증·재시도·폴백**: 응답마다 위 서버 검증을 한다. 실패(검증 실패·HTTP 오류·시간 초과·도구 결과 없음·응답 잘림)면 다시 요청하고(`max-attempts`, 기본 2), 다 실패하면 고정 문장으로 바꾼다(출처 `AI_FALLBACK`). 로그에는 틀과 실패 사유만 남긴다 — API 키, 응답 본문, AI 가 쓴 문장은 남기지 않는다.
- **시간 제한과 위험**: AI 호출은 발행 트랜잭션(`REQUIRES_NEW`) 안에서 일어나 그동안 DB 연결을 잡고 있다. 연결 10초·읽기 20초 제한, 한 미션 총 40초(`total-timeout`)를 넘길 것 같으면 더 시도하지 않고 바로 고정 문장으로 간다. 그룹이 많아지면 발행 구조(생성을 트랜잭션 밖으로)를 다시 봐야 한다.
- **멱등성**: AI 결과는 매번 달라도 `(group_id, mission_date, slot_no)` UNIQUE와 발행 전 존재 확인이 중복 발행을 막는다.

**운영 설정** (`hawe.mission.ai.*`, 모두 기본값이 있어 비워 둬도 서버는 뜬다):

| 설정 | 기본값 | 설명 |
|---|---|---|
| `enabled` (`HAWE_MISSION_AI_ENABLED`) | `false` | 켜야 AI 를 부른다 |
| `api-key` (`ANTHROPIC_API_KEY`) | 빈 값 | 없으면 켜져 있어도 고정 문장. `.env`나 환경변수로만 넣고 저장소에 올리지 않는다 |
| `model` | `claude-haiku-5-5` | |
| `prompt-paths` | 팀원 프롬프트 2개 (`classpath:prompts/…`) | 운영 서버에서는 `file:/경로/mission-create-prompt.md,…`로 바꾼다 |
| `categories` | `일상,감성,추억,유머` | |
| `blocked-words`, `special-days` | 없음 | 쉼표 목록. `special-days`는 `MM-dd=이름` |
| `max-attempts`, `connect-timeout`, `read-timeout`, `total-timeout` | 2, 10s, 20s, 40s | |

- `application.properties`·`.env`는 ISO-8859-1로 읽히므로 한글 값은 `\uXXXX`로 쓰거나 환경변수로 넣는다(예: `special-days=12-25=크리스마스`).
- 테스트는 `pom.xml`의 surefire 설정(`hawe.mission.ai.enabled=false`)으로 `.env`에 키가 있어도 실제 AI 를 부르지 않는다. 실제 호출 확인은 키가 있는 환경에서 사람이 한다.

### 6-3. AI 주제 vs 랜덤 주제 비교 실험 (B9, 2026-10-13 구현됨)

효과를 숫자로 보이기 위해 일부 (그룹, 날짜)는 AI 주제, 나머지는 고정 문장(랜덤) 주제로 발행한다. 설계·지표·분석·한계는 `EXPERIMENT_TOPIC.md`.

- **배정**: `ExperimentAssigner`가 `SHA-256(salt|그룹|날짜)`로 군을 정한다(`hawe.experiment.topic.ai-ratio`, 기본 0.5). 같은 날 슬롯은 모두 같은 군. 실험이 꺼져 있거나(`enabled=false`, 기본) AI 를 쓸 수 없으면 군을 정하지 않는다 — 발행은 이전과 같다.
- **발행**: 발행기가 그날 군을 한 번 정해 `GenerationContext.experimentArm`에 넣는다. 랜덤 군이면 `FallbackMissionGenerator`가 AI 를 부르지 않고 고정 문장을 쓴다. 틀·대상·시각·최근 문장 회피는 군과 무관하다(두 군의 틀 분포가 같음 — 테스트로 고정).
- **기록**: `missions.experiment_arm`(`AI`/`RANDOM`, 실험 밖이면 NULL), `missions.generation_source`(`AI`/`AI_FALLBACK`/`PLACEHOLDER`). AI 군에서 AI 가 실패한 미션은 군 `AI`·출처 `AI_FALLBACK`(배정대로 센다). 앱 응답(DTO)에는 둘 다 없다(블라인드).
- **리포트**: `GET /internal/experiments/topic?from=&to=&format=json|md`, 헤더 `X-Admin-Token`(= `hawe.admin.token`, 환경변수 `HAWE_ADMIN_TOKEN`). 토큰이 없거나 틀리면 404, 설정하지 않으면 엔드포인트가 꺼진다. 닫힌 실험 미션만 센다.

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

- **`TEXT_FILL_BLANK` (랜덤 사람 지정 빈칸 채우기)**: MISSIONS.md의 예시 문장은 특정 멤버를 비하하는 내용이다. 이 문서에는 옮기지 않았다. 이 틀은 구조상 **여러 명이 한 명에 대해 쓰는** 형태라 미션 안전 규칙(`PROJECT_OVERVIEW.md` §7)과 충돌한다 (웬수: "여러 명이 한 명을 공격하게 되는 미션" 금지, 친구: "특정 친구 한 명을 지목해서 놀리는 미션" 금지, 공통: 혐오·비하 금지, 외모·몸 금지). 사용하려면 (a) 긍정적 문장만 허용, (b) 그룹 관계별 허용 여부, (c) 신고/삭제 수단 중 무엇을 둘지 먼저 정해야 한다.
- **`DRAW_SKETCH`, `DRAW_A_TO_B`의 대상 지정**: 설정된 확률로 대상이 지정된다. "[대상]을 동물로 그리기"가 외모 비하가 되지 않도록 프롬프트가 성격·분위기·좋아할 것 같은 것 등 칭찬하거나 가벼운 방향으로 제한한다. 관계별 선호를 두지 않기로 했으므로 가족, 그저그런사이에도 같은 확률이 적용된다.
- **`PHOTO_GALLERY`**: 갤러리 사진에는 얼굴·위치·개인정보가 있을 수 있다. 조건(`condition`)을 만들 때 해당 주제를 피하도록 프롬프트 규칙을 유지한다.
- **프롬프트 계약**: 프롬프트 파일은 이 저장소에서 제거되었고 팀원이 따로 관리한다. 프롬프트가 지켜야 할 약속은 다음과 같다. (1) 제출 평가는 하지 않고 미션 생성만 한다. (2) 틀은 서버가 정해서 입력으로 주며 AI는 고르거나 바꾸지 않는다. (3) 입력: 날짜·요일, 그룹 관계(문장의 톤과 금지 주제용, 틀 선택에는 쓰지 않음), 선택된 틀과 빈칸 정의, 대상 멤버 유무, 최근 미션(같은 날 먼저 열린 슬롯 포함), 카테고리 목록, 그리고 B8에서 더한 특별한 날·그룹 기록(§6-2). 서버는 이것을 사용자 메시지의 JSON으로 보내고, 입력 항목 설명과 결과 형식은 서버 안내(`mission-ai/server-guide.txt`)를 프롬프트 뒤에 붙여 알려 준다. (4) 출력은 `content`, `category`, `params` 세 가지이고 `params`의 빈칸 이름은 §1 코드표를 따른다(서버가 `submit_mission` 도구로 받는다). (5) 대상 멤버가 있으면 이름 대신 `[대상]`을 쓴다. (6) (폐기 — 음성 제거로 성대모사·소리 흉내 틀 없음.) (7) 프롬프트에 중괄호 `{ }`를 쓰지 않는다. 서버는 Spring AI 없이 직접 호출하고 치환 변수를 쓰지 않으므로 지금은 오류가 나지 않지만, 팀원 프롬프트 호환을 위해 규칙은 유지한다. 틀이 추가·변경되면 §1 코드표를 고치고 팀원에게 알린다.

---

## 9. DB 변경 (제안, 마이그레이션 V3 이후)

| 테이블 | 변경 |
|---|---|
| `groups` | `relation_type VARCHAR(20) NOT NULL DEFAULT 'CASUAL'` 추가 (그룹 관계, 코드 유효성은 서비스에서 검증). `daily_mission_count TINYINT UNSIGNED NOT NULL DEFAULT 1` 추가. `mission_publish_time`, `mission_deadline_time` **삭제** (현재 데이터가 없어 이전 없이 삭제) |
| `group_mission_slots` (신규) | 슬롯별 발행·마감 시각 지정. 컬럼: `group_id`, `slot_no`, `publish_time NULL`, `deadline_time NULL`. 행이 없거나 값이 NULL이면 설정 파일의 기본 시각을 사용. `(group_id, slot_no)` UNIQUE |
| `missions` | `template VARCHAR(40) NOT NULL`, `params JSON NOT NULL`, `slot_no TINYINT UNSIGNED NOT NULL DEFAULT 1`, `published_at DATETIME NOT NULL`, `deadline_at DATETIME NOT NULL` 추가. UNIQUE `(group_id, mission_date, slot_no)` 추가 (중복 발행 방지). B9: `generation_source VARCHAR(20) NULL`, `experiment_arm VARCHAR(10) NULL`, 인덱스 `(experiment_arm, mission_date)` (§6-3) |
| `submissions` | `image_url NOT NULL` → `media_url NULL`로 변경. `media_type`, `number_value`, `choice_value`, `answer_data`(JSON: 글 본문 `text`, 다수결 예측값 `guess` 등 집계하지 않는 답), `answer_text`(정답), `answer_aliases`(별칭 JSON 배열) 추가 (모두 NULL 허용, `answer_text`·`answer_aliases`는 음성 제거로 **현재 미사용** 잔재). 요청 필드(`text`, `guess`)는 그대로이고 저장만 다르다: `number`→`number_value`, `choice`→`choice_value`, `text`→`answer_data.text`, `guess`→`answer_data.guess` (`BACKEND_DB.md` "제출 답 저장 방식"). `(mission_id, user_id)` UNIQUE 추가 (`ALREADY_SUBMITTED`를 DB가 보장, 동시 제출 방지) |
| `submission_guesses` (신규) | 정답 시도 기록 — **현재 미사용** (음성 제거로 정답 맞히기 폐기, 테이블은 유지). 컬럼: `id`, `submission_id`(FK), `user_id`(FK), `guess_text VARCHAR(100) NOT NULL`, `is_correct BOOLEAN NOT NULL`, `created_at DATETIME`. `(submission_id, user_id)` 인덱스(UNIQUE 아님, 여러 번 시도 가능) |

- 기존 `group_members` UNIQUE 제약처럼 **동시 요청에서의 중복은 DB 제약으로 막고** 서비스에서 `DuplicateKeyException`을 `ALREADY_SUBMITTED`로 변환한다.
- `submissions.caption`의 `TEXT` 길이는 서비스에서 제한한다 (200자).
- 컬럼별 상세 정의는 `BACKEND_DB.md`의 "미션/제출/그룹 스케줄 스키마 변경 상세"를 기준으로 한다.

### 다른 문서에 미치는 영향 (이 문서에서는 수정하지 않음)
- `API_DESIGN.md` §7 (Calendar)의 `submission.imageUrl` → `mediaUrl` 계열로 맞춰야 함. 하루에 미션이 여러 개일 수 있으므로 `GET /me/calendar/{date}`의 그룹별 `mission`/`submission`도 목록 구조로 바꿔야 함.
- 이미 구현된 그룹 API(`GroupController`, `GroupService`, `GroupMapper`, `Group` 도메인, DTO)는 `dailyMissionCount`, `missionSlots`를 모르고, 삭제되는 `missionPublishTime`/`missionDeadlineTime`을 아직 사용한다. 그룹 생성·수정·조회의 요청/응답과 SQL을 별도 구현 작업으로 바꿔야 한다.
- `GroupMemberMapper.findMembersByGroupId`의 `submissionCountThisMonth`는 B7에서 이번 달 **제출한 날 수**로 바꿨다 (§11-9).
- `API_DESIGN.md` §4 (Comment/Reaction)는 `submissions` 기준이라 그대로 유효.
- `BACKEND_DB.md`의 엔드포인트 목록(미션/제출)과 `submissions` 컬럼 설명은 이 문서에 맞춰 갱신했다 (`DB_DESIGN`은 삭제됨).

## 10. 에러 코드 (`MissionErrorCode`, 새 enum — `mission/service/`)

| 코드 | HTTP | 설명 |
|---|---|---|
| `MISSION_NOT_FOUND` | 404 | 미션 없음 |
| `MISSION_CLOSED` | 409 | 다음 미션 발행으로 이전 미션 종료 (6단계 제출 API에서 구현됨) |
| `NOT_GROUP_MEMBER` | 403 | 그룹 멤버가 아님 (`GroupErrorCode` 재사용 또는 동일 코드) |
| `ALREADY_SUBMITTED` | 409 | 이미 제출함 |
| `FEED_LOCKED` | 423 | 내가 제출하기 전에는 피드·결과·상세를 볼 수 없음 |
| `SUBMISSION_NOT_FOUND` | 404 | 제출물 없음 |
| `INVALID_SUBMISSION` | 400 | 틀이 요구하지 않는 필드가 있거나 필수 필드/형식 위반 (메시지에 사유) |
| `UNSUPPORTED_FILE_TYPE` | 415 | 허용하지 않는 파일 형식 |
| `FILE_TOO_LARGE` | 413 | 파일 크기 초과 |
| `UNSUPPORTED_MEDIA_TYPE` | 415 | multipart가 아닌 제출 요청 등 지원하지 않는 요청 형식 |
| `RESULT_NOT_SUPPORTED` | 400 | 집계가 없는 틀에 결과 조회 |
| `GUESS_NOT_SUPPORTED` | 400 | 미사용(예약) — 정답 맞히기 폐기 |
| `CANNOT_GUESS_OWN_SUBMISSION` | 409 | 미사용(예약) — 정답 맞히기 폐기 |
| `ALREADY_SOLVED` | 409 | 미사용(예약) — 정답 맞히기 폐기 |
| `GUESS_LIMIT_EXCEEDED` | 409 | 미사용(예약) — 정답 맞히기 폐기 |

## 11. 미정 사항 (구현 전에 결정 필요)

1. (B10 갱신) 사진 틀에는 별도 비전 서비스 기반 긍정 판정이 있다. 도장은 제출 기록만으로 계산한다. 판정 프롬프트는 사용하지 않는다. 상세 `VISION_TOPIC_FIT.md`.
2. 하루가 끝난 뒤(예: 자정) 제출을 완전히 막을 것인가? 현재는 마감 후에도 `isLate`로 제출 가능하고 상한이 없다.
3. "오늘"의 기준 시간대. DB `CURDATE()`와 서버 시간대를 `Asia/Seoul`로 통일할지 (그룹 API의 `submittedToday`와 동일 문제).
4. (결정됨) 틀 중복·순환 규칙은 §6-1이다. 그룹 관계별 허용 틀 목록은 두지 않는다.
5. `SLIDER_CONSENSUS`의 승리 판정 해석 확인 (§1-2).
6. 대상 지정 틀에서 대상 본인이 제출할 수 있는가, 대상이 도중에 탈퇴하면 어떻게 표시하는가.
7. 파일 한도·허용 형식의 확정값. (음성 길이 검증 문제는 음성 제거로 해당 없음)
8. (결정됨, B7) **도장은 미션별**(선택지 c). 달력은 그날 `missions[]`의 미션마다 도장을 따로 보여 준다(이미 미션 단위로 계산하고 있었다). 멤버 목록의 `stampedToday`는 "현재 미션 중 하나라도 제출"이다.
9. (결정됨, B7) 멤버 목록의 `submissionCountThisMonth`는 이번 달 **제출한 날 수**다(같은 날 여러 개를 내도 1).
10. (결정됨, B7) 하루 미션 수 상한은 5(`max-daily-count=5`), 슬롯 간 최소 간격은 두지 않는다. 모든 슬롯이 같은 시각이면 같은 시각에 N개가 한꺼번에 열린다.
11. (결정됨, B7) 허용한다. 마감이 발행보다 이른 시각이면 다음 날 그 시각이 마감이다(`MissionTiming.deadlineAfter`).
12. (결정됨, B7) 이미 발행된 미션은 유지한다. 하루 미션 수를 줄이면 없어진 슬롯의 미션은 그날 계속 열려 있고 그룹의 다음 날 첫 발행에서 닫힌다. 하루 넘게 발행이 없었던 슬롯의 옛 미션은 수를 다시 늘려도 열리지 않는다 (§2 "그룹 설정 연동").
13. (결정됨, B7) 피드 잠금은 **미션별** 그대로다. 그 미션에 제출해야 그 미션의 피드·결과를 볼 수 있다.
14. (결정됨) 서버 기본 시각은 설정 파일 `hawe.mission.*`에 둔다 (§2). 값(초안 발행 09:00 / 마감 23:00)은 확정이 필요하다. `max-daily-count`는 5로 정했다 (B7).
15. (결정됨) 틀 선택 규칙은 §6-1: 하루 여러 개는 같은 날 매체 중복 금지, 하루 1개는 어제와 같은 매체 제외, 후보 부족 시 예외 허용, 그룹 관계별 선호는 두지 않음. 남은 일: 매체 목록·시드 계산 방식 구현 시 확정.
16. (결정됨) 그림 틀(`DRAW_SKETCH`, `DRAW_A_TO_B`)은 설정된 확률로 대상 멤버를 지정한다 (§6-1 규칙 5, 초안 0.3). 확률 값은 확정이 필요하고, 안전 검토는 §8에서 이어진다.
17. ~~(결정됨) 소리 흉내도 성대모사와 같은 정답 맞히기(G 유형)를 쓴다.~~ 폐기됨 (2026-10-12 음성 제거).
18. ~~(결정됨) 별칭은 정답으로 인정하고 오타는 허용하지 않는다.~~ 폐기됨 (음성 제거로 정답 맞히기 없음).
19. ~~(결정됨) 정답을 맞혀도 제출자에게 알림을 보내지 않는다.~~ 폐기됨 (음성 제거로 정답 맞히기 없음).
