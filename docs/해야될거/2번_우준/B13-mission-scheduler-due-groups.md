# B13. 미션 발행 스케줄러: 발행할 그룹만 골라 오기 (백엔드)

> 범위: 백엔드만(앱 변경 없음). 작성 2026-11-09. 근거: B7 계획 Findings 의 "스케줄러 비용: `MissionScheduler` 가 매분 전 그룹 순회(그룹 수 증가 시 개선 필요 — 이번 범위 아님)".
>
> **목표 한 줄**: 매분 **모든 그룹**을 확인하던 방식을, **지금 발행해야 하는 그룹만** SQL 한 번으로 골라 확인하도록 바꿔 DB 조회를 줄인다. 발행 결과(어떤 미션이 언제 열리는지)는 바뀌면 안 된다.

## Goal

- 1분마다 도는 발행 확인이 그룹 수에 비례해 늘어나지 않게 한다. 발행할 게 없는 분에는 **조회 1번**으로 끝난다.
- 발행 시각, 틀 선택, 대상, 마감, 멱등성(같은 슬롯 두 번 발행 안 함)은 지금과 **완전히 같다**.
- 바꾸기 전과 후의 조회 수·소요 시간을 측정해 기록한다(포트폴리오용 수치).

## Findings (2026-11-09 코드 기준)

- `MissionScheduler.publishDueMissions` (`@Scheduled(cron = "0 * * * * *")`): `groups.findAllIds()` 로 **모든 그룹**을 가져와 그룹마다 `publisher.publishIfDue(groupId, SeoulTime.now())` 를 부른다.
- `MissionPublisher.publish` (그룹마다 `REQUIRES_NEW` 트랜잭션): 발행할 게 없어도 아래를 매번 실행한다.
  1. `groups.findById` (122줄)
  2. `slots.findByGroupId` (127줄)
  3. `missions.findTemplatesByGroupIdAndDate(어제)` (129줄) — 어제 매체. **발행할 때만 필요한데 매번 실행**
  4. 슬롯마다 `missions.existsByGroupIdAndDateAndSlot` (142줄)
  → 그룹 G개면 매분 대략 `1 + 4G` 번 조회 + G개의 트랜잭션. 그룹 1만 개면 초당 약 670번.
- 발행 조건(142줄): `now >= scheduledPublishAt(오늘, 발행시각)` 이고 `(group_id, 오늘, slot_no)` 미션이 없을 때. 발행 시각 = `group_mission_slots.publish_time`, 없으면 `hawe.mission.default-publish-time`(09:00). 슬롯 번호는 `1..groups.daily_mission_count`(최대 `max-daily-count`=5).
- 그룹이 발행 시각 뒤에 만들어져도 그날 미션은 나간다(`publishedAt` = 그룹 생성 시각, 144줄). 이 동작도 유지한다.
- 멱등성: `missions` 에 `UNIQUE (group_id, mission_date, slot_no)`(`uk_missions_group_date_slot`) + `DuplicateKeyException` 무시(179줄). `group_mission_slots` 에 `UNIQUE (group_id, slot_no)`.
- **개발자 모드 시계**: `SeoulTime.now()` 는 테스트 모드에서 시간을 옮긴 값이다. 그래서 SQL 에서 `NOW()` 를 쓰면 안 되고, **앱이 계산한 지금 시각을 파라미터로** 넘겨야 한다.
- AI 호출(`generator.generate`, 157줄)은 실제로 발행하는 슬롯에서만 일어난다 → 이번 변경과 비용 무관.

## Root Cause / Key Implementation Points

1. **발행할 그룹만 골라 오는 쿼리** `findDueGroupIds(today, nowTime, defaultPublishTime, slotNumbers)` (`GroupMapper` 또는 `MissionMapper`):
   ```sql
   SELECT DISTINCT g.id
   FROM `groups` g
   JOIN ( /* 슬롯 번호 1..max-daily-count — MyBatis foreach 로 SELECT 1 UNION ALL SELECT 2 ... */ ) n
     ON n.slot_no <= g.daily_mission_count
   LEFT JOIN group_mission_slots s ON s.group_id = g.id AND s.slot_no = n.slot_no
   WHERE COALESCE(s.publish_time, #{defaultPublishTime}) <= #{nowTime}
     AND NOT EXISTS (SELECT 1 FROM missions m
                     WHERE m.group_id = g.id AND m.mission_date = #{today} AND m.slot_no = n.slot_no)
   ORDER BY g.id
   ```
   - 슬롯 번호 목록은 설정값 `max-daily-count` 에서 만든다(5를 SQL 에 고정하지 않는다).
   - `NOT EXISTS` 는 기존 UNIQUE 인덱스를 그대로 탄다. 새 인덱스는 필요 없을 가능성이 높다(`EXPLAIN` 으로 확인해 기록).
   - MariaDB(로컬)와 MySQL(RDS) 둘 다에서 도는 문법만 쓴다.
2. **스케줄러**: `findAllIds()` 대신 `findDueGroupIds(...)` 결과만 순회한다. 그룹마다 `publishIfDue` 를 부르는 구조와 예외 처리(한 그룹 실패가 다른 그룹을 막지 않음)는 그대로 둔다.
3. **안전장치는 그대로**: `publish()` 안의 기존 확인(142줄 시각·중복 검사, UNIQUE 제약)은 지우지 않는다. 새 쿼리는 "미리 거르는 필터"일 뿐이라 쿼리가 실수로 그룹을 더 골라도 결과는 같다. **반대로 덜 고르면 미션이 안 나가므로**, 테스트는 "빠뜨리지 않는가"에 집중한다.
4. **어제 매체 조회 미루기**: `publish()` 의 129줄 조회를 발행할 슬롯이 처음 나왔을 때만 하도록 바꾼다(바로 아래 `groupContext` 가 이미 이렇게 한다, 151줄).
5. **측정**: 바꾸기 전과 후에 같은 조건(예: 그룹 1,000개, 발행할 게 없는 분)에서 한 번의 확인에 걸리는 조회 수·시간을 재서 보고서에 남긴다.

## Files / Functions to Change

| 파일 | 변경 |
|---|---|
| `group/dao/GroupMapper.java` (또는 `mission/dao/MissionMapper.java`) | `findDueGroupIds(...)` 추가 |
| `mission/service/MissionScheduler.java` | 발행할 그룹만 순회. 오늘 날짜·시각은 `SeoulTime.now()` 한 번으로 계산해 쿼리와 `publishIfDue` 에 같은 값을 넘긴다 |
| `mission/service/MissionPublisher.java` | 어제 매체 조회를 필요할 때만(Key Point 4) |
| `global/config/MissionProperties.java` | (필요하면) 기본 발행 시각·`max-daily-count` 를 꺼내 쓰는 메서드 확인 |
| 테스트 | 아래 |
| 문서 | `docs/BACKEND/MISSIONS_API.md` 발행 절(스케줄러가 발행할 그룹만 고른다), 이 계획의 보고서(측정값) |

## Implementation Steps

1. 바꾸기 전 측정: 로컬에 그룹을 많이 만들고(개발자 도구 시드 등) 한 번의 `publishDueMissions` 조회 수·시간을 잰다.
2. `findDueGroupIds` 쿼리 + 매퍼 테스트(실제 DB).
3. 스케줄러 교체 + 스케줄러 테스트.
4. `publish()` 의 어제 매체 조회 미루기.
5. 바꾼 뒤 같은 조건으로 측정, 보고서에 전후 비교.
6. `./mvnw -q clean test` 전체 통과, 문서 정리.

## Constraints

- 발행 결과(시각, 틀, 대상, 마감, 문장 생성 방식)는 바뀌면 안 된다. `MissionTemplateSelectorTests` 지문 값이 그대로여야 한다.
- 개발자 모드 시계를 따라야 한다(SQL `NOW()` 금지).
- 강제 발행(`forcePublish`), 첫 미션(`publishFirstMission`), 다시 뽑기(`reroll`) 경로는 건드리지 않는다.
- DB 구조(V1) 변경 없음 → 로컬 DB 재생성 불필요.

## Edge Cases

- 슬롯 설정 행이 없는 슬롯(기본 발행 시각 사용).
- 그날 `daily_mission_count` 를 늘리거나 줄인 그룹(늘린 슬롯이 발행 시각을 지났으면 바로 발행).
- 발행 시각 00:00 슬롯, 23:59 슬롯.
- 발행 시각이 지난 뒤 만들어진 그룹(지금처럼 그날 미션 발행).
- 같은 그룹의 여러 슬롯이 동시에 발행 대상(그룹은 한 번만 고르고, `publish()` 가 슬롯을 순서대로 처리).
- 개발자 모드로 시계를 옮긴 상태.
- 서버가 몇 분 멈췄다 켜짐(놓친 슬롯을 다음 확인에서 발행).

## Regression Risks

- 쿼리 조건 실수로 **발행할 그룹을 빠뜨리면 미션이 안 열린다**(가장 위험). → 아래 테스트 케이스를 모두 실제 DB 로 검증.
- 시간대 혼동(DB 세션 `+09:00`, 앱 `Asia/Seoul`). 파라미터로 넘기는 값은 모두 앱이 계산한 서울 시각.

## Tests Required

- 매퍼(실제 DB): 시각 전 → 안 고름 / 시각 후 미발행 → 고름 / 이미 발행 → 안 고름 / 슬롯 3개 중 2개만 발행됨 → 고름 / 설정 행 없음(기본 시각) / 하루 미션 수보다 큰 슬롯 번호의 설정 행은 무시 / 그룹 생성이 발행 시각 뒤.
- 스케줄러(목): 고른 그룹에만 `publishIfDue` 호출, 한 그룹 예외가 나머지를 막지 않음, 같은 `now` 를 넘김.
- 기존 `MissionPublisherTests`, `MissionTemplateSelectorTests` 그대로 통과.

## Validation Steps

- `./mvnw -q clean test` 통과.
- 로컬 서버로 확인: 개발자 도구에서 시계를 발행 시각 직전 → 직후로 옮겨 미션이 1분 안에 열리는지.
- 측정 결과(조회 수·시간 전후)를 보고서에 기록.

## Potential Merge Conflicts

- `MissionPublisher.java` 는 10월 20일에도 찬희가 고쳤다. 시작 전에 pull 하고, 끝나면 바로 push.
- 지금 우준 PC 에 커밋 안 된 "30초 스케치 중단"(`MissionTemplateSelector` 등)이 있다. 이번 작업과 파일이 겹치지 않게 먼저 커밋하거나 따로 커밋한다.