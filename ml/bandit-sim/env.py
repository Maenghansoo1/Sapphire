"""합성 환경(TODO_2/D1): 그룹마다 틀(팔)별로 숨은 참여율이 있고, 서버와 같은 후보 규칙으로 미션을 낸다.

실제 사용자·미션 기록은 쓰지 않는다. 아래 틀별 평균값은 **가정치**다(실측 아님) — 결과는 "이런 환경이라면"의 비교일 뿐이다.
"""
from __future__ import annotations

from dataclasses import dataclass

import numpy as np

# 서버 기본 발행 풀(MissionTemplateSelector.MVP_POOL)을 enum 순서로. 30초 스케치는 2026-10-19 팀 결정으로 빠졌다.
ARMS = ("PHOTO_NOW", "PHOTO_GALLERY", "PHOTO_COLOR", "TEXT_ACROSTIC", "TEXT_FILL_BLANK", "DRAW_A_TO_B")
ARM_MEDIA = {arm: arm.split("_")[0] for arm in ARMS}
# 서버와 같은 고정 매체 순서. 그룹·날짜마다 이 순서를 섞는다.
MEDIA = ("PHOTO", "TEXT", "DRAW")
MEDIA_OF = np.array([MEDIA.index(ARM_MEDIA[arm]) for arm in ARMS])

# 틀별 가정치: (제출 확률 p, 제출했을 때 정성 q). 멤버 보상 r = 제출 × (0.6 + 0.2·effort + 0.2·engage), effort·engage 평균 = q
BASE_ARMS = {
    "PHOTO_NOW": (0.70, 0.55),
    "PHOTO_GALLERY": (0.62, 0.60),
    "PHOTO_COLOR": (0.58, 0.50),
    "TEXT_ACROSTIC": (0.50, 0.55),
    "TEXT_FILL_BLANK": (0.60, 0.45),
    "DRAW_A_TO_B": (0.45, 0.50),
}
# 서버 정원: 연인 2명, 그 외 최대 6명
MEMBER_SIZES = np.array([2, 3, 4, 5, 6])
MEMBER_WEIGHTS = np.array([0.30, 0.20, 0.20, 0.15, 0.15])
MAX_MEMBERS = int(MEMBER_SIZES.max())
# 하루 미션 수. 3개까지만 둬서 같은 날 같은 매체가 다시 오는 경우(서버의 start + round 규칙)는 매체가 줄어든 풀에서만 생긴다
DAILY_COUNTS = np.array([1, 2, 3])
DAILY_WEIGHTS = np.array([0.60, 0.25, 0.15])
MEMBER_SPREAD = 0.5  # 멤버마다 활동성이 다른 정도(로짓)
QUALITY_SPREAD = 0.1
QUALITY_CONCENTRATION = 6.0


def sigmoid(x):
    return 1.0 / (1.0 + np.exp(-x))


def logit(p):
    return np.log(p / (1.0 - p))


@dataclass(frozen=True)
class Scenario:
    name: str
    heterogeneity: float  # 그룹마다 틀 선호가 얼마나 다른지(로짓 표준편차)
    description: str
    days: int = 90
    new_arm: str | None = None  # 이 틀은 new_arm_day 부터 풀에 들어온다(그 전엔 없음)
    new_arm_day: int = 30
    shift_day: int | None = None  # 이 날부터 그룹마다 선호가 뒤집힌다(비정상성)
    join_spread: int = 0  # 0 이면 모두 첫날 시작, 아니면 그룹마다 0~이 날 사이 아무 날에 가입(늦게 온 그룹은 전역 데이터 덕을 본다)


SCENARIOS = {
    "base": Scenario("base", 0.7, "그룹 간 선호 차이 중간"),
    "het_low": Scenario("het_low", 0.3, "그룹 간 선호 차이 작음"),
    "het_high": Scenario("het_high", 1.2, "그룹 간 선호 차이 큼"),
    "new_arm": Scenario("new_arm", 0.7, "30일째에 '그림 A를 B로' 틀이 새로 들어옴(prior 없는 팔)",
                        new_arm="DRAW_A_TO_B", new_arm_day=30),
    "shift": Scenario("shift", 0.7, "45일째에 그룹 선호가 뒤집힘(취향 변화)", shift_day=45),
    "staggered": Scenario("staggered", 0.7, "그룹이 60일에 걸쳐 차례로 가입(서비스가 커지는 상황)", join_spread=60),
}


def global_member_means() -> np.ndarray:
    """틀별 멤버 보상의 대략적 전역 평균 p·(0.6+0.4q) — 시뮬레이션용 '맞는 prior'를 만들 때 쓴다."""
    return np.array([BASE_ARMS[a][0] * (0.6 + 0.4 * BASE_ARMS[a][1]) for a in ARMS])


class Population:
    """그룹 N개의 숨은 참여 구조. 같은 (seed, 시나리오)면 정책과 상관없이 같은 그룹이 만들어진다."""

    def __init__(self, scenario: Scenario, n_groups: int, rng: np.random.Generator):
        self.scenario = scenario
        self.n_groups = n_groups
        self.members = rng.choice(MEMBER_SIZES, size=n_groups, p=MEMBER_WEIGHTS)
        self.daily = rng.choice(DAILY_COUNTS, size=n_groups, p=DAILY_WEIGHTS)
        self.member_mask = np.arange(MAX_MEMBERS)[None, :] < self.members[:, None]  # (G, M)
        base_p = np.array([BASE_ARMS[a][0] for a in ARMS])
        base_q = np.array([BASE_ARMS[a][1] for a in ARMS])
        self.arm_offset = rng.normal(0.0, scenario.heterogeneity, size=(n_groups, len(ARMS)))  # (G, A)
        self.member_offset = rng.normal(0.0, MEMBER_SPREAD, size=(n_groups, MAX_MEMBERS))  # (G, M)
        self.base_logit = logit(base_p)
        self.quality = np.clip(base_q[None, :] + rng.normal(0.0, QUALITY_SPREAD, size=(n_groups, len(ARMS))), 0.05, 0.95)
        self.shifted = False
        # 가입일은 맨 마지막에 뽑아서, 가입일이 없는 시나리오의 그룹은 예전과 똑같이 만들어진다
        self.start_day = (rng.integers(0, scenario.join_spread + 1, size=n_groups) if scenario.join_spread
                          else np.zeros(n_groups, dtype=int))

    def joined(self, day: int) -> np.ndarray:
        """그날 이미 가입한 그룹 (G,)."""
        return self.start_day <= day

    def apply_day(self, day: int) -> None:
        """그날의 환경 변화(선호 뒤집힘)를 반영한다."""
        if self.scenario.shift_day is not None and day == self.scenario.shift_day and not self.shifted:
            self.arm_offset = -self.arm_offset
            self.shifted = True

    def pool_mask(self, day: int) -> np.ndarray:
        """그날 발행 풀(A,) — 새 틀은 들어오는 날 전까지 없다."""
        mask = np.ones(len(ARMS), dtype=bool)
        if self.scenario.new_arm is not None and day < self.scenario.new_arm_day:
            mask[ARMS.index(self.scenario.new_arm)] = False
        return mask

    def participation(self) -> np.ndarray:
        """멤버별 제출 확률 (G, A, M)."""
        logits = self.base_logit[None, :, None] + self.arm_offset[:, :, None] + self.member_offset[:, None, :]
        return sigmoid(logits) * self.member_mask[:, None, :]

    def expected_reward(self) -> np.ndarray:
        """미션 하나의 기대 보상 합 E[R] (G, A) — 후회(regret) 계산용 정답값."""
        return self.participation().sum(axis=2) * (0.6 + 0.4 * self.quality)

    def simulate(self, arms: np.ndarray, active: np.ndarray, rng: np.random.Generator):
        """고른 틀로 미션을 낸 결과. 반환: R(보상 합), n(대상 인원), S(제출 수) — 모두 (G,), 안 낸 그룹은 0."""
        g = np.arange(self.n_groups)
        safe = np.where(active, arms, 0)
        p = self.participation()[g, safe, :]  # (G, M)
        q = self.quality[g, safe][:, None]  # (G, 1)
        submitted = (rng.random(p.shape) < p) & self.member_mask & active[:, None]
        a, b = q * QUALITY_CONCENTRATION, (1.0 - q) * QUALITY_CONCENTRATION
        effort = rng.beta(np.broadcast_to(a, p.shape), np.broadcast_to(b, p.shape))
        engage = rng.beta(np.broadcast_to(a, p.shape), np.broadcast_to(b, p.shape))
        reward = submitted * (0.6 + 0.2 * effort + 0.2 * engage)
        n = np.where(active, self.members, 0)
        return reward.sum(axis=1), n, submitted.sum(axis=1)


def present_media(pool_mask: np.ndarray) -> np.ndarray:
    """풀에 틀이 하나라도 있는 매체 번호들(고정 순서)."""
    return np.array([m for m in range(len(MEDIA)) if pool_mask[MEDIA_OF == m].any()])


def media_orders(n_groups: int, pool_mask: np.ndarray, rng: np.random.Generator) -> np.ndarray:
    """그룹마다 그날의 매체 순서 (G, 매체 수). 서버처럼 풀에 틀이 있는 매체만 섞는다."""
    present = present_media(pool_mask)
    return np.array([rng.permutation(present) for _ in range(n_groups)])


def candidates(daily: np.ndarray, slot: int, yesterday_media: np.ndarray, orders: np.ndarray,
               pool_mask: np.ndarray, starts: np.ndarray | None = None) -> np.ndarray:
    """서버 MissionTemplateSelector 와 같은 후보 (G, A) — D4 계획의 후보 구성:

    - 하루 1개: 어제 매체를 뺀 매체들의 틀 전체(남는 매체가 없으면 풀 전체).
    - 하루 2개 이상: 슬롯 순서로 매체를 돌리고(position = (slot-1) % 매체 수) 그 매체의 틀들.
      같은 날 같은 매체가 다시 오면(round > 0) 서버처럼 처음 고른 틀의 다음 틀 하나뿐이다([starts]: 매체별 첫 선택).
    """
    n_groups = len(daily)
    n_media = orders.shape[1]
    present = np.zeros(len(MEDIA), dtype=bool)
    present[present_media(pool_mask)] = True
    out = np.zeros((n_groups, len(ARMS)), dtype=bool)

    single = (daily == 1) & (slot == 1)
    if single.any():
        allowed = present[None, :] & ~yesterday_media  # (G, 매체)
        allowed = np.where(allowed.any(axis=1)[:, None], allowed, present[None, :])
        out[single] = (pool_mask[None, :] & allowed[:, MEDIA_OF])[single]

    multi = (daily >= 2) & (slot <= daily)
    if multi.any():
        position, round_ = (slot - 1) % n_media, (slot - 1) // n_media
        media = orders[:, position]  # (G,)
        in_media = pool_mask[None, :] & (MEDIA_OF[None, :] == media[:, None])
        if round_ == 0 or starts is None:
            out[multi] = in_media[multi]
        else:
            for g in np.flatnonzero(multi):  # 같은 날 같은 매체가 다시 온 드문 경우만 하나씩
                arms = np.flatnonzero(in_media[g])
                first = starts[g, media[g]]
                pick = arms[(list(arms).index(first) + round_) % len(arms)] if first in arms else arms[0]
                out[g, pick] = True
    return out
