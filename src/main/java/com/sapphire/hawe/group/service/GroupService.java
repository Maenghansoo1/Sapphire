package com.sapphire.hawe.group.service;

import com.sapphire.hawe.global.dto.PageResponse;
import com.sapphire.hawe.global.exception.BusinessException;
import com.sapphire.hawe.global.security.JwtTokenProvider;
import com.sapphire.hawe.global.security.TokenErrorCode;
import com.sapphire.hawe.group.dao.GroupMapper;
import com.sapphire.hawe.group.dao.GroupMemberMapper;
import com.sapphire.hawe.group.dao.GroupMemberRow;
import com.sapphire.hawe.group.dao.GroupSummaryRow;
import com.sapphire.hawe.group.domain.Group;
import com.sapphire.hawe.group.domain.GroupMember;
import com.sapphire.hawe.group.dto.GroupCreateRequest;
import com.sapphire.hawe.group.dto.GroupMemberResponse;
import com.sapphire.hawe.group.dto.GroupResponse;
import com.sapphire.hawe.group.dto.GroupSettingsUpdateRequest;
import com.sapphire.hawe.group.dto.GroupSummaryResponse;
import com.sapphire.hawe.group.dto.InviteResponse;
import com.sapphire.hawe.group.dto.JoinRequest;
import com.sapphire.hawe.group.dto.JoinResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class GroupService {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final GroupMapper groupMapper;
    private final GroupMemberMapper groupMemberMapper;
    private final JwtTokenProvider jwtTokenProvider;

    public GroupService(GroupMapper groupMapper, GroupMemberMapper groupMemberMapper,
            JwtTokenProvider jwtTokenProvider) {
        this.groupMapper = groupMapper;
        this.groupMemberMapper = groupMemberMapper;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    public PageResponse<GroupSummaryResponse> listMyGroups(Long userId, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = clampSize(size);
        List<GroupSummaryRow> rows = groupMapper.findSummariesByUserId(userId, safePage * safeSize, safeSize);
        long total = groupMapper.countByUserId(userId);

        List<GroupSummaryResponse> content = rows.stream()
                .map(row -> new GroupSummaryResponse(row.getId(), row.getName(), row.isSubmittedToday()))
                .toList();
        return PageResponse.of(content, safePage, safeSize, total);
    }

    @Transactional
    public GroupResponse createGroup(Long userId, GroupCreateRequest request) {
        Group group = new Group();
        group.setName(request.name());
        group.setOwnerId(userId);
        group.setMissionPublishTime(LocalTime.parse(request.missionPublishTime(), TIME_FORMAT));
        group.setMissionDeadlineTime(LocalTime.parse(request.missionDeadlineTime(), TIME_FORMAT));
        groupMapper.insert(group);

        GroupMember owner = new GroupMember();
        owner.setGroupId(group.getId());
        owner.setUserId(userId);
        owner.setRole(GroupMember.ROLE_OWNER);
        groupMemberMapper.insert(owner);

        return toGroupResponse(group);
    }

    public GroupResponse getGroup(Long userId, Long groupId) {
        Group group = requireGroup(groupId);
        requireMember(groupId, userId);
        return toGroupResponse(group);
    }

    @Transactional
    public GroupResponse updateSettings(Long userId, Long groupId, GroupSettingsUpdateRequest request) {
        Group group = requireGroup(groupId);
        requireOwner(group, userId);

        if (request.name() != null) {
            group.setName(request.name());
        }
        if (request.missionPublishTime() != null) {
            group.setMissionPublishTime(LocalTime.parse(request.missionPublishTime(), TIME_FORMAT));
        }
        if (request.missionDeadlineTime() != null) {
            group.setMissionDeadlineTime(LocalTime.parse(request.missionDeadlineTime(), TIME_FORMAT));
        }
        groupMapper.update(group);
        return toGroupResponse(group);
    }

    public PageResponse<GroupMemberResponse> listMembers(Long userId, Long groupId, int page, int size) {
        requireGroup(groupId);
        requireMember(groupId, userId);

        int safePage = Math.max(page, 0);
        int safeSize = clampSize(size);
        List<GroupMemberRow> rows = groupMemberMapper.findMembersByGroupId(groupId, safePage * safeSize, safeSize);
        long total = groupMemberMapper.countByGroupId(groupId);

        List<GroupMemberResponse> content = rows.stream()
                .map(row -> new GroupMemberResponse(row.getUserId(), row.getNickname(), row.getRole(),
                        row.getSubmissionCountThisMonth(), row.isStampedToday()))
                .toList();
        return PageResponse.of(content, safePage, safeSize, total);
    }

    public InviteResponse createInvite(Long userId, Long groupId) {
        Group group = requireGroup(groupId);
        requireOwner(group, userId);
        String token = jwtTokenProvider.createInviteToken(groupId);
        return new InviteResponse("hawe://join?token=" + token);
    }

    @Transactional
    public JoinResponse joinByInvite(Long userId, JoinRequest request) {
        Long groupId = validateInviteToken(request.token());
        Group group = groupMapper.findById(groupId);
        if (group == null) {
            throw new BusinessException(GroupErrorCode.INVALID_INVITE_TOKEN);
        }
        if (groupMemberMapper.existsByGroupIdAndUserId(groupId, userId)) {
            throw new BusinessException(GroupErrorCode.ALREADY_MEMBER);
        }

        GroupMember member = new GroupMember();
        member.setGroupId(groupId);
        member.setUserId(userId);
        member.setRole(GroupMember.ROLE_MEMBER);
        groupMemberMapper.insert(member);

        return new JoinResponse(group.getId(), group.getName());
    }

    @Transactional
    public void leaveGroup(Long userId, Long groupId) {
        GroupMember membership = groupMemberMapper.findByGroupIdAndUserId(groupId, userId);
        if (membership == null) {
            throw new BusinessException(GroupErrorCode.NOT_GROUP_MEMBER);
        }
        if (GroupMember.ROLE_OWNER.equals(membership.getRole())) {
            throw new BusinessException(GroupErrorCode.OWNER_CANNOT_LEAVE);
        }
        groupMemberMapper.deleteByGroupIdAndUserId(groupId, userId);
    }

    private Long validateInviteToken(String token) {
        try {
            return jwtTokenProvider.validateInviteTokenAndGetGroupId(token);
        } catch (BusinessException e) {
            if (e.errorCode() == TokenErrorCode.TOKEN_EXPIRED) {
                throw new BusinessException(GroupErrorCode.INVITE_EXPIRED);
            }
            throw new BusinessException(GroupErrorCode.INVALID_INVITE_TOKEN);
        }
    }

    private Group requireGroup(Long groupId) {
        Group group = groupMapper.findById(groupId);
        if (group == null) {
            throw new BusinessException(GroupErrorCode.GROUP_NOT_FOUND);
        }
        return group;
    }

    private void requireMember(Long groupId, Long userId) {
        if (!groupMemberMapper.existsByGroupIdAndUserId(groupId, userId)) {
            throw new BusinessException(GroupErrorCode.NOT_GROUP_MEMBER);
        }
    }

    private void requireOwner(Group group, Long userId) {
        if (!group.getOwnerId().equals(userId)) {
            throw new BusinessException(GroupErrorCode.NOT_GROUP_OWNER);
        }
    }

    private int clampSize(int size) {
        if (size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    private GroupResponse toGroupResponse(Group group) {
        return new GroupResponse(group.getId(), group.getName(), group.getOwnerId(),
                group.getMissionPublishTime().format(TIME_FORMAT), group.getMissionDeadlineTime().format(TIME_FORMAT));
    }
}
