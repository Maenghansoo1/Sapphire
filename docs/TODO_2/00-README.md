# TODO_2 — AI 신규 기능 작업 단위

근거: [`docs/FEATURE/AI_FEATURES.md`](../FEATURE/AI_FEATURES.md) (2026-11-09 초안). 담당자는 정하지 않았다.
각 파일은 계획 형식(목표/배경/구현 요점/변경 파일/제약/엣지/테스트/검증/충돌)이며 **그 파일만 읽고 구현할 수 있게** 썼다. 한 번에 한 작업만 진행한다.

> 번호 체계: 여기서 **A/B/C/D 는 기능 묶음**이다(플랫폼이 아님). `docs/해야될거/` 의 옛 A(Android)·B(백엔드) 번호와는 별개이므로 이름이 겹쳐 보여도 섞지 않는다. 대화·커밋에서 헷갈리면 `TODO_2/A1` 처럼 폴더를 붙여 부른다.

| 묶음 | 기능 (AI_FEATURES.md) | 서버 변경 | 파일 |
|---|---|---|---|
| **A** | §1 실시간 AI 판단 (Quick, Draw! 방식) | 없음 | A1~A4 |
| **B** | §2 AI 미션 검색 | 있음 (검색 API, LLM 질의 확장) | B1~B6 |
| **C** | §3 얼굴 자동 블러 (선택적 모자이크) | 없음 | C1~C4 |
| **D** | §4 멀티 암드 밴딧 (틀 선택 최적화) | 있음 (선택기, 통계, 잡, 리포트) | D1~D6 |

## 단위 목록

| ID | 파일 | 내용 | 규모 | 선행 |
|---|---|---|---|---|
| A1 | `A1-realtime-core.md` | 연속 추론 상태 로직(디바운스·안정화·연속 판정), 순수 Kotlin | M | — |
| A2 | `A2-android-live-guess-ui.md` | 그리는 중 추론 연동·말풍선·맞힘 연출 | M | A1 |
| A3 | `A3-calibration-and-eval.md` | 임계 보정·수동 평가·범주 확장 판단 | S | A2 |
| A4 | `A4-ios-port.md` | iOS(Core ML) 이식 | M | A2, Mac |
| B1 | `B1-search-core-and-permissions.md` | 검색 API 코어(Nori 매칭) + **권한 필터** | M | — |
| B2 | `B2-query-expansion-llm.md` | LLM 질의 확장·캐시·제한·폴백 | M | B1 |
| B3 | `B3-android-search-screen.md` | Android 검색 화면 | M | B1 (B2 없이도 동작) |
| B4 | `B4-eval-and-docs.md` | 적중률 평가표·문서 갱신 | S | B2, B3 |
| B5 | `B5-embedding-stage2.md` | (선택) 임베딩 2단계 | L | B4 결과가 부족할 때만 |
| B6 | `B6-ios-search-port.md` | iOS 검색 화면 이식 | M | B3, Mac |
| C1 | `C1-precheck-exif-and-fit-impact.md` | EXIF 제거 확인, B10 점수 영향 측정 | S | — |
| C2 | `C2-blur-engine.md` | 모자이크 엔진(순수 로직) | M | — |
| C3 | `C3-android-detect-and-edit-ui.md` | ML Kit 검출 + 편집 UI | L | C1, C2 |
| C4 | `C4-ios-port.md` | iOS(Vision) 이식 | L | C3, Mac |
| D1 | `D1-simulator-and-seed.md` | 시뮬레이터 + AI 합성 시드(전역 prior) | L | — |
| D2 | `D2-schema-and-reward.md` | 스키마·보상 계산 | M | — |
| D3 | `D3-apply-job.md` | 닫힌 미션 반영 잡·감쇠 | S | D2 (D1 의 prior 파일은 선택) |
| D4 | `D4-selector-integration.md` | 선택기에 Thompson Sampling 통합(스위치 기본 off) | M | D2, D3 |
| D5 | `D5-report-extension.md` | B9 리포트 확장(정책 층화·IPS) | S | D4 |
| D6 | `D6-docs-and-notice.md` | 문서·고지 | S | D4 |

규모: S ≤ 0.5일, M ≈ 1~2일, L ≈ 3일 이상(한 사람 기준 대략치).

## 권장 순서

위험이 낮고 체감이 큰 것부터: **C → A → B → D**. 묶음끼리는 독립이라 병렬 가능하나 아래 충돌을 지킨다.

```
00 결정 확정 ─┬─ C1·C2 → C3 → C4
              ├─ A1 → A2 → A3 → A4
              ├─ B1 → B2·B3 → B4 → (B5) · B6
              └─ D1 ∥ D2 → D3 → D4 → D5 · D6
```

## 먼저 정할 결정 (AI_FEATURES.md §6)

| # | 질문 | 영향 단위 | 기본값 |
|---|---|---|---|
| 1 | 실시간 판정용 범주 확장(재학습) | A3 | 유지, A3 측정 후 판단 |
| 2 | 검색: 질의 확장(A안) vs 임베딩 | B2, B5 | 질의 확장 → 필요 시 임베딩. Qdrant 미도입 |
| 3 | 블러 기본 모드·갤러리 기본·댓글/프로필 적용 | C3 | `PHOTO_GALLERY` 만 "모두 블러", 나머지는 탭. 댓글·프로필 제외 |
| 4 | 그림 틀 `effort` 지표 사용 | D2 | 사용 안 함(engage 만) |
| 5 | 합성 prior 관계별 여부(→ `MISSIONS_API.md` §6-1 규칙 4 변경) | D1, D4 | 관계 무관 전역 prior |
| 6 | 한 틀 최대 점유율 | D4 | 7일 창 40% |
| 7 | 시뮬레이터 위치 | D1 | `ml/bandit-sim/` |
| 8 | 합성 시드 생성 방식 | D1 | 오프라인 1회 생성 → JSON 커밋 |
| 9 | 약관/처리방침 문구 | D6 | 팀 확정 |

## 겹침·순서 주의

| 영역 | 겹치는 단위 | 조치 |
|---|---|---|
| 마이그레이션 | B1(보조 테이블 선택 시), D2 | 최신 `V*` 번호(현재 `V4__ai_mascot.sql` 까지 확인됨)를 확인해 새 파일로 추가하거나 팀 방식(V1 직접 수정 + 로컬 DB 재생성)을 따른다. 둘 다 하면 먼저 끝난 쪽이 푸시·알림 |
| 비동기/스케줄 | D3, B5 | 공통 방식은 `docs/FEATURE/IMPROVEMENTS.md` §1(아키텍처 조사)과 연결 |
| `AnthropicClient` | B2 | 프롬프트 개선(`IMPROVEMENTS.md` §2)이 같은 클래스를 바꿀 수 있음 — 조율 |
| `MissionTemplateSelector`, `MissionPublisher` | D4 | 다른 미션 발행 작업과 동시 수정 금지 |
| Android `build.gradle.kts`/`libs.versions.toml` | C3 | 다른 Android 의존성 작업과 순차 |
| `DrawMissionScreen.kt`, `feature/draw/*` | A2 | 다른 그림 화면 수정과 순차 |
| `PhotoMissionScreen.kt` | C3 | 다른 사진 화면 수정과 순차 |

## 공통 제약 (모든 단위)

- 커밋/푸시 금지. `CLAUDE.md`·`AGENTS.md` 수정 금지. 계획에 없는 리팩터링 금지.
- **LLM/외부 AI 에 사용자 콘텐츠(글·댓글·사진·닉네임·그룹 이름·대상 신원)를 보내지 않는다.** (`B8` 결정 #11)
- **기기 안 처리 데이터(그림·사진)는 서버·로그·분석에 남기지 않는다.**
- 잠금 규칙(내가 제출하지 않은 미션의 피드는 423) 우회 금지.
- 디자인이 없는 UI 는 임시 에셋 + `임시` 주석.
- 서버 테스트는 실제 LLM 을 부르지 않는다(`pom.xml` surefire 설정으로 `hawe.mission.ai.enabled=false`).
- 빌드/테스트: 백엔드 `./mvnw test`(Java 25), Android `android\gradlew.bat :app:testDebugUnitTest --offline`(JDK 21, `set JAVA_HOME=C:\Users\kimch\.jdks\ms-21.0.10`).

## 사용자가 직접 해야 하는 일 (코드 밖)

- 결정 1~9 확정, 개인정보 처리방침 문구 확정.
- C3/C4 수동 검수용 사진(동의된 것) 수집 — 저장소 비커밋.
- A3 그림 그려 보기, B4 평가 질의 30개 작성.
- Figma 확정 시 임시 에셋 교체.
