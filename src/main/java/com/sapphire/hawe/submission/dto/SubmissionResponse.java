package com.sapphire.hawe.submission.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.sapphire.hawe.group.dto.MemberProfileResponse;
import java.time.OffsetDateTime;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SubmissionResponse(Long id, Long missionId, MemberProfileResponse author, String template,
        String mediaUrl, String mediaType, String caption, String text, Integer number, String choice, Integer guess,
        List<ReactionSummary> reactions, int commentCount, AiReviewResponse aiReview, OffsetDateTime submittedAt,
        @JsonProperty("isLate") boolean isLate) { }
