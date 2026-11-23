package com.sapphire.hawe.mission.service;

import com.sapphire.hawe.global.config.BanditProperties;
import com.sapphire.hawe.global.config.VisionProperties.Threshold;
import com.sapphire.hawe.mission.domain.BanditReward;
import com.sapphire.hawe.submission.service.TopicFitJudge;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 닫힌 미션의 밴딧 보상 계산(TODO_2/D2, AI_FEATURES.md §4.2). DB·스프링 없이 돌아가는 순수 계산이다.
 * <ul>
 *   <li>멤버 보상: 안 냄 0, 냄 {@code base + wEffort·effort + wEngage·engage} (기본 0.6/0.2/0.2, 0~1). 늦게 낸 것도 낸 것으로 친다.</li>
 *   <li>effort(0~1, 틀마다): 글은 공백 뺀 글자 수 ÷ 틀별 상한(+ 캡션 0.2), 밸런스 게임은 이유(캡션)를 쓰면 1,
 *       사진은 사진 적합도 점수(B10 표시 환산 0~100)/100 — 점수가 없으면 중립 0.5, 그림은 중립 0.5(결정 4: 쓰지 않음).</li>
 *   <li>engage(0~1): 그 제출물의 반응+댓글 수 ÷ (그 미션 제출물 평균 + 1) 을 2 로 나눠 1 에서 자른다(인기 투표가 되지 않게 완만하게).
 *       대상이 3명 이하인 작은 그룹은 반응할 사람 자체가 적어서 2 로 나누지 않는다.</li>
 * </ul>
 * 사진 점수는 서버 안 계산에만 쓰고 어디에도 내보내지 않는다(B10 규칙). AI 마스코트의 반응·댓글은 매퍼에서 이미 빠져 있다.
 */
public class BanditRewardCalculator {
    /** 글 틀마다 effort 1 이 되는 글자 수(공백 제외). */
    static final Map<String, Integer> TEXT_CAPS = Map.of(
            "TEXT_ACROSTIC", 30, "TEXT_FILL_BLANK", 40, "TEXT_ONE_WORD", 10, "TEXT_REVIEW", 40);
    static final double CAPTION_BONUS = 0.2;
    static final double NEUTRAL = 0.5;
    static final int SMALL_GROUP = 3;

    /** 제출물 하나에서 보상에 쓰는 값. [textLength]·[fitScore] 는 없으면 null. */
    public record Submission(Integer textLength, boolean caption, Double fitScore, long engagement) { }

    private final BanditProperties properties;
    private final Function<String, Threshold> thresholds;
    private final TopicFitJudge judge = new TopicFitJudge();

    /** [thresholds]: 사진 틀 → 사진 적합도 임계값(B10, 없으면 null). */
    public BanditRewardCalculator(BanditProperties properties, Function<String, Threshold> thresholds) {
        this.properties = properties;
        this.thresholds = thresholds;
    }

    public BanditReward reward(long missionId, long groupId, String template, long eligible, List<Submission> submissions) {
        if (eligible <= 0) return new BanditReward(missionId, groupId, template, 0, 0);
        double meanEngagement = submissions.stream().mapToLong(Submission::engagement).average().orElse(0);
        double total = 0;
        for (Submission submission : submissions) total += memberReward(template, submission, meanEngagement, eligible);
        return new BanditReward(missionId, groupId, template, eligible, Math.min(total, eligible));
    }

    double memberReward(String template, Submission submission, double meanEngagement, long eligible) {
        double reward = properties.rewardBase() + properties.rewardEffort() * effort(template, submission)
                + properties.rewardEngage() * engage(submission, meanEngagement, eligible);
        return clamp(reward);
    }

    double effort(String template, Submission submission) {
        if (template == null) return NEUTRAL;
        if (template.equals("TEXT_BALANCE")) return submission.caption() ? 1.0 : NEUTRAL;
        if (template.startsWith("TEXT_")) {
            Integer cap = TEXT_CAPS.get(template);
            if (cap == null || submission.textLength() == null) return NEUTRAL;
            return clamp((double) submission.textLength() / cap + (submission.caption() ? CAPTION_BONUS : 0));
        }
        if (template.startsWith("PHOTO_")) {
            Threshold threshold = thresholds.apply(template);
            if (submission.fitScore() == null || threshold == null) return NEUTRAL;
            return judge.score(submission.fitScore(), threshold) / 100.0;
        }
        return NEUTRAL;  // 그림(결정 4)·모르는 틀
    }

    static double engage(Submission submission, double meanEngagement, long eligible) {
        double relative = submission.engagement() / (meanEngagement + 1.0);
        return clamp(eligible <= SMALL_GROUP ? relative : relative / 2.0);
    }

    private static double clamp(double value) {
        return Double.isFinite(value) ? Math.max(0.0, Math.min(1.0, value)) : 0.0;
    }
}
