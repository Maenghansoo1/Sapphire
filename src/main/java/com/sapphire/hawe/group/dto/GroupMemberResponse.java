package com.sapphire.hawe.group.dto;

public record GroupMemberResponse(
        Long userId, String nickname, String characterKey, String profileImageUrl,
        String role, long submissionCountThisMonth, boolean stampedToday
) {
}
