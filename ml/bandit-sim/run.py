"""밴딧 시뮬레이션 실행(TODO_2/D1).

  python run.py --scenario all --seeds 30            # 본 비교 + 파라미터 비교, 결과는 out/
  python run.py --scenario base --seeds 5 --no-sweeps # 빠르게 한 번

같은 (seed, 시나리오)면 정책과 상관없이 같은 그룹·같은 난수 흐름을 써서(공통 난수) 정책 차이만 비교한다.
"""
from __future__ import annotations

import argparse
import csv
import time
from collections import deque
from dataclasses import replace
from pathlib import Path

import numpy as np

import prior as priors
from env import ARMS, MEDIA, MEDIA_OF, SCENARIOS, Population, Scenario, candidates, media_orders
from policies import EpsilonGreedy, HierParams, Policy, RandomPolicy, TSFlat, TSHier

HERE = Path(__file__).resolve().parent
LLM_PRIOR = HERE / "seed" / "synthetic_prior.json"
EARLY_DAYS = 14
LATE_DAYS = 30
CAP_SHARE, CAP_WINDOW, CAP_MIN = 0.40, 7, 5


def policy_factories(params: HierParams = HierParams()) -> dict:
    """이름 → (prior 난수를 받아 정책을 만드는 함수). 합성 prior 는 K_syn=10(권장 상한)."""
    factories = {
        "random": lambda rng: RandomPolicy(),
        "epsilon_greedy": lambda rng: EpsilonGreedy(0.1),
        "ts_flat": lambda rng: TSFlat(),
        "ts_hier": lambda rng: TSHier("ts_hier", replace(params, k_syn=0.0)),
        # 안전장치(반복 패널티·점유율 상한)를 뺀 계층 TS — 안전장치가 후회를 얼마나 늘리는지 보려는 비교용
        "ts_hier_free": lambda rng: TSHier("ts_hier_free", replace(params, k_syn=0.0, repeat_penalty=1.0, share_cap=1.0)),
        "ts_hier_syn": lambda rng: TSHier("ts_hier_syn", replace(params, k_syn=10.0), priors.simulated_means("right", rng)),
        "ts_hier_syn_wrong": lambda rng: TSHier("ts_hier_syn_wrong", replace(params, k_syn=10.0),
                                                priors.simulated_means("wrong", rng)),
    }
    if LLM_PRIOR.exists():  # D1 합성 시드를 만들어 두었으면 그것도 비교한다
        means = priors.means_from(priors.load(LLM_PRIOR))
        factories["ts_hier_llm"] = lambda rng: TSHier("ts_hier_llm", replace(params, k_syn=10.0), means)
    return factories


def simulate(scenario: Scenario, policy: Policy, seed: int, n_groups: int, scenario_index: int, policy_index: int) -> dict:
    """한 번 돌린 결과(날마다): 후회 합, 보상 합, 대상 인원, 제출 수, 최선 후보 맞춘 수, 미션 수, 점유율 상한 넘은 그룹 수."""
    population = Population(scenario, n_groups, np.random.default_rng([seed, scenario_index, 0]))
    policy.reset(n_groups, len(ARMS))
    rng = np.random.default_rng([seed, scenario_index, 1, policy_index])
    days = scenario.days
    out = {key: np.zeros(days) for key in ("regret", "reward", "n", "submitted", "best", "missions", "cap_over",
                                            "early_regret", "early_reward", "early_n")}
    yesterday = np.zeros((n_groups, len(MEDIA)), dtype=bool)
    window: deque[np.ndarray] = deque(maxlen=CAP_WINDOW)
    groups = np.arange(n_groups)
    for day in range(days):
        population.apply_day(day)
        pool = population.pool_mask(day)
        orders = media_orders(n_groups, pool, np.random.default_rng([seed, scenario_index, 2, day]))
        expected = population.expected_reward()
        starts = np.full((n_groups, len(MEDIA)), -1)
        today_media = np.zeros_like(yesterday)
        today_counts = np.zeros((n_groups, len(ARMS)))
        results = []
        for slot in (1, 2, 3):
            cand = candidates(population.daily, slot, yesterday, orders, pool, starts) & population.joined(day)[:, None]
            active = cand.any(axis=1)
            if not active.any():
                continue
            arms = np.where(active, policy.choose(cand, day, rng), -1)
            if not cand[groups[active], arms[active]].all():
                raise AssertionError(f"{policy.name}: 후보가 아닌 틀을 골랐어요")
            policy.observe_choice(arms, day)
            reward, n, submitted = population.simulate(arms, active, np.random.default_rng([seed, scenario_index, 3, day, slot]))
            safe = np.where(active, arms, 0)
            best = np.where(cand, expected, -np.inf).max(axis=1)
            got = expected[groups, safe]
            regret = np.where(active, best - got, 0.0)
            out["regret"][day] += regret.sum()
            # 그룹마다 가입 뒤 첫 14일(계층·합성 prior 의 효과가 보이는 구간)
            newbie = active & (day - population.start_day < EARLY_DAYS)
            out["early_regret"][day] += regret[newbie].sum()
            out["early_reward"][day] += reward[newbie].sum()
            out["early_n"][day] += n[newbie].sum()
            out["best"][day] += (active & (got >= best - 1e-12)).sum()
            out["missions"][day] += active.sum()
            out["reward"][day] += reward.sum()
            out["n"][day] += n.sum()
            out["submitted"][day] += submitted.sum()
            idx = groups[active]
            first = starts[idx, MEDIA_OF[arms[active]]] < 0
            starts[idx[first], MEDIA_OF[arms[active]][first]] = arms[active][first]
            today_media[idx, MEDIA_OF[arms[active]]] = True
            np.add.at(today_counts, (idx, arms[active]), 1)
            results.append((arms, reward, n))
        for arms, reward, n in results:  # 미션은 다음 미션이 열릴 때 닫히므로 보상은 그날 끝에 반영
            policy.update(arms, reward, n, day)
        policy.end_day(day)
        window.append(today_counts)
        counts = np.sum(window, axis=0)
        total = counts.sum(axis=1)
        over = ((counts / np.maximum(total, 1)[:, None]) > CAP_SHARE).any(axis=1) & (total >= CAP_MIN)
        out["cap_over"][day] = over.sum()
        yesterday = today_media
    return out


def summarize(runs: list[dict], n_groups: int) -> dict:
    """여러 시드 결과 → 평균과 95% 신뢰구간 반폭."""
    def stat(values):
        values = np.asarray(values, dtype=float)
        half = 1.96 * values.std(ddof=1) / np.sqrt(len(values)) if len(values) > 1 else 0.0
        return float(values.mean()), float(half)

    cum = [np.cumsum(r["regret"]) / n_groups for r in runs]
    days = len(runs[0]["regret"])
    late = slice(max(days - LATE_DAYS, 0), days)
    return {
        "regret_final": stat([c[-1] for c in cum]),
        "regret_day30": stat([c[min(29, days - 1)] for c in cum]),
        "regret_early": stat([r["early_regret"].sum() / n_groups for r in runs]),
        "reward_early": stat([r["early_reward"].sum() / r["early_n"].sum() for r in runs]),
        "reward_late": stat([r["reward"][late].sum() / r["n"][late].sum() for r in runs]),
        "participation_late": stat([r["submitted"][late].sum() / r["n"][late].sum() for r in runs]),
        "best_rate": stat([r["best"].sum() / r["missions"].sum() for r in runs]),
        "cap_over_rate": stat([r["cap_over"].sum() / (n_groups * days) for r in runs]),
        "curve": np.mean(cum, axis=0),
        "curve_half": 1.96 * np.std(cum, axis=0, ddof=1) / np.sqrt(len(cum)) if len(cum) > 1 else np.zeros(days),
    }


def run_matrix(scenario_names, policies: dict, seeds: int, n_groups: int, label: str = "") -> dict:
    results = {}
    for s_index, name in enumerate(scenario_names):
        scenario = SCENARIOS[name]
        for p_index, (policy_name, make) in enumerate(policies.items()):
            started = time.perf_counter()
            runs = []
            for seed in range(seeds):
                policy = make(np.random.default_rng([seed, 99]))
                runs.append(simulate(scenario, policy, seed, n_groups, list(SCENARIOS).index(name), p_index))
            results[(name, policy_name)] = summarize(runs, n_groups)
            print(f"{label}{name:9s} {policy_name:18s} {time.perf_counter() - started:5.1f}s", flush=True)
    return results


def fmt(stat, digits=3, percent=False):
    mean, half = stat
    if percent:
        return f"{mean * 100:.1f}% ± {half * 100:.1f}"
    return f"{mean:.{digits}f} ± {half:.{digits}f}"


def write_outputs(results: dict, out: Path, title: str) -> str:
    out.mkdir(parents=True, exist_ok=True)
    rows = []
    with (out / f"{title}_summary.csv").open("w", newline="", encoding="utf-8") as f:
        writer = csv.writer(f)
        writer.writerow(["scenario", "policy", "regret_final", "regret_final_ci", "regret_day30", "reward_early",
                         "reward_late", "participation_late", "best_rate", "cap_over_rate"])
        for (scenario, policy), r in results.items():
            writer.writerow([scenario, policy, *r["regret_final"], r["regret_day30"][0], r["reward_early"][0],
                             r["reward_late"][0], r["participation_late"][0], r["best_rate"][0], r["cap_over_rate"][0]])
            rows.append(f"| {scenario} | {policy} | {fmt(r['regret_final'], 2)} | {fmt(r['regret_early'], 2)} | "
                        f"{fmt(r['reward_early'])} | {fmt(r['reward_late'])} | {fmt(r['best_rate'], percent=True)} | "
                        f"{fmt(r['cap_over_rate'], percent=True)} |")
    with (out / f"{title}_curves.csv").open("w", newline="", encoding="utf-8") as f:
        writer = csv.writer(f)
        writer.writerow(["scenario", "policy", "day", "cum_regret", "ci"])
        for (scenario, policy), r in results.items():
            for day, (value, half) in enumerate(zip(r["curve"], r["curve_half"])):
                writer.writerow([scenario, policy, day + 1, f"{value:.4f}", f"{half:.4f}"])
    header = ("| 시나리오 | 정책 | 누적 후회(그룹당, 90일) | 후회(그룹당, 가입 뒤 첫 14일) | 보상/인원 가입 뒤 첫 14일 | "
              "보상/인원 마지막 30일 | 최선 후보 고른 비율 | 점유율 40% 넘은 그룹·일 |\n|---|---|---|---|---|---|---|---|")
    table = header + "\n" + "\n".join(rows)
    (out / f"{title}_summary.md").write_text(table + "\n", encoding="utf-8")
    plot_curves(results, out / f"{title}_regret.png")
    return table


def plot_curves(results: dict, path: Path) -> None:
    import matplotlib
    matplotlib.use("Agg")
    import matplotlib.pyplot as plt

    scenarios = sorted({s for s, _ in results}, key=list(SCENARIOS).index)
    fig, axes = plt.subplots(1, len(scenarios), figsize=(4.2 * len(scenarios), 3.6), squeeze=False)
    for ax, scenario in zip(axes[0], scenarios):
        for (s, policy), r in results.items():
            if s != scenario:
                continue
            days = np.arange(1, len(r["curve"]) + 1)
            ax.plot(days, r["curve"], label=policy, linewidth=1.4)
            ax.fill_between(days, r["curve"] - r["curve_half"], r["curve"] + r["curve_half"], alpha=0.15)
        ax.set_title(scenario)
        ax.set_xlabel("day")
        ax.grid(alpha=0.3)
    axes[0][0].set_ylabel("cumulative regret per group")
    axes[0][-1].legend(fontsize=7, loc="upper left")
    fig.tight_layout()
    fig.savefig(path, dpi=130)
    plt.close(fig)


def run_sweeps(seeds: int, n_groups: int, out: Path) -> str:
    """파라미터 근거: K_syn(맞는/틀린 prior), κ(수축 강도), ε(탐색 하한)."""
    lines = []
    ksyn = {}
    for kind in ("right", "wrong"):
        for k in (0, 2, 5, 10, 20, 50):
            ksyn[f"k_syn={k} {kind}"] = (lambda kind=kind, k=k: (
                lambda rng: TSHier(f"k{k}_{kind}", HierParams(k_syn=float(k)), priors.simulated_means(kind, rng))))()
    # 그룹이 적을 때(출시 초반)는 전역 실측이 천천히 쌓여 합성 prior 의 영향이 커진다 → 그룹 수 두 가지로 본다
    lines.append("### K_syn (합성 prior 의사 관측 수)\n\n| 시나리오 | 그룹 수 | 설정 | 후회 가입 뒤 첫 14일 | "
                 "보상/인원 가입 뒤 첫 14일 | 누적 후회 90일 |\n|---|---|---|---|---|---|")
    for groups in sorted({20, n_groups}):
        r = run_matrix(["base", "staggered"], ksyn, seeds, groups, label=f"[K_syn G={groups}] ")
        for (scenario, name), s in r.items():
            lines.append(f"| {scenario} | {groups} | {name} | {fmt(s['regret_early'], 2)} | {fmt(s['reward_early'])} | "
                         f"{fmt(s['regret_final'], 2)} |")
    kappa = {f"kappa={k}": (lambda k=k: (lambda rng: TSHier(f"kappa{k}", HierParams(kappa=float(k)))))()
             for k in (0, 2, 5, 10, 20, 50)}
    r = run_matrix(["base", "het_high", "staggered"], kappa, seeds, n_groups, label="[kappa] ")
    lines.append("\n### κ (전역 → 그룹 수축 강도, 0 = 계층 없음)\n\n| 시나리오 | 설정 | 후회 가입 뒤 첫 14일 | 누적 후회 90일 | "
                 "보상/인원 마지막 30일 |\n|---|---|---|---|---|")
    for (scenario, name), s in r.items():
        lines.append(f"| {scenario} | {name} | {fmt(s['regret_early'], 2)} | {fmt(s['regret_final'], 2)} | {fmt(s['reward_late'])} |")
    eps = {f"epsilon={e}": (lambda e=e: (lambda rng: TSHier(f"eps{e}", HierParams(epsilon=e))))()
           for e in (0.0, 0.05, 0.1, 0.2)}
    r = run_matrix(["base", "shift"], eps, seeds, n_groups, label="[epsilon] ")
    lines.append("\n### ε (탐색 하한)\n\n| 시나리오 | 설정 | 누적 후회 90일 | 보상/인원 마지막 30일 |\n|---|---|---|---|")
    for (scenario, name), s in r.items():
        lines.append(f"| {scenario} | {name} | {fmt(s['regret_final'], 2)} | {fmt(s['reward_late'])} |")
    text = "\n".join(lines) + "\n"
    (out / "sweeps.md").write_text(text, encoding="utf-8")
    return text


def main():
    parser = argparse.ArgumentParser(description="틀 선택 밴딧 시뮬레이션")
    parser.add_argument("--scenario", default="all", help="all 또는 " + ", ".join(SCENARIOS))
    parser.add_argument("--seeds", type=int, default=30)
    parser.add_argument("--groups", type=int, default=200)
    parser.add_argument("--out", type=Path, default=HERE / "out")
    parser.add_argument("--no-sweeps", action="store_true", help="파라미터 비교를 건너뛴다")
    args = parser.parse_args()
    names = list(SCENARIOS) if args.scenario == "all" else [args.scenario]
    started = time.perf_counter()
    results = run_matrix(names, policy_factories(), args.seeds, args.groups)
    print("\n" + write_outputs(results, args.out, "main"))
    if not args.no_sweeps:
        print("\n" + run_sweeps(args.seeds, args.groups, args.out))
    print(f"\n완료 {time.perf_counter() - started:.0f}s → {args.out}")


if __name__ == "__main__":
    main()
