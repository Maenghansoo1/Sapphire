package com.sapphire.hawe.group.dto;

import com.sapphire.hawe.badge.dto.FeaturedBadgeView;

public record GroupMemberResponse(
        Long userId, String nickname, String characterKey, String profileImageUrl,
        boolean hasGroupProfile, String role, long submissionCountThisMonth, boolean stampedToday,
        FeaturedBadgeView featuredBadge
) {
}
