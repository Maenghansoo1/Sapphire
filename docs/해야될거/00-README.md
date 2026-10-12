# 남은 작업 구현 계획 큐 (알림·배포 제외)

작성: 2026-10-12. 각 계획은 `.agent/plan.md` 형식(Goal/Findings/…/Potential Merge Conflicts)이며 **그 파일만 읽고 구현할 수 있게** 썼다. 실행하려면 해당 파일 내용을 `.agent/plan.md` 에 복사(덮어쓰기)하고 Codex(또는 Claude)에게 한 줄 지시를 보낸다. **`.agent/plan.md` 는 한 번에 한 작업만** 두므로 앞 작업의 보고서/리뷰/커밋이 끝난 뒤 다음을 올린다.

## 전제 / 현재 상태 (이 큐를 쓴 시점)
- 7단계(반응·댓글, 백엔드)는 구현·보고서까지 끝났고 **리뷰 전·커밋 전**. 7A(Android 스티커·댓글)도 구현됨·커밋 전.
- 8단계(닉네임·프로필 사진·탈퇴, 백엔드)는 Codex 구현 중(`.agent/plan.md` = 8단계).
- 이 큐의 모든 항목은 **7·8단계가 커밋된 뒤** 시작한다(`SubmissionService`, `GroupService`, `V1`, `SecurityConfig` 등 겹치는 파일이 많다).
- 제외: **알림(FCM·APNs·알림 목록·알림 설정·기기 토큰)**, **배포(Docker·CI·S3 전환·EventBridge)**. 사용자 지시로 나중에 한다.
- 디자인(Figma) 없는 UI는 **임시 이미지/이모지로 채운다**(사용자 지시). 예: 똥뿌리기 버튼, 후임자 선택 화면, 스티커. 임시 에셋은 `임시` 표기 주석을 달고 나중에 교체하기 쉽게 한 곳에 모은다(5단계 도장 임시 이미지 `stamp_good/late/penalty` 와 같은 방식).
- V1 마이그레이션 직접 수정 + 로컬 DB 재생성이 프로젝트 방식(5·7·8단계). 이 큐의 스키마 변경 계획도 같은 방식이며, 각 계획의 "Implementation Steps 1"에 백업·재생성을 적었다. 공유/운영 DB 에 올리기 전에는 baseline 재정리가 필요하다(배포 단계에서).

## 실행 순서와 의존

| 순서 | 파일 | 내용 | 구현 | 선행 |
|---|---|---|---|---|
| 0 | (직접) | 7단계 리뷰·커밋, 8단계 리뷰·커밋 | 사용자+Claude | — |
| 1 | `B1-group-profile.md` | 그룹 내 프로필(닉네임+캐릭터/사진) API 3개 + 모든 응답에 표시 프로필 적용 | Codex | 0 |
| 2 | `B2-invite-reissue-and-limits.md` | 초대 코드 재발급(그룹장) + 코드 조회 시도 제한 | Codex | 0 (B1 과 동시 금지: `GroupService`) |
| 3 | `B3-password-change-and-token-version.md` | `PATCH /me/password` + 토큰 버전으로 기존 토큰 무효화 | Codex | 8단계(필터의 사용자 조회) |
| 4 | `B4-file-cleanup.md` | 그룹 삭제·나가기·교체 시 고아 파일 정리 | Codex | B1(그룹 프로필 사진 key) |
| 5 | `B5-templates-simple-and-voting.md` | 틀 `TEXT_ONE_WORD`·`TEXT_REVIEW`·`TEXT_BALANCE`·`SLIDER_*` 3종 + 집계 결과 API | Codex | 0 |
| 6 | `B6-guess-templates.md` | 틀 `VOICE_MIMIC`·`VOICE_IMPRESSION` + 정답 시도 API | Codex | B5(제출 검증 구조) |
| 7 | `B7-multi-mission-per-day.md` | 하루 여러 개 미션 개방(상한·슬롯 규칙·도장/집계 기준) | Codex | **결정 필요(아래)**, B5·B6 |
| 8 | `B8-ai-mission-generation.md` | AI 미션 생성기(`MissionGenerator` 구현 + 검증 + 폴백) | Codex | **결정 필요(아래)**, B5·B6(틀 확장 후 프롬프트 계약 확장) |
| A1 | `A1-android-5A-poke.md` | 콕/똥뿌리기 버튼 서버 연결(임시 이미지) | Claude 또는 Codex | 0 |
| A2 | `A2-android-8A-profile-withdraw.md` | 17-1 프로필 편집(닉네임·사진)·17-5 탈퇴 서버 연결 | Claude 또는 Codex | 8단계 |
| A3 | `A3-android-group-2nd.md` | 그룹 내 프로필(16-1)·후임자 선택·코드 재발급·주제 도착 시각·비밀번호 변경 | Claude 또는 Codex | B1·B2·B3 |
| A4 | `A4-android-new-templates.md` | 새 틀 화면(별점 후기·한 단어·밸런스·슬라이더 3종·성대모사/소리 흉내·집계 결과) | Claude 또는 Codex | B5·B6 |
| A6 | `A6-android-multi-mission.md` | 05 화면·홈·수행·콕의 다중 미션 | Claude 또는 Codex | B7, A1·A3·A4 이후(마지막) |
| A5 | `A5-ios-port.md` | iOS 일괄 이식(10·5A·7A·8A·A3·A4·A6) — 묶음 계획 | Mac 환경 필요 | A1~A4, A6 |
| D | (직접) | 문서 정리(`CONTRACT_DECISIONS` §0 상태, `CONTRACT_WORK_PLAN` 진행 표시, `PROJECT_OVERVIEW` §10) | Claude | 각 단계 완료 시 |

병렬 가능: **B 계열(서버)과 A 계열(Android)** 은 서로 다른 디렉터리라 동시에 진행할 수 있다(서버 단계가 앱 단계보다 한 칸 앞서 가면 된다). 서버끼리는 **B1·B2·B4(GroupService/GroupMapper)** 와 **B5·B6·B7·B8(mission/submission)** 두 묶음으로 묶여 있어 묶음 안에서는 순차, 묶음 사이는 병렬 가능(단 `SubmissionService` 를 건드리는 B4 와 B5/B6 는 겹치므로 순차).

## 결정이 필요한 것 (계획에는 아래 **기본값**을 가정으로 적어 두었다 — 다르게 정하면 알려 달라)

| # | 질문 | 영향 계획 | 기본값(계획의 가정) |
|---|---|---|---|
| 1 | 하루 여러 개 미션을 MVP 에서 열까? 열면 상한은? | B7 | 연다. 상한 5(`hawe.mission.max-daily-count`), 슬롯 간 최소 간격 없음 |
| 2 | 도장 기준(하루 여러 개일 때) | B7 | **미션별 도장** — 달력(`CalendarMapper`/`Stamp.of`)이 이미 미션 단위로 계산하므로 현행 유지. `stampedToday`(멤버 목록)는 "하나라도" 유지 |
| 3 | 피드 잠금 단위 | B7 | 미션별 유지(해당 미션을 제출해야 그 미션 피드 열람). 하루 단위로 풀지 않음 |
| 4 | 멤버 목록 `submissionCountThisMonth` 의미 | B7 | "제출한 날 수"로 변경(여러 개일 때 의미 일치) — 앱이 쓰지 않으므로 영향 작음 |
| 5 | AI 제공자·호출 방식 | B8 | Claude API(Anthropic Messages API)를 `RestClient` 로 직접 호출, 모델 ID 는 설정값(`hawe.mission.ai.model`, 기본 `claude-haiku-5-5`), API 키는 환경변수 `ANTHROPIC_API_KEY`. 키가 없으면 자동으로 `PlaceholderMissionGenerator` 폴백. (Spring AI 는 쓰지 않음 — Boot 4.1 호환 미확인) |
| 6 | `TEXT_FILL_BLANK`(사람 지정 빈칸)의 안전 정책(MISSIONS_API §8) | B8 | 이미 풀에 포함돼 있음 → 프롬프트 계약에 "긍정적·가벼운 문장만" 을 명시하고 서버 검증에 금칙어 목록(설정 파일)을 둔다. 신고/삭제 수단은 만들지 않음 |
| 7 | `SLIDER_CONSENSUS` 승자 판정 해석 | B5 | 문서 해석대로: `guess` 와 실제 평균의 차이가 가장 작은 멤버(동률이면 모두 승자) |
| 8 | 정답 맞히기 시도 횟수·별칭 최대 개수 | B6 | 3회, 별칭 5개 |
| 9 | 그룹 내 프로필 닉네임이 기본 닉네임과 중복되는 것 | B1 | 허용(유니크 아님) |
| 10 | 비밀번호 변경 시 다른 기기 로그아웃 | B3 | 한다(토큰 버전 증가). 변경 요청을 보낸 기기는 새 토큰을 응답으로 받는다 |

## 앱 쪽 권장 순서 (Android)
A1(콕) → A2(프로필·탈퇴) → A3(그룹 2차) → A4(새 틀) → A6(다중 미션). 같은 파일(`DataStore`, `MainNavigation`, `*Repository`)을 계속 바꾸므로 순차 진행. 7A(스티커·댓글)는 이미 구현되어 커밋만 남음.

## 직접 처리하는 문서 정리 (Claude, 코드 변경 없음)
- `CONTRACT_DECISIONS.md` §0 상태: "결정 완료 (반영 전)" → 실제 반영 상태로.
- `CONTRACT_WORK_PLAN.md`: 단계별 진행 표시(✅/🔶/⬜) 추가.
- `PROJECT_OVERVIEW.md` §10: 8단계 계획에 포함됨(Codex).
