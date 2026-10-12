package com.sapphire.hawe.mission.dto;

import java.time.OffsetDateTime;
import java.util.Map;

public record MissionResponse(Long missionId, Long groupId, int slotNo, String content,
        String category, String template, String media, Map<String, Object> params,
        OffsetDateTime publishedAt, OffsetDateTime deadlineAt, boolean submittedByMe) {
}
