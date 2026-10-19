package com.sapphire.hawe.submission.service;

import com.sapphire.hawe.global.config.VisionProperties.Threshold;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TopicFitJudgeScoreTests {
    TopicFitJudge judge = new TopicFitJudge();
    Threshold now = new Threshold(0.024, 0.017);

    @Test void scoreUsesVerdictBoundaries() {
        assertEquals(0, judge.score(0, now));
        assertEquals(60, judge.score(now.good(), now));
        assertEquals(80, judge.score(now.perfect(), now));
        assertEquals(100, judge.score(0.5, now));
        assertEquals(100, judge.score(1, now));
        assertEquals(0, judge.score(Double.NaN, now));
        assertEquals(0, judge.score(Double.NEGATIVE_INFINITY, now));
        assertEquals(0, judge.score(-1, now));
        assertEquals(80, judge.score(0.2, new Threshold(0.2, 0.2)));
        assertTrue(judge.score(0.014, now) < 60);
    }
}
