package com.sapphire.hawe.submission.dao;

import java.time.LocalDateTime;

public class SubmissionRow {
    private Long id, missionId, groupId, userId;
    private String nickname, characterKey, template, mediaUrl, mediaType, caption, answerData;
    private LocalDateTime submittedAt;
    private boolean late;
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getMissionId() { return missionId; }
    public void setMissionId(Long missionId) { this.missionId = missionId; }
    public Long getGroupId() { return groupId; }
    public void setGroupId(Long groupId) { this.groupId = groupId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public String getCharacterKey() { return characterKey; }
    public void setCharacterKey(String characterKey) { this.characterKey = characterKey; }
    public String getTemplate() { return template; }
    public void setTemplate(String template) { this.template = template; }
    public String getMediaUrl() { return mediaUrl; }
    public void setMediaUrl(String mediaUrl) { this.mediaUrl = mediaUrl; }
    public String getMediaType() { return mediaType; }
    public void setMediaType(String mediaType) { this.mediaType = mediaType; }
    public String getCaption() { return caption; }
    public void setCaption(String caption) { this.caption = caption; }
    public String getAnswerData() { return answerData; }
    public void setAnswerData(String answerData) { this.answerData = answerData; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(LocalDateTime submittedAt) { this.submittedAt = submittedAt; }
    public boolean isLate() { return late; }
    public void setLate(boolean late) { this.late = late; }
}
