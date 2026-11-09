# A3. 업적 — 스케줄 판정 + 누락 보정 스윕

> 범위: 백엔드 `badge/service`. **선행: A1**(A2 의 `BadgeRules` 를 확장). 근거: `BLOCKCHAIN_BADGES.md` §7·§13. 빌드/테스트: `./mvnw test`.

## Goal

제출 이벤트만으로 알 수 없는 업적(스트릭, 월 개근, 인기 글, 기념일, 찰떡 5회)을 **하루 1회 스케줄**로 판정하고, 이벤트 누락(서버 재시작, 큐 폐기)을 보정하는 **스윕**을 겸한다.

## Findings

- 스트릭·개근은 "닫힌 미션" 기준이 필요하다(B9/B11 과 같은 정의: 마감이 지났고 같은 슬롯에 다음 미션이 열렸거나 없어진 슬롯).
- **결정 3 기본값**: 스트릭은 하루에 제출이 하나라도 있으면 그날을 센다(하루 여러 미션이어도), 지각 제출 포함(도장 기준과 동일), 날짜는 서버 시간대 일관의 **`missions.mission_date`**.
- 도장은 `SUBMISSIONS` 존재 여부로 계산한다(`CLAUDE.md`). 스트릭 판정도 같은 입력을 쓴다.
- 데이터: `missions`(`mission_date`, `group_id`, `slot_no`, `deadline_at`), `submissions`, `reactions`, `comments`, `groups`(`created_at`, `start_date`, `relation_type`), `submission_ai_reviews`(찰떡 판정; 점수는 API 비노출 규칙 → **개수만** 센다. 판정은 `badge='PERFECT_FIT'` 같은 저장 컬럼이 있는지 확인; 없으면 `fit_score ≥ 템플릿별 perfect 임계` 를 `hawe.vision.*` 설정으로 계산 — 구현 시 컬럼을 확인하고 보고서에 기록).
- 스케줄러 선례: `mission/service/MissionScheduler.java`(`@Scheduled`). 시간대 유틸 `SeoulTime`(global) 사용.
- 규칙(초안):
  - `STREAK_7/30`: 오늘 기준 연속 제출 일수 ≥ N(오늘 미제출이어도 어제까지 이어졌으면 연속 유지로 보되, **N일 완성**만 수여 — 구현 시 규칙 명확화).
  - `PERFECT_MONTH`: 직전 달의 **닫힌 미션 전부**에 제출(해당 달에 열린 미션이 1개 이상인 그룹 중 하나에서 전부 제출; 그룹 단위 vs 사용자 전체 단위는 **그룹 단위**로 단순화 — 결정 필요 시 보고).
  - `CROWD_FAVORITE`: 한 제출물의 반응+댓글 수 ≥ 20(작성자에게 수여).
  - `GROUP_100_DAYS`, `GROUP_1_YEAR`: 그룹 결성일(또는 COUPLE `start_date`) 기준 100일/1년 도달 시 그 그룹의 멤버에게 수여(수여 시점의 현재 멤버).
  - `PHOTO_PERFECT_5`: 찰떡 판정 제출 5회.
- 어뷰징: 멤버 ≥ 2 인 그룹의 데이터만 사용(A2 와 동일 규칙).

## Key Implementation Points

1. **`BadgeRules` 확장**(A2 의 순수 평가기에 스케줄형 규칙 추가): 입력 스냅샷 확장(`제출한 mission_date 집합`, `월별 닫힌 미션/제출 수`, `제출물별 반응+댓글`, `그룹 기념일 계산용 날짜`, `찰떡 수`). 날짜 계산은 순수 함수(`StreakCalculator`).
2. **스윕 잡** `BadgeSweepJob`: `@Scheduled(cron = "0 30 3 * * *", zone = "Asia/Seoul")`(설정 `hawe.badge.sweep-cron`) — 대상 사용자 = 최근 N일(예: 2일) 활동이 있거나 전날 기준 후보가 될 수 있는 사용자(전체 스캔 금지; 활동 기반 범위를 쿼리로). 사용자별로 A2 의 이벤트형 규칙까지 **전부 재평가**해 누락을 보정(멱등 insert 라 중복 안전).
3. **월 개근**은 매월 1일 새벽 잡에서 직전 달을 평가(또는 일일 스윕이 "직전 달이 확정됐는가"를 보고 수행). 미션이 닫혔는지 확인 후에만.
4. 사용자 단위 트랜잭션, 한 사용자 실패가 전체를 막지 않게 try/catch, 배치 상한(예: 한 번에 500명).
5. 스위치: `hawe.badge.enabled`(기본 `true`? — **기본 `false`** 권장: 켜기 전 드라이런 단계가 있다, D2). 드라이런 모드(`hawe.badge.dry-run=true`)면 수여하지 않고 "수여했을 업적"만 로그(사용자 식별자 해시)로 남긴다.
6. 수동 호출 가능 서비스 메서드 `sweep(Instant now)`(컨트롤러 노출 금지).

## Files / Functions to Change

| 파일 | 변경 |
|---|---|
| `.../badge/domain/BadgeRules.java` | 스케줄형 규칙 추가(A2 와 공유) |
| `.../badge/domain/StreakCalculator.java` (신규) | 연속 일수 순수 계산 |
| `.../badge/dao/UserActivityMapper.java` + XML | 스트릭·월 개근·인기·기념일·찰떡 집계 쿼리 |
| `.../badge/service/BadgeSweepJob.java` (신규) | 스케줄·스윕 |
| `.../global/config/…BadgeProperties.java` (신규) | `enabled`, `dryRun`, `sweepCron`, 임계 파라미터 |
| `application.properties` | 기본값 |
| 테스트 | 아래 |

## Constraints

- 서버 DB 데이터만 사용. 닫힌 미션만 월 개근에 사용.
- 전체 사용자 풀스캔을 매일 하지 않는다(활동 기반 범위).
- 로그에 사용자 식별 정보 최소화(드라이런은 해시/카운트).
- 커밋/푸시 금지.

## Edge Cases

- 스트릭: 자정 경계, 하루에 여러 미션 제출(1일로), 그룹 여러 개에서 같은 날 제출(1일), 지각 포함, 하루 건너뜀 → 리셋.
- 월 개근: 월 중간에 그룹 가입(가입 전 미션은 제외 — "미션이 열릴 때 있던 사람" 기준 B9 정의), 월 중 미션이 0개인 그룹 제외.
- 기념일: 2월 29일 시작은 평년 2월 28일(`EXPERIMENT_TOPIC.md`/`AnniversaryResolver` 규칙과 일치시킬 것 — 기존 `AnniversaryResolver` 재사용 검토).
- 인기 글 20개 경계, 자기 반응/댓글을 세는지 → 본인 반응·댓글은 제외(어뷰징 방지).
- 대량 백로그(처음 켤 때) → 배치 상한으로 여러 날에 나눠 처리.
- 서버 시간대 `Asia/Seoul` 고정.

## Tests Required

- `StreakCalculator`: 6/7일 경계, 자정 경계, 중복 날짜, 공백일, 다중 그룹 합산.
- 월 개근 판정(가입 시점 반영), 기념일(윤년), 인기 글(본인 제외), 찰떡 수.
- 스윕: 멱등(두 번 실행해도 동일), 한 사용자 실패 격리, 드라이런에서는 DB 불변, 배치 상한.
- 이벤트형 규칙을 스윕에서도 재평가해 누락 보정되는지.

## Validation Steps

`./mvnw test` 통과. 수동: 로컬 DB 에 테스트 데이터를 만들고 `sweep` 호출 후 `user_badges` 확인, 드라이런 로그 확인.

## Potential Merge Conflicts

`application.properties`, `UserActivityMapper.xml`(A2 와 같은 파일 — 순차).
