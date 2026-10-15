package com.sapphire.hawe.group.service;

import com.sapphire.hawe.mission.domain.MissionTemplateSelector;
import com.sapphire.hawe.global.config.MissionProperties;
import com.sapphire.hawe.global.exception.BusinessException;
import com.sapphire.hawe.global.exception.CommonErrorCode;
import com.sapphire.hawe.global.time.SeoulTime;
import com.sapphire.hawe.global.storage.FileStorage;
import com.sapphire.hawe.global.storage.FileCleanup;
import com.sapphire.hawe.global.ratelimit.AttemptLimiter;
import com.sapphire.hawe.group.dao.GroupMapper;
import com.sapphire.hawe.group.dao.GroupMemberPreviewRow;
import com.sapphire.hawe.group.dao.GroupSummaryRow;
import com.sapphire.hawe.group.dao.GroupMemberMapper;
import com.sapphire.hawe.group.dao.GroupMissionSlotMapper;
import com.sapphire.hawe.group.domain.Group;
import com.sapphire.hawe.group.domain.GroupMember;
import com.sapphire.hawe.group.dto.GroupCreateRequest;
import com.sapphire.hawe.group.dto.GroupSettingsUpdateRequest;
import com.sapphire.hawe.group.dto.JoinRequest;
import com.sapphire.hawe.mission.dto.TodayMissionSummary;
import com.sapphire.hawe.mission.service.MissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.MockedStatic;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GroupContractServiceTests {
    @Mock GroupMapper groups;
    @Mock GroupMemberMapper members;
    @Mock GroupMissionSlotMapper slots;
    @Mock MissionService missionService;
    @Mock ApplicationEventPublisher events;
    @Mock FileStorage storage;
    @Mock FileCleanup fileCleanup;
    @Mock AttemptLimiter inviteLimiter;
    GroupService service;

    @BeforeEach
    void setUp() {
        service = new GroupService(groups, members, slots,
                new MissionProperties(LocalTime.of(9, 0), LocalTime.of(23, 0), 5, MissionTemplateSelector.MVP_POOL), "https://hawe.app/invite",
                missionService, events, storage, inviteLimiter, fileCleanup);
    }

    @Test
    void createTrimsNameStoresStartDateAndRetriesCodeCollision() {
        AtomicInteger attempts = new AtomicInteger();
        doAnswer(invocation -> {
            if (attempts.getAndIncrement() == 0) throw new DuplicateKeyException("code collision");
            insertedGroup = invocation.getArgument(0);
            insertedGroup.setId(11L);
            return null;
        }).when(groups).insert(any(Group.class));
        when(groups.findById(11L)).thenAnswer(invocation -> {
            Group group = insertedGroup;
            group.setId(11L);
            return group;
        });
        var response = service.createGroup(7L,
                new GroupCreateRequest("  우리 둘  ", "COUPLE", SeoulTime.today(), null, null));
        assertEquals("우리 둘", response.name());
        assertEquals(SeoulTime.today(), response.startDate());
        assertEquals(2, response.maxMembers());
        assertTrue(response.inviteCode().matches("[A-HJ-NP-Z2-9]{6}"));
        assertEquals("https://hawe.app/invite/" + response.inviteCode(), response.inviteUrl());
        verify(groups, times(2)).insert(any(Group.class));
        verify(members).insert(argThat(member -> member.getUserId().equals(7L)
                && GroupMember.ROLE_OWNER.equals(member.getRole())));
    }

    private Group insertedGroup;

    @Test
    void createRejectsWrongOrFutureStartDate() {
        assertCode(CommonErrorCode.VALIDATION_ERROR, () -> service.createGroup(7L,
                new GroupCreateRequest("  ", "FAMILY", null, null, null)));
        assertCode(CommonErrorCode.VALIDATION_ERROR, () -> service.createGroup(7L,
                new GroupCreateRequest("Home", "FAMILY", SeoulTime.today(), null, null)));
        assertCode(CommonErrorCode.VALIDATION_ERROR, () -> service.createGroup(7L,
                new GroupCreateRequest("Home", "COUPLE", SeoulTime.today().plusDays(1), null, null)));
        verify(groups, never()).insert(any(Group.class));
    }

    @Test
    void previewAndJoinNormalizeCodeAndCheckMemberBeforeCapacity() {
        Group group = group("COUPLE");
        when(groups.findByInviteCode("K7Q2PX")).thenReturn(group);
        when(members.countByGroupId(11L)).thenReturn(2L);
        assertCode(GroupErrorCode.GROUP_FULL, () -> service.previewInvite(8L, " k7q2px "));
        when(members.existsByGroupIdAndUserId(11L, 7L)).thenReturn(true);
        assertCode(GroupErrorCode.ALREADY_MEMBER, () -> service.previewInvite(7L, "K7Q2PX"));
        verify(groups, times(2)).findByInviteCode("K7Q2PX");
        verify(groups, never()).findByInviteCode("ABC");
        assertCode(GroupErrorCode.INVITE_NOT_FOUND, () -> service.previewInvite(8L, "ABC"));
        when(groups.findByInviteCodeForUpdate("K7Q2PX")).thenReturn(group);
        assertCode(GroupErrorCode.GROUP_FULL, () -> service.join(8L, new JoinRequest(" k7q2px ")));
        verify(groups).findByInviteCodeForUpdate("K7Q2PX");
    }

    @Test
    void previewReturnsMembersAndSummaryBatchesMemberLookup() {
        Group group = group("FRIEND");
        when(groups.findByInviteCode("K7Q2PX")).thenReturn(group);
        when(members.countByGroupId(11L)).thenReturn(1L);
        GroupMemberPreviewRow preview = new GroupMemberPreviewRow();
        preview.setGroupId(11L);
        preview.setUserId(7L);
        preview.setNickname("Owner");
        preview.setCharacterKey("mongsil");
        when(groups.findMemberPreviewsByGroupIds(List.of(11L))).thenReturn(List.of(preview));
        var result = service.previewInvite(8L, " k7q2px ");
        assertEquals(1, result.memberCount());
        assertEquals("mongsil", result.members().getFirst().characterKey());
        GroupSummaryRow row = new GroupSummaryRow();
        row.setId(11L);
        row.setName("Home");
        row.setRelationType("FRIEND");
        when(groups.findSummariesByUserId(8L, 0L, 20)).thenReturn(List.of(row));
        when(missionService.summarizeCurrent(List.of(11L), 8L)).thenReturn(Map.of());
        var page = service.listMyGroups(8L, 0, 20);
        assertEquals(1, page.content().getFirst().memberCount());
        assertNull(page.content().getFirst().todayMission());
        assertEquals(List.of(), page.content().getFirst().todayMissions());
        assertFalse(page.content().getFirst().submittedToday());
        verify(groups, times(2)).findMemberPreviewsByGroupIds(List.of(11L));
    }

    @Test
    void groupListShowsEveryOpenMissionAndKeepsFirstSlotFields() {
        GroupSummaryRow row = new GroupSummaryRow();
        row.setId(11L);
        row.setName("Home");
        row.setRelationType("FRIEND");
        when(groups.findSummariesByUserId(8L, 0L, 20)).thenReturn(List.of(row));
        var first = new TodayMissionSummary(10L, "PHOTO", "창밖을 찍어줘", 1, false);
        var second = new TodayMissionSummary(12L, "TEXT", "삼행시를 지어줘", 2, true);
        when(missionService.summarizeCurrent(List.of(11L), 8L)).thenReturn(Map.of(11L, List.of(first, second)));

        var summary = service.listMyGroups(8L, 0, 20).content().getFirst();

        assertEquals(List.of(first, second), summary.todayMissions());
        // 예전 필드는 첫 슬롯 기준 그대로 (2번에 냈어도 1번을 안 냈으면 false)
        assertEquals(first, summary.todayMission());
        assertFalse(summary.submittedToday());
    }

    @Test
    void joinUsesLockedLookupAndHandlesDuplicateInsert() {
        Group group = group("FRIEND");
        when(groups.findByInviteCodeForUpdate("K7Q2PX")).thenReturn(group);
        when(members.countByGroupId(11L)).thenReturn(5L);
        assertEquals(11L, service.join(8L, new JoinRequest("k7q2px")).groupId());
        verify(members).insert(argThat(member -> member.getGroupId().equals(11L)
                && GroupMember.ROLE_MEMBER.equals(member.getRole())));
        doThrow(new DuplicateKeyException("duplicate")).when(members).insert(any(GroupMember.class));
        assertCode(GroupErrorCode.ALREADY_MEMBER, () -> service.join(8L, new JoinRequest("K7Q2PX")));
        verify(groups, times(2)).findByInviteCodeForUpdate("K7Q2PX");
    }

    @Test
    void reissueRequiresGroupMembershipAndOwnership() {
        assertCode(GroupErrorCode.GROUP_NOT_FOUND, () -> service.reissueInviteCode(7L, 11L));
        Group group = group("FRIEND");
        when(groups.findByIdForUpdate(11L)).thenReturn(group);
        assertCode(GroupErrorCode.NOT_GROUP_MEMBER, () -> service.reissueInviteCode(8L, 11L));
        when(members.existsByGroupIdAndUserId(11L, 8L)).thenReturn(true);
        assertCode(GroupErrorCode.NOT_GROUP_OWNER, () -> service.reissueInviteCode(8L, 11L));
        verify(groups, never()).updateInviteCode(any(), any());
    }

    @Test
    void reissueSkipsCurrentCodeAndRetriesCollision() {
        Group group = group("FRIEND");
        when(groups.findByIdForUpdate(11L)).thenReturn(group);
        when(members.existsByGroupIdAndUserId(11L, 7L)).thenReturn(true);
        doThrow(new DuplicateKeyException("collision")).when(groups).updateInviteCode(11L, "NEWABC");
        try (MockedStatic<InviteCodeGenerator> generator = mockStatic(InviteCodeGenerator.class)) {
            generator.when(InviteCodeGenerator::generate)
                    .thenReturn("K7Q2PX", "NEWABC", "NEWDEF");
            var response = service.reissueInviteCode(7L, 11L);
            assertEquals("NEWDEF", response.inviteCode());
            assertEquals("https://hawe.app/invite/NEWDEF", response.inviteUrl());
            verify(groups, never()).updateInviteCode(11L, "K7Q2PX");
            verify(groups).updateInviteCode(11L, "NEWABC");
            verify(groups).updateInviteCode(11L, "NEWDEF");
        }
    }

    @Test
    void inviteLimiterCountsOnlyUnknownCodesAndBlocksBeforeLookup() {
        Group group = group("FRIEND");
        when(groups.findByInviteCode("K7Q2PX")).thenReturn(group);
        when(members.existsByGroupIdAndUserId(11L, 8L)).thenReturn(true);
        assertCode(GroupErrorCode.ALREADY_MEMBER, () -> service.previewInvite(8L, "K7Q2PX"));
        verify(inviteLimiter, never()).recordFailure(any());
        assertCode(GroupErrorCode.INVITE_NOT_FOUND, () -> service.previewInvite(8L, "BAD"));
        verify(inviteLimiter).recordFailure(8L);
        assertCode(GroupErrorCode.INVITE_NOT_FOUND, () -> service.join(8L, new JoinRequest("BAD")));
        verify(inviteLimiter, times(2)).recordFailure(8L);
        doThrow(new BusinessException(CommonErrorCode.TOO_MANY_REQUESTS)).when(inviteLimiter).check(8L);
        assertCode(CommonErrorCode.TOO_MANY_REQUESTS, () -> service.previewInvite(8L, "K7Q2PX"));
        verify(groups, times(1)).findByInviteCode("K7Q2PX");
        verify(inviteLimiter, times(2)).recordFailure(8L);
    }

    @Test
    void updateEnforcesCapacityAndStartDateRules() {
        Group group = group("FRIEND");
        when(groups.findByIdForUpdate(11L)).thenReturn(group);
        when(members.countByGroupId(11L)).thenReturn(3L);
        assertCode(CommonErrorCode.VALIDATION_ERROR, () -> service.updateSettings(7L, 11L,
                new GroupSettingsUpdateRequest("   ", null, null, null, null)));
        assertCode(CommonErrorCode.VALIDATION_ERROR, () -> service.updateSettings(7L, 11L,
                new GroupSettingsUpdateRequest("abcdefghijklmnopq", null, null, null, null)));
        assertCode(GroupErrorCode.GROUP_FULL, () -> service.updateSettings(7L, 11L,
                new GroupSettingsUpdateRequest(null, "COUPLE", null, null, null)));
        assertCode(CommonErrorCode.VALIDATION_ERROR, () -> service.updateSettings(7L, 11L,
                new GroupSettingsUpdateRequest(null, null, SeoulTime.today(), null, null)));
        verify(groups, never()).update(any(Group.class));
        group.setRelationType("COUPLE");
        group.setStartDate(SeoulTime.today());
        when(members.countByGroupId(11L)).thenReturn(2L);
        when(groups.findById(11L)).thenReturn(group);
        assertNull(service.updateSettings(7L, 11L,
                new GroupSettingsUpdateRequest(null, "FAMILY", null, null, null)).startDate());
        assertEquals("FAMILY", group.getRelationType());
        assertNull(group.getStartDate());
    }

    @Test
    void leaveRequiresValidSuccessorAndTransfersOwnership() {
        Group group = group("FRIEND");
        GroupMember owner = member(GroupMember.ROLE_OWNER);
        owner.setProfileImageUrlOverride("group-profiles/owner.jpg");
        when(groups.findByIdForUpdate(11L)).thenReturn(group);
        when(members.findByGroupIdAndUserId(11L, 7L)).thenReturn(owner);
        when(members.countByGroupId(11L)).thenReturn(2L);
        assertCode(GroupErrorCode.SUCCESSOR_REQUIRED, () -> service.leaveGroup(7L, 11L, null));
        assertCode(GroupErrorCode.INVALID_SUCCESSOR, () -> service.leaveGroup(7L, 11L, 7L));
        assertCode(GroupErrorCode.INVALID_SUCCESSOR, () -> service.leaveGroup(7L, 11L, 9L));
        verifyNoInteractions(fileCleanup);
        verify(groups, never()).update(any(Group.class));
        when(members.existsByGroupIdAndUserId(11L, 8L)).thenReturn(true);
        service.leaveGroup(7L, 11L, 8L);
        assertEquals(8L, group.getOwnerId());
        InOrder order = inOrder(groups, members);
        order.verify(groups).update(group);
        order.verify(members).updateRole(11L, 8L, GroupMember.ROLE_OWNER);
        order.verify(members).deleteByGroupIdAndUserId(11L, 7L);
        verify(fileCleanup).deleteAfterCommit(List.of("group-profiles/owner.jpg"));
    }

    @Test
    void ordinaryMemberLeavesWithoutSuccessorAndNonmemberCannotLeave() {
        when(groups.findByIdForUpdate(11L)).thenReturn(group("FRIEND"));
        assertCode(GroupErrorCode.NOT_GROUP_MEMBER, () -> service.leaveGroup(8L, 11L, null));
        GroupMember member = member(GroupMember.ROLE_MEMBER);
        member.setProfileImageUrlOverride("group-profiles/member.jpg");
        when(members.findByGroupIdAndUserId(11L, 8L)).thenReturn(member);
        service.leaveGroup(8L, 11L, null);
        verify(members).deleteByGroupIdAndUserId(11L, 8L);
        verify(fileCleanup).deleteAfterCommit(List.of("group-profiles/member.jpg"));
        verify(groups, never()).findSubmissionMediaKeysByGroupId(any());
        verify(groups, never()).update(any(Group.class));
    }

    @Test
    void memberWithoutGroupPhotoDoesNotScheduleAFile() {
        when(groups.findByIdForUpdate(11L)).thenReturn(group("FRIEND"));
        when(members.findByGroupIdAndUserId(11L, 8L)).thenReturn(member(GroupMember.ROLE_MEMBER));
        service.leaveGroup(8L, 11L, null);
        verify(fileCleanup).deleteAfterCommit(argThat(keys -> keys.stream().noneMatch(key -> key != null && !key.isBlank())));
        verify(groups, never()).findSubmissionMediaKeysByGroupId(any());
    }

    @Test
    void lastOwnerDeletesDependentsInForeignKeyOrder() {
        when(groups.findByIdForUpdate(11L)).thenReturn(group("FRIEND"));
        when(members.findByGroupIdAndUserId(11L, 7L)).thenReturn(member(GroupMember.ROLE_OWNER));
        when(members.countByGroupId(11L)).thenReturn(1L);
        when(groups.findSubmissionMediaKeysByGroupId(11L)).thenReturn(List.of("submissions/photo.jpg"));
        when(groups.findCommentImageKeysByGroupId(11L)).thenReturn(List.of("comments/photo.jpg"));
        when(groups.findGroupProfileImageKeysByGroupId(11L)).thenReturn(List.of("group-profiles/photo.jpg"));
        service.leaveGroup(7L, 11L, null);
        InOrder order = inOrder(groups, members, slots, fileCleanup);
        order.verify(groups).findSubmissionMediaKeysByGroupId(11L);
        order.verify(groups).findCommentImageKeysByGroupId(11L);
        order.verify(groups).findGroupProfileImageKeysByGroupId(11L);
        order.verify(fileCleanup).deleteAfterCommit(List.of("submissions/photo.jpg", "comments/photo.jpg", "group-profiles/photo.jpg"));
        order.verify(groups).deleteCommentMentionsByGroupId(11L);
        order.verify(groups).deleteCommentsByGroupId(11L);
        order.verify(groups).deleteReactionsByGroupId(11L);
        order.verify(groups).deleteSubmissionGuessesByGroupId(11L);
        order.verify(groups).deleteAiReviewsByGroupId(11L);
        order.verify(groups).deleteSubmissionsByGroupId(11L);
        order.verify(groups).deletePokesByGroupId(11L);
        order.verify(groups).deleteMissionsByGroupId(11L);
        order.verify(slots).deleteByGroupId(11L);
        order.verify(groups).deleteInsightsByGroupId(11L);
        order.verify(members).deleteByGroupId(11L);
        order.verify(groups).deleteById(11L);
    }

    private static Group group(String relation) {
        Group group = new Group();
        group.setId(11L);
        group.setOwnerId(7L);
        group.setName("Home");
        group.setInviteCode("K7Q2PX");
        group.setRelationType(relation);
        group.setDailyMissionCount(1);
        return group;
    }

    private static GroupMember member(String role) {
        GroupMember member = new GroupMember();
        member.setGroupId(11L);
        member.setUserId(7L);
        member.setRole(role);
        return member;
    }

    private static void assertCode(com.sapphire.hawe.global.exception.ErrorCode code,
            org.junit.jupiter.api.function.Executable action) {
        assertEquals(code, assertThrows(BusinessException.class, action).errorCode());
    }
}
