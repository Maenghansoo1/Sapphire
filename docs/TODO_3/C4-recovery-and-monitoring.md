# C4. 복구·재시도·잔액 모니터링·장애 주입 테스트

> 범위: 백엔드 `badge`. **선행: C3.** 근거: `BLOCKCHAIN_BADGES.md` §9·§13·§14. 빌드/테스트: `./mvnw test`.

## Goal

발급 파이프라인이 **장애에 견디도록** 한다: 확정되지 않은 전송 추적, 정체/드롭된 트랜잭션 처리, 일정 주기 재시도, 발급 키 잔액 경고, 장애 주입 테스트로 "체인이 죽어도 제출 흐름은 안 죽는다"를 증명한다.

## Findings

- C3 는 정상 경로 + 기본 백오프를 구현했다. 남은 위험: (1) `SENT` 상태로 오래 남는 잡(영수증이 안 옴 — 드롭/가스 부족/RPC 불안정), (2) `DEAD` 잡 누적, (3) 발급 키 잔액 소진(testnet faucet 의존), (4) RPC 제공자 속도 제한.
- 온체인 멱등 `(to, code)` 덕분에 "드롭됐다고 판단해 재전송했는데 사실 앞선 tx 가 뒤늦게 확정"되어도 두 번째가 revert(`AlreadyIssued`)될 뿐 중복 발급은 없다. 그때는 revert 를 **성공으로 해석**해(`hasBadge=true`) `CONFIRMED` 처리하는 경로가 필요하다.
- 모니터링 방식: 별도 모니터링 스택은 없다 → 로그(WARN) + 내부 조회 엔드포인트(관리자 토큰 보호, 기존 `/internal/…` 패턴). 외부 알림(푸시/슬랙)은 범위 밖.

## Key Implementation Points

1. **확정 확인 루프** `BadgeReceiptChecker`(스케줄, 기본 30초): `SENT` 잡의 `tx_hash` 영수증을 조회 → 성공+확정 수 충족이면 `CONFIRMED`/`DONE`, `status=0` 이면 `Permanent` 처리, 영수증이 없으면 대기. 확정 수는 `hawe.chain.confirmations`(기본 2).
2. **정체 트랜잭션 처리**: `SENT` 후 `stuck-after`(기본 10분) 동안 영수증이 없으면 ① `hasBadge` 확인(이미 발급됐으면 `CONFIRMED`) ② 아니면 새로 전송(가스 상향 또는 새 nonce) — 잡을 `PENDING` 으로 되돌리고 `attempts++`. 같은 발급 키의 nonce 는 노드의 `pending` 기준이므로 stuck 이 다음 전송을 막지 않는지 확인(필요 시 같은 nonce 교체 전송: C1 에 `replaceMint` 추가 여부는 구현자가 판단·보고).
3. **재시도 스케줄러**: `PENDING` 은 C3 워커가 기한에 맞춰 처리. `DEAD` 는 자동 재시도하지 않되, 설정 `hawe.chain.dead-retry-after`(기본 24h)가 지나면 한 번 더 `PENDING` 으로 되돌리는 옵션(일시적 환경 문제 복구용) — 기본 off.
4. **잔액 모니터링**: 주기 점검(기본 5분) `minterBalanceWei()` < 임계(`hawe.chain.min-balance-wei`) 이면 WARN 로그 + **발급 일시 중단 플래그**(워커가 `PENDING` 을 처리하지 않고 대기; 판정/수여는 계속). 잔액 회복 시 자동 재개. 잔액/중단 상태는 관리자 엔드포인트로 조회.
5. **관리자 상태 조회** `GET /internal/badges/chain-status`(헤더 `X-Admin-Token`, 틀리면 404): `{enabled, usable, chainId, contractAddress, minterBalanceWei, paused, jobs: {PENDING, SENT, DONE, DEAD}, oldestPendingAgeSeconds, lastErrorCodes(상위 5)}` — **사용자·주소·키 비노출**.
6. **RPC 속도 제한 대응**: 429/rate limit 은 `Transient` + 더 긴 백오프(워커 전체 일시 정지 60초).
7. **장애 주입 테스트(필수)**: 가짜 클라이언트로 (a) RPC 전면 장애 중 제출→수여→응답이 정상(체인 호출 0건이 요청 스레드에서 발생하지 않음), (b) 장애 해소 후 밀린 잡이 모두 `CONFIRMED`, (c) 프로세스 재시작 모의(서비스 재생성) 후 `SENT` 가 재전송 없이 확정, (d) 잔액 부족 시 중단/재개, (e) 정체 → 복구 경로.

## Files / Functions to Change

| 파일 | 변경 |
|---|---|
| `.../badge/service/{BadgeReceiptChecker,BadgeBalanceMonitor}.java` (신규) | 확정 확인·잔액 모니터 |
| `.../badge/service/BadgeMintWorker.java` (C3) | 일시중단 플래그·정체 처리 연계 |
| `.../badge/controller/BadgeAdminController.java` (A5 와 공유 가능) | `chain-status` |
| `.../badge/dao/MintJobMapper.java` + XML | 정체/DEAD 조회·전이 쿼리 |
| `ChainProperties` | `stuck-after`, `dead-retry-after`, `min-balance-wei` 등 |
| 테스트 | 아래 |

## Constraints

- 관리자 엔드포인트는 사용자 JWT 로 열리면 안 된다(토큰 없으면 404). 응답에 사용자·주소·키 정보 없음.
- 모든 외부 호출은 요청 스레드 밖. 모니터/체커가 느려져도 API 응답에 영향 없음.
- 로그에 키·주소 전체·사용자 식별 정보 금지.
- 커밋/푸시 금지.

## Edge Cases

- 영수증이 뒤늦게 도착해 재전송된 tx 와 원 tx 가 모두 존재 → 둘 중 하나만 성공, 다른 쪽 revert → 성공으로 수렴.
- reorg 로 확정된 tx 가 사라짐 → 확정 수 충족 전에만 상태를 `CONFIRMED` 로 바꾸므로 드묾; 이후 발견 시 `hasBadge=false` 이면 `PENDING` 으로 되돌림(보정 스윕).
- 잔액 임계 근처에서 깜빡임 → 히스테리시스(재개 임계 > 중단 임계).
- 컨트랙트 주소/체인 변경(재배포) → `hasBadge` 가 항상 false 가 되어 대량 재발급이 일어날 수 있음 → 설정 변경 시 `contract-address`/`chain-id` 가 기존 `user_badges.chain_id` 와 다르면 **재발급 모드**를 명시적으로 켜야 하도록 방어(자동 대량 재발급 금지, D3 런북).

## Tests Required

- 위 장애 주입 (a)~(e), 확정 확인 루프 상태 전이, 정체 복구, 잔액 히스테리시스, 관리자 엔드포인트 인증(404)·응답 필드(비식별), 429 백오프.

## Validation Steps

`./mvnw test` 통과. 보고서에 장애 주입 시나리오별 결과, 정체 처리 방식(교체 전송 여부) 결정, 관리자 응답 샘플.

## Potential Merge Conflicts

`BadgeMintWorker.java`(C3 와 같은 파일), `MintJobMapper.xml`, `BadgeAdminController`(A5 가 먼저 만들면 확장).
