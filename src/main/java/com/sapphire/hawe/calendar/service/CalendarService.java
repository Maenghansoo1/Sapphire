package com.sapphire.hawe.calendar.service;

import com.sapphire.hawe.calendar.dao.CalendarMapper;
import com.sapphire.hawe.calendar.dao.CalendarMissionRow;
import com.sapphire.hawe.calendar.dao.TemplateCountRow;
import com.sapphire.hawe.calendar.domain.Stamp;
import com.sapphire.hawe.calendar.dto.CalendarDay;
import com.sapphire.hawe.calendar.dto.CalendarMission;
import com.sapphire.hawe.calendar.dto.CalendarResponse;
import com.sapphire.hawe.calendar.dto.MyStatsResponse;
import com.sapphire.hawe.global.exception.BusinessException;
import com.sapphire.hawe.global.time.SeoulTime;
import com.sapphire.hawe.group.dao.GroupMapper;
import com.sapphire.hawe.group.dao.GroupMemberMapper;
import com.sapphire.hawe.group.service.GroupErrorCode;
import com.sapphire.hawe.mission.domain.MissionTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class CalendarService {

    private static final List<String> MEDIA = List.of("DRAW", "PHOTO", "TEXT");

    private final CalendarMapper calendarMapper;
    private final GroupMapper groupMapper;
    private final GroupMemberMapper groupMemberMapper;

    public CalendarService(CalendarMapper calendarMapper, GroupMapper groupMapper,
            GroupMemberMapper groupMemberMapper) {
        this.calendarMapper = calendarMapper;
        this.groupMapper = groupMapper;
        this.groupMemberMapper = groupMemberMapper;
    }

    public CalendarResponse getCalendar(Long userId, Long groupId, YearMonth month) {
        if (groupMapper.findById(groupId) == null) {
            throw new BusinessException(GroupErrorCode.GROUP_NOT_FOUND);
        }
        if (!groupMemberMapper.existsByGroupIdAndUserId(groupId, userId)) {
            throw new BusinessException(GroupErrorCode.NOT_GROUP_MEMBER);
        }

        List<CalendarMissionRow> rows = calendarMapper.findMissionsInMonth(groupId, userId,
                month.atDay(1), month.atEndOfMonth());

        // 미션이 있었던 날만 넣는다. 하루 여러 미션을 대비해 날짜별로 묶는다.
        Map<LocalDate, List<CalendarMission>> missionsByDate = new LinkedHashMap<>();
        for (CalendarMissionRow row : rows) {
            missionsByDate.computeIfAbsent(row.getMissionDate(), date -> new ArrayList<>()).add(toMission(row));
        }
        List<CalendarDay> days = missionsByDate.entrySet().stream()
                .map(entry -> new CalendarDay(entry.getKey(), entry.getValue()))
                .toList();
        return new CalendarResponse(month, days);
    }

    public MyStatsResponse getMyStats(Long userId) {
        Map<String, Long> byMedia = new LinkedHashMap<>();
        MEDIA.forEach(media -> byMedia.put(media, 0L));

        long completedMissions = 0;
        for (TemplateCountRow row : calendarMapper.countSubmissionsByTemplate(userId)) {
            completedMissions += row.getCount();
            String media = mediaOf(row.getTemplate());
            if (media != null) {
                byMedia.merge(media, row.getCount(), Long::sum);
            }
        }

        int streakDays = streakDays(calendarMapper.findSubmittedDates(userId), SeoulTime.today());
        return new MyStatsResponse(completedMissions, streakDays, calendarMapper.countGroups(userId), byMedia);
    }

    /** 오늘(오늘 아직 안 냈으면 어제)부터 거꾸로 하루도 빠짐없이 낸 날 수 */
    static int streakDays(Collection<LocalDate> submittedDates, LocalDate today) {
        Set<LocalDate> dates = new HashSet<>(submittedDates);
        LocalDate day = dates.contains(today) ? today : today.minusDays(1);
        int streak = 0;
        while (dates.contains(day)) {
            streak++;
            day = day.minusDays(1);
        }
        return streak;
    }

    private CalendarMission toMission(CalendarMissionRow row) {
        Stamp stamp = Stamp.of(row.isSubmittedByMe(), row.isLateByMe(), row.isClosed(),
                row.isMemberWhenPublished());
        return new CalendarMission(row.getMissionId(), mediaOf(row.getTemplate()), displayContent(row),
                row.getSubmittedCount(), row.getMemberCount(), stamp);
    }

    // 미션 화면과 같게 [대상] 자리를 대상 닉네임으로 바꾼다.
    private String displayContent(CalendarMissionRow row) {
        String content = row.getContent();
        if (content == null || !content.contains("[대상]")) {
            return content;
        }
        String nickname = row.getTargetNickname() != null ? row.getTargetNickname() : "친구";
        return content.replace("[대상]", nickname);
    }

    private String mediaOf(String template) {
        try {
            return MissionTemplate.valueOf(template).media();
        } catch (IllegalArgumentException | NullPointerException e) {
            return null;
        }
    }
}
