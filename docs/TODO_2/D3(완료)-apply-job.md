# D3. 멀티 암드 밴딧 — 닫힌 미션 반영 잡 (스케줄러)

> 범위: 백엔드. **선행: D2**(스키마·보상 계산·설정). 근거: `AI_FEATURES.md` §4.2(지연 보상)·§4.8(비정상성). 빌드/테스트: `./mvnw test`.

## Goal

하루 한 번, 새로 닫힌 미션들의 보상을 `template_bandit_stats` 에 반영하고 시간 감쇠를 적용한다. 같은 날 안에서는 통계가 변하지 않아 슬롯별 틀 선택이 일관되게 한다.

## Findings

- 스케줄러 선례: `src/main/java/com/sapphire/hawe/mission/service/MissionScheduler.java`(매분 전체 그룹 순회로 발행). 비동기 선례: `AsyncConfig`(`global/config`)의 실행기. 공통 비동기 방식은 `docs/FEATURE/IMPROVEMENTS.md` §1 에서 나중에 조사 예정 — 이 단위는 **기존 `@Scheduled` 패턴을 그대로** 따른다.
- 통계는 같은 날 여러 슬롯 발행 사이에 바뀌면 안 된다(D4: 다중 슬롯 일관성). 따라서 **새벽 고정 시각 1회**(예: 03:00 Asia/Seoul — 기존 `SeoulTime` 유틸 사용) 실행하고, 그 뒤 발행되는 모든 슬롯은 같은 통계를 읽는다. 발행 시각이 03:00 이전으로 설정된 그룹이 있으면(`default-publish-time` 09:00 이전 슬롯 가능) 그 날 첫 슬롯은 전날 통계를 쓰게 되어도 무방(일관성만 유지).
- 감쇠: 오래된 관측 약화(반감기 30일). 구현 방법 — 매 실행 시 모든 `alpha_real/beta_real` 에 `2^(−Δdays/halfLife)` 곱(Δ=이전 갱신 이후 일수, `updated_at` 기준). 합성 prior 는 `K_syn·exp(−N_real/N0)` 로 영향력을 감쇠하되 **저장은 원값, 선택 시 계산**(D4)하는 것이 단순하다(저장값은 건드리지 않음).

## Key Implementation Points

1. `BanditApplyJob`(mission/service): `@Scheduled(cron = "0 0 3 * * *", zone = "Asia/Seoul")` — 설정 `hawe.bandit.apply-cron`, 스위치 `hawe.bandit.enabled`(기본 `false`; 켜지 않으면 아무것도 하지 않음. D4 의 `template-policy=bandit` 와는 별개로 **통계 수집은 정책과 무관하게 켜서 미리 쌓을 수 있게** 하는 목적).
2. 흐름: ① 감쇠 적용(모든 통계 행) ② 닫힌 미션 후보 조회(D2 매퍼, 배치 상한 예: 500) ③ 각 미션을 **자체 트랜잭션**으로 보상 계산 → GLOBAL·GROUP 통계 upsert → `template_bandit_applied` insert(PK 충돌은 이미 반영된 것으로 무시) ④ 후보가 남으면 반복(상한까지).
3. 한 건 실패가 전체를 막지 않도록 미션 단위 try/catch, 로그에는 미션 ID·사유만(사용자/그룹 식별 정보·본문 제외).
4. 다중 인스턴스 안전성: 지금은 단일 인스턴스 가정. 중복 실행되어도 `template_bandit_applied` PK 로 이중 반영이 막힌다(감쇠는 `updated_at` 기준이라 중복 적용 위험 → 감쇠를 "행 단위 `updated_at` 비교 후 UPDATE" 로 멱등하게 구현).
5. 수동 실행 훅: 테스트/운영 확인용으로 서비스 메서드 `applyOnce(Instant now)` 를 공개(컨트롤러 노출은 하지 않는다).

## Files / Functions to Change

| 파일 | 변경 |
|---|---|
| `.../mission/service/BanditApplyJob.java`, `BanditStatsService.java` (신규) | 잡·반영 로직 |
| `.../mission/dao/…BanditMapper` | D2 에서 만든 쿼리 사용(필요 시 감쇠 UPDATE 추가) |
| `.../global/config/BanditProperties.java` | `enabled`, `applyCron`, `batchSize` |
| `application.properties` | 기본값 |
| 테스트 | 아래 |

## Constraints

- 닫힌 미션만. 외부 호출 없음. 트랜잭션은 미션 단위로 짧게.
- 기본 비활성(`hawe.bandit.enabled=false`). 켜지 않으면 기존 동작에 영향 없음.
- 로그에 사용자·그룹 식별 정보 금지.
- 커밋/푸시 금지.

## Edge Cases

- 아직 열려 있는 미션/같은 슬롯의 다음 미션이 안 열린 미션 → 반영하지 않음(닫힘 기준은 B9 정의).
- 서버 재시작/중복 실행 → `applied` 로 멱등, 감쇠 멱등.
- 그룹이 중간에 삭제됨 → 해당 그룹 통계 삭제는 D2 의 삭제 순서가 처리, 잡은 존재하지 않는 미션을 건너뜀.
- 대량 백로그(처음 켤 때 과거 미션 전부) → 배치 상한으로 나누어 처리, 한 번 실행에서 최대 N건 후 다음 실행에 이어 감.
- 시간대: `Asia/Seoul` 고정.

## Tests Required

- 시계 주입으로: 감쇠 계산(30일 후 절반), 중복 실행 멱등(두 번 `applyOnce` → 통계 불변), 닫히지 않은 미션 미반영, 한 건 실패 시 나머지 반영, 비활성 시 무동작.
- 배치 상한 동작.

## Validation Steps

`./mvnw test` 통과. 수동: 로컬 DB 에 닫힌 미션 몇 개를 만들고 `applyOnce` 로 통계가 기대대로 쌓이는지 SQL 로 확인(보고서에 기록).

## Potential Merge Conflicts

`application.properties`, `MissionScheduler` 와의 스케줄 설정 충돌(별도 클래스라 낮음).
