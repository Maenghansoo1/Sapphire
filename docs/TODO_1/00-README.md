# 남은 작업 구현 계획 (알림·배포 제외)

최종 갱신: 2026-10-12. 각 계획은 `.agent/plan.md` 형식(Goal/Findings/…/Potential Merge Conflicts)이며 **그 파일만 읽고 구현할 수 있게** 썼다. 실행하려면 해당 파일 내용을 `.agent/plan.md` 에 복사(덮어쓰기)하고 Codex(또는 Claude)에게 한 줄 지시를 보낸다. **`.agent/plan.md` 는 한 번에 한 작업만** 두므로 앞 작업의 보고서/리뷰/커밋이 끝난 뒤 다음을 올린다. (이 폴더가 정본이다. `.agent/queue/` 에 남아 있는 같은 이름의 복사본은 **이전 버전**이므로 쓰지 말고 지워도 된다.)

## 담당 분담 (2026-10-12, 10-13 조정)

계획 파일은 담당자 폴더로 옮겼다. 각 폴더의 `00-순서와-참고.md` 에 순서·시작 조건·다른 사람과 주고받는 지점·정할 것이 있다. 아래 표들의 파일 이름은 그대로이고 위치만 바뀌었다.

| 담당 | 폴더 | 영역 | 계획 |
|---|---|---|---|
| 1번 찬희 | `1번_찬희/` | 서버: 그룹·계정·인사이트 (V1 대부분) + Android 그룹·프로필·인사이트 | B1 → B3 → B2 → B10 Spring 연동 → B4 → B11, A2 → A3 → A9 |
| 2번 우준 | `2번_우준/` | AI 전담: 미션·LLM·그림 모델·사진 AI + Android 일부 | B0, M1, B10 비전 서비스(Python) → A0 → A1 → B5 → B7 → B8 → B9 → A4 → A7 → A8 → A6 |
| 3번 경환 | `3번_경환/` | iOS 이식·iOS 테스트 (Android 는 맡지 않음) | A5(iOS 전부, Mac 필요) — Android 가 끝나는 대로 항목별로 이식 |

- B10 은 한 파일(`2번_우준/B10-photo-topic-fit-ai.md`)이지만 우준이 비전 서비스(Python, Key Points 1~6)를, 찬희가 Spring 연동(Key Points 7~15)을 맡는다.
- `SubmissionService` 를 고치는 순서: B0(우준) → B1(찬희) → B5(우준) → B10 연동(찬희) → B4(찬희). 앞 사람이 푸시하면 단톡에 알린다.
- `B6-guess-templates.md` 는 폐기라 이 폴더에 그대로 둔다.
- 2026-10-13: 경환의 Android 중 작거나 중간 크기이고 찬희 작업을 기다리지 않는 **A0·A1·A6·A8 을 우준으로** 옮겼다. iOS 이식·테스트는 전부 경환(팀에서 아이폰이 경환뿐). Android 는 같은 파일을 계속 고치므로 앞 사람이 푸시·알린 뒤 다음 사람이 시작한다: **우준 A0 → 우준 A1 → 경환 A2 → A3 → A4 → A7 → 우준 A8 → 경환 A9 → 우준 A6**.
- 2026-10-13 (다시 조정): 경환이 iOS 를 전부 맡아 Android 가 밀리므로 **A4·A7 도 우준으로** 옮겼다(iOS 이식은 그대로 경환). 서버 쪽 선행(B0·B5)은 이미 끝나 A4 는 바로 시작할 수 있다. 새 Android 순서: **우준 A0 → 우준 A1 → [경환 A2 → A3] 와 [우준 A4 → A7 → A8] 를 나란히 → 경환 A9 → 우준 A6**. A3 와 A4 는 `SubmissionRepository`·`DataStore` 를 함께 고치므로 먼저 끝난 쪽이 바로 푸시하고, 다른 쪽은 pull 해서 합친 뒤 이어 간다.
- 2026-10-15: 경환은 **구현된 Android 를 iOS 로 옮기는 일만** 하기로 해서 경환의 Android **A2·A3·A9 를 찬희로** 옮겼다(파일도 `1번_찬희/` 로 이동). A5(iOS 이식·테스트)는 그대로 경환. 새 Android 순서: **우준 A0 → 우준 A1 → [찬희 A2 → A3] 와 [우준 A4 → A7 → A8] 를 나란히 → 찬희 A9 → 우준 A6**. A3 와 A4 는 `SubmissionRepository`·`DataStore` 를 함께 고치므로 먼저 끝난 쪽이 바로 푸시한다.

## 이번 갱신에서 바뀐 것 (사용자 결정 반영)

| 변경 | 내용 | 영향 문서 |
|---|---|---|
| **음성 기능 완전 제거** | 음성 틀 3종(`VOICE_LINE`, `VOICE_MIMIC`, `VOICE_IMPRESSION`)·음성 제출·재생·녹음·마이크 권한·파형 전부 삭제. 매체는 사진/그림/글(+슬라이더) | 신규 **B0, A0**, A5 항목 0, B5·B7·B8·A4 수정, **B6 폐기** |
| **사진-주제 적합도 AI (CLIP)** | 사진과 주제 문장의 유사도로 "오늘의 주제 찰떡상" 배지·한 줄 코멘트(점수·순위 비노출), 정확도 평가 | 신규 **B10**(백엔드+비전 서비스), **A7** |
| **직접 학습 그림 인식 ("AI가 맞혀볼게요")** | Quick, Draw! 로 CNN 직접 학습·경량화, TFLite/Core ML 로 기기 안에서 추론 | 신규 **M1**(학습 파이프라인), **A8**, A5 항목 9 |
| **그룹 맞춤 LLM 주제 + 효과 측정** | 관계·기념일·지난 기록 신호를 반영한 LLM 주제, "AI 주제 vs 랜덤 주제 참여율" A/B 실험 | **B8 확장**, 신규 **B9** |
| **그룹 활동 인사이트 대시보드** | 가장 빨리 낸 사람·많이 쓰인 단어(형태소 분석)·반응 최다 글·월말 하이라이트·활동 지수, 캐시+비동기 생성 | 신규 **B11**, **A9**, A5 항목 10 |

## 전제 / 현재 상태
- 7단계(반응·댓글)·7A(Android 스티커·댓글)는 커밋된 것으로 보인다. 8단계(닉네임·프로필 사진·탈퇴, 백엔드)는 Codex 구현/리뷰 진행 중일 수 있다 — **이 목록의 모든 항목은 8단계가 커밋된 뒤 시작**한다(`SubmissionService`, `GroupService`, `V1`, `SecurityConfig` 등 겹치는 파일이 많다).
- 제외: **알림(FCM·APNs·알림 목록·알림 설정·기기 토큰)**, **배포(Docker·CI·S3 전환·EventBridge·비전 서비스 운영 배포)**. 사용자 지시로 나중에 한다.
- 디자인(Figma) 없는 UI 는 **임시 이미지/이모지/기호로 채운다**(사용자 지시). 임시 에셋은 `임시` 표기 주석을 달고 `HIcon`(Android)/에셋 카탈로그(iOS) 한 곳에 모은다(10단계 임시 도장 이미지와 같은 방식).
- V1 마이그레이션 직접 수정 + 로컬 DB 재생성이 프로젝트 방식(5·7·8단계). 스키마를 바꾸는 계획(B1·B3·B9·B10·B11)은 Implementation Steps 1에 백업·재생성을 적었다. 공유/운영 DB 에 올리기 전에는 baseline 재정리가 필요하다(배포 단계에서).
- Android 빌드/테스트: JDK 21(`C:\Users\kimch\.jdks\ms-21.0.10`)로 `android\gradlew.bat :app:testDebugUnitTest --offline`. 백엔드: `.\mvnw.cmd clean test -q`(Java 25).

## 실행 순서와 의존

### 서버 (B 계열)

| 순서 | 파일 | 내용 | 구현 | 선행 |
|---|---|---|---|---|
| 0 | (직접) | 8단계 리뷰·커밋 | 사용자+Claude | — |
| 1 | `B0-voice-removal.md` | **음성 제거**(틀 3종·오디오 제출·파형·문서) | Codex | 0 |
| 2 | `B1-group-profile.md` | 그룹 내 프로필 API 3개 + 모든 응답에 표시 프로필 적용 | Codex | 0 |
| 3 | `B2-invite-reissue-and-limits.md` | 초대 코드 재발급 + 코드 조회 시도 제한 | Codex | B1 이후(`GroupService`) |
| 4 | `B3-password-change-and-token-version.md` | `PATCH /me/password` + 토큰 버전 | Codex | 8단계 |
| 5 | `B5-templates-simple-and-voting.md` | 틀 6종(한 단어·별점 후기·밸런스·슬라이더 3종) + 집계 결과 API | Codex | B0, B1 |
| 6 | `B10-photo-topic-fit-ai.md` | 사진-주제 적합도(CLIP 비전 서비스 + 찰떡상 배지) | Codex(Python+Java) | B0, B1 |
| 7 | `B4-file-cleanup.md` | 그룹 삭제·나가기 때 고아 파일 정리 | Codex | B1, B10(`FileStorage.load` 와 같은 인터페이스를 건드림) |
| 8 | `B7-multi-mission-per-day.md` | 하루 여러 개 미션 개방 | Codex | B0, B5 |
| 9 | `B8-ai-mission-generation.md` | **그룹 맞춤** LLM 주제 생성(관계·기념일·지난 기록 신호) + 검증·폴백 | Codex | B7 |
| 10 | `B9-topic-experiment.md` | AI vs 랜덤 주제 참여율 A/B 실험·리포트 | Codex | B8 |
| 11 | `B11-group-insights.md` | 그룹 활동 인사이트(형태소 분석·집계·캐시·비동기) | Codex | B1(권장), B10 의 `AsyncConfig` 와 조율 |
| — | `B6-guess-templates.md` | **폐기**(음성 제거로 G 틀 없음) | — | — |

### 모델 학습 (M 계열) — **리드타임이 길어 서버/앱과 병렬로 가장 먼저 시작**

| 파일 | 내용 | 구현 | 선행 |
|---|---|---|---|
| `M1-quickdraw-model.md` | Quick, Draw! CNN 학습·양자화·TFLite/Core ML 내보내기·평가 | Claude/Codex + **사용자(범주 확정, 평가용 그림 수집, GPU/Colab)** | 없음(독립) |

### 앱 (A 계열)

| 순서 | 파일 | 내용 | 구현 | 선행 |
|---|---|---|---|---|
| 1 | `A0-android-voice-removal.md` | Android 음성 제거 | Claude/Codex | 없음(B0 와 병행) |
| 2 | `A1-android-5A-poke.md` | 콕/똥뿌리기 버튼 서버 연결 | Claude/Codex | A0 |
| 3 | `A2-android-8A-profile-withdraw.md` | 프로필 편집·가입 사진·탈퇴 | Claude/Codex | A1, 8단계 |
| 4 | `A3-android-group-2nd.md` | 그룹 내 프로필·후임자 선택·코드 재발급·주제 시각·비밀번호 변경 | Claude/Codex | A2, B1·B2·B3 |
| 5 | `A4-android-new-templates.md` | 새 틀 6종 화면 + 집계 결과 (2026-10-13 우준) | Claude/Codex | A1, B0·B5 (찬희 A3 와 같은 파일 — 먼저 끝난 쪽이 푸시) |
| 6 | `A7-android-photo-fit-badge.md` | 찰떡상 배지·코멘트 표시 (2026-10-13 우준) | Claude/Codex | A4, B10 |
| 7 | `A8-android-ai-draw-guess.md` | "AI가 맞혀볼게요" 온디바이스 추론 | Claude/Codex | A7, **M1 산출물** |
| 8 | `A9-android-insights.md` | 그룹 인사이트 화면 | Claude/Codex | A3, B11 |
| 9 | `A6-android-multi-mission.md` | 05·홈·수행·콕의 다중 미션 | Claude/Codex | 위 전부, B7 (마지막) |
| 10 | `A5-ios-port.md` | iOS 일괄 이식(항목 0~10) — 묶음 계획 | Mac 환경 필요 | Android 해당 단계 |

병렬 가능: **B 계열(서버)·M1(모델)·A 계열(Android)** 은 서로 다른 디렉터리라 동시에 진행할 수 있다(서버 단계가 앱 단계보다 한 칸 앞서 가면 된다). 서버끼리는 **그룹 묶음(B1·B2·B4)** 과 **미션·제출 묶음(B0→B5→B7→B8→B9, B10)** 으로 나뉜다 — 묶음 안은 순차, 묶음 사이는 병렬. 단 `SubmissionService` 를 건드리는 B0·B5·B10·B4 는 서로 겹치므로 위 표의 순서대로 순차.

### 새 AI 기능의 데이터 흐름 한눈에
- **찰떡상**: 사진 제출 → (커밋 후 비동기) Spring → 비전 서비스(Python, CLIP) → `submission_ai_reviews` → 피드/상세 `aiReview{badge,comment}` → 앱 배지. 점수는 비노출.
- **AI가 맞혀볼게요**: 앱 안에서 그림 → 28×28 전처리 → TFLite/Core ML 추론 → 앱 화면 내 결과. 서버 관여 없음, 저장·전송 없음.
- **그룹 맞춤 주제**: 발행 시 `GroupContextBuilder`(관계·기념일·지난 기록 집계) → LLM → 서버 재검증 → 미션. 출처(`AI`/`AI_FALLBACK`/`PLACEHOLDER`)·실험 군을 `missions` 에 기록 → B9 리포트.
- **인사이트**: 요청 → 캐시 확인 → 없으면 202 + 비동기 생성(월말에는 자동 생성) → 캐시(`group_insights`) → 200.

## 결정이 필요한 것 (계획에는 아래 **기본값**을 가정으로 적어 두었다 — 다르게 정하면 알려 달라)

| # | 질문 | 영향 계획 | 기본값(계획의 가정) |
|---|---|---|---|
| 1 | 하루 여러 개 미션을 MVP 에서 열까? 상한은? | B7, A6 | 연다. 상한 5, 슬롯 간 최소 간격 없음 |
| 2 | 도장 기준(하루 여러 개일 때) | B7 | **미션별 도장**(달력이 이미 미션 단위). `stampedToday` 는 "하나라도" 유지 |
| 3 | 피드 잠금 단위 | B7 | 미션별 유지 |
| 4 | 멤버 목록 `submissionCountThisMonth` 의미 | B7 | "제출한 날 수" |
| 5 | LLM 제공자·호출 방식 | B8 | Claude API 를 `RestClient` 로 직접 호출(모델 `claude-haiku-5-5` 기본, 키는 `ANTHROPIC_API_KEY`), 키 없으면 고정 문장 폴백. Spring AI 미사용 |
| 6 | `TEXT_FILL_BLANK`(사람 지정 빈칸) 안전 정책 | B8 | 풀에 유지 + 프롬프트 "긍정적·가벼운 문장만" + 서버 금칙어 검증. 신고/삭제 수단 없음 |
| 7 | `SLIDER_CONSENSUS` 승자 판정 | B5 | `guess` 와 실제 평균의 차이가 가장 작은 멤버(동률 복수) |
| 8 | G 유형 잔재 스키마(`answer_text`, `answer_aliases`, `submission_guesses`) 정리 | B0, B6 | **유지**(8단계 삭제 쿼리와 충돌 회피, 후속 재사용 가능). 정리 원하면 별도 계획 |
| 9 | 그룹 내 프로필 닉네임 중복 | B1 | 허용 |
| 10 | 비밀번호 변경 시 다른 기기 로그아웃 | B3 | 한다. 요청 기기는 새 토큰 수령(계약 204 → 200 보완) |
| 11 | **LLM 에 보낼 정보 범위** | B8 | **미션 단위 집계만**(관계·기념일·참여율·반응 수·최근 미션 문장). 사용자 글/댓글/사진/닉네임/그룹 이름은 보내지 않음 |
| 12 | 기념일 소스 | B8 | 연인 시작일 100일 단위·주년, 그룹 결성 100일/1년, 설정 가능한 특별한 날(`MM-dd=이름`). 음력 명절·개인 생일은 이번 범위 제외 |
| 13 | A/B 실험 설계 | B9 | 단위 = (그룹, 날짜), `ai-ratio` 0.5, 틀 선택과 독립, 기본 비활성, 닫힌 미션만 분석, 대응 비교(그룹 내) 주 지표 |
| 14 | 실험 리포트 접근 방식 | B9 | 토큰 헤더(`X-Admin-Token`) 보호 내부 엔드포인트 `/internal/experiments/topic`. 관리자 역할 체계는 만들지 않음 |
| 15 | 사진 적합도 모델·배치 | B10 | 별도 Python 프로세스(FastAPI) + 다국어 CLIP(한국어 주제 직접 입력), 판정은 백엔드 임계값. 대상: `PHOTO_NOW`, `PHOTO_COLOR`(갤러리 제외) |
| 16 | 낮은 적합도 표시 | B10, A7 | **표시하지 않음**(긍정 배지·코멘트만, 점수 비노출). 기존 "AI 는 제출을 평가하지 않는다" 원칙을 이렇게 좁혀 수정 |
| 17 | Quick, Draw! 범주(약 50개) | M1 | M1 이 `categories.proposed.md` 후보를 제시 → **사용자 확정** 필요(변경 시 재학습) |
| 18 | 학습 프레임워크·환경 | M1 | TensorFlow/Keras + Colab/GPU(막히면 PyTorch). 학습용 GPU 환경 마련은 사용자 몫 |
| 19 | 인사이트 집계 범위 | B11 | 닫힌 미션만, 단어는 그룹 공통 집계(캐시 가능), 반응 최다 글은 메타데이터만, 형태소 분석 = Lucene Nori(Java 25 호환 확인) |
| 20 | 활동 지수 공식 | B11 | `0.6×참여율 + 0.2×정시율 + 0.2×교류도` ×100 (가중치는 조정 가능) |

### 사용자가 직접 해야 하는 일 (코드 밖)
- **평가 데이터 수집**: 사진-주제 적합도 평가셋(≥200장, 동의된 사진), 앱으로 직접 그린 그림 ≥300장(범주 라벨 포함) — 개인정보/얼굴 포함 시 저장소에 커밋하지 않는다.
- **M1 범주 확정**, **학습 환경(GPU/Colab) 준비**, 모델 카드/라이선스(CC BY 4.0) 문구 확인.
- **개인정보 처리방침/약관 문구**: ① 사진이 서비스 내부 AI(비전 서비스)로 분석됨, ② 주제 생성 방식 비교를 위한 비식별 집계 분석, ③ 그림 인식은 기기 안에서만 처리됨 — 확정 문구는 사용자.
- Figma 디자인이 나오면 임시 이미지(`*_temp`) 교체.

## 직접 처리하는 문서 정리 (Claude, 코드 변경 없음)
- `CONTRACT_DECISIONS.md` §0 상태, `CONTRACT_WORK_PLAN.md` 진행 표시, `PROJECT_OVERVIEW.md` §6(AI 역할 원칙 수정)·§10(진행 상태)·기능 소개 — 각 단계 완료 시 해당 계획의 Documentation 항목과 함께 갱신.
