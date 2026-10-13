package com.sapphire.hawe.group.domain;

import java.time.LocalDateTime;

public class GroupMember {

    public static final String ROLE_OWNER = "owner";
    public static final String ROLE_MEMBER = "member";

    private Long id;
    private Long groupId;
    private Long userId;
    private String role;
    private LocalDateTime joinedAt;
    private String nicknameOverride;
    private Long characterIdOverride;
    private String profileImageUrlOverride;

    public String getNicknameOverride() { return nicknameOverride; }
    public void setNicknameOverride(String nicknameOverride) { this.nicknameOverride = nicknameOverride; }
    public Long getCharacterIdOverride() { return characterIdOverride; }
    public void setCharacterIdOverride(Long characterIdOverride) { this.characterIdOverride = characterIdOverride; }
    public String getProfileImageUrlOverride() { return profileImageUrlOverride; }
    public void setProfileImageUrlOverride(String profileImageUrlOverride) { this.profileImageUrlOverride = profileImageUrlOverride; }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

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

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public LocalDateTime getJoinedAt() {
        return joinedAt;
    }

    public void setJoinedAt(LocalDateTime joinedAt) {
        this.joinedAt = joinedAt;
    }
}
