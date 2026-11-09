# D1. 탈퇴 연동 — 배지 소각과 연결 정보 삭제

> 범위: 백엔드 `withdraw` 와 `badge`. **선행: C3**(소각 전송 경로), C2(주소 매핑). 근거: `BLOCKCHAIN_BADGES.md` §6. 빌드/테스트: `./mvnw test`.

## Goal

사용자가 탈퇴하면 ① 서버 DB 의 업적·매핑·발급 작업을 삭제하고 ② 그 사용자의 온체인 토큰을 소각하며 ③ 온체인에 남는 과거 기록이 **사람을 식별할 수 없게** 한다. 외부 체인 호출 때문에 탈퇴 트랜잭션이 막히거나 실패하면 안 된다.

## Findings

- `withdraw/service/WithdrawService.java#withdraw(Long userId)` 는 **하나의 `@Transactional`** 안에서 `WithdrawMapper` 의 삭제 메서드를 순서대로 부른다(`deleteMyReactions` … `deleteMySubmissions`, `deleteMyPokes`, `deleteMyNotifications`, 마지막에 `deleteUserById`). 파일 삭제는 afterCommit 콜백. 외부 체인 호출은 이 트랜잭션 안에서 하면 안 된다(롤백 불가·지연).
- 온체인 데이터는 삭제할 수 없다. 대신 **매핑(사용자↔주소)을 지우고 토큰을 소각**해 식별 불가능하게 만든다(BLOCKCHAIN_BADGES.md §6). 소각 후에도 과거 `BadgeIssued` 로그(익명 주소 + 배지 코드 + 시각)는 남는다 → 처리방침에 명시(D3).
- 소각은 비동기 작업이어야 하므로 **소각 outbox 가 `user_id` 없이** 동작해야 한다(탈퇴 시점에 `user_badges`·`user_chain_accounts` 가 삭제되므로). 소각 작업은 **주소 + (알려진) 토큰 ID** 만 갖는다.
- 전송 중(`SENT`) 민팅이 탈퇴 후 뒤늦게 확정될 수 있다 → 소각 작업은 "해당 주소의 **현재 보유 토큰 전부**"를 소각하도록 설계(B1 의 `ERC721Enumerable` 로 열거), 일정 지연 후 잔여 토큰이 없는지 재확인.
- C2 (a) 기본 구현은 사용자 주소의 개인키가 없다 → 소각은 항상 MINTER 권한으로 수행(B1 `burn` 은 MINTER 전용).

## Key Implementation Points

1. **스키마**(새 마이그레이션):
   ```sql
   chain_burn_jobs(
     id BIGINT PK AUTO_INCREMENT,
     address CHAR(42) NOT NULL,            -- 사용자 식별 정보 없음
     status VARCHAR(20) NOT NULL,          -- PENDING|DONE|DEAD
     attempts INT NOT NULL DEFAULT 0,
     next_attempt_at DATETIME NOT NULL,
     last_error VARCHAR(500) NULL,
     created_at DATETIME NOT NULL, updated_at DATETIME NOT NULL,
     INDEX (status, next_attempt_at))
   ```
2. **탈퇴 트랜잭션 변경**(`WithdrawService.withdraw`): `deleteUserById` 앞에서 — (a) `user_chain_accounts` 에서 주소 조회 → 주소가 있으면 `chain_burn_jobs` insert(`PENDING`, `next_attempt_at = now + 10분`: 진행 중 민팅 확정을 기다림) (b) 해당 사용자의 `badge_mint_jobs`(PENDING 은 취소 = 삭제) 삭제 (c) `user_badges` 삭제 (d) `user_chain_accounts` 삭제. 체인 비활성이면 DB 삭제만. 삭제 순서는 FK 순서대로(`badge_mint_jobs` → `user_badges` → `user_chain_accounts` → … → `users`). `WithdrawMapper` 에 메서드 추가(`deleteMyBadgeMintJobs`, `deleteMyBadges`, `deleteMyChainAccount`).
3. **소각 워커** `ChainBurnWorker`(C3 의 직렬 실행기를 공유 — 같은 발급 키 nonce): 기한이 된 `PENDING` 잡 → `chain.tokensOf(address)` 로 열거 → 각 토큰 `sendBurn` → 영수증 확인 → 보유 토큰이 0 이면 `DONE`, 아직 `SENT` 민팅이 남아 확정되지 않았을 가능성이 있으면 한 번 더 지연 후 재확인(잔여 확인 2회). `Transient` 는 백오프, 영구 실패/소진은 `DEAD` + 경고.
4. **재시도·재개**: 서버 재시작 후에도 `chain_burn_jobs` 로 이어서 처리. 중복 실행 안전(`tokensOf` 가 비면 `DONE`).
5. 로그에 사용자 ID 와 주소를 함께 남기지 않는다. 소각 작업 로그는 잡 ID·토큰 수·상태만.
6. 체인 비활성/미구성 환경: 소각 작업을 만들지 않는다(주소가 있어도 체인 off 면 온체인에 토큰이 없을 수 있음 — 단 이전에 켠 적이 있다면 토큰이 있을 수 있으므로 **주소가 있으면 항상 잡 생성**, 워커는 체인이 usable 해질 때 처리).
7. 그룹 삭제/방장 위임 등 다른 탈퇴 경로(그룹 삭제 `GroupService`) 는 배지(사용자 소유)에 영향 없음을 확인.

## Files / Functions to Change

| 파일 | 변경 |
|---|---|
| `src/main/resources/db/migration/V*__chain_burn_jobs.sql` (신규) | 스키마 |
| `.../withdraw/service/WithdrawService.java` | 소각 잡 생성 + 배지/매핑 삭제 호출 |
| `.../withdraw/dao/WithdrawMapper.java` + XML | 삭제 메서드 추가 |
| `.../badge/service/ChainBurnWorker.java` (신규), `BurnJobMapper` (신규) | 소각 워커 |
| 테스트 | 아래 |

기존 삭제 순서를 바꾸지 않고 **배지 관련 삭제만 추가**한다.

## Constraints

- 탈퇴 트랜잭션 안에서 체인/네트워크 호출 금지. 체인 장애가 탈퇴를 막으면 안 된다.
- 소각 작업에 사용자 식별 정보(`user_id`, 이메일 등)를 저장하지 않는다.
- 소각 실패가 있어도 **매핑은 이미 삭제**되어 식별 불가능함을 유지(실패는 운영 경고일 뿐 개인정보 문제 아님).
- 기존 탈퇴 동작(파일 삭제, 알림 삭제 등) 불변.
- 커밋/푸시 금지.

## Edge Cases

- 배지가 하나도 없는 사용자 → 주소도 없으면 잡 없음.
- 주소는 있으나 토큰 0개(민팅 전/체인 off) → 잡 생성해도 `DONE`.
- 탈퇴 직전 `SENT` 민팅이 있음 → 지연 후 재확인으로 소각(소각 전 확정되면 포함, 이후 확정되면 두 번째 재확인에서 포함).
- 동시에 탈퇴 요청 두 번 → 사용자 삭제는 한 번만 성공, 소각 잡은 `address` 기준 멱등(중복 잡이 생겨도 `tokensOf` 가 비어 `DONE`).
- 소각 잡이 `DEAD` → 경고 로그, 런북(D3)에 수동 조치.
- 같은 주소를 다른 사용자가 재사용? C2 의 (a) 는 사용자별 유일 주소라 없음.

## Tests Required

- 탈퇴 시: 배지·매핑·mint job 삭제, 소각 잡 생성(주소만), 체인 호출 0(가짜 클라이언트), 체인 off 환경 정상 탈퇴, 배지/주소 없는 사용자 정상 탈퇴, 기존 탈퇴 테스트 전부 통과.
- 소각 워커: 토큰 N개 → N번 소각, 일시 실패 백오프, 소진 시 `DEAD`, 재시작 후 이어서, 잔여 재확인(소각 중 새 토큰이 확정된 시나리오).
- 소각 잡 테이블에 사용자 식별 컬럼이 없음을 스키마 테스트/리뷰로 확인.

## Validation Steps

`./mvnw test` 통과(기존 탈퇴 테스트 포함). 로컬 노드(C1 통합 프로필)에서 발급→탈퇴→소각→`tokensOf` 가 비는지 1건 확인(결과 첨부, 못 했으면 "실체인 미검증").

## Potential Merge Conflicts

`WithdrawService.java`, `WithdrawMapper.java/xml`(다른 탈퇴 로직 변경과 순차), 마이그레이션 번호, C3 워커 실행기.
