package com.sapphire.hawe.comment.dto;

import com.sapphire.hawe.group.dto.MemberProfileResponse;
import java.time.OffsetDateTime;
import java.util.List;

public record CommentResponse(Long id, MemberProfileResponse author, String content, String imageUrl,
        List<Long> mentionedUserIds, OffsetDateTime createdAt, boolean mine) { }
