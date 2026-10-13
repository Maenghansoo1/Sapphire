# A0. Android — 음성 기능 완전 제거

> 범위: **Android 앱만**(`android/`). iOS 는 A5 항목 0. 사용자 결정: 음성 기능을 아예 제거한다. 서버 쪽은 B0. **앱과 서버의 선후는 자유롭다**: 서버가 먼저 `VOICE_*` 발행을 멈추면 구 앱은 음성 화면을 쓸 일이 없고, 앱이 먼저 제거돼도 서버가 `media: "VOICE"` 미션을 내려줄 때 05 화면이 안전하게 처리(아래)하기만 하면 된다. 구현자: Claude 또는 Codex. 다른 A 계획(A1·A2·A3·A4·A6)보다 **먼저** 실행한다(`MissionType` 의 exhaustive `when` 을 줄이는 작업이라 뒤 계획의 충돌을 줄인다).
> 빌드/테스트: JDK 21 — `set JAVA_HOME=C:\Users\kimch\.jdks\ms-21.0.10` 후 `android\gradlew.bat :app:testDebugUnitTest --offline`.

## Goal

음성 미션 수행(녹음)·재생·파형·마이크 권한·음성 관련 모델/문구/테스트를 모두 제거한다. 앱의 미션 유형은 `DRAW` / `PHOTO` / `WRITE` 3개가 된다(A4 가 `SLIDER` 를 추가).

## Findings

- 음성 관련 파일/코드(`rg -i "voice|audio|waveform|RECORD_AUDIO|HIcon.MIC"` 기준):
  - 화면/컨트롤러/로직: `feature/mission/VoiceMissionScreen.kt`, `VoiceRecorderController.kt`, `VoiceLogic.kt`, `feature/results/VoicePlayerController.kt`; 테스트 `feature/mission/VoiceLogicTest.kt`.
  - 모델: `core/model/MissionType.kt` 의 `VOICE("voice","음성",HIcon.MIC,"음성 녹음")`; `core/model/Submission.kt` 의 `duration`, `waveform`; `core/data/DataStore.kt` 의 `SubmissionContent.Voice`, 음성 시드; `core/data/DemoSeed.kt` 의 음성 목업.
  - 네트워크: `core/net/SubmissionApi.kt`(`SubmissionDto.duration/waveform`), `SubmissionRepository.kt`(`voiceDurationParam`, `waveformParam`, `Voice` 파트 직렬화, `templateType` 의 `VOICE_`), `MissionRepository.kt`(`mediaToType("VOICE")`).
  - 결과 화면: `feature/results/ResultsScreen.kt`(`MissionType.VOICE -> VoiceList`, `player`, `VoicePlayerController` 수명주기, "전체 이어 듣기"), `SubmissionDetailScreen.kt`(`MissionType.VOICE` 분기, `player`, 재생 버튼), `ResultComponents.kt`(`VoiceBars`), `ResultsFormat.kt`(`hintText` 의 VOICE 분기, `voiceProgress`, `shortDurationText`).
  - 05/수행 라우팅: `feature/mission/MissionPerformScreen.kt`(`MissionType.VOICE -> VoiceMissionScreen`), `MissionFormat.kt`(음성 문구 있을 수 있음).
  - 시작 화면 일러스트: `feature/groups/StartScreen.kt:43` `MissionRow(MissionType.WRITE, MissionType.VOICE)`.
  - 디자인 토큰/아이콘: `designsystem/tokens/HColors.kt` `waveformIdle`, `designsystem/assets/HIcon.kt` `MIC` (+ `res/drawable/ic_mic.xml`). `HIcon.PLAY/PAUSE` 는 음성 화면(재생)에서만 쓰인다(`rg` 로 확인 — 쓰는 곳이 모두 음성이면 함께 제거, 아니면 유지).
  - 권한: `AndroidManifest.xml` 의 `android.permission.RECORD_AUDIO`.
  - 테스트(음성/VOICE 케이스 포함): `core/net/ApiClientTest.kt`, `CalendarRepositoryTest.kt`, `MissionRepositoryTest.kt`, `SubmissionRepositoryTest.kt`, `feature/mission/MissionFormatTest.kt`, `feature/records/RecordCalendarLogicTest.kt`, `feature/results/ResultsFormatTest.kt`, `DataStoreTest.kt`.
- `MissionType` 의 exhaustive `when` 은 40곳 이상 — `VOICE` 를 지우면 컴파일 오류가 곧 수정 목록이다.
- 서버(B0 이후)는 `media: "VOICE"` 를 더 이상 발행하지 않지만, **구 데이터/비재생성 환경**에서는 올 수 있다. 앱의 `mediaToType` 는 모르는 매체에 `null` 을 반환하고 달력은 `mapNotNull` 로 건너뛰며, 05 화면은 `mission.type == null` 인 미션을 이미 "수행 불가"로 다룬다(`MissionScreen.kt` 의 `mission.type != null` 가드).
- iOS 는 `Info.plist`(`project.yml`)에 `NSMicrophoneUsageDescription` — A5 항목 0 에서 처리.

## Root Cause / Key Implementation Points

1. **삭제 순서(컴파일 오류 기반)**: `MissionType.VOICE` 와 `SubmissionContent.Voice` 를 먼저 삭제 → 컴파일 오류 지점(모델 → 레포지토리 → 화면 → 테스트)을 따라 제거. 마지막에 미사용이 된 파일·리소스 삭제.
2. **삭제 대상 파일**: `VoiceMissionScreen.kt`, `VoiceRecorderController.kt`, `VoiceLogic.kt`, `VoicePlayerController.kt`, `VoiceLogicTest.kt`, `res/drawable/ic_mic.xml`(`HIcon.MIC` 와 함께), 필요 시 `ic_play.xml`/`ic_pause.xml`(`HIcon.PLAY/PAUSE` 사용처가 음성뿐일 때만. 단 A8 의 AI 그림 맞히기 UI 가 재생 아이콘을 쓸 계획이 없으므로 제거해도 무방).
3. **수정 대상**: 
   - `MissionType`: `VOICE` 삭제.
   - `Submission`: `duration`, `waveform` 삭제(+ 생성부). `SubmissionDto`: 두 필드 삭제. `SubmissionRepository`: `voiceDurationParam`, `waveformParam`, `Voice` 직렬화, `templateType` 의 `VOICE_` 분기 삭제 → **서버가 모르는 `VOICE_*` 제출을 내려줘도 `templateType == null` 이라 `mapNotNull` 로 목록에서 제외**(크래시 없음).
   - `MissionRepository.mediaToType`: `"VOICE"` 분기 삭제(→ `null`).
   - `DataStore`: `SubmissionContent.Voice` 삭제, 음성 시드/목업(`DemoSeed`) 제거, 관련 상태 정리.
   - `ResultsScreen`/`SubmissionDetailScreen`/`ResultComponents`/`ResultsFormat`: 음성 분기·`player`·`VoicePlayerController` 수명주기 `DisposableEffect`·`VoiceBars`·`voiceProgress`·`shortDurationText`(음성 전용이면) 삭제. `hintText(type)` 는 VOICE 분기 제거(남은 3개 유형의 힌트 유지).
   - `MissionPerformScreen`: `VOICE` 분기·`onSubmit(Voice)` 삭제.
   - `StartScreen`: 시작 화면 미션 예시 행(`MissionRow(WRITE, VOICE)`)을 `MissionRow(WRITE, DRAW)` 등 남은 유형으로 교체(레이아웃 유지, 임시 조합).
   - `HColors.waveformIdle`, `HIcon.MIC` 삭제.
   - `AndroidManifest.xml`: `RECORD_AUDIO` 권한 삭제(앱이 마이크를 쓰는 곳이 없어짐 — `CAMERA` 는 유지). 마이크 관련 `uses-feature`가 있으면 삭제.
   - 설정/문구: 알림 설정 화면(`NotificationSettingsScreen`)의 음성 언급 없음 확인. 온보딩/소개 문구에 "음성" 단어가 있으면 수정(`rg -n "음성|녹음|목소리"`).
4. **구 서버/구 데이터 안전성**: 05 화면에서 `type == null` 미션을 만나면 기존 가드가 수행 버튼을 숨기므로, 여기에 **임시 안내 문구**("이 미션은 지금 앱에서 지원하지 않아요")를 보여 주는 한 줄을 추가한다(이미 비슷한 문구가 있으면 재사용). A4 의 "구형 앱 안내"(`supportsTemplate`)와 같은 분기를 쓰므로, A4 를 먼저 하지 않았다면 이 계획에서 최소 구현(`type == null` 일 때 문구)만 한다.
5. **디자인 토큰 정리**: 파형 색 외에 음성 전용 토큰이 없는지 확인(`rg`), 있으면 함께 삭제.

## Files / Functions to Change

위 Key Point 2·3 의 파일 목록. 신규 파일 없음(StartScreen 일러스트는 기존 컴포넌트 재사용).

## Implementation Steps

1. `MissionType.VOICE`, `SubmissionContent.Voice` 삭제 → 컴파일 오류 따라 수정. 2. 음성 전용 파일·리소스 삭제. 3. 결과 화면의 `when` 분기·수명주기 정리. 4. 매니페스트·시작 화면·문구. 5. 테스트 수정(음성 케이스 삭제, "VOICE 매체/템플릿을 서버가 내려줘도 건너뜀" 케이스 추가). 6. `android\gradlew.bat :app:testDebugUnitTest --offline` 통과. 7. `rg -i "voice|audio|waveform|RECORD_AUDIO|ic_mic|음성|녹음" android/app/src` 잔여 0건(라이선스 파일의 폰트 문구 등 무관한 매치는 제외).

## Constraints

- `android/` 만 수정. 다른 유형(사진/그림/글) 동작 불변. 사진 권한(CAMERA) 불변. 새 라이브러리 금지. 서버 필드를 앱이 요구하지 않는 방향이므로 서버 계약 변경 없음. 커밋/푸시 금지.
- iOS/백엔드 수정 금지.

## Edge Cases

- 서버가 아직 `VOICE_*` 미션/제출을 내려주는 환경(구 서버): 05 는 안내 문구, 결과 피드는 해당 제출을 건너뜀(참여 인원 수와 목록 길이 불일치 가능 — 허용), 달력은 건너뜀. 크래시 금지(테스트로 고정).
- 사용자가 이전 버전에서 녹음 중이던 임시 파일: 앱 캐시 디렉터리에 남을 수 있으나 삭제 코드 불필요(OS 가 정리).
- 마이크 권한을 이미 허용한 사용자: 매니페스트에서 선언이 사라져도 문제없음.
- 시작 화면 일러스트의 미션 행 높이/정렬이 유형 아이콘 변경으로 어긋나지 않는지.
- 접근성: 삭제된 화면의 `contentDescription` 문자열 리소스가 `strings.xml` 에 남아 있다면 함께 삭제.

## Regression Risks

- `MissionType` 의 `when` 연쇄 수정 중 분기 삭제 실수로 다른 유형 로직이 바뀔 수 있음 → 기존 테스트(`ResultsFormatTest`, `RecordCalendarLogicTest`, `MyPageLogicTest`, `DataStoreTest`) 통과가 안전망.
- 마이페이지 활동 요약(`ActivityStats.byType`) 이 유형 4개 고정 리스트를 그리면 음성 행이 사라져야 함(`MyPageLogic`).
- `DemoSeed`/`DataStore` 의 목업 시드에 음성이 섞여 있어 `DataStoreTest` 기대값이 바뀜 → 테스트 수정 허용(음성 제거가 의도).
- 앱 용량 감소 외 다른 영향 없음.

## Tests Required

- 수정: 위 Findings 의 테스트 파일에서 음성 케이스 삭제/치환.
- 추가: `MissionRepositoryTest` — `media = "VOICE"` 미션 → `type = null` 로 파싱·크래시 없음; `SubmissionRepositoryTest` — 서버가 `template = "VOICE_LINE"` 제출을 내려줘도 피드 목록에서 제외; `CalendarRepositoryTest` — `media = "VOICE"` 행 제외(기존 동작 확인).
- 화면은 컴파일 + 수동 확인(시작 화면, 05, 결과).

## Validation Steps

`android\gradlew.bat :app:testDebugUnitTest --offline`(JDK 21) 통과 기록, 위 `rg` 결과 보고, APK 매니페스트에서 `RECORD_AUDIO` 없음(`rg` 로 확인) 보고. 서버 연동 수동 확인은 사용자(미션 한 번 수행·결과 열람).

## Potential Merge Conflicts

`MissionType.kt`·`DataStore.kt`·`ResultsScreen.kt`·`SubmissionDetailScreen.kt`·`SubmissionRepository.kt`·`MissionRepository.kt`(모든 A 계획과 겹침 — **A0 가 가장 먼저**), `AndroidManifest.xml`, `StartScreen.kt`.
