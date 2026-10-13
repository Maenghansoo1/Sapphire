package com.sapphire.hawe.group.dto;

import java.time.LocalDate;
import java.util.List;
import com.sapphire.hawe.mission.dto.TodayMissionSummary;

/** {@code todayMission}·{@code submittedToday}는 첫 슬롯(현재 미션 중 가장 작은 slotNo) 기준, 전체는 {@code todayMissions}(슬롯 순). */
public record GroupSummaryResponse(Long id, String name, String relationType, int memberCount,
        int maxMembers, List<MemberProfileResponse> members, LocalDate startDate,
        boolean submittedToday, TodayMissionSummary todayMission, List<TodayMissionSummary> todayMissions) {
}
