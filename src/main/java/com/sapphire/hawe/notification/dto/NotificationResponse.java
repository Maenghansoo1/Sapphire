package com.sapphire.hawe.notification.dto;

import java.time.OffsetDateTime;

public record NotificationResponse(Long id, String type, Long groupId, String groupName,
        FromUser fromUser, Long missionId, Long submissionId, String body, boolean isRead,
        OffsetDateTime createdAt) {
    public record FromUser(Long userId, String nickname, String characterKey, String profileImageUrl) { }
}
