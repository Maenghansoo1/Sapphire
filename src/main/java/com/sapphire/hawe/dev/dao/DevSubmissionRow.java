package com.sapphire.hawe.dev.dao;

import java.time.LocalDateTime;

public class DevSubmissionRow {
    private Long submissionId, userId;
    private String nickname, template;
    private LocalDateTime submittedAt;
    private boolean late;
    public Long getSubmissionId() { return submissionId; }
    public void setSubmissionId(Long value) { submissionId = value; }
    public Long getUserId() { return userId; }
    public void setUserId(Long value) { userId = value; }
    public String getNickname() { return nickname; }
    public void setNickname(String value) { nickname = value; }
    public String getTemplate() { return template; }
    public void setTemplate(String value) { template = value; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(LocalDateTime value) { submittedAt = value; }
    public boolean isLate() { return late; }
    public void setLate(boolean value) { late = value; }
}
