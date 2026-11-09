# B2. 컨트랙트 배포 스크립트와 배포 기록

> 범위: `chain/` 배포 스크립트 + `chain/deployments/*.json`. **선행: B1.** 서버 코드 변경 없음. 근거: `BLOCKCHAIN_BADGES.md` §5·§13. 

## Goal

로컬 노드와 선택한 테스트넷에 컨트랙트를 **재현 가능하게 배포**하고, 서버(C1)가 읽을 배포 정보(주소·체인 ID·ABI)를 남기며, 역할 설정(소유자/발급 키 분리)을 스크립트로 고정한다.

## Findings

- 결정 1(네트워크): 기본값은 퍼블릭 EVM 테스트넷(Base Sepolia 또는 Polygon Amoy). 체인 ID·RPC URL·탐색기 URL 은 설정으로 분리해 메인넷 이전 시 값만 바꾼다.
- 키 분리(BLOCKCHAIN_BADGES.md §13): **배포자/소유자(ops) 키** ≠ **발급(MINTER) 키**. 소유자 키는 역할 부여/회수·`baseURI` 변경에만 쓰고 서버에 두지 않는다. 발급 키만 서버(C1/C2)가 쓴다.
- 서버는 `chain/deployments/<network>.json` 에서 주소와 ABI 를 읽거나(권장: 복사해 리소스로 포함) 설정으로 받는다. C1 에서 확정.
- 테스트넷은 초기화·중단 가능 → 재배포 경로(새 주소, 재발급)를 문서화한다(D3).

## Key Implementation Points

1. 스크립트 `chain/scripts/deploy` (툴체인 방식에 맞게): 입력 환경변수 — `RPC_URL`, `DEPLOYER_PRIVATE_KEY`(ops 키), `MINTER_ADDRESS`(서버 발급 키의 **주소**, 개인키 아님), `BASE_URI`(B3 메타데이터 URL), `TOKEN_NAME`, `TOKEN_SYMBOL`. 동작: 배포 → `MINTER_ROLE` 을 `MINTER_ADDRESS` 에 부여 → 상태 검증(`hasRole`, `baseURI`) → 결과 출력.
2. 배포 기록 `chain/deployments/<network>.json`: `{ "network", "chainId", "address", "deployer"(주소만), "minter"(주소만), "deployedAt", "blockNumber", "txHash", "baseURI", "abiHash" }` — **개인키·RPC 키 포함 금지**. ABI 는 `chain/abi/HaweBadge.json` 로 별도 커밋.
3. 로컬 개발 흐름: `anvil`(또는 `hardhat node`) 띄우고 배포 스크립트 실행, 테스트 키(툴체인 기본 제공 키)는 **로컬 전용**임을 README 에 명시.
4. 탐색기 검증(verify) 명령을 README 에 문서화(API 키는 환경변수, 커밋 금지). 검증은 선택.
5. 역할 회수/교체 스크립트(`rotate-minter`): 새 발급 주소에 역할 부여 → 기존 주소 회수. 키 유출 대응(D3 런북)에서 사용.
6. `.env.example` 에 이름만 추가(`CHAIN_RPC_URL`, `CHAIN_DEPLOYER_KEY` 등 — 서버용 이름은 C1 에서 정의하는 것과 겹치지 않게 접두사 구분).

## Files / Functions to Change

| 파일 | 변경 |
|---|---|
| `chain/scripts/{deploy,rotate-minter}.*` (신규) | 배포·키 교체 |
| `chain/deployments/local.json`(예시, 선택), `chain/deployments/<testnet>.json` (배포 후) | 배포 기록 |
| `chain/abi/HaweBadge.json` (신규) | ABI |
| `chain/README.md` | 배포·검증·교체 절차 |
| `.env.example` | 변수 이름만(값 없음) |

## Constraints

- 개인키·시드·RPC/탐색기 API 키를 커밋하지 않는다. 배포 기록에는 주소와 해시만.
- 테스트넷 배포는 사용자가 키·자금(faucet)을 준비한 뒤에만 실행한다(자동 실행 금지, 보고서에 "미배포/배포함" 명시).
- 메인넷 배포 스크립트 경로는 만들지 않는다(이번 범위 밖).
- 커밋/푸시 금지.

## Edge Cases

- 배포 실패/부분 성공(배포됐지만 역할 부여 실패) → 스크립트가 재개 가능(이미 배포된 주소를 인자로 받아 역할 부여만 수행하는 모드).
- 같은 네트워크 재배포 → 기존 기록을 덮어쓰기 전 이전 값을 `deployments/history/` 로 보관 또는 경고.
- 가스 부족 → 명확한 오류 메시지.
- 체인 ID 불일치(RPC 가 다른 체인) → 배포 전 검증 후 중단.

## Tests Required

- 로컬 노드에서 배포 스크립트 end-to-end 1회(배포 → `hasRole` 확인 → `mint` 1건 → `tokenURI` 확인) 를 스크립트 또는 테스트로 재현 가능하게.
- `rotate-minter` 로컬 검증(교체 후 이전 키가 mint 불가).

## Validation Steps

로컬 노드 배포 출력과 `deployments/local.json`(또는 형식 예시) 첨부. 테스트넷 배포를 했다면 탐색기 링크 기록. 못 했으면 "테스트넷 미배포".

## Potential Merge Conflicts

`.env.example`, `chain/README.md`(B1 과 같은 파일 — 순차).
