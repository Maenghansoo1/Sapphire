# 계약 반영 1차 / 6A단계: Android 제출 · 결과(피드) 서버 연결

> 범위: **Android 앱만** (`android/`, `docs/FRONTEND/ANDROID.md`). 백엔드 6단계(제출·피드·파일 저장소)는 구현·리뷰 완료 상태이며 이 계획은 그 계약을 소비한다. 백엔드(`src/`), iOS(`ios/`), `frontend/` 는 건드리지 않는다. 4A(미션 서버 연결)가 작업 트리에 **미커밋일 수 있다** — 4A 의 `MissionRepository`/`DataStore.applyCurrentMission`/`StoredMission.serverMembers` 등이 이미 있다는 전제로 그 위에 쌓는다(되돌리거나 다시 쓰지 말 것).

## Goal

가입 → 그룹 → 오늘 미션 → **제출 → 결과 확인**이 실제 서버로 동작한다.
- 사진/그림/음성/글을 서버(`POST /api/missions/{id}/submissions`, multipart)로 올린다. 올리기 중 로딩, 실패 시 입력 보존 + 재시도, 오류 코드별 안내.
- 결과 화면(`Results`)과 상세(`SubmissionDetail`)가 서버 피드(`GET /api/missions/{id}/submissions`) 데이터로 그려진다. 사진/그림은 서버 URL 이미지, 음성은 서버 URL 재생, 글은 서버 텍스트. 지각 제출은 "지각" 표시.
- 앱 안 임시 제출 로직(로컬 파일 저장, 제출 오버레이, 로컬 좋아요/댓글)을 제거한다. 서버에 아직 없는 **스티커(좋아요)·댓글 UI 는 숨긴다**(7단계에서 추가). 목업과 서버 데이터를 섞지 않는다.

## Findings

백엔드 계약(확정, 구현 완료 — `docs/BACKEND/MISSIONS_API.md` §3·§4 참고):

- `POST /api/missions/{missionId}/submissions` → 201 `SubmissionResponse`. `multipart/form-data`, **허용 필드만**(그 외 이름은 400 `INVALID_SUBMISSION`):
  - 사진 `PHOTO_*`/그림 `DRAW_*`: `file`(JPEG/PNG/WebP, ≤10MB), 선택 `caption`(앱은 안 보냄)
  - `VOICE_LINE`: `file`(m4a 등, ≤5MB), `duration`(초, **0 < d ≤ 30**, 소수 허용), `waveform`(JSON 배열 문자열, 각 0~1, **최대 64개**) — 셋 모두 필수
  - `TEXT_FILL_BLANK`/`TEXT_ACROSTIC`: `text` 만(≤100 grapheme / 정확히 3줄·줄마다 `params.word` 글자로 시작). `file`·`caption` 보내면 400.
- 서버는 파일 종류를 **파일 내용(magic bytes)** 으로 판정한다. 파일명/Content-Type 은 신뢰하지 않지만 올바른 값을 보내는 게 좋다(`image/jpeg`, `image/png`, `audio/mp4`).
- 응답 `SubmissionResponse`(생성·피드 항목·상세 동일 모양, 쓰지 않는 필드는 **키 자체가 없음**):
  ```json
  { "id": 100, "missionId": 10,
    "author": { "userId": 3, "nickname": "서연", "characterKey": "mulbangul", "profileImageUrl": null },
    "template": "VOICE_LINE",
    "mediaUrl": "/files/submissions/3f2a....m4a", "mediaType": "AUDIO",
    "duration": 9.4, "waveform": [0.25, 0.42], "caption": null, "text": null,
    "reactions": [], "commentCount": 0,
    "submittedAt": "2026-09-28T21:00:00+09:00", "isLate": false }
  ```
  `mediaUrl` 은 **루트 상대 경로**(`/files/...`)다. 앱이 `API_BASE_URL` 을 앞에 붙여 절대 URL 로 만든다. 파일은 **인증 없이** GET 가능(Authorization 헤더 불필요).
- `GET /api/missions/{id}/submissions?page=0&size=20` → `{content:[SubmissionResponse], page, size, totalPages, totalElements}`. 정렬 `submittedAt` 오름차순. `size` 1..100 보정. 내가 아직 제출 안 했으면 **423 `FEED_LOCKED`**. 비멤버 403, 없는 미션 404.
- 오류 코드(JSON `{code,message}`): `MISSION_CLOSED`(409, 다음 미션이 열려 닫힘), `ALREADY_SUBMITTED`(409), `FEED_LOCKED`(423), `INVALID_SUBMISSION`(400, `message` 에 사유), `UNSUPPORTED_FILE_TYPE`(415), `FILE_TOO_LARGE`(413), `NOT_GROUP_MEMBER`(403), `MISSION_NOT_FOUND`(404), `SUBMISSION_NOT_FOUND`(404).
- 지각(`isLate`)이어도 마감~다음 미션 발행 전까지는 제출 가능(201).

현재 Android 코드(조사 결과):

- `core/net/ApiClient.kt`: JSON 전용. `execute(method, path, bodyJson: String?, auth)` 가 **고정으로 `Content-Type: application/json` 헤더를 설정**하고(`send`), 401 `TOKEN_EXPIRED` 시 single-flight 재발급 후 같은 `bodyJson` 으로 재시도. `val baseUrl` 은 private. 타임아웃 connect 10s/read 20s/write 20s(업로드에는 짧음). multipart 지원 없음.
- `core/data/DataStore.kt`: `SubmissionContent`(Image(bytes, ext)/Text/Voice(File, duration, waveform)). `submit()` 이 **로컬 UUID 제출을 만들고 파일을 캐시에 저장**(`saveMedia`, `mediaDir`). `StoredMission.submissions`(로컬 제출 보관), `serverMembers`/`serverSubmittedByMe`(4A, 서버값). `todayMission()` 의 `isDone`·`summaries` 의 `submittedCount/didSubmit` 이 "서버값 + 로컬 제출 오버레이"(4A 임시 설계, 코드에 `6A 에서 서버 제출로 교체` 주석). `result()` 는 `mission.submissions.any { isMine }` 일 때만 결과 반환. `toggleLike/addComment/deleteComment/poke` 로컬 구현.
- `core/model/Submission.kt`: `Submission(id: UUID, author, isMine, type, createdAt, mediaURL: URI?, text, duration, waveform: List<Float>, likeCount, likedByMe, likerNames, comments, authorID)`, `MissionResult(id: Long, groupName, type, topic, date, memberCount, submissions, pending)`.
- 화면: `ResultsScreen`(사진 피드/그림 그리드/글 카드/음성 목록, 좋아요·댓글 수 표시, `store.result()` 가 null 이면 빈 화면), `SubmissionDetailScreen`(좋아요, 댓글 목록, `CommentInput`, 삭제 다이얼로그), `ResultComponents.kt`(`PhotoArtwork`/`DrawingArtwork` 은 Coil `AsyncImage`, `LikeButton`), `VoicePlayerController`(`MediaPlayer`, **로컬 `file:` 만 재생**, `prepare()` 동기, `playingID: UUID?`).
- `feature/mission/*Screen`: `WriteMissionScreen`(`onSubmit(String)`, `submitted` 플래그를 true 로 만들고 **복구하지 않음**), `PhotoMissionScreen`/`DrawMissionScreen`(`scope.launch { try { onSubmit(bytes) } catch { submitted=false } }`, 동기 `onSubmit`), `VoiceMissionScreen`(`onSubmit(File, Double, List<Float>)`, 호출 직전 `submitted=true`, 복구 없음; 호출 값 `controller.elapsed`·`levels.filter { it >= 0f }`). `MissionPerformScreen` 이 유형별 화면을 고른다.
- `app/MainNavigation.kt`: `MissionPerform` 라우트에서 `store.submit(content, id)` → `previousBackStackEntry.savedStateHandle["submitted"]=true` → `popBackStack()`. `Results(groupId, missionId: Long)`, `SubmissionDetail(groupId, missionId: Long, submissionId: String)` 라우트(진입 시 `store.result(...)==null` 이면 pop). 푸시용 `PushGroupSnapshot(id, lastMissionId, lastMissionSubmittedByMe)` 가 `last.submissions.any { isMine }` 로 만들어짐. Home 은 진입할 때마다 `groups.loadAll()` 로 목록(+`todayMission` 요약 `submittedCount/submittedByMe`)을 다시 불러온다.
- `core/push/PushModel.kt`, `feature/records/*`, `core/model/DayRecord.kt`: 로컬 미션 기록 기반(캘린더는 4A 에서 "준비 중" 처리되어 진입 불가).
- 이미지 로딩: `libs.coil.compose`(coil3) **만** 있고 네트워크 페처(`coil-network-okhttp`)가 **없다** → http URL 이미지가 로드되지 않는다. 의존성 추가 필요. 디버그 빌드는 `src/debug` 의 `network_security_config`(cleartext 허용) 덕분에 `http://10.0.2.2:8080` 가능; 릴리스 `usesCleartextTraffic=false`.
- `VoiceRecorderController`: 파일 `mission-voice.m4a`(MPEG_4/AAC) — 서버 판정과 호환. 최대 30초에서 자동 정지하지만 `elapsed` 가 30.0 을 **약간 넘을 수 있다**(→ 서버 400). 파형 `VOICE_BAR_COUNT = 32`(≤64 OK).
- 테스트: `DataStoreTest`(제출·좋아요·댓글·오버레이 테스트 다수 → 재작성 필요), `PushModelTest`, `ResultsFormatTest`, `RecordCalendarLogicTest`, `MissionPerformCommonTest` 등. 서버 계층 테스트는 MockWebServer(`MissionRepositoryTest`, `InMemoryTokenStore` 참고).

## Root Cause / Key Implementation Points

1. **서버가 제출/피드의 단일 출처.** 앱은 (a) 제출을 서버로 보내고 (b) 결과 화면 진입 시 피드를 받아 `DataStore` 의 현재 미션에 저장한다. 로컬 제출 생성·오버레이를 없앤다.
2. **`ApiClient` multipart 지원.** `postMultipart<T>(path, parts)` 신설. 요구사항:
   - JSON 용 `Content-Type: application/json` 헤더를 **multipart 에서는 설정하지 않는다**(OkHttp `MultipartBody` 가 boundary 포함 Content-Type 을 직접 정함). `send` 를 `RequestBody` 팩토리 기반으로 일반화하되 기존 JSON 호출 동작은 동일해야 한다.
   - 401 `TOKEN_EXPIRED` 재발급 후 재시도 시 **본문을 다시 보낼 수 있어야** 한다(바이트 배열/파일 기반 `RequestBody` 는 재사용 가능; 요청마다 새 `MultipartBody` 를 만드는 팩토리를 넘겨도 됨).
   - 업로드용 타임아웃은 더 길게(예: write/read 60s) — 기본 `OkHttpClient` 를 `newBuilder()` 로 파생해 업로드에만 사용.
   - 절대 URL 만들기: `fun absoluteUrl(path: String): String`(= `baseUrl.trimEnd('/') + path`) 공개.
3. **`SubmissionRepository`(신규, `core/net/SubmissionRepository.kt` + `SubmissionApi.kt` DTO).**
   - `suspend fun submit(missionId: Long, content: SubmissionContent, myUserId: Long): Submission` — `SubmissionContent` 별 파트 구성:
     - `Image(bytes, ext)`: 파트 `file`, 파일명 `photo.jpg`/`drawing.png`(ext 에 따라), 미디어 타입 `image/jpeg`/`image/png`. **다른 파트 없음.**
     - `Text(body)`: 폼 필드 `text` 만.
     - `Voice(file, duration, waveform)`: 파트 `file`(`voice.m4a`, `audio/mp4`), `duration`, `waveform`.
       - `duration` = `Locale.ROOT` 로 포맷한 소수 문자열, **`coerceIn(0.1, 30.0)`** (예 `"9.40"`; 서버는 30.0 허용, 30.01 거부).
       - `waveform` = 값 `coerceIn(0f,1f)`, **64개 초과 시 64개로 균등 축소**, JSON 배열 문자열(예 `[0.25,0.42]`). 비어 있으면 `[]`.
   - `suspend fun feed(missionId: Long, myUserId: Long): List<Submission>` — `GET /api/missions/{id}/submissions?page=0&size=100`. (그룹 최대 6명이라 한 페이지로 충분.)
   - DTO(`@Serializable`, `ignoreUnknownKeys` 기존 설정): `SubmissionDto(id: Long, missionId: Long, author: AuthorDto, template: String, mediaUrl: String? , mediaType: String?, duration: Double?, waveform: List<Float>?, caption: String?, text: String?, submittedAt: String, isLate: Boolean)`. `isLate` 키 이름 주의(`@SerialName` 필요 없음, 프로퍼티명이 `isLate` 면 그대로). 알 수 없는 키(`reactions`, `commentCount`)는 무시.
   - 변환: `Submission.id = Long`, `author = Profile(AvatarContent.Character(ProfileCharacter.fromKey(characterKey)), nickname)`(기존 `GroupRepository.toMember` 와 같은 방식 재사용), `isMine = author.userId == myUserId`, `type = 템플릿 → MissionType`(서버 `template` 의 `PHOTO_`/`DRAW_`/`VOICE_`/`TEXT_` 접두 또는 `mediaType`/필드 존재로 매핑; `mediaToType` 은 `media` 문자열용이라 템플릿 접두 매핑 함수를 새로 둔다. 알 수 없으면 해당 항목을 건너뛴다), `createdAt = OffsetDateTime.parse(submittedAt).toInstant()`, `mediaURL = mediaUrl?.let { URI(api.absoluteUrl(it)) }`, `isLate`.
4. **모델 변경(`core/model/Submission.kt`).** `Submission.id: UUID → Long`, `isLate: Boolean = false` 추가. 임시 좋아요/댓글 필드(`likeCount`, `likedByMe`, `likerNames`, `comments`)와 `SubmissionComment` 는 서버에 없으므로 **제거**(7A 에서 서버 모양으로 다시 추가). `authorID` 는 `author` 에 userId 가 없으므로 유지(= 서버 `author.userId`).
5. **`DataStore` 정리.**
   - 제거: `submit()`, `saveMedia`/`mediaDir` 관련(테스트/프리뷰 생성자 파라미터가 필요하면 최소 유지), `toggleLike`, `addComment`, `deleteComment`, `updateSubmission`, 제출 오버레이(4A) 로직 및 `// 6A 에서 서버 제출로 교체` 주석. `poke` 는 5단계 대상이라 그대로 둔다.
   - 추가: `applyFeed(groupID, missionID, submissions: List<Submission>)` — 현재 미션 id 가 일치할 때만 `submissions` 를 교체하고 `feedLoaded=true`; `applySubmitted(groupID, missionID, submission)` — 제출 성공 응답 반영: `serverSubmittedByMe=true`, `serverMembers[me]=true`, `submissions` 에 추가(중복 id 방지), 그룹 `summary` 가 같은 미션이면 `submittedCount+1`·`submittedByMe=true`.
   - `StoredMission`: `feedLoaded: Boolean = false` 추가. 새 미션(`applyCurrentMission` 에서 id 가 바뀜)이면 `submissions` 비우고 `feedLoaded=false`. 같은 id 면 기존 `submissions`/`feedLoaded` 유지(현재도 같은 id 면 유지하는 동작 확인).
   - `todayMission()` 의 `isDone`: `serverMembers[id] == true || (member.isMe && serverSubmittedByMe)` (로컬 제출 항목 제거). `summaries` 의 `didSubmit`/`submittedCount`: **요약(`summary`)만** 사용(오버레이 제거).
   - `result(groupID, missionID)`: 미션이 존재하고 `serverSubmittedByMe || feedLoaded` 인 경우 반환하되, **피드가 로드되지 않았으면 로딩 상태를 구분**할 수 있어야 한다(예: `feedLoaded=false` 면 null 반환 + 화면은 별도 상태로 로딩/실패를 표시). `pending` = 그룹 멤버 중 제출자(authorID)가 아닌 사람.
   - `stats`/`records`: 사용처가 없어졌거나(마이페이지 요약 숨김, 캘린더 준비 중) 로컬 제출 필드를 쓰면 컴파일이 깨지지 않게 최소 수정(필요하면 `records` 는 빈 목록 반환 대신 기존 로직 유지가 불가능하므로 제거 가능 — 호출처 확인 후 판단, 불필요한 대규모 삭제는 하지 말 것).
   - `DemoSeed` 는 **삭제하지 않는다**(테스트·프리뷰 용). 모델 변경에 맞게 컴파일만 맞춘다(샘플 제출의 좋아요 필드 제거, id 는 `Long` 로컬 음수 id).
6. **제출 흐름(`MainNavigation` MissionPerform 라우트 + `MissionPerformScreen`).**
   - 각 유형 화면의 `onSubmit` 을 **`suspend (…) -> Boolean`**(true = 화면을 닫아도 됨, false = 실패→입력 유지·재시도 가능)으로 바꾼다. 화면 안에서 `submitted`/로딩 플래그는 호출 중 true, 실패(false) 시 false 로 되돌린다(Write/Voice 도 `rememberCoroutineScope` 로 호출). 호출 중에는 올리기 버튼 비활성 + 진행 표시(스피너/문구), 뒤로가기·닫기는 허용하되 진행 중 요청은 취소하지 않거나 취소 시 서버 상태를 신뢰(아래 멱등 처리).
   - 라우트가 `SubmissionRepository.submit` 을 호출하고 결과를 **순수 함수로 분류**한다(테스트 대상): `classifySubmitFailure(e): SubmitOutcome`
     - 성공: `store.applySubmitted(...)`, `savedStateHandle["submitted"]=true`(+ 지각이면 `"submittedLate"=true`), pop. 지각이면 토스트 "지각으로 올렸어요", 아니면 기존 "오늘의 미션을 올렸어요".
     - `ALREADY_SUBMITTED`(409) → **성공으로 간주**(이전 요청이 서버에 도달했으나 응답을 못 받은 재시도 케이스): 미션 새로고침을 유발하고(`savedStateHandle["refresh"]=true` 등) pop, 토스트 "이미 올렸어요".
     - `MISSION_CLOSED`(409) → 입력은 버려지지만 안내: pop + 토스트 "새 미션이 열려서 마감됐어요", 05 가 미션 재조회.
     - `INVALID_SUBMISSION`(400) → 서버 `message` 를 토스트로, 화면 유지(재시도 가능).
     - `FILE_TOO_LARGE`(413) → "파일이 너무 커요", `UNSUPPORTED_FILE_TYPE`(415) → "지원하지 않는 파일이에요", 화면 유지.
     - `NOT_GROUP_MEMBER`(403)/`MISSION_NOT_FOUND`(404) → Home 으로 pop + 목록 재조회.
     - `NetworkException` → "네트워크 연결을 확인해 주세요", 화면 유지(입력 보존).
     - 그 외 → 일반 실패 안내, 화면 유지.
   - `NetworkException` 이후 사용자가 같은 입력으로 다시 누를 수 있어야 하며, 서버가 이미 받았다면 위 `ALREADY_SUBMITTED` 처리로 수렴한다.
   - 현재 `performEntryDecision`/`didSubmit` 가드는 그대로(서버값 기반으로 이미 동작).
7. **결과 화면 서버 연결.**
   - `ResultsScreen` 은 `SubmissionRepository` 를 받아 진입(및 앱 복귀 `ON_RESUME`)마다 피드를 불러 `store.applyFeed`. 상태: 로딩(빈 화면 또는 스피너), 실패(재시도 화면 — `MissionLoadFailed` 와 같은 구성의 "결과를 불러오지 못했어요 / 다시 시도"), 성공. **423** 이면 05 로 pop + 미션 재조회 유도 + 토스트 "먼저 미션을 올려 주세요". 403/404 → Home 으로.
   - 진입 가드(`MainNavigation` Results/SubmissionDetail 의 `LaunchedEffect`): `store.result()==null` 이면 무조건 pop 하던 것을, **미션이 없거나(id 불일치) 내가 제출하지 않았을 때만** pop 하도록 바꾼다(피드 미로딩 상태에서 튕기지 않게).
   - `SubmissionDetailScreen` 은 `store` 에 로드된 피드에서 해당 제출을 찾는다(별도 상세 API 호출 없음). 피드가 비어 있거나 제출이 없으면 pop. 라우트 `SubmissionDetail.submissionId: String → Long`.
   - 지각 표시: 결과 항목(사진 피드 작성자 행, 글 카드, 음성 행, 그림 그리드, 상세 부제)에 `isLate` 이면 작은 "지각" 칩/문구. 문구는 상수로 한 곳에 둔다.
   - **숨김(7단계 전)**: 좋아요 버튼/수, 좋아요한 사람 문구, 댓글 수/목록/입력창/삭제 다이얼로그, 글 카드의 "좋아요·댓글 n". `hintText` 의 "댓글을 남겨보세요" 문구도 제거("눌러서 자세히 보기" 등으로). 순수 헬퍼(`likersText`, `commentSendEnabled`, `commentPlaceholder`, `koreanRelativeText`)는 7A 에서 재사용하므로 **삭제하지 않는다**.
8. **이미지/음성 재생.**
   - `gradle/libs.versions.toml` + `app/build.gradle.kts`: `coil-network-okhttp`(`io.coil-kt.coil3:coil-network-okhttp`, version.ref = coil) 추가. 이로써 `AsyncImage(model = "http://…")` 가 동작. (새 의존성 다운로드가 필요하다 — 오프라인이면 보고서에 명시.)
   - `ResultComponents.kt` 의 `imageModel`: 절대 http(s) URL 이면 문자열 모델, `file:` 은 기존 분기 유지(테스트/프리뷰 호환). 로딩 전/실패 시 기존 플레이스홀더 텍스트 유지.
   - `VoicePlayerController`: `playingID: Long?`. `mediaURL.scheme` 이 http/https 이면 `MediaPlayer().setDataSource(url)` + **`prepareAsync()`** + `OnPreparedListener` 에서 `start()`·`player` 할당·틱 시작, `OnErrorListener`/`OnCompletionListener` 에서 다음 곡/정지. 준비 중 `stop()`/다른 항목 재생이 오면 이전 `generation` 콜백은 무시(기존 `generation` 패턴 유지). `file:` 재생은 기존 동작 유지. 준비 전에는 진행률을 제출의 `duration` 으로 근사하던 기존 틱 로직이 오작동하지 않게 한다.

## Files / Functions to Change

신규 (`android/app/src/main/java/com/sapphire/hawe/`):
- `core/net/SubmissionApi.kt`(DTO), `core/net/SubmissionRepository.kt`(`submit`, `feed`, 파라미터 생성 순수 함수 `voiceDurationParam`, `waveformParam`, 템플릿→`MissionType` 매핑, 실패 분류 `classifySubmitFailure`/`SubmitOutcome` — 위치는 `core/net` 또는 `feature/mission`, 한 파일에 모아 테스트하기 쉽게).

수정:
- `core/net/ApiClient.kt` — `postMultipart`, `absoluteUrl`, JSON 헤더 분기, 업로드 타임아웃, 재시도 가능한 본문.
- `core/model/Submission.kt` — `Submission`(id Long, `isLate`, 좋아요/댓글 필드 제거), `SubmissionComment` 제거.
- `core/data/DataStore.kt` — 위 5번. (`SubmissionContent` 는 그대로 유지: 화면과 레포지토리가 공유.)
- `core/data/DemoSeed.kt` — 컴파일 맞춤.
- `app/AppViewModel.kt` — `val submissions = SubmissionRepository(api)`; `app/RootScreen.kt` → `MainNavHost(..., submissions)` 전달.
- `app/MainNavigation.kt` — `MainNavHost` 파라미터에 `submissions`; MissionPerform 제출 흐름, Results/SubmissionDetail 가드·시그니처, 라우트 `SubmissionDetail.submissionId: Long`, 푸시 스냅샷(`last?.serverSubmittedByMe`), 05 가 `"submittedLate"`/`"refresh"` 신호 처리(필요 시 `MissionScreen` 파라미터).
- `feature/mission/MissionPerformScreen.kt`, `WriteMissionScreen.kt`, `PhotoMissionScreen.kt`, `DrawMissionScreen.kt`, `VoiceMissionScreen.kt` — `onSubmit` suspend/Boolean, 진행 표시, 실패 시 재활성화, 토스트 호스트(Draw 는 이미 있음; 나머지는 공통 래퍼에서 제공해도 됨).
- `feature/mission/MissionScreen.kt` — 지각 제출 토스트 분기(`submittedLate`), `refresh` 신호 시 `refresh++`.
- `feature/results/ResultsScreen.kt`, `SubmissionDetailScreen.kt`, `ResultComponents.kt`, `ResultsFormat.kt`(`hintText`), `VoicePlayerController.kt` — 위 7·8번.
- `core/push/PushModel.kt` — 변경 불필요할 수 있음(이미 `Long` 미션 id). 스냅샷 생성부만 수정.
- `gradle/libs.versions.toml`, `app/build.gradle.kts` — `coil-network-okhttp`.
- `docs/FRONTEND/ANDROID.md` — 제출·결과가 서버 연결됨, 좋아요/댓글은 7A, 로컬 임시 로직 제거 반영.
- 테스트 갱신/신규는 아래 "Tests Required".

## Implementation Steps

1. 테스트 먼저(실패 확인): `SubmissionRepositoryTest`, `ApiClientTest`(multipart), 제출 실패 분류 테스트, 파라미터 변환 테스트, `DataStoreTest` 신규 케이스.
2. `ApiClient`: 요청 본문 팩토리로 일반화 → `postMultipart`·`absoluteUrl`·업로드 클라이언트. 기존 `ApiClientTest` 전부 통과 유지.
3. `Submission` 모델 정리 → 컴파일 에러를 따라 `DataStore`/`DemoSeed`/화면/테스트 수정.
4. `SubmissionApi`/`SubmissionRepository` 구현.
5. `DataStore`: `applyFeed`/`applySubmitted`/`feedLoaded`, 오버레이·로컬 제출·좋아요·댓글 제거.
6. 의존성 `coil-network-okhttp`, `imageModel` 확인.
7. 제출 UI 흐름: 유형별 화면 `onSubmit` suspend/Boolean + 진행 표시 + 재시도, `MainNavigation` MissionPerform 라우트에서 레포지토리 호출·결과 분류·네비게이션.
8. Results/SubmissionDetail: 피드 로딩/실패/423 처리, 진입 가드 수정, 지각 표시, 좋아요·댓글 UI 숨김, `hintText`.
9. `VoicePlayerController`: http 재생(`prepareAsync`), id 타입 `Long`.
10. 푸시 스냅샷·05 신호·문서 갱신. 전체 테스트·빌드.

## Constraints

- Android 만. 백엔드·iOS·`frontend/` 변경 금지. Flyway/서버 계약 변경 금지(계약이 불편해도 앱에서 맞춘다 — 서버 이슈는 보고서에 기록).
- 서버 계약에 없는 필드를 보내지 않는다(특히 글 틀에 `file`/`caption`, 사진 틀에 `duration` 등 금지 → 400).
- 좋아요·댓글·콕·달력·활동 요약은 **숨김/준비 중** 유지. 목업 데이터를 서버 데이터와 섞지 않는다. `TopicPool`/`DemoSeed` 는 테스트·프리뷰 용으로만 남긴다.
- 토큰/로그: 업로드 본문·URL 을 로그에 남기지 않는다. 디버그 빌드의 cleartext 설정은 그대로(릴리스 변경 없음).
- 사용자에게 보이는 문자열은 한국어, 코드/주석은 기존 스타일(주석 최소).
- 커밋·푸시 금지. `.agent/plan.md`·`.agent/review.md` 는 읽기만, 보고서는 `.agent/report.md`.
- Gradle 은 JDK 17 로 실행(기존 보고서 방식): `android\.\gradlew.bat testDebugUnitTest assembleDebug lint assembleRelease --quiet`.

## Edge Cases

- **오프라인/타임아웃 후 재시도**: 서버가 이미 저장했는데 응답을 못 받은 경우 재시도가 409 `ALREADY_SUBMITTED` → 성공으로 취급하고 서버 상태로 수렴(05 재조회). 입력 데이터는 실패 시 그대로 유지.
- **더블탭/중복 제출**: 진행 중에는 버튼 비활성, 라우트 레벨에서도 진행 중 재진입 무시.
- **음성 길이 30초 초과**: `elapsed` 가 30.0x 라도 `coerceIn(0.1,30.0)` 로 보정해 400 을 피한다. 0 에 가까운 길이도 0.1 로 보정.
- **파형 개수/범위**: 32개 기본, 64 초과 시 축소, 음수·NaN 값 방지(`coerceIn`, 비유한 값은 0).
- **MISSION_CLOSED**: 제출 중 새 미션이 열린 경우. 입력은 버려지므로 사용자에게 이유를 알린다. 05 가 새 미션을 보여준다.
- **지각 제출**: 마감 후에도 가능, 결과에서 "지각" 표시. 토스트 분기.
- **423 (결과 진입)**: 로컬은 "제출함"인데 서버는 아님(예: 앱 재설치 직후 로컬 상태 불일치 — 서버 값 기준이므로 드묾). 05 로 되돌리고 재조회.
- **피드 로딩 중 뒤로가기/회전**: 요청 취소 시 상태가 깨지지 않게(`LaunchedEffect` 취소 안전).
- **다른 사용자의 미디어 URL 실패**(파일 삭제/404): 이미지는 플레이스홀더 텍스트 유지, 음성은 에러 리스너에서 정지(앱이 죽지 않음).
- **세션 만료 중 업로드**: `TOKEN_EXPIRED` 재발급 후 같은 본문 재전송이 되어야 한다(테스트). 재발급 실패 시 기존대로 로그아웃.
- **알 수 없는 템플릿/미디어 타입**의 피드 항목은 건너뛴다(앱이 죽지 않음).
- **작성자 캐릭터 키가 null/미지원**: 기존 `ProfileCharacter.fromKey` 의 기본 처리 사용.
- **앱 재시작**: 제출 상태는 서버값(`submittedByMe`/`members`)에서 복구되므로 4A 의 "재시작 시 미제출로 보임" 한계가 해소된다 — 수동 검증 항목.
- **긴 글/이모지**: 글 화면의 기존 grapheme 제한과 서버 제한이 같다(FILL_BLANK 100, ACROSTIC 3줄).

## Regression Risks

- `ApiClient` 리팩터링이 기존 JSON 호출(로그인/그룹/미션)에 영향 — `ApiClientTest`·`AuthRepositoryTest`·`GroupRepositoryTest`·`MissionRepositoryTest` 전부 통과해야 한다. JSON 요청의 `Content-Type: application/json; charset=utf-8` 유지.
- `Submission`/`StoredMission`/`DataStore` 모양 변경이 `DataStoreTest`, `DemoSeed`, `RecordCalendarLogicTest`, `PushModelTest`, 프리뷰에 파급 — 컴파일/테스트 정리 필요(의도된 변경).
- 4A 의 `applyCurrentMission`(같은 id 면 submissions 유지)·`markMissionFailed`·`summaries` 와 새 `applyFeed`/`applySubmitted` 의 상호작용: 미션 재조회가 피드/제출 상태를 지우지 않아야 한다. 새 미션(id 변경)일 때만 비운다.
- `VoicePlayerController` 변경으로 로컬 `file:` 재생(테스트/프리뷰/기존 녹음 미리 듣기 `VoiceRecorderController` 는 별개 클래스) 회귀 금지.
- `MissionPerform` 화면의 뒤로가기·닫기 확인 다이얼로그(`MissionHeader`)가 진행 중 상태와 충돌하지 않게.
- 푸시 → Results 이동(`PushTarget.Results`)은 이제 피드 로딩을 거친다. 스냅샷의 `lastMissionSubmittedByMe` 의미 유지.
- `coil-network-okhttp` 추가로 APK/라이선스 화면(`LicensesScreen`) 영향 여부 확인(별도 라이선스 목록이 하드코딩이면 의존성 언급 필요 여부만 확인, 불필요하면 건드리지 않음).

## Tests Required

JVM 테스트 (MockWebServer/순수):
- `SubmissionRepositoryTest`:
  - 사진: 파트 `file` 하나, 파일명·`image/jpeg`, 다른 폼 필드 없음; 그림 PNG 도 동일(`image/png`).
  - 글: `text` 필드만, `file` 없음.
  - 음성: `file`(`audio/mp4`) + `duration` + `waveform`; 30.4 입력 → `"30.00"`; 0 → `"0.10"`; 70개 파형 → 64개; 범위 밖/비유한 값 보정; 요청 `Content-Type` 이 `multipart/form-data; boundary=…` 이고 JSON 이 아님; `Authorization` 헤더 포함.
  - 응답 매핑: 필드(`id`, `author`→Profile, `isMine`, `type`, `createdAt`, `mediaURL` 절대 URL = baseUrl + `/files/...`, `isLate`, `duration`, `waveform`, `text`), 음성/글/사진 각각; 알 수 없는 키 무시; 알 수 없는 템플릿 항목 건너뜀.
  - `feed`: 경로 `/api/missions/{id}/submissions?page=0&size=100`, 정렬 유지, 423 → `ApiException(423,"FEED_LOCKED")` 전파.
  - 오류: 409 `ALREADY_SUBMITTED`/`MISSION_CLOSED`, 400 `INVALID_SUBMISSION`(메시지 보존), 413, 415 가 `ApiException` 코드·상태로 전달.
- `ApiClientTest` 추가: multipart 요청에 JSON 헤더가 없음; 401 `TOKEN_EXPIRED` → 재발급 1회 후 **같은 multipart 본문 재전송**; 업로드 중 `NetworkException` 변환; `absoluteUrl`.
- 실패 분류 테스트(`classifySubmitFailure`): 위 7가지 결과 매핑 전부(메시지 포함).
- `DataStoreTest`(재작성): `applyCurrentMission` 이 같은 id 면 `submissions`/`feedLoaded` 유지·다른 id 면 비움; `applySubmitted` 가 `todayMission().didSubmit`, `members[me].isDone`, `summaries` 의 `submittedCount/didSubmit` 을 갱신(중복 호출 안전); `applyFeed` 후 `result()` 의 `pending` 계산; 피드 미로딩 시 `result()` 동작(약속한 계약대로); 로컬 오버레이가 없어졌음(서버 `submittedByMe=false` 이면 미제출). 좋아요/댓글 관련 기존 테스트 삭제.
- `PushModelTest`/`MissionPerformCommonTest`/`RecordCalendarLogicTest`/`ResultsFormatTest`: 모델 변경에 맞춰 수정, `hintText` 변경 반영.
- 기존 전체 JVM 테스트 통과(4A 시점 89개 + 신규).

## Validation Steps

1. JDK 17 로 `android\.\gradlew.bat testDebugUnitTest assembleDebug lint assembleRelease --quiet` 통과, 테스트 개수/실패 0 보고.
2. `git diff --check` 통과. 잔여 코드 확인: `rg "toggleLike|addComment|deleteComment|LikeButton|CommentInput|saveMedia|SubmissionComment" android/app/src/main` 에서 숨김 대상이 남아있지 않을 것(`LikeButton` 정의 자체는 7A 재사용을 위해 남겨도 되나 **호출은 없어야** 함 — 남기면 보고서에 명시).
3. 백엔드 로컬 서버(MariaDB, `mvnw.cmd spring-boot:run`) + 에뮬레이터(`http://10.0.2.2:8080`)로 **수동 시나리오**(가능하면 실행, 못 하면 사유와 함께 "미검증"으로 보고):
   1. 회원가입 → 그룹 생성 → (슬롯 발행 시각을 `00:00` 으로 하면 즉시 미션) 05 진입.
   2. 사진/그림/음성/글 각각 제출 → 성공 토스트 → 05 가 "제출함" 상태 → "친구들 작품 보기" → 결과에 내 작품이 서버 URL 로 표시(사진/그림 이미지 로드, 음성 재생, 글 표시), 상세 진입.
   3. 두 번째 계정으로 같은 그룹 참여: 제출 전 결과 진입 불가(버튼이 "작품 올리기"), 제출 후 상대 작품이 보임.
   4. 앱을 완전히 종료 후 재실행 → 제출 상태가 유지(서버 값).
   5. 서버 중단 상태에서 제출 → 네트워크 안내·입력 보존 → 서버 재기동 후 재시도 성공.
   6. DB 에서 마감 시각을 과거로(`UPDATE missions SET deadline_at = NOW() - INTERVAL 1 HOUR WHERE ...`) 바꾼 뒤 제출 → "지각" 표시.
   7. 음성 30초 가득 녹음 제출 성공(400 이 아님), 글 틀: 삼행시/빈칸 제한 위반 시 올리기 비활성.
4. 보고서에: 변경 파일, 테스트 결과(개수), 수동 검증 결과(실제 한 것/못 한 것), 의존성 추가(`coil-network-okhttp`), 남은 로컬 임시 코드(`DemoSeed`, `poke`, `records`, `stats` 등)와 이유.

## Potential Merge Conflicts

- **4A 가 아직 커밋되지 않았다면** 이 작업은 4A 의 `DataStore.kt`, `MainNavigation.kt`, `MissionScreen.kt`, `ResultsScreen.kt`, `SubmissionDetailScreen.kt`, `MissionPerformCommon.kt`, 테스트들을 그대로 이어서 수정한다. 4A 코드를 되돌리지 말고 그 위에서 수정한다(사용자가 4A 와 6A 를 별도 커밋으로 나누려면 6A 전에 4A 를 커밋해야 함 — Codex 는 커밋하지 않는다).
- 병렬 작업 없음(백엔드 6단계는 완료). 7A(스티커·댓글)가 `Submission` 모델과 `ResultsScreen`/`SubmissionDetailScreen` 을 다시 수정하게 된다 — 이번 단계에서 좋아요/댓글 필드를 지우는 대신 7A 에서 서버 모양으로 추가한다.
- iOS 는 이 단계에서 건드리지 않으므로 충돌 없음(iOS 는 별도 후속).
