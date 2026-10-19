package com.sapphire.hawe.comment.domain;

import java.util.List;

public record CommentCreatedEvent(Long commentId, Long groupId, Long missionId, Long submissionId,
        Long fromUserId, Long submissionUserId, List<Long> mentionedUserIds) { }
