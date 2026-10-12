# B5. 미션 틀 확장 ① — 단순 글 틀 + 집계(V) 틀 + 결과 API (백엔드)

> 범위: 백엔드만. 앱 화면은 A4. 알림 무관. **전제: 7·8단계 + B0(음성 제거) + B1 커밋 후**(B0 이 음성 틀·오디오 제출 분기를 지운 상태가 기준이다. `SubmissionMapper` 의 작성자 표시 쿼리는 B1 이 바꾼다). 근거: `docs/BACKEND/MISSIONS_API.md` §1-2(코드표), §3(제출 규칙), §5(집계 결과), §6-1(틀 선택), §11. 이 계획의 대상 틀 6종: `TEXT_ONE_WORD`, `TEXT_REVIEW`, `TEXT_BALANCE`(V), `SLIDER_POSITION`(V), `SLIDER_TASTE`(V), `SLIDER_CONSENSUS`(V). 정답 맞히기(G) 유형은 음성 제거로 폐기되었다(B6 참고).

## Goal

1. 위 6개 틀을 **제출**할 수 있다(검증·저장·응답).
2. `GET /api/missions/{missionId}/result` — V 유형 4종(`TEXT_BALANCE`, `SLIDER_*`)의 집계 결과.
3. 발행 풀(어떤 틀이 실제로 선택되는지)을 **설정으로 제어**한다. 기본값은 지금의 MVP 7종 그대로 → 앱(A4)이 새 틀을 그릴 수 있게 된 뒤에 설정으로 켠다(안 그러면 새 틀 미션을 받은 구 앱이 깨짐).
4. 새 틀의 임시 문장 생성(`PlaceholderMissionGenerator`)을 추가한다(AI 연동은 B8).

## Findings

- `MissionTemplate` enum 16종(1차 틀 전부)은 이미 정의돼 있고 `GET /mission-templates` 가 16종 전체를 돌려준다. 여기에 `paramsSchema`/`payloadSchema` 문자열이 있다 — **`TEXT_ONE_WORD` 의 payload 에 `caption: string(required)` 로 되어 있으나 MISSIONS_API §3-2 는 caption 을 선택으로 규정**한다 → 문서(§3-2) 기준으로 enum 문자열을 `optional` 로 고친다.
- `MissionTemplateSelector`: `MVP_POOL` 상수(**B0 이후 7종**), `MEDIA = [PHOTO, TEXT, DRAW]`(B0 이후), `select(groupId, date, slotNo, dailyMissionCount, yesterdayMedia)` 가 결정적 시드로 매체→틀을 고른다. `MissionPublisher.publishIfDue` 가 호출. 풀을 설정으로 받지 않는다.
- `PlaceholderMissionGenerator.variants(template, withTarget)` 은 (B0 이후) 7종만 처리하고 나머지는 `IllegalArgumentException`.
- `MissionProperties(defaultPublishTime, defaultDeadlineTime, maxDailyCount)` (`hawe.mission.*`).
- `SubmissionService.submit`: 템플릿을 `image`(PHOTO_NOW, PHOTO_GALLERY, PHOTO_COLOR, DRAW_SKETCH, DRAW_A_TO_B)/`text`(TEXT_FILL_BLANK, TEXT_ACROSTIC) 로 분기(오디오 분기는 B0 에서 삭제됨)하고, 그 외 틀은 `invalid("지원하지 않는 미션 틀입니다")`. 요구하지 않는 필드(`fields`/`files` 키)는 `allowed` 세트로 거부, 중복 필드 거부, `caption` ≤200 코드포인트. 글 본문은 `answer_data` JSON `{"text": …}` 로 저장. 파일 저장 시 롤백 정리 패턴 있음.
- `SubmissionInsertRow(missionId, userId, mediaUrl, caption, submittedAt, late, mediaType, answerData)` — `number_value`/`choice_value` 컬럼 대응 필드 없음. DB `submissions` 에는 `number_value tinyint unsigned`, `choice_value char(1)`, `answer_data` JSON(체크 제약), `answer_text`, `answer_aliases` 가 **이미 있다**(`V1`).
- `SubmissionMapper.FIELDS`: `s.id … s.answer_data, s.submitted_at, s.is_late AS late` — number/choice 미선택. `SubmissionRow`·`SubmissionResponse(id, missionId, author, template, mediaUrl, mediaType, caption, text, duration, waveform, reactions, commentCount, submittedAt, isLate)` — number/choice/guess 필드 없음. `SubmissionResponse` 는 `@JsonInclude(NON_NULL)`.
- `response(row)` 가 `answer_data` JSON 에서 `text` 를 읽는다(`duration`/`waveform` 은 B0 에서 삭제됨).
- 잠금 규칙 `requireSubmitted(missionId, userId)` → `FEED_LOCKED`(423). 멤버 확인 `requireMember`. `MissionErrorCode`: `MISSION_NOT_FOUND`, `MISSION_CLOSED` 뿐.
- 달력(`CalendarMapper/CalendarService`)·활동 요약은 `missions.template` 의 매체 문자열을 앱에 내려준다. 새 매체 `SLIDER` 가 풀에 들어오면 `media: "SLIDER"` 가 내려가며 Android `mediaToType` 은 모르는 매체를 건너뛴다(현재 코드가 `mapNotNull`) — A4 에서 처리.

## Root Cause / Key Implementation Points

1. **발행 풀 설정화**: `MissionProperties` 에 `List<MissionTemplate> enabledTemplates` 추가(`hawe.mission.enabled-templates`, **기본값 = B0 이후 MVP 7종**). `MissionTemplateSelector.select(…)` 가 풀을 파라미터로 받게 하고 `MissionPublisher` 가 `properties.enabledTemplates()` 를 넘긴다. 매체 순서 `MEDIA` 는 풀에 존재하는 매체만으로 구성(풀에 `SLIDER` 틀이 없으면 매체 4종 그대로 → **기본 설정에서 기존 선택 결과가 바뀌지 않아야 한다**, 회귀 테스트로 고정). 풀에 있는 틀 중 `phase() == 1` 만 허용. 풀이 비면 시작 시 예외(설정 오류).
2. **제출 검증 확장** (`SubmissionService.submit`): 틀 분류에 `scalar` 그룹을 추가하고 틀별 허용 필드/규칙을 `MISSIONS_API §3-2` 대로 구현.

   | 틀 | 필수 | 선택 | 저장 | 규칙 |
   |---|---|---|---|---|
   | `TEXT_ONE_WORD` | `text` | `caption` | `answer_data.text`, `caption` | text trim 후 1~20 grapheme, 공백 불가 · caption ≤100 코드포인트 |
   | `TEXT_REVIEW` | `number`, `text` | — | `number_value`, `answer_data.text` | number 1~5 정수 · text trim 후 1~100 grapheme |
   | `TEXT_BALANCE` | `choice` | `caption` | `choice_value`, `caption` | choice 는 `A`/`B`(대소문자 구분, 정확히 일치) · caption ≤200 |
   | `SLIDER_POSITION`, `SLIDER_TASTE` | `number` | — | `number_value` | 0~100 정수 |
   | `SLIDER_CONSENSUS` | `number`, `guess` | — | `number_value`, `answer_data.guess` | 둘 다 0~100 정수 |

   숫자 파싱: 정수만(소수·공백·빈 값·범위 밖 → 400 `INVALID_SUBMISSION`). `number_value` 컬럼은 `tinyint unsigned`(0~255)라 0~100 안전. 파일은 이 틀들에서 **받지 않는다**(files 키가 있으면 `요구하지 않는 필드`). 기존 틀의 규칙(허용 필드 세트, 글 틀의 caption 거부 등)은 **바꾸지 않는다**.
3. **저장/응답 필드**: `SubmissionInsertRow` 에 `Integer numberValue`, `String choiceValue` 추가(생성자 파라미터 추가 → 호출부 수정), `SubmissionMapper.insert` 에 컬럼 추가, `FIELDS` 에 `s.number_value AS number_value, s.choice_value AS choice_value` 추가, `SubmissionRow` 에 `numberValue`, `choiceValue`, `SubmissionResponse` 에 `Integer number`, `String choice`, `Integer guess` 추가(NON_NULL 이라 해당 틀에서만 노출). 위치: `text` 다음, `duration` 앞이 아니라 **기존 필드 순서를 깨지 않도록 `waveform` 뒤·`reactions` 앞**에 추가. `guess` 는 `answer_data.guess` 에서 읽는다. 생성자 호출부(`SubmissionResponseTests` 포함) 수정.
4. **결과 API**: `GET /api/missions/{missionId}/result` — 신규 `submission/service/MissionResultService` + `submission/controller/MissionResultController` + `submission/dto/MissionResultResponse`(`@JsonInclude(NON_NULL)` 한 레코드에 틀별 필드를 nullable 로: `template, totalCount, choices[], myChoice, average, distribution[], myNumber, diffFromAverage, ranking[], winnerUserIds[]`). 순서: 미션 없음 404 `MISSION_NOT_FOUND` → 멤버 아님 403 → 내가 미제출 423 `FEED_LOCKED` → 틀이 V 유형이 아니면 400 `RESULT_NOT_SUPPORTED`(`MissionErrorCode` 에 추가). 집계는 해당 미션 제출 전부를 한 번에 조회(새 매퍼 쿼리 `findResultRowsByMissionId(missionId)`: `userId, nickname(표시 프로필), numberValue, choiceValue, answerData, submittedAt`; `SubmissionMapper.FIELDS/JOINS` 재사용으로 B1 의 표시 닉네임 적용) 후 메모리에서 계산:
   - `TEXT_BALANCE`: `choices` 는 항상 `A`,`B` 두 항목(0 포함), `myChoice`.
   - `SLIDER_POSITION/TASTE`: `average`(소수 첫째 자리 HALF_UP), `distribution`(제출 순서, `{userId, nickname, number}`), `myNumber`, `diffFromAverage = myNumber - average`(소수 첫째 자리).
   - `SLIDER_CONSENSUS`: `average`=제출자들의 `number` 평균, 각 제출자 `diff = |guess - average|`(소수 첫째 자리), `ranking` 은 `diff` 오름차순(동률은 제출 시각→id), `winnerUserIds` 는 최소 diff 와 같은 모든 사용자(동률이면 복수). `guess` 가 저장되지 않은 이상 데이터는 건너뜀.
   - `totalCount` = 제출 수. 결과는 "현재까지의 제출 기준"(마감 후에도 제출 가능하므로 변할 수 있음).
5. **임시 문장 생성기**: `PlaceholderMissionGenerator.variants` 에 6종 추가(틀마다 3개 이상, `params` 는 `MissionTemplate.paramsSchema` 키와 정확히 일치): `TEXT_ONE_WORD`(params 없음), `TEXT_REVIEW`(없음), `TEXT_BALANCE`(`optionA`,`optionB` 서로 다름), `SLIDER_POSITION`(`question,left,right`), `SLIDER_TASTE`(`topic,left,right`), `SLIDER_CONSENSUS`(`question,left,right`). `left ≠ right`, 각 ≤20자. 문구에 외모·몸·혐오 소재 금지(MISSIONS_API §8).
6. **기본 설정에서 동작 불변**: 설정 기본값이 (B0 이후) 7종이면 발행·선택·API 응답은 이전과 동일해야 한다. 새 틀은 (a) 설정으로 켜거나 (b) 테스트에서 직접 `missions.template` 을 UPDATE 해서 검증.

## Files / Functions to Change

| 파일 | 변경 |
|---|---|
| `global/config/MissionProperties.java`, `application.properties` | `enabledTemplates`(기본 MVP 7종 이름 목록) |
| `mission/domain/MissionTemplate.java` | `TEXT_ONE_WORD` payload 문자열 caption → optional |
| `mission/domain/MissionTemplateSelector.java` | 풀 파라미터화(`select(…, pool)`), `MEDIA` 를 풀에서 도출(고정 순서 `PHOTO, TEXT, DRAW, SLIDER` 중 풀에 있는 것만), 기본 결과 불변 |
| `mission/service/MissionPublisher.java` | 풀 전달 |
| `mission/service/PlaceholderMissionGenerator.java` | 6종 변형 |
| `submission/service/SubmissionService.java` | 제출 검증 확장(scalar 그룹), `response` 에 number/choice/guess |
| `submission/dao/SubmissionInsertRow.java`, `SubmissionRow.java`, `SubmissionMapper.java` | number/choice 컬럼 insert·select, `findResultRowsByMissionId` |
| `submission/dto/SubmissionResponse.java` | `number`, `choice`, `guess` |
| `submission/service/MissionResultService.java`, `submission/controller/MissionResultController.java`, `submission/dto/MissionResultResponse.java` (신규) | Key Point 4 |
| `mission/service/MissionErrorCode.java` | `RESULT_NOT_SUPPORTED(BAD_REQUEST, "집계가 없는 미션입니다")` |
| 테스트 | 아래 |
| 문서 | `MISSIONS_API.md` §3(제출 규칙 표를 "구현됨"으로, 응답에 number/choice/guess), §5(구현됨·rounding 규칙), §6-1(풀 설정화), `CONTRACT_DECISIONS.md` §3·§6 상태, `API_DESIGN.md`, Postman `HAWE_SUBMISSION` 에 6틀 제출·결과 요청 추가. |

## Implementation Steps

1. 설정/선택기 파라미터화 + **기존 선택 결과 불변** 회귀 테스트 먼저. 2. 제출 insert/select 필드 확장 + 6틀 검증. 3. 결과 서비스·API. 4. 임시 생성기 6종. 5. 테스트 → `.\mvnw.cmd clean test -q`. 6. 문서·Postman. (스키마 변경 없음 — 컬럼이 이미 있으므로 DB 재생성 불필요.)

## Constraints

- 스키마 변경 없음(V1 불변). 기본 설정에서 기존 7종 동작·응답 불변. 2차 틀(R 유형)·AI(B8)·사진 적합도(B10)는 건드리지 않는다.
- 새 틀의 파일 업로드 경로 변경 금지(새 틀은 파일 없음). 알림 없음. 도장/달력 계산 변경 금지(제출 기록 기반 그대로).
- `SubmissionService.submit` 은 기존 틀의 동작을 보존하며 분기만 확장한다. 커밋/푸시·지침 파일 수정 금지.

## Edge Cases

- 숫자: `"50.0"`, `"abc"`, `""`, `"-1"`, `"101"`, `" 5"` → 400. `TEXT_REVIEW` `number=0`/`6` → 400. `TEXT_BALANCE` `choice="a"`/`"C"`/없음 → 400. `SLIDER_CONSENSUS` `guess` 누락 → 400. 허용되지 않은 필드(예: `SLIDER_POSITION` 에 `guess`/`text`/`file`) → 400 `요구하지 않는 필드`. 같은 필드 중복 → 400.
- `TEXT_ONE_WORD`: 이모지·결합 문자는 grapheme 단위로 20자 제한(기존 `graphemes()` 사용), caption 101자 → 400, 빈 caption 은 NULL 로 저장.
- 결과: 제출자 1명뿐인 슬라이더(평균=본인, diff 0, 승자 본인); 제출 0명은 결과 요청자가 제출해야 열리므로 불가능; 동률 승자 복수; `average` 소수 처리(예: 평균 62.25 → 62.3, 비교는 반올림 전 값으로 하고 표시만 반올림); 마감 후 늦은 제출이 결과에 포함되는지(포함 — "현재까지"); V 아닌 틀 → 400 `RESULT_NOT_SUPPORTED`; 다른 그룹 멤버 403; 내가 미제출 423.
- 기본 설정(7종)에서는 새 틀 미션이 발행되지 않는다 — 풀에 새 틀을 넣은 설정에서만 발행되고, `SLIDER` 매체가 순환에 들어간다. 어제 매체 제외 규칙(하루 1개)이 5매체에서도 동작하는지.
- 달력/활동 요약에 `SLIDER` 매체 미션이 섞여도 서버 응답이 깨지지 않는다(앱이 모르는 매체는 앱 쪽에서 건너뜀).

## Regression Risks

- `SubmissionInsertRow` 생성자 변경 → 호출부(`SubmissionService`, 테스트) 수정. `SubmissionResponse` 레코드 필드 추가 → 생성 호출부(`SubmissionService`, `SubmissionResponseTests`) 수정, JSON 키 순서에 의존하는 테스트가 있으면 확인.
- 선택기 시그니처 변경 → `MissionTemplateTests`/`MissionPublisher` 관련 테스트 수정, **기본 설정 결정 결과가 동일**함을 기존 시드 기대값으로 검증.
- `SubmissionMapper.FIELDS` 확장은 피드/상세/제출 응답 모두에 영향 — 기존 응답 JSON 이 새 틀이 아닌 경우 변하지 않아야 함(NON_NULL).
- `MissionProperties` 레코드 컴포넌트 추가 → 이를 직접 생성하는 테스트 수정.

## Tests Required

- `MissionTemplateSelectorTests`: 기본 풀 결과가 변경 전과 같음(여러 시드), 새 틀 포함 풀에서 5매체 순환·어제 매체 제외·결정성.
- `SubmissionServiceTests`(Mockito) 6틀 × (성공 / 필드 누락 / 범위 밖 / 허용 안 된 필드) 매트릭스, 저장 필드 매핑(number/choice/guess/answer_data), 기존 틀 회귀.
- `SubmissionMapperTests`(실 DB): number/choice 왕복, `findResultRowsByMissionId`.
- `MissionResultServiceTests`: 틀별 집계 값(평균 반올림, diff, 승자 동률, 순위 정렬), 접근 규칙(404/403/423/400).
- `PlaceholderMissionGeneratorTests`: 6종 각각 3개 이상 변형, `params` 키가 `paramsSchema` 와 일치, 직전 문장과 같은 문장 회피, `left≠right`/`optionA≠optionB`.
- MockMvc 통합: 새 틀 미션을 DB 에 직접 만들고(`UPDATE missions SET template=…, params=…`) 두 명이 제출 → 피드 항목에 number/choice/guess 노출, `GET /missions/{id}/result` 값, 미제출자 423.

## Validation Steps

`.\mvnw.cmd clean test -q` 통과 기록, 기본 설정 불변 회귀 결과 명시, Postman 파싱·한글, `git diff --check`, 서버 스모크(가능 시)에서 `enabled-templates` 를 새 틀로 바꿔 발행·제출·결과 확인, 못 하면 "미검증".

## Potential Merge Conflicts

`SubmissionService`/`SubmissionMapper`/`SubmissionRow`/`SubmissionResponse`(B0·B1 후), `MissionTemplateSelector`/`MissionPublisher`/`PlaceholderMissionGenerator`(B7·B8 과 겹침), `MissionProperties`, `docs/BACKEND/MISSIONS_API.md`.
