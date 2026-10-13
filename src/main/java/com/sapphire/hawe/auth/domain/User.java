package com.sapphire.hawe.auth.domain;

import java.time.LocalDateTime;

public class User {

    private Long id;
    private String nickname;
    private String passwordHash;
    private String email;
    private Long characterId;
    private String profileImageUrl;
    private int tokenVersion;

    public int getTokenVersion() { return tokenVersion; }
    public void setTokenVersion(int tokenVersion) { this.tokenVersion = tokenVersion; }
    private LocalDateTime createdAt;
    private LocalDateTime termsAgreedAt;
    private LocalDateTime privacyAgreedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Long getCharacterId() {
        return characterId;
    }

    public void setCharacterId(Long characterId) {
        this.characterId = characterId;
    }

    public String getProfileImageUrl() { return profileImageUrl; }
    public void setProfileImageUrl(String profileImageUrl) { this.profileImageUrl = profileImageUrl; }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getTermsAgreedAt() {
        return termsAgreedAt;
    }

    public void setTermsAgreedAt(LocalDateTime termsAgreedAt) {
        this.termsAgreedAt = termsAgreedAt;
    }

    public LocalDateTime getPrivacyAgreedAt() {
        return privacyAgreedAt;
    }

    public void setPrivacyAgreedAt(LocalDateTime privacyAgreedAt) {
        this.privacyAgreedAt = privacyAgreedAt;
    }
}
