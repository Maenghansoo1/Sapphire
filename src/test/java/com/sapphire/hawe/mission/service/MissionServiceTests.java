package com.sapphire.hawe.mission.service;

import com.sapphire.hawe.auth.dao.UserMapper;
import com.sapphire.hawe.auth.domain.User;
import com.sapphire.hawe.global.config.MissionProperties;
import com.sapphire.hawe.global.exception.BusinessException;
import com.sapphire.hawe.global.time.SeoulTime;
import com.sapphire.hawe.group.dao.GroupMapper;
import com.sapphire.hawe.group.dao.GroupMemberMapper;
import com.sapphire.hawe.group.dao.GroupMissionSlotMapper;
import com.sapphire.hawe.group.domain.Group;
import com.sapphire.hawe.group.domain.GroupMissionSlot;
import com.sapphire.hawe.group.service.GroupErrorCode;
import com.sapphire.hawe.mission.dao.MissionMapper;
import com.sapphire.hawe.mission.dao.MissionRow;
import com.sapphire.hawe.mission.dao.MissionMemberRow;
import com.sapphire.hawe.mission.dao.MissionSummaryRow;
import com.sapphire.hawe.poke.dao.PokeMapper;
import com.sapphire.hawe.poke.dao.PokedRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MissionServiceTests {

    @Mock MissionMapper missions;
    @Mock GroupMapper groups;
    @Mock GroupMemberMapper members;
    @Mock GroupMissionSlotMapper slots;
    @Mock UserMapper users;
    @Mock PokeMapper pokes;

    private MissionService service;

    @BeforeEach
    void setUp() {
        service = new MissionService(missions, groups, members, slots, users,
                new MissionProperties(LocalTime.of(9, 0), LocalTime.of(23, 0), 5), new ObjectMapper(), pokes);
    }

    @Test
    void emptyCurrentUsesNextScheduledSlotTime() {
        allowGroup(3);
        when(missions.findCurrentByGroupId(11L, 7L)).thenReturn(List.of());
        when(slots.findByGroupId(11L)).thenReturn(List.of(slot(2, LocalTime.of(14, 0)), slot(3, null)));

        var response = service.getTodayMissions(7L, 11L);

        assertEquals(3, response.dailyMissionCount());
        assertEquals(SeoulTime.today(), response.date());
        assertTrue(response.missions().isEmpty());
        LocalDateTime now = SeoulTime.now();
        LocalDateTime nextDefault = com.sapphire.hawe.mission.domain.MissionTiming.nextPublishAfter(
                now, LocalTime.of(9, 0));
        LocalDateTime nextCustom = com.sapphire.hawe.mission.domain.MissionTiming.nextPublishAfter(
                now, LocalTime.of(14, 0));
        assertEquals(SeoulTime.toOffset(nextDefault.isBefore(nextCustom) ? nextDefault : nextCustom),
                response.nextPublishAt());
    }

    @Test
    void currentMissionUsesCustomTimeForUnpublishedSlot() {
        allowGroup(2);
        when(missions.findCurrentByGroupId(11L, 7L)).thenReturn(List.of(row(1L, 11L, 1, "{}")));
        when(slots.findByGroupId(11L)).thenReturn(List.of(slot(2, LocalTime.of(14, 0))));

        var response = service.getTodayMissions(7L, 11L);

        assertTrue(response.nextPublishAt() != null);
        assertEquals(1, response.missions().size());
        assertEquals("PHOTO", response.missions().getFirst().media());
    }

    @Test
    void allActiveSlotsPublishedReturnsNextPublishTimeEvenWithOldExtraSlot() {
        allowGroup(2);
        when(missions.findCurrentByGroupId(11L, 7L)).thenReturn(List.of(
                row(1L, 11L, 1, "{}"), row(2L, 11L, 2, "{}"), row(3L, 11L, 3, "{}")));

        var response = service.getTodayMissions(7L, 11L);

        assertTrue(response.nextPublishAt() != null);
        assertEquals(3, response.missions().size());
    }

    @Test
    void loweredCountIgnoresPublishedExtraSlotWhenCalculatingNextTime() {
        allowGroup(2);
        when(missions.findCurrentByGroupId(11L, 7L)).thenReturn(List.of(row(3L, 11L, 3, "{}")));

        var response = service.getTodayMissions(7L, 11L);

        assertTrue(response.nextPublishAt() != null);
        assertEquals(3, response.missions().getFirst().slotNo());
    }

    @Test
    void missingGroupAndNonMemberReturnFeatureErrors() {
        assertEquals(GroupErrorCode.GROUP_NOT_FOUND,
                assertThrows(BusinessException.class, () -> service.getTodayMissions(7L, 11L)).errorCode());
        verify(members, never()).existsByGroupIdAndUserId(11L, 7L);

        when(groups.findById(11L)).thenReturn(group(1));
        assertEquals(GroupErrorCode.NOT_GROUP_MEMBER,
                assertThrows(BusinessException.class, () -> service.getTodayMissions(7L, 11L)).errorCode());
    }

    @Test
    void singleMissionResolvesTargetAndPreservesOtherParams() {
        MissionRow row = row(10L, 11L, 1, "{\"sentence\":\"hello\",\"targetUserId\":3}");
        row.setContent("[대상]에게 [대상]의 그림을 보여줘");
        row.setPublishedAt(LocalDateTime.of(2026, 9, 28, 9, 0));
        row.setDeadlineAt(LocalDateTime.of(2026, 9, 28, 23, 0));
        when(missions.findById(10L, 7L)).thenReturn(row);
        when(members.existsByGroupIdAndUserId(11L, 7L)).thenReturn(true);
        User target = new User();
        target.setNickname("잎사귀");
        when(users.findById(3L)).thenReturn(target);
        MissionMemberRow member = new MissionMemberRow();
        member.setUserId(7L);
        member.setSubmitted(true);
        when(missions.findMemberStatuses(10L)).thenReturn(List.of(member));

        var response = service.getMission(7L, 10L);

        assertEquals("hello", response.params().get("sentence"));
        assertEquals(Map.of("userId", 3L, "nickname", "잎사귀"), response.params().get("target"));
        assertFalse(response.params().containsKey("targetUserId"));
        assertEquals(10L, response.missionId());
        assertEquals(SeoulTime.toOffset(row.getPublishedAt()), response.publishedAt());
        assertEquals(SeoulTime.toOffset(row.getDeadlineAt()), response.deadlineAt());
        assertEquals("잎사귀에게 잎사귀의 그림을 보여줘", response.content());
        assertEquals(1, response.number());
        assertEquals(SeoulTime.toOffset(row.getPublishedAt().plusDays(1)), response.closesAt());
        assertTrue(response.members().getFirst().submitted());
        assertEquals(List.of(), response.members().getFirst().pokedByMe());
    }

    @Test
    void memberStatusesShowOnlyMySentTypesInFixedOrder() {
        when(missions.findById(10L, 7L)).thenReturn(row(10L, 11L, 1, "{}"));
        when(members.existsByGroupIdAndUserId(11L, 7L)).thenReturn(true);
        MissionMemberRow me = new MissionMemberRow();
        me.setUserId(7L);
        MissionMemberRow target = new MissionMemberRow();
        target.setUserId(8L);
        when(missions.findMemberStatuses(10L)).thenReturn(List.of(me, target));
        when(pokes.findSentTypes(10L, 7L)).thenReturn(List.of(poked(8L, "fling"),
                poked(8L, "poke"), poked(7L, "poke")));

        var response = service.getMission(7L, 10L);

        assertEquals(List.of(), response.members().get(0).pokedByMe());
        assertEquals(List.of("poke", "fling"), response.members().get(1).pokedByMe());
        verify(pokes).findSentTypes(10L, 7L);
    }

    private static PokedRow poked(Long toUserId, String type) {
        PokedRow row = new PokedRow();
        row.setToUserId(toUserId);
        row.setType(type);
        return row;
    }

    @Test
    void missingTargetAndMalformedParamsDoNotFailRead() {
        MissionRow missingTarget = row(10L, 11L, 1, "{\"targetUserId\":3}");
        missingTarget.setContent("[대상]에게 인사해");
        when(missions.findById(10L, 7L)).thenReturn(missingTarget);
        when(missions.findById(12L, 7L)).thenReturn(row(12L, 11L, 1, "not json"));
        when(members.existsByGroupIdAndUserId(11L, 7L)).thenReturn(true);

        assertEquals(Map.of(), service.getMission(7L, 10L).params());
        assertEquals("친구에게 인사해", service.getMission(7L, 10L).content());
        assertEquals(Map.of(), service.getMission(7L, 12L).params());
    }

    @Test
    void currentSummaryContainsResolvedContentAndSubmissionStatus() {
        MissionSummaryRow row = new MissionSummaryRow();
        row.setGroupId(11L);
        row.setMissionId(10L);
        row.setTemplate("TEXT_FILL_BLANK");
        row.setContent("[대상]은 ___ 할 것 같다");
        row.setParams("{\"targetUserId\":3}");
        row.setSubmittedCount(2);
        row.setSubmittedByMe(true);
        when(missions.findCurrentSummariesByGroupIds(List.of(11L), 7L)).thenReturn(List.of(row));
        User target = new User();
        target.setNickname("잎사귀");
        when(users.findById(3L)).thenReturn(target);

        var summary = service.summarizeCurrent(List.of(11L), 7L).get(11L);

        assertEquals("TEXT", summary.media());
        assertEquals("잎사귀은 ___ 할 것 같다", summary.content());
        assertEquals(2, summary.submittedCount());
        assertTrue(summary.submittedByMe());
    }

    @Test
    void unknownTemplateStillReturnsStoredMission() {
        MissionRow row = row(10L, 11L, 1, "{\"x\":1}");
        row.setTemplate("FUTURE_TEMPLATE");
        when(missions.findById(10L, 7L)).thenReturn(row);
        when(members.existsByGroupIdAndUserId(11L, 7L)).thenReturn(true);

        var response = service.getMission(7L, 10L);

        assertNull(response.media());
        assertEquals(1, response.params().get("x"));
    }

    @Test
    void removedVoiceTemplateStillReadsWithoutMedia() {
        allowGroup(1);
        MissionRow row = row(10L, 11L, 1, "{\"sentence\":\"hello\"}");
        row.setTemplate("VOICE_LINE");
        when(missions.findCurrentByGroupId(11L, 7L)).thenReturn(List.of(row));
        when(missions.findById(10L, 7L)).thenReturn(row);

        assertNull(service.getTodayMissions(7L, 11L).missions().getFirst().media());
        assertNull(service.getMission(7L, 10L).media());
    }

    @Test
    void singleMissionChecksExistenceBeforeMembership() {
        assertEquals(MissionErrorCode.MISSION_NOT_FOUND,
                assertThrows(BusinessException.class, () -> service.getMission(7L, 10L)).errorCode());
        verify(members, never()).existsByGroupIdAndUserId(11L, 7L);

        when(missions.findById(10L, 7L)).thenReturn(row(10L, 11L, 1, "{}"));
        assertEquals(GroupErrorCode.NOT_GROUP_MEMBER,
                assertThrows(BusinessException.class, () -> service.getMission(7L, 10L)).errorCode());
    }

    private void allowGroup(int count) {
        when(groups.findById(11L)).thenReturn(group(count));
        when(members.existsByGroupIdAndUserId(11L, 7L)).thenReturn(true);
    }

    private static Group group(int count) {
        Group group = new Group();
        group.setId(11L);
        group.setDailyMissionCount(count);
        return group;
    }

    private static GroupMissionSlot slot(int number, LocalTime publishTime) {
        GroupMissionSlot slot = new GroupMissionSlot();
        slot.setSlotNo(number);
        slot.setPublishTime(publishTime);
        return slot;
    }

    private static MissionRow row(Long id, Long groupId, int slotNo, String params) {
        MissionRow row = new MissionRow();
        row.setId(id);
        row.setGroupId(groupId);
        row.setSlotNo(slotNo);
        row.setContent("Test mission");
        row.setCategory("TEST");
        row.setTemplate("PHOTO_NOW");
        row.setParams(params);
        row.setMissionDate(SeoulTime.today());
        row.setNumber(slotNo);
        row.setPublishedAt(SeoulTime.today().atTime(9, 0));
        row.setDeadlineAt(SeoulTime.today().atTime(23, 0));
        return row;
    }
}
