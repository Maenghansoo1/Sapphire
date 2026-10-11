package com.sapphire.hawe.group.domain;

import java.time.LocalDateTime;

public class Group {

    private Long id;
    private String name;
    private Long ownerId;
    private String relationType;
    private int dailyMissionCount;
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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
