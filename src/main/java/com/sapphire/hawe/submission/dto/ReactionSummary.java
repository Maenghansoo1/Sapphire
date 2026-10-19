package com.sapphire.hawe.submission.dto;

import java.util.List;

public record ReactionSummary(String stickerType, int count, boolean reactedByMe, List<Long> userIds,
        boolean aiReacted) { }
