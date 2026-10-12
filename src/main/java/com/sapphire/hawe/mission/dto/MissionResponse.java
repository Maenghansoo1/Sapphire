package com.sapphire.hawe.mission.dto;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.List;

public record MissionResponse(Long missionId, Long groupId, int slotNo, int number, String content,
        String category, String template, String media, Map<String, Object> params,
        OffsetDateTime publishedAt, OffsetDateTime deadlineAt, OffsetDateTime closesAt,
        boolean submittedByMe, List<MissionMemberStatus> members) {
}
