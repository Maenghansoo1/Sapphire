package com.sapphire.hawe.group.service;

import com.sapphire.hawe.global.dto.PageResponse;
import com.sapphire.hawe.global.exception.BusinessException;
import com.sapphire.hawe.global.exception.CommonErrorCode;
import com.sapphire.hawe.global.config.MissionProperties;
import com.sapphire.hawe.global.security.JwtTokenProvider;
import com.sapphire.hawe.global.security.TokenErrorCode;
import com.sapphire.hawe.group.dao.GroupMapper;
import com.sapphire.hawe.group.dao.GroupMemberMapper;
import com.sapphire.hawe.group.dao.GroupMissionSlotMapper;
import com.sapphire.hawe.group.dao.GroupMemberRow;
import com.sapphire.hawe.group.dao.GroupSummaryRow;
import com.sapphire.hawe.group.domain.Group;
import com.sapphire.hawe.group.domain.GroupMember;
import com.sapphire.hawe.group.domain.GroupMissionSlot;
import com.sapphire.hawe.group.dto.GroupCreateRequest;
import com.sapphire.hawe.group.dto.GroupMemberResponse;
import com.sapphire.hawe.group.dto.GroupResponse;
import com.sapphire.hawe.group.dto.GroupSettingsUpdateRequest;
import com.sapphire.hawe.group.dto.GroupSummaryResponse;
import com.sapphire.hawe.group.dto.MissionSlotRequest;
import com.sapphire.hawe.group.dto.MissionSlotResponse;
import com.sapphire.hawe.group.dto.InviteResponse;
import com.sapphire.hawe.group.dto.JoinRequest;
import com.sapphire.hawe.group.dto.JoinResponse;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Service
@EnableConfigurationProperties(MissionProperties.class)
public class GroupService {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final GroupMapper groupMapper;
    private final GroupMemberMapper groupMemberMapper;
    private final GroupMissionSlotMapper slotMapper;
    private final MissionProperties missionProperties;
    private final JwtTokenProvider jwtTokenProvider;

    public GroupService(GroupMapper groupMapper, GroupMemberMapper groupMemberMapper,
            GroupMissionSlotMapper slotMapper, MissionProperties missionProperties, JwtTokenProvider jwtTokenProvider) {
        this.groupMapper = groupMapper;
        this.groupMemberMapper = groupMemberMapper;
        this.slotMapper = slotMapper;
        this.missionProperties = missionProperties;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    public PageResponse<GroupSummaryResponse> listMyGroups(Long userId, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = clampSize(size);
        List<GroupSummaryRow> rows = groupMapper.findSummariesByUserId(userId, (long) safePage * safeSize, safeSize);
        long total = groupMapper.countByUserId(userId);

        List<GroupSummaryResponse> content = rows.stream()
                .map(row -> new GroupSummaryResponse(row.getId(), row.getName(), row.isSubmittedToday()))
                .toList();
        return PageResponse.of(content, safePage, safeSize, total);
    }

    @Transactional
    public GroupResponse createGroup(Long userId, GroupCreateRequest request) {
        String relationType = request.relationType() == null ? "CASUAL" : request.relationType();
        int dailyMissionCount = request.dailyMissionCount() == null ? 1 : request.dailyMissionCount();
        validateSettings(relationType, dailyMissionCount, request.missionSlots());
        Group group = new Group();
        group.setName(request.name());
        group.setOwnerId(userId);
        group.setRelationType(relationType);
        group.setDailyMissionCount(dailyMissionCount);
        groupMapper.insert(group);

        GroupMember owner = new GroupMember();
        owner.setGroupId(group.getId());
        owner.setUserId(userId);
        owner.setRole(GroupMember.ROLE_OWNER);
        groupMemberMapper.insert(owner);

        if (request.missionSlots() != null) {
            insertSlots(group.getId(), request.missionSlots());
        }

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

        String relationType = request.relationType() == null ? group.getRelationType() : request.relationType();
        int previousDailyMissionCount = group.getDailyMissionCount();
        int dailyMissionCount = request.dailyMissionCount() == null
                ? group.getDailyMissionCount() : request.dailyMissionCount();
        validateSettings(relationType, dailyMissionCount, request.missionSlots());

        if (request.name() != null) {
            group.setName(request.name());
        }
        group.setRelationType(relationType);
        group.setDailyMissionCount(dailyMissionCount);
        groupMapper.update(group);
        if (request.missionSlots() != null) {
            slotMapper.deleteByGroupId(groupId);
            insertSlots(groupId, request.missionSlots());
        } else if (dailyMissionCount < previousDailyMissionCount) {
            slotMapper.deleteByGroupIdAndSlotNoGreaterThan(groupId, dailyMissionCount);
        }
        return toGroupResponse(group);
    }

    public PageResponse<GroupMemberResponse> listMembers(Long userId, Long groupId, int page, int size) {
        requireGroup(groupId);
        requireMember(groupId, userId);

        int safePage = Math.max(page, 0);
        int safeSize = clampSize(size);
        List<GroupMemberRow> rows = groupMemberMapper.findMembersByGroupId(groupId, (long) safePage * safeSize, safeSize);
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
        try {
            groupMemberMapper.insert(member);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(GroupErrorCode.ALREADY_MEMBER);
        }

        return new JoinResponse(group.getId(), group.getName());
    }

    @Transactional
    public void leaveGroup(Long userId, Long groupId) {
        requireGroup(groupId);
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
        Map<Integer, GroupMissionSlot> byNumber = new HashMap<>();
        for (GroupMissionSlot slot : slotMapper.findByGroupId(group.getId())) {
            byNumber.put(slot.getSlotNo(), slot);
        }
        List<MissionSlotResponse> slots = new ArrayList<>();
        for (int number = 1; number <= group.getDailyMissionCount(); number++) {
            GroupMissionSlot custom = byNumber.get(number);
            LocalTime publish = custom != null && custom.getPublishTime() != null
                    ? custom.getPublishTime() : missionProperties.defaultPublishTime();
            LocalTime deadline = custom != null && custom.getDeadlineTime() != null
                    ? custom.getDeadlineTime() : missionProperties.defaultDeadlineTime();
            boolean customized = custom != null
                    && (custom.getPublishTime() != null || custom.getDeadlineTime() != null);
            slots.add(new MissionSlotResponse(number, publish.format(TIME_FORMAT),
                    deadline.format(TIME_FORMAT), customized));
        }
        return new GroupResponse(group.getId(), group.getName(), group.getOwnerId(),
                group.getRelationType(), group.getDailyMissionCount(), slots);
    }

    private void validateSettings(String relationType, int dailyMissionCount, List<MissionSlotRequest> slots) {
        if (!Set.of("COUPLE", "FAMILY", "FRIEND", "RIVAL", "CASUAL").contains(relationType)
                || dailyMissionCount < 1 || dailyMissionCount > missionProperties.maxDailyCount()) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR);
        }
        if (slots == null) {
            return;
        }
        Set<Integer> numbers = new HashSet<>();
        for (MissionSlotRequest slot : slots) {
            if (slot == null || slot.slotNo() == null || slot.slotNo() < 1
                    || slot.slotNo() > dailyMissionCount || !numbers.add(slot.slotNo())
                    || !validTime(slot.publishTime()) || !validTime(slot.deadlineTime())) {
                throw new BusinessException(CommonErrorCode.VALIDATION_ERROR);
            }
        }
    }

    private boolean validTime(String time) {
        return time == null || time.matches("^([01]\\d|2[0-3]):[0-5]\\d$");
    }

    private void insertSlots(Long groupId, List<MissionSlotRequest> slots) {
        for (MissionSlotRequest request : slots) {
            if (request.publishTime() == null && request.deadlineTime() == null) {
                continue;
            }
            GroupMissionSlot slot = new GroupMissionSlot();
            slot.setGroupId(groupId);
            slot.setSlotNo(request.slotNo());
            if (request.publishTime() != null) {
                slot.setPublishTime(LocalTime.parse(request.publishTime(), TIME_FORMAT));
            }
            if (request.deadlineTime() != null) {
                slot.setDeadlineTime(LocalTime.parse(request.deadlineTime(), TIME_FORMAT));
            }
            slotMapper.insert(slot);
        }
    }
}
