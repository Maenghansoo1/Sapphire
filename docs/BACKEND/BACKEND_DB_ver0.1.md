# HA'WE — 백엔드 구성요소 설계 (ver0.1)

## 계층 구조
- **Controller** (REST API 진입점) → **Service** (비즈니스 로직) → **DAO** (MariaDB 접근, MyBatis)
- 부가 저장소: **로컬 파일 시스템**(이미지·음성, 개발 환경) / **S3**(이미지·음성, 운영 환경 전환 예정), **LLM**(미션 생성·판정, 제공사 미정), **Qdrant**(벡터 검색, 도입 시 - 필수 X), **SNS/EventBridge**(알림·스케줄)

---

## 1. 도메인 엔티티 & MariaDB 테이블

```mermaid
erDiagram
  CHARACTERS ||--o{ USERS : "selected by"
  USERS ||--o{ GROUPS : owns
  USERS ||--o{ GROUP_MEMBERS : joins
  GROUPS ||--o{ GROUP_MEMBERS : has
  GROUPS ||--o{ GROUP_MISSION_SLOTS : customizes
  GROUPS ||--o{ MISSIONS : publishes
  MISSIONS ||--o{ SUBMISSIONS : receives
  USERS ||--o{ SUBMISSIONS : writes
  SUBMISSIONS ||--o{ COMMENTS : has
  USERS ||--o{ COMMENTS : writes
  COMMENTS ||--o{ COMMENT_MENTIONS : tags
  USERS ||--o{ COMMENT_MENTIONS : "mentioned in"
  SUBMISSIONS ||--o{ REACTIONS : has
  USERS ||--o{ REACTIONS : reacts
  USERS ||--o{ POKES : sends
  USERS ||--o{ POKES : receives
  GROUPS ||--o{ POKES : scopes
  USERS ||--o{ NOTIFICATIONS : sends
  USERS ||--o{ NOTIFICATIONS : receives

  USERS {
    bigint id PK
    string nickname
    string login_id UK
    string password_hash
    string email
    bigint character_id FK
    datetime created_at
  }
  GROUPS {
    bigint id PK
    string name
    bigint owner_id FK
    int daily_mission_count
    datetime created_at
  }
  GROUP_MISSION_SLOTS {
    bigint id PK
    bigint group_id FK
    int slot_no
    time publish_time
    time deadline_time
  }
  GROUP_MEMBERS {
    bigint id PK
    bigint group_id FK
    bigint user_id FK
    string role
    datetime joined_at
  }
  MISSIONS {
    bigint id PK
    bigint group_id FK
    string content
    string category
    string template
    json params
    int slot_no
    datetime published_at
    datetime deadline_at
    date mission_date
  }
  SUBMISSIONS {
    bigint id PK
    bigint mission_id FK
    bigint user_id FK
    string media_url
    string media_type
    string text_value
    int number_value
    int guess_value
    string choice_value
    string caption
    datetime submitted_at
    boolean is_late
  }
  COMMENTS {
    bigint id PK
    bigint submission_id FK
    bigint user_id FK
    string content
    string image_url
    datetime created_at
  }
  COMMENT_MENTIONS {
    bigint id PK
    bigint comment_id FK
    bigint mentioned_user_id FK
  }
  REACTIONS {
    bigint id PK
    bigint submission_id FK
    bigint user_id FK
    string sticker_type
  }
  POKES {
    bigint id PK
    bigint from_user_id FK
    bigint to_user_id FK
    bigint group_id FK
    string type
    datetime sent_at
  }
  NOTIFICATIONS {
    bigint id PK
    bigint from_user_id FK
    bigint to_user_id FK
    string type
    string payload
    boolean is_read
    datetime created_at
  }
  CHARACTERS {
    bigint id PK
    string name
    string svg_key
  }
```

**비고**
- `USERS` 인증 방식 — 소셜 로그인 대신 **자체 로그인**(`login_id` + `password_hash`) 사용. `email`은 부가 정보로 저장
- `POKES.type` — `poke`(콕찌르기) / `fling`(똥뿌리기)
- `SUBMISSIONS.is_late` — 해당 미션의 `MISSIONS.deadline_at`과 `submitted_at`을 비교해 제출 시점에 계산·저장 (정책 확정)
- `SUBMISSIONS.media_url` — 이미지·음성 파일 위치. 개발 환경은 로컬 경로 저장, 운영 환경은 S3 URL로 전환 예정. 파일이 없는 틀(글/슬라이더)에서는 NULL
- `MISSIONS.template` / `MISSIONS.params` — 미션 틀(예: `TEXT_ACROSTIC`)과 그 빈칸 값(JSON). 틀 목록과 `params` 모양은 `docs/BACKEND/MISSIONS_API.md` §1이 기준
- 틀에 따라 쓰는 제출 컬럼이 다르다 (사진·그림·음성=`media_*`, 글=`text_value`, 슬라이더·별점=`number_value`, 다수결 예측=`number_value`+`guess_value`, 밸런스 게임=`choice_value`). 쓰지 않는 컬럼은 NULL이며, 틀별 필수 여부는 DB가 아니라 서비스가 검증한다
- 하루 미션 수 — `GROUPS.daily_mission_count`(1~5, 기본 1)만큼 슬롯(`MISSIONS.slot_no` 1, 2, …)이 열린다. 슬롯별 발행·마감 시각은 `GROUP_MISSION_SLOTS`가 전담하며, **행이 없거나 값이 NULL이면 서버 코드의 기본 시각**(발행 09:00, 마감 23:00 — 초안)을 쓴다. `GROUPS`에는 시각 컬럼이 없다
- `MISSIONS.published_at` / `deadline_at` — 발행 시점의 실제 발행·마감 시각을 고정 저장. 이후 그룹 설정이 바뀌어도 이미 열린 미션의 시각과 지각 판정(`SUBMISSIONS.is_late`)은 바뀌지 않는다
- 유일성 제약 — `GROUP_MISSION_SLOTS (group_id, slot_no)`: 슬롯 재정의는 슬롯당 1행, `MISSIONS (group_id, mission_date, slot_no)`: 그룹·날짜·슬롯당 미션 1개(중복 발행 방지), `SUBMISSIONS (mission_id, user_id)`: 미션당 1인 1회 제출(`ALREADY_SUBMITTED`)
- `NOTIFICATIONS`는 `from_user_id`(발신자, 시스템 알림은 NULL) / `to_user_id`(수신자)로 분리
- 도장(참잘했어요/벌점)은 별도 테이블 없이 `SUBMISSIONS` 존재 여부로 계산 (해당 그룹·날짜에 제출 기록이 있으면 참잘했어요, 없으면 벌점). **하루 미션이 여러 개일 때의 기준은 미정**이며 임시로 "하나라도 제출하면"을 유지한다 (`MISSIONS_API.md` §11)

상세 ERD/FK 참조 흐름/컬럼 설명은 `docs/DB/DB_DESIGN_ver0.1.md` 참고.

### 미션/제출/그룹 스케줄 스키마 변경 상세 (V3 마이그레이션 기준)

근거: `docs/BACKEND/MISSIONS_API.md` §2 (그룹 설정 연동), §9. 현재 상태는 `V1__initial_schema.sql`, `V2__unique_group_members.sql`이다.

**`groups`**

| 컬럼 | 변경 | 타입 | NULL | 설명 |
|---|---|---|---|---|
| `daily_mission_count` | 추가 | `TINYINT UNSIGNED` | NOT NULL, DEFAULT 1 | 하루에 열 미션 수. 허용 범위 1~5는 서비스에서 검증 (상한은 초안). DEFAULT 1이라 기존 그룹은 동작이 그대로다 |
| `mission_publish_time` | **삭제** | `TIME` | - | 슬롯 테이블로 대체. 삭제 전에 기존 값을 슬롯 1번 행으로 이전 (아래 마이그레이션 참고) |
| `mission_deadline_time` | **삭제** | `TIME` | - | 위와 같음 |

**`group_mission_slots` (신규 테이블)** — 슬롯별 시각 재정의. 재정의하지 않은 슬롯은 행이 없다.

| 컬럼 | 타입 | NULL | 설명 |
|---|---|---|---|
| `id` | `BIGINT AUTO_INCREMENT` | PK | |
| `group_id` | `BIGINT` | NOT NULL | FK → `groups.id` (제약명 제안: `fk_group_mission_slots_group`) |
| `slot_no` | `TINYINT UNSIGNED` | NOT NULL | 1~`daily_mission_count`. 범위 검증은 서비스에서 수행 |
| `publish_time` | `TIME` | NULL | NULL이면 서버 코드의 기본 발행 시각 사용 |
| `deadline_time` | `TIME` | NULL | NULL이면 서버 코드의 기본 마감 시각 사용 |
| `(group_id, slot_no)` | UNIQUE | - | 슬롯당 1행. 제약명 제안: `uk_group_mission_slots_group_slot` |

- 실제 적용 시각은 `COALESCE(slot.publish_time, 서버 기본 발행 시각)` 방식으로 계산한다(마감도 동일). 슬롯 1~N 전체의 유효 시각은 행이 없는 슬롯까지 서비스가 펼쳐서 만든다. 기본 시각은 DB가 아니라 코드에 있으므로 값을 바꾸면 지정하지 않은 모든 그룹에 적용된다.
- 설정 API에서 `missionSlots`를 보내면 전체 교체이므로, 서비스는 해당 그룹의 행을 지우고 다시 넣는다. `daily_mission_count`를 줄이면 범위 밖 `slot_no` 행은 함께 지운다.

**`missions`**

| 컬럼 | 변경 | 타입 | NULL | 설명 |
|---|---|---|---|---|
| `template` | 추가 | `VARCHAR(40)` | NOT NULL | 틀 코드 (`PHOTO_NOW`, `TEXT_ACROSTIC` 등). 코드값 검증은 서비스에서 수행 (DB ENUM/CHECK 사용 안 함 — 틀이 늘 때마다 마이그레이션이 필요해지므로) |
| `params` | 추가 | `JSON` | NOT NULL | 틀별 빈칸 값. 빈칸이 없는 틀은 `{}`. 대상 지정 틀은 `targetUserId` 포함 |
| `slot_no` | 추가 | `TINYINT UNSIGNED` | NOT NULL, DEFAULT 1 | 그날 몇 번째 미션인지 (1부터) |
| `published_at` | 추가 | `DATETIME` | NOT NULL | 실제 발행 시각 (발행 시점의 유효 시각을 고정) |
| `deadline_at` | 추가 | `DATETIME` | NOT NULL | 마감 시각. `is_late` 판정 기준 |
| `(group_id, mission_date, slot_no)` | UNIQUE 추가 | - | - | 그룹·날짜·슬롯당 미션 1개 (중복 발행 방지). 제약명 제안: `uk_missions_group_date_slot` |

- `mission_date`는 `published_at`의 날짜다. 마감이 자정을 넘기는 경우는 미정이다 (`MISSIONS_API.md` §11).

**`submissions`**

| 컬럼 | 변경 | 타입 | NULL | 설명 |
|---|---|---|---|---|
| `image_url` | `media_url`로 이름 변경, NULL 허용 | `VARCHAR(500)` | NULL | 글/슬라이더 제출은 파일이 없음 |
| `media_type` | 추가 | `VARCHAR(10)` | NULL | `IMAGE` / `AUDIO`. `media_url`이 있을 때만 값이 있음 |
| `text_value` | 추가 | `VARCHAR(500)` | NULL | 글 본문 (삼행시 3줄, 한 단어, 한 줄 후기, 빈칸 채우기). 줄바꿈 포함 |
| `number_value` | 추가 | `TINYINT UNSIGNED` | NULL | 별점(1~5) 또는 슬라이더 위치(0~100) |
| `guess_value` | 추가 | `TINYINT UNSIGNED` | NULL | 다수결 예측의 예측값(0~100) |
| `choice_value` | 추가 | `CHAR(1)` | NULL | 밸런스 게임 선택 `A` / `B` |
| `caption` | 변경 없음 | `TEXT` | NULL | 보조 설명. 길이 제한(200자)은 서비스에서 수행 |
| `(mission_id, user_id)` | UNIQUE 추가 | - | - | 미션당 1인 1회. 제약명 제안: `uk_submissions_mission_user` |

**마이그레이션 시 확인할 것**
- 기존 테이블에 추가하는 두 UNIQUE 제약(`missions`, `submissions`)은 기존 데이터에 중복이 있으면 추가에 실패한다. (`group_mission_slots`는 새 테이블이라 해당 없음) `V2__unique_group_members.sql`처럼 제약 추가 전에 중복을 정리(가장 작은 `id`만 유지)해야 한다. 현재는 미션/제출을 만드는 코드가 없어 데이터가 거의 없을 것으로 예상되지만 개발 DB는 직접 확인한다.
- `missions`의 새 NOT NULL 컬럼은 기존 행이 있으면 값을 채워야 한다.
  - `slot_no`: DEFAULT 1로 자동 채워진다.
  - `template`, `params`: 기존 제출은 모두 이미지였으므로 `'PHOTO_NOW'`, `'{}'`을 권장한다.
  - `published_at`, `deadline_at`: 컬럼을 NULL 허용으로 먼저 추가하고, 그룹의 시각으로 채운 뒤 NOT NULL로 바꾼다. 예: `TIMESTAMP(m.mission_date, g.mission_publish_time)`, `TIMESTAMP(m.mission_date, g.mission_deadline_time)` (`groups`와 JOIN). **이 백필은 `groups`의 시각 컬럼을 삭제하기 전에 해야 한다.**
  - 행이 없으면 위 백필은 불필요하다.
- `submissions` 기존 행은 `media_url`로 이름을 바꾸면서 `media_type='IMAGE'`로 채운다.
- **`groups` 시각 컬럼 제거 순서:** (1) `missions`의 `published_at`/`deadline_at` 백필, (2) 기존 그룹의 시각을 `group_mission_slots`의 슬롯 1번 행으로 이전(`INSERT ... SELECT id, 1, mission_publish_time, mission_deadline_time FROM groups`, 서버 기본 시각과 같은 그룹은 생략 가능), (3) 그 다음 `groups`의 두 컬럼 `DROP COLUMN`. 순서를 어기면 기존 그룹의 설정값이 사라진다.
- 코드 영향
  - 메인 코드의 `GroupMapper.findSummariesByUserId`, `GroupMemberMapper.findMembersByGroupId`는 `submissions`/`missions`를 `mission_id`, `user_id`, `group_id`, `mission_date`로만 읽으므로 컬럼 변경에는 영향이 없다. 다만 하루 미션이 여러 개가 되면 `submissionCountThisMonth`(제출 행 수)의 의미가 달라진다 (`MISSIONS_API.md` §11).
  - **`GroupMapperTests`(`summariesAndMembersMapSubmissionValues`)가 `submissions`에 `image_url`로, `missions`에 `template`/`params`/`published_at`/`deadline_at` 없이 직접 INSERT하므로 V3 적용 후 실패한다.** V3와 함께 테스트의 INSERT를 새 컬럼 기준으로 고쳐야 한다. (`comments.image_url`은 다른 테이블이라 이번 변경 대상이 아니다.)
  - 그룹 모듈(`Group` 도메인, `GroupMapper`의 INSERT/UPDATE/SELECT, `GroupCreateRequest`, `GroupSettingsUpdateRequest`, `GroupResponse`, `GroupService`)은 삭제되는 `missionPublishTime`/`missionDeadlineTime` 대신 `daily_mission_count`와 슬롯 지정을 다루도록 별도 수정이 필요하다. `GroupMapperTests`의 `insertGroup`도 시각 세팅을 제거해야 한다.
- 추가 인덱스는 필요 없다. `missions`의 UNIQUE 제약이 그룹별 날짜 조회의 인덱스 역할을 하고, `submissions`의 UNIQUE 제약이 피드 조회(`mission_id`)의 인덱스 역할을 하며, `user_id`와 `group_mission_slots.group_id`는 FK 인덱스가 있다.

---

## 2. 서비스 컴포넌트

| Service | 책임 |
|---|---|
| `AuthService` | 회원가입/로그인(자체 인증), 비밀번호 해싱, JWT 발급·검증 |
| `GroupService` | 그룹 생성/초대/설정(하루 미션 수, 슬롯별 시각)/멤버 관리 |
| `MissionService` | 슬롯별 미션 발행(스케줄러 트리거 → LLM 호출 → 서버 검증 → 저장, 실패 시 예비 미션), 오늘 열린 미션 목록·단건 조회 |
| `MissionScheduleService` | 그룹의 슬롯별 유효 발행·마감 시각 계산(지정 없으면 서버 코드의 기본 시각), 설정 변경 시 스케줄 재등록, 다음 발행 예정 시각 계산 |
| `MissionTemplateValidator` | 틀별 `params` 검증(미션 생성 시)과 제출 필드 검증(제출 시). 틀마다 검증기 1개 |
| `SubmissionService` | 제출 처리(지각 판정, 중복 제출 차단), 잠금 해제 여부 판단 |
| `MissionResultService` | 집계 틀(밸런스 게임, 슬라이더 3종)의 그룹 결과 계산 |
| `FileStorageService` | 이미지·음성 파일 저장/조회 (개발=로컬, 운영=S3로 전환), 형식·크기 검증 |
| `FeedService` | 그룹 피드 조회 (제출자만 열람 가능하도록 필터링) |
| `CommentService` | 댓글 작성, @멘션 파싱, 사진 첨부 |
| `ReactionService` | 스티커 반응 등록/집계 |
| `PokeService` | 콕찌르기/똥뿌리기 전송 → 알림 트리거 |
| `NotificationService` | 알림 생성·조회·읽음 처리, SNS 푸시 발송 |
| `CalendarService` | 마이페이지 달력/기록 집계 (사용자 기준 전체 그룹 통합) |
| `CharacterService` | 캐릭터 목록 조회, 프로필 캐릭터 선택 |
| `RecallService` (선택) | Qdrant 기반 — 과거 비슷한 감정의 게시물 찾기 |

---

## 3. API 엔드포인트

### 인증
- `POST /api/auth/register` — 회원가입 (`loginId`, `password`, `nickname`, `email`)
- `POST /api/auth/login` — 로그인 (`loginId`, `password`) → JWT 발급
- `POST /api/auth/refresh` — 토큰 갱신

### 그룹
- `GET /api/groups` — 내 그룹 목록 (오늘 제출 여부 포함)
- `POST /api/groups` — 그룹 생성 (`name`, 선택: `dailyMissionCount`, `missionSlots`)
- `GET /api/groups/{groupId}/members` — 멤버 목록 + 이번 달 제출 횟수
- `PATCH /api/groups/{groupId}/settings` — 그룹명/하루 미션 수(`dailyMissionCount`)/슬롯별 시각(`missionSlots`) 변경
- `POST /api/groups/{groupId}/invite` — 초대 링크 발급
- `DELETE /api/groups/{groupId}/members/me` — 그룹 나가기

### 미션 & 제출 (상세 명세: `docs/BACKEND/MISSIONS_API.md`)
- `GET /api/groups/{groupId}/missions/today` — 오늘 열린 미션 목록(슬롯 순서, 틀 `template` + 빈칸 `params` 포함) + 내 제출 여부 + 다음 발행 예정 시각
- `GET /api/missions/{missionId}` — 미션 단건
- `GET /api/mission-templates` — 틀 목록과 빈칸/제출 형식
- `POST /api/missions/{missionId}/submissions` — 미션 제출 (`multipart/form-data`: `file`, `text`, `number`, `guess`, `choice`, `caption` 중 틀이 요구하는 필드만)
  - 개발/운영 환경 공통으로 서버가 파일을 직접 받음 (API 계약 동일)
  - 저장 위치만 환경별로 다름: 개발 환경은 로컬 디스크, 운영 환경은 내부적으로 S3 업로드 (presigned URL 방식은 사용하지 않음 — 클라이언트 변경 없이 저장소만 전환)
- `GET /api/missions/{missionId}/submissions` — 그룹 피드 (내가 제출했을 때만 200, 아니면 423 Locked)
- `GET /api/missions/{missionId}/result` — 집계 틀의 그룹 결과 (제출한 사람만, 집계 틀이 아니면 400)

### 게시물 & 댓글
- `GET /api/submissions/{id}` — 게시물 상세 (미션에 제출한 사람만)
- `POST /api/submissions/{id}/comments` — 댓글 작성 (`@멘션`, `imageUrl` 포함)
- `POST /api/submissions/{id}/reactions` — 스티커 반응

### 재촉 기능
- `POST /api/groups/{groupId}/pokes` — 콕찌르기/똥뿌리기 (`toUserId`, `type`). 보내는 사람(`fromUserId`)은 인증 토큰에서 식별

### 마이페이지
- `GET /api/me/calendar?month=2026-09` — 이번 달 도장 현황 (그룹 통합)
- `GET /api/me/calendar/{date}` — 특정 날짜의 그룹별 미션/제출 상세

### 알림
- `GET /api/notifications` — 알림 목록 (발신자 정보는 `fromUserId`가 있는 경우에만 포함, 시스템 알림은 없음)
- `PATCH /api/notifications/{id}/read` — 읽음 처리

### 캐릭터
- `GET /api/characters` — 선택 가능한 캐릭터 목록
- `PATCH /api/me/character` — 프로필 캐릭터 변경

---

## 4. 비동기·스케줄 처리
- **EventBridge Scheduler** → 매일 그룹별·슬롯별 발행 시각(지정이 없으면 서버 코드의 기본 발행 시각)에 `MissionService.publishDailyMission(groupId, date, slotNo)` 호출
  - 같은 그룹·날짜·슬롯의 미션이 이미 있으면 중단 → LLM으로 `{content, category, template, params}` 생성 → 서버 검증(`MissionTemplateValidator`) → 대상 지정 틀이면 멤버 1명 무작위 선택 → 저장(`published_at`, `deadline_at` 고정) → `mission_open` 알림
  - LLM 실패/검증 실패가 반복되면 예비 미션으로 대체 (상세: `docs/BACKEND/MISSIONS_API.md` §6)
- 제출/댓글/멘션/콕찌르기 발생 시 `NotificationService`가 `NOTIFICATIONS` 테이블에 기록 + **SNS**로 푸시 발송 (비동기, 이벤트 기반)

## 5. Qdrant(VectorDB) 활용 (선택 기능)
- 제출 캡션을 임베딩하여 저장
- "1년 전 오늘과 비슷한 감정의 글" 같은 회고형 기능에 활용 가능 (마이페이지 확장 아이디어)
- 초기 MVP에는 필수 아님 — 위 API에는 포함하지 않음, 추후 `RecallService`로 별도 확장
