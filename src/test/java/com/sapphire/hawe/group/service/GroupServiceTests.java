package com.sapphire.hawe.group.service;

import com.sapphire.hawe.global.config.MissionProperties;
import com.sapphire.hawe.global.exception.BusinessException;
import com.sapphire.hawe.global.exception.CommonErrorCode;
import com.sapphire.hawe.global.storage.FileStorage;
import com.sapphire.hawe.group.dao.GroupMapper;
import com.sapphire.hawe.group.dao.GroupMemberMapper;
import com.sapphire.hawe.group.dao.GroupMissionSlotMapper;
import com.sapphire.hawe.group.domain.Group;
import com.sapphire.hawe.group.domain.GroupMember;
import com.sapphire.hawe.group.domain.GroupMissionSlot;
import com.sapphire.hawe.group.dto.GroupCreateRequest;
import com.sapphire.hawe.group.dto.GroupSettingsUpdateRequest;
import com.sapphire.hawe.group.dto.JoinRequest;
import com.sapphire.hawe.group.dto.MissionSlotRequest;
import com.sapphire.hawe.group.domain.GroupCreatedEvent;
import com.sapphire.hawe.mission.service.MissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GroupServiceTests {

    @Mock GroupMapper groupMapper;
    @Mock GroupMemberMapper groupMemberMapper;
    @Mock GroupMissionSlotMapper slotMapper;
    @Mock MissionService missionService;
    @Mock ApplicationEventPublisher events;
    @Mock FileStorage storage;

    private GroupService service;

    @BeforeEach
    void setUp() {
        service = new GroupService(groupMapper, groupMemberMapper, slotMapper,
                new MissionProperties(LocalTime.of(9, 0), LocalTime.of(23, 0), 5), "https://hawe.app/invite",
                missionService, events, storage);
    }

    @Test
    void createGroupUsesDefaultsAndAddsOwner() {
        assignGroupId();
        var response = service.createGroup(7L, new GroupCreateRequest("Home", null, null, null, null));

        assertEquals(11L, response.id());
        assertEquals("CASUAL", response.relationType());
        assertEquals(1, response.dailyMissionCount());
        assertEquals("09:00", response.missionSlots().getFirst().publishTime());
        assertEquals("23:00", response.missionSlots().getFirst().deadlineTime());
        assertFalse(response.missionSlots().getFirst().custom());
        ArgumentCaptor<GroupMember> member = ArgumentCaptor.forClass(GroupMember.class);
        verify(groupMemberMapper).insert(member.capture());
        assertEquals(11L, member.getValue().getGroupId());
        assertEquals(7L, member.getValue().getUserId());
        assertEquals(GroupMember.ROLE_OWNER, member.getValue().getRole());
        verify(events).publishEvent(new GroupCreatedEvent(11L));
    }

    @Test
    void createGroupStoresOnlyCustomizedSlotsAndExpandsResponse() {
        assignGroupId();
        when(slotMapper.findByGroupId(11L)).thenReturn(List.of(
                slot(11L, 2, "14:00", "22:00"), slot(11L, 3, "19:00", null)));
        var response = service.createGroup(7L, new GroupCreateRequest("Home", "FAMILY", null, 3,
                List.of(new MissionSlotRequest(1, null, null), new MissionSlotRequest(2, "14:00", "22:00"),
                        new MissionSlotRequest(3, "19:00", null))));

        assertEquals("FAMILY", response.relationType());
        assertEquals(3, response.missionSlots().size());
        assertFalse(response.missionSlots().get(0).custom());
        assertEquals("14:00", response.missionSlots().get(1).publishTime());
        assertEquals("22:00", response.missionSlots().get(1).deadlineTime());
        assertTrue(response.missionSlots().get(1).custom());
        assertEquals("19:00", response.missionSlots().get(2).publishTime());
        assertEquals("23:00", response.missionSlots().get(2).deadlineTime());
        ArgumentCaptor<GroupMissionSlot> inserted = ArgumentCaptor.forClass(GroupMissionSlot.class);
        verify(slotMapper, org.mockito.Mockito.times(2)).insert(inserted.capture());
        assertEquals(List.of(2, 3), inserted.getAllValues().stream().map(GroupMissionSlot::getSlotNo).toList());
    }

    @Test
    void replacingAndClearingSlotsDeletesExistingRows() {
        Group group = group(11L, 7L);
        when(groupMapper.findByIdForUpdate(11L)).thenReturn(group);
        when(groupMapper.findById(11L)).thenReturn(group);
        service.updateSettings(7L, 11L, new GroupSettingsUpdateRequest(null, null, null, 2,
                List.of(new MissionSlotRequest(2, "14:00", null))));
        verify(slotMapper).deleteByGroupId(11L);
        verify(slotMapper).insert(any(GroupMissionSlot.class));

        service.updateSettings(7L, 11L, new GroupSettingsUpdateRequest(null, null, null, null, List.of()));
        verify(slotMapper, org.mockito.Mockito.times(2)).deleteByGroupId(11L);
        verify(slotMapper, org.mockito.Mockito.times(1)).insert(any(GroupMissionSlot.class));
    }

    @Test
    void loweringCountTrimsSlotsAndOmittedSettingsStayUnchanged() {
        Group group = group(11L, 7L);
        group.setDailyMissionCount(5);
        when(groupMapper.findByIdForUpdate(11L)).thenReturn(group);
        when(groupMapper.findById(11L)).thenReturn(group);
        var response = service.updateSettings(7L, 11L,
                new GroupSettingsUpdateRequest("New", null, null, 2, null));
        assertEquals("New", response.name());
        assertEquals("CASUAL", response.relationType());
        assertEquals(2, response.dailyMissionCount());
        verify(slotMapper).deleteByGroupIdAndSlotNoGreaterThan(11L, 2);
        verify(slotMapper, never()).deleteByGroupId(11L);
    }

    @Test
    void nameOnlyUpdateLeavesSlotsUntouched() {
        Group group = group(11L, 7L);
        when(groupMapper.findByIdForUpdate(11L)).thenReturn(group);
        when(groupMapper.findById(11L)).thenReturn(group);
        var response = service.updateSettings(7L, 11L,
                new GroupSettingsUpdateRequest("New", null, null, null, null));
        assertEquals("New", response.name());
        assertEquals("09:00", response.missionSlots().getFirst().publishTime());
        verify(slotMapper, never()).deleteByGroupId(11L);
        verify(slotMapper, never()).deleteByGroupIdAndSlotNoGreaterThan(11L, 1);
    }

    @Test
    void invalidSettingsReturnValidationError() {
        when(groupMapper.findByIdForUpdate(11L)).thenAnswer(invocation -> group(11L, 7L));
        assertValidation(() -> service.updateSettings(7L, 11L,
                new GroupSettingsUpdateRequest(null, null, null, 6, null)));
        assertValidation(() -> service.updateSettings(7L, 11L,
                new GroupSettingsUpdateRequest(null, "family", null, null, null)));
        assertValidation(() -> service.updateSettings(7L, 11L,
                new GroupSettingsUpdateRequest(null, null, null, null, List.of(new MissionSlotRequest(2, null, null)))));
        assertValidation(() -> service.updateSettings(7L, 11L,
                new GroupSettingsUpdateRequest(null, null, null, null,
                        List.of(new MissionSlotRequest(1, null, null), new MissionSlotRequest(1, null, null)))));
        assertValidation(() -> service.updateSettings(7L, 11L,
                new GroupSettingsUpdateRequest(null, null, null, null, List.of(new MissionSlotRequest(0, null, null)))));
        assertValidation(() -> service.updateSettings(7L, 11L,
                new GroupSettingsUpdateRequest(null, null, null, null, List.of(new MissionSlotRequest(1, "9:00", null)))));
        verify(slotMapper, never()).insert(any(GroupMissionSlot.class));
        verify(slotMapper, never()).deleteByGroupId(11L);
    }

    @Test
    void nonOwnerCannotChangeSettings() {
        when(groupMapper.findByIdForUpdate(11L)).thenReturn(group(11L, 7L));
        BusinessException error = assertThrows(BusinessException.class,
                () -> service.updateSettings(8L, 11L, new GroupSettingsUpdateRequest("New", null, null, null, null)));
        assertEquals(GroupErrorCode.NOT_GROUP_OWNER, error.errorCode());
        verify(slotMapper, never()).deleteByGroupId(11L);
    }

    @Test
    void largePageUsesLongOffsetForBothLists() {
        when(groupMapper.findSummariesByUserId(7L, 214748364700L, 100)).thenReturn(List.of());
        service.listMyGroups(7L, Integer.MAX_VALUE, 100);
        verify(groupMapper).findSummariesByUserId(7L, 214748364700L, 100);
        when(groupMapper.findById(11L)).thenReturn(group(11L, 7L));
        when(groupMemberMapper.existsByGroupIdAndUserId(11L, 7L)).thenReturn(true);
        when(groupMemberMapper.findMembersByGroupId(11L, 214748364700L, 100)).thenReturn(List.of());
        service.listMembers(7L, 11L, Integer.MAX_VALUE, 100);
        verify(groupMemberMapper).findMembersByGroupId(11L, 214748364700L, 100);
    }

    @Test
    void missingGroupOnLeaveReturnsNotFound() {
        BusinessException error = assertThrows(BusinessException.class, () -> service.leaveGroup(7L, 11L, null));
        assertEquals(GroupErrorCode.GROUP_NOT_FOUND, error.errorCode());
    }

    @Test
    void nonMemberCannotReadGroup() {
        when(groupMapper.findById(11L)).thenReturn(group(11L, 7L));
        BusinessException error = assertThrows(BusinessException.class, () -> service.getGroup(8L, 11L));
        assertEquals(GroupErrorCode.NOT_GROUP_MEMBER, error.errorCode());
    }

    private void assignGroupId() {
        AtomicReference<Group> inserted = new AtomicReference<>();
        doAnswer(invocation -> {
            Group group = invocation.getArgument(0);
            group.setId(11L);
            inserted.set(group);
            return null;
        }).when(groupMapper).insert(any(Group.class));
        when(groupMapper.findById(11L)).thenAnswer(invocation -> inserted.get());
    }

    private static void assertValidation(org.junit.jupiter.api.function.Executable action) {
        assertEquals(CommonErrorCode.VALIDATION_ERROR,
                assertThrows(BusinessException.class, action).errorCode());
    }

    private static GroupMissionSlot slot(Long groupId, int number, String publish, String deadline) {
        GroupMissionSlot slot = new GroupMissionSlot();
        slot.setGroupId(groupId);
        slot.setSlotNo(number);
        if (publish != null) slot.setPublishTime(LocalTime.parse(publish));
        if (deadline != null) slot.setDeadlineTime(LocalTime.parse(deadline));
        return slot;
    }

    private static Group group(Long id, Long ownerId) {
        Group group = new Group();
        group.setId(id);
        group.setOwnerId(ownerId);
        group.setName("Home");
        group.setRelationType("CASUAL");
        group.setDailyMissionCount(1);
        return group;
    }
}
