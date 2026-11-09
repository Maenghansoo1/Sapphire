# A1. 업적 — 스키마, 카탈로그, 도메인 (체인 없이)

> 범위: 백엔드 신규 feature 패키지 `com.sapphire.hawe.badge/{controller,service,dto,domain,dao}`. **판정 로직은 A2/A3, API 는 A4.** 근거: `docs/FEATURE/BLOCKCHAIN_BADGES.md` §7·§8. 빌드/테스트: `./mvnw test`(Java 25).

## Goal

업적 종류를 **코드/설정 카탈로그**로 정의하고, 사용자가 획득한 업적을 저장하는 `user_badges` 테이블과 도메인·매퍼를 만든다. 이 단계는 체인과 무관하다.

## Findings

- 틀(template)과 같은 이유로 업적 종류는 DB ENUM 이 아니라 코드/설정으로 관리한다(추가 시 마이그레이션 불필요). 참고: `MissionTemplate`(`mission/domain`) 이 enum 으로 틀을 정의한다. 업적은 설정 변경으로 임계값(예: 스트릭 7/30일)을 바꿀 수 있어야 하므로 **Java enum 대신 코드 안의 불변 카탈로그 클래스 + `application.properties` 로 조정 가능한 파라미터**를 권장한다(구현자가 더 단순한 쪽을 선택하고 이유를 보고서에 기록).
- 마이그레이션: `src/main/resources/db/migration/` 에 `V1`~`V4`(`V4__ai_mascot.sql` 까지). 새 파일(`V5__user_badges.sql`, 번호는 최신 확인)을 기본으로 추가.
- 상태 값: `EARNED | MINTING | CONFIRMED | FAILED | ISSUED_OFFCHAIN` (BLOCKCHAIN_BADGES.md §8). 체인이 꺼져 있으면(`hawe.chain.enabled=false`) 새 업적은 `ISSUED_OFFCHAIN` 으로 저장되고, 체인을 켜면 C3 가 이를 `EARNED` → 발급 큐로 이어서 처리한다.
- 멱등: `UNIQUE (user_id, badge_code)`. 반복 가능 배지(월간 개근 등)는 1차 범위 밖(결정 9) — 스키마에 `period` 컬럼을 **넣지 않는다**(필요해지면 마이그레이션으로 확장).
- 초안 카탈로그(BLOCKCHAIN_BADGES.md §7): `FIRST_SUBMISSION`, `SUBMISSIONS_10/50/100`, `STREAK_7/30`, `PERFECT_MONTH`, `EARLY_BIRD_10`, `PHOTO_PERFECT_5`, `CROWD_FAVORITE`, `GROUP_100_DAYS`, `GROUP_1_YEAR`, `ALL_TEMPLATES`.

## Key Implementation Points

1. **스키마**
   ```sql
   user_badges(
     id BIGINT PK AUTO_INCREMENT,
     user_id BIGINT NOT NULL,            -- FK users
     badge_code VARCHAR(40) NOT NULL,
     status VARCHAR(20) NOT NULL,        -- EARNED|MINTING|CONFIRMED|FAILED|ISSUED_OFFCHAIN
     earned_at DATETIME NOT NULL,        -- 실제 달성 시각(소급 시 달성일)
     token_id VARCHAR(80) NULL, tx_hash CHAR(66) NULL, chain_id INT NULL, confirmed_at DATETIME NULL,
     created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
     UNIQUE (user_id, badge_code),
     INDEX (user_id, earned_at))
   ```
   FK 는 `users(id)`; 삭제는 서비스가 순서를 지킨다(D1). `badge_mint_jobs`·`user_chain_accounts` 는 C 단계에서 만든다(여기서 만들지 않음).
2. **카탈로그**: `BadgeCatalog`(domain) — 항목 `BadgeDefinition(code, nameKo, descriptionKo, tier(COMMON/RARE/EPIC), imageKey, target(진행도 목표값, 예: 7), kind(EVENT|SCHEDULED))`. 이름·설명은 한국어, 이미지 키는 앱이 해석(메타데이터는 B3). 조회: `all()`, `byCode(code)`.
3. **매퍼** `BadgeMapper`(dao + XML): `insertIgnore(userId, code, status, earnedAt)`(`INSERT IGNORE` → 영향 행 수로 "새로 획득" 판정), `findByUserId`, `findByUserIdAndCode`, `countByUser`. MyBatis 설정·XML 위치는 기존 매퍼 규칙을 따른다.
4. **서비스 뼈대** `BadgeAwardService.award(userId, code, earnedAt)`: 카탈로그에 없는 코드 거부, `insertIgnore` 결과가 1이면 "신규 획득" 반환(알림은 A4 에서 연결), 상태는 `chainEnabled ? EARNED : ISSUED_OFFCHAIN`. 설정 `hawe.chain.enabled`(기본 false)를 읽는 프로퍼티 클래스 `ChainProperties` 는 C 단계에서 확장하므로 여기서는 `enabled` 만.
5. 도메인 객체 `UserBadge`(MyBatis 매핑), 상태 enum `BadgeStatus`.

## Files / Functions to Change

| 파일 | 변경 |
|---|---|
| `src/main/resources/db/migration/V5__user_badges.sql` (신규, 번호 확인) | 스키마 |
| `.../badge/domain/{BadgeDefinition,BadgeCatalog,BadgeStatus,UserBadge}.java` (신규) | 도메인 |
| `.../badge/dao/BadgeMapper.java` + `resources/.../BadgeMapper.xml` (신규; 기존 매퍼 XML 위치 규칙 확인) | 저장 |
| `.../badge/service/BadgeAwardService.java` (신규) | 수여 |
| `.../global/config/ChainProperties.java` (신규, `enabled` 만) | 스위치 |
| `application.properties` | `hawe.chain.enabled=false` 주석 포함 |
| 테스트 | 아래 |

기존 코드 수정 없음(탈퇴 삭제는 D1).

## Constraints

- 체인 호출·키·지갑 코드를 이 단위에 넣지 않는다.
- 사용자에게 보이는 이름/설명에 과장 금지("블록체인으로 영원히 보증" 같은 문구 금지).
- 기존 마이그레이션 파일을 수정하지 않는다(체크섬).
- 커밋/푸시 금지.

## Edge Cases

- 같은 업적 동시 수여(경쟁) → `UNIQUE` + `INSERT IGNORE` 로 한 번만, 반환값 0 이면 신규 아님.
- 카탈로그에서 제거된 코드가 DB 에 남음 → 조회 시 무시(예외 금지).
- `earned_at` 이 미래 → 서버 시각으로 클램프.
- 탈퇴한 사용자 FK → D1 이 순서대로 삭제.

## Tests Required

- 매퍼: `insertIgnore` 신규/중복(영향 행 1/0), 사용자별 조회 정렬, 유니크 위반 없음.
- `BadgeAwardService`: 신규 획득 true / 중복 false / 미등록 코드 거부 / 체인 off 시 상태 `ISSUED_OFFCHAIN`, on 시 `EARNED`.
- 카탈로그 일관성: 코드 중복 없음, `target`·tier 값 유효.

## Validation Steps

`./mvnw test` 통과. 보고서에 마이그레이션 번호·카탈로그 구현 방식(enum vs 클래스+설정) 선택 이유를 기록.

## Potential Merge Conflicts

마이그레이션 번호(다른 스키마 작업), `application.properties`.
