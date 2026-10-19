package com.sapphire.hawe.submission.service;

import com.sapphire.hawe.global.config.VisionProperties.Threshold;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TopicFitJudgeTests {
    private final TopicFitJudge judge = new TopicFitJudge();
    private final Threshold threshold = new Threshold(0.024, 0.017);

    @Test void boundariesAndStableComments() {
        assertEquals("PERFECT_FIT", judge.judge(0.024, threshold, 1).badge());
        assertNull(judge.judge(0.023, threshold, 1).badge());
        assertNotNull(judge.judge(0.017, threshold, 1).comment());
        assertNull(judge.judge(0.016, threshold, 1).comment());
        assertEquals(judge.judge(0.5, threshold, 4), judge.judge(0.5, threshold, 4));
        assertNotEquals(judge.judge(0.5, threshold, 4).comment(), judge.judge(0.5, threshold, 5).comment());
    }
}
