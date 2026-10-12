# A9. Android — 그룹 활동 인사이트 대시보드 화면

> 범위: **Android 앱만**(`android/`). **전제: 백엔드 B11(`GET /api/groups/{groupId}/insights`)이 커밋된 뒤, A0 이후.** iOS 는 A5. 알림 무관. 구현자: Claude 또는 Codex. Figma 가 없으므로 **임시 UI**(기존 컴포넌트 + 단순 도형 + 임시 아이콘)로 만든다.
> 빌드/테스트: JDK 21 — `set JAVA_HOME=C:\Users\kimch\.jdks\ms-21.0.10` 후 `android\gradlew.bat :app:testDebugUnitTest --offline`.

## Goal

그룹 정보(16) 화면에서 진입하는 **"우리 그룹 이야기"** 화면: 월 선택 → **활동 지수, 가장 빨리 낸 사람, 많이 쓰인 단어, 가장 반응이 많았던 글, 하이라이트 문장**을 보여 준다. 서버가 아직 생성 중(202)이면 로딩 후 재시도한다.

## Findings

- 서버 계약(B11): `GET /api/groups/{groupId}/insights?month=YYYY-MM` →
  - **200** `{ groupId, month, status:"READY", stale, empty, generatedAt, activityIndex{score, deltaFromPreviousMonth?, participationRate, onTimeRate, interactionRate}, fastest{userId,nickname,characterKey,profileImageUrl,firstCount,averageResponseSeconds,fastestSingleSeconds}?, topWords[{word,count,memberCount}], mostReacted{submissionId,missionId,missionContent,template,authorUserId,authorNickname,reactionCount,commentCount,topSticker}?, highlights[String] }` (없는 섹션은 `null`/빈 배열; `empty:true` 면 기록 없음).
  - **202** `{ status:"GENERATING", retryAfterSeconds }` → 앱이 지연 후 재호출(최대 N회).
  - 오류: 404 `GROUP_NOT_FOUND`, 403 `NOT_GROUP_MEMBER`, 400 `VALIDATION_ERROR`(월 범위: 과거 12개월·미래 불가).
  - `mostReacted` 는 **본문 없이 메타데이터만** — 글을 보려면 기존 상세 `GET /submissions/{id}` 로 이동(그 미션을 내가 안 냈으면 423 `FEED_LOCKED`).
- 앱 구조: 라우트 `MainRoute`(`app/MainNavigation.kt`, `@Serializable` sealed interface), 그룹 정보 `GroupInfoScreen`(`feature/groups/GroupInfoScreen.kt`; 멤버 목록·설정 행 `HListRow`), 결과/상세 라우트 `Results(groupId, missionId)`, `SubmissionDetail(groupId, missionId, submissionId)`; 상세는 `store.result(groupID, missionID)` 에 피드가 로드돼 있어야 열린다(`SubmissionDetailScreen` 은 없으면 빈 화면, `MainNavigation` 이 `popBackStack`). → 인사이트에서 "가장 반응이 많았던 글" 탭 시 **결과 화면(`Results`)으로 이동**(결과 화면이 피드를 로드하고, 잠겨 있으면 `onLocked` 안내)한 뒤 해당 글로 이동하는 단순 경로를 쓴다(직접 상세 이동 금지).
- 레포지토리 패턴: `GroupRepository`, `CalendarRepository`(`core/net/CalendarRepository.kt` — 달력/통계를 `ApiClient.get` 으로 읽고 모델로 변환). 새 `InsightRepository` 를 `AppViewModel` 에서 만들어 `MainNavHost` 로 전달하는 기존 방식.
- 시간/월 표시: 한국어 포맷 유틸 `core/HaweTime.kt`, `feature/results/ResultsFormat.kt`(`koreanMonthDayText` 등). `RecordCalendarScreen` 이 월 이동 UI(`YearMonth`)를 이미 가진다 — 월 선택 패턴 재사용 가능.
- 아바타: `HAvatar(AvatarContent, size)`, 원격 사진은 A2 의 `avatarOf`.
- 그림/차트 라이브러리 없음 → Compose 기본 도형(`Box` 폭 비율, `Canvas`)으로 막대/게이지 구현.

## Root Cause / Key Implementation Points

1. **모델/레포지토리**: `core/model/GroupInsight.kt`(`ActivityIndex`, `FastestMember`, `TopWord`, `MostReacted`, `GroupInsight`) + `core/net/InsightApi.kt`(DTO) + `InsightRepository.load(groupId, month): InsightResult`(`sealed: Ready(GroupInsight) / Generating(retryAfter) / Empty`). 202 는 `ApiClient` 가 2xx 로 취급하므로 **응답 본문의 `status` 로 분기**(`get<InsightDto>`; `ready` 필드는 모두 nullable). 오류 코드 분류 순수 함수 `classifyInsightFailure(error)`.
2. **재시도 정책**(`InsightLoader`, 순수 로직 + 코루틴): 202 → `retryAfterSeconds`(최소 2초, 최대 6초) 대기 후 재호출, **최대 8회**(약 30초) 후 "아직 준비 중이에요. 잠시 후 다시 열어 주세요" 안내. `stale: true` 인 200 은 즉시 표시하고 "업데이트 중…" 작은 표시 + 한 번 더 재조회.
3. **화면** `feature/insight/GroupInsightScreen.kt` (신규 feature 폴더): 상단 `HNavBar("우리 그룹 이야기")` + 월 선택(이전/다음 달 화살표, 미래 달 비활성, 최대 12개월 전). 섹션(임시 UI):
   - **활동 지수**: 큰 숫자(score) + 원형 게이지(`Canvas` arc) + 전월 대비 `+5`/`-3`(양수 초록 아님 — 색 토큰 `accent`/`textSecondary` 사용, 감소를 붉게 표시하지 않음), 아래 보조 지표 3개(참여/정시/교류 %) 작은 막대.
   - **가장 빨리 낸 사람**: 아바타+닉네임+"○번 가장 먼저 냈어요", 평균 응답 시간 문구("보통 ○분 안에").
   - **많이 쓰인 단어**: 칩(Flow 레이아웃 또는 `FlowRow`) — 빈도에 비례한 글자 크기(3단계)와 "N번" 표기, 탭 시 아무 동작 없음.
   - **가장 반응이 많았던 글**: 카드(미션 문장, 작성자, 반응 N · 댓글 N, 대표 스티커 이모지 `Sticker.fromKey`) — 탭 → `Results(groupId, missionId)` 이동.
   - **하이라이트**: 문장 목록(불릿).
   - `empty` → 안내 "이 달에는 아직 쌓인 기록이 없어요", 섹션이 null/빈 배열이면 해당 섹션 숨김.
   - 로딩: 임시 스켈레톤(회색 블록) 또는 "우리 이야기를 정리하고 있어요…" 문구. 실패: "불러오지 못했어요" + 다시 시도.
4. **진입점**: `GroupInfoScreen` 의 설정 영역 첫 줄에 `HListRow("우리 그룹 이야기", …)` 추가(알림 행 위), 라우트 `MainRoute.GroupInsight(groupId)`.
5. **임시 이미지/아이콘**: 섹션 헤더용 임시 아이콘 `ic_insight_temp.xml`(막대그래프 모양 단순 벡터, 첫 줄 주석 `<!-- 임시 이미지 -->`), `HIcon.INSIGHT_TEMP`. 나머지는 기호/도형.
6. **데이터 표시 원칙**: 서버가 내려준 값을 그대로 표시(재계산 금지). 단어는 서버 제공만 사용(앱이 글을 가공하지 않음).

## Files / Functions to Change

| 파일 | 변경 |
|---|---|
| `core/model/GroupInsight.kt` (신규) | 모델 |
| `core/net/InsightApi.kt`, `InsightRepository.kt`, `InsightLoader.kt` (신규) | DTO·로드·재시도 |
| `app/AppViewModel.kt`, `app/MainNavigation.kt` | 레포지토리 생성·전달, `MainRoute.GroupInsight(groupId)` 라우트·목적지, 반응 글 탭 → `Results` 이동 |
| `feature/insight/GroupInsightScreen.kt`, `InsightSections.kt`, `InsightFormat.kt` (신규) | 화면·섹션·문구/순수 계산(월 이동 범위, 단어 크기 단계, 시간 문구) |
| `feature/groups/GroupInfoScreen.kt` | 진입 행 |
| `designsystem/assets/HIcon.kt`, `res/drawable/ic_insight_temp.xml` | 임시 아이콘 |
| 테스트 | `InsightRepositoryTest`(MockWebServer: 200 전체/부분 null/empty/202/403/404/400 매핑), `InsightLoaderTest`(재시도 횟수·대기·stale 처리 — 코루틴 테스트로 가상 시간), `InsightFormatTest`(월 범위, 단어 크기 단계, 시간 문구, 전월 대비 문구) |

## Implementation Steps

1. 모델/DTO/레포지토리 + 테스트. 2. `InsightLoader` 재시도 + 가상 시간 테스트. 3. 화면 섹션 구현. 4. 진입 행·라우트·`Results` 연결. 5. `android\gradlew.bat :app:testDebugUnitTest --offline`. 6. 서버 연동(제출·반응이 쌓인 그룹)으로 수동 확인은 사용자.

## Constraints

- `android/` 만. 서버 응답에 없는 값을 만들어 내지 않는다. 낮은 활동/감소를 비난하거나 붉은 경고로 표시하지 않는다. 사용자 본문 텍스트를 이 화면에 표시하지 않는다(서버가 주지 않음). 새 라이브러리 금지(차트 라이브러리 포함). 커밋/푸시 금지.

## Edge Cases

- 202 가 길게 이어짐 → 안내 후 종료(무한 재시도 금지). 화면 이탈 시 진행 중 재시도 취소(`LaunchedEffect` 키).
- `empty:true`, `fastest == null`, `topWords == []`, `mostReacted == null`, `highlights == []` 조합별 레이아웃.
- 월 이동 중 이전 요청 응답이 늦게 도착해 현재 월을 덮어쓰는 경합 → 요청 월을 키로 취소/무시.
- 탈퇴한 멤버(닉네임이 "탈퇴한 멤버"로 올 수 있음) 표시. `profileImageUrl` 원격 사진.
- 미래 달/12개월 초과 이동 차단(서버 400 이전에 앱에서 막음).
- 그룹을 나갔다가 403/404 → 홈으로 복귀(기존 `GroupInfoScreen` 처리 패턴).
- 반응 글 탭 시 결과 화면에서 423(내가 안 낸 미션) → 기존 `onLocked` 가 이전 화면(인사이트)으로 돌아와 "먼저 미션을 올려 주세요" 토스트 — 이 흐름이 자연스러운지 확인.

## Regression Risks

- `MainRoute` 추가(직렬화 누락 → 내비게이션 크래시), `GroupInfoScreen` 행 추가로 기존 항목 순서/구분선 영향.
- `ApiClient` 가 202 를 정상 응답으로 처리하는지 확인(`in 200..299`) — 확인만, 변경 불필요.
- 큰 화면 스크롤 성능은 데이터가 작아 문제 없음.

## Tests Required

위 표. 화면은 컴파일 + 수동 확인.

## Validation Steps

`android\gradlew.bat :app:testDebugUnitTest --offline`(JDK 21) 통과 기록, 수동 체크리스트(첫 진입 202→200, 월 이동, 빈 달, 반응 글 탭 이동/잠금) 보고서에 포함, 못 했으면 "미검증".

## Potential Merge Conflicts

`MainNavigation.kt`, `AppViewModel.kt`, `GroupInfoScreen.kt`(A3 와 겹침 — A3 이후), `HIcon.kt`.
