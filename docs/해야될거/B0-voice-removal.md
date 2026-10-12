# B0. 음성 기능 완전 제거 (백엔드 + 문서)

> 범위: 백엔드(`src/`)와 설계 문서(`docs/BACKEND/`, `docs/FEATURE/`, `docs/FRONTEND/`). 앱은 A0(Android)·A5 항목 0(iOS). 알림·배포 무관. **이 계획은 큐의 모든 미션/제출 관련 계획(B5·B7·B8 등)보다 먼저 실행한다**(틀 목록·매체 목록·제출 검증 코드를 바꾸므로 뒤 계획의 전제가 된다). **전제: 7·8단계 커밋 후**(8단계가 `SubmissionService`/탈퇴 삭제 쿼리를 건드리고 있음). 사용자 결정: "음성 기능을 아예 제거한다".

## Goal

음성 미션과 음성 제출을 서비스에서 **완전히 없앤다**. 매체는 `PHOTO` / `DRAW` / `TEXT` (+ 이후 B5 의 `SLIDER`)만 남는다.
- 제거 틀: `VOICE_LINE`(한 줄), `VOICE_MIMIC`(소리 흉내), `VOICE_IMPRESSION`(성대모사). 1차 틀 16종 → **13종**, MVP 발행 풀 8종 → **7종**(`PHOTO_NOW`, `PHOTO_GALLERY`, `PHOTO_COLOR`, `DRAW_SKETCH`, `DRAW_A_TO_B`, `TEXT_ACROSTIC`, `TEXT_FILL_BLANK`).
- 제거 기능: 음성 파일 업로드·저장·서빙, `duration`/`waveform` 필드, 오디오 파일 형식 판별(m4a/mp3/aac/webm), 음성 길이 검증, 정답 맞히기(G 유형) 제출 틀과 정답 시도 API 계획(B6 폐기).

## Findings

- 음성 관련 백엔드 사용처(`rg -i "voice|audio|waveform|duration"` 결과):
  - `mission/domain/MissionTemplate.java` — enum 3항목(`VOICE_LINE`, `VOICE_MIMIC`, `VOICE_IMPRESSION`).
  - `mission/domain/MissionTemplateSelector.java` — `MVP_POOL` 에 `VOICE_LINE`, `MEDIA = [PHOTO, VOICE, TEXT, DRAW]`.
  - `mission/service/PlaceholderMissionGenerator.java` — `case VOICE_LINE`, `voice(...)` 헬퍼.
  - `submission/service/SubmissionService.java` — `audio` 분기(허용 필드 `file/duration/waveform/caption`, 5MB, 30초, `parseWaveform`, `answer_data` 의 `duration/waveform`, `mediaType = "AUDIO"`), `response()` 가 `answer_data` 에서 `duration/waveform` 을 읽음.
  - `submission/dto/SubmissionResponse.java` — `Double duration`, `List<Double> waveform` 필드.
  - `global/storage/FileTypeDetector.java` — `AUDIO` 판별 분기 5개(`m4a`, `webm`, `mp3`, `aac`).
  - `calendar/service/CalendarService.java` — `MEDIA = ["DRAW","PHOTO","TEXT","VOICE"]` (활동 요약 `byMedia` 순서/0 채움에 쓰이는 목록).
  - 테스트: `CalendarServiceTests`, `FileTypeDetectorTests`, `MissionTemplateSelectorTests`, `MissionTemplateTests`, `SubmissionServiceTests` 등이 VOICE/오디오를 다룸.
- `CommentService`, `ProfileService` 는 이미 `"IMAGE".equals(detected.mediaType())` 만 허용 → 오디오 판별이 사라져도 영향 없음(비이미지 → 415).
- **기존 데이터 방어는 이미 되어 있다**: `MissionService.media(templateCode)`, `CalendarService.mediaOf`, `MissionPublisher`(어제 매체 계산) 가 `MissionTemplate.valueOf` 실패(`IllegalArgumentException`)를 삼키고 `null` 매체로 처리한다. 즉 DB 에 `VOICE_*` 미션이 남아 있어도 500 이 나지 않는다. (프로젝트 방식상 개발 DB 는 V1 수정 때 재생성하므로 실데이터는 없다.)
- 스키마: `submissions.media_type`(문자열), `answer_data`(JSON), `answer_text`, `answer_aliases`, 그리고 `submission_guesses` 테이블은 G 유형(정답 맞히기)을 위해 V1 에 이미 있다. **이 계획에서는 스키마를 바꾸지 않는다**(8단계 탈퇴 삭제·`GroupMapper.deleteSubmissionGuessesByGroupId` 가 이 테이블을 참조하고 있어 지금 건드리면 충돌/회귀 위험). 잔재 정리 여부는 결정 대기(아래 Constraints).
- 업로드 한도: `spring.servlet.multipart.max-file-size=10MB` — 음성 5MB 는 별도 상수라 제거만 하면 됨.
- 문서: 음성 언급 파일 — `docs/BACKEND/{API_DESIGN,BACKEND_DB,CONTRACT_AUDIT,CONTRACT_DECISIONS,CONTRACT_WORK_PLAN,MISSIONS_API}.md`, `docs/FEATURE/{MISSIONS,PROJECT_OVERVIEW}.md`, `docs/FRONTEND/{ANDROID,APP_DECISIONS}.md`, `docs/superpowers/plans/2026-10-12-contract-phase1.md`(과거 기록 — 수정하지 않음). 지침 파일 `CLAUDE.md`/`AGENTS.md` 는 수정하지 않는다(음성 언급 없음 확인됨).
- Postman: `docs/BACKEND/postman/HAWE_SUBMISSION.postman_collection.json` 등에 음성 제출 요청이 있을 수 있음(확인 후 제거).

## Root Cause / Key Implementation Points

1. **틀 카탈로그 축소**: `MissionTemplate` 에서 `VOICE_*` 3항목 삭제. 이 순간부터 `GET /api/mission-templates` 는 13종을 돌려준다(응답에 `media: "VOICE"` 가 더 이상 나오지 않음). `MissionTemplateSelector.MVP_POOL` 에서 `VOICE_LINE` 삭제, `MEDIA` 를 `[PHOTO, TEXT, DRAW]` 로 변경. **하루 1개일 때 "어제 매체 제외" 규칙은 매체가 3개여도 동일하게 동작**한다(어제 매체를 뺀 첫 번째). 후보 부족 시 완화 규칙도 그대로.
   - 선택 결과가 바뀐다: 매체 순서의 셔플 입력이 4개 → 3개라 같은 시드에서도 결과가 달라진다 → `MissionTemplateSelectorTests` 의 시드 기대값을 새 규칙으로 갱신(고정 시드로 결정성만 검증). 이것은 의도된 변화이므로 "회귀"로 취급하지 않는다.
2. **생성기**: `PlaceholderMissionGenerator` 에서 `case VOICE_LINE` 과 `voice()` 삭제. 이후 계획(B5/B8)이 새 틀을 추가한다.
3. **제출 검증**: `SubmissionService.submit` 에서 `audio` 변수·분기, `duration`/`waveform` 처리, 음성 크기 상수, `parseWaveform`, `answer_data` 의 `duration/waveform` 쓰기·읽기를 삭제한다. 틀 분류는 `image`(`PHOTO_*`, `DRAW_*`)와 `text`(`TEXT_ACROSTIC`, `TEXT_FILL_BLANK`)만 남는다(B5 가 scalar 분류를 추가함). 오디오 파일이 `file` 로 올라오면 `FileTypeDetector` 가 `null` 을 돌려주므로 415 `UNSUPPORTED_FILE_TYPE` — 별도 처리 불필요. `duration`/`waveform` 필드가 오면 기존 규칙대로 "요구하지 않는 필드" 400.
4. **응답 계약**: `SubmissionResponse` 에서 `duration`, `waveform` 삭제. 앱은 `ignoreUnknownKeys` 이고 서버가 키를 안 내려주는 방향이라 호환 문제 없음(Android 모델의 `duration/waveform` 은 A0 에서 제거).
5. **파일 판별**: `FileTypeDetector` 에서 `AUDIO` 분기 전부 삭제(`Detected` 의 `mediaType` 은 `IMAGE` 만 남지만 시그니처는 유지 — `"IMAGE".equals(...)` 호출부 불변). `FileTypeDetectorTests` 의 오디오 케이스는 "null 반환" 케이스로 바꾼다(오디오 시그니처 바이트가 이제 지원 안 함).
6. **달력/통계**: `CalendarService.MEDIA` 에서 `"VOICE"` 삭제 → 활동 요약 `byMedia` 에 `VOICE` 키가 더 이상 나오지 않는다. 이미 `VOICE` 로 제출된 과거 데이터가 있는 환경(없음)에서는 `countSubmissionsByTemplate` 이 알 수 없는 템플릿을 `mediaOf == null` 로 건너뛰는지 확인(건너뛰어야 함).
7. **설정 정리**: 음성 관련 `application.properties` 항목이 있으면 삭제(현재는 없음 — 확인만).
8. **문서 갱신(이 계획의 일부)**:
   - `MISSIONS_API.md`: §1-2 코드표에서 `VOICE_*` 3행 삭제, "G 유형" 설명을 "현재 틀 없음(정답 맞히기는 폐기 — 그림 맞히기 AI 게임은 온디바이스, M1/A8 참고)"으로, §3 제출 표에서 음성 행·`duration`/`waveform` 필드·음성 파일 한도 삭제, §3-3 응답 예시에서 음성 예시 제거, §4-1 정답 맞히기 절을 "폐기됨(음성 제거로 G 틀 없음)" 안내로 축소, §6-1 MVP 풀 8종 → 7종·매체 4 → 3, §8 정책 충돌 목록에서 `VOICE_*` 항목 삭제, §10 에러 코드의 `GUESS_*` 4개 표시를 "미사용(예약)"으로, §9 DB 변경의 `answer_*`/`submission_guesses` 설명에 "현재 미사용" 주석.
   - `API_DESIGN.md`: 미션/제출 설명의 음성 언급 삭제, 통계 `byMedia` 예시에서 `VOICE` 제거.
   - `BACKEND_DB.md`: 제출 답 저장 방식에서 음성 필드 삭제, `answer_text/answer_aliases/submission_guesses` 에 "미사용(잔재)" 표기.
   - `CONTRACT_DECISIONS.md`: §6 파일 정책에서 음성 5MB·형식(`m4a/mp3/aac/webm`) 삭제 및 "음성 제거" 결정 한 줄(날짜 포함) 추가, §9 활동 요약 `byMedia` 목록에서 VOICE 삭제, 영향받는 절에 "음성 제거로 N/A" 표기. 과거 결정을 지우지 말고 "폐기됨(음성 제거)" 로 남긴다.
   - `CONTRACT_AUDIT.md`/`CONTRACT_WORK_PLAN.md`: 음성 관련 행에 "폐기" 주석.
   - `docs/FEATURE/MISSIONS.md`: "2. 음성" 절 전체에 취소선/"폐기됨(음성 기능 제거)" 표기(아이디어 문서이므로 삭제 대신 폐기 표기).
   - `docs/FEATURE/PROJECT_OVERVIEW.md`: 매체 설명(사진/음성/글/그림/슬라이더 → 사진/글/그림/슬라이더), §4 틀 표, §11 정책(음성 길이 검증 항목 취소), §6 AI 역할의 매체 목록, 틀 개수.
   - `docs/FRONTEND/ANDROID.md`, `APP_DECISIONS.md`: 음성 화면/마이크 권한 언급에 "제거됨" 표기.
   - Postman 컬렉션에서 음성 요청 삭제.

## Files / Functions to Change

| 파일 | 변경 |
|---|---|
| `mission/domain/MissionTemplate.java` | `VOICE_LINE`, `VOICE_MIMIC`, `VOICE_IMPRESSION` 삭제 |
| `mission/domain/MissionTemplateSelector.java` | `MVP_POOL`·`MEDIA` 수정 |
| `mission/service/PlaceholderMissionGenerator.java` | `VOICE_LINE` case·`voice()` 삭제 |
| `submission/service/SubmissionService.java` | 오디오 분기·`duration/waveform` 처리·`parseWaveform`·응답 읽기 삭제 |
| `submission/dto/SubmissionResponse.java` | `duration`, `waveform` 필드 삭제(생성 호출부 수정) |
| `global/storage/FileTypeDetector.java` | 오디오 판별 삭제 |
| `calendar/service/CalendarService.java` | `MEDIA` 에서 `VOICE` 삭제 |
| 테스트 | `FileTypeDetectorTests`, `MissionTemplateTests`(카탈로그 13종·풀 7종), `MissionTemplateSelectorTests`, `PlaceholderMissionGeneratorTests`(있다면), `SubmissionServiceTests`(음성 케이스 삭제, 오디오 업로드 → 415 케이스 추가), `SubmissionResponseTests`, `CalendarServiceTests`, MockMvc 제출 테스트(음성 제출 케이스 삭제) |
| 문서·Postman | 위 Key Point 8 |

## Implementation Steps

1. 코드 삭제는 **컴파일 오류가 곧 체크리스트**: `MissionTemplate` 삭제 → 컴파일 오류 수정 → 테스트 수정. 2. 선택기 시드 기대값 갱신. 3. 문서 갱신. 4. `rg -i "voice|audio|waveform|m4a|음성|녹음" src docs/BACKEND docs/FEATURE docs/FRONTEND` 로 잔여 점검(과거 기록 문서·"폐기" 표기 문장은 허용). 5. `.\mvnw.cmd clean test -q`. (스키마 변경 없음 — DB 재생성 불필요.)

## Constraints

- **스키마 불변**(`answer_text`, `answer_aliases`, `submission_guesses`, `media_type` 유지). 잔재 정리(컬럼·테이블 삭제 + `GroupMapper`/`WithdrawMapper` 삭제 쿼리 정리)는 별도 결정: 기본값은 **유지**(후속에 그림 맞히기 같은 R 유형이나 서버 기반 게임이 재사용 가능, 지금 건드리면 8단계와 충돌). 정리를 원하면 그때 V1 직접 수정 + 로컬 DB 재생성으로 별도 계획.
- 음성 제거 외의 동작 변경 금지(다른 틀·API·응답 불변). 기존 `/files/**` 공개 서빙 설정 불변. `CLAUDE.md`/`AGENTS.md` 수정 금지, 커밋/푸시 금지.
- 앱 변경 금지(A0/A5). 앱이 구버전이어서 음성 화면이 남아 있어도 서버가 음성 미션을 더 이상 발행하지 않으므로 문제없다 — 단 이미 발행된 VOICE 미션이 있는 환경은 없다는 전제를 보고서에 명시.

## Edge Cases

- 오디오 파일을 사진 틀에 `file` 로 업로드 → 415 `UNSUPPORTED_FILE_TYPE`(이전엔 `IMAGE != AUDIO` 로도 415 였음, 동작 동일). `duration`/`waveform` 를 보내면 400 `요구하지 않는 필드`.
- `template = VOICE_LINE` 인 기존 미션 행(비재생성 환경)에 대한 제출/조회: 제출은 "지원하지 않는 미션 틀입니다" 400, 조회(`GET /missions/{id}`·today·달력)는 `media: null` 로 크래시 없이 응답 — 테스트로 고정.
- 하루 1개 발행에서 매체 3개: 어제 매체 제외 후 후보 2개, 둘 다 어제 매체이면(불가능하지만) 완화 규칙으로 `mediaOrder.getFirst()`.
- `GET /api/mission-templates` 응답 개수·`media` 값 집합 변화(앱이 목록을 캐시하지 않는지 확인 — 앱은 이 API 를 쓰지 않음).
- 활동 요약 `byMedia` 에서 `VOICE` 키 소멸 — 앱(Android `mediaToType`)은 모르는 키를 건너뛰므로 영향 없음.

## Regression Risks

- 선택기 결과 변화로 이미 발행된 그룹의 "내일 미션" 매체가 달라진다(정상). 같은 날 재실행 멱등성은 DB 에 이미 저장된 미션이 있으므로 유지.
- `SubmissionResponse` 레코드 필드 삭제 → 생성 호출부(`SubmissionService`, `SubmissionResponseTests`) 수정 누락 시 컴파일 오류(안전).
- 8단계 변경(`SubmissionService`, `FileTypeDetector` 사용부, 테스트)과 같은 파일을 수정 → 8단계 커밋 후 시작.
- 문서에서 음성을 지우다가 G 유형 설명이 B5(V 유형 결과) 계획과 어긋나지 않게(B5 는 V 유형만 다룸).

## Tests Required

- `MissionTemplateTests`: 카탈로그 13종, `media` 집합 = {PHOTO, DRAW, TEXT, SLIDER}, 풀 7종, `phase()` 규칙.
- `MissionTemplateSelectorTests`: 새 매체 3개 기준 결정성·어제 매체 제외·대상 지정 확률 규칙 불변.
- `FileTypeDetectorTests`: 이미지 시그니처(jpg/png/webp) 유지, 오디오 시그니처 → `null`.
- `SubmissionServiceTests`/MockMvc: 이미지/그림/글 틀 회귀, 오디오 업로드 415, `duration` 필드 400.
- `CalendarServiceTests`: `byMedia` 에 VOICE 없음, 알 수 없는 템플릿(`VOICE_LINE` 행 직접 삽입) 건너뜀.
- `MissionService` 조회: `VOICE_LINE` 행이 있어도 today/단건 조회가 `media = null` 로 성공.

## Validation Steps

`.\mvnw.cmd clean test -q` 통과(테스트 수 기록), `rg -i "voice|audio|waveform|m4a" src/main` → 0건(주석 포함), 문서 `rg` 점검 결과 보고, Postman 파싱·한글 확인, `git diff --check`. 보고서에 "스키마 불변·G 유형 잔재 유지" 와 앱(A0) 선행/후행 순서(서버 먼저 배포해도 구 앱은 음성 미션을 받지 않아 안전)를 명시.

## Potential Merge Conflicts

`SubmissionService`/`SubmissionResponse`/`MissionTemplate`/`MissionTemplateSelector`/`PlaceholderMissionGenerator`(B5·B7·B8 과 순차 — **B0 가 먼저**), `FileTypeDetector`, `CalendarService`, 문서 전반(`MISSIONS_API.md`, `PROJECT_OVERVIEW.md`, `CONTRACT_DECISIONS.md`).
