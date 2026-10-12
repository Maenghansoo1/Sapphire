# Review — 계약 반영 1차 / 6단계(백엔드): 제출 · 피드 · 파일 저장소

대상: 6단계 미커밋 변경(`submission/**` 신규, `global/storage/**`, `WebMvcConfig`, `SecurityConfig`, `BusinessException`/`GlobalExceptionHandler`/`CommonErrorCode`, `MissionMapper.isCurrent`, `MissionErrorCode`, `application.properties`, `.gitignore`, 테스트, `MISSIONS_API.md`·`CONTRACT_DECISIONS.md`, Postman `HAWE_SUBMISSION`) + `.agent/report.md`. `android/**` 변경은 4A 몫이라 제외했다.
검증 근거: `SubmissionService`·`SubmissionController`·`SubmissionMapper`·`FileTypeDetector`·`LocalFileStorage`·`WebMvcConfig` 전문과 나머지 diff, 문서 diff, Postman 한글 깨짐 grep(0건), Android 음성 녹음 형식(`MPEG_4`+`AAC`, `.m4a`)을 읽었다. Maven 테스트와 스모크는 다시 실행하지 않았고 보고서(`clean test` 103개 통과, 실서버 스모크: 미션 수신 → 제출 전 423 → 201 → 파일 200 → 피드 200 → 재제출 409)를 신뢰한다.

## 요구사항 충족 확인

| 요구 | 결과 |
|---|---|
| 검증 순서 | 404 → 403 → `MISSION_CLOSED`(`isCurrent`) → `ALREADY_SUBMITTED` → 템플릿/필드/파일. 계획과 일치 |
| 닫힘 판단 | 시계가 아니라 기존 `CURRENT` 쿼리 재사용(`isCurrent`), 기존 쿼리 변경 없음. 자정 넘는 미션도 정상 |
| 지각 | `now.isAfter(deadlineAt)` — 마감 정각은 지각 아님 |
| 틀별 필드 | 허용 집합 밖 필드·파일 파트 거부, 중복 필드 거부, 글 틀은 `file`/`caption` 불가. FILL_BLANK 100 grapheme, ACROSTIC 3줄·첫 글자(`params.word` 3글자일 때), 음성 `duration`(0<d≤30, 유한)·`waveform`(≤64, 0~1) |
| 파일 | magic bytes 로 종류·확장자 판정, 클라이언트 파일명/Content-Type 미사용, UUID 키, 이미지 10MB·음성 5MB, 이미지↔음성 불일치 415. 저장 후 `afterCompletion` 롤백 정리 + `DuplicateKeyException` 즉시 삭제 |
| 저장 키/URL | DB 에는 키만, 응답은 `publicBaseUrl + "/" + key`(`/files/...`) |
| 응답 | 생성·피드·상세 동일 `SubmissionResponse`, `isLate` 키, `reactions: []`, `commentCount: 0`, `+09:00` |
| 피드/상세 | 미제출자 423, 비멤버 403, 정렬 `submitted_at ASC, id ASC`, size/page 보정 |
| 오류 처리 | `BusinessException` 메시지 생성자(기존 메시지 동일), 누락 파트·비 multipart → 400, JSON 본문 → 415, 기존 413 유지 |
| 공개 서빙 | `GET /files/**` 만 permitAll, `/api/**` 인증 유지, 경로 순회 테스트 있음 |
| 범위 | 스키마·Flyway 변경 없음, Android/iOS 변경 없음, 반응·댓글·알림 훅 없음 |
| 문서/Postman | `MISSIONS_API.md` §3·§4·§10, `CONTRACT_DECISIONS.md` §6 갱신, Postman 9개 요청 UTF-8 |

Android 음성 녹음이 MPEG_4/AAC(`.m4a`)라 서버의 `ftyp` 판정과 호환된다(6A 에서 문제 없음).

로직 결함(경쟁 조건, 트랜잭션·파일 정리, 검증 순서, 권한)은 찾지 못했다.

## P0

없음.

## P1

없음.

## P2

### `global/storage/FileTypeDetector.java` — `ftyp` 컨테이너를 모두 AUDIO 로 판정
#### Problem
바이트 4..8 이 `ftyp` 이면 무조건 `m4a`(AUDIO)다. 영상 mp4/HEIC 도 같은 시그니처라 음성 틀에 영상이, 사진 틀에 HEIC 가 올라오면 각각 `m4a` 로 저장되거나 415 가 된다.
#### Why It Matters
음성은 5MB·길이 값 클라이언트 신뢰이므로 실제 피해는 작다. 앱이 사진을 JPEG, 그림을 PNG 로만 보내므로 정상 흐름에는 영향 없음.
#### Recommended Fix
(선택) `ftyp` 브랜드(`M4A `, `mp42`, `isom`, `M4B ` 등) 화이트리스트. 지금은 조치 불필요.

### `submission/service/SubmissionService.java` `duration` — `Double.parseDouble` 이 느슨함
#### Problem
`"9d"`, `"0x1p3"`, `"1e1"` 같은 문자열도 파싱된다.
#### Why It Matters
값 자체는 유한·범위 검사를 통과해야만 저장되므로 안전. 응답에는 숫자로만 나간다.
#### Recommended Fix
(선택) `[0-9]+(\.[0-9]+)?` 정규식으로 선검사.

### 참고 (조치 불필요)
- **그룹 삭제 시 업로드 파일 고아**: 보고서·계획이 알려진 한계로 명시. 후속 정리 작업 필요.
- **`/files/**` 공개 정적 서빙**: 로컬 공개로 결정한 사항. UUID 파일명, 경로 순회 차단. S3/서명 URL 은 2차.
- **스모크 범위**: 라이브 스모크는 사진 틀만 수행. 음성·그림·글은 MockMvc/통합 테스트로 갈음(보고서 명시). `isLate` 정각 경계는 단위 테스트 기준이며 시계 주입은 없다.
- **MariaDB DATETIME 초 단위**: `submitted_at` 은 초 단위로 저장되므로 같은 초의 피드 정렬은 `id` 로 결정된다(계획 정렬 규칙과 일치).
- **커밋 권장**: 작업 트리에 4A(Android) 변경이 아직 미커밋으로 함께 있다. 6단계(백엔드)와 4A 를 **서로 다른 커밋**으로 나눈 뒤 6A 를 시작할 것.
- 6A 계약: 응답 모양(`author`, `mediaUrl` 루트 상대 경로 → 앱이 서버 origin 을 붙임, `duration`, `waveform`, `isLate`, 423/409 처리)이 이번 구현으로 고정됐다.

## 결론

**승인 가능.** 계획 요구사항과 계약을 충족하고 결함은 없다. P2 는 선택이라 Codex 에게 보낼 필수 수정은 없다.
