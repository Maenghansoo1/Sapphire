import numpy as np

from env import ARMS, MEDIA, MEDIA_OF, SCENARIOS, Population, candidates, media_orders

PHOTO, TEXT, DRAW = (MEDIA.index(m) for m in MEDIA)
ALL = np.ones(len(ARMS), dtype=bool)


def arms_of(mask_row):
    return {ARMS[i] for i in np.flatnonzero(mask_row)}


def test_one_mission_a_day_skips_yesterdays_media():
    orders = np.array([[PHOTO, TEXT, DRAW]] * 2)
    yesterday = np.zeros((2, 3), dtype=bool)
    yesterday[0, PHOTO] = True  # 어제 사진
    yesterday[1] = True  # 어제 모든 매체(하루 여러 개였던 그룹) → 남는 게 없으면 풀 전체
    cand = candidates(np.array([1, 1]), 1, yesterday, orders, ALL)
    assert arms_of(cand[0]) == {"TEXT_ACROSTIC", "TEXT_FILL_BLANK", "DRAW_A_TO_B"}
    assert arms_of(cand[1]) == set(ARMS)
    # 하루 1개면 슬롯 2부터는 후보가 없어요
    assert not candidates(np.array([1, 1]), 2, yesterday, orders, ALL).any()


def test_several_missions_a_day_rotate_media_by_slot():
    orders = np.array([[TEXT, DRAW, PHOTO]])
    daily = np.array([3])
    none = np.zeros((1, 3), dtype=bool)
    assert arms_of(candidates(daily, 1, none, orders, ALL)[0]) == {"TEXT_ACROSTIC", "TEXT_FILL_BLANK"}
    assert arms_of(candidates(daily, 2, none, orders, ALL)[0]) == {"DRAW_A_TO_B"}
    assert arms_of(candidates(daily, 3, none, orders, ALL)[0]) == {"PHOTO_NOW", "PHOTO_GALLERY", "PHOTO_COLOR"}
    assert not candidates(np.array([2]), 3, none, orders, ALL).any()  # 하루 2개면 슬롯 3 없음


def test_same_media_again_uses_next_template_like_the_server():
    pool = ALL.copy()
    pool[ARMS.index("DRAW_A_TO_B")] = False  # 그림 매체가 없어져 매체 2개
    orders = media_orders(1, pool, np.random.default_rng(0))
    assert orders.shape == (1, 2)
    starts = np.full((1, 3), -1)
    first_media = orders[0, 0]
    first_arm = np.flatnonzero(pool & (MEDIA_OF == first_media))[0]
    starts[0, first_media] = first_arm
    cand = candidates(np.array([3]), 3, np.zeros((1, 3), dtype=bool), orders, pool, starts)
    in_media = list(np.flatnonzero(pool & (MEDIA_OF == first_media)))
    assert np.flatnonzero(cand[0]).tolist() == [in_media[1]]  # 슬롯 3 = 첫 매체의 다음 틀 하나


def test_population_is_reproducible_and_bounded():
    a = Population(SCENARIOS["base"], 50, np.random.default_rng(7))
    b = Population(SCENARIOS["base"], 50, np.random.default_rng(7))
    assert np.array_equal(a.expected_reward(), b.expected_reward())
    expected = a.expected_reward()
    assert expected.shape == (50, len(ARMS))
    assert (expected >= 0).all() and (expected <= a.members[:, None]).all()
    assert set(a.members) <= {2, 3, 4, 5, 6} and set(a.daily) <= {1, 2, 3}


def test_inactive_groups_get_nothing():
    population = Population(SCENARIOS["base"], 4, np.random.default_rng(1))
    reward, n, submitted = population.simulate(np.array([0, 1, -1, 2]), np.array([True, True, False, True]),
                                               np.random.default_rng(2))
    assert reward[2] == 0 and n[2] == 0 and submitted[2] == 0
    assert (reward <= n).all() and (submitted <= n).all()


def test_staggered_groups_join_over_time():
    population = Population(SCENARIOS["staggered"], 500, np.random.default_rng(0))
    assert population.start_day.min() >= 0 and population.start_day.max() <= 60
    assert population.joined(0).sum() < population.joined(30).sum() < population.joined(60).sum() == 500
    # 가입일이 없는 시나리오는 모두 첫날, 그룹 구성도 그대로
    base = Population(SCENARIOS["base"], 500, np.random.default_rng(0))
    assert base.joined(0).all()


def test_scenarios_change_pool_and_preferences():
    new_arm = Population(SCENARIOS["new_arm"], 3, np.random.default_rng(0))
    assert not new_arm.pool_mask(0)[ARMS.index("DRAW_A_TO_B")]
    assert new_arm.pool_mask(30)[ARMS.index("DRAW_A_TO_B")]
    shift = Population(SCENARIOS["shift"], 3, np.random.default_rng(0))
    before = shift.arm_offset.copy()
    shift.apply_day(45)
    assert np.allclose(shift.arm_offset, -before)
