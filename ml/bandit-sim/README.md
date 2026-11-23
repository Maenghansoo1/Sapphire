# bandit-sim — 미션 틀 선택 밴딧 시뮬레이터 (TODO_2/D1)

"참여·정성이 높게 나오는 틀 위주로 내되 다른 틀도 가끔 시도"하는 선택(멀티 암드 밴딧)이 지금의 **무작위 선택**보다
나은지, 어떤 설정이 좋은지를 **합성 환경**에서 미리 비교한다. 서버·앱 코드는 바꾸지 않는다. 결과는 `REPORT.md`.

> 합성 환경의 틀별 참여율은 **가정치**다(`env.py` 의 `BASE_ARMS`). 실제 사용자 데이터는 쓰지 않았고, 결과는
> "이런 환경이라면"의 비교일 뿐 실제 효과를 증명하지 않는다. 실제 효과는 서버 연결(D4) 뒤 실측으로만 말한다.

## 실행

```bash
cd ml/bandit-sim
../quickdraw/.venv/Scripts/python.exe -m venv .venv      # 처음 한 번 (Windows, Python 3.12)
./.venv/Scripts/python.exe -m pip install -r requirements.txt
./.venv/Scripts/python.exe -m pytest -q tests
PYTHONIOENCODING=utf-8 ./.venv/Scripts/python.exe run.py --scenario all --seeds 30   # 약 10분, 결과는 out/
```

- 빠르게 한 번: `run.py --scenario base --seeds 3 --no-sweeps`
- 결과물(`out/`, 저장소 비커밋): `main_summary.md`·`main_summary.csv`(요약 표), `main_curves.csv`(날짜별 누적 후회), `main_regret.png`(그래프), `sweeps.md`(파라미터 비교)
- 같은 명령이면 같은 결과가 나온다(시드 고정, 정책끼리 같은 그룹·같은 난수 흐름).

## 구성

| 파일 | 내용 |
|---|---|
| `env.py` | 합성 그룹(멤버 2~6명, 하루 미션 1~3개, 틀별 숨은 참여율·정성), 서버와 같은 **후보 규칙**(하루 1개: 어제 매체 제외 / 하루 여러 개: 슬롯이 매체를 정함), 시나리오 5종 |
| `policies.py` | `random`(지금 서버), `epsilon_greedy`, `ts_flat`(그룹별 TS), `ts_hier`(계층 TS + 탐색 하한·반복 패널티·점유율 상한·시간 감쇠, 합성 prior 선택) |
| `prior.py` | 합성 prior JSON 형식 검사, 시뮬레이션용 맞는/틀린 prior |
| `run.py` | 시나리오 × 정책 × 시드 반복, 지표 계산, 표·그래프, 파라미터 비교 |
| `tests/` | 후보 규칙, Beta 갱신 손계산, 탐색 하한, 반복 패널티, 점유율 상한, 재현성, prior 형식 |

## 보상과 갱신 (서버 D2·D3 와 같은 식)

- 멤버 보상: 안 냄 0, 냄 `0.6 + 0.2·effort + 0.2·engage` (0~1). 미션 보상 합 `R = Σ r_i`, 대상 인원 `n`.
- 틀(팔)마다 Beta: `α += R`, `β += n − R` — 분수 보상이라 근사다(AI_FEATURES.md §4.8). 미션은 다음 미션이 열릴 때 닫히므로 그날 끝에 반영한다.

## 합성 시드(AI 로 만든 출발값)

`seed/synthetic_prior.json` 은 아직 없다. LLM 에 (관계 5종 × 틀 6종) 시나리오를 물어 팔별 의사 관측을 만드는 단계로,
LLM 을 약 175번 불러야 해서 따로 승인을 받고 돌린다. 파일이 생기면 `run.py` 가 자동으로 `ts_hier_llm` 정책을 함께 비교하고,
`tests/test_prior.py` 가 형식(α, β > 0, α+β ≤ kSyn ≤ 10)과 팔 간 차이(≤ 0.2)를 검사한다. 원시 응답(`seed/raw/`)은 커밋하지 않는다.
