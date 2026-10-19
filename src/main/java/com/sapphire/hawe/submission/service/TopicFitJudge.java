package com.sapphire.hawe.submission.service;

import com.sapphire.hawe.global.config.VisionProperties.Threshold;

public class TopicFitJudge {
    private static final String[] PERFECT = {"주제랑 딱 맞는 사진이에요!", "오늘 주제를 제대로 담았어요", "이 사진, 주제 그 자체예요"};
    private static final String[] GOOD = {"주제를 잘 담았어요", "오늘 주제가 잘 느껴져요", "주제와 잘 어울리는 사진이에요"};
    private static final String[] LOW = {"사진 잘 받았어요", "오늘의 한 장, 잘 남겼어요", "이 사진도 오늘의 기록이에요"};

    public record Verdict(String badge, String comment) { }

    public Verdict judge(double fit, Threshold threshold, long submissionId) {
        if (fit >= threshold.perfect())
            return new Verdict("PERFECT_FIT", PERFECT[Math.floorMod(submissionId, PERFECT.length)]);
        if (fit >= threshold.good())
            return new Verdict(null, GOOD[Math.floorMod(submissionId, GOOD.length)]);
        return new Verdict(null, LOW[Math.floorMod(submissionId, LOW.length)]);
    }
}
