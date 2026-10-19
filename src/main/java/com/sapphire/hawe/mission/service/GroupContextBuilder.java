package com.sapphire.hawe.mission.service;

import com.sapphire.hawe.global.config.MissionAiProperties;
import com.sapphire.hawe.group.dao.GroupMemberMapper;
import com.sapphire.hawe.group.domain.Group;
import com.sapphire.hawe.mission.dao.MissionMapper;
import com.sapphire.hawe.mission.dao.MissionStatRow;
import com.sapphire.hawe.mission.domain.AnniversaryResolver;
import com.sapphire.hawe.mission.domain.GenerationContext;
import com.sapphire.hawe.mission.domain.GroupSignals;
import com.sapphire.hawe.mission.domain.MissionTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 발행할 때 그룹 맞춤 정보를 모은다: 관계, 기념일, 지난 기록 신호(미션 단위 숫자). 최근 미션 문장은 슬롯마다
 * 발행기가 {@link GenerationContext#withRecentMissions} 로 채운다.
 */
@Component
public class GroupContextBuilder {

    static final int SIGNAL_DAYS = 30;
    static final int SIGNAL_MISSIONS = 10;
    // 30일 × 하루 최대 5개
    static final int MAX_STAT_ROWS = 150;
    private static final List<String> MEDIA_ORDER = List.of("PHOTO", "TEXT", "DRAW");

    private final MissionMapper missions;
    private final GroupMemberMapper members;
    private final MissionAiProperties properties;

    public GroupContextBuilder(MissionMapper missions, GroupMemberMapper members, MissionAiProperties properties) {
        this.missions = missions;
        this.members = members;
        this.properties = properties;
    }

    public GenerationContext build(Group group, LocalDate date) {
        LocalDate created = group.getCreatedAt() == null ? date : group.getCreatedAt().toLocalDate();
        return new GenerationContext(date, group.getRelationType(), List.of(),
                AnniversaryResolver.resolve(group.getRelationType(), group.getStartDate(), created, date,
                        properties.calendarDays()),
                signals(group.getId(), created, date));
    }

    GroupSignals signals(Long groupId, LocalDate created, LocalDate date) {
        int memberCount = (int) members.countByGroupId(groupId);
        long daysTogether = ChronoUnit.DAYS.between(created, date) + 1;
        List<MissionStatRow> rows = missions.findClosedStatsByGroupId(groupId, date.minusDays(SIGNAL_DAYS), MAX_STAT_ROWS);
        List<GroupSignals.MissionStat> recent = rows.stream().limit(SIGNAL_MISSIONS)
                .map(row -> new GroupSignals.MissionStat(row.getTemplate(), row.getContent(),
                        round(participation(row)), round(engagement(row))))
                .toList();
        Map<String, double[]> totals = new LinkedHashMap<>();  // 매체 → [참여율 합, 미션 수]
        for (MissionStatRow row : rows) {
            String media = media(row.getTemplate());
            if (media == null) continue;
            double[] total = totals.computeIfAbsent(media, ignored -> new double[2]);
            total[0] += participation(row);
            total[1]++;
        }
        Map<String, Double> mediaParticipation = new LinkedHashMap<>();
        for (String media : MEDIA_ORDER)
            if (totals.containsKey(media)) mediaParticipation.put(media, round(totals.get(media)[0] / totals.get(media)[1]));
        return new GroupSignals(memberCount, daysTogether, recent, mediaParticipation);
    }

    private static double participation(MissionStatRow row) {
        return row.getMemberCount() <= 0 ? 0 : Math.min(1, (double) row.getSubmittedCount() / row.getMemberCount());
    }

    private static double engagement(MissionStatRow row) {
        return row.getSubmittedCount() == 0 ? 0 : (double) row.getEngagementCount() / row.getSubmittedCount();
    }

    private static double round(double value) {
        return Math.round(value * 100) / 100.0;
    }

    private static String media(String template) {
        try {
            return MissionTemplate.valueOf(template).media();
        } catch (IllegalArgumentException | NullPointerException ignored) {
            return null;  // 지금은 없는 옛 틀
        }
    }
}
