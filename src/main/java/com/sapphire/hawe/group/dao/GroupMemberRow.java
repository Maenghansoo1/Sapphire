package com.sapphire.hawe.group.dao;

/** Read-model projection for {@code GET /groups/{groupId}/members}; not a table-backed entity. */
public class GroupMemberRow {

    private Long userId;
    private String nickname;
    private String characterKey;
    private String role;
    private long submissionCountThisMonth;
    private boolean stampedToday;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getCharacterKey() {
        return characterKey;
    }

    public void setCharacterKey(String characterKey) {
        this.characterKey = characterKey;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public long getSubmissionCountThisMonth() {
        return submissionCountThisMonth;
    }

    public void setSubmissionCountThisMonth(long submissionCountThisMonth) {
        this.submissionCountThisMonth = submissionCountThisMonth;
    }

    public boolean isStampedToday() {
        return stampedToday;
    }

    public void setStampedToday(boolean stampedToday) {
        this.stampedToday = stampedToday;
    }
}
