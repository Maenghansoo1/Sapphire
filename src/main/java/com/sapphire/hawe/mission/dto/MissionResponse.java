package com.sapphire.hawe.mission.dto;

import java.time.LocalDateTime;
import java.util.Map;

public record MissionResponse(Long missionId, Long groupId, int slotNo, String content,
        String category, String template, String media, Map<String, Object> params,
        LocalDateTime publishedAt, LocalDateTime deadlineAt, boolean submittedByMe) {
}
