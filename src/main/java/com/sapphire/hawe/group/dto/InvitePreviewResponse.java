package com.sapphire.hawe.group.dto;

import java.util.List;

public record InvitePreviewResponse(Long groupId, String name, String relationType,
        int memberCount, int maxMembers, List<MemberProfileResponse> members) {
}
