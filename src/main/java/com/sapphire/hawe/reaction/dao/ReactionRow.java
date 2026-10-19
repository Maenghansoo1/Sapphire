package com.sapphire.hawe.reaction.dao;

public class ReactionRow {
    private Long submissionId, userId;
    private String stickerType;
    private boolean ai;
    public Long getSubmissionId() { return submissionId; }
    public void setSubmissionId(Long submissionId) { this.submissionId = submissionId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getStickerType() { return stickerType; }
    public void setStickerType(String stickerType) { this.stickerType = stickerType; }
    public boolean isAi() { return ai; }
    public void setAi(boolean ai) { this.ai = ai; }
}
