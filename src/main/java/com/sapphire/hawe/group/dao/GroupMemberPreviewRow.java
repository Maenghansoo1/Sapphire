package com.sapphire.hawe.group.dao;

public class GroupMemberPreviewRow {
    private Long groupId;
    private Long userId;
    private String nickname;
    private String characterKey;
    private String profileImageUrl;

    public String getProfileImageUrl() { return profileImageUrl; }
    public void setProfileImageUrl(String profileImageUrl) { this.profileImageUrl = profileImageUrl; }

    public Long getGroupId() {
        return groupId;
    }

    public void setGroupId(Long groupId) {
        this.groupId = groupId;
    }

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
}
