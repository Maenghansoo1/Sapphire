# Review — 계약 반영 1차 / 4단계(백엔드): 미션 생성 · 현재 미션 · 마감/종료 규칙 (+ 3A P1 수정 재확인)

대상: 4단계 미커밋 변경(`mission/**` 신규·수정, `group/**` 수정, `poke/**`, `HaweApplication`, `application.properties`, 테스트, 문서, Postman) + `.agent/report.md`. 이전 단계(1·2·3, 2A·3A)는 이미 리뷰했으므로 이번에는 4단계 변경과 3A P1 수정만 확인했다.
검증 근거: `MissionPublisher`·`MissionScheduler`·`MissionPublishListener`·`MissionTiming`·`MissionTemplateSelector` 전문, `MissionMapper`·`MissionService`·`GroupMemberMapper`·`PokeMapper` diff, `GroupService` 관련 줄, 통합 테스트(`FirstMissionPublishTests`)를 읽었다. Maven 테스트와 실서버 스모크는 다시 실행하지 않았고 보고서(`clean test` 92개 통과, 스모크: 슬롯 00:00 그룹 생성 직후 미션 1개·`+09:00`·`nextPublishAt == closesAt`·목록 `todayMission` 일치, `dailyMissionCount=2` → 400)를 신뢰한다.

## 3A P1 수정 확인 (Android)

`GroupRepository.create` 가 `POST /api/groups` 한 번만 호출하고, 응답 상세 + 호출자가 넘긴 내 프로필로 방장 1명짜리 `GroupDetail` 을 만든다(`CreateGroupScreen` 이 `session.account.profile` 전달). 생성 직후 멤버 조회가 사라져 "서버에는 생성됐는데 화면은 실패" 시나리오가 없어졌다. **해결됨.**

## 요구사항 충족 확인

| 요구 | 결과 |
|---|---|
| 멱등한 발행 | `publishIfDue`(`REQUIRES_NEW`)가 슬롯 `1..dailyMissionCount` 마다 `now < 예정 시각` 이면 건너뛰고, `(group,date,slot)` 존재 시 건너뛰고, `DuplicateKeyException` 은 이미 발행으로 무시. 과거 날짜 소급 없음 |
| `publishedAt` | `max(예정 시각, group.createdAt)` — 다운 후 따라잡기는 예정 시각, 늦게 만든 그룹은 생성 시각 |
| `deadlineAt` / `closesAt` | `MissionTiming.deadlineAfter`(발행 후 처음 오는 마감, 동일 순간이면 다음 날), `nextPublishAfter` — 순수 함수, 경계 동일 순간 처리 일치 |
| 틀 선택 | MVP 8종·4매체, `seed` 는 `Long` 산술(JVM 안정), `dailyMissionCount==1` 이면 어제 매체 제외 후 첫 후보·모두 겹치면 `mediaOrder[0]`, ≥2 면 슬롯별 매체 분배, 대상 지정(FILL_BLANK 항상, DRAW 0.3 seed 결정) |
| 임시 생성기 | `MissionGenerator` 인터페이스 + `PlaceholderMissionGenerator`(변형·직전 문장 회피, `targetUserId` 는 발행기가 추가) |
| 첫 미션 즉시 발행 | `GroupService.createGroup` 이 `GroupCreatedEvent` 를 발행 → `MissionPublishListener` 가 `AFTER_COMMIT` 에서 발행, 예외는 로그만(그룹 생성 유지). non-@Transactional 통합 테스트가 커밋 후 발행을 검증 |
| 스케줄러 | 분 단위 cron(`Asia/Seoul`), 그룹별 `try/catch` 로 한 그룹 실패가 다른 그룹을 막지 않음, `@EnableScheduling` 추가 |
| 현재 미션 기준 | `findCurrentByGroupId`(슬롯별 `published_at DESC, id DESC` 최신), `stamped_today`·`existsCurrentMissionSubmission` 도 같은 패턴. `number` 서브쿼리(`<script>` 안에서 `&lt;` 이스케이프 올바름) |
| 응답 확장 | `number`, `closesAt`, `members`, `[대상]` 치환(대상 없음 → "친구"), 그룹 목록 `todayMission`(+ `submittedToday` 를 이것에서 유도), `GroupSummaryRow.submittedToday` 제거 |
| 설정 | `hawe.mission.max-daily-count=1`, `MissionProperties` 시그니처 불변 |
| 스키마 | 변경 없음(DB 초기화 불필요) |
| 범위 밖 금지 | AI 호출, `MISSION_CLOSED`, 알림, `pokedByMe`, 제출/피드, 클라이언트 변경 없음 |

**계획 밖 변경 하나(타당)**: `@MapperScan("com.sapphire.hawe")` 를 `annotationClass = Mapper.class` 로 제한했다. 기존 설정은 패키지의 **모든 인터페이스**를 매퍼로 등록하는데, 새 `MissionGenerator` 인터페이스가 매퍼 빈으로 잡히면 `PlaceholderMissionGenerator` 와 주입이 충돌한다. 모든 DAO 인터페이스에 `@Mapper` 가 붙어 있어 부작용이 없고 보고서에도 명시됨.

로직 결함(경쟁 조건, 시간 경계, 쿼리 의미, 트랜잭션 경계)은 찾지 못했다.

## P0

없음.

## P1

없음.

## P2

### `mission/service/MissionService.java` `nextPublishAt` — 현재 미션이 있는데 다음 발행 시각이 이미 지난 경우 과거 시각이 내려감
#### Problem
현재 미션이 있으면 `nextPublishAfter(current.getPublishedAt(), time)` 을 쓴다. 스케줄러가 지연되거나 생성이 실패해 아직 새 미션이 열리지 않은 상태(예: 어제 09:00 발행 미션, 지금 오늘 09:00 이후인데 발행 전)에서는 이 값이 **지금보다 과거**(오늘 09:00)다. `closesAt` 도 같은 이유로 과거일 수 있다.
#### Why It Matters
앱이 "다음 미션까지 N시간"을 `nextPublishAt - now` 로 계산하면 음수가 되어 표시가 어긋날 수 있다. 정상 운영에서는 스케줄러가 1분 안에 발행하므로 일시적이다.
#### Recommended Fix
(선택) `nextPublishAt` 만 `max(계산값, now)` 로 보정하거나, 앱(4A)에서 음수면 "곧 열려요"로 처리. `closesAt` 은 설계상 "예정값"이라 그대로 두어도 된다.

### 참고 (조치 불필요)
- **분당 전체 그룹 순회**: 보고서가 알려진 한계로 기록(그룹 수가 커지면 개선 필요). MVP 범위에서는 허용.
- **미래 tick 미관찰**: 보고서대로 "발행 시각이 미래인 그룹이 실제로 분 단위 tick 에서 열리는 것"은 실서버에서 지켜보지 못했다. 단위 테스트(`MissionPublisherTests`, 시각 전/후/이미 존재)와 즉시 발행 통합 테스트로 갈음했다. 필요하면 슬롯 `publishTime` 을 "현재+2분"으로 그룹을 만들어 한 번 확인하면 된다.
- **Postman 미실행**: 두 컬렉션 JSON 은 파싱·한글 확인됐으나 `newman` 이 없어 실행하지 않았다. 생성 시각에 따라 미션이 이미 열려 있을 수 있어 시간 의존 기대값으로 바꿨다고 보고됨.
- **템플릿 수**: 계획서가 `/api/mission-templates` 를 15종으로 적었으나 실제 enum 은 16종(보고서가 지적). 문서는 코드에 맞춰졌고 영향 없음.
- **생성 응답 `missionCount` 0 가능**: 발행이 커밋 이후라 의도된 동작. 16 화면 진입 시 상세 조회로 갱신된다(문서 명시).
- 1~4단계 백엔드와 2A·3A Android 변경이 한 작업 트리에 미커밋으로 쌓였다(`git log` 상 `360b3d0` 이후 커밋이 거의 없음). **5단계/4A/6단계 시작 전에 단계별로 나눠 커밋**하는 것을 권장.

## 결론

**승인 가능.** 코드·테스트·문서가 계획과 계약(§3 1차 범위)을 충족하고 결함은 없다. 3A 의 P1 도 해결되었다. P2 는 선택이며 Codex 에게 보낼 필수 수정은 없다.
