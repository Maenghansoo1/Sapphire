package com.sapphire.hawe.submission.service;

import com.sapphire.hawe.global.exception.BusinessException;
import com.sapphire.hawe.group.dao.GroupMemberMapper;
import com.sapphire.hawe.mission.dao.MissionMapper;
import com.sapphire.hawe.mission.dao.MissionRow;
import com.sapphire.hawe.submission.dao.SubmissionMapper;
import com.sapphire.hawe.submission.dao.SubmissionRow;
import com.sapphire.hawe.submission.dto.MissionResultResponse.ChoiceCount;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MissionResultServiceTests {
    MissionMapper missions = mock(MissionMapper.class);
    GroupMemberMapper members = mock(GroupMemberMapper.class);
    SubmissionMapper submissions = mock(SubmissionMapper.class);
    ObjectMapper json = new ObjectMapper();
    MissionResultService service = new MissionResultService(missions, members, submissions);
    MissionRow mission = new MissionRow();
    List<SubmissionRow> rows = new ArrayList<>();

    @BeforeEach void setUp() {
        mission.setId(1L); mission.setGroupId(2L); mission.setTemplate("TEXT_BALANCE");
        when(missions.findById(1L, 3L)).thenReturn(mission);
        when(members.existsByGroupIdAndUserId(2L, 3L)).thenReturn(true);
        when(submissions.existsByMissionIdAndUserId(1L, 3L)).thenReturn(true);
        when(submissions.findResultRowsByMissionId(1L)).thenReturn(rows);
    }

    private String error() {
        return assertThrows(BusinessException.class, () -> service.result(3L, 1L)).errorCode().code();
    }

    private void add(long userId, String choice) {
        SubmissionRow row = new SubmissionRow();
        row.setId(100 + userId); row.setMissionId(1L); row.setUserId(userId); row.setNickname("u" + userId);
        row.setChoiceValue(choice);
        row.setSubmittedAt(LocalDateTime.of(2026, 10, 13, 9, 0).plusMinutes(rows.size()));
        rows.add(row);
    }

    @Test void accessRulesInOrder() {
        when(missions.findById(1L, 3L)).thenReturn(null);
        assertEquals("MISSION_NOT_FOUND", error());
        when(missions.findById(1L, 3L)).thenReturn(mission);
        when(members.existsByGroupIdAndUserId(2L, 3L)).thenReturn(false);
        assertEquals("NOT_GROUP_MEMBER", error());
        when(members.existsByGroupIdAndUserId(2L, 3L)).thenReturn(true);
        when(submissions.existsByMissionIdAndUserId(1L, 3L)).thenReturn(false);
        assertEquals("FEED_LOCKED", error());
        when(submissions.existsByMissionIdAndUserId(1L, 3L)).thenReturn(true);
        for (String template : List.of("PHOTO_NOW", "TEXT_FILL_BLANK", "TEXT_ONE_WORD", "TEXT_REVIEW", "VOICE_LINE",
                "SLIDER_POSITION", "SLIDER_TASTE", "SLIDER_CONSENSUS")) {
            mission.setTemplate(template);
            assertEquals("RESULT_NOT_SUPPORTED", error());
        }
        verify(submissions, never()).findResultRowsByMissionId(any());
    }

    @Test void balanceCountsBothSidesAndMyChoice() {
        add(3, "B");
        add(4, "A");
        add(5, "A");
        var result = service.result(3L, 1L);
        assertEquals("TEXT_BALANCE", result.template());
        assertEquals(3, result.totalCount());
        assertEquals(List.of(new ChoiceCount("A", 2), new ChoiceCount("B", 1)), result.choices());
        assertEquals("B", result.myChoice());
        String body = json.writeValueAsString(result);
        assertFalse(body.contains("average") || body.contains("ranking") || body.contains("distribution"));

        rows.subList(1, 3).clear();
        assertEquals(List.of(new ChoiceCount("A", 0), new ChoiceCount("B", 1)), service.result(3L, 1L).choices());
    }
}
