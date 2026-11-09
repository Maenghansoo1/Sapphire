# C1. 서버 체인 클라이언트 (`BadgeChainClient`)

> 범위: 백엔드 `badge/service` + 설정. **선행: B1(ABI), 가능하면 B2(로컬 배포).** 근거: `BLOCKCHAIN_BADGES.md` §4·§9·결정 11. 빌드/테스트: `./mvnw test`(Java 25). 서버 테스트는 실제 RPC 를 부르지 않는다.

## Goal

서버가 컨트랙트와 통신하는 얇은 계층을 **인터페이스 + 구현 + 가짜 구현**으로 만든다: 민팅·소각 전송, 영수증 조회, 보유 여부 조회, 잔액 조회. 판정 로직과 발급 매체를 분리하는 BLOCKCHAIN_BADGES.md §4·§12 의 핵심 경계다.

## Findings

- **Java 25 호환 확인이 첫 작업이다(결정 11).** web3j 등 라이브러리가 Java 25·Spring Boot 4.1 과 호환되는지, 의존성(BouncyCastle 등)이 충돌하지 않는지 확인한다. 비호환/무거우면 대안: ① JSON-RPC 를 `RestClient` 로 직접 호출하고 서명만 작은 라이브러리(BouncyCastle 기반)로 구현 ② 별도 소형 서비스. 선택 근거와 의존성 추가 목록을 보고서에 기록하고, **새 의존성은 이 단위에서만 추가**한다.
- 컨트랙트 인터페이스(B1): `mint(address, string) → tokenId`, `burn(uint256)`, `has(address, string)`, `tokensOfOwner/balanceOf/tokenOfOwnerByIndex`, 이벤트 `BadgeIssued(to, tokenId, badgeCode, issuedAt)`, `BadgeBurned`.
- 설정 선례: `MissionAiProperties`(`hawe.mission.ai.*`, 타임아웃·`usable()`), `hawe.vision.*`. 같은 스타일로 `hawe.chain.*`.
- 발급 키(MINTER) 개인키는 환경변수/시크릿 매니저에서만 로드. 사용자 지갑 키는 서버에 필요 없다(C2).

## Key Implementation Points

1. **인터페이스** `BadgeChainClient`(badge/service):
   ```java
   interface BadgeChainClient {
     boolean enabled();
     BigInteger minterBalanceWei();
     boolean hasBadge(String address, String badgeCode);                 // 뷰 호출
     SentTx sendMint(String address, String badgeCode);                  // 서명·전송, txHash 반환(확정 대기 없음)
     SentTx sendBurn(BigInteger tokenId);
     Optional<TxReceipt> receipt(String txHash);                         // 없으면 pending/unknown
     List<BigInteger> tokensOf(String address);                          // 열거
     long latestBlock();
   }
   ```
   `TxReceipt`: `status(성공/실패/revert)`, `blockNumber`, `confirmations`, `issuedTokenId`(영수증의 `BadgeIssued` 로그에서 파싱), `revertReason?`.
2. **구현** `Web3BadgeChainClient`(또는 JSON-RPC 구현): RPC URL·체인 ID 검증(연결 시 `eth_chainId` 가 설정과 같은지), 가스 추정 + 여유(20%), EIP-1559 수수료는 노드 제안값 + 상한(`max-fee-per-gas-gwei`), nonce 는 `pending` 기준으로 매 전송 직전에 조회(전송은 호출자가 직렬화한다 — C3), 타임아웃(연결 3초/읽기 10초).
3. **가짜 구현** `FakeBadgeChainClient`(테스트용, 인메모리): 전송 성공/실패/지연/중복 발급 revert/영수증 지연을 시나리오로 주입 가능.
4. **설정** `ChainProperties`(`hawe.chain.*`): `enabled`(기본 false), `rpc-url`, `chain-id`, `contract-address`, `explorer-base-url`(앱 링크용), `confirmations`(기본 2), `gas-limit-cap`, `max-fee-per-gas-gwei`, `minter-private-key`(**환경변수 `HAWE_CHAIN_MINTER_KEY` 만**, 프로퍼티 파일에 기본값 금지), `metadata-base-url`(B3). `usable()` = enabled && 필수값 존재.
5. 키 로딩 인터페이스 `MinterKeyProvider`: 기본 구현은 환경변수. (시크릿 매니저 구현은 운영 단계 선택, D3.)
6. 로그: txHash·체인 ID·실패 사유 코드만. **개인키/서명 원문/RPC 키 URL 전체를 로그에 남기지 않는다**(URL 의 API 키 마스킹).
7. 에러 분류: `TransientChainException`(타임아웃·RPC 5xx·nonce too low 등 재시도 가능), `PermanentChainException`(revert: AlreadyIssued·권한 오류 등). C3/C4 가 이에 따라 재시도/종결.

## Files / Functions to Change

| 파일 | 변경 |
|---|---|
| `pom.xml` | 필요한 라이브러리만(결정 근거 기록) |
| `.../badge/service/{BadgeChainClient,Web3BadgeChainClient,FakeBadgeChainClient,MinterKeyProvider}.java` (신규) | 클라이언트 계층 |
| `.../badge/dto/{SentTx,TxReceipt}.java` 또는 domain (신규) | 값 객체 |
| `.../badge/domain/{TransientChainException,PermanentChainException}.java` (신규) | 에러 분류 |
| `.../global/config/ChainProperties.java` | 설정 확장(A1 의 `enabled` 위에) |
| `chain/abi/HaweBadge.json` 의 복사본(리소스) 또는 라이브러리 래퍼 생성물 | 서버가 ABI 사용 |
| `application.properties`, `.env.example` | 이름·기본값(키 값 금지) |

## Constraints

- 서버 테스트(`./mvnw test`)에서 **실제 RPC 호출 금지**(`FakeBadgeChainClient` 사용). 로컬 노드 통합 테스트는 별도 프로필(`-Pchain-it`)로 분리하고 기본 제외.
- `hawe.chain.enabled=false` 이면 어떤 네트워크 접속도 시도하지 않는다(부팅 시 RPC 연결 금지).
- 개인키를 프로퍼티 파일·로그·예외 메시지에 노출하지 않는다.
- 새 의존성은 이 단위에서만, 최소로.
- 커밋/푸시 금지.

## Edge Cases

- RPC 일시 장애·타임아웃 → `Transient`; 응답이 와도 revert → `Permanent`.
- 체인 ID 불일치 → 즉시 실패(잘못된 네트워크에 전송 방지).
- 가스 추정 실패(= 대개 revert) → 사유 분류 후 `Permanent`/`Transient` 구분.
- nonce 충돌/"replacement underpriced" → `Transient`(C3/C4 가 재시도 정책 처리).
- 영수증에 `BadgeIssued` 로그가 없는 성공 → 이상 상태로 `Permanent` 처리 + 경고.
- 잔액 0 → `Transient`(자금 보충 필요) 로 분류하되 C4 가 별도 모니터링.

## Tests Required

- `FakeBadgeChainClient` 시나리오 테스트(성공/중복 revert/지연/타임아웃).
- 영수증 로그 파싱(`BadgeIssued`) 단위 테스트(고정 로그 샘플).
- 설정 검증(`usable()`, 체인 ID 불일치), 예외 분류 매핑, 로그 마스킹 테스트(키/URL 비노출).
- (선택, 별도 프로필) 로컬 노드(Anvil/Hardhat)에 B2 로 배포한 컨트랙트와 실제 민팅·소각·영수증 확인.

## Validation Steps

`./mvnw test` 통과. 별도 프로필로 로컬 노드 통합 테스트를 돌렸다면 결과 첨부. 라이브러리 선택 근거(Java 25 호환 확인 결과)를 보고서에 기록.

## Potential Merge Conflicts

`pom.xml`(다른 의존성 추가 작업), `ChainProperties.java`(A1·B3 와 같은 파일), `application.properties`.
