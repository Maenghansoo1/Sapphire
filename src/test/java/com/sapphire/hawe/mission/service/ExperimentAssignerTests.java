package com.sapphire.hawe.mission.service;

import com.sapphire.hawe.global.config.ExperimentProperties;
import com.sapphire.hawe.mission.domain.ExperimentArm;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExperimentAssignerTests {

    private static final LocalDate START = LocalDate.of(2026, 10, 1);
    private final AiMissionGenerator ai = mock(AiMissionGenerator.class);

    @Test
    void sameGroupAndDateAlwaysGetTheSameArm() {
        when(ai.available()).thenReturn(true);
        ExperimentAssigner assigner = assigner(true, 0.5, "salt");
        for (long groupId = 1; groupId <= 50; groupId++) {
            Optional<ExperimentArm> arm = assigner.assign(groupId, START);
            assertTrue(arm.isPresent());
            assertEquals(arm, assigner.assign(groupId, START));
            assertEquals(arm, assigner(true, 0.5, "salt").assign(groupId, START));
        }
    }

    @Test
    void ratioIsCloseToTheSetting() {
        when(ai.available()).thenReturn(true);
        assertEquals(0.5, aiShare(assigner(true, 0.5, "salt")), 0.02);
        assertEquals(0.2, aiShare(assigner(true, 0.2, "salt")), 0.02);
        assertEquals(0.0, aiShare(assigner(true, 0.0, "salt")));
        assertEquals(1.0, aiShare(assigner(true, 1.0, "salt")));
    }

    @Test
    void groupsSwitchArmsAcrossDaysForPairedComparison() {
        when(ai.available()).thenReturn(true);
        ExperimentAssigner assigner = assigner(true, 0.5, "salt");
        int both = 0;
        for (long groupId = 1; groupId <= 100; groupId++) {
            boolean ai = false, random = false;
            for (int day = 0; day < 14; day++) {
                if (assigner.assign(groupId, START.plusDays(day)).orElseThrow() == ExperimentArm.AI) ai = true;
                else random = true;
            }
            if (ai && random) both++;
        }
        assertTrue(both >= 99, "2주면 거의 모든 그룹이 두 군을 겪는다: " + both);
    }

    @Test
    void anotherSaltReshufflesTheAssignment() {
        when(ai.available()).thenReturn(true);
        ExperimentAssigner first = assigner(true, 0.5, "실험-1");
        ExperimentAssigner second = assigner(true, 0.5, "실험-2");
        int changed = 0;
        for (long groupId = 1; groupId <= 1000; groupId++)
            if (!first.assign(groupId, START).equals(second.assign(groupId, START))) changed++;
        assertEquals(500, changed, 60);
    }

    @Test
    void noArmWhenTheExperimentIsOffOrAiCannotRun() {
        when(ai.available()).thenReturn(true);
        assertEquals(Optional.empty(), assigner(false, 0.5, "salt").assign(1, START));
        when(ai.available()).thenReturn(false);
        assertEquals(Optional.empty(), assigner(true, 0.5, "salt").assign(1, START));
        assertThrows(IllegalArgumentException.class, () -> new ExperimentProperties(true, 1.5, null));
        assertEquals("hawe-topic-1", new ExperimentProperties(false, null, " ").salt());
    }

    @Test
    void bucketIsUniformlyBetweenZeroAndOne() {
        double min = 1, max = 0;
        for (long groupId = 1; groupId <= 2000; groupId++) {
            double bucket = ExperimentAssigner.bucket("salt", groupId, START);
            assertTrue(bucket >= 0 && bucket < 1);
            min = Math.min(min, bucket);
            max = Math.max(max, bucket);
        }
        assertTrue(min < 0.01 && max > 0.99);
    }

    // 그룹 100개 × 100일 = 10,000개
    private double aiShare(ExperimentAssigner assigner) {
        int ai = 0;
        for (long groupId = 1; groupId <= 100; groupId++)
            for (int day = 0; day < 100; day++)
                if (assigner.assign(groupId, START.plusDays(day)).orElseThrow() == ExperimentArm.AI) ai++;
        return ai / 10_000.0;
    }

    private ExperimentAssigner assigner(boolean enabled, double ratio, String salt) {
        return new ExperimentAssigner(new ExperimentProperties(enabled, ratio, salt), ai);
    }
}
