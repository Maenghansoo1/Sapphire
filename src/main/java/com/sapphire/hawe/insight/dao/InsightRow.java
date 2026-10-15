package com.sapphire.hawe.insight.dao;

public class InsightRow {
    private Long id;
    private Long missionId;
    private Long submissionId;
    private Long userId;
    private String content;
    private String template;
    private String text;
    private String stickerType;
    private String nickname;
    private String characterKey;
    private String profileImageUrl;
    private String status;
    private String payload;
    private String month;
    private java.time.LocalDateTime publishedAt;
    private java.time.LocalDateTime submittedAt;
    private java.time.LocalDateTime updatedAt;
    private java.time.LocalDateTime generatedAt;
    private java.time.LocalDateTime expiresAt;
    private int memberCount;
    private int count;
    private boolean late;
    private boolean expired;
    private boolean abandoned;
    public Long getId() { return id; }
    public void setId(Long value) { id = value; }
    public Long getMissionId() { return missionId; }
    public void setMissionId(Long value) { missionId = value; }
    public Long getSubmissionId() { return submissionId; }
    public void setSubmissionId(Long value) { submissionId = value; }
    public Long getUserId() { return userId; }
    public void setUserId(Long value) { userId = value; }
    public String getContent() { return content; }
    public void setContent(String value) { content = value; }
    public String getTemplate() { return template; }
    public void setTemplate(String value) { template = value; }
    public String getText() { return text; }
    public void setText(String value) { text = value; }
    public String getStickerType() { return stickerType; }
    public void setStickerType(String value) { stickerType = value; }
    public String getNickname() { return nickname; }
    public void setNickname(String value) { nickname = value; }
    public String getCharacterKey() { return characterKey; }
    public void setCharacterKey(String value) { characterKey = value; }
    public String getProfileImageUrl() { return profileImageUrl; }
    public void setProfileImageUrl(String value) { profileImageUrl = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { status = value; }
    public String getPayload() { return payload; }
    public void setPayload(String value) { payload = value; }
    public String getMonth() { return month; }
    public void setMonth(String value) { month = value; }
    public java.time.LocalDateTime getPublishedAt() { return publishedAt; }
    public void setPublishedAt(java.time.LocalDateTime value) { publishedAt = value; }
    public java.time.LocalDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(java.time.LocalDateTime value) { submittedAt = value; }
    public java.time.LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(java.time.LocalDateTime value) { updatedAt = value; }
    public java.time.LocalDateTime getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(java.time.LocalDateTime value) { generatedAt = value; }
    public java.time.LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(java.time.LocalDateTime value) { expiresAt = value; }
    public int getMemberCount() { return memberCount; }
    public void setMemberCount(int value) { memberCount = value; }
    public int getCount() { return count; }
    public void setCount(int value) { count = value; }
    public boolean isLate() { return late; }
    public void setLate(boolean value) { late = value; }
    public boolean isExpired() { return expired; }
    public void setExpired(boolean value) { expired = value; }
    public boolean isAbandoned() { return abandoned; }
    public void setAbandoned(boolean value) { abandoned = value; }
}
