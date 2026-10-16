# 개발자 모드 미션 리롤 — 리뷰

대상: 미커밋 변경 16개 파일(서버 `MissionPublisher.reroll`, `MissionMapper.updateGenerated`, `DevMapper` 미션 단위 삭제 7개, `DevMissionService.reroll`, `DevController`, `DevDtos.RerollRequest` / Android `DevApi`·`DevRepository`·`DataStore`·`MissionScreen`·`DevToolsScreen` / 테스트), `.agent/report.md`. 이전 작업의 미커밋 변경과 같은 worktree 에 있으나 이번 범위만 확인했다.

## 요구사항 충족 확인
- **제자리 갱신**: `uk_missions_group_date_slot` 때문에 새 행을 넣을 수 없는 점을 반영해 `UPDATE missions SET content/category/template/params/generation_source/experiment_arm` 만 바꾼다. id·날짜·슬롯·`published_at`·`deadline_at` 유지(`MissionMapper.updateGenerated`).
- **다양성**: 시드 = 결정적 시드 `^ ThreadLocalRandom.nextLong()`, 틀은 현재 틀을 뺀 풀에서 무작위(풀이 1종이면 전체 풀로 폴백), 지정 틀은 그대로 사용. 대상(`needsTarget`/`targetUserId`)·최근 문장 회피·실험 군은 `publish` 와 같은 규칙. params 는 통째로 교체되어 이전 틀 값이 남지 않는다.
- **검증 순서**: 틀 이름 검증 → 현재 미션 확인(`findById`/`isCurrent`) → 삭제 → 갱신. 잘못된 입력/없는 미션이면 아무것도 지우지 않는다.
- **제출물 정리**: 자식→부모 순서(`comment_mentions`→`comments`→`reactions`→`submission_guesses`→`submission_ai_reviews`→`submissions`→`pokes`)로 FK 위반 없음. `DevMissionService.reroll` `@Transactional` 에 `MissionPublisher.reroll`(기본 전파) 이 합류해 삭제와 갱신이 한 트랜잭션. 보고서가 계획의 `REQUIRES_NEW` 와 "한 트랜잭션" 요구가 모순임을 짚고 후자를 택한 것은 올바른 판단이다.
- **앱**: 미션 화면 `[개발자] 미션 리롤`(`dev != null`일 때만, `HAlert` 확인, 진행 중 중복 방지, `CancellationException` 재던짐), 개발자 도구의 무작위/틀 지정 리롤(`HAlert` 확인, 결과 로그, 제출 목록 선택 초기화). 리롤 직후 `clearMissionForReroll` 로 로컬 제출·콕·멤버·피드 상태와 요약을 비우고 새로고침.
- **id 가 같은 채 내용이 바뀌는 경우의 캐시**: `applyCurrentMission` 이 topic/template/spec 이 달라지면 이전 상태(`submissions`·`poked`·`serverSubmittedByMe`·`feedLoaded`)를 승계하지 않도록 바꿨고, `summaries` 도 서버 값으로 갱신한다. 이 변경이 없으면 리롤 후에도 "제출함"이 남을 수 있어 필요한 수정이다. 일반 새로고침(내용이 같을 때)은 이전과 동일하게 승계된다.
- **보고된 검증**: `mvnw clean test`(MariaDB), 대상 테스트, `package`, `testDebugUnitTest`+`assembleDebug`, `lintDebug`, `git diff --check` 통과. 실제 MariaDB 스모크: id 유지, 틀 지정·무작위 리롤, 제출 1건→0건·`submittedByMe=false`, 잘못된 틀 400, 없는 미션 404, 테스트 그룹 정리.

## P2
### File / Location
`src/main/java/com/sapphire/hawe/mission/service/MissionPublisher.java` `reroll(Long, LocalDateTime now, MissionTemplate)`
### Problem
`now` 인자를 쓰지 않는다.
### Why It Matters
기능상 문제는 없다(시각을 바꾸지 않는 설계). 읽는 사람에게 약간의 혼란만 준다.
### Recommended Fix
선택: 인자를 제거하거나 그대로 둔다(계획 시그니처를 따른 것).

### File / Location
기기 확인 (보고서 "Device UI behavior was not manually checked") 및 Codex 가 띄워 둔 테스트 서버
### Problem
(1) 폰 UI 에서 리롤 → 카드/제출 상태 갱신이 확인되지 않았다. (2) 보고서에 따르면 Codex 의 스모크용 백엔드가 **포트 8080 에서 계속 실행 중**이다(`adb reverse` 는 건드리지 않음).
### Why It Matters
사용자가 별도로 서버를 띄우려 하면 포트 충돌이 난다. 반대로 폰에서 바로 쓰려면 이 서버가 최신 코드(리롤 포함)로 떠 있어야 한다.
### Recommended Fix
(코드 수정 아님) 사용자가 서버를 직접 띄울 때는 기존 8080 프로세스를 종료하고 새로 시작. 앱은 새 debug APK 를 다시 설치해야 리롤 버튼이 보인다. 체크리스트: 개발자 모드 → 그룹 → 미션 → `[개발자] 미션 리롤` → 확인 → 문구·틀 변경, 제출 표시 초기화, 참여 현황 초기화 / 개발자 도구에서 틀 지정 리롤.

## 확인했지만 문제 없음
- 마감 지난 미션 리롤 시 내용만 바뀌고 마감 유지: 계획된 동작이며 보고서에 명시됨.
- 업로드 파일 고아 상태: 계획대로 허용, 보고서에 명시됨.
- `applyCurrentMission` 변경이 A6 다중 미션 흐름(`StoredGroup.summaries`, `CurrentMissionState`)에 미치는 영향: id 가 같고 내용도 같을 때의 동작은 이전과 동일하고, `DataStoreTest` 에 케이스가 추가되었다.
- 운영 경로(`publishIfDue`, 스케줄러, 일반 미션 API) 변경 없음. `publish` 코드를 헬퍼로 뽑지 않고 복제해 기존 동작·테스트 안정성 유지(계획이 허용).

결론: **승인 가능** (기기 확인 전에 서버 재시작/포트 확인과 새 APK 설치 필요)
