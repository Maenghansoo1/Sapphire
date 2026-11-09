# C2. 사용자 온체인 주소 할당과 `user_chain_accounts`

> 범위: 백엔드 `badge`. **선행: C1.** 근거: `BLOCKCHAIN_BADGES.md` §6·결정 2. 빌드/테스트: `./mvnw test`.

## Goal

사용자마다 **서로 다른 익명 온체인 주소**를 할당하고(같은 사용자의 배지는 같은 주소), 사용자↔주소 매핑을 **서버 DB 에만** 저장한다. 서버가 사용자 주소의 개인키를 보관하는 위험을 최소화한다.

## Findings

- 원 설계(BLOCKCHAIN_BADGES.md §6): 서버가 **HD 키 파생**(`m/44'/60'/0'/0/{userIndex}`)으로 커스터디얼 주소를 만들고 시드는 시크릿 매니저에 둔다.
- **더 안전한 대안을 이 단위에서 검토·권장**한다(결정 2 보완): 배지는 **양도 불가(soulbound)** 라서 사용자 주소는 *서명할 일이 없다* — 서버 발급 키만 `mint`/`burn` 한다(B1). 따라서 사용자 주소에는 개인키가 필요 없다.
  - (a) **무작위 주소 + 키 폐기(권장 기본)**: 안전한 난수로 키쌍을 만들어 주소만 저장하고 개인키는 즉시 폐기. 보관할 시드가 없어 유출 위험이 사라진다. 단점: 나중에 사용자가 "내 지갑으로 내보내기"를 원하면 소각 후 사용자 제공 주소로 재발급하는 방식이어야 한다(1차 범위 밖).
  - (b) **xpub 파생**: 확장 공개키(xpub)만 서버에 두고 자식 주소를 파생(개인키 시드는 오프라인). 구현이 복잡하고 라이브러리 지원이 제한적(public derivation).
  - (c) **시드 보관 HD(원안)**: 시크릿 매니저의 시드로 파생. 유출 위험과 운영 부담이 가장 큼.
  - 이 단위는 `UserAddressAllocator` 인터페이스 뒤에 구현을 두고 **(a)를 기본 구현**한다. (b)/(c) 채택은 사용자 결정이 있을 때만 별도 구현(보고서에 선택 이유 기록). **BLOCKCHAIN_BADGES.md §6 과 달라지므로 사용자 확인이 필요하다.**
- 주소는 사용자별로 달라야 한다(다른 사용자와 배지를 합쳐 추적할 수 없게). 같은 사용자는 항상 같은 주소(소유 증명, 배지 코드 중복 방지 `(to, code)`).
- `user_chain_accounts(user_id PK, address CHAR(42) UNIQUE, created_at)`. `derivation_index` 는 (a)에서 불필요(구현 방식 (b)/(c) 일 때만).
- 주소를 앱 API 에 내려주지 않는다(결정 2 기본).

## Key Implementation Points

1. **스키마**: 새 마이그레이션(번호 확인) — `user_chain_accounts(user_id BIGINT PK, address CHAR(42) NOT NULL UNIQUE, created_at DATETIME NOT NULL)`, FK `users(id)`(삭제는 D1 이 순서대로).
2. `UserAddressAllocator` 인터페이스: `String allocate()` — (a) 구현 `RandomAddressAllocator`: `SecureRandom` 으로 secp256k1 키쌍 생성 → 주소(체크섬 형식) 계산 → **개인키 바이트 메모리에서 0 으로 덮어쓰기** 후 폐기. 주소 계산에 필요한 라이브러리는 C1 에서 추가한 것을 재사용.
3. `UserChainAccountService.getOrCreate(userId)`: 있으면 반환, 없으면 할당해 `INSERT`(경쟁 시 `UNIQUE(user_id)` 충돌은 재조회로 해결 — 항상 한 주소만 존재). 주소 충돌(확률 무시 가능)은 `UNIQUE(address)` 위반 시 재할당.
4. 매퍼 `ChainAccountMapper`: `findByUserId`, `insertIgnore`, `deleteByUserId`(D1), `findAddressesByUserIds`(선택).
5. 로그: 주소 전체를 INFO 로그에 남기지 않는다(앞 6자리 정도만 DEBUG). 사용자 ID 와 주소를 한 줄에 함께 남기지 않는다.
6. 설정: `hawe.chain.address-allocator=random`(기본) — 나머지 구현을 추가할 여지만 남긴다.

## Files / Functions to Change

| 파일 | 변경 |
|---|---|
| `src/main/resources/db/migration/V*__user_chain_accounts.sql` (신규, 번호 확인) | 스키마 |
| `.../badge/service/{UserAddressAllocator,RandomAddressAllocator,UserChainAccountService}.java` (신규) | 할당 |
| `.../badge/dao/ChainAccountMapper.java` + XML (신규) | 저장 |
| `.../badge/domain/UserChainAccount.java` (신규) | 도메인 |
| 테스트 | 아래 |

## Constraints

- **사용자 주소의 개인키를 DB/로그/파일에 저장하지 않는다**((a) 기본). 발급 키(MINTER) 는 C1 의 `MinterKeyProvider` 로만.
- 사용자↔주소 매핑은 DB 에만. API 응답·알림·로그에 주소를 노출하지 않는다.
- 난수는 `SecureRandom` 만 사용(`java.util.Random` 금지).
- 커밋/푸시 금지.

## Edge Cases

- 동시에 같은 사용자의 첫 업적 두 개 → `getOrCreate` 경쟁: 하나만 생성, 다른 쪽은 기존 주소 사용(테스트).
- 탈퇴(D1)로 매핑 삭제 후 같은 `user_id` 재사용은 없음(AUTO_INCREMENT) → 새 주소.
- 체크섬 대소문자 형식 차이로 같은 주소가 다르게 저장되지 않도록 한 형식(EIP-55)으로 정규화 후 비교.
- 주소 UNIQUE 충돌 → 재할당 루프 상한(예: 5회) 후 실패.

## Tests Required

- 할당 주소 형식(0x + 40 hex, EIP-55), 사용자별 상이, 같은 사용자 재호출 동일, 경쟁 시 단일 주소, 개인키 비저장(할당기가 키를 반환/보관하는 필드가 없음), 정규화.
- 매퍼: insertIgnore/find/delete.

## Validation Steps

`./mvnw test` 통과. 보고서에 (a)/(b)/(c) 중 선택과 이유, 사용자 확인 필요 사항 기록.

## Potential Merge Conflicts

마이그레이션 번호, `pom.xml`(C1 의 라이브러리 재사용 — 추가 없음 목표).
