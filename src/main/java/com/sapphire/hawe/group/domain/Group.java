package com.sapphire.hawe.group.domain;

import java.time.LocalDateTime;
import java.time.LocalTime;

public class Group {

    private Long id;
    private String name;
    private Long ownerId;
    private LocalTime missionPublishTime;
    private LocalTime missionDeadlineTime;
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

    public LocalTime getMissionPublishTime() {
        return missionPublishTime;
    }

    public void setMissionPublishTime(LocalTime missionPublishTime) {
        this.missionPublishTime = missionPublishTime;
    }

    public LocalTime getMissionDeadlineTime() {
        return missionDeadlineTime;
    }

    public void setMissionDeadlineTime(LocalTime missionDeadlineTime) {
        this.missionDeadlineTime = missionDeadlineTime;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
