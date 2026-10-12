package com.sapphire.hawe.submission.dao;

import java.time.LocalDateTime;

public class SubmissionInsertRow {
    private Long id;
    private final Long missionId;
    private final Long userId;
    private final String mediaUrl;
    private final String caption;
    private final LocalDateTime submittedAt;
    private final boolean late;
    private final String mediaType;
    private final String answerData;

    public SubmissionInsertRow(Long missionId, Long userId, String mediaUrl, String caption,
            LocalDateTime submittedAt, boolean late, String mediaType, String answerData) {
        this.missionId = missionId; this.userId = userId; this.mediaUrl = mediaUrl;
        this.caption = caption; this.submittedAt = submittedAt; this.late = late;
        this.mediaType = mediaType; this.answerData = answerData;
    }
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getMissionId() { return missionId; }
    public Long getUserId() { return userId; }
    public String getMediaUrl() { return mediaUrl; }
    public String getCaption() { return caption; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public boolean isLate() { return late; }
    public String getMediaType() { return mediaType; }
    public String getAnswerData() { return answerData; }
}
