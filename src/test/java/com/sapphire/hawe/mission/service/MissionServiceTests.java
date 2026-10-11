package com.sapphire.hawe.mission.service;

import com.sapphire.hawe.auth.dao.UserMapper;
import com.sapphire.hawe.auth.domain.User;
import com.sapphire.hawe.global.config.MissionProperties;
import com.sapphire.hawe.global.exception.BusinessException;
import com.sapphire.hawe.group.dao.GroupMapper;
import com.sapphire.hawe.group.dao.GroupMemberMapper;
import com.sapphire.hawe.group.dao.GroupMissionSlotMapper;
import com.sapphire.hawe.group.domain.Group;
import com.sapphire.hawe.group.domain.GroupMissionSlot;
import com.sapphire.hawe.group.service.GroupErrorCode;
import com.sapphire.hawe.mission.dao.MissionMapper;
import com.sapphire.hawe.mission.dao.MissionRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
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

    private MissionService service;

    @BeforeEach
    void setUp() {
        service = new MissionService(missions, groups, members, slots, users,
                new MissionProperties(LocalTime.of(9, 0), LocalTime.of(23, 0), 5), new ObjectMapper());
    }

    @Test
    void emptyTodayUsesEarliestEffectiveSlotTime() {
        allowGroup(3);
        when(missions.findTodayByGroupId(11L, 7L)).thenReturn(List.of());
        when(slots.findByGroupId(11L)).thenReturn(List.of(slot(2, LocalTime.of(14, 0)), slot(3, null)));

        var response = service.getTodayMissions(7L, 11L);

        assertEquals(3, response.dailyMissionCount());
        assertEquals(LocalDate.now(), response.date());
        assertTrue(response.missions().isEmpty());
        assertEquals(LocalDate.now().atTime(9, 0), response.nextPublishAt());
    }

    @Test
    void todayUsesCustomTimeForUnpublishedSlotAndIgnoresPublishedTime() {
        allowGroup(2);
        when(missions.findTodayByGroupId(11L, 7L)).thenReturn(List.of(row(1L, 11L, 1, "{}")));
        when(slots.findByGroupId(11L)).thenReturn(List.of(slot(2, LocalTime.of(14, 0))));

        var response = service.getTodayMissions(7L, 11L);

        assertEquals(LocalDate.now().atTime(14, 0), response.nextPublishAt());
        assertEquals(1, response.missions().size());
        assertEquals("PHOTO", response.missions().getFirst().media());
    }

    @Test
    void allActiveSlotsPublishedReturnsNoNextTimeEvenWithOldExtraSlot() {
        allowGroup(2);
        when(missions.findTodayByGroupId(11L, 7L)).thenReturn(List.of(
                row(1L, 11L, 1, "{}"), row(2L, 11L, 2, "{}"), row(3L, 11L, 3, "{}")));

        var response = service.getTodayMissions(7L, 11L);

        assertNull(response.nextPublishAt());
        assertEquals(3, response.missions().size());
    }

    @Test
    void loweredCountIgnoresPublishedExtraSlotWhenCalculatingNextTime() {
        allowGroup(2);
        when(missions.findTodayByGroupId(11L, 7L)).thenReturn(List.of(row(3L, 11L, 3, "{}")));

        var response = service.getTodayMissions(7L, 11L);

        assertEquals(LocalDate.now().atTime(9, 0), response.nextPublishAt());
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
        when(missions.findById(10L, 7L)).thenReturn(row);
        when(members.existsByGroupIdAndUserId(11L, 7L)).thenReturn(true);
        User target = new User();
        target.setNickname("잎사귀");
        when(users.findById(3L)).thenReturn(target);

        var response = service.getMission(7L, 10L);

        assertEquals("hello", response.params().get("sentence"));
        assertEquals(Map.of("userId", 3L, "nickname", "잎사귀"), response.params().get("target"));
        assertFalse(response.params().containsKey("targetUserId"));
        assertEquals(10L, response.missionId());
    }

    @Test
    void missingTargetAndMalformedParamsDoNotFailRead() {
        when(missions.findById(10L, 7L)).thenReturn(row(10L, 11L, 1, "{\"targetUserId\":3}"));
        when(missions.findById(12L, 7L)).thenReturn(row(12L, 11L, 1, "not json"));
        when(members.existsByGroupIdAndUserId(11L, 7L)).thenReturn(true);

        assertEquals(Map.of(), service.getMission(7L, 10L).params());
        assertEquals(Map.of(), service.getMission(7L, 12L).params());
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
        return row;
    }
}
