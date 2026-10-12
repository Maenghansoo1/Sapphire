# HA'WE — 미션 / 제출 API 설계

`docs/FEATURE/MISSIONS.md`의 미션 템플릿(틀)을 기준으로 미션·제출 API를 설계한 문서. 미션 조회와 임시 생성기는 구현되었고 제출·피드는 아직 설계 단계다.
공통 규칙(Base path, 인증, 에러 형식, 페이지네이션, camelCase, ISO-8601)은 `API_DESIGN.md` §0을 따른다.

## 0. 핵심 개념

- **틀(template)**: 매체(사진/음성/글/그림/슬라이더) + 놀이 방식. 코드값으로 식별한다 (예: `TEXT_ACROSTIC`).
- **빈칸(params)**: 틀이 요구하는 입력값 (삼행시는 3글자 제시어, 슬라이더는 양 끝 키워드 등). 틀마다 모양이 다르며 미션에 JSON으로 저장한다.
- **미션**: 그룹 × 날짜 × 슬롯마다 1개. `template` + `params` + 사용자에게 보여줄 문장 `content`로 구성된다.
- **슬롯(slot)**: 하루에 열리는 미션의 순번 (`slotNo` 1, 2, 3 …). 그룹이 정한 "하루 미션 수"(`dailyMissionCount`)만큼 슬롯이 있다.
- **발행 시각 규칙**: 슬롯마다 발행·마감 시각은 `group_mission_slots`(그룹 설정의 `missionSlots`)가 정한다. **지정하지 않은 슬롯(행이 없거나 값이 NULL)은 설정 파일(`hawe.mission.*`)의 기본 시각(발행 09:00, 마감 23:00 — 초안)을 쓴다.** 그룹 자체에는 발행·마감 시각 필드가 없다 (§2 "그룹 설정 연동").
- **제출**: 틀이 요구하는 형식의 답. 미션당 1인 1회. 틀마다 담는 필드(파일/글/숫자/선택)가 다르다.
- **잠금 규칙**: 내가 제출해야 그날의 그룹 피드와 결과를 볼 수 있다 (423 `FEED_LOCKED`).
- **지각**: 마감 시각(`deadlineAt`) 이후, 같은 슬롯의 다음 미션이 발행되기 전까지 제출하면 `isLate=true`. 다음 미션 발행 순간 이전 미션은 닫힌다. 제출 종료 판정은 6단계에서 구현한다.
- **도장**: 별도 저장 없이 제출 기록 유무로 계산한다 (`PROJECT_OVERVIEW.md` 핵심 규칙). 하루에 미션이 여러 개일 때의 기준은 미정 (§11, 임시로 현행 유지: 그날 하나라도 제출하면 참잘했어요).

## 0-1. 이번 설계에서 확정한 것들

| 항목 | 결정 | 사유 |
|---|---|---|
| 제출 횟수 | 미션당 1인 1회 (`ALREADY_SUBMITTED`) | 도장 계산 시 중복 방지 |
| 제출 엔드포인트 | 모든 틀이 `POST /missions/{id}/submissions` 하나를 쓰고, 서버가 `template`으로 필수 필드를 검증 | 틀이 늘어도 API가 늘지 않음 |
| 파일 업로드 | 제출 API가 파일을 직접 받음 (multipart). 개발=로컬 저장, 운영=내부적으로 S3 | API 계약을 바꾸지 않고 저장소만 교체 |
| 파일 응답 필드 | `mediaUrl` + `mediaType`(`IMAGE`/`AUDIO`)로 통일 (기존 `imageUrl`에서 변경) | 음성도 담아야 함 |
| 틀 확장 방식 | `template` 코드 1개 + 검증기 1개 추가 | 컬럼/엔드포인트 추가 최소화 |
| 1차/2차 구분 | 혼자 제출로 끝나는 틀과 집계 틀은 1차, 순서·역할이 필요한 틀은 2차 | 2차는 별도 설계 필요 (§7) |
| 미션 생성 | HTTP API 없음. 스케줄러가 슬롯별 발행 시각에 서버 내부에서 생성 (§6) | 외부에서 임의로 미션을 만들 수 없게 |
| AI의 역할 | **미션 생성만** 한다. 서버가 정한 틀 안에서 문장·카테고리·빈칸 값을 만든다. 제출물 평가·판정은 하지 않는다 | 도장은 제출 기록만으로 계산되고 판정 결과를 저장할 필드가 필요 없음 |
| 성대모사·소리 흉내 | 제출자가 음성과 **정답**(과 선택적으로 **별칭**)을 함께 내고, 다른 멤버가 정답을 입력해 맞힌다. 별칭은 정답으로 인정한다. 사람당 최대 3회(초안), 맞혀도 제출자에게 알림을 보내지 않는다. 미션 문장은 "성대모사를 맞춰보세요" 수준이고 흉내 낼 대상은 제출자가 정한다 | 소리를 AI로 분석하지 않고 사람이 맞히는 방식이라 AI 판정이 필요 없음 |
| 하루 미션 수 | MVP에서는 `dailyMissionCount=1`만 허용 (`max-daily-count=1`) | 이후 설정으로 확장 가능 |
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
| **G** 정답 맞히기 | 제출자가 정답(과 별칭)을 함께 내고, 다른 멤버가 그 정답을 맞힘 (성대모사, 소리 흉내, §4-1) | 1차 |
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
| 음성 | 소리 흉내 | `VOICE_MIMIC` | G | 없음 | `file`(음성), `answer`(정답), `alias`(별칭, 선택) | 1차 |
| 음성 | 성대모사 | `VOICE_IMPRESSION` | G | 없음 | `file`(음성), `answer`(정답), `alias`(별칭, 선택) | 1차 |
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
- 소리 흉내(`VOICE_MIMIC`)와 성대모사(`VOICE_IMPRESSION`)는 둘 다 정답 맞히기(G 유형, §4-1)로 처리한다. 미션 문장에는 흉내 낼 대상이 없고("소리 흉내를 맞춰보세요", "성대모사를 맞춰보세요"), 무엇을 흉내 낼지는 제출자가 정한다.
- `SLIDER_CONSENSUS`의 승자는 `guess`와 실제 평균(`number`들의 평균)의 차이가 가장 작은 멤버다. (MISSIONS.md의 설명을 이렇게 해석했다 — §11 확인 필요)

---

## 2. 미션 조회

| Method | Path | 설명 |
|---|---|---|
| GET | `/groups/{groupId}/missions/today` | 오늘 열린 미션 목록 + 내 제출 여부 |
| GET | `/missions/{missionId}` | 미션 단건 (알림 payload, 달력에서 진입 시) |
| GET | `/mission-templates` | 틀 목록과 빈칸/제출 형식 (프론트 화면 구성용) |

### GET /groups/{groupId}/missions/today
슬롯별 가장 최근 발행된 현재 미션을 `slotNo` 오름차순으로 돌려준다. 전날 발행되어 자정을 넘긴 미션도 다음 발행 전에는 현재 미션이다. 아직 하나도 발행되지 않았으면 `missions`가 빈 배열이다.
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
- `dailyMissionCount`: 그룹 설정의 하루 미션 수(MVP는 1).
- `nextPublishAt`: 슬롯별 다음 발행 예정 시각 중 가장 이른 값. 현재 미션이 있으면 해당 미션의 `closesAt`을 사용한다.
- `number`: 해당 그룹의 누적 미션 번호. `members`: 참여 순서의 멤버별 해당 미션 제출 여부와 요청자가 이 미션에서 보낸 재촉 종류 `pokedByMe` (`poke`, `fling` 순서, 없으면 `[]`). 그룹 목록의 `todayMission` 요약에는 재촉 정보가 없다.
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
| `dailyMissionCount` | int (MVP: 1) | 하루에 열 미션 수. 생략하면 1 (생성 시) / 변경 없음 (수정 시) |
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
- **검증 (400 `VALIDATION_ERROR`):** `relationType`이 5종 코드가 아님, `dailyMissionCount` 범위, `slotNo`가 1~`dailyMissionCount` 밖, 같은 `slotNo` 중복, 시각 형식(`HH:mm`) 위반.
- **기존 API와의 차이(호환성 깨짐):** 이미 구현된 그룹 API의 `missionPublishTime`, `missionDeadlineTime` 필드는 폐지된다. 프론트가 아직 연결되지 않아 영향은 작지만 Postman 컬렉션과 그룹 테스트는 고쳐야 한다.
- 권한은 기존과 같다 (owner만 변경, 멤버는 조회).

### 서버 기본 시각 설정 (`hawe.mission.*`)
슬롯에 시각이 지정되지 않았을 때만 쓰는 값이며 `application.properties`에 둔다.
```properties
hawe.mission.default-publish-time=09:00
hawe.mission.default-deadline-time=23:00
hawe.mission.max-daily-count=1
```
- `@ConfigurationProperties(prefix = "hawe.mission")` 레코드(`MissionProperties`)로 바인딩한다. 기존 `JwtProperties`와 같은 방식이다.
- `max-daily-count`는 `dailyMissionCount`의 상한 검증에 쓴다.
- 그림 틀의 대상 지정 확률 0.3은 현재 코드 상수다. 정답 맞히기 설정은 해당 기능 구현 때 정한다.
- 환경변수로 덮어쓸 수 있다 (예: `HAWE_MISSION_DEFAULT_PUBLISH_TIME`). 개발 환경에서는 확인이 쉽도록 가까운 시각으로 바꿔 쓸 수 있다.
- 값을 바꾸고 **재시작**하면 시각을 지정하지 않은 모든 그룹에 적용된다. 이미 열린 미션은 `published_at`/`deadline_at`에 고정되어 있어 바뀌지 않는다. 실행 중에는 바꿀 수 없다.
- 형식이 잘못된 값(예: `9시`)이면 애플리케이션이 시작되지 않는다 (바인딩 실패).

---

## 3. 제출

6단계 1차 구현됨. 같은 슬롯의 다음 미션이 발행되면 이전 미션 제출은 409 `MISSION_CLOSED`로 거부한다. 사진·그림·음성·글의 MVP 8종 틀만 받는다.

| Method | Path | 설명 |
|---|---|---|
| POST | `/missions/{missionId}/submissions` | 제출 (`multipart/form-data`) |

### 3-1. 요청

`multipart/form-data`. 아래 필드 중 **틀이 요구하는 것만** 보낸다. 요구하지 않는 필드가 오면 무시하지 않고 거부한다 (`INVALID_SUBMISSION`).

| 필드 | 타입 | 설명 |
|---|---|---|
| `file` | file | 이미지 또는 음성 |
| `text` | text | 글 본문 |
| `duration` | decimal | `VOICE_LINE` 음성 길이(초), 0 초과 30 이하 |
| `waveform` | JSON array string | `VOICE_LINE` 파형 값 0~1, 최대 64개 |
| `number` | int | 별점(1~5) 또는 슬라이더 위치(0~100) |
| `guess` | int | `SLIDER_CONSENSUS`의 예측값(0~100) |
| `choice` | text | `A` 또는 `B` (`TEXT_BALANCE`) |
| `answer` | text | 성대모사·소리 흉내의 정답 (공백 불가, 최대 50자). 다른 멤버에게는 맞히거나 미션 마감 후에만 공개 |
| `alias` | text | 같은 정답으로 인정할 다른 표현 (선택, 최대 50자). 여러 개면 같은 이름으로 반복해서 보낸다 (최대 5개) |
| `caption` | text | 보조 설명 (최대 200자, 틀에 따라 선택/필수) |

### 3-2. 틀별 필수 필드

| `template` | 필수 | 선택 | 검증 |
|---|---|---|---|
| `PHOTO_NOW`, `PHOTO_GALLERY`, `PHOTO_COLOR`, `DRAW_SKETCH`, `DRAW_A_TO_B` | `file`(이미지) | `caption` | 파일 헤더로 이미지 판정, ≤ 10MB |
| `VOICE_LINE` | `file`(음성), `duration`, `waveform` | `caption` | 파일 헤더로 음성 판정, ≤ 5MB, 길이 ≤ 30초 |
| `VOICE_MIMIC`, `VOICE_IMPRESSION` (후속) | `file`(음성), `answer`(정답) | `caption`, `alias`(별칭, 최대 5개) | 음성 파일, 길이 ≤ 30초, 정답 공백 불가, 별칭 개수·길이 제한 |
| `TEXT_ACROSTIC` | `text` | - | 정확히 3줄, 각 줄이 `params.word`의 해당 글자로 시작 |
| `TEXT_ONE_WORD` | `text`(단어, ≤ 20자) | `caption`(이유, ≤ 100자) | 공백 불가 |
| `TEXT_REVIEW` | `number`(1~5), `text`(≤ 100자) | - | - |
| `TEXT_BALANCE` | `choice` | `caption` | `A`/`B` |
| `TEXT_FILL_BLANK` | `text`(≤ 100자) | - | 공백 불가 |
| `SLIDER_POSITION`, `SLIDER_TASTE` | `number`(0~100) | - | 정수 |
| `SLIDER_CONSENSUS` | `number`, `guess` | - | 둘 다 0~100 정수 |

- 시간 제한이 있는 틀(`DRAW_SKETCH`의 `seconds`)은 **클라이언트가 시간을 강제**한다. 서버는 그림 제작 시간을 신뢰할 수 있는 방식으로 알 수 없으므로 검증하지 않는다.
- 파일 제한: 이미지 ≤ 10MB (`jpg`/`png`/`webp`), 음성 ≤ 5MB (`m4a`/`mp3`/`aac`/`webm`). 서버가 파일 헤더로 종류와 확장자를 판정하며 원본 파일명·Content-Type은 신뢰하지 않는다. 로컬 저장 키로 저장한 파일은 인증 없이 `/files/<key>`로 공개된다.

**1차 구현 규칙:** 사진·그림 5종은 `file` 필수·`caption` 선택이다. `VOICE_LINE`은 `file`, `duration`, `waveform` 필수·`caption` 선택이며 길이는 30초 이하이다. `TEXT_FILL_BLANK`는 공백 제거 후 1~100 grapheme, `TEXT_ACROSTIC`은 정확히 3줄이며 `params.word`가 3글자일 때 각 줄은 해당 글자로 시작해야 한다. 글 틀에서 `caption`은 받지 않는다. 위 표의 나머지 틀은 1차에서 `INVALID_SUBMISSION`으로 거부한다.

### 3-3. 응답

1차의 생성·피드 항목·상세 응답은 동일한 `SubmissionResponse` 모양이다. 사용하지 않는 필드는 생략한다. `mediaUrl`은 `/files/submissions/<UUID>.<확장자>` 형식의 루트 상대 경로이며, 저장 DB 값은 `submissions/...` 키다. `submittedAt`은 `+09:00` 오프셋이다.

```json
{ "id": 100, "missionId": 10,
  "author": { "userId": 3, "nickname": "서연", "characterKey": "mulbangul", "profileImageUrl": null },
  "template": "VOICE_LINE", "mediaUrl": "/files/submissions/uuid.m4a", "mediaType": "AUDIO",
  "duration": 9.4, "waveform": [0.25, 0.42], "reactions": [], "commentCount": 0,
  "submittedAt": "2026-09-28T21:00:00+09:00", "isLate": false }
```

글 제출은 `text`가 포함되고 `mediaUrl`·`mediaType`은 생략된다. 사진·그림은 `mediaUrl`과 `mediaType: "IMAGE"`, 음성은 이에 더해 `duration`과 `waveform`이 포함된다. 미구현 틀의 제출 응답은 후속 단계에서 정의한다.

에러: `MISSION_NOT_FOUND` (404), `NOT_GROUP_MEMBER` (403), `ALREADY_SUBMITTED` (409), `INVALID_SUBMISSION` (400), `UNSUPPORTED_FILE_TYPE` (415), `FILE_TOO_LARGE` (413)

---

## 4. 피드 / 게시물

| Method | Path | 설명 |
|---|---|---|
| GET | `/missions/{missionId}/submissions` | 그룹 피드 (잠금 규칙 적용) |
| GET | `/submissions/{id}` | 게시물 상세 |

### GET /missions/{missionId}/submissions
- `page` 기본 0(음수는 0), `size` 기본 20(1~100으로 보정), 정렬 `submitted_at ASC, id ASC`. 항목은 §3-3의 공통 응답이다. `reactions`는 스티커별 `{ stickerType, count, reactedByMe, userIds }` 배열이고 `commentCount`는 실제 댓글 수다. 반응이 없는 종류는 생략한다.
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

### 4-1. 정답 맞히기 (G 유형, 성대모사·소리 흉내)
제출자는 음성과 함께 **정답**(무엇/누구를 흉내 냈는지)과, 선택적으로 같은 정답으로 인정할 **별칭**을 적는다. 다른 멤버는 음성을 듣고 정답을 입력해서 맞는지 확인한다.

| Method | Path | 설명 |
|---|---|---|
| POST | `/submissions/{id}/guesses` | 정답 시도 |

```json
// Request
{ "guess": "유재석" }

// Response 200 — 틀린 경우
{ "correct": false, "attemptsLeft": 2 }

// Response 200 — 맞힌 경우 (대표 정답 공개)
{ "correct": true, "attemptsLeft": 1, "answer": "유재석" }
```

피드(`GET /missions/{id}/submissions`)와 상세(`GET /submissions/{id}`)의 정답 맞히기 항목에는 아래 필드가 추가된다.
```json
{ "id": 105, "userId": 2, "nickname": "잎사귀", "template": "VOICE_IMPRESSION",
  "mediaUrl": "...", "mediaType": "AUDIO",
  "answer": null,
  "myGuess": { "solved": false, "attemptsLeft": 3 },
  "reactionCount": 0, "commentCount": 1, "submittedAt": "...", "isLate": false }
```
- `answer`: 공개 조건을 충족할 때만 값이 있고, 아니면 `null`. **별칭(`aliases`)은 다른 멤버에게 절대 내려주지 않는다.** 서버는 공개 조건이 아닐 때 정답과 별칭을 응답에 포함하지 않는다.
- `myGuess`: 현재 사용자의 시도 상태. 본인 제출물이면 `null`.
- **정답 공개 조건(초안):** 제출자 본인은 항상, 다른 멤버는 정답을 맞혔거나 미션 마감 시각(`deadlineAt`) 이후.
- **정답 비교(초안):**
  1. 시도 값과 정답·별칭을 정규화한다 (앞뒤·내부 공백 제거, 영문 대소문자 무시).
  2. 정규화한 정답 또는 별칭 중 하나와 **완전 일치**하면 정답이다.
  3. 맞히면 응답의 `answer`는 제출자가 적은 대표 정답이다 (별칭이 아님).
- **시도 횟수(초안):** 한 사람이 한 제출물에 최대 3회 (`hawe.mission.guess-max-attempts`). 맞히면 더 시도할 수 없다.
- **알림 없음:** 누군가 정답을 맞혀도 제출자에게 알림을 보내지 않는다. 제출자에게 맞힌 사람 목록도 제공하지 않는다 (초안).
- 본인 제출물에는 시도할 수 없다. 해당 미션에 내가 제출하지 않았으면 잠금 규칙에 따라 막힌다.
- 시도는 제출이 아니므로 도장과 제출 횟수에 영향이 없다.
- 에러: `SUBMISSION_NOT_FOUND` (404), `NOT_GROUP_MEMBER` (403), `FEED_LOCKED` (423), `GUESS_NOT_SUPPORTED` (400), `CANNOT_GUESS_OWN_SUBMISSION` (409), `ALREADY_SOLVED` (409), `GUESS_LIMIT_EXCEEDED` (409), `VALIDATION_ERROR` (400, 빈 값)

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

`MissionScheduler`가 서울 시각 매분 모든 그룹을 순회하고 `MissionPublisher.publishIfDue`를 호출한다. 오늘 날짜의 발행 시각이 지났고 `(group_id, mission_date, slot_no)`에 미션이 없으면 발행한다. 서버 중단 중 놓친 오늘 슬롯은 다음 실행에서 따라잡으며 지난 날짜는 소급하지 않는다. UNIQUE 제약으로 중복 발행을 막고, 그룹별 예외를 기록한 뒤 다음 그룹을 계속 처리한다.

그룹 생성 트랜잭션이 커밋되면 첫 발행을 시도한다. 오늘 발행 시각이 이미 지났으면 바로 열린다. 생성 응답의 `missionCount`는 커밋 이후 발행 전 값인 0일 수 있으므로 상세 조회에서 갱신한다. 발행 시각은 예정 시각과 그룹 생성 시각 중 늦은 값이다. `deadlineAt`은 발행 이후 처음 오는 슬롯 마감 시각이며, `closesAt`은 응답 시점에 계산하는 다음 발행 예정 시각이다.

현재 내용 생성은 `MissionGenerator` 뒤의 고정 문장 `PlaceholderMissionGenerator`가 담당한다. 틀마다 3개 이상의 변형을 갖고 직전 미션과 같은 문장이면 다음 변형을 선택한다. AI 호출, 생성 결과 검증, 알림 발송은 아직 구현되지 않았다. 생성 오류는 기록되며 다음 분 실행에서 재시도한다.

AI 응답에 대한 서버 검증 (AI가 규칙을 어길 수 있으므로 프롬프트와 별개로 반드시 수행):

| 검증 | 내용 |
|---|---|
| 틀 | 응답이 서버가 정한 `template`의 `params` 형식을 따르는가 (AI가 다른 틀로 바꾸지 못함). 2차 틀은 구현 전까지 선택 대상에서 제외 |
| 빈칸 | 틀의 `params`가 모두 채워졌는가, 불필요한 키가 없는가 |
| 삼행시 | `word`가 정확히 한글 3글자인가 |
| 밸런스 | `optionA` ≠ `optionB` |
| 슬라이더 | `left` ≠ `right`, 각각 짧은 단어(≤ 20자) |
| 빈칸 채우기 | `sentence`에 `___`가 정확히 1개 |
| 카테고리 | 주어진 목록에 있는 값인가 |
| 연속 방지 | 최근 미션 및 같은 날 먼저 열린 미션과 같거나 거의 같은 문장이 아닌가 (틀은 서버가 정하므로 확인 대상이 아님) |

- 분당 전체 그룹을 순회하므로 그룹 수가 늘면 스케줄러 개선이 필요하다. `(group_id, mission_date, slot_no)` UNIQUE 제약이 중복 실행을 막는다.
- 시각 결정: 슬롯 N의 실제 발행 시각 = `group_mission_slots.publish_time`, 행이 없거나 NULL이면 설정 파일의 기본 발행 시각. 마감도 같다.

### 6-1. 틀 선택 규칙 (서버가 정함, AI 개입 없음)

MVP 발행 풀은 `PHOTO_NOW`, `PHOTO_GALLERY`, `PHOTO_COLOR`, `VOICE_LINE`, `DRAW_SKETCH`, `DRAW_A_TO_B`, `TEXT_ACROSTIC`, `TEXT_FILL_BLANK`의 8종이다. 매체는 PHOTO/VOICE/TEXT/DRAW 4종이다. `GET /mission-templates`는 기존 16종 전체를 반환한다.

| # | 규칙 | 강도 |
|---|---|---|
| 1 | 후보는 1차 틀(§1-2)만 쓴다 | 필수 |
| 2 | **하루 미션이 2개 이상일 때:** 4매체 순서를 슬롯별로 순환한다. MVP에서는 하루 1개만 허용한다 | 향후 확장 |
| 3 | **하루 미션이 1개일 때:** 어제 같은 그룹이 쓴 매체를 제외한다 (어제와 같은 매체가 이어지지 않게) | 기본, 후보 부족 시 예외 허용 |
| 4 | 그룹 관계에 따른 선호·가중치는 **두지 않는다.** 관계는 AI가 문장의 톤과 금지 주제를 정하는 입력으로만 쓴다 | 확정 |
| 5 | **대상 지정:** `TEXT_FILL_BLANK`는 항상, `DRAW_SKETCH`와 `DRAW_A_TO_B`는 결정적 시드에 따른 확률 0.3으로 지정한다. 다른 틀은 대상을 지정하지 않는다 | 구현됨 |

**후보 부족 시 예외 허용:** 규칙 2·3을 지키면 고를 수 있는 틀이 없을 때만 완화한다.
- 하루 1개: 어제 매체 제외를 풀고 고른다 (예: 어제 5개 매체를 모두 썼을 때).
- 하루 2개 이상: 구현된 틀이 있는 매체가 슬롯 수보다 적을 때만 같은 날 매체 중복을 허용한다.
- 위로도 후보가 없으면 예비 미션 풀을 쓴다 (§6).

**계산 방식 (결정적, 동시성 문제 없음):**
```
mediaOrder = shuffle(매체 4종, seed = seed(groupId, 날짜, 0))
N >= 2 : 슬롯 k의 매체 = mediaOrder[(k-1) % 4]
N == 1 : mediaOrder에서 어제 미션들이 쓴 매체를 뺀 첫 번째 (모두 빠지면 mediaOrder[0])
틀     : 그 매체의 MVP 풀 틀 중 하나를 seed(groupId, 날짜, slotNo)로 선택
```
- 읽는 DB 데이터는 **어제의 미션뿐**이다. 오늘 슬롯끼리는 서로 조회하지 않으므로, 같은 시각에 여러 슬롯이 동시에 발행되어도 결과가 같고 경쟁이 없다.
- 같은 `(그룹, 날짜, 슬롯)`을 다시 실행해도 같은 결과가 나온다 (멱등). `(group_id, mission_date, slot_no)` UNIQUE 제약이 중복 저장을 막는다.
- 어제 미션이 없는 그룹(첫날, 발행이 없었던 날)은 제외 없이 고른다.
- 대상을 지정하는 틀의 안전 검토는 §8에서 따로 다룬다.
- AI에게 줄 대상 표기: 대상이 있는 틀에서 AI는 이름 대신 `[대상]`을 문장에 쓰고, 서버가 응답할 때 현재 닉네임으로 바꿔 넣는다 (초안, 닉네임이 바뀌어도 최신 이름이 보이도록).

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

- **`TEXT_FILL_BLANK` (랜덤 사람 지정 빈칸 채우기)**: MISSIONS.md의 예시 문장은 특정 멤버를 비하하는 내용이다. 이 문서에는 옮기지 않았다. 이 틀은 구조상 **여러 명이 한 명에 대해 쓰는** 형태라 미션 안전 규칙(`PROJECT_OVERVIEW.md` §7)과 충돌한다 (웬수: "여러 명이 한 명을 공격하게 되는 미션" 금지, 친구: "특정 친구 한 명을 지목해서 놀리는 미션" 금지, 공통: 혐오·비하 금지, 외모·몸 금지). 사용하려면 (a) 긍정적 문장만 허용, (b) 그룹 관계별 허용 여부, (c) 신고/삭제 수단 중 무엇을 둘지 먼저 정해야 한다.
- **`DRAW_SKETCH`, `DRAW_A_TO_B`의 대상 지정**: 설정된 확률로 대상이 지정된다. "[대상]을 동물로 그리기"가 외모 비하가 되지 않도록 프롬프트가 성격·분위기·좋아할 것 같은 것 등 칭찬하거나 가벼운 방향으로 제한한다. 관계별 선호를 두지 않기로 했으므로 가족, 그저그런사이에도 같은 확률이 적용된다.
- **`VOICE_MIMIC`, `VOICE_IMPRESSION`**: 무엇을 흉내 낼지는 제출자가 정하며, 이에 대한 별도 제한이나 신고 수단은 두지 않는다 (의도된 결정).
- **`PHOTO_GALLERY`**: 갤러리 사진에는 얼굴·위치·개인정보가 있을 수 있다. 조건(`condition`)을 만들 때 해당 주제를 피하도록 프롬프트 규칙을 유지한다.
- **프롬프트 계약**: 프롬프트 파일은 이 저장소에서 제거되었고 팀원이 따로 관리한다. 프롬프트가 지켜야 할 약속은 다음과 같다. (1) 제출 평가는 하지 않고 미션 생성만 한다. (2) 틀은 서버가 정해서 입력으로 주며 AI는 고르거나 바꾸지 않는다. (3) 입력: 날짜·요일, 그룹 관계(문장의 톤과 금지 주제용, 틀 선택에는 쓰지 않음), 선택된 틀과 빈칸 정의, 대상 멤버 유무, 최근 미션(같은 날 먼저 열린 슬롯 포함), 카테고리 목록. (4) 출력은 `content`, `category`, `params` 세 가지이고 `params`의 빈칸 이름은 §1 코드표를 따른다. (5) 대상 멤버가 있으면 이름 대신 `[대상]`을 쓴다. (6) 성대모사·소리 흉내는 빈칸 없이 "~를 맞춰보세요" 문장을 쓴다. (7) 프롬프트에 중괄호 `{ }`를 쓰지 않는다 (Spring AI가 변수로 해석함). 틀이 추가·변경되면 §1 코드표를 고치고 팀원에게 알린다.

---

## 9. DB 변경 (제안, 마이그레이션 V3 이후)

| 테이블 | 변경 |
|---|---|
| `groups` | `relation_type VARCHAR(20) NOT NULL DEFAULT 'CASUAL'` 추가 (그룹 관계, 코드 유효성은 서비스에서 검증). `daily_mission_count TINYINT UNSIGNED NOT NULL DEFAULT 1` 추가. `mission_publish_time`, `mission_deadline_time` **삭제** (현재 데이터가 없어 이전 없이 삭제) |
| `group_mission_slots` (신규) | 슬롯별 발행·마감 시각 지정. 컬럼: `group_id`, `slot_no`, `publish_time NULL`, `deadline_time NULL`. 행이 없거나 값이 NULL이면 설정 파일의 기본 시각을 사용. `(group_id, slot_no)` UNIQUE |
| `missions` | `template VARCHAR(40) NOT NULL`, `params JSON NOT NULL`, `slot_no TINYINT UNSIGNED NOT NULL DEFAULT 1`, `published_at DATETIME NOT NULL`, `deadline_at DATETIME NOT NULL` 추가. UNIQUE `(group_id, mission_date, slot_no)` 추가 (중복 발행 방지) |
| `submissions` | `image_url NOT NULL` → `media_url NULL`로 변경. `media_type`, `number_value`, `choice_value`, `answer_data`(JSON: 글 본문 `text`, 다수결 예측값 `guess` 등 집계하지 않는 답), `answer_text`(정답), `answer_aliases`(별칭 JSON 배열) 추가 (모두 NULL 허용). 요청 필드(`text`, `guess`)는 그대로이고 저장만 다르다: `number`→`number_value`, `choice`→`choice_value`, `text`→`answer_data.text`, `guess`→`answer_data.guess` (`BACKEND_DB.md` "제출 답 저장 방식"). `(mission_id, user_id)` UNIQUE 추가 (`ALREADY_SUBMITTED`를 DB가 보장, 동시 제출 방지) |
| `submission_guesses` (신규) | 성대모사 정답 시도 기록. 컬럼: `id`, `submission_id`(FK), `user_id`(FK), `guess_text VARCHAR(100) NOT NULL`, `is_correct BOOLEAN NOT NULL`, `created_at DATETIME`. `(submission_id, user_id)` 인덱스(UNIQUE 아님, 여러 번 시도 가능) |

- 기존 `group_members` UNIQUE 제약처럼 **동시 요청에서의 중복은 DB 제약으로 막고** 서비스에서 `DuplicateKeyException`을 `ALREADY_SUBMITTED`로 변환한다.
- `submissions.caption`의 `TEXT` 길이는 서비스에서 제한한다 (200자).
- 컬럼별 상세 정의는 `BACKEND_DB.md`의 "미션/제출/그룹 스케줄 스키마 변경 상세"를 기준으로 한다.

### 다른 문서에 미치는 영향 (이 문서에서는 수정하지 않음)
- `API_DESIGN.md` §7 (Calendar)의 `submission.imageUrl` → `mediaUrl` 계열로 맞춰야 함. 하루에 미션이 여러 개일 수 있으므로 `GET /me/calendar/{date}`의 그룹별 `mission`/`submission`도 목록 구조로 바꿔야 함.
- 이미 구현된 그룹 API(`GroupController`, `GroupService`, `GroupMapper`, `Group` 도메인, DTO)는 `dailyMissionCount`, `missionSlots`를 모르고, 삭제되는 `missionPublishTime`/`missionDeadlineTime`을 아직 사용한다. 그룹 생성·수정·조회의 요청/응답과 SQL을 별도 구현 작업으로 바꿔야 한다.
- `GroupMemberMapper.findMembersByGroupId`의 `submissionCountThisMonth`는 현재 제출 행 수를 센다. 하루 미션이 여러 개가 되면 "제출한 날 수"와 의미가 달라지므로 기준을 정해야 한다 (§11).
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
| `GUESS_NOT_SUPPORTED` | 400 | 정답이 없는 틀(성대모사가 아닌 제출물)에 정답을 시도함 |
| `CANNOT_GUESS_OWN_SUBMISSION` | 409 | 본인 제출물에 정답을 시도함 |
| `ALREADY_SOLVED` | 409 | 이미 정답을 맞힘 |
| `GUESS_LIMIT_EXCEEDED` | 409 | 시도 횟수를 모두 사용함 |

## 11. 미정 사항 (구현 전에 결정 필요)

1. (결정됨, 정리 완료) AI는 제출을 평가하지 않는다. 도장은 제출 기록만으로 계산하고 판정 정책·필드는 없다. 판정 프롬프트는 삭제했다.
2. 하루가 끝난 뒤(예: 자정) 제출을 완전히 막을 것인가? 현재는 마감 후에도 `isLate`로 제출 가능하고 상한이 없다.
3. "오늘"의 기준 시간대. DB `CURDATE()`와 서버 시간대를 `Asia/Seoul`로 통일할지 (그룹 API의 `submittedToday`와 동일 문제).
4. (결정됨) 틀 중복·순환 규칙은 §6-1이다. 그룹 관계별 허용 틀 목록은 두지 않는다.
5. `SLIDER_CONSENSUS`의 승리 판정 해석 확인 (§1-2).
6. 대상 지정 틀에서 대상 본인이 제출할 수 있는가, 대상이 도중에 탈퇴하면 어떻게 표시하는가.
7. 파일 한도·허용 형식의 확정값, 음성 길이 검증 방식 (서버에서 오디오 길이를 읽을 수 있는지).
8. **도장 기준 (하루에 미션이 여러 개일 때).** 선택지: (a) 하나라도 제출하면 참잘했어요(현행 규칙 유지, 임시 기준), (b) 그날 열린 미션을 모두 제출해야 참잘했어요, (c) 미션별 도장. (b)나 (c)를 택하면 `PROJECT_OVERVIEW.md`의 도장 규칙, 달력 API, `stampedToday` 계산이 모두 바뀐다.
9. 멤버 목록의 `submissionCountThisMonth`를 "제출 수"로 둘지 "제출한 날 수"로 바꿀지.
10. 하루 미션 수 상한(현재 초안 5)과 슬롯 간 최소 간격 제한 여부. 모든 슬롯이 같은 시각이면 같은 시각에 N개가 한꺼번에 열린다 (허용하는 것으로 가정).
11. 슬롯의 마감이 발행보다 이르거나 자정을 넘기는 경우 허용 여부 (그룹 설정의 기존 미정 사항과 동일).
12. 설정 변경 적용 시점 규칙(§2 "그룹 설정 연동")이 의도와 맞는지.
13. 피드 잠금 단위. 현재는 **미션별**로 제출해야 그 미션의 피드를 볼 수 있다. 하루 단위(그날 하나라도 제출하면 모두 열람)로 할지.
14. (결정됨) 서버 기본 시각은 설정 파일 `hawe.mission.*`에 둔다 (§2). 값(초안 발행 09:00 / 마감 23:00)과 `max-daily-count`(초안 5)는 확정이 필요하다.
15. (결정됨) 틀 선택 규칙은 §6-1: 하루 여러 개는 같은 날 매체 중복 금지, 하루 1개는 어제와 같은 매체 제외, 후보 부족 시 예외 허용, 그룹 관계별 선호는 두지 않음. 남은 일: 매체 목록·시드 계산 방식 구현 시 확정.
16. (결정됨) 그림 틀(`DRAW_SKETCH`, `DRAW_A_TO_B`)은 설정된 확률로 대상 멤버를 지정한다 (§6-1 규칙 5, 초안 0.3). 확률 값은 확정이 필요하고, 안전 검토는 §8에서 이어진다.
17. (결정됨) 소리 흉내(`VOICE_MIMIC`)도 성대모사와 같은 정답 맞히기(G 유형)를 쓴다 (§4-1).
18. (결정됨) 별칭은 정답으로 인정하고 오타는 허용하지 않는다 (정규화 후 완전 일치만, §4-1). 남은 일: 별칭 최대 개수(5) 확정.
19. (결정됨) 정답을 맞혀도 제출자에게 알림을 보내지 않는다.
