# A6. 업적 — Android 업적 화면

> 범위: Android 앱. **선행: A4**(API). 체인 연동 전이라도 `RECORDING`(미기록) 상태로 동작한다. 근거: `BLOCKCHAIN_BADGES.md` §10. 빌드/테스트: `android\gradlew.bat :app:testDebugUnitTest --offline`(JDK 21).

## Goal

마이페이지에서 들어가는 **업적 화면**: 획득/잠금 업적 그리드, 진행도, 상세 시트(설명·획득일·블록체인 상태), 획득 알림 표시. 지갑·코인·가스 개념은 UI 에 나오지 않는다.

## Findings

- API(A4): `GET /api/me/badges`, `GET /api/badges/catalog`(전체 + 진행도). 상태는 `RECORDING | ON_CHAIN | FAILED`(정규화값), `explorerUrl`/`tokenId` 는 `ON_CHAIN` 일 때만.
- Android 구조: `android/app/src/main/java/com/sapphire/hawe/feature/<name>`, 마이페이지 `feature/mypage/MyPageScreen.kt`, 알림 `feature/notifications`, 디자인시스템 `designsystem`(`HButton`, `HListRow`, `HIcon` 등). 내비게이션·API 저장소 패턴은 기존 `feature`/`core` 를 확인해 따른다. 외부 링크 열기는 `feature/mypage/LicensesScreen.kt` 의 `openInCustomTab` 패턴 재사용.
- 디자인(Figma) 없음 → 기존 컴포넌트 + 임시 에셋(`임시` 주석, `HIcon` 한 곳).
- 구버전 호환: 알림에 새 타입(`badge`)이 오면 알림 화면이 안전하게 처리해야 한다(A4 와 짝) — 알림 목록 파서/표시에서 알 수 없는 타입 처리 확인·보강.
- 다른 멤버 배지 노출은 1차 범위 밖.

## Key Implementation Points

1. `core/…` API 정의·저장소: `BadgeRepository.myBadges()`, `catalog()`; 모델/serialization.
2. `feature/achievements/AchievementsScreen.kt` + `AchievementsViewModel`: 카탈로그 그리드(획득=컬러 아이콘, 미획득=회색 + 진행도 바 `progress/target`), 등급(`COMMON/RARE/EPIC`)별 테두리/표시, 정렬(획득 최신순 → 진행도 높은 순).
3. 상세 시트: 이름·설명·획득일, 상태 문구 — `RECORDING`: "기록 준비 중이에요"(체인 미연동/대기), `ON_CHAIN`: "블록체인에 기록됐어요" + **"블록체인에서 확인"** 버튼(`explorerUrl` 외부 브라우저), `FAILED`: "기록을 다시 시도하고 있어요"(비난/불안 문구 금지). **과장 금지**("영원히 안전" 같은 문구 금지).
4. 진입점: 마이페이지에 "업적" 항목(획득 수/전체 수 표시). 내비게이션 라우트 추가.
5. 획득 직후 표시: 알림 화면에서 `badge` 타입 알림을 업적 상세로 연결(또는 최소한 목록에 문구로 표시).
6. 오프라인/오류: 재시도 안내. 로딩 상태.
7. 접근성: 아이콘에 업적 이름·획득 여부 `contentDescription`.

## Files / Functions to Change

| 파일 | 변경 |
|---|---|
| `android/app/src/main/java/com/sapphire/hawe/feature/achievements/*` (신규) | 화면·ViewModel·시트 |
| `core/…` API·저장소·모델 | 신규 |
| `feature/mypage/MyPageScreen.kt` + 내비게이션 | 진입 항목·라우트 |
| `feature/notifications/*` | `badge` 타입 표시(알 수 없는 타입 안전 처리 포함) |
| `designsystem/assets/HIcon.kt` | 임시 아이콘 |
| 테스트 | 아래 |

## Constraints

- `android/` 만. 서버 API 변경 요구 금지(필요하면 A4 로). 새 의존성 금지(웹3/지갑 SDK 금지).
- 앱에 주소·키·시드를 저장/표시하지 않는다. 지갑 연결/전송/구매 UI 금지.
- 문구에 투자·금전 가치 암시 금지.
- 커밋/푸시 금지.

## Edge Cases

- 획득 0건 → 빈 상태 안내("미션을 제출하면 첫 업적을 받아요").
- 카탈로그에 새 업적이 서버에서 추가됨 → 알 수 없는 `imageKey` 는 기본 아이콘.
- `explorerUrl` 없음/열 수 없음 → 버튼 숨김 또는 토스트.
- 알림 타입 알 수 없음 → 크래시 없이 일반 문구.
- 진행도 클램프, 매우 긴 이름 말줄임.

## Tests Required

- 상태→문구 매핑 순수 함수, 정렬 규칙, 진행도 표시 클램프.
- ViewModel 상태 전이(로딩/성공/빈/오류). 알림 타입 알 수 없음 처리.

## Validation Steps

`android\gradlew.bat :app:testDebugUnitTest --offline` 통과. 수동: 서버(A4)와 연결해 제출 후 업적 획득·목록 갱신, 마이페이지 진입, 알림→상세. 못 했으면 "미검증".

## Potential Merge Conflicts

`MyPageScreen.kt`, 내비게이션 파일, `feature/notifications`, `HIcon.kt`.
