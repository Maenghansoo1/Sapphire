# A7. Android — "오늘의 주제 찰떡상" 배지·한 줄 코멘트 표시

> 범위: **Android 앱만**(`android/`). **전제: 백엔드 B10(사진-주제 적합도, 응답의 `aiReview`)이 커밋된 뒤, A0(음성 제거) 이후.** iOS 는 A5. 구현자: Claude 또는 Codex. 디자인이 없으므로 배지 이미지는 **임시 이미지**로 만든다. 서버가 `hawe.vision.enabled=false`(기본)이면 `aiReview` 가 오지 않으므로 앱은 아무것도 표시하지 않는다(기능 플래그가 서버에 있음).
> 빌드/테스트: JDK 21 — `set JAVA_HOME=C:\Users\kimch\.jdks\ms-21.0.10` 후 `android\gradlew.bat :app:testDebugUnitTest --offline`.

## Goal

사진 미션의 피드·상세에서, 서버가 AI 심사 결과를 내려준 제출물에 **배지("오늘의 주제 찰떡상")와 한 줄 코멘트**를 보여 준다. 점수·순위·낮은 평가는 서버가 내려주지 않으며 앱도 만들지 않는다.

## Findings

- 서버 계약(B10): `SubmissionResponse.aiReview = { "badge": "PERFECT_FIT" | null, "comment": "주제랑 딱 맞는 사진이에요!" | null }` — 심사가 끝났고 표시할 것이 있을 때만 존재(그 외 키 자체가 없음). 심사는 제출 직후가 아니라 **비동기**로 끝나므로 제출 직후 응답에는 없고, 이후 피드/상세를 다시 불러올 때 나타난다. 제출자 본인과 같은 그룹의 제출한 멤버가 모두 본다(피드 잠금 규칙과 동일). 사진 틀 `PHOTO_NOW`/`PHOTO_COLOR` 만 대상.
- 앱 현재 상태: `core/model/Submission.kt` 에 `aiReview` 없음. `SubmissionDto`(`core/net/SubmissionApi.kt`)와 `SubmissionRepository.toModel` 이 응답 매핑. 사진 피드: `ResultsScreen.kt` 의 `PhotoFeed`(카드: 아바타·닉네임·시각·지각 + `PhotoArtwork`), 상세: `SubmissionDetailScreen.kt` 의 `DetailArtwork`(PHOTO → `PhotoArtwork(submission, 300.dp)`) 아래 7A `SubmissionSocial`. 결과 화면은 `ON_RESUME` 마다 `refresh++` 로 피드를 다시 읽으므로 뒤로 갔다 오면 비동기 결과가 반영된다.
- 임시 이미지 선례: `res/drawable/stamp_good/late/penalty.xml`(10단계), A1 의 `ic_poke_temp.xml` 방식. `HIcon` enum(`designsystem/assets/HIcon.kt`)에 아이콘을 추가하는 패턴.
- 디자인 토큰: `HColors.accent`(`0xFF67A0C3`), `accentSubtle`, `HType.plex(...)`.

## Root Cause / Key Implementation Points

1. **모델**: `data class AiReview(val badge: String?, val comment: String?)`(`core/model/Submission.kt`), `Submission.aiReview: AiReview? = null`. DTO `AiReviewDto(badge: String? = null, comment: String? = null)`, `SubmissionDto.aiReview: AiReviewDto? = null`, `toModel` 매핑. 알 수 없는 `badge` 값은 배지 없이 코멘트만 표시(전방 호환).
2. **표시 컴포넌트** `feature/results/AiReviewBadge.kt`: `@Composable fun AiReviewLine(review: AiReview)` — `badge == "PERFECT_FIT"` 이면 둥근 칩(배경 `accentSubtle`, 글자 `accent`) "오늘의 주제 찰떡상" + 임시 배지 아이콘 `ic_badge_fit_temp`; `comment` 가 있으면 칩 아래/옆에 `HType.caption13` 한 줄(최대 1줄 말줄임, 상세에서는 2줄). 접근성: 칩에 `contentDescription = "오늘의 주제 찰떡상"`. 문구 상수는 `ResultsFormat.kt` 에 둬서 테스트 가능하게(`aiBadgeLabel(badge): String?`).
3. **배치**: `PhotoFeed` 카드의 `PhotoArtwork` 바로 아래(작성자 줄 위 아님), 상세 화면은 `DetailArtwork` 아래·`SubmissionSocial` 위. 사진 외 유형에는 표시하지 않는다(서버가 주지 않지만 방어적으로 `type == PHOTO` 에서만 호출).
4. **임시 이미지**: `res/drawable/ic_badge_fit_temp.xml`(원형 배경 + 별/리본 단순 벡터, 파일 첫 줄 주석 `<!-- 임시 이미지: Figma 디자인 확정 시 교체 -->`), `HIcon.BADGE_FIT_TEMP` 추가(교체 지점 한 곳).
5. **갱신 UX**: 제출 직후 결과 화면으로 이동하면 아직 배지가 없을 수 있다 → 별도 폴링은 만들지 않는다(서버 부하/복잡도). 대신 결과 화면이 `ON_RESUME` 새로고침을 이미 하므로 충분. 선택 개선: 결과 화면 진입 후 처음 한 번 5초 뒤 자동 새로고침(내 제출이 사진이고 `aiReview == null` 일 때만) — 구현 비용이 작으면 포함, 아니면 보고서에 후속 제안으로.

## Files / Functions to Change

| 파일 | 변경 |
|---|---|
| `core/model/Submission.kt` | `AiReview`, `Submission.aiReview` |
| `core/net/SubmissionApi.kt`, `SubmissionRepository.kt` | DTO·매핑 |
| `feature/results/AiReviewBadge.kt` (신규), `ResultsFormat.kt` | 컴포넌트·문구 |
| `feature/results/ResultsScreen.kt`, `SubmissionDetailScreen.kt` | 배치 |
| `designsystem/assets/HIcon.kt`, `res/drawable/ic_badge_fit_temp.xml` (신규) | 임시 아이콘 |
| 테스트 | `SubmissionRepositoryTest`(`aiReview` 있음/없음/알 수 없는 badge/코멘트만), `ResultsFormatTest`(`aiBadgeLabel`) |

## Implementation Steps

1. 모델/DTO/매핑 + 테스트. 2. 컴포넌트·임시 아이콘. 3. 피드/상세 배치. 4. `android\gradlew.bat :app:testDebugUnitTest --offline`. 5. 서버(`hawe.vision.enabled=true` + 비전 서비스 기동)로 수동 확인은 사용자.

## Constraints

- `android/` 만. 점수·퍼센트·"낮은 적합도" 같은 표현을 만들거나 표시하지 않는다. 서버 값이 없으면 UI 를 비운다(자리 차지 금지, 레이아웃 점프 최소화). 새 라이브러리 금지. 커밋/푸시 금지.

## Edge Cases

- `aiReview` 없음(비활성/미완/FAILED/낮은 점수) → 아무것도 표시하지 않음. 코멘트만 있고 배지 없음 → 코멘트만. 배지만 있고 코멘트 없음 → 칩만.
- 긴 코멘트 → 말줄임. 다크 모드가 없으므로 라이트 전용 색.
- 그림/글 유형의 제출에 `aiReview` 가 와도 표시 안 함.
- 내 제출이 `PHOTO` 인데 아직 비동기 완료 전: 표시 없음(정상).

## Regression Risks

- `Submission` 생성자에 기본값 있는 필드 추가 → 기존 생성부 호환. `SubmissionDto` 에 기본값 필드 추가 → 기존 응답 파싱 영향 없음(`ignoreUnknownKeys`/기본값).
- 사진 피드 카드 높이 변화로 스크롤 위치가 새로고침 시 점프하는지 확인.

## Tests Required

위 표 + 컴포넌트는 컴파일/수동 확인.

## Validation Steps

`android\gradlew.bat :app:testDebugUnitTest --offline`(JDK 21) 통과 기록. 서버 연동 시나리오(사진 제출 → 몇 초 뒤 결과 화면 재진입 → 배지) 체크리스트를 보고서에 포함, 못 했으면 "미검증".

## Potential Merge Conflicts

`Submission.kt`, `SubmissionRepository.kt`, `ResultsScreen.kt`, `SubmissionDetailScreen.kt`, `HIcon.kt`(A0·A1·A4·A8 과 겹침 — A0 먼저).
