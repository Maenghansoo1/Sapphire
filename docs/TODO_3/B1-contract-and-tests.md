# B1. 스마트 컨트랙트 — 양도 불가 배지(ERC-721 + ERC-5192) 와 테스트

> 범위: 저장소 신규 디렉터리 `chain/`(Solidity). 서버·앱 코드 변경 없음. **선행: 결정 1(체인), 결정 7(툴체인).** 근거: `docs/FEATURE/BLOCKCHAIN_BADGES.md` §5·§14. 테스트: 선택한 툴체인(Foundry `forge test` 또는 Hardhat `npx hardhat test`).

## Goal

서버(발급 키)만 발급·소각할 수 있고, **사용자가 전송·승인할 수 없는** 배지 NFT 컨트랙트를 만든다. 같은 `(받는 주소, 배지 코드)` 는 한 번만 발급된다(온체인 멱등).

## Findings

- 표준: ERC-721 + **ERC-5192(Minimal Soulbound)** — `locked(uint256 tokenId) returns (bool)` 가 항상 `true`, 발급 시 `Locked(tokenId)` 이벤트, `supportsInterface(0xb45a3c0e)`. OpenZeppelin 컨트랙트(v5 계열)를 사용하고 보안 로직을 새로 짜지 않는다. 사용할 정확한 버전은 구현 시 확인·고정.
- 역할: `MINTER`(서버 발급 키) 는 `mint`·`burn` 만, `owner`(운영 키, 발급 키와 **분리**)는 역할 부여/회수·`baseURI` 변경. (BLOCKCHAIN_BADGES.md §5·§13.)
- **소각 경로가 중요하다**: 탈퇴(D1)는 "특정 주소가 가진 모든 토큰을 소각"해야 하는데 서버는 토큰 ID 를 DB 에 못 가질 수 있다(삭제 후). → `ERC721Enumerable`(또는 `tokensOfOwner` 헬퍼)로 **주소별 토큰 열거**가 가능해야 한다. 가스는 테스트넷이라 문제없지만 열거 비용은 문서화.
- 배지 코드는 서버 카탈로그의 문자열(`FIRST_SUBMISSION` 등, 40자 이하). 온체인에는 **배지 종류 코드, 발급 시각, 토큰 ID, 익명 주소**만 올린다 — 개인정보·그룹/미션/제출 ID 금지.
- `tokenURI` = `baseURI + badgeCode + ".json"` (서버가 B3 에서 정적 메타데이터 제공).

## Key Implementation Points

1. `chain/contracts/HaweBadge.sol`:
   - `contract HaweBadge is ERC721Enumerable, AccessControl, IERC5192` (또는 동등 구성).
   - `bytes32 MINTER_ROLE`; `constructor(string name, string symbol, string baseURI, address owner)` — `DEFAULT_ADMIN_ROLE` = owner(운영 키), `MINTER_ROLE` 은 배포 후 owner 가 서버 키에 부여.
   - `mint(address to, string badgeCode) returns (uint256 tokenId)` — `onlyRole(MINTER_ROLE)`; `keccak256(abi.encodePacked(to, badgeCode))` 로 중복 확인 → 중복이면 revert(`AlreadyIssued`); `_mint`(**`_safeMint` 금지** — 수신 주소가 키 없는 임의 주소일 수 있고 컨트랙트 콜백을 피한다); `issuedAt` 저장; `emit BadgeIssued(to, tokenId, badgeCode, issuedAt)`; `emit Locked(tokenId)`.
   - `burn(uint256 tokenId)` — `onlyRole(MINTER_ROLE)`; 중복 확인 매핑도 해제(같은 주소에 재발급 가능해야 오발급 정정이 된다 → 단 탈퇴 시에는 주소 자체가 폐기됨). `emit BadgeBurned(tokenId)`.
   - 양도·승인 차단: OZ v5 의 `_update` 오버라이드로 **mint(from==0)와 burn(to==0) 외 모든 이동 revert**(`Soulbound`), `approve`/`setApprovalForAll` 도 revert.
   - `locked(tokenId)` → 존재하면 `true`, 없으면 revert(`ERC721NonexistentToken`).
   - `badgeCodeOf(tokenId)`, `issuedAtOf(tokenId)`, `has(address, badgeCode)` 뷰 함수(서버의 멱등 확인용).
   - `setBaseURI(string)` — `DEFAULT_ADMIN_ROLE` 전용. `tokenURI` 는 `baseURI + badgeCode + ".json"`.
2. `chain/test/` 단위 테스트(아래 Tests).
3. `chain/README.md`: 설치(툴체인 버전 고정), 테스트 실행 명령 1줄, 보안 메모(역할 분리, 키 관리, 업그레이드 불가 — 필요 시 재배포 + 재발급 경로, 문서 D3 참조).
4. 가스/크기: 최적화는 하지 않는다(테스트넷). 단 `optimizer` 설정은 툴체인 기본으로.

## Files / Functions to Change

| 파일 | 변경 |
|---|---|
| `chain/contracts/HaweBadge.sol` (신규) | 컨트랙트 |
| `chain/test/*` (신규) | 테스트 |
| `chain/{foundry.toml 또는 hardhat.config.*, package.json 또는 remappings}` (신규) | 툴체인 설정(버전 고정) |
| `chain/README.md` (신규) | 사용법 |
| `.gitignore` | `chain/node_modules`, `chain/out`, `chain/cache`, `.env` 등 빌드 산출물 |

서버/앱 코드는 이 단위에서 수정하지 않는다.

## Constraints

- 개인키·시드·RPC 키를 저장소에 넣지 않는다. 테스트는 툴체인 내장 로컬 체인(Anvil/Hardhat network)만 사용.
- OpenZeppelin 코드를 복사·수정하지 않는다(의존성으로 사용). 새 암호/보안 로직 작성 금지.
- 컨트랙트에 사용자 식별·개인정보 필드를 넣지 않는다.
- 업그레이드 가능(프록시) 구조는 만들지 않는다(복잡도 대비 이득 작음).
- 커밋/푸시 금지.

## Edge Cases

- 같은 주소에 같은 코드를 두 번 발급 → revert. 다른 코드는 정상 발급.
- `to == address(0)` → revert(OZ 기본).
- 존재하지 않는 토큰 `burn` → revert. 소각 후 같은 `(to, code)` 재발급 허용.
- MINTER 가 아닌 주소가 `mint`/`burn` → revert.
- 사용자(소유자)가 `transferFrom`/`safeTransferFrom`/`approve`/`setApprovalForAll` 시도 → 모두 revert.
- `baseURI` 변경 후 `tokenURI` 반영.
- 열거: 소각 후 `tokenOfOwnerByIndex`/`balanceOf` 일관성.

## Tests Required

- 발급(토큰 ID 증가, 이벤트 `BadgeIssued`/`Locked`, `locked()==true`, `badgeCodeOf`/`issuedAtOf`), 중복 revert, 비-MINTER revert, 양도·승인 전부 revert, `burn` 권한과 재발급, 열거(발급 3개 → 소각 1개 → 남은 2개), `supportsInterface`(ERC-721, ERC-5192, AccessControl), `setBaseURI` 권한, `tokenURI` 포맷.
- 퍼징/속성(선택, Foundry 이면): "어떤 호출 순서에서도 소유자 이동 불가" 불변식.

## Validation Steps

테스트 명령이 통과한 출력과 사용한 툴체인·OpenZeppelin 버전을 보고서에 기록. 가스 사용량 표(발급/소각)는 참고용으로 첨부.

## Potential Merge Conflicts

신규 디렉터리라 없음. `.gitignore` 만 겹칠 수 있음.
