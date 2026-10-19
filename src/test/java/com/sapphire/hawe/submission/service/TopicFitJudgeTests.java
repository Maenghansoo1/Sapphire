package com.sapphire.hawe.submission.service;

import com.sapphire.hawe.global.config.VisionProperties.Threshold;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TopicFitJudgeTests {
    private final TopicFitJudge judge = new TopicFitJudge();
    private final Threshold threshold = new Threshold(0.024, 0.017);

    @Test void boundariesAndStableComments() {
        var perfect = judge.judge(0.024, threshold, 1);
        var good = judge.judge(0.017, threshold, 1);
        var low = judge.judge(0.016, threshold, 1);
        assertEquals("PERFECT_FIT", perfect.badge());
        assertNotNull(perfect.comment());
        assertNull(judge.judge(0.023, threshold, 1).badge());
        assertNull(good.badge());
        assertNotNull(good.comment());
        assertNull(low.badge());
        assertEquals("오늘의 한 장, 잘 남겼어요", low.comment());
        assertEquals("사진 잘 받았어요", judge.judge(0.0, threshold, 3).comment());
        assertEquals("이 사진도 오늘의 기록이에요", judge.judge(0.016, threshold, 2).comment());
        assertNotEquals(perfect.comment(), low.comment());
        assertNotEquals(good.comment(), low.comment());
        assertEquals(low, judge.judge(0.016, threshold, 1));
        assertNotEquals(low.comment(), judge.judge(0.016, threshold, 2).comment());
    }
}
