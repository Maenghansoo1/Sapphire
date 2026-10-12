# B7. 하루 여러 개 미션 개방 (백엔드)

> 범위: 백엔드만. 앱(홈 카드·05 화면 다중 미션)은 A6. 알림 무관. **전제: B5·B6 커밋 후**(발행 풀·선택기·생성기 시그니처를 바꾸는 계획들이 먼저 끝나야 한다). 근거: `MISSIONS_API.md` §2 "그룹 설정 연동", §6·§6-1(틀 선택), §11-8,9,10,11,13, `CONTRACT_DECISIONS.md` §3-4(하루 여러 개 모양은 MVP 이후), §9.
>
> **가정(README 결정 표 기본값 — 다르게 정하면 알려 달라)**: 상한 5개(`max-daily-count`), 슬롯 간 최소 간격 없음, **도장은 미션별(현재 달력 구현 유지)**, 피드 잠금은 미션별 유지, `submissionCountThisMonth` 는 "제출한 날 수".

## Goal

그룹이 하루 미션 수를 2~5개로 설정할 수 있고, 발행·선택·조회·콕·집계가 여러 미션에서 일관되게 동작한다. (지금은 `hawe.mission.max-daily-count=1` 이라 설정 단계에서 막혀 있다. 코드는 이미 슬롯 단위로 동작하도록 만들어져 있어 **검증·연결 빠진 부분을 메우고 상한을 연다**.)

## Findings

- 게이트: `application.properties` `hawe.mission.max-daily-count=1`. `GroupService.validateSettings` 는 `dailyMissionCount ∈ [1, maxDailyCount]`, 슬롯 번호 `1..dailyMissionCount`, 시각 `HH:mm` 검증. `PATCH /groups/{id}/settings` 는 `dailyMissionCount` 감소 시 초과 슬롯 행을 지운다.
- 발행: `MissionPublisher.publishIfDue` 가 슬롯 `1..dailyMissionCount` 를 순회, 슬롯별 발행/마감 시각(`group_mission_slots`, 없으면 기본값), `(group_id, mission_date, slot_no)` UNIQUE 로 멱등. 마감 = `MissionTiming.deadlineAfter(publishedAt, deadlineTime)`.
- 선택기: `MissionTemplateSelector.select(groupId, date, slotNo, dailyMissionCount, yesterdayMedia)` — N≥2 이면 `mediaOrder[(slotNo-1) % mediaCount]`, 틀은 `seed(groupId,date,slotNo)` 로 매체 풀에서 고름(→ 슬롯 수가 매체 수보다 많으면 **같은 매체가 다시 나오고 같은 틀이 중복 선택될 수 있다**). B5 이후 풀은 설정(`enabled-templates`)에서 오고 매체 순서는 풀의 매체에서 도출.
- 생성기: `MissionGenerator.generate(template, seed, String previousContent, boolean withTarget)` — 직전 **한 개** 문장만 피한다(`missions.findLatestContentByGroupId`). MISSIONS_API §6 는 "최근 미션 및 **같은 날 먼저 열린 슬롯**"과 같은/거의 같은 문장을 피하라고 규정.
- 현재 미션 판정: `MissionMapper.CURRENT` = 슬롯별 최신 발행 미션. `GET /groups/{id}/missions/today` 는 `missions[]` 배열(슬롯 순)로 이미 다중 응답. 그룹 목록 `todayMission` 은 `findCurrentSummariesByGroupIds` 가 **가장 작은 `slot_no` 의 미션 하나만** 내려준다(`AND m.slot_no = (SELECT MIN(slot_no) …)`). 콕은 `PokeMapper.findCurrentMissionId` 가 같은 MIN 슬롯 기준(요청에 `missionId` 없음).
- 멤버 목록: `GroupMemberMapper.findMembersByGroupId` 의 `submission_count_this_month` 는 이번 달 **제출 행 수**, `stamped_today` 는 슬롯별 현재 미션 중 하나라도 제출했는지(EXISTS).
- 달력: `CalendarMapper.findMissionsInMonth` 는 미션 단위 행(`ORDER BY mission_date, slot_no`)이고 도장은 **미션별** `Stamp.of(submitted, late, closed, memberWhenPublished)`. → 하루 여러 개일 때 도장도 미션별로 이미 계산된다(= "미션별 도장" 기준이 이미 구현돼 있음).
- 피드 잠금/제출/반응 등은 `missionId` 기준이라 다중 미션에서도 동작.
- 스케줄러 비용: `MissionScheduler` 가 매분 전 그룹 순회(그룹 수 증가 시 개선 필요 — 이번 범위 아님).

## Root Cause / Key Implementation Points

1. **상한 개방**: `max-daily-count=5`. 슬롯 시각 규칙은 새로 제한하지 않는다(모든 슬롯이 같은 시각이면 같은 시각에 N개가 열림 — 허용; 마감이 발행보다 이르거나 자정을 넘기는 설정도 기존 `MissionTiming` 규칙대로 허용).
2. **같은 날 틀 중복 방지(결정적)**: 선택기에서 슬롯 k(≥2 일 때)의 매체/틀을 다음으로 정한다 — `M = 풀에 존재하는 매체 수`, `mediaOrder = shuffle(매체, seed(groupId,date,0))`, 매체 = `mediaOrder[(k-1) % M]`, 그 매체의 풀 틀 리스트 `L`(고정 순서) 에서 시작 인덱스 `s = new Random(seed(groupId,date,0)).nextInt(L.size())` 를 매체별로 고정하고 **틀 = `L[(s + (k-1)/M) % L.size()]`** (같은 매체가 두 번째로 나오면 다음 틀). → 같은 날 같은 틀은 `L.size()` 가 1이 아니면 중복되지 않는다. N==1 의 기존 규칙(어제 매체 제외, 시드로 틀 선택)은 **결과가 바뀌지 않아야 한다**(N==1 경로 불변 — 회귀 테스트 고정). 다른 슬롯의 DB 상태를 읽지 않는다(경쟁 없음 유지).
3. **연속 방지 확장**: `MissionGenerator.generate(template, seed, Collection<String> avoidContents, boolean withTarget)` 로 시그니처 변경(`previousContent: String` → 회피 문장 집합). `MissionPublisher` 가 `최근 K개(K=5) 미션 문장 + 같은 날 이미 저장된 슬롯 문장` 을 모아 전달(`MissionMapper` 에 `findRecentContentsByGroupId(groupId, limit)` 추가; 같은 날 미션은 그 쿼리에 포함되면 충분 — 같은 트랜잭션에서 앞 슬롯 insert 후 읽히도록 슬롯 순서대로 처리). `PlaceholderMissionGenerator` 는 집합에 없는 변형을 우선 선택, 모두 있으면 시드 변형 그대로(예외 던지지 않음 — 현재는 `IllegalStateException`; 다중 슬롯에서는 변형이 부족할 수 있으므로 **완화**). **B8(AI)이 이 시그니처를 그대로 쓴다.**
4. **그룹 목록 `todayMission(s)`**: 응답에 `todayMissions: [TodayMissionSummary…]`(슬롯 순, 현재 미션 전부) 추가, 기존 `todayMission`(가장 작은 슬롯 하나)과 `submittedToday` 는 **그대로 유지**(앱 호환). `findCurrentSummariesByGroupIds` 에서 MIN 슬롯 제한을 제거한 쿼리를 새로 두거나 기존 쿼리를 확장해 `Map<Long, List<TodayMissionSummary>>` 로 반환하고 서비스에서 첫 항목을 `todayMission` 에 채운다. `submittedToday` 정의는 기존(= `todayMission.submittedByMe`) 유지 — 문서에 "하루 여러 개면 첫 슬롯 기준, 전체는 `todayMissions`" 명시.
5. **콕 대상 미션**: `PokeRequest` 에 **선택 필드** `missionId` 추가. 없으면 기존(가장 작은 슬롯의 현재 미션). 있으면 그 미션이 **그 그룹의 현재 미션(슬롯별 최신)** 이어야 함(아니면 409 `NO_OPEN_MISSION` 재사용). 응답 `PokeResponse.missionId` 는 이미 있음. 종류별 1회 제한은 `(보낸 사람, 받는 사람, 미션, 종류)` 라 미션이 다르면 따로 가능.
6. **멤버 목록 지표**: `submissionCountThisMonth` = 이번 달 **서로 다른 미션 날짜 수**(`COUNT(DISTINCT m.mission_date)`)로 변경. `stampedToday` 는 "하나라도"(기존 EXISTS) 유지. 둘 다 앱이 쓰지 않으므로 호환 영향 작음 — 문서에 의미 명시.
7. **문서의 미정 항목 정리**: 도장(미션별), 잠금(미션별), 상한 5, 간격 없음, 마감 규칙을 `MISSIONS_API.md` §11 에서 "결정됨" 으로 옮긴다.

## Files / Functions to Change

| 파일 | 변경 |
|---|---|
| `application.properties` | `hawe.mission.max-daily-count=5` |
| `mission/domain/MissionTemplateSelector.java` | 슬롯별 매체·틀 결정 규칙(Key Point 2), N==1 경로 불변 |
| `mission/service/MissionGenerator.java`, `PlaceholderMissionGenerator.java`, `MissionPublisher.java` | `avoidContents` 시그니처(Key Point 3), 예외 완화, 최근 문장 수집 |
| `mission/dao/MissionMapper.java` | `findRecentContentsByGroupId`, `findCurrentSummariesByGroupIds` 확장(또는 신규) |
| `mission/service/MissionService.java`, `group/service/GroupService.java`, `group/dto/GroupSummaryResponse.java` | `todayMissions` 추가 |
| `poke/dto/PokeRequest.java`, `poke/service/PokeService.java`, `poke/dao/PokeMapper.java` | 선택 `missionId` |
| `group/dao/GroupMemberMapper.java` | `submission_count_this_month` → distinct 날짜 수 |
| 테스트 | 아래 |
| 문서 | `MISSIONS_API.md` §2·§6-1·§11, `CONTRACT_DECISIONS.md` §3·§4(콕 `missionId`)·§2(`todayMissions`), `API_DESIGN.md` §2·§5, `PROJECT_OVERVIEW.md` §5(그룹 설정)·§11(해결된 항목 취소선), Postman `HAWE_GROUP`/`HAWE_MISSION`/`HAWE_POKE` |

## Implementation Steps

1. `MissionGenerator` 시그니처 + 최근 문장 수집(기본 설정 N=1 동작 불변 확인). 2. 선택기 규칙 변경(N==1 불변 테스트 먼저). 3. 상한 5. 4. `todayMissions`·콕 `missionId`·멤버 지표. 5. 테스트 → `.\mvnw.cmd clean test -q`. 6. 문서·Postman. (스키마 변경 없음 — DB 재생성 불필요.)

## Constraints

- 스키마 변경 없음. N==1 동작·응답 불변(기존 필드 제거/이름 변경 금지, `todayMissions` 는 추가만). 슬롯 간 DB 조회 의존(경쟁 조건) 도입 금지. 도장 계산·피드 잠금 단위 변경 금지. 알림 없음. 2차 틀(R) 금지. 커밋/푸시·지침 파일 수정 금지.

## Edge Cases

- N=5, 매체 4종: 슬롯 5가 슬롯 1과 같은 매체지만 **다른 틀**(매체에 틀이 2개 이상일 때). 매체에 틀이 1개뿐이면 중복 불가피 — 허용(테스트로 명시).
- 풀에 매체가 1개뿐인 설정: 모든 슬롯이 그 매체, 틀이 순환.
- 같은 날 슬롯 시각이 모두 같음 → 한 번의 `publishIfDue` 에서 N개 발행, 문장 중복 없음, 대상 지정 틀의 대상이 슬롯마다 독립(시드가 슬롯 포함).
- 슬롯 3개 중 슬롯 2만 아직 발행 전(시각 미도래) → `missions[]` 에 2개, 슬롯 2는 시각 후에 추가.
- 하루 미션 수를 3→1 로 줄인 날: 이미 발행된 슬롯 2·3 미션은 그날 계속 존재(다음 발행부터 반영) — 현재 규칙 유지, `todayMissions` 에 그대로 나옴.
- 같은 미션에 대한 콕과 다른 슬롯 미션에 대한 콕은 독립. 그룹의 현재 미션이 아닌 `missionId`(닫힌 이전 미션/남의 그룹) → 409 `NO_OPEN_MISSION`.
- `todayMissions` 정렬 = `slot_no` 오름차순, 미션 없는 그룹은 빈 배열(기존 `todayMission` 은 null).
- 지난 슬롯의 다음 미션 발행으로 이전 미션 제출은 `MISSION_CLOSED`(슬롯 단위, 기존 규칙).

## Regression Risks

- `MissionGenerator` 시그니처 변경 → `PlaceholderMissionGenerator`, `MissionPublisher`, 관련 테스트, (B8 이전이므로 다른 구현 없음).
- 선택기 변경이 **N==1 의 기존 결정 결과**를 바꾸면 이미 발행된 그룹의 어제 매체 제외 규칙이 흔들린다 → 기존 시드 기대값 테스트 유지.
- `GroupSummaryResponse` 필드 추가 → 직접 생성하는 테스트 수정; 앱은 `ignoreUnknownKeys` 라 안전.
- 멤버 목록 쿼리 변경은 `GroupMemberMapperTests` 기대값(제출 행 수) 수정 필요.
- `findCurrentSummariesByGroupIds` 확장이 홈 목록 성능에 영향(그룹당 행 수 증가: 최대 5) — IN 쿼리 1회 유지.

## Tests Required

- `MissionTemplateSelectorTests`: N==1 결과 불변(고정 시드 몇 개), N=2~5 에서 같은 날 (매체,틀) 중복 없음(틀 ≥2 매체), 풀 1매체, 결정성(같은 입력 같은 출력).
- `PlaceholderMissionGeneratorTests`: 회피 집합 반영, 모두 소진 시 예외 없이 시드 변형.
- `MissionPublisherTests`/통합: N=3, 같은 시각 슬롯 → 3개 발행·문장 서로 다름·재호출 멱등(중복 발행 없음), 최근 문장 회피.
- `GroupServiceTests`/MockMvc: `max-daily-count=5` 에서 설정 PATCH 2~5 허용·6 거절, `todayMissions` 배열(슬롯 순)과 기존 `todayMission` 호환, 슬롯 수 감소.
- `PokeServiceTests`/MockMvc: `missionId` 지정(현재 미션/이전 미션/남의 그룹), 기본값 동작 불변.
- `GroupMemberMapperTests`: 같은 날 2개 제출 → `submissionCountThisMonth` 1, 이틀 → 2.
- 달력: 하루 2개 미션에서 도장이 미션별로 나오는지(기존 `CalendarServiceTests` 확장).

## Validation Steps

`.\mvnw.cmd clean test -q` 통과 기록(N==1 회귀 포함), Postman 파싱·한글, `git diff --check`, 서버 스모크(그룹 `dailyMissionCount=3` 설정→같은 시각 슬롯→`/missions/today` 3개) 가능 시 수행, 아니면 "미검증". 보고서에 `max-daily-count` 변경과 앱 영향(A6) 명시.

## Potential Merge Conflicts

`mission/*`(B5·B6·B8 과 겹침 — 순차), `GroupService`/`GroupSummaryResponse`, `PokeService`, `GroupMemberMapper`, `application.properties`, `docs/BACKEND/MISSIONS_API.md`.
