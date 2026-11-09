# D6. 멀티 암드 밴딧 — 문서 갱신과 고지

> 범위: 문서만. **선행: D4**(동작 확정). 근거: `AI_FEATURES.md` §4, §6 결정 9.

## Goal

밴딧 도입을 기존 문서와 팀 합의 문서에 반영해 규칙 충돌(특히 "관계별 선호 없음")·실험 해석·개인정보 고지가 빠지지 않게 한다.

## Findings

- `docs/BACKEND/MISSIONS_API.md` §6-1 "틀 선택 규칙": 규칙 1~5. 규칙 4(관계별 선호·가중치 없음)는 확정이므로 밴딧 규칙은 **규칙 2·3 안에서만** 동작함을 명시해야 한다.
- `docs/BACKEND/EXPERIMENT_TOPIC.md` §1: "비교하는 것은 문장뿐이다. 틀·대상·발행 시각은 두 군에서 똑같이 서버가 정한다" — 밴딧 정책이 켜져도 이 전제가 유지되는 이유(두 군에 같은 선택기)와 시간에 따른 틀 분포 이동을 한계로 적어야 한다.
- `docs/BACKEND/BACKEND_DB.md`: 새 테이블/컬럼 반영 필요(`template_bandit_stats`, `template_bandit_applied`, `missions.template_policy`, `missions.selection_prob`).
- 고지: B9 의 고지 문구 방식(`EXPERIMENT_TOPIC.md` §8, **문구 확정은 팀**)과 함께 밴딧 한 줄.

## Key Implementation Points

1. `MISSIONS_API.md` §6-1 에 "밴딧 정책(기본 off)" 절 추가: 후보 구성은 기존 규칙 그대로, 후보 안에서 Thompson Sampling, 입력은 그룹별 실측 + 관계 무관 전역 prior, 관계를 입력으로 쓰지 않음, 설정 키(`hawe.mission.template-policy`, `hawe.bandit.*`), 폴백(통계 조회 실패 시 random), **선택 자유도 한계**(D4 Findings: 하루 2개↑이면 매체 순환이 고정이라 시작 틀만 선택) 포함.
2. `EXPERIMENT_TOPIC.md` 에 "틀 선택 정책과의 관계" 절 추가(전제 유지 근거, 정책별 층화 리포트 안내).
3. `BACKEND_DB.md`: 테이블·컬럼 설명, 삭제 순서(통계·적용 기록을 미션/그룹보다 먼저).
4. `docs/FEATURE/PROJECT_OVERVIEW.md` 기능 소개 한 줄(AI 틀 최적화), `docs/FEATURE/AI_FEATURES.md` §4 를 "구현됨/구현 중"으로 갱신하고 실제 구현과 달라진 점(특히 §4.6 후보 구조 설명)을 정정.
5. 합성 시드 한계 문구를 README/발표 자료용으로 정리: "LLM 이 만든 prior 는 학습 초기 방향만 주고, 효과 주장은 실측으로만 한다."
6. **개인정보 고지(문구 확정은 팀)**: "서비스 개선을 위해 미션 틀 선택 방식을 비교·최적화할 수 있으며, 분석에는 개인을 알아볼 수 없는 집계 수치만 사용합니다." 초안만 제시.
7. 운영 메모: 켜는 법(`HAWE_BANDIT_ENABLED`, 정책 스위치), 끄는 법, 통계를 초기화하는 법(테이블 비우기, 전역 prior 재시드), 모니터링할 로그/지표.

## Files / Functions to Change

`docs/BACKEND/MISSIONS_API.md`, `docs/BACKEND/EXPERIMENT_TOPIC.md`, `docs/BACKEND/BACKEND_DB.md`, `docs/FEATURE/PROJECT_OVERVIEW.md`, `docs/FEATURE/AI_FEATURES.md`, (필요 시) `.env.example` 주석.

## Constraints

- 코드 변경 없음. 규칙 4 를 문서에서 바꾸지 않는다(변경하려면 별도 결정). `CLAUDE.md`/`AGENTS.md` 수정 금지.
- 개인정보 처리방침 최종 문구는 팀이 확정(초안만).
- 커밋/푸시 금지.

## Edge Cases

- 구현이 문서 초안과 달라진 부분(예: 정책 배정 방식, NULL 유니크 처리) → 문서를 구현에 맞춘다.
- 문서 간 설정 키 이름 불일치 방지 → `application.properties` 실제 키로 통일.

## Tests Required / Validation Steps

문서 내 링크·설정 키가 코드(`application.properties`, `BanditProperties`)와 일치하는지 대조. 보고서에 변경한 문서와 구현과의 차이 목록 기록.

## Potential Merge Conflicts

`MISSIONS_API.md`, `PROJECT_OVERVIEW.md`(다른 문서 갱신과 줄 단위 충돌 — 추가 위주로).
