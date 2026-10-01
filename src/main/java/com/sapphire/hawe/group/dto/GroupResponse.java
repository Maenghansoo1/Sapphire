package com.sapphire.hawe.group.dto;

public record GroupResponse(Long id, String name, Long ownerId, String missionPublishTime, String missionDeadlineTime) {
}
