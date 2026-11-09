# A2. 업적 — 제출 이벤트 기반 판정

> 범위: 백엔드 `badge/service`. **선행: A1.** 근거: `BLOCKCHAIN_BADGES.md` §4·§7. 빌드/테스트: `./mvnw test`.

## Goal

제출이 커밋된 직후 해당 사용자의 업적 중 **제출로 달라지는 것**(첫 제출, 누적 N회, 얼리버드, 전 틀 제출)을 판정해 수여한다. 판정 실패나 지연이 제출 응답에 영향을 주면 안 된다.

## Findings

- 이벤트: `SubmissionCreatedEvent(Long submissionId, String template, String aiGuess)` (`submission/domain/SubmissionCreatedEvent.java`) — **ID 와 틀만 있다.** 사용자·미션·그룹·`is_late`·시각은 DB 에서 조회해야 한다.
- 리스너 선례: `mascot/service/MascotListener.java`, `notification/service/NotificationListener.java` 가 `@TransactionalEventListener(phase = AFTER_COMMIT)` 를 쓴다. AFTER_COMMIT 단계에서 DB 를 쓰려면 새 트랜잭션이 필요하다(`@Transactional(propagation = REQUIRES_NEW)` 또는 서비스 메서드 분리).
- 데이터: `submissions`(`mission_id`, `user_id`, `submitted_at`, `is_late`), `missions`(`group_id`, `template`, `published_at`, `deadline_at`, `mission_date`), `group_members`.
- **어뷰징 방지(결정 4)**: 혼자뿐인 그룹의 제출은 세지 않는다 → 해당 미션 그룹의 현재 멤버 수 ≥ 2 인 제출만 카운트.
- 이 이벤트가 처리되지 못하는 경우(서버 재시작, 리스너 예외)를 위해 **A3 의 보정 스윕**이 같은 규칙을 다시 돌린다 → 판정 로직은 이벤트/스케줄이 **공유하는 순수 평가 함수**여야 한다.
- 규칙(초안):
  - `FIRST_SUBMISSION`: 사용자의 유효 제출이 1개 이상.
  - `SUBMISSIONS_10/50/100`: 유효 제출 수 ≥ N (구현은 `COUNT(*)`; `target` 은 카탈로그에서).
  - `EARLY_BIRD_10`: `is_late = false` 이고 `submitted_at − published_at ≤ 1시간` 인 제출 수 ≥ 10.
  - `ALL_TEMPLATES`: 서로 다른 `template` 7종(카탈로그에 고정한 필수 세트)을 모두 제출. 풀이 바뀌어도 필수 세트는 카탈로그가 정한다.
  - `PHOTO_PERFECT_5`: 찰떡(`PERFECT_FIT`) 판정 5회 — `submission_ai_reviews` 가 **비동기로 나중에** 쓰이므로 이 이벤트로는 알 수 없다 → A3 스윕에서 판정(여기서 구현하지 않음).

## Key Implementation Points

1. **순수 평가기** `BadgeRules`(domain): 입력 `UserActivity`(유효 제출 수, 얼리버드 수, 제출한 틀 집합, 시각 등 집계 스냅샷) → 출력 `List<BadgeCode> 달성 후보`. DB 접근 없음 → 단위 테스트가 쉽다.
2. **집계 조회** 매퍼 메서드: `UserActivityMapper.findEventActivity(userId)` — 한 번의 쿼리(또는 소수)로 위 집계를 가져온다(유효 제출 = 멤버 ≥ 2 인 그룹의 제출).
3. **리스너** `BadgeEventListener`: AFTER_COMMIT 에서 `submissionId` → `userId` 조회 → 집계 → 평가 → 각 후보를 `BadgeAwardService.award` (이미 있으면 무시). 전체를 try/catch 로 감싸 **예외를 삼키고 WARN**(제출 응답 영향 금지, 로그에 사용자 식별 정보 최소).
4. 실행 위치: 요청 스레드가 아니라 **별도 비동기 실행기**에서(빈 이름 `badgeExecutor`, 코어 1~2, 큐 100). 큐가 차면 폐기해도 A3 스윕이 보정하므로 괜찮다(로그만).
5. `award` 가 신규 획득을 반환하면 A4 가 연결할 **알림 훅**(`BadgeAwardedEvent` 발행 또는 콜백)을 호출한다 — A4 전이라면 이벤트만 발행하고 리스너는 비워 둔다.
6. `earned_at` = 달성을 만든 제출의 `submitted_at`(해당 제출이 임계를 넘긴 시점). 단순화를 위해 "이번 제출의 `submitted_at`" 을 쓰되, 소급(A5)에서는 N번째 제출 시각을 계산한다 → 평가기는 `earnedAt` 을 함께 반환하는 인터페이스로 설계.

## Files / Functions to Change

| 파일 | 변경 |
|---|---|
| `.../badge/domain/BadgeRules.java`, `UserActivity.java` (신규) | 순수 판정 |
| `.../badge/dao/UserActivityMapper.java` + XML (신규) | 집계 조회 |
| `.../badge/service/BadgeEventListener.java` (신규) | AFTER_COMMIT 리스너 |
| `.../global/config/AsyncConfig.java` | `badgeExecutor` 빈 추가(기존 빈 불변) |
| 테스트 | 아래 |

기존 `SubmissionService` 등은 수정하지 않는다(이벤트만 구독).

## Constraints

- 제출 흐름 불변: 리스너 예외가 제출 응답/트랜잭션에 영향 금지.
- 클라이언트가 보낸 값을 판정에 쓰지 않는다(서버 DB 만).
- 판정 로직을 이벤트/스케줄 양쪽이 공유한다(복붙 금지).
- 커밋/푸시 금지.

## Edge Cases

- 같은 제출 이벤트 중복 → `INSERT IGNORE` 로 무해.
- 한 제출로 여러 업적 동시 달성(예: 첫 제출 + 얼리버드) → 각각 수여.
- 그룹 멤버가 1명으로 줄어듦(나감) → 이후 제출은 미집계, 이미 받은 업적은 유지.
- 틀 목록 변경(풀 확장) → 필수 세트는 카탈로그 고정.
- 시간 경계: 얼리버드 정확히 1시간은 포함(`≤`).
- 탈퇴 중인 사용자 → 조회 실패 시 조용히 무시.

## Tests Required

- `BadgeRules`: 각 규칙 경계(9/10회, 정확히 1시간, 6/7종 틀), 1인 그룹 제출 제외(집계 단계 테스트), 후보 중복 제거.
- 리스너: 예외 주입 시 삼키고 계속, 이미 보유한 업적은 재수여 안 함, 신규 시에만 이벤트 발행.
- 통합(가능하면): 제출 생성 → 업적 생성 확인(비동기는 테스트에서 동기 실행기로 대체).

## Validation Steps

`./mvnw test` 통과. 수동: 로컬에서 계정으로 제출해 `user_badges` 에 `FIRST_SUBMISSION` 이 `ISSUED_OFFCHAIN` 으로 생기는지, 제출 응답 시간 변화 없음 확인.

## Potential Merge Conflicts

`AsyncConfig.java`(다른 실행기 추가 작업과 같은 파일), 신규 파일 위주라 그 외 낮음.
