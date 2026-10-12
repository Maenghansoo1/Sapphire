# Review — 계약 반영 1차 / 6A: Android 제출·결과 서버 연결

대상: 6A 구현(현재 HEAD `6cd6153`에 4A·6단계 클라이언트와 함께 커밋됨) + `.agent/report.md`. 백엔드는 이전 리뷰(승인 가능) 그대로라 제외.
검증 근거: `ApiClient`(multipart/재시도/타임아웃), `SubmissionApi`·`SubmissionRepository`, `DataStore`(applyFeed/applySubmitted/result), `MainNavigation`의 MissionPerform·Results 경로, 네 제출 화면 `onSubmit`, `VoicePlayerController`, `VoiceRecorderController`, `ResultsScreen` 전문을 읽었다. 빌드·테스트는 재실행하지 않고 보고서(JVM 96개 통과, assemble/lint 통과)를 신뢰한다. 실서버 수동 검증은 보고서 자체가 미수행이라고 명시했다.

## 요구사항 충족 확인

| 요구 | 결과 |
|---|---|
| multipart | JSON Content-Type 헤더 제거, 요청마다 본문 빌더 재호출이라 401 재발급 후 재전송 가능, 업로드 전용 60초 타임아웃 |
| 틀별 파트 | 사진/그림 `file`만, 글 `text`만, 음성 `file`+`duration`(0.1~30)+`waveform`(≤64, 0~1) — 서버 허용 필드와 일치 |
| 응답 매핑 | `Submission.id` Long, `isLate`, `mediaUrl` → 서버 origin 결합, 알 수 없는 template 은 건너뜀 |
| 제출 UI | 중복 탭 방지, 실패 시 입력 보존·재시도, 성공 시 `applySubmitted` + 이전 화면에 신호 |
| 오류 분류 | ALREADY_SUBMITTED=성공 취급, MISSION_CLOSED 안내, 413/415/400 메시지, 403/404 홈 복귀, 네트워크 안내 |
| 결과 | 진입·복귀 시 피드 로드, 423 → 05로 복귀 + 안내, 실패 시 재시도 화면, "지각" 표시 |
| 숨김 | 좋아요·댓글 UI 제거(7A 재사용용 `LikeButton`만 유지) |
| 이미지/음성 | `coil-network-okhttp` 추가, 음성 http 재생은 `prepareAsync` + generation 가드 |

논리 버그, 레이스, 파일 정리 문제는 찾지 못했다(음성 파일은 업로드 중 배경 전환에도 보존, 화면 해제 시 삭제).

## P0
없음.

## P1
없음.

## P2

### `feature/results/ResultsScreen.kt:49-65` — 진입 시 피드 이중 호출
#### Problem
`LaunchedEffect(missionID, refresh)` 가 첫 호출을 하고, `DisposableEffect` 에서 옵저버를 등록하면 이미 RESUMED 상태라 `ON_RESUME` 이 즉시 와서 `refresh++` → 같은 피드를 한 번 더 조회한다.
#### Why It Matters
네트워크 1회 낭비, 깜박임 가능성. 기능 결함은 아니다(4A 의 05 화면과 같은 패턴).
#### Recommended Fix
(선택) 첫 resume 은 건너뛰거나 `refresh` 를 `ON_START` 이후에만 증가.

### `core/net/SubmissionRepository.kt` `feed` — 첫 페이지(100개)만 조회
#### Problem
`page=0&size=100` 한 번만 호출한다. 서버 상한도 100.
#### Why It Matters
그룹 정원이 100을 넘을 수 없다면 영향 없음. 정원이 늘어나면 뒤쪽 제출이 잘린다.
#### Recommended Fix
(선택) 정원 상한을 계약에 명시하거나, `totalPages` 만큼 이어 조회.

### 참고 (조치 불필요)
- **수동 검증 미수행**: 가입→그룹→4종 제출→결과→재시작→서버 중단 재시도→지각 시나리오는 실기기 확인이 필요하다. 이번에 폰에 설치된 빌드가 바로 그 대상이다.
- **타임아웃 후 중복 제출**: 서버는 처리했는데 응답을 못 받은 경우, 재시도가 ALREADY_SUBMITTED 로 수렴해 성공 처리된다(설계대로).
- **커밋 분리**: 4A·6(백엔드)·6A 가 한 커밋(`6cd6153`)에 들어갔다. 기능상 문제는 없으나 이력 분리가 필요하면 이미 늦었다.
- 5단계(콕)는 `POKES` 스키마가 이미 있어 백엔드부터 시작 가능하며, 앱은 숨겨 둔 콕 버튼만 켜면 된다.

## 결론

**승인 가능.** 계획 요구사항과 서버 계약을 충족하고 결함은 없다. P2 는 선택이라 Codex 에게 보낼 필수 수정은 없다.
