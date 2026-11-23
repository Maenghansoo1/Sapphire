import numpy as np
import pytest

from env import ARMS, SCENARIOS
from policies import EpsilonGreedy, HierParams, RandomPolicy, TSFlat, TSHier, beta_update
from run import simulate

A = len(ARMS)


def test_beta_update_matches_hand_calculation():
    alpha, beta = np.zeros((2, A)), np.zeros((2, A))
    # 그룹 0: 틀 1 에서 4명 중 보상 합 2.4 → α += 2.4, β += 1.6 / 그룹 1: 미션 없음
    beta_update(alpha, beta, np.array([1, -1]), np.array([2.4, 0.0]), np.array([4, 0]))
    assert alpha[0, 1] == pytest.approx(2.4) and beta[0, 1] == pytest.approx(1.6)
    assert alpha.sum() == pytest.approx(2.4) and beta.sum() == pytest.approx(1.6)


def test_decay_halves_after_half_life():
    policy = TSHier("t", HierParams(half_life=30))
    policy.reset(1, A)
    policy.update(np.array([0]), np.array([3.0]), np.array([4]), 0)
    for day in range(30):
        policy.end_day(day)
    assert policy.alpha[0, 0] == pytest.approx(1.5) and policy.beta[0, 0] == pytest.approx(0.5)


@pytest.mark.parametrize("policy", [RandomPolicy(), EpsilonGreedy(), TSFlat(), TSHier("t", HierParams())])
def test_policies_only_pick_candidates(policy):
    rng = np.random.default_rng(3)
    policy.reset(64, A)
    for day in range(40):
        cand = rng.random((64, A)) < 0.4
        cand[:5] = False  # 후보가 없는 그룹
        arms = policy.choose(cand, day, rng)
        ok = cand.any(axis=1)
        assert (arms[~ok] == -1).all()
        assert cand[np.flatnonzero(ok), arms[ok]].all()
        policy.observe_choice(arms, day)
        policy.update(arms, rng.random(64) * 3, np.full(64, 4), day)
        policy.end_day(day)


def test_exploration_floor_keeps_every_candidate_alive():
    epsilon = 0.05
    policy = TSHier("t", HierParams(epsilon=epsilon, repeat_penalty=1.0, share_cap=1.0))
    groups = 20_000
    policy.reset(groups, A)
    policy.alpha[:, 0] = 1000.0  # 틀 0 이 압도적으로 좋다고 믿는 상태
    policy.beta[:, 1:3] = 1000.0
    cand = np.zeros((groups, A), dtype=bool)
    cand[:, :3] = True
    arms = policy.choose(cand, 0, np.random.default_rng(4))
    share = np.bincount(arms, minlength=A) / groups
    assert share[0] > 0.9
    assert share[1] >= epsilon / 3 * 0.8 and share[2] >= epsilon / 3 * 0.8  # 어떤 후보도 ε/후보수 근처 이상


def test_recently_used_template_is_penalised():
    policy = TSHier("t", HierParams(epsilon=0.0, share_cap=1.0))
    policy.reset(2000, A)
    policy.alpha[:, :2] = policy.beta[:, :2] = 5000.0  # 두 틀 다 0.5 로 확실히 같음
    policy.observe_choice(np.zeros(2000, dtype=int), day=9)  # 틀 0 을 어제 씀
    policy.end_day(9)
    cand = np.zeros((2000, A), dtype=bool)
    cand[:, :2] = True
    arms = policy.choose(cand, 10, np.random.default_rng(5))
    assert (arms == 1).mean() > 0.95  # ×0.85 패널티로 거의 항상 다른 틀


def test_share_cap_defers_overused_template_unless_all_over():
    policy = TSHier("t", HierParams(epsilon=0.0, repeat_penalty=1.0, share_cap=0.4, cap_min_missions=5))
    policy.reset(2, A)
    policy.alpha[:, 0] = 1000.0  # 틀 0 이 가장 좋아 보임
    for day in range(6):  # 6일 연속 틀 0 → 점유율 100%
        policy.observe_choice(np.array([0, 0]), day)
        policy.end_day(day)
    cand = np.zeros((2, A), dtype=bool)
    cand[0, :2] = True  # 그룹 0: 다른 후보가 있음 → 미룸
    cand[1, 0] = True  # 그룹 1: 후보가 그것뿐 → 상한 무시
    arms = policy.choose(cand, 6, np.random.default_rng(6))
    assert arms.tolist() == [1, 0]


def test_synthetic_prior_starts_global_mean_then_fades():
    policy = TSHier("t", HierParams(k_syn=10.0, syn_decay=50.0), prior_means=np.full(A, 0.8))
    policy.reset(1, A)
    assert policy.global_mean()[0] == pytest.approx((1 + 8) / (2 + 10))  # Beta(1,1) + 의사 관측 10개(평균 0.8)
    policy.real_missions[:] = 10_000  # 실측이 충분히 쌓이면 합성 prior 영향은 사라진다
    assert policy.global_mean()[0] == pytest.approx(0.5, abs=1e-6)


def test_simulation_is_reproducible():
    first = simulate(SCENARIOS["base"], TSHier("t", HierParams()), seed=3, n_groups=30, scenario_index=0, policy_index=1)
    second = simulate(SCENARIOS["base"], TSHier("t", HierParams()), seed=3, n_groups=30, scenario_index=0, policy_index=1)
    for key in first:
        assert np.array_equal(first[key], second[key])
