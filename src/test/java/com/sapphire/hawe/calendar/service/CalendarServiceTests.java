package com.sapphire.hawe.calendar.service;

import com.sapphire.hawe.calendar.dao.CalendarMapper;
import com.sapphire.hawe.calendar.dao.CalendarMissionRow;
import com.sapphire.hawe.calendar.dao.TemplateCountRow;
import com.sapphire.hawe.calendar.domain.Stamp;
import com.sapphire.hawe.calendar.dto.CalendarMission;
import com.sapphire.hawe.calendar.dto.CalendarResponse;
import com.sapphire.hawe.calendar.dto.MyStatsResponse;
import com.sapphire.hawe.global.exception.BusinessException;
import com.sapphire.hawe.global.time.SeoulTime;
import com.sapphire.hawe.group.dao.GroupMapper;
import com.sapphire.hawe.group.dao.GroupMemberMapper;
import com.sapphire.hawe.group.domain.Group;
import com.sapphire.hawe.group.service.GroupErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CalendarServiceTests {

    private static final YearMonth OCTOBER = YearMonth.of(2026, 10);

    @Mock CalendarMapper calendarMapper;
    @Mock GroupMapper groupMapper;
    @Mock GroupMemberMapper groupMemberMapper;

    private CalendarService service;

    @BeforeEach
    void setUp() {
        service = new CalendarService(calendarMapper, groupMapper, groupMemberMapper);
    }

    @Test
    void calendarGroupsMissionsByDateWithMyStamp() {
        givenMember(11L, 7L);
        CalendarMissionRow submitted = row(101L, "2026-10-01", "PHOTO_NOW", "오늘의 하늘");
        submitted.setSubmittedByMe(true);
        submitted.setClosed(true);
        CalendarMissionRow missed = row(102L, "2026-10-02", "DRAW_SKETCH", "기분을 날씨로 그려줘");
        missed.setClosed(true);
        missed.setMemberWhenPublished(true);
        when(calendarMapper.findMissionsInMonth(11L, 7L, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)))
                .thenReturn(List.of(submitted, missed));

        CalendarResponse response = service.getCalendar(7L, 11L, OCTOBER);

        assertEquals(OCTOBER, response.month());
        assertEquals(2, response.days().size());
        assertEquals(LocalDate.of(2026, 10, 1), response.days().get(0).date());
        CalendarMission first = response.days().get(0).missions().getFirst();
        assertEquals(101L, first.missionId());
        assertEquals("PHOTO", first.media());
        assertEquals(Stamp.GOOD, first.stamp());
        CalendarMission second = response.days().get(1).missions().getFirst();
        assertEquals("DRAW", second.media());
        assertEquals(Stamp.PENALTY, second.stamp());
    }

    @Test
    void missionsOnSameDayShareOneDayWithAStampEach() {
        givenMember(11L, 7L);
        CalendarMissionRow submitted = row(101L, "2026-10-01", "PHOTO_NOW", "첫 번째");
        submitted.setSubmittedByMe(true);
        submitted.setClosed(true);
        CalendarMissionRow missed = row(102L, "2026-10-01", "TEXT_ACROSTIC", "두 번째");
        missed.setClosed(true);
        missed.setMemberWhenPublished(true);
        CalendarMissionRow open = row(103L, "2026-10-01", "DRAW_SKETCH", "세 번째");
        open.setMemberWhenPublished(true);
        when(calendarMapper.findMissionsInMonth(any(), any(), any(), any())).thenReturn(List.of(submitted, missed, open));

        CalendarResponse response = service.getCalendar(7L, 11L, OCTOBER);

        // 하루 여러 개면 도장도 미션마다 따로 (하루에 하나로 합치지 않는다)
        assertEquals(1, response.days().size());
        assertEquals(java.util.Arrays.asList(Stamp.GOOD, Stamp.PENALTY, null),
                response.days().getFirst().missions().stream().map(CalendarMission::stamp).toList());
    }

    @Test
    void targetPlaceholderShowsTargetNickname() {
        givenMember(11L, 7L);
        CalendarMissionRow withTarget = row(101L, "2026-10-01", "DRAW_A_TO_B", "[대상]의 얼굴을 고양이로 그려줘");
        withTarget.setTargetNickname("우준");
        CalendarMissionRow targetLeft = row(102L, "2026-10-02", "TEXT_FILL_BLANK", "[대상]에게 한마디");
        when(calendarMapper.findMissionsInMonth(any(), any(), any(), any())).thenReturn(List.of(withTarget, targetLeft));

        CalendarResponse response = service.getCalendar(7L, 11L, OCTOBER);

        assertEquals("우준의 얼굴을 고양이로 그려줘", response.days().get(0).missions().getFirst().content());
        assertEquals("친구에게 한마디", response.days().get(1).missions().getFirst().content());
    }

    @Test
    void missingGroupReturnsNotFound() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.getCalendar(7L, 11L, OCTOBER));

        assertEquals(GroupErrorCode.GROUP_NOT_FOUND, exception.errorCode());
        verify(calendarMapper, never()).findMissionsInMonth(any(), any(), any(), any());
    }

    @Test
    void nonMemberCannotSeeCalendar() {
        when(groupMapper.findById(11L)).thenReturn(new Group());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.getCalendar(7L, 11L, OCTOBER));

        assertEquals(GroupErrorCode.NOT_GROUP_MEMBER, exception.errorCode());
        verify(calendarMapper, never()).findMissionsInMonth(any(), any(), any(), any());
    }

    @Test
    void statsCountSubmissionsByMedia() {
        when(calendarMapper.countSubmissionsByTemplate(7L)).thenReturn(List.of(
                templateCount("PHOTO_NOW", 3), templateCount("PHOTO_COLOR", 2), templateCount("DRAW_SKETCH", 1)));
        when(calendarMapper.findSubmittedDates(7L)).thenReturn(List.of());
        when(calendarMapper.countGroups(7L)).thenReturn(2L);

        MyStatsResponse stats = service.getMyStats(7L);

        assertEquals(6, stats.completedMissions());
        assertEquals(2, stats.groupCount());
        assertEquals(Map.of("DRAW", 1L, "PHOTO", 5L, "TEXT", 0L), stats.byMedia());
    }

    @Test
    void statsSkipRemovedVoiceTemplatesInByMedia() {
        when(calendarMapper.countSubmissionsByTemplate(7L)).thenReturn(List.of(
                templateCount("PHOTO_NOW", 1), templateCount("VOICE_LINE", 2)));
        when(calendarMapper.findSubmittedDates(7L)).thenReturn(List.of());

        MyStatsResponse stats = service.getMyStats(7L);

        assertEquals(3, stats.completedMissions());
        assertEquals(Map.of("DRAW", 0L, "PHOTO", 1L, "TEXT", 0L), stats.byMedia());
    }

    @Test
    void statsUseTodayForStreak() {
        LocalDate today = SeoulTime.today();
        when(calendarMapper.countSubmissionsByTemplate(7L)).thenReturn(List.of());
        when(calendarMapper.findSubmittedDates(7L)).thenReturn(List.of(today, today.minusDays(1)));

        assertEquals(2, service.getMyStats(7L).streakDays());
    }

    @Test
    void streakCountsBackFromToday() {
        LocalDate today = LocalDate.of(2026, 10, 12);

        int streak = CalendarService.streakDays(List.of(
                today, today.minusDays(1), today.minusDays(2), today.minusDays(4)), today);

        assertEquals(3, streak);
    }

    @Test
    void streakStartsFromYesterdayWhenNothingSubmittedToday() {
        LocalDate today = LocalDate.of(2026, 10, 12);

        assertEquals(2, CalendarService.streakDays(List.of(today.minusDays(1), today.minusDays(2)), today));
    }

    @Test
    void streakIsZeroWhenYesterdayWasMissed() {
        LocalDate today = LocalDate.of(2026, 10, 12);

        assertEquals(0, CalendarService.streakDays(List.of(today.minusDays(2), today.minusDays(3)), today));
    }

    @Test
    void streakIsZeroWithoutSubmissions() {
        assertEquals(0, CalendarService.streakDays(List.of(), LocalDate.of(2026, 10, 12)));
    }

    @Test
    void unknownTemplateHasNoMedia() {
        givenMember(11L, 7L);
        when(calendarMapper.findMissionsInMonth(any(), any(), any(), any()))
                .thenReturn(List.of(row(101L, "2026-10-01", "SOMETHING_NEW", "새 틀"),
                        row(102L, "2026-10-01", "VOICE_LINE", "지워진 음성 틀")));

        List<CalendarMission> missions = service.getCalendar(7L, 11L, OCTOBER).days().getFirst().missions();
        assertNull(missions.get(0).media());
        assertNull(missions.get(1).media());
    }

    private void givenMember(Long groupId, Long userId) {
        when(groupMapper.findById(groupId)).thenReturn(new Group());
        when(groupMemberMapper.existsByGroupIdAndUserId(groupId, userId)).thenReturn(true);
    }

    private CalendarMissionRow row(Long missionId, String date, String template, String content) {
        CalendarMissionRow row = new CalendarMissionRow();
        row.setMissionId(missionId);
        row.setMissionDate(LocalDate.parse(date));
        row.setTemplate(template);
        row.setContent(content);
        row.setSubmittedCount(3);
        row.setMemberCount(5);
        return row;
    }

    private TemplateCountRow templateCount(String template, long count) {
        TemplateCountRow row = new TemplateCountRow();
        row.setTemplate(template);
        row.setCount(count);
        return row;
    }
}
