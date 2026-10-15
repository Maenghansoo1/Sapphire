package com.sapphire.hawe.insight.domain;

import java.util.List;

public record InsightPayload(boolean empty, Activity activityIndex, Fastest fastest,
        List<Word> topWords, MostReacted mostReacted) {
    public record Activity(int score, double participationRate, double onTimeRate, double interactionRate) { }
    public record Fastest(Long userId, int firstCount, long averageResponseSeconds, long fastestSingleSeconds) { }
    public record Word(String word, int count, int memberCount) { }
    public record MostReacted(Long submissionId, Long missionId, String missionContent, String template,
            Long authorUserId, int reactionCount, int commentCount, String topSticker) { }
}
