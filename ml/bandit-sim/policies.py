"""틀 선택 정책(TODO_2/D1). 모두 같은 모양: choose(후보) → 틀, update(결과), end_day().

- random: 지금 서버처럼 후보 중 무작위.
- epsilon_greedy: 비교용. 그룹별 평균이 가장 높은 틀, ε 확률로 무작위.
- ts_flat: 그룹마다 Beta(1,1)에서 시작하는 Thompson Sampling(계층 없음, 비교용).
- ts_hier: AI_FEATURES.md §4.3 의 계층 TS — 전역 분포로 그룹 분포를 수축 + 탐색 하한 + 반복 패널티 + 점유율 상한 + 시간 감쇠.
  합성 prior(K_syn)를 주면 전역 분포의 출발점으로 쓰고, 실측이 쌓일수록 영향력을 줄인다.

보상은 분수(0~1)라 Beta 갱신 α += R, β += n − R 은 근사다(AI_FEATURES.md §4.8).
"""
from __future__ import annotations

from collections import deque
from dataclasses import dataclass

import numpy as np

NEG = -np.inf


def masked_argmax(scores: np.ndarray, cand: np.ndarray) -> np.ndarray:
    """후보 안에서 가장 큰 점수의 틀(후보가 없는 그룹은 -1)."""
    masked = np.where(cand, scores, NEG)
    choice = masked.argmax(axis=1)
    return np.where(cand.any(axis=1), choice, -1)


def random_choice(cand: np.ndarray, rng: np.random.Generator) -> np.ndarray:
    return masked_argmax(rng.random(cand.shape), cand)


def beta_update(alpha: np.ndarray, beta: np.ndarray, arms: np.ndarray, reward: np.ndarray, n: np.ndarray) -> None:
    """그룹 g 가 틀 arms[g] 에서 보상 합 reward[g], 대상 n[g] 을 얻었으면 α += R, β += n − R (제자리). arms < 0 은 건너뛴다."""
    ok = arms >= 0
    g = np.flatnonzero(ok)
    np.add.at(alpha, (g, arms[ok]), reward[ok])
    np.add.at(beta, (g, arms[ok]), n[ok] - reward[ok])


class Policy:
    name = "policy"

    def reset(self, n_groups: int, n_arms: int) -> None:
        self.n_groups, self.n_arms = n_groups, n_arms

    def choose(self, cand: np.ndarray, day: int, rng: np.random.Generator) -> np.ndarray:
        raise NotImplementedError

    def observe_choice(self, arms: np.ndarray, day: int) -> None:
        """고른 직후(같은 날 다음 슬롯의 반복 패널티·점유율에 쓰임). 보상은 아직 모른다."""

    def update(self, arms: np.ndarray, reward: np.ndarray, n: np.ndarray, day: int) -> None:
        """미션이 닫혀 보상이 확정됐을 때(시뮬레이션에서는 그날 끝)."""

    def end_day(self, day: int) -> None:
        pass


class RandomPolicy(Policy):
    name = "random"

    def choose(self, cand, day, rng):
        return random_choice(cand, rng)


class EpsilonGreedy(Policy):
    name = "epsilon_greedy"

    def __init__(self, epsilon: float = 0.1):
        self.epsilon = epsilon

    def reset(self, n_groups, n_arms):
        super().reset(n_groups, n_arms)
        self.reward = np.zeros((n_groups, n_arms))
        self.count = np.zeros((n_groups, n_arms))

    def choose(self, cand, day, rng):
        mean = (self.reward + 1.0) / (self.count + 2.0)  # 안 해 본 틀은 0.5 에서 시작
        greedy = masked_argmax(mean + 1e-9 * rng.random(cand.shape), cand)
        explore = rng.random(self.n_groups) < self.epsilon
        return np.where(explore, random_choice(cand, rng), greedy)

    def update(self, arms, reward, n, day):
        ok = arms >= 0
        g = np.flatnonzero(ok)
        np.add.at(self.reward, (g, arms[ok]), reward[ok])
        np.add.at(self.count, (g, arms[ok]), n[ok])


class TSFlat(Policy):
    name = "ts_flat"

    def reset(self, n_groups, n_arms):
        super().reset(n_groups, n_arms)
        self.alpha = np.zeros((n_groups, n_arms))
        self.beta = np.zeros((n_groups, n_arms))

    def choose(self, cand, day, rng):
        return masked_argmax(rng.beta(1.0 + self.alpha, 1.0 + self.beta), cand)

    def update(self, arms, reward, n, day):
        beta_update(self.alpha, self.beta, arms, reward, n)


@dataclass
class HierParams:
    kappa: float = 10.0  # 전역 → 그룹 수축 강도(의사 관측 수)
    epsilon: float = 0.05  # 탐색 하한: 이 확률로 후보 중 무작위 → 어떤 후보도 ε/후보수 이상
    repeat_penalty: float = 0.85  # 최근 repeat_days 안에 쓴 틀이면 표본값 ×
    repeat_days: int = 3
    share_cap: float = 0.40  # 최근 cap_window 일 점유율이 이보다 크면 그 틀을 미룬다(결정 6)
    cap_window: int = 7
    cap_min_missions: int = 5  # 창 안 미션이 이보다 적으면 상한을 보지 않는다
    half_life: float = 30.0  # 관측 감쇠 반감기(일)
    k_syn: float = 0.0  # 합성 prior 의사 관측 수(팔당, ≤ 10 권장)
    syn_decay: float = 50.0  # 실측 미션이 N0 개 쌓이면 합성 prior 영향 1/e


class TSHier(Policy):
    def __init__(self, name: str, params: HierParams, prior_means: np.ndarray | None = None):
        self.name = name
        self.params = params
        self.prior_means = prior_means  # 팔별 합성 prior 평균(없으면 합성 prior 를 쓰지 않음)

    def reset(self, n_groups, n_arms):
        super().reset(n_groups, n_arms)
        self.alpha = np.zeros((n_groups, n_arms))
        self.beta = np.zeros((n_groups, n_arms))
        self.real_missions = np.zeros(n_arms)  # 전역 실측 미션 수(감쇠 안 함 — 합성 prior 감쇠용)
        self.last_used = np.full((n_groups, n_arms), -10_000)
        # 지난 (창 − 1)일 + 오늘 = 창 크기만큼의 날
        self.window: deque[np.ndarray] = deque(maxlen=max(self.params.cap_window - 1, 1))
        self.today = np.zeros((n_groups, n_arms))

    def global_mean(self) -> np.ndarray:
        """전역 평균 보상(팔별): 모든 그룹 실측 합 + (있으면) 줄어드는 합성 prior + Beta(1,1)."""
        a = self.alpha.sum(axis=0) + 1.0
        b = self.beta.sum(axis=0) + 1.0
        if self.prior_means is not None and self.params.k_syn > 0:
            weight = self.params.k_syn * np.exp(-self.real_missions / self.params.syn_decay)
            a = a + weight * self.prior_means
            b = b + weight * (1.0 - self.prior_means)
        return a / (a + b)

    def group_posterior(self) -> tuple[np.ndarray, np.ndarray]:
        if self.params.kappa <= 0:  # 계층 없음: 그룹마다 Beta(1,1) 에서 시작(비교용)
            return 1.0 + self.alpha, 1.0 + self.beta
        m = self.global_mean()[None, :]
        return self.params.kappa * m + self.alpha, self.params.kappa * (1.0 - m) + self.beta

    def window_share(self) -> tuple[np.ndarray, np.ndarray]:
        """최근 창(오늘 포함)에서 그룹별 틀 점유율과 미션 수."""
        counts = self.today + (np.sum(self.window, axis=0) if self.window else 0.0)
        total = counts.sum(axis=1)
        return counts / np.maximum(total, 1)[:, None], total

    def choose(self, cand, day, rng):
        p = self.params
        a, b = self.group_posterior()
        score = rng.beta(a, b)
        recent = (day - self.last_used) <= p.repeat_days
        score = np.where(recent, score * p.repeat_penalty, score)
        share, total = self.window_share()
        over = (share > p.share_cap) & (total >= p.cap_min_missions)[:, None] & cand
        all_over = (over == cand).all(axis=1)  # 후보가 모두 넘었으면 상한을 무시
        score = np.where(over & ~all_over[:, None], 0.0, score)
        chosen = masked_argmax(score, cand)
        explore = rng.random(self.n_groups) < p.epsilon
        return np.where(explore, random_choice(cand, rng), chosen)

    def observe_choice(self, arms, day):
        ok = arms >= 0
        g = np.flatnonzero(ok)
        self.last_used[g, arms[ok]] = day
        np.add.at(self.today, (g, arms[ok]), 1)

    def update(self, arms, reward, n, day):
        beta_update(self.alpha, self.beta, arms, reward, n)
        np.add.at(self.real_missions, arms[arms >= 0], 1)

    def end_day(self, day):
        self.window.append(self.today)
        self.today = np.zeros((self.n_groups, self.n_arms))
        decay = 0.5 ** (1.0 / self.params.half_life)
        self.alpha *= decay
        self.beta *= decay
