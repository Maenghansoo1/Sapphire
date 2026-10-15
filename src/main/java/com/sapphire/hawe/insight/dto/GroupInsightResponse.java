package com.sapphire.hawe.insight.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.sapphire.hawe.insight.domain.InsightPayload;
import java.time.OffsetDateTime;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record GroupInsightResponse(Long groupId, String month, String status, boolean stale, boolean empty,
        OffsetDateTime generatedAt, ActivityIndex activityIndex, Fastest fastest,
        List<InsightPayload.Word> topWords, MostReacted mostReacted, List<String> highlights) {
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ActivityIndex(int score, Integer deltaFromPreviousMonth,
            double participationRate, double onTimeRate, double interactionRate) { }
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Fastest(Long userId, String nickname, String characterKey, String profileImageUrl,
            int firstCount, long averageResponseSeconds, long fastestSingleSeconds) { }
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record MostReacted(Long submissionId, Long missionId, String missionContent, String template,
            Long authorUserId, String authorNickname, int reactionCount, int commentCount, String topSticker) { }
}
