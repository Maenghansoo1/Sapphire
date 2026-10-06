package com.sapphire.hawe.group.service;

import com.sapphire.hawe.global.exception.BusinessException;
import com.sapphire.hawe.global.security.JwtTokenProvider;
import com.sapphire.hawe.global.security.TokenErrorCode;
import com.sapphire.hawe.group.dao.GroupMapper;
import com.sapphire.hawe.group.dao.GroupMemberMapper;
import com.sapphire.hawe.group.domain.Group;
import com.sapphire.hawe.group.domain.GroupMember;
import com.sapphire.hawe.group.dto.GroupCreateRequest;
import com.sapphire.hawe.group.dto.GroupSettingsUpdateRequest;
import com.sapphire.hawe.group.dto.JoinRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GroupServiceTests {

    @Mock GroupMapper groupMapper;
    @Mock GroupMemberMapper groupMemberMapper;
    @Mock JwtTokenProvider jwtTokenProvider;

    private GroupService service;

    @BeforeEach
    void setUp() {
        service = new GroupService(groupMapper, groupMemberMapper, jwtTokenProvider);
    }

    @Test
    void createGroupAddsOwnerMembershipAndFormatsTimes() {
        doAnswer(invocation -> {
            Group group = invocation.getArgument(0);
            group.setId(11L);
            return null;
        }).when(groupMapper).insert(any(Group.class));

        var response = service.createGroup(7L, new GroupCreateRequest("Home", "09:00", "23:00"));

        assertEquals(11L, response.id());
        assertEquals("09:00", response.missionPublishTime());
        assertEquals("23:00", response.missionDeadlineTime());
        org.mockito.ArgumentCaptor<GroupMember> member = org.mockito.ArgumentCaptor.forClass(GroupMember.class);
        verify(groupMemberMapper).insert(member.capture());
        assertEquals(11L, member.getValue().getGroupId());
        assertEquals(7L, member.getValue().getUserId());
        assertEquals(GroupMember.ROLE_OWNER, member.getValue().getRole());
    }

    @Test
    void duplicateInsertDuringJoinReturnsAlreadyMember() {
        when(jwtTokenProvider.validateInviteTokenAndGetGroupId("token")).thenReturn(11L);
        when(groupMapper.findById(11L)).thenReturn(group(11L, 7L));
        doThrow(new DuplicateKeyException("duplicate")).when(groupMemberMapper).insert(any(GroupMember.class));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.joinByInvite(8L, new JoinRequest("token")));

        assertEquals(GroupErrorCode.ALREADY_MEMBER, error.errorCode());
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
        BusinessException error = assertThrows(BusinessException.class, () -> service.leaveGroup(7L, 11L));
        assertEquals(GroupErrorCode.GROUP_NOT_FOUND, error.errorCode());
    }

    @Test
    void nonMemberCannotReadGroup() {
        when(groupMapper.findById(11L)).thenReturn(group(11L, 7L));
        BusinessException error = assertThrows(BusinessException.class, () -> service.getGroup(8L, 11L));
        assertEquals(GroupErrorCode.NOT_GROUP_MEMBER, error.errorCode());
    }

    @Test
    void nonOwnerCannotChangeSettings() {
        when(groupMapper.findById(11L)).thenReturn(group(11L, 7L));
        BusinessException error = assertThrows(BusinessException.class,
                () -> service.updateSettings(8L, 11L, new GroupSettingsUpdateRequest("New", null, null)));
        assertEquals(GroupErrorCode.NOT_GROUP_OWNER, error.errorCode());
    }

    @Test
    void expiredInviteHasSpecificError() {
        when(jwtTokenProvider.validateInviteTokenAndGetGroupId("token"))
                .thenThrow(new BusinessException(TokenErrorCode.TOKEN_EXPIRED));
        BusinessException error = assertThrows(BusinessException.class,
                () -> service.joinByInvite(8L, new JoinRequest("token")));
        assertEquals(GroupErrorCode.INVITE_EXPIRED, error.errorCode());
    }

    @Test
    void settingsUpdatePreservesOmittedFields() {
        Group group = group(11L, 7L);
        when(groupMapper.findById(11L)).thenReturn(group);

        var response = service.updateSettings(7L, 11L, new GroupSettingsUpdateRequest("New", null, null));

        assertEquals("New", response.name());
        assertEquals("09:00", response.missionPublishTime());
        assertEquals("23:00", response.missionDeadlineTime());
        verify(groupMapper).update(group);
    }

    private static Group group(Long id, Long ownerId) {
        Group group = new Group();
        group.setId(id);
        group.setOwnerId(ownerId);
        group.setName("Home");
        group.setMissionPublishTime(LocalTime.of(9, 0));
        group.setMissionDeadlineTime(LocalTime.of(23, 0));
        return group;
    }
}
