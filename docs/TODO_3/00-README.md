# TODO_3 — 블록체인 디지털 배지(업적) 작업 단위

근거: [`docs/FEATURE/BLOCKCHAIN_BADGES.md`](../FEATURE/BLOCKCHAIN_BADGES.md) (2026-11-09 초안). 담당자는 정하지 않았다.
각 파일은 계획 형식(목표/배경/구현 요점/변경 파일/제약/엣지/테스트/검증/충돌)이며 **그 파일만 읽고 구현할 수 있게** 썼다. 한 번에 한 작업만 진행한다.

> 번호 체계: 여기서 **A/B/C/D 는 단계 묶음**이다(플랫폼 아님). `docs/TODO_2/` 와 `docs/해야될거/` 의 A/B 번호와는 별개다. 대화·커밋에서는 `TODO_3/A1` 처럼 폴더를 붙여 부른다.

| 묶음 | 단계 | 체인 필요 | 파일 |
|---|---|---|---|
| **A** | 업적 판정·저장·API·앱 화면 (**체인 없이** `ISSUED_OFFCHAIN` 으로 먼저 출시 가능) | 아니오 | A1~A7 |
| **B** | 스마트 컨트랙트(ERC-721 + ERC-5192) + 메타데이터 | 로컬 노드/테스트넷 | B1~B3 |
| **C** | 서버의 체인 연동: 클라이언트·지갑 파생·발급 워커·복구 | 예 | C1~C4 |
| **D** | 탈퇴 연동(소각), 롤아웃·E2E, 문서·고지 | 예 | D1~D3 |

## 단위 목록

| ID | 파일 | 내용 | 규모 | 선행 |
|---|---|---|---|---|
| A1 | `A1-schema-and-catalog.md` | `user_badges` 스키마, 업적 카탈로그(코드/설정), 도메인 | M | — |
| A2 | `A2-event-evaluator.md` | 제출 이벤트 기반 판정(첫 제출·누적·얼리버드·전 틀) | M | A1 |
| A3 | `A3-scheduled-evaluator.md` | 일/월 스케줄 판정(스트릭·개근·인기·기념일·찰떡) + 누락 보정 스윕 | M | A1 (A2 와 로직 공유) |
| A4 | `A4-badge-api-and-notification.md` | `GET /api/me/badges`, `/api/badges/catalog`(진행도), `BADGE_EARNED` 알림 | M | A1, A2 |
| A5 | `A5-backfill-job.md` | 기존 사용자 소급 발급 잡 | S | A2, A3 |
| A6 | `A6-android-achievements-screen.md` | Android 업적 화면 | M | A4 |
| A7 | `A7-ios-achievements-port.md` | iOS 업적 화면 이식 | M | A6, Mac |
| B1 | `B1-contract-and-tests.md` | 컨트랙트 + 단위 테스트(`chain/`) | M | 결정 1·7 |
| B2 | `B2-deploy-scripts.md` | 로컬/테스트넷 배포 스크립트, `deployments/*.json` | S | B1 |
| B3 | `B3-metadata-endpoint.md` | `tokenURI` 메타데이터·이미지 정적 서빙(서버) | S | A1 (카탈로그) |
| C1 | `C1-chain-client.md` | `BadgeChainClient` 인터페이스 + 구현, Java 25 호환 확인 | M | B1 (ABI) |
| C2 | `C2-wallet-and-accounts.md` | 사용자 익명 주소 할당(권장: 키를 보관하지 않는 무작위 주소), `user_chain_accounts` | M | C1 |
| C3 | `C3-mint-outbox-worker.md` | outbox, 단일 발급 워커, 상태 전이, 재시작 안전 | L | A1, C1, C2 |
| C4 | `C4-recovery-and-monitoring.md` | 영수증 재확인, 재시도 스케줄러, 잔액 모니터링, 장애 주입 테스트 | M | C3 |
| D1 | `D1-withdrawal-burn.md` | 탈퇴 시 소각 + 매핑·배지 삭제 | M | C3 |
| D2 | `D2-rollout-and-e2e.md` | 드라이런, 체인 ON 절차, 테스트넷 E2E, 앱 탐색기 링크 | M | C4, D1, A6 |
| D3 | `D3-docs-notice-runbook.md` | 문서 갱신, 처리방침 초안, 키 교체·장애 대응 런북 | S | D2 |

규모: S ≤ 0.5일, M ≈ 1~2일, L ≈ 3일 이상(한 사람 기준 대략치).

## 권장 순서

```
결정 확정 ─┬─ A1 → A2 ∥ A3 → A4 → A5 → A6 → A7        ← 여기까지 체인 없이 출시 가능 (가치 먼저)
           └─ B1 → B2 ∥ (B3)                           ← A 와 병렬 가능
                 └─ C1 → C2 → C3 → C4 → D1 → D2 → D3   ← 체인 연동 (A1 완료 후 C3)
```

BLOCKCHAIN_BADGES.md §15 의 순서(1. 오프체인 업적, 2. 컨트랙트, 3. 발급 워커, 4. 탈퇴·운영)와 같다. **결정 10**(처음엔 `ISSUED_OFFCHAIN` 으로 배포) 기본값을 따른다.

## 먼저 정할 결정 (BLOCKCHAIN_BADGES.md §11)

| # | 질문 | 영향 단위 | 기본값 |
|---|---|---|---|
| 1 | 체인/네트워크 | B1, B2, C1 | 퍼블릭 EVM 테스트넷(Base Sepolia 또는 Polygon Amoy) |
| 2 | 사용자 주소 방식: (a) 무작위 주소+키 폐기 / (b) xpub 파생 / (c) 시드 보관 HD(원안) | C2, D1 | **(a) 권장** — 배지가 양도 불가라 사용자 주소는 서명할 일이 없어 시드 보관이 불필요. 원안(c)과 달라지므로 **확인 필요**. 어느 쪽이든 API 비노출 |
| 3 | 스트릭 기준(하루 1개라도? 지각 포함?) | A3 | 하루 1개라도, 지각 포함, `mission_date` 기준 |
| 4 | 어뷰징(1인 그룹 제외) | A2, A3 | 멤버 ≥ 2 인 그룹의 제출만 카운트 |
| 5 | 기존 사용자 소급 발급 | A5 | 소급한다, `earned_at` 은 실제 달성일 |
| 6 | 메타데이터/이미지 호스팅 | B3 | 서버 정적 경로(IPFS 제외) |
| 7 | 컨트랙트 툴체인 | B1 | Foundry 또는 Hardhat — 팀이 익숙한 쪽 |
| 8 | 다른 멤버에게 내 배지 공개 | A4, A6 | 1차 본인에게만 |
| 9 | 반복 가능 배지 | A1 | 1차 1회성만 |
| 10 | 온체인 민팅 ON 시점 | D2 | 먼저 `ISSUED_OFFCHAIN` 배포 → 체인은 다음 배포 |
| 11 | web3j 와 Java 25 호환 | C1 | 구현 시 확인, 비호환이면 JSON-RPC 직접 호출/소형 별도 서비스 |

## 코드로 확인한 현재 상태 (계획의 전제)

- 이벤트: `SubmissionCreatedEvent(Long submissionId, String template, String aiGuess)` — **제출 ID 만 담는다.** 리스너는 AFTER_COMMIT 패턴(`mascot/service/MascotListener.java`, `notification/service/NotificationListener.java`)을 쓴다. 판정은 제출·사용자·미션·그룹 정보를 직접 조회해야 한다.
- 비동기 실행기: `global/config/AsyncConfig.java` 의 `visionExecutor`(2~4 스레드), `insightExecutor`(1~2). 큐 가득 차면 **폐기하고 재처리 없음** → 배지 발급은 누락되면 안 되므로 **DB outbox + 스케줄러 재처리**가 필요하다(별도 단일 스레드 실행기).
- 알림 타입: `notification/domain/NotificationType.java` 는 `topic, poke, comment, mention`. `BADGE_EARNED` 를 쓰려면 enum 추가 + DB 컬럼 타입 확인(ENUM 인지 VARCHAR 인지) + **구버전 앱이 모르는 타입을 받아도 죽지 않는지** 확인이 필요하다(A4).
- 탈퇴: `withdraw/service/WithdrawService.java#withdraw` 는 하나의 `@Transactional` 안에서 `WithdrawMapper` 삭제 메서드를 순서대로 부른다(`deleteMyNotifications` → `deleteUserById` 등). 파일 삭제는 afterCommit. 외부 체인 호출은 이 트랜잭션 안에서 할 수 없다 → outbox(D1).
- 마이그레이션: `src/main/resources/db/migration/` 에 `V1`~`V4`. 새 스키마는 새 `V*` 파일로 추가하는 것을 기본으로 한다(번호는 구현 시 최신 확인).
- 패키지 규칙: 기능별 `controller/service/dto/domain/dao` 5개 하위 폴더만. 체인 클라이언트도 `service` 안에 둔다.

## 겹침·순서 주의

| 영역 | 겹치는 단위 | 조치 |
|---|---|---|
| 마이그레이션 번호 | A1, C2, C3, D1 (+ `docs/TODO_2` D2 등) | 푸시 전 최신 pull, 번호 충돌 시 재번호 |
| `WithdrawService`/`WithdrawMapper` | D1 | 다른 탈퇴 로직 변경과 순차 |
| `AsyncConfig` | C3 (+ TODO_2 B5/D3) | 실행기 빈 이름 충돌 주의 |
| `NotificationType` / 알림 화면 | A4 | 앱 파싱 확인 후 enum 추가 |
| `SubmissionCreatedEvent` 리스너 | A2 | 기존 리스너(마스코트·알림·비전)와 독립, 순서 의존 금지 |
| Android 마이페이지/내비게이션 | A6 | 다른 Android 작업과 순차 |

## 공통 제약 (모든 단위)

- 커밋/푸시 금지. `CLAUDE.md`·`AGENTS.md` 수정 금지. 계획에 없는 리팩터링 금지.
- **온체인에는 이메일·닉네임·그룹 이름·그룹/미션/제출 ID·사진·글을 올리지 않는다.** (배지 종류 코드, 시각, 토큰 ID, 익명 주소만)
- **사용자↔주소 매핑은 서버 DB 에만 둔다.** 앱 API 에 주소·개인키·시드를 내려주지 않는다.
- 판정은 **서버 DB 데이터만** 사용(클라이언트 값 불신). 체인 장애가 제출·피드·도장 흐름을 막으면 안 된다.
- 개인키/시드/RPC 키는 저장소에 커밋하지 않는다(`.env`, 시크릿 매니저). `.env.example` 에는 이름만.
- 로그에 키·시드·서명 원문·사용자 식별 정보를 남기지 않는다.
- 서버 테스트는 실제 체인/RPC 를 부르지 않는다(가짜 `BadgeChainClient`). 로컬 노드 통합 테스트는 별도 프로필.
- 디자인(Figma) 없는 UI 는 임시 에셋 + `임시` 주석.
- 빌드/테스트: 백엔드 `./mvnw test`(Java 25), Android `android\gradlew.bat :app:testDebugUnitTest --offline`(JDK 21, `set JAVA_HOME=C:\Users\kimch\.jdks\ms-21.0.10`).

## 사용자가 직접 해야 하는 일 (코드 밖)

- 결정 1~11 확정.
- RPC 제공자 계정/키 발급, 테스트넷 faucet 충전, 시크릿 매니저에 시드·발급 키 등록(저장소 비커밋).
- 배지 이름·이미지 디자인(Figma 확정 전엔 임시), 개인정보 처리방침 문구 확정, 스토어 심사 정책·법적 검토 여부 결정.
