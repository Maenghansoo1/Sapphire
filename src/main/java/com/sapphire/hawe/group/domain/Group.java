package com.sapphire.hawe.group.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class Group {

    private Long id;
    private String name;
    private Long ownerId;
    private String inviteCode;
    private String relationType;
    private int dailyMissionCount;
    private LocalDate startDate;
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Long getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(Long ownerId) {
        this.ownerId = ownerId;
    }

    public String getInviteCode() {
        return inviteCode;
    }

    public void setInviteCode(String inviteCode) {
        this.inviteCode = inviteCode;
    }

    public String getRelationType() {
        return relationType;
    }

    public void setRelationType(String relationType) {
        this.relationType = relationType;
    }

    public int getDailyMissionCount() {
        return dailyMissionCount;
    }

    public void setDailyMissionCount(int dailyMissionCount) {
        this.dailyMissionCount = dailyMissionCount;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public int maxMembers() {
        return "COUPLE".equals(relationType) ? 2 : 6;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
