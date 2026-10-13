package com.sapphire.hawe.mission.service;

import com.sapphire.hawe.global.config.MissionAiProperties;
import com.sapphire.hawe.group.dao.GroupMemberMapper;
import com.sapphire.hawe.group.domain.Group;
import com.sapphire.hawe.mission.dao.MissionMapper;
import com.sapphire.hawe.mission.dao.MissionStatRow;
import com.sapphire.hawe.mission.domain.GenerationContext;
import com.sapphire.hawe.mission.domain.GroupSignals;
import com.sapphire.hawe.mission.domain.SpecialDay;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class GroupContextBuilderTests {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 13);

    private final MissionMapper missions = mock(MissionMapper.class);
    private final GroupMemberMapper members = mock(GroupMemberMapper.class);
    private final GroupContextBuilder builder = new GroupContextBuilder(missions, members,
            new MissionAiProperties(false, null, null, null, null, null, null, null, null, null, null, null, null,
                    List.of("10-20=그룹 소풍날")));

    @Test
    void collectsRelationAnniversariesAndMissionLevelSignals() {
        Group group = group("COUPLE", TODAY.minusDays(99), TODAY.minusDays(30));   // 연인 100일 = 오늘
        when(members.countByGroupId(11L)).thenReturn(2L);
        List<MissionStatRow> rows = List.of(
                stat("TEXT_BALANCE", "짜장 vs 짬뽕", 2, 2, 3),
                stat("PHOTO_NOW", "책상 위 TMI", 1, 2, 0),
                stat("PHOTO_COLOR", "노란색 원픽", 0, 2, 0),
                stat("VOICE_LINE", "옛 음성 미션", 2, 2, 0),        // 없어진 틀은 매체별 평균에서 뺀다
                stat("DRAW_SKETCH", "고양이 그리기", 3, 2, 1));     // 나간 사람 때문에 1을 넘으면 1
        when(missions.findClosedStatsByGroupId(11L, TODAY.minusDays(30), GroupContextBuilder.MAX_STAT_ROWS)).thenReturn(rows);

        GenerationContext context = builder.build(group, TODAY);

        assertEquals(TODAY, context.date());
        assertEquals("COUPLE", context.relationType());
        assertTrue(context.recentMissions().isEmpty());  // 슬롯마다 발행기가 채운다
        assertEquals(List.of(new SpecialDay(SpecialDay.Kind.COUPLE_MILESTONE, "우리의 100일", 0),
                new SpecialDay(SpecialDay.Kind.CALENDAR, "그룹 소풍날", 7)), context.specialDays());
        GroupSignals signals = context.signals();
        assertEquals(2, signals.memberCount());
        assertEquals(31, signals.daysTogether());
        assertEquals(new GroupSignals.MissionStat("TEXT_BALANCE", "짜장 vs 짬뽕", 1.0, 1.5), signals.recentMissions().get(0));
        assertEquals(new GroupSignals.MissionStat("PHOTO_NOW", "책상 위 TMI", 0.5, 0.0), signals.recentMissions().get(1));
        assertEquals(new GroupSignals.MissionStat("PHOTO_COLOR", "노란색 원픽", 0.0, 0.0), signals.recentMissions().get(2));
        assertEquals(new GroupSignals.MissionStat("DRAW_SKETCH", "고양이 그리기", 1.0, 0.33), signals.recentMissions().get(4));
        Map<String, Double> media = new LinkedHashMap<>();
        media.put("PHOTO", 0.25);
        media.put("TEXT", 1.0);
        media.put("DRAW", 1.0);
        assertEquals(List.copyOf(media.entrySet()), List.copyOf(signals.mediaParticipation().entrySet()));
    }

    @Test
    void recentSignalsAreCappedAndNewGroupsHaveNone() {
        when(members.countByGroupId(11L)).thenReturn(4L);
        List<MissionStatRow> many = java.util.stream.IntStream.range(0, 15)
                .mapToObj(i -> stat("PHOTO_NOW", "미션 " + i, 1, 4, 0)).toList();
        when(missions.findClosedStatsByGroupId(11L, TODAY.minusDays(30), GroupContextBuilder.MAX_STAT_ROWS)).thenReturn(many);
        GroupSignals signals = builder.build(group("FRIEND", null, TODAY.minusDays(5)), TODAY).signals();
        assertEquals(GroupContextBuilder.SIGNAL_MISSIONS, signals.recentMissions().size());
        assertEquals(Map.of("PHOTO", 0.25), signals.mediaParticipation());

        when(missions.findClosedStatsByGroupId(11L, TODAY.minusDays(30), GroupContextBuilder.MAX_STAT_ROWS)).thenReturn(List.of());
        GenerationContext first = builder.build(group("FRIEND", null, TODAY), TODAY);
        assertEquals(GroupSignals.none(4, 1), first.signals());
        assertTrue(first.specialDays().stream().noneMatch(day -> day.kind() == SpecialDay.Kind.COUPLE_MILESTONE));
    }

    private static Group group(String relation, LocalDate coupleStart, LocalDate created) {
        Group group = new Group();
        group.setId(11L);
        group.setName("비밀 그룹 이름");
        group.setRelationType(relation);
        group.setStartDate(coupleStart);
        group.setCreatedAt(LocalDateTime.of(created, java.time.LocalTime.of(12, 0)));
        return group;
    }

    private static MissionStatRow stat(String template, String content, long submitted, long members, long engagement) {
        MissionStatRow row = new MissionStatRow();
        row.setTemplate(template);
        row.setContent(content);
        row.setSubmittedCount(submitted);
        row.setMemberCount(members);
        row.setEngagementCount(engagement);
        return row;
    }
}
