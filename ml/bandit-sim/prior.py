"""합성 prior(TODO_2/D1, AI_FEATURES.md §4.4): 팔별 의사 관측 (α, β).

- 저장 형식(`seed/synthetic_prior.json`, D4 가 읽음): {"version":1, "generatedAt":..., "model":..., "kSyn":10,
  "arms": {"PHOTO_NOW": {"alpha":.., "beta":..}, ...}, "isSynthetic": true}
- LLM 으로 만든 prior 는 LLM 의 **믿음**이지 실제 사용자 행동이 아니다. 출발점으로만 쓰고 영향력을 K_syn ≤ 10 으로 묶는다.
- 시뮬레이션에서는 LLM 대신 "맞는 prior"(정답 근처)와 "틀린 prior"(순위를 뒤집음)를 만들어, 틀렸을 때의 피해를 잰다.
"""
from __future__ import annotations

import json
from pathlib import Path

import numpy as np

from env import ARMS, global_member_means

MAX_K_SYN = 10.0


class PriorError(ValueError):
    pass


def validate(prior: dict) -> dict[str, tuple[float, float]]:
    """형식·범위를 확인하고 팔 → (α, β) 를 돌려준다. α, β > 0, α + β ≤ kSyn ≤ 10, 합성 표시 필수."""
    if prior.get("version") != 1:
        raise PriorError("version 은 1 이어야 해요")
    if prior.get("isSynthetic") is not True:
        raise PriorError("합성 prior 는 isSynthetic=true 로 표시해야 해요")
    k = prior.get("kSyn")
    if not isinstance(k, (int, float)) or not 0 < k <= MAX_K_SYN:
        raise PriorError(f"kSyn 은 0 초과 {MAX_K_SYN:g} 이하여야 해요")
    arms = prior.get("arms")
    if not isinstance(arms, dict) or not arms:
        raise PriorError("arms 가 비어 있어요")
    out = {}
    for arm, value in arms.items():
        alpha, beta = value.get("alpha"), value.get("beta")
        if not all(isinstance(x, (int, float)) and x > 0 for x in (alpha, beta)):
            raise PriorError(f"{arm}: alpha, beta 는 0 보다 커야 해요")
        if alpha + beta > k + 1e-9:
            raise PriorError(f"{arm}: alpha + beta 가 kSyn({k}) 을 넘어요")
        out[arm] = (float(alpha), float(beta))
    return out


def load(path: Path) -> dict[str, tuple[float, float]]:
    return validate(json.loads(Path(path).read_text(encoding="utf-8")))


def means_from(prior: dict[str, tuple[float, float]], arms=ARMS) -> np.ndarray:
    """팔 순서대로 prior 평균 α/(α+β). prior 에 없는 팔(새 틀)은 0.5(서버는 Beta(1,1) 로 시작)."""
    return np.array([prior[a][0] / sum(prior[a]) if a in prior else 0.5 for a in arms])


def simulated_means(kind: str, rng: np.random.Generator, noise: float = 0.03) -> np.ndarray:
    """시뮬레이션용 prior 평균. right: 정답(틀별 가정치) + 약간의 오차, wrong: 정답의 순위를 뒤집은 값."""
    truth = global_member_means()
    if kind == "right":
        return np.clip(truth + rng.normal(0.0, noise, size=truth.shape), 0.05, 0.95)
    if kind == "wrong":
        order = np.argsort(truth)
        flipped = np.empty_like(truth)
        flipped[order] = np.sort(truth)[::-1]
        return flipped
    raise ValueError(kind)
