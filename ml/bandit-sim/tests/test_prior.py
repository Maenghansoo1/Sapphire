import copy

import numpy as np
import pytest

import prior as priors
from env import ARMS, global_member_means
from run import LLM_PRIOR

GOOD = {"version": 1, "generatedAt": "2026-11-23T00:00:00Z", "model": "test", "kSyn": 10, "isSynthetic": True,
        "arms": {"PHOTO_NOW": {"alpha": 4.0, "beta": 6.0}, "TEXT_ACROSTIC": {"alpha": 3.5, "beta": 6.5}}}


def test_valid_prior_loads():
    parsed = priors.validate(GOOD)
    assert parsed["PHOTO_NOW"] == (4.0, 6.0)
    means = priors.means_from(parsed)
    assert means[ARMS.index("PHOTO_NOW")] == pytest.approx(0.4)
    assert means[ARMS.index("DRAW_A_TO_B")] == pytest.approx(0.5)  # prior 에 없는 새 틀은 Beta(1,1) 평균


@pytest.mark.parametrize("change", [
    lambda p: p.update(version=2),
    lambda p: p.update(isSynthetic=False),
    lambda p: p.update(kSyn=11),
    lambda p: p.update(arms={}),
    lambda p: p["arms"]["PHOTO_NOW"].update(alpha=0),
    lambda p: p["arms"]["PHOTO_NOW"].update(alpha=8.0),  # α + β = 14 > kSyn 10
])
def test_invalid_prior_is_rejected(change):
    broken = copy.deepcopy(GOOD)
    change(broken)
    with pytest.raises(priors.PriorError):
        priors.validate(broken)


def test_simulated_priors():
    truth = global_member_means()
    right = priors.simulated_means("right", np.random.default_rng(0))
    assert np.abs(right - truth).max() < 0.15
    wrong = priors.simulated_means("wrong", np.random.default_rng(0))
    assert wrong.argmax() == truth.argmin() and wrong.argmin() == truth.argmax()  # 순위가 뒤집힌 prior


@pytest.mark.skipif(not LLM_PRIOR.exists(), reason="합성 시드를 아직 만들지 않음")
def test_committed_seed_prior_is_valid():
    parsed = priors.load(LLM_PRIOR)
    means = np.array([a / (a + b) for a, b in parsed.values()])
    assert means.max() - means.min() <= 0.2 + 1e-9  # 팔 간 차이가 비정상적으로 크지 않아야
