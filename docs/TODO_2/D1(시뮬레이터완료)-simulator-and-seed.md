# D1. 멀티 암드 밴딧 — 시뮬레이터 + AI 합성 시드(전역 prior)

> 범위: 신규 `ml/bandit-sim/`(Python) + 시드 산출물 JSON. **서버·앱 코드 변경 없음.** 선행 없음(D2~D4 와 병렬 가능; D4 가 이 단위의 prior JSON 을 선택적으로 읽는다). 근거: `docs/FEATURE/AI_FEATURES.md` §4.3·§4.4·§4.7, §6 결정 5·7·8.

## Goal

1. **오프라인 시뮬레이터**: 합성 환경에서 `random` vs `bandit(합성 prior 없음)` vs `bandit(합성 prior 있음)` 의 누적 후회(regret)·평균 참여율 곡선을 재현 가능하게 계산한다. 알고리즘 파라미터(ε, 감쇠, κ, 반복 패널티) 선택의 근거가 된다.
2. **AI 합성 시드**: LLM 으로 (틀 × 상황) 시나리오를 만들어 **전역 prior(팔별 의사 관측 α, β)** JSON 을 한 번 생성해 저장소에 커밋한다. 런타임에 LLM 을 부르지 않는다.

## Findings

- 팔(arm) = 미션 틀. 기본 발행 풀 7종: `PHOTO_NOW`, `PHOTO_GALLERY`, `PHOTO_COLOR`, `DRAW_SKETCH`, `DRAW_A_TO_B`, `TEXT_ACROSTIC`, `TEXT_FILL_BLANK`. 매체 3종(PHOTO/TEXT/DRAW)의 틀 수는 3/2/2. 실제 선택은 서버의 **매체 규칙 안에서** 일어난다(D4 참고): 하루 1개면 어제 매체를 제외한 매체의 틀들 중, 하루 2개 이상이면 슬롯이 정한 매체 안의 틀들 중.
- 보상 정의(AI_FEATURES.md §4.2, D2 에서 서버가 계산): 멤버 `r_i ∈ [0,1]` — 미제출 0, 제출 `0.6 + 0.2·effort + 0.2·engage`. 미션 단위 `R = Σ r_i`, `n = 대상 인원`. 갱신은 `α += R`, `β += n − R`.
- 계층 prior: 전역 prior(관계 무관, 결정 5 기본값) → 그룹별 posterior 는 전역을 `κ`(의사 관측 수, 초안 10)로 수축.
- 합성 데이터는 LLM 의 **믿음**이지 실제 사용자 행동이 아니다 → prior 로만 쓰고 영향력을 `K_syn ≤ 10`(팔당)으로 제한, 실측이 쌓이면 감쇠(`K_syn·exp(−N_real/N0)`). 문서·발표에서 "검증된 결과"로 말하지 않는다.
- 규칙 4(관계별 선호·가중치 없음)는 확정이므로, 관계 유형(`COUPLE/FAMILY/FRIEND/RIVAL/CASUAL`)은 **시드 생성의 다양성 소스**로만 쓰고 최종 prior 는 관계 평균 하나로 만든다.
- 사용자 데이터는 시드 생성 입력에 쓰지 않는다(관계 유형과 틀 설명만).

## Key Implementation Points

1. **시뮬레이터** (`ml/bandit-sim/`):
   - `env.py`: 그룹 N개(예: 200), 각 그룹은 팔별 숨은 평균 보상 `μ_g,a` 를 전역 평균 주변에서 샘플(그룹 간 이질성), 멤버 수 2~8, 미션마다 멤버별 베르누이 참여 × 정성 노이즈로 `r_i` 생성. 선택 제약 모사: 매체 순환/어제 매체 제외(AI_FEATURES.md §4.6 규칙) — 서버와 같은 후보 구성 함수를 파이썬으로 재구현하고 서버 `MissionTemplateSelector` 의 의미와 일치하는지 주석으로 근거 명시.
   - `policies.py`: `random`, `epsilon_greedy`(비교용), `thompson_hier`(계층 TS + 탐색 하한 ε=0.05 + 반복 패널티 ×0.85 + 시간 감쇠 반감기 30일 + 합성 prior K_syn).
   - `run.py`: 시드 고정, 반복 실험(≥ 30 시드), 지표 — 누적 후회, 평균 보상/참여율 곡선, 상위 팔 점유율, 틀 점유율 상한 위반 여부. 결과 CSV·PNG 를 `ml/bandit-sim/out/`(비커밋)에 저장하고 요약 표를 `REPORT.md` 에 남긴다.
   - 시나리오 3종: 이질성 낮음/중간/높음, 신규 틀 추가(prior 없는 팔), 선호 변화(비정상성).
   - `K_syn=0` vs `K_syn=10` 의 초기 N 일 평균 참여율 비교(합성 prior 의 기여도), **합성 prior 가 틀렸을 때**(실제 분포와 반대로 편향)의 피해도 시뮬레이션해 상한 `K_syn` 정당화.
2. **합성 시드 생성** (`seed/generate_seed.py`, 오프라인 1회):
   - 입력: (관계 5종 × 틀 7종 = 35 조합) 각각에 대해 LLM 에 페르소나(멤버 수·친밀도·활동 성향)를 주고 "이 틀을 냈을 때 멤버별 참여 확률과 제출 시 정성 수준(0~1)"을 JSON 으로 받는다. 조합당 여러 번(예: 5회) 샘플링해 분포 평균·분산을 구한다.
   - 변환: 조합별 평균 보상 `m` 과 분산으로 팔별 의사 관측을 만든다 — `α_syn = K_syn_a·m`, `β_syn = K_syn_a·(1−m)` (`K_syn_a` ≤ 10). 관계는 평균내어 **관계 무관 팔별 prior 하나**로 합친다.
   - 산출물: `ml/bandit-sim/seed/synthetic_prior.json` — `{ "version":1, "generatedAt":..., "model":..., "kSyn":10, "arms": { "PHOTO_NOW": {"alpha":..,"beta":..}, ... }, "isSynthetic": true }`. 이 파일은 **저장소에 커밋**한다(재현성, 런타임 호출 없음). 원시 LLM 응답은 커밋하지 않는다(비용·잡음).
   - 합성임을 JSON 안과 README 에 명시한다.
3. **품질 점검**: 생성된 prior 의 팔 간 평균 차이가 비정상적으로 크지 않은지(예: 최상위/최하위 팔 평균 차 ≤ 0.2) 확인하는 sanity 테스트와, 어떤 팔도 `α+β > K_syn` 이 아님을 확인하는 테스트.
4. README: 재현 명령 1줄(`python run.py --scenario all --seeds 30`), Python 버전·라이브러리 버전 고정(`requirements.txt`), LLM 키는 `.env` 로만(저장소 비커밋).

## Files / Functions to Change

| 파일 | 변경 |
|---|---|
| `ml/bandit-sim/{README.md, REPORT.md, requirements.txt, env.py, policies.py, run.py, tests/}` (신규) | 시뮬레이터 |
| `ml/bandit-sim/seed/{generate_seed.py, synthetic_prior.json}` (신규) | 합성 prior 생성·산출물 |
| `.gitignore` | `ml/bandit-sim/out/`, `.venv/`, 원시 응답 캐시 |

서버/앱 코드는 이 단위에서 수정하지 않는다.

## Constraints

- 사용자 데이터·실제 미션 기록을 입력으로 쓰지 않는다. LLM 에는 관계 유형과 틀 설명만 보낸다.
- 합성 데이터를 실측과 섞어 "효과가 있다"는 근거로 쓰지 않는다 — REPORT.md 에 한계(§4.4-4)를 그대로 적는다.
- 키/원시 응답 커밋 금지. 파이썬·라이브러리 버전 고정.
- 커밋/푸시 금지.

## Edge Cases

- LLM 응답이 JSON 이 아니거나 범위 밖(확률 > 1) → 검증 후 재시도, 끝내 실패한 조합은 중립 prior(0.5, `K=2`) 로 대체하고 보고서에 표시.
- 틀 목록이 바뀜(새 틀 추가) → prior JSON 에 없는 팔은 서버가 Beta(1,1) 로 시작(D4 가 처리). 생성 스크립트는 틀 목록을 인자로 받는다.
- 시뮬레이션 난수 시드 고정, 병렬 실행 시 결과 재현성.

## Tests Required

- pytest: 정책별 선택 제약 준수(어제 매체 제외, 매체 순환), 탐색 하한(최소 선택 비율 ≥ ε/후보수), 시드 고정 재현성, prior JSON 스키마/범위(α,β>0, K ≤ 10), Beta 갱신 산식(손계산 값과 비교).

## Validation Steps

`pytest ml/bandit-sim/tests` 통과, `python run.py --scenario all --seeds 30` 결과(누적 후회 곡선, 요약 표)를 `REPORT.md` 에 기록, `K_syn` 오편향 시나리오 결과와 채택한 `K_syn` 근거 기재. `synthetic_prior.json` 의 `kSyn`·팔 목록·생성 모델이 문서화됨.

## Potential Merge Conflicts

신규 디렉터리라 낮음. `.gitignore` 만 겹칠 수 있음.
