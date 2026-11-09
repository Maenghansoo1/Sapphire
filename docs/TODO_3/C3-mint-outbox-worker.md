# C3. 발급 outbox 와 단일 발급 워커

> 범위: 백엔드 `badge` 패키지. **선행: A1, C1, C2.** 근거: `BLOCKCHAIN_BADGES.md` §4·§9·§13. 빌드/테스트: `./mvnw test`(가짜 체인 클라이언트).

## Goal

업적이 수여되면 **DB outbox 에 발급 작업**을 만들고, **단일 스레드 워커**가 하나씩 체인에 민팅해 `user_badges` 를 `CONFIRMED`(tx_hash·token_id)로 만든다. 체인 장애가 제출·피드·도장 흐름을 막지 않고, 재시작·중복에도 중복 발급이 없다.

## Findings

- 기존 비동기 실행기(`AsyncConfig`: `visionExecutor`, `insightExecutor`)는 큐가 가득 차면 **작업을 폐기하고 재처리하지 않는다**. 배지 발급은 누락되면 안 되므로 **DB outbox + 주기적 재처리**가 필수다(BLOCKCHAIN_BADGES.md §9, B10 과의 차이).
- 발급 키 하나당 **동시에 한 트랜잭션만** 보내야 nonce 충돌이 없다 → 단일 스레드 실행기 `badgeMintExecutor`(코어 1, 큐 소량) 또는 스케줄러 스레드 하나에서 순차 처리.
- 상태 전이(`user_badges.status`): `ISSUED_OFFCHAIN`(체인 off 때 수여) → (체인 on 후 스위퍼가) `EARNED` + 잡 생성 → `MINTING`(전송됨, tx_hash 저장) → `CONFIRMED`(영수증 성공·확정 수) / `FAILED`(영구 실패 또는 재시도 소진). outbox `badge_mint_jobs.status`: `PENDING | SENT | DONE | DEAD`.
- 멱등: 온체인 `(to, badgeCode)` 중복 revert(`AlreadyIssued`) + `UNIQUE(user_badge_id)` 잡 + `INSERT IGNORE`. 재시작 시 `SENT`(tx_hash 있음)는 **재전송하지 않고 영수증만 확인**한다.
- DB: MariaDB — `SELECT … FOR UPDATE SKIP LOCKED` 지원 여부는 버전에 따라 다르다(10.6+). 단일 인스턴스 가정이므로 상태 CAS 업데이트(`UPDATE … WHERE status='PENDING' AND id=?`)로도 충분하다. 구현 시 쓰는 방식을 보고.
- C4 가 정체 트랜잭션 복구·잔액 모니터링·DEAD 처리·재시도 스케줄러를 맡는다. 이 단위는 정상 경로 + 기본 재시도.

## Key Implementation Points

1. **스키마**(새 마이그레이션):
   ```sql
   badge_mint_jobs(
     id BIGINT PK AUTO_INCREMENT,
     user_badge_id BIGINT NOT NULL UNIQUE,    -- FK user_badges
     attempts INT NOT NULL DEFAULT 0,
     next_attempt_at DATETIME NOT NULL,
     last_error VARCHAR(500) NULL,            -- 사유 코드 위주, 사용자 정보 금지
     status VARCHAR(20) NOT NULL,             -- PENDING|SENT|DONE|DEAD
     tx_hash CHAR(66) NULL,
     created_at DATETIME NOT NULL, updated_at DATETIME NOT NULL,
     INDEX (status, next_attempt_at))
   ```
2. **작업 생성**: `BadgeAwardService.award`(A1)에서 체인이 켜져 있으면 `user_badges` 를 `EARNED` 로 넣는 **같은 트랜잭션**에서 잡도 `PENDING` 으로 insert. 체인이 꺼져 있으면 `ISSUED_OFFCHAIN` 만(잡 없음).
3. **스위퍼** `BadgeChainEnableSweeper`: 체인을 켠 뒤 `ISSUED_OFFCHAIN` 행을 점진적으로(배치 상한) `EARNED` + 잡 생성으로 전환(소급 발급 흐름 연결).
4. **워커** `BadgeMintWorker`: 폴링 주기(기본 15초, 설정) — 기한이 된 `PENDING` 잡을 최대 N건 순차 처리:
   1. `ChainAccountService.getOrCreate(userId)` 로 주소 확보(C2).
   2. `chain.hasBadge(address, code)` 가 true 면 이미 발급된 것(재시작/복구) → 토큰 ID 조회 후 `CONFIRMED` 처리.
   3. 아니면 `chain.sendMint(address, code)` → `tx_hash` 저장, 잡 `SENT`, 배지 `MINTING`.
   4. 즉시 영수증을 짧게 폴링(상한 예: 30초) → 성공이면 `token_id`(영수증 로그), `confirmed_at`, `chain_id` 저장 → 배지 `CONFIRMED`, 잡 `DONE`. 시간 내 확정 안 되면 `SENT` 로 남기고 C4 의 확인 루프가 이어받는다.
   5. 예외 분류: `Transient` → `attempts++`, 백오프(30초→2분→10분→1시간), `PENDING` 복귀; `Permanent` → 배지 `FAILED`, 잡 `DEAD`(C4 에서 알림/조치).
5. 직렬성: 워커는 한 번에 한 잡만 전송(루프 내 순차). 전송 구간은 `synchronized`/단일 스레드 실행기로 보호.
6. 설정: `hawe.chain.worker.poll-interval`, `batch-size`, `max-attempts`(기본 8), `confirm-wait`, 백오프 목록.
7. 로그: 잡 ID·배지 코드·상태·사유 코드만(사용자 ID/주소/키 금지).
8. 서버 시작 시 워커는 `hawe.chain.enabled && usable()` 일 때만 활성(꺼져 있으면 빈 자체를 만들지 않거나 무동작).

## Files / Functions to Change

| 파일 | 변경 |
|---|---|
| `src/main/resources/db/migration/V*__badge_mint_jobs.sql` (신규) | 스키마 |
| `.../badge/service/{BadgeMintWorker,BadgeChainEnableSweeper}.java` (신규) | 워커·스위퍼 |
| `.../badge/dao/MintJobMapper.java` + XML (신규) | 잡 조회/전이 |
| `.../badge/service/BadgeAwardService.java` (A1) | 체인 on 시 잡 생성 |
| `.../global/config/AsyncConfig.java` | `badgeMintExecutor`(단일 스레드) 추가 |
| `ChainProperties` | 워커 설정 |
| 테스트 | 아래 |

## Constraints

- 판정/수여 트랜잭션에서 **체인 호출 금지**(DB 만). 체인 호출은 워커에서만.
- 외부 호출은 DB 트랜잭션 밖에서 하고, 결과 기록만 짧은 트랜잭션으로.
- 중복 발급 금지(온체인 멱등 + DB 유니크). 재시작 후에도 `SENT` 를 재전송하지 않는다.
- 사용자 개인정보/주소/키를 로그·`last_error` 에 남기지 않는다.
- 커밋/푸시 금지.

## Edge Cases

- 워커 중간에 프로세스 종료(전송 직전/직후) → `SENT` 인데 tx_hash 가 없을 가능성: 전송 **직전에 잡을 `SENT` 로 선기록하지 않는다**; 전송 성공으로 tx_hash 를 받은 직후 기록하고, 그 사이 종료되면 재시작 후 `hasBadge` 로 확인해 중복을 막는다.
- 같은 사용자에게 여러 잡이 연속 → nonce 순서대로 순차 전송.
- 주소 할당 실패/DB 오류 → 잡 `PENDING` 유지, 백오프.
- 체인을 끄고 켬 → 켜는 순간 `ISSUED_OFFCHAIN` 스위퍼가 이어서 처리.
- 영수증 `status=0`(revert) → `Permanent`.
- 테스트넷 초기화로 컨트랙트가 사라짐 → `hasBadge` 호출 실패가 반복되면 `DEAD` 로 모이고 C4/D3 런북 대상.

## Tests Required

- (가짜 체인 클라이언트) 정상 경로: 수여 → 잡 생성 → 워커 → `CONFIRMED`(token_id 저장).
- 중복 방지: 이미 온체인에 있는 경우(`hasBadge=true`) 전송 없이 `CONFIRMED`.
- 일시 실패 백오프 → 성공, 영구 실패 → `FAILED`/`DEAD`.
- 재시작 시나리오: `SENT`+tx_hash 상태에서 영수증만 확인, 재전송 안 함.
- 직렬성: 동시에 여러 잡이 있어도 전송은 순차(가짜 클라이언트가 동시 호출 감지 시 실패).
- 체인 off 에서는 잡 없음/워커 무동작, 켜면 스위퍼가 전환.
- 수여 트랜잭션이 체인 호출을 하지 않음(가짜 클라이언트 호출 0 확인).

## Validation Steps

`./mvnw test` 통과. 로컬 노드(Anvil) + C1 통합 프로필로 실제 민팅이 `CONFIRMED` 되는지 1건 확인(결과 첨부). 못 했으면 "실체인 미검증".

## Potential Merge Conflicts

`AsyncConfig.java`, `BadgeAwardService`(A1/A2 와 같은 파일), 마이그레이션 번호, `ChainProperties`.
