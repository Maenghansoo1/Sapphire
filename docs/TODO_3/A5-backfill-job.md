# A5. 업적 — 기존 사용자 소급 발급 잡

> 범위: 백엔드. **선행: A2, A3**(공유 판정 로직). 근거: `BLOCKCHAIN_BADGES.md` §7(과거 데이터 소급), 결정 5. 빌드/테스트: `./mvnw test`.

## Goal

기능 출시 시점에 이미 존재하는 사용자 기록을 한 번 스캔해, 조건을 이미 만족한 업적을 **실제 달성일(`earned_at`)로** 소급 수여한다. 대량 수여가 서버·알림·(이후) 체인에 폭탄이 되지 않게 천천히 처리한다.

## Findings

- A2/A3 의 `BadgeRules` 는 "현재 상태에서 달성한 업적 후보"를 계산한다. 소급에서는 **언제 달성했는가**도 필요하다: 누적 N회는 N번째 제출의 `submitted_at`, 스트릭은 N일째 되는 날, 월 개근은 그 달 마지막 닫힌 미션 마감 시각, 기념일은 그 기념일 날짜. → 평가기가 `earnedAt` 을 반환하도록 A2 에서 설계해 두었다. 부족하면 이 단위에서 소급 전용 계산을 추가한다.
- 소급 수여 시 **알림은 보내지 않는 것**이 기본이다(수십 개 알림 폭탄 방지) — 대신 앱 업적 화면에서 확인. 스위치 `hawe.badge.backfill.notify`(기본 false).
- 체인 켜진 환경이면 소급 수여가 `EARNED` 로 들어가 C3 가 천천히 발급한다. 체인 off 면 `ISSUED_OFFCHAIN`.
- 사용자 수는 현재 많지 않을 것이므로 단순한 페이징 순회로 충분하다. 단 **멱등**이어야 한다(여러 번 실행 가능).

## Key Implementation Points

1. `BadgeBackfillService.run(batchSize, fromUserId)`: `users` 를 id 순으로 페이징, 사용자별로 전체 규칙을 평가(`earnedAt` 포함) → `award(userId, code, earnedAt)` (멱등). 진행 위치(마지막 사용자 id)를 반환/로그해 재개 가능.
2. 실행 방식: 관리자 토큰 보호 내부 엔드포인트(`POST /internal/badges/backfill?batch=200&from=0`, 헤더 `X-Admin-Token`, 틀리면 404 — 기존 `ExperimentReportController` 방식 재사용) **또는** 설정 스위치(`hawe.badge.backfill.on-startup`) 중 팀 방식에 맞는 쪽. 기본은 내부 엔드포인트(수동 트리거, 안전).
3. 처리량 제한: 배치 사이 sleep 또는 실행 시간 상한, 한 번 호출당 N명. 로그는 진행 수·수여 수 집계만(사용자 식별 제외).
4. 드라이런 옵션(`dryRun=true`): 수여하지 않고 업적별 수여 예정 수만 반환 → D2 의 드라이런 검증에 사용.
5. 소급 후 결과 요약 응답: `{processedUsers, awarded: {code: count}, lastUserId, done}`.

## Files / Functions to Change

| 파일 | 변경 |
|---|---|
| `.../badge/service/BadgeBackfillService.java` (신규) | 소급 로직 |
| `.../badge/controller/BadgeAdminController.java` (신규) | 내부 엔드포인트(토큰 보호) |
| `.../badge/domain/BadgeRules.java` | 필요 시 `earnedAt` 계산 보강 |
| 테스트 | 아래 |

## Constraints

- 관리자 엔드포인트는 사용자 JWT 로 열리면 안 된다(토큰 없으면 404). 응답에 개인 식별 값 없음.
- 알림 기본 off. 체인 호출 없음(발급은 C3).
- 전체를 한 트랜잭션으로 묶지 않는다(사용자 단위).
- 커밋/푸시 금지.

## Edge Cases

- 중복 실행 → 멱등, 이미 가진 업적은 건너뜀.
- 중간에 서버 중단 → `from` 으로 재개.
- 탈퇴한 사용자는 `users` 에 없으므로 자연히 제외.
- 소급으로 이미 오래된 `earned_at` — 정렬은 `earned_at` 기준이라 최신 획득이 아래로 가도 정상.
- 규칙이 바뀐 뒤 재실행 → 새로 만족하는 것만 추가(회수는 하지 않음).

## Tests Required

- 소급 `earnedAt` 계산(N번째 제출 시각, 스트릭 N일째, 월 말, 기념일), 멱등, 드라이런 DB 불변, 배치 재개, 알림 off 시 알림 없음, 토큰 없으면 404.

## Validation Steps

`./mvnw test` 통과. 수동: 로컬 DB 시드 데이터로 드라이런 → 실행 → 결과 요약 비교, 두 번째 실행에서 `awarded` 가 0 인지 확인.

## Potential Merge Conflicts

`BadgeRules.java`, 관리자 컨트롤러 패턴(`ExperimentReportController`, 보안 설정).
