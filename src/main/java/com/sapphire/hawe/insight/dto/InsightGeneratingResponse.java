package com.sapphire.hawe.insight.dto;

public record InsightGeneratingResponse(Long groupId, String month, String status, int retryAfterSeconds) {
    public InsightGeneratingResponse(Long groupId, String month) {
        this(groupId, month, "GENERATING", 3);
    }
}
