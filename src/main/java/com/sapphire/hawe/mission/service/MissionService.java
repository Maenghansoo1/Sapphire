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
import com.sapphire.hawe.mission.domain.MissionTemplate;
import com.sapphire.hawe.mission.dto.MissionResponse;
import com.sapphire.hawe.mission.dto.MissionTemplateListResponse;
import com.sapphire.hawe.mission.dto.MissionTemplateResponse;
import com.sapphire.hawe.mission.dto.TodayMissionsResponse;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@EnableConfigurationProperties(MissionProperties.class)
public class MissionService {

    private final MissionMapper missionMapper;
    private final GroupMapper groupMapper;
    private final GroupMemberMapper groupMemberMapper;
    private final GroupMissionSlotMapper slotMapper;
    private final UserMapper userMapper;
    private final MissionProperties missionProperties;
    private final ObjectMapper objectMapper;

    public MissionService(MissionMapper missionMapper, GroupMapper groupMapper,
            GroupMemberMapper groupMemberMapper, GroupMissionSlotMapper slotMapper, UserMapper userMapper,
            MissionProperties missionProperties, ObjectMapper objectMapper) {
        this.missionMapper = missionMapper;
        this.groupMapper = groupMapper;
        this.groupMemberMapper = groupMemberMapper;
        this.slotMapper = slotMapper;
        this.userMapper = userMapper;
        this.missionProperties = missionProperties;
        this.objectMapper = objectMapper;
    }

    public TodayMissionsResponse getTodayMissions(Long userId, Long groupId) {
        Group group = groupMapper.findById(groupId);
        if (group == null) {
            throw new BusinessException(GroupErrorCode.GROUP_NOT_FOUND);
        }
        requireMember(groupId, userId);
        List<MissionRow> rows = missionMapper.findTodayByGroupId(groupId, userId);
        List<MissionResponse> missions = rows.stream().map(this::toResponse).toList();
        LocalDate date = SeoulTime.today();
        LocalDateTime nextPublishAt = nextPublishAt(date, group.getDailyMissionCount(), rows,
                slotMapper.findByGroupId(groupId));
        return new TodayMissionsResponse(date, group.getDailyMissionCount(), SeoulTime.toOffset(nextPublishAt), missions);
    }

    public MissionResponse getMission(Long userId, Long missionId) {
        MissionRow row = missionMapper.findById(missionId, userId);
        if (row == null) {
            throw new BusinessException(MissionErrorCode.MISSION_NOT_FOUND);
        }
        requireMember(row.getGroupId(), userId);
        return toResponse(row);
    }

    public MissionTemplateListResponse listTemplates() {
        List<MissionTemplateResponse> content = Arrays.stream(MissionTemplate.values())
                .map(template -> new MissionTemplateResponse(template.name(), template.media(),
                        template.displayName(), template.phase(), template.paramsSchema(),
                        template.payloadSchema()))
                .toList();
        return new MissionTemplateListResponse(content);
    }

    private void requireMember(Long groupId, Long userId) {
        if (!groupMemberMapper.existsByGroupIdAndUserId(groupId, userId)) {
            throw new BusinessException(GroupErrorCode.NOT_GROUP_MEMBER);
        }
    }

    private MissionResponse toResponse(MissionRow row) {
        String media = null;
        try {
            media = MissionTemplate.valueOf(row.getTemplate()).media();
        } catch (IllegalArgumentException | NullPointerException ignored) {
            // Future template codes can still be read before the catalog is expanded.
        }
        return new MissionResponse(row.getId(), row.getGroupId(), row.getSlotNo(), row.getContent(),
                row.getCategory(), row.getTemplate(), media, resolveParams(row.getParams()),
                SeoulTime.toOffset(row.getPublishedAt()), SeoulTime.toOffset(row.getDeadlineAt()), row.isSubmittedByMe());
    }

    private Map<String, Object> resolveParams(String json) {
        Map<String, Object> params = new LinkedHashMap<>();
        if (json != null) {
            try {
                Map<?, ?> parsed = objectMapper.readValue(json, Map.class);
                if (parsed != null) {
                    parsed.forEach((key, value) -> {
                        if (key instanceof String name) {
                            params.put(name, value);
                        }
                    });
                }
            } catch (Exception ignored) {
                return params;
            }
        }
        Object targetValue = params.remove("targetUserId");
        Long targetId = targetUserId(targetValue);
        if (targetId != null) {
            User target = userMapper.findById(targetId);
            if (target != null) {
                params.put("target", Map.of("userId", targetId, "nickname", target.getNickname()));
            }
        }
        return params;
    }

    private Long targetUserId(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String string) {
            try {
                return Long.valueOf(string);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private LocalDateTime nextPublishAt(LocalDate date, int dailyMissionCount,
            List<MissionRow> missions, List<GroupMissionSlot> slots) {
        Set<Integer> published = new HashSet<>();
        for (MissionRow mission : missions) {
            published.add(mission.getSlotNo());
        }
        Map<Integer, LocalTime> customPublishTimes = new HashMap<>();
        for (GroupMissionSlot slot : slots) {
            if (slot.getPublishTime() != null) {
                customPublishTimes.put(slot.getSlotNo(), slot.getPublishTime());
            }
        }
        List<LocalDateTime> unpublishedTimes = new ArrayList<>();
        for (int slotNo = 1; slotNo <= dailyMissionCount; slotNo++) {
            if (!published.contains(slotNo)) {
                unpublishedTimes.add(date.atTime(customPublishTimes.getOrDefault(slotNo,
                        missionProperties.defaultPublishTime())));
            }
        }
        return unpublishedTimes.stream().min(LocalDateTime::compareTo).orElse(null);
    }
}
