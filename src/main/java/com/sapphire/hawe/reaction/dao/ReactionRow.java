package com.sapphire.hawe.reaction.dao;

public class ReactionRow {
    private Long submissionId, userId;
    private String stickerType;
    public Long getSubmissionId() { return submissionId; }
    public void setSubmissionId(Long submissionId) { this.submissionId = submissionId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getStickerType() { return stickerType; }
    public void setStickerType(String stickerType) { this.stickerType = stickerType; }
}
