package com.sapphire.hawe.mission.service;

import com.sapphire.hawe.global.config.BanditProperties;
import com.sapphire.hawe.global.config.VisionProperties.Threshold;
import com.sapphire.hawe.mission.domain.BanditReward;
import com.sapphire.hawe.mission.service.BanditRewardCalculator.Submission;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** 밴딧 보상 계산(TODO_2/D2) — DB 없이 순수 계산만. */
class BanditRewardCalculatorTests {
    private static final BanditProperties DEFAULTS = BanditProperties.defaults();
    private static final Threshold PHOTO_NOW = new Threshold(0.024, 0.017);
    private final BanditRewardCalculator calculator = new BanditRewardCalculator(DEFAULTS,
            template -> "PHOTO_NOW".equals(template) ? PHOTO_NOW : null);

    private static Submission text(int length, boolean caption, long engagement) {
        return new Submission(length, caption, null, engagement);
    }

    @Test void nobodySubmittedMeansZero() {
        BanditReward reward = calculator.reward(1, 9, "TEXT_ACROSTIC", 4, List.of());
        assertEquals(0.0, reward.total(), 1e-9);
        assertEquals(4, reward.eligible());
        assertEquals(4.0, reward.beta(), 1e-9);  // β += n − R
    }

    @Test void everyoneSubmittedWithLeastEffortGetsBaseOnly() {
        BanditReward reward = calculator.reward(1, 9, "TEXT_FILL_BLANK", 4,
                List.of(text(0, false, 0), text(0, false, 0), text(0, false, 0), text(0, false, 0)));
        assertEquals(0.6 * 4, reward.total(), 1e-9);  // R = 0.6n
    }

    @Test void memberRewardNeverPassesOneAndTotalNeverPassesEligible() {
        // 2명 그룹: 한 사람이 반응을 몰아 받고 글도 상한 이상 + 캡션 → 그 사람 보상 1
        BanditReward reward = calculator.reward(1, 9, "TEXT_ONE_WORD", 2, List.of(text(50, true, 3), text(50, true, 0)));
        assertEquals(1.0 + 0.6 + 0.2, reward.total(), 1e-9);  // 1 + (0.6 + 0.2·1 + 0.2·0)
        assertTrue(reward.total() <= reward.eligible());
        assertThrows(IllegalArgumentException.class, () -> new BanditReward(1, 9, "X", 2, 2.5));
    }

    @Test void textEffortIsLengthOverTemplateCapPlusCaption() {
        assertEquals(0.5, calculator.effort("TEXT_ACROSTIC", text(15, false, 0)), 1e-9);  // 상한 30
        assertEquals(1.0, calculator.effort("TEXT_ACROSTIC", text(45, false, 0)), 1e-9);  // 넘으면 1
        assertEquals(0.7, calculator.effort("TEXT_ACROSTIC", text(15, true, 0)), 1e-9);  // 캡션 +0.2
        assertEquals(0.25, calculator.effort("TEXT_FILL_BLANK", text(10, false, 0)), 1e-9);  // 상한 40
        assertEquals(0.5, calculator.effort("TEXT_FILL_BLANK", new Submission(null, false, null, 0)), 1e-9);  // 본문 없음 → 중립
        assertEquals(1.0, calculator.effort("TEXT_BALANCE", text(0, true, 0)), 1e-9);  // 이유를 쓰면 1
        assertEquals(0.5, calculator.effort("TEXT_BALANCE", text(0, false, 0)), 1e-9);
    }

    @Test void photoEffortUsesTopicFitScoreOrNeutral() {
        assertEquals(0.5, calculator.effort("PHOTO_NOW", new Submission(null, false, null, 0)), 1e-9);  // 점수 없음
        assertEquals(0.6, calculator.effort("PHOTO_NOW", new Submission(null, false, 0.017, 0)), 1e-9);  // good → 60
        assertEquals(0.8, calculator.effort("PHOTO_NOW", new Submission(null, false, 0.024, 0)), 1e-9);  // perfect → 80
        assertEquals(1.0, calculator.effort("PHOTO_NOW", new Submission(null, false, 0.7, 0)), 1e-9);  // ≥ 0.5 → 100
        assertEquals(0.5, calculator.effort("PHOTO_GALLERY", new Submission(null, false, 0.3, 0)), 1e-9);  // 임계값 없는 틀
    }

    @Test void drawingEffortIsNeutral() {
        assertEquals(0.5, calculator.effort("DRAW_A_TO_B", new Submission(null, false, null, 9)), 1e-9);  // 결정 4
        assertEquals(0.5, calculator.effort(null, text(10, true, 0)), 1e-9);
    }

    @Test void engagementIsGentleAndNotDampedInSmallGroups() {
        Submission popular = text(0, false, 4);
        // 미션 평균 2 → 4 / (2 + 1) = 1.33 → 큰 그룹은 ÷2 = 0.67, 3명 이하 그룹은 그대로(1 에서 자름)
        assertEquals(4.0 / 3 / 2, BanditRewardCalculator.engage(popular, 2.0, 6), 1e-9);
        assertEquals(1.0, BanditRewardCalculator.engage(popular, 2.0, 3), 1e-9);
        assertEquals(0.0, BanditRewardCalculator.engage(text(0, false, 0), 2.0, 6), 1e-9);
    }

    @Test void emptyGroupIsRecordedWithoutStats() {
        BanditReward reward = calculator.reward(1, 9, "PHOTO_NOW", 0, List.of());
        assertEquals(0, reward.eligible());
        assertEquals(0.0, reward.total(), 1e-9);
    }

    @Test void propertiesHaveSimulationBackedDefaultsAndRejectBadWeights() {
        assertEquals(2.0, DEFAULTS.kappa());  // D1 시뮬레이션 결과
        assertEquals(0.05, DEFAULTS.epsilon());
        assertEquals(0.6 + 0.2 + 0.2, DEFAULTS.rewardBase() + DEFAULTS.rewardEffort() + DEFAULTS.rewardEngage(), 1e-12);
        assertThrows(IllegalArgumentException.class,
                () -> new BanditProperties(0.5, 0.2, 0.2, null, null, null, null, null, null, null, null, null, null, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> new BanditProperties(null, null, null, null, 11.0, null, null, null, null, null, null, null, null, null, null));
        assertFalse(DEFAULTS.enabled(), "반영 잡은 기본으로 꺼져 있어요");
        assertEquals(500, DEFAULTS.batchSize());
    }
}
