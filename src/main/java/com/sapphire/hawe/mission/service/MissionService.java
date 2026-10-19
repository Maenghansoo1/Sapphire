package com.sapphire.hawe.mission.service;

import com.sapphire.hawe.global.text.KoreanJosa;
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
import com.sapphire.hawe.mission.dao.MissionSummaryRow;
import com.sapphire.hawe.mission.dao.MissionRow;
import com.sapphire.hawe.mission.domain.MissionTemplate;
import com.sapphire.hawe.mission.domain.MissionTiming;
import com.sapphire.hawe.mission.dto.MissionMemberStatus;
import com.sapphire.hawe.mission.dto.MissionResponse;
import com.sapphire.hawe.mission.dto.MissionTemplateListResponse;
import com.sapphire.hawe.mission.dto.MissionTemplateResponse;
import com.sapphire.hawe.mission.dto.TodayMissionsResponse;
import com.sapphire.hawe.mission.dto.TodayMissionSummary;
import com.sapphire.hawe.poke.dao.PokeMapper;
import com.sapphire.hawe.poke.dao.PokedRow;
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
    private final PokeMapper pokeMapper;

    public MissionService(MissionMapper missionMapper, GroupMapper groupMapper,
            GroupMemberMapper groupMemberMapper, GroupMissionSlotMapper slotMapper, UserMapper userMapper,
            MissionProperties missionProperties, ObjectMapper objectMapper, PokeMapper pokeMapper) {
        this.missionMapper = missionMapper;
        this.groupMapper = groupMapper;
        this.groupMemberMapper = groupMemberMapper;
        this.slotMapper = slotMapper;
        this.userMapper = userMapper;
        this.missionProperties = missionProperties;
        this.objectMapper = objectMapper;
        this.pokeMapper = pokeMapper;
    }

    public TodayMissionsResponse getTodayMissions(Long userId, Long groupId) {
        Group group = groupMapper.findById(groupId);
        if (group == null) {
            throw new BusinessException(GroupErrorCode.GROUP_NOT_FOUND);
        }
        requireMember(groupId, userId);
        List<MissionRow> rows = missionMapper.findCurrentByGroupId(groupId, userId);
        Map<Integer, LocalTime> publishTimes = publishTimes(groupId);
        List<MissionResponse> missions = rows.stream().map(row -> toResponse(row, publishTimes, userId)).toList();
        LocalDate date = rows.isEmpty() ? SeoulTime.today() : rows.getFirst().getMissionDate();
        LocalDateTime nextPublishAt = nextPublishAt(group.getDailyMissionCount(), rows, publishTimes);
        return new TodayMissionsResponse(date, group.getDailyMissionCount(), SeoulTime.toOffset(nextPublishAt), missions);
    }

    public MissionResponse getMission(Long userId, Long missionId) {
        MissionRow row = missionMapper.findById(missionId, userId);
        if (row == null) {
            throw new BusinessException(MissionErrorCode.MISSION_NOT_FOUND);
        }
        requireMember(row.getGroupId(), userId);
        return toResponse(row, publishTimes(row.getGroupId()), userId);
    }

    public MissionTemplateListResponse listTemplates() {
        List<MissionTemplateResponse> content = Arrays.stream(MissionTemplate.values())
                .map(template -> new MissionTemplateResponse(template.name(), template.media(),
                        template.displayName(), template.phase(), template.paramsSchema(),
                        template.payloadSchema()))
                .toList();
        return new MissionTemplateListResponse(content);
    }

    /** 그룹별 현재 미션 요약, 슬롯 순서. 현재 미션이 없는 그룹은 결과에 들어가지 않는다. */
    public Map<Long, List<TodayMissionSummary>> summarizeCurrent(List<Long> groupIds, Long userId) {
        if (groupIds.isEmpty()) return Map.of();
        Map<Long, List<TodayMissionSummary>> summaries = new HashMap<>();
        for (MissionSummaryRow row : missionMapper.findCurrentSummariesByGroupIds(groupIds, userId)) {
            summaries.computeIfAbsent(row.getGroupId(), ignored -> new ArrayList<>()).add(new TodayMissionSummary(
                    row.getMissionId(), media(row.getTemplate()),
                    displayContent(row.getContent(), resolveParams(row.getParams())),
                    row.getSubmittedCount(), row.isSubmittedByMe()));
        }
        return summaries;
    }

    private void requireMember(Long groupId, Long userId) {
        if (!groupMemberMapper.existsByGroupIdAndUserId(groupId, userId)) {
            throw new BusinessException(GroupErrorCode.NOT_GROUP_MEMBER);
        }
    }

    private String media(String templateCode) {
        try {
            return MissionTemplate.valueOf(templateCode).media();
        } catch (IllegalArgumentException | NullPointerException ignored) {
            // Future template codes can still be read before the catalog is expanded.
            return null;
        }
    }

    private MissionResponse toResponse(MissionRow row, Map<Integer, LocalTime> publishTimes, Long viewerId) {
        Map<String, Object> params = resolveParams(row.getParams());
        LocalTime publishTime = publishTimes.getOrDefault(row.getSlotNo(), missionProperties.defaultPublishTime());
        Map<Long, Set<String>> sent = new HashMap<>();
        for (PokedRow poke : pokeMapper.findSentTypes(row.getId(), viewerId)) {
            sent.computeIfAbsent(poke.getToUserId(), ignored -> new HashSet<>()).add(poke.getType());
        }
        List<MissionMemberStatus> members = missionMapper.findMemberStatuses(row.getId()).stream()
                .map(member -> {
                    Set<String> types = member.getUserId().equals(viewerId)
                            ? Set.of() : sent.getOrDefault(member.getUserId(), Set.of());
                    List<String> pokedByMe = List.of("poke").stream()
                            .filter(types::contains).toList();
                    return new MissionMemberStatus(member.getUserId(), member.isSubmitted(), pokedByMe);
                }).toList();
        return new MissionResponse(row.getId(), row.getGroupId(), row.getSlotNo(), row.getNumber(),
                displayContent(row.getContent(), params), row.getCategory(), row.getTemplate(),
                media(row.getTemplate()), params, SeoulTime.toOffset(row.getPublishedAt()),
                SeoulTime.toOffset(row.getDeadlineAt()),
                SeoulTime.toOffset(MissionTiming.nextPublishAfter(row.getPublishedAt(), publishTime)),
                row.isSubmittedByMe(), members);
    }

    private String displayContent(String content, Map<String, Object> params) {
        if (content == null || !content.contains("[대상]")) return content;
        Object target = params.get("target");
        String nickname = target instanceof Map<?, ?> map && map.get("nickname") instanceof String name
                ? name : "친구";
        return KoreanJosa.replaceTarget(content, nickname);
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

    private Map<Integer, LocalTime> publishTimes(Long groupId) {
        Map<Integer, LocalTime> times = new HashMap<>();
        for (GroupMissionSlot slot : slotMapper.findByGroupId(groupId)) {
            if (slot.getPublishTime() != null) times.put(slot.getSlotNo(), slot.getPublishTime());
        }
        return times;
    }

    private LocalDateTime nextPublishAt(int dailyMissionCount,
            List<MissionRow> missions, Map<Integer, LocalTime> customPublishTimes) {
        LocalDateTime next = null;
        LocalDateTime now = SeoulTime.now();
        for (int slotNo = 1; slotNo <= dailyMissionCount; slotNo++) {
            int currentSlot = slotNo;
            LocalTime time = customPublishTimes.getOrDefault(slotNo, missionProperties.defaultPublishTime());
            MissionRow current = missions.stream().filter(row -> row.getSlotNo() == currentSlot).findFirst().orElse(null);
            LocalDateTime candidate = current == null
                    ? MissionTiming.nextPublishAfter(now, time)
                    : MissionTiming.nextPublishAfter(current.getPublishedAt(), time);
            if (next == null || candidate.isBefore(next)) next = candidate;
        }
        return next;
    }
}
