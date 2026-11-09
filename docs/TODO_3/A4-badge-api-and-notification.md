# A4. 업적 — 조회 API, 진행도, 획득 알림

> 범위: 백엔드. **선행: A1, A2**(A3 규칙의 진행도는 점진 추가). 근거: `BLOCKCHAIN_BADGES.md` §10. 빌드/테스트: `./mvnw test`.

## Goal

앱이 업적 화면을 그릴 수 있도록 **획득 목록**과 **전체 카탈로그 + 내 진행도**를 내려주고, 새 업적을 획득하면 앱 안 알림(`BADGE_EARNED`)을 만든다.

## Findings

- 컨트롤러 관례: 기능별 `controller` (예: `notification/…`, `calendar/…`) — 인증 사용자 식별 방식·에러 코드(`BusinessException` + ErrorCode enum)·`ApiResponse` 같은 응답 포맷은 기존 컨트롤러를 보고 따른다(`docs/BACKEND/API_DESIGN.md`).
- 알림: `notification/domain/NotificationType.java` 는 현재 `topic, poke, comment, mention`. `BADGE_EARNED`(또는 `badge`)를 추가하려면 ① `notifications.type` 컬럼이 ENUM 인지 VARCHAR 인지 확인(V1/V3 마이그레이션) ② Android(`feature/notifications`)·iOS 가 **모르는 타입을 받아도 죽지 않는지** 확인(구버전 호환) ③ 알림 payload 에 개인정보/체인 정보를 넣지 않는다. 알림 레코드 구조: `from_user_id`(시스템 알림은 NULL), `to_user_id`, `group_id`/`mission_id`/`submission_id`(NULL 가능), `type`, `payload`(새 알림에서는 쓰지 않음).
- 응답에는 **사용자 주소·개인키·시드를 절대 내려주지 않는다**(결정 2). `explorerUrl` 은 `CONFIRMED` 일 때만, `tokenId` 는 `CONFIRMED` 일 때만.
- 진행도: `progress/target`(예: 연속 5/7일, 제출 7/10회). 계산은 A2/A3 의 집계를 재사용(순수 함수).
- 다른 멤버에게 내 배지 노출은 1차 범위 밖(결정 8) → 본인만 조회.

## Key Implementation Points

1. `GET /api/me/badges` → `[{code, name, description, tier, imageKey, earnedAt, status, explorerUrl?, tokenId?}]`, 획득 최신순. 상태는 앱 표시용으로 정규화: `ISSUED_OFFCHAIN|EARNED|MINTING → "RECORDING"`(블록체인 기록 중/미기록), `CONFIRMED → "ON_CHAIN"`, `FAILED → "FAILED"`(재시도 중) — 내부 상태명을 그대로 노출할지 단순화할지 구현 시 정하고 보고(앱은 문구 표시만).
2. `GET /api/badges/catalog` → `[{code, name, description, tier, imageKey, target, progress, earned, earnedAt?}]` 전체 카탈로그 + 내 진행도. 획득한 업적은 `progress=target`. 진행도 계산이 비싼 업적(스트릭 등)은 사용자 단위 1회 집계(요청당 쿼리 수 상한 확인).
3. **알림**: `BadgeAwardService.award` 가 신규 획득(영향 행 1)이면 알림 한 건 생성(같은 트랜잭션 또는 `BadgeAwardedEvent` 리스너). 중복 알림 금지(획득이 멱등이므로 자연히 1회). `NotificationType` 에 `badge` 추가 시 알림 목록 응답에서 앱이 타입별 문구를 만들 수 있게 `payload` 대신 `refCode`(업적 코드) 같은 구조화 필드를 쓴다 — 기존 알림 스키마에 맞는 방법(예: 별도 컬럼 vs 기존 컬럼 재사용)을 확인해 **스키마 변경 최소**로 결정하고 보고.
4. 보안 설정: 새 경로 `/api/me/badges`, `/api/badges/catalog` 가 인증 필요로 걸리는지 `SecurityConfig` 확인(기본 인증이면 수정 불필요).
5. 문서: `docs/BACKEND/API_DESIGN.md` 또는 `MISSIONS_API.md` 형식에 맞춰 API 절 초안 추가(D3 에서 최종).

## Files / Functions to Change

| 파일 | 변경 |
|---|---|
| `.../badge/controller/BadgeController.java` (신규) | 두 엔드포인트 |
| `.../badge/dto/{MyBadgeResponse,BadgeCatalogItem}.java` (신규) | 응답 DTO |
| `.../badge/service/BadgeQueryService.java` (신규) | 목록·카탈로그·진행도 |
| `.../notification/domain/NotificationType.java` | `badge` 추가(호환 확인 후) |
| `.../notification/…` 생성 서비스/매퍼 | 배지 알림 생성(기존 생성 패턴 재사용) |
| 마이그레이션(필요 시) | 알림 type 컬럼이 ENUM 일 때만 |
| 테스트 | 아래 |

## Constraints

- 주소·키·시드·내부 오류 메시지를 응답에 포함하지 않는다.
- 구버전 앱 호환: 알림 타입 추가가 기존 클라이언트를 깨뜨리지 않는지 확인 후에만 추가. 깨진다면 알림은 **앱 업데이트 이후**에 켜는 스위치(`hawe.badge.notify`)를 둔다.
- 알림 payload 에 개인정보/체인 정보 금지.
- 커밋/푸시 금지.

## Edge Cases

- 획득 0건 → 빈 배열(200).
- 카탈로그에서 제거된 코드가 DB 에 있음 → 목록에서 제외.
- 진행도 > target → target 으로 클램프.
- 알림 생성 실패 → 수여 자체를 롤백하지 않음(알림 실패는 로그만).
- 상태가 `FAILED` 여도 업적 달성은 앱에 표시(체인 문제를 사용자 책임으로 보이게 하지 않음).

## Tests Required

- 컨트롤러/서비스: 인증 필요(401), 본인 데이터만, 상태 정규화 매핑, `CONFIRMED` 가 아닐 때 `explorerUrl`/`tokenId` 없음.
- 진행도: 대표 업적별 값(경계: 0, target−1, target 초과).
- 알림: 신규 획득 1건, 중복 수여 시 알림 없음, 구버전 호환 확인(응답 필드 추가가 기존 알림 목록 직렬화에 영향 없음).

## Validation Steps

`./mvnw test` 통과. 수동: curl/Postman 으로 두 엔드포인트와 알림 목록 확인. Postman 컬렉션 추가는 선택(`docs/BACKEND/postman/`).

## Potential Merge Conflicts

`NotificationType.java`, 알림 생성 서비스(다른 알림 작업과 순차), `SecurityConfig`(변경 없을 가능성).
