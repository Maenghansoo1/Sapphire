package com.sapphire.hawe.submission.service;

import com.sapphire.hawe.global.exception.BusinessException;
import com.sapphire.hawe.group.dao.GroupMemberMapper;
import com.sapphire.hawe.mission.dao.MissionMapper;
import com.sapphire.hawe.mission.dao.MissionRow;
import com.sapphire.hawe.submission.dao.SubmissionMapper;
import com.sapphire.hawe.submission.dao.SubmissionRow;
import com.sapphire.hawe.submission.dto.MissionResultResponse.ChoiceCount;
import com.sapphire.hawe.submission.dto.MissionResultResponse.NumberEntry;
import com.sapphire.hawe.submission.dto.MissionResultResponse.RankingEntry;
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
    MissionResultService service = new MissionResultService(missions, members, submissions, json);
    MissionRow mission = new MissionRow();
    List<SubmissionRow> rows = new ArrayList<>();

    @BeforeEach void setUp() {
        mission.setId(1L); mission.setGroupId(2L); mission.setTemplate("SLIDER_POSITION");
        when(missions.findById(1L, 3L)).thenReturn(mission);
        when(members.existsByGroupIdAndUserId(2L, 3L)).thenReturn(true);
        when(submissions.existsByMissionIdAndUserId(1L, 3L)).thenReturn(true);
        when(submissions.findResultRowsByMissionId(1L)).thenReturn(rows);
    }

    private String error() {
        return assertThrows(BusinessException.class, () -> service.result(3L, 1L)).errorCode().code();
    }

    private void add(long userId, Integer number, String choice, Integer guess) {
        SubmissionRow row = new SubmissionRow();
        row.setId(100 + userId); row.setMissionId(1L); row.setUserId(userId); row.setNickname("u" + userId);
        row.setNumberValue(number); row.setChoiceValue(choice);
        row.setAnswerData(guess == null ? null : "{\"guess\":" + guess + "}");
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
        for (String template : List.of("PHOTO_NOW", "TEXT_FILL_BLANK", "TEXT_ONE_WORD", "TEXT_REVIEW", "VOICE_LINE")) {
            mission.setTemplate(template);
            assertEquals("RESULT_NOT_SUPPORTED", error());
        }
        verify(submissions, never()).findResultRowsByMissionId(any());
    }

    @Test void balanceCountsBothSidesAndMyChoice() {
        mission.setTemplate("TEXT_BALANCE");
        add(3, null, "B", null);
        add(4, null, "A", null);
        add(5, null, "A", null);
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

    @Test void sliderAverageAndMyDifferenceRoundToOneDecimal() {
        add(4, 70, null, null);
        add(3, 70, null, null);
        add(5, 20, null, null);
        add(6, 89, null, null);
        var result = service.result(3L, 1L);
        // 평균 62.25 → 62.3, 내 차이 70 − 62.25 = 7.75 → 7.8 (반올림 전 값으로 계산)
        assertEquals(62.3, result.average());
        assertEquals(7.8, result.diffFromAverage());
        assertEquals(70, result.myNumber());
        assertEquals(4, result.totalCount());
        assertEquals(List.of(new NumberEntry(4L, "u4", 70), new NumberEntry(3L, "u3", 70),
                new NumberEntry(5L, "u5", 20), new NumberEntry(6L, "u6", 89)), result.distribution());
        assertNull(result.choices());
        assertNull(result.ranking());
    }

    @Test void singleSliderAnswerIsTheAverageAndBelowAverageIsNegative() {
        mission.setTemplate("SLIDER_TASTE");
        add(3, 40, null, null);
        var alone = service.result(3L, 1L);
        assertEquals(40.0, alone.average());
        assertEquals(0.0, alone.diffFromAverage());
        add(4, 100, null, null);
        add(5, 100, null, null);
        add(6, 51, null, null);
        // 평균 72.75, 내 차이 40 − 72.75 = −32.75 → −32.8 (0에서 먼 쪽)
        assertEquals(-32.8, service.result(3L, 1L).diffFromAverage());
        assertEquals(72.8, service.result(3L, 1L).average());
    }

    @Test void consensusRanksGuessesAndKeepsTiedWinners() {
        mission.setTemplate("SLIDER_CONSENSUS");
        add(3, 70, null, 60);
        add(4, 20, null, 80);
        add(5, 80, null, 65);
        add(6, 80, null, null);  // 예측이 없는 이상한 행: 평균에는 들어가고 순위에서는 빠진다
        var result = service.result(3L, 1L);
        assertEquals(62.5, result.average());
        assertEquals(4, result.totalCount());
        assertEquals(List.of(new RankingEntry(3L, "u3", 60, 2.5), new RankingEntry(5L, "u5", 65, 2.5),
                new RankingEntry(4L, "u4", 80, 17.5)), result.ranking());
        assertEquals(List.of(3L, 5L), result.winnerUserIds());
        assertNull(result.distribution());
        assertNull(result.myNumber());
    }

    @Test void consensusOrdersByExactDifferenceWithRepeatingAverage() {
        mission.setTemplate("SLIDER_CONSENSUS");
        // 평균 61/3 = 20.33…: 차이를 3배 한 정수(1, 2, 4)로 비교하고, 응답에서만 0.3·0.7·1.3 으로 반올림
        add(3, 1, null, 19);
        add(4, 30, null, 21);
        add(5, 30, null, 20);
        var result = service.result(3L, 1L);
        assertEquals(List.of(5L), result.winnerUserIds());
        assertEquals(List.of(5L, 4L, 3L), result.ranking().stream().map(RankingEntry::userId).toList());
        assertEquals(List.of(0.3, 0.7, 1.3), result.ranking().stream().map(RankingEntry::diff).toList());
    }

    @Test void tenthsRoundsHalfUpAwayFromZero() {
        assertEquals(62.3, MissionResultService.tenths(249, 4));
        assertEquals(-7.3, MissionResultService.tenths(-29, 4));
        assertEquals(0.0, MissionResultService.tenths(0, 3));
        assertEquals(33.3, MissionResultService.tenths(100, 3));
    }
}
