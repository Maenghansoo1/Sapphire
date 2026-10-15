package com.sapphire.hawe.group.service;

import com.sapphire.hawe.global.dto.PageResponse;
import com.sapphire.hawe.global.exception.BusinessException;
import com.sapphire.hawe.global.exception.CommonErrorCode;
import com.sapphire.hawe.global.config.MissionProperties;
import com.sapphire.hawe.global.time.SeoulTime;
import com.sapphire.hawe.global.storage.FileStorage;
import com.sapphire.hawe.global.ratelimit.AttemptLimiter;
import com.sapphire.hawe.group.dao.GroupMapper;
import com.sapphire.hawe.group.dao.GroupMemberMapper;
import com.sapphire.hawe.group.dao.GroupMissionSlotMapper;
import com.sapphire.hawe.group.dao.GroupMemberRow;
import com.sapphire.hawe.group.dao.GroupMemberPreviewRow;
import com.sapphire.hawe.group.dao.GroupSummaryRow;
import com.sapphire.hawe.group.domain.Group;
import com.sapphire.hawe.group.domain.GroupMember;
import com.sapphire.hawe.group.domain.GroupMissionSlot;
import com.sapphire.hawe.group.domain.GroupCreatedEvent;
import com.sapphire.hawe.group.dto.GroupCreateRequest;
import com.sapphire.hawe.group.dto.GroupMemberResponse;
import com.sapphire.hawe.group.dto.GroupResponse;
import com.sapphire.hawe.group.dto.GroupSettingsUpdateRequest;
import com.sapphire.hawe.group.dto.GroupSummaryResponse;
import com.sapphire.hawe.group.dto.MissionSlotRequest;
import com.sapphire.hawe.group.dto.MissionSlotResponse;
import com.sapphire.hawe.group.dto.InvitePreviewResponse;
import com.sapphire.hawe.group.dto.InviteCodeResponse;
import com.sapphire.hawe.group.dto.MemberProfileResponse;
import com.sapphire.hawe.group.dto.JoinRequest;
import com.sapphire.hawe.group.dto.JoinResponse;
import com.sapphire.hawe.mission.dto.TodayMissionSummary;
import com.sapphire.hawe.mission.service.MissionService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.time.LocalDate;
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
    private final MissionService missionService;
    private final ApplicationEventPublisher events;
    private final String inviteBaseUrl;
    private final FileStorage storage;
    private final AttemptLimiter inviteLimiter;

    public GroupService(GroupMapper groupMapper, GroupMemberMapper groupMemberMapper,
            GroupMissionSlotMapper slotMapper, MissionProperties missionProperties,
            @Value("${hawe.invite.base-url:https://hawe.app/invite}") String inviteBaseUrl,
            MissionService missionService, ApplicationEventPublisher events, FileStorage storage,
            AttemptLimiter inviteLimiter) {
        this.groupMapper = groupMapper;
        this.groupMemberMapper = groupMemberMapper;
        this.slotMapper = slotMapper;
        this.missionProperties = missionProperties;
        this.missionService = missionService;
        this.events = events;
        this.storage = storage;
        this.inviteLimiter = inviteLimiter;
        this.inviteBaseUrl = inviteBaseUrl.replaceAll("/+$", "");
    }

    public PageResponse<GroupSummaryResponse> listMyGroups(Long userId, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = clampSize(size);
        List<GroupSummaryRow> rows = groupMapper.findSummariesByUserId(userId, (long) safePage * safeSize, safeSize);
        long total = groupMapper.countByUserId(userId);

        Map<Long, List<MemberProfileResponse>> membersByGroup = new HashMap<>();
        Map<Long, List<TodayMissionSummary>> missionsByGroup = Map.of();
        if (!rows.isEmpty()) {
            List<Long> groupIds = rows.stream().map(GroupSummaryRow::getId).toList();
            missionsByGroup = missionService.summarizeCurrent(groupIds, userId);
            for (GroupMemberPreviewRow member : groupMapper.findMemberPreviewsByGroupIds(groupIds)) {
                membersByGroup.computeIfAbsent(member.getGroupId(), ignored -> new ArrayList<>())
                        .add(toMemberProfile(member));
            }
        }
        Map<Long, List<TodayMissionSummary>> currentMissions = missionsByGroup;
        List<GroupSummaryResponse> content = rows.stream().map(row -> {
            List<MemberProfileResponse> members = membersByGroup.getOrDefault(row.getId(), List.of());
            int maxMembers = "COUPLE".equals(row.getRelationType()) ? 2 : 6;
            List<TodayMissionSummary> todayMissions = currentMissions.getOrDefault(row.getId(), List.of());
            TodayMissionSummary todayMission = todayMissions.isEmpty() ? null : todayMissions.getFirst();
            return new GroupSummaryResponse(row.getId(), row.getName(), row.getRelationType(),
                    members.size(), maxMembers, members, row.getStartDate(),
                    todayMission != null && todayMission.submittedByMe(), todayMission, todayMissions);
        }).toList();
        return PageResponse.of(content, safePage, safeSize, total);
    }

    @Transactional
    public GroupResponse createGroup(Long userId, GroupCreateRequest request) {
        validateName(request.name());
        String relationType = request.relationType() == null ? "CASUAL" : request.relationType();
        int dailyMissionCount = request.dailyMissionCount() == null ? 1 : request.dailyMissionCount();
        validateSettings(relationType, dailyMissionCount, request.missionSlots());
        validateStartDate(relationType, request.startDate());
        Group group = new Group();
        group.setName(request.name());
        group.setOwnerId(userId);
        group.setRelationType(relationType);
        group.setDailyMissionCount(dailyMissionCount);
        group.setStartDate(request.startDate());
        boolean inserted = false;
        for (int attempt = 0; attempt < 5; attempt++) {
            group.setInviteCode(InviteCodeGenerator.generate());
            try {
                groupMapper.insert(group);
                inserted = true;
                break;
            } catch (DuplicateKeyException e) {
                if (attempt == 4) throw e;
            }
        }
        if (!inserted) throw new IllegalStateException("Unable to create a unique invite code");

        GroupMember owner = new GroupMember();
        owner.setGroupId(group.getId());
        owner.setUserId(userId);
        owner.setRole(GroupMember.ROLE_OWNER);
        groupMemberMapper.insert(owner);

        if (request.missionSlots() != null) {
            insertSlots(group.getId(), request.missionSlots());
        }

        GroupResponse response = toGroupResponse(requireGroup(group.getId()));
        events.publishEvent(new GroupCreatedEvent(group.getId()));
        return response;
    }

    public GroupResponse getGroup(Long userId, Long groupId) {
        Group group = requireGroup(groupId);
        requireMember(groupId, userId);
        return toGroupResponse(group);
    }

    @Transactional
    public GroupResponse updateSettings(Long userId, Long groupId, GroupSettingsUpdateRequest request) {
        Group group = groupMapper.findByIdForUpdate(groupId);
        if (group == null) throw new BusinessException(GroupErrorCode.GROUP_NOT_FOUND);
        requireOwner(group, userId);

        String relationType = request.relationType() == null ? group.getRelationType() : request.relationType();
        int previousDailyMissionCount = group.getDailyMissionCount();
        int dailyMissionCount = request.dailyMissionCount() == null
                ? group.getDailyMissionCount() : request.dailyMissionCount();
        validateSettings(relationType, dailyMissionCount, request.missionSlots());
        if (request.name() != null) validateName(request.name());
        LocalDate startDate = request.startDate() == null ? group.getStartDate() : request.startDate();
        if (!"COUPLE".equals(relationType)) startDate = null;
        if (request.startDate() != null) validateStartDate(relationType, request.startDate());
        if (groupMemberMapper.countByGroupId(groupId) > ("COUPLE".equals(relationType) ? 2 : 6)) {
            throw new BusinessException(GroupErrorCode.GROUP_FULL);
        }

        if (request.name() != null) {
            group.setName(request.name());
        }
        group.setRelationType(relationType);
        group.setStartDate(startDate);
        group.setDailyMissionCount(dailyMissionCount);
        groupMapper.update(group);
        if (request.missionSlots() != null) {
            slotMapper.deleteByGroupId(groupId);
            insertSlots(groupId, request.missionSlots());
        } else if (dailyMissionCount < previousDailyMissionCount) {
            slotMapper.deleteByGroupIdAndSlotNoGreaterThan(groupId, dailyMissionCount);
        }
        return toGroupResponse(requireGroup(groupId));
    }

    public PageResponse<GroupMemberResponse> listMembers(Long userId, Long groupId, int page, int size) {
        requireGroup(groupId);
        requireMember(groupId, userId);

        int safePage = Math.max(page, 0);
        int safeSize = clampSize(size);
        List<GroupMemberRow> rows = groupMemberMapper.findMembersByGroupId(groupId, (long) safePage * safeSize, safeSize);
        long total = groupMemberMapper.countByGroupId(groupId);

        List<GroupMemberResponse> content = rows.stream()
                .map(row -> new GroupMemberResponse(row.getUserId(), row.getNickname(), row.getCharacterKey(),
                        row.getProfileImageUrl() == null ? null : storage.publicUrl(row.getProfileImageUrl()),
                        row.isGroupProfile(), row.getRole(),
                        row.getSubmissionCountThisMonth(), row.isStampedToday()))
                .toList();
        return PageResponse.of(content, safePage, safeSize, total);
    }

    @Transactional
    public InviteCodeResponse reissueInviteCode(Long userId, Long groupId) {
        Group group = groupMapper.findByIdForUpdate(groupId);
        if (group == null) throw new BusinessException(GroupErrorCode.GROUP_NOT_FOUND);
        requireMember(groupId, userId);
        requireOwner(group, userId);
        for (int attempt = 0; attempt < 5; attempt++) {
            String code = InviteCodeGenerator.generate();
            if (code.equals(group.getInviteCode())) continue;
            try {
                groupMapper.updateInviteCode(groupId, code);
                return new InviteCodeResponse(code, inviteBaseUrl + "/" + code);
            } catch (DuplicateKeyException e) {
                if (attempt == 4) throw e;
            }
        }
        throw new IllegalStateException("Unable to create a unique invite code");
    }

    public InvitePreviewResponse previewInvite(Long userId, String rawCode) {
        Group group = findInviteGroup(userId, rawCode, false);
        checkJoinable(group, userId);
        List<MemberProfileResponse> members = memberProfiles(group.getId());
        return new InvitePreviewResponse(group.getId(), group.getName(), group.getRelationType(),
                members.size(), group.maxMembers(), members);
    }

    @Transactional
    public JoinResponse join(Long userId, JoinRequest request) {
        Group group = findInviteGroup(userId, request.code(), true);
        Long groupId = group.getId();
        checkJoinable(group, userId);

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
    public void leaveGroup(Long userId, Long groupId, Long successorUserId) {
        Group group = groupMapper.findByIdForUpdate(groupId);
        if (group == null) throw new BusinessException(GroupErrorCode.GROUP_NOT_FOUND);
        GroupMember membership = groupMemberMapper.findByGroupIdAndUserId(groupId, userId);
        if (membership == null) {
            throw new BusinessException(GroupErrorCode.NOT_GROUP_MEMBER);
        }
        if (!GroupMember.ROLE_OWNER.equals(membership.getRole())) {
            groupMemberMapper.deleteByGroupIdAndUserId(groupId, userId);
            return;
        }
        if (groupMemberMapper.countByGroupId(groupId) == 1) {
            deleteGroupCascade(groupId);
            return;
        }
        if (successorUserId == null) throw new BusinessException(GroupErrorCode.SUCCESSOR_REQUIRED);
        if (successorUserId.equals(userId)
                || !groupMemberMapper.existsByGroupIdAndUserId(groupId, successorUserId)) {
            throw new BusinessException(GroupErrorCode.INVALID_SUCCESSOR);
        }
        group.setOwnerId(successorUserId);
        groupMapper.update(group);
        groupMemberMapper.updateRole(groupId, successorUserId, GroupMember.ROLE_OWNER);
        groupMemberMapper.deleteByGroupIdAndUserId(groupId, userId);
    }

    private Group findInviteGroup(Long userId, String rawCode, boolean lock) {
        inviteLimiter.check(userId);
        try {
            String code = InviteCodeGenerator.normalize(rawCode);
            if (code == null) throw new BusinessException(GroupErrorCode.INVITE_NOT_FOUND);
            Group group = lock ? groupMapper.findByInviteCodeForUpdate(code) : groupMapper.findByInviteCode(code);
            if (group == null) throw new BusinessException(GroupErrorCode.INVITE_NOT_FOUND);
            return group;
        } catch (BusinessException error) {
            if (error.errorCode() == GroupErrorCode.INVITE_NOT_FOUND) inviteLimiter.recordFailure(userId);
            throw error;
        }
    }

    private void checkJoinable(Group group, Long userId) {
        if (groupMemberMapper.existsByGroupIdAndUserId(group.getId(), userId)) {
            throw new BusinessException(GroupErrorCode.ALREADY_MEMBER);
        }
        if (groupMemberMapper.countByGroupId(group.getId()) >= group.maxMembers()) {
            throw new BusinessException(GroupErrorCode.GROUP_FULL);
        }
    }

    private List<MemberProfileResponse> memberProfiles(Long groupId) {
        return groupMapper.findMemberPreviewsByGroupIds(List.of(groupId)).stream()
                .map(this::toMemberProfile).toList();
    }

    private MemberProfileResponse toMemberProfile(GroupMemberPreviewRow row) {
        return new MemberProfileResponse(row.getUserId(), row.getNickname(), row.getCharacterKey(),
                row.getProfileImageUrl() == null ? null : storage.publicUrl(row.getProfileImageUrl()));
    }

    private void deleteGroupCascade(Long groupId) {
        groupMapper.deleteCommentMentionsByGroupId(groupId);
        groupMapper.deleteCommentsByGroupId(groupId);
        groupMapper.deleteReactionsByGroupId(groupId);
        groupMapper.deleteSubmissionGuessesByGroupId(groupId);
        groupMapper.deleteAiReviewsByGroupId(groupId);
        groupMapper.deleteSubmissionsByGroupId(groupId);
        groupMapper.deletePokesByGroupId(groupId);
        groupMapper.deleteMissionsByGroupId(groupId);
        slotMapper.deleteByGroupId(groupId);
        groupMemberMapper.deleteByGroupId(groupId);
        groupMapper.deleteById(groupId);
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
                group.getRelationType(), SeoulTime.toOffset(group.getCreatedAt()),
                (int) groupMemberMapper.countByGroupId(group.getId()), group.maxMembers(),
                group.getInviteCode(), inviteBaseUrl + "/" + group.getInviteCode(), group.getStartDate(),
                group.getDailyMissionCount(), slots, groupMapper.countMissionsByGroupId(group.getId()));
    }

    private void validateStartDate(String relationType, LocalDate startDate) {
        if (startDate != null && (!"COUPLE".equals(relationType) || startDate.isAfter(SeoulTime.today()))) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR);
        }
    }

    private void validateName(String name) {
        if (name == null || name.isEmpty() || name.length() > 16) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR);
        }
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
