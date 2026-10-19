package com.sapphire.hawe.submission.dao;

public class AiReviewRow {
    private Long submissionId;
    private String status, badge, comment, modelVersion;
    private Double fitScore;
    private int attempts;
    public Long getSubmissionId() { return submissionId; }
    public void setSubmissionId(Long value) { submissionId = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { status = value; }
    public String getBadge() { return badge; }
    public void setBadge(String value) { badge = value; }
    public String getComment() { return comment; }
    public void setComment(String value) { comment = value; }
    public String getModelVersion() { return modelVersion; }
    public void setModelVersion(String value) { modelVersion = value; }
    public Double getFitScore() { return fitScore; }
    public void setFitScore(Double value) { fitScore = value; }
    public int getAttempts() { return attempts; }
    public void setAttempts(int value) { attempts = value; }
}
