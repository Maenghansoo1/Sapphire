package com.sapphire.hawe.group.dto;

import java.time.LocalDate;
import java.util.List;
import com.sapphire.hawe.mission.dto.TodayMissionSummary;

public record GroupSummaryResponse(Long id, String name, String relationType, int memberCount,
        int maxMembers, List<MemberProfileResponse> members, LocalDate startDate,
        boolean submittedToday, TodayMissionSummary todayMission) {
}
