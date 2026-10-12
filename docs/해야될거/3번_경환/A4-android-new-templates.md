# A4. Android — 새 미션 틀 화면 (별점 후기·한 단어·밸런스·슬라이더 3종) + 집계 결과

> 범위: **Android 앱만**(`android/`). **전제: A0(음성 제거) 이후, 백엔드 B0·B5(단순 글·집계 틀 + 결과 API)가 커밋된 뒤.** (정답 맞히기 틀 `VOICE_MIMIC`/`VOICE_IMPRESSION` 은 음성 제거로 **폐기**되어 이 계획에서 빠졌다 — B6 참고.) 서버는 새 틀을 `hawe.mission.enabled-templates` 로 켜기 전까지 발행하지 않으므로 **이 앱 작업이 끝나고 배포된 뒤에 서버 설정으로 켠다**(순서 중요: 앱 먼저). iOS 는 A5. 구현자: Claude 또는 Codex. 디자인이 없는 화면(슬라이더, 별점, A/B 카드 등)은 **기존 디자인 시스템 컴포넌트 + 임시 이미지/텍스트 기호**로 만든다.
> 빌드/테스트: JDK 21 — `set JAVA_HOME=C:\Users\kimch\.jdks\ms-21.0.10` 후 `android\gradlew.bat :app:testDebugUnitTest --offline`.

## Goal

앱이 다음 **6개 틀**을 **제출·열람**할 수 있다: `TEXT_ONE_WORD`, `TEXT_REVIEW`, `TEXT_BALANCE`, `SLIDER_POSITION`, `SLIDER_TASTE`, `SLIDER_CONSENSUS`. 집계 틀(밸런스·슬라이더 3종)은 그룹 결과(`GET /missions/{id}/result`)를 보여 준다.

## Findings

- 서버 계약(`MISSIONS_API.md` §1-2, §3, §5; B0·B5 계획 반영):
  - 매체(`media`): `PHOTO`/`TEXT`/`DRAW`/**`SLIDER`**(새). (`VOICE` 는 제거됨.) 틀별 `params`: `TEXT_BALANCE{optionA,optionB}`, `SLIDER_POSITION{question,left,right}`, `SLIDER_TASTE{topic,left,right}`, `SLIDER_CONSENSUS{question,left,right}`, `TEXT_ONE_WORD`/`TEXT_REVIEW` 없음.
  - 제출 필드(multipart): `TEXT_ONE_WORD` `text`(≤20자)+선택 `caption`(≤100); `TEXT_REVIEW` `number`(1~5)+`text`(≤100); `TEXT_BALANCE` `choice`(`A`/`B`)+선택 `caption`(≤200); `SLIDER_POSITION/TASTE` `number`(0~100); `SLIDER_CONSENSUS` `number`+`guess`(0~100). 요구하지 않는 필드는 400.
  - 응답 `SubmissionResponse` 새 필드(해당 틀에서만): `number`, `choice`, `guess`, `caption`. (정답 맞히기용 `answer`/`myGuess` 는 없음.)
  - 결과 `GET /api/missions/{id}/result`(제출한 사람만, 423 `FEED_LOCKED`; V 가 아닌 틀 400 `RESULT_NOT_SUPPORTED`): 밸런스 `{totalCount, choices[{choice,count}], myChoice}`; 슬라이더 `{totalCount, average, distribution[{userId,nickname,number}], myNumber, diffFromAverage}`; 다수결 예측 `{totalCount, average, ranking[{userId,nickname,guess,diff}], winnerUserIds[]}`.
- 앱 현재 상태 (A0 이후 3종 매체 전제):
  - `MissionType`(`core/model/MissionType.kt`) = `DRAW/PHOTO/WRITE` 3개(A0 에서 `VOICE` 삭제), 앱 전반에 **exhaustive `when` 다수**(결과·달력·마이페이지 통계·05·홈 등) → 새 값 추가는 컴파일 오류로 누락을 잡을 수 있다.
  - `mediaToType(media)`(`core/net/MissionRepository.kt`)는 `SLIDER` 를 모르면 `null`(→ 달력 `mapNotNull` 이 건너뜀, 05 화면은 `type == null` 미션을 "수행 불가"로 다룸). `paramsToSpec(params)` → `MissionSpec(seconds, word, drawFrom, drawTo)` 만.
  - 제출 입력 모델 `SubmissionContent`(`core/data/DataStore.kt`) = `Image(bytes, ext)`, `Text(body)` (A0 에서 `Voice` 삭제). `SubmissionRepository.submit(missionId, content, myUserId)` 가 content 종류별 multipart 파트를 구성(`core/net/SubmissionRepository.kt`).
  - 수행 화면 라우팅: `MissionPerformScreen(type, topic, template, spec, …)`(`feature/mission/MissionPerformScreen.kt`)가 `type` 으로 `Write/Photo/DrawMissionScreen` 선택, 글 화면은 `template` 문자열로 `TEXT_FILL_BLANK`/`TEXT_ACROSTIC` 분기(`WriteMissionScreen.kt`).
  - 열람: `ResultsScreen`(`feature/results/ResultsScreen.kt`)이 `when (result.type)` 으로 `PhotoFeed/DrawingGrid/TextCards`; `SubmissionDetailScreen` 이 `DetailArtwork(type)`. `Submission` 모델(`core/model/Submission.kt`) 필드: id, author, isMine, type, createdAt, mediaURL, text, isLate, authorID, reactions, commentCount(+A7 의 `aiReview`).
  - 홈/05 카드, 달력, 마이페이지 통계가 `MissionType` 의 `displayName`/`icon`/`actionTitle` 을 사용(`MissionType.kt`). `HIcon` 에는 슬라이더/별 아이콘이 없다.
  - 7A: `SubmissionSocial`(스티커·댓글)은 `SubmissionDetailScreen` 에서 모든 타입에 공통으로 붙는다.
  - `ApiClient.postMultipart`: `Text`/`Bytes`/`FilePart` 파트 — 새 틀은 텍스트 파트만.

## Root Cause / Key Implementation Points

1. **타입/모델 확장**
   - `MissionType` 에 `SLIDER("slider", "슬라이더", HIcon.SLIDER_TEMP, "슬라이더 정하기")` 추가(+ `subjectForm`). `mediaToType("SLIDER") = SLIDER`. 달력·통계의 `mediaToType` 사용처가 새 타입을 정상 처리하는지 확인(서버 달력은 `media` 문자열을 그대로 내려줌).
   - `MissionSpec` 에 `optionA, optionB, left, right, question, topic` 추가, `paramsToSpec` 확장.
   - `Submission` 에 `number: Int?, choice: String?, guess: Int?, caption: String?` 추가(기본값 null — 기존 생성부 영향 없게), `template: String = ""`(틀별 카드 분기용; 서버 `template`). DTO 매핑(`SubmissionDto`) 확장, `templateType(template)` 에 `SLIDER_` 접두 → `SLIDER`.
   - `SubmissionContent` 에 `Scalar(number: Int? = null, guess: Int? = null, choice: String? = null, text: String? = null, caption: String? = null)` 추가. `SubmissionRepository.submit` 가 새 content 를 위 multipart 필드 규칙대로 직렬화(없는 값은 파트 생략).
2. **수행 화면** (`feature/mission/`): `WriteMissionScreen` 을 건드리기보다 **틀별 신규 컴포저블**을 `MissionPerformScreen` 이 `template` 으로 분기: `OneWordMissionScreen`(단어 입력 20자 + 이유 입력 100자 선택, 글자 수 표시), `ReviewMissionScreen`(별 5개 탭 `★/☆` 텍스트 기호 사용 + 한 줄 100자), `BalanceMissionScreen`(A/B 두 개의 큰 카드 선택 + 이유 선택 입력; 카드 문구는 `spec.optionA/B`), `SliderMissionScreen`(질문/주제 + 양 끝 라벨 + `Slider` 0~100, 값 표시; `SLIDER_CONSENSUS` 는 슬라이더 2개 — "내 위치"와 "그룹 평균 예측"). 슬라이더는 Material3 `Slider` 를 쓰되 색상·두께를 디자인 토큰(`HColors.accent` 등)으로 맞춘다(임시 UI, 코드 주석 `임시 UI`).
   - 입력 검증은 서버와 같은 한도를 클라이언트에서 먼저 적용하는 **순수 함수**로 분리(`feature/mission/TemplateInputRules.kt`: 글자 수는 grapheme 기준 — 기존 `limitGraphemes` 재사용) 후 단위 테스트.
   - 상단 안내 카드/05 카드에 `type.actionTitle`·틀별 안내(예: 밸런스 "둘 중 하나를 골라요", 슬라이더 양 끝 라벨) 표시.
3. **열람 화면** (`feature/results/`):
   - `ResultsScreen.when(type)` 에 `SLIDER` 분기와, `WRITE` 안에서 `template` 별 카드(`OneWordCard` 단어+이유, `ReviewCard` 별점+한 줄, `BalanceCard` 선택 A/B+이유)를 추가.
   - `SliderCard`: 양 끝 라벨 사이에 위치 막대(읽기 전용). 다수결 예측은 내 위치/예측 둘 다 표시.
   - **집계 섹션**: V 틀(`TEXT_BALANCE`, `SLIDER_*`)이면 목록 상단에 "결과" 카드 — `SubmissionRepository.result(missionId)`(신규, `GET /api/missions/{id}/result`)로 로드: 밸런스는 A/B 비율 막대+내 선택, 슬라이더는 평균·분포 점/막대·"내 위치와 평균의 차이", 다수결 예측은 순위표+승자 표시(`winnerUserIds`). 로드 실패는 카드 내 "결과를 불러오지 못했어요" 재시도.
4. **임시 이미지/아이콘**: `HIcon.SLIDER_TEMP`(벡터 `res/drawable/ic_slider_temp.xml`: 가로선+원), 별은 텍스트 `★`/`☆`, 파일 첫 줄 주석 `<!-- 임시 이미지: Figma 디자인 확정 시 교체 -->`. 그 외 새 이미지 없음.
5. **달력/마이페이지**: 달력 항목의 유형 이름·아이콘이 `SLIDER` 에서 깨지지 않게(예: `RecordCalendarScreen`, `MyPageScreen` 통계 목록에 슬라이더 항목 추가 또는 제외 결정 — 통계 `byType` 에 `SLIDER` 포함).
6. **서버 활성화 순서(문서화)**: 앱 릴리스 후 서버 `hawe.mission.enabled-templates` 에 새 틀을 추가. 구 앱이 새 틀 미션을 받으면 `type == null`/템플릿 미지원이므로 05 화면에 "앱을 업데이트해 주세요" 안내를 띄운다(구현: `type == null` 또는 모르는 template 일 때 05 카드 대체 문구 — A0 가 최소 문구를 이미 넣었으면 확장만).

## Files / Functions to Change

| 파일 | 변경 |
|---|---|
| `core/model/MissionType.kt`, `MissionSpec.kt`, `Submission.kt` | 위 1 |
| `core/net/MissionRepository.kt` (`mediaToType`, `paramsToSpec`), `SubmissionApi.kt` (DTO), `SubmissionRepository.kt` (`submit` 직렬화, `result`, `templateType`, `toModel`) | |
| `core/data/DataStore.kt` | `SubmissionContent` 확장 |
| `feature/mission/MissionPerformScreen.kt` + 신규 `OneWordMissionScreen.kt`, `ReviewMissionScreen.kt`, `BalanceMissionScreen.kt`, `SliderMissionScreen.kt`, `TemplateInputRules.kt` | 수행 화면 |
| `feature/mission/MissionFormat.kt`, `MissionScreen.kt` | 틀별 안내/구형 앱 안내 |
| `feature/results/ResultsScreen.kt`, `ResultComponents.kt`, `SubmissionDetailScreen.kt` + 신규 `ResultSummary.kt` | 열람·집계 |
| `designsystem/assets/HIcon.kt`, `res/drawable/ic_slider_temp.xml` | 임시 아이콘 |
| 달력/마이페이지: `feature/records/*`, `feature/mypage/MyPage*.kt` | `SLIDER` 처리 |
| 테스트 | `SubmissionRepositoryTest`(각 틀 multipart 직렬화: 필드 존재/부재, 응답 매핑 number/choice/guess), `result()` MockWebServer, `MissionRepositoryTest`(SLIDER 매핑, params 확장), `TemplateInputRulesTest`(한도·grapheme), 결과 집계 표시용 순수 함수(비율 계산 등) 단위 테스트, `ResultsFormatTest` 확장 |

## Implementation Steps

1. 모델/DTO/레포지토리(제출 직렬화·result) + 테스트. 2. `MissionType.SLIDER` 추가 → 컴파일 오류 전부 수정(exhaustive when). 3. 수행 화면 6종(작은 것부터: 후기→한 단어→밸런스→슬라이더). 4. 열람 카드·집계 섹션. 5. 달력/통계/구형 안내. 6. `android\gradlew.bat :app:testDebugUnitTest --offline`. 7. 서버 연동은 사용자: 서버를 `enabled-templates` 에 새 틀을 넣고 기동해 각 틀을 한 번씩 제출·열람.

## Constraints

- `android/` 만. 새 라이브러리 금지(Material3 `Slider` 는 이미 의존성에 있는지 확인, 없으면 기존 컴포넌트로 직접 그리기). 서버 값이 진실 — 결과 판정은 클라이언트가 계산하지 않는다(표시용 비율 계산만).
- 기존 3종 유형(사진/그림/글)의 수행·열람 동작 불변. 알림/푸시 금지. 커밋/푸시 금지.

## Edge Cases

- 입력 한도: 단어 20자(grapheme), 이유 100자, 후기 한 줄 100자, 별점 미선택 시 제출 비활성, 밸런스 선택 전 비활성, 슬라이더는 항상 값이 있으므로 기본 50, 다수결 예측은 두 값 모두.
- 서버 400/413/415 문구, 네트워크 실패 시 입력 유지.
- 열람: 결과 카드는 내가 제출한 뒤에만(423 이면 기존 잠금 안내), 제출 1명뿐인 결과, 동률 승자 복수, 평균 소수 표시(서버가 반올림해 내려줌 — 그대로 표시).
- 구형 앱 호환: 서버가 새 틀을 켰는데 앱이 구형이면 05 에서 안내만(크래시 금지). 신형 앱 + 서버가 새 틀을 아직 안 켬 → 영향 없음.
- 슬라이더 `left`/`right` 라벨이 길 때 줄바꿈/말줄임.

## Regression Risks

- `MissionType` 추가로 인한 exhaustive `when` 연쇄 수정: 달력 필터, 마이페이지 통계(`MyPageLogic`), `DataStore` 의 목업 시드(`DemoSeed`/`TopicPool`)가 새 타입을 처리하도록(목업은 서버 연결 후 대부분 비활성이지만 컴파일은 필요).
- `Submission` 생성자 기본값 추가라 기존 생성부 호환. `SubmissionRepository.toModel` 이 `templateType` 이 null 이면 항목을 버리므로(`mapNotNull`) 모르는 틀의 제출은 목록에서 사라짐 — 새 틀 매핑 누락 시 "제출했는데 피드에서 사라짐" 처럼 보임 → 테스트로 6틀 매핑 확인.
- 7A `SubmissionSocial`·결과 화면 레이아웃 높이: 새 카드가 추가돼도 스크롤 구조 유지.
- 글 화면 상태(`WriteMissionScreen`)를 재사용하지 않고 분리하므로 기존 삼행시/빈칸 채우기 회귀는 없어야 한다.

## Tests Required

위 표 + 구형 앱 안내 분기(순수 함수 `supportsTemplate(template): Boolean`) 단위 테스트. 화면은 컴파일 + 수동 확인.

## Validation Steps

`android\gradlew.bat :app:testDebugUnitTest --offline`(JDK 21) 통과 기록. 사용자 수동 확인 체크리스트를 보고서에 포함(6틀 제출·열람·결과).

## Potential Merge Conflicts

`DataStore.kt`, `SubmissionRepository.kt`, `MissionRepository.kt`, `MissionPerformScreen.kt`, `ResultsScreen.kt`(A0·A1·A3·A7·A8·A6 과 겹침 — **A0 → A1 → A3 → A4 → A7 → A8 → A6** 순 권장), `MissionType.kt`(광범위 영향).
