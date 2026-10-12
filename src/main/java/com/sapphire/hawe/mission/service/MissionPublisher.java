package com.sapphire.hawe.mission.service;

import com.sapphire.hawe.global.config.MissionProperties;
import com.sapphire.hawe.group.dao.GroupMapper;
import com.sapphire.hawe.group.dao.GroupMemberMapper;
import com.sapphire.hawe.group.dao.GroupMissionSlotMapper;
import com.sapphire.hawe.group.domain.Group;
import com.sapphire.hawe.group.domain.GroupMissionSlot;
import com.sapphire.hawe.mission.dao.MissionInsertRow;
import com.sapphire.hawe.mission.dao.MissionMapper;
import com.sapphire.hawe.mission.domain.GeneratedMission;
import com.sapphire.hawe.mission.domain.MissionTemplate;
import com.sapphire.hawe.mission.domain.MissionTemplateSelector;
import com.sapphire.hawe.mission.domain.MissionTiming;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class MissionPublisher {

    private final MissionMapper missions;
    private final GroupMapper groups;
    private final GroupMemberMapper members;
    private final GroupMissionSlotMapper slots;
    private final MissionProperties properties;
    private final MissionGenerator generator;
    private final ObjectMapper objectMapper;

    public MissionPublisher(MissionMapper missions, GroupMapper groups, GroupMemberMapper members,
            GroupMissionSlotMapper slots, MissionProperties properties, MissionGenerator generator,
            ObjectMapper objectMapper) {
        this.missions = missions;
        this.groups = groups;
        this.members = members;
        this.slots = slots;
        this.properties = properties;
        this.generator = generator;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void publishIfDue(Long groupId, LocalDateTime now) {
        Group group = groups.findById(groupId);
        if (group == null) return;
        LocalDate date = now.toLocalDate();
        Map<Integer, GroupMissionSlot> configured = new HashMap<>();
        for (GroupMissionSlot slot : slots.findByGroupId(groupId)) configured.put(slot.getSlotNo(), slot);
        Set<String> yesterdayMedia = new HashSet<>();
        for (String code : missions.findTemplatesByGroupIdAndDate(groupId, date.minusDays(1))) {
            try { yesterdayMedia.add(MissionTemplate.valueOf(code).media()); }
            catch (IllegalArgumentException | NullPointerException ignored) { }
        }
        for (int slotNo = 1; slotNo <= group.getDailyMissionCount(); slotNo++) {
            GroupMissionSlot slot = configured.get(slotNo);
            LocalTime publishTime = slot != null && slot.getPublishTime() != null
                    ? slot.getPublishTime() : properties.defaultPublishTime();
            LocalTime deadlineTime = slot != null && slot.getDeadlineTime() != null
                    ? slot.getDeadlineTime() : properties.defaultDeadlineTime();
            LocalDateTime scheduled = MissionTiming.scheduledPublishAt(date, publishTime);
            if (now.isBefore(scheduled) || missions.existsByGroupIdAndDateAndSlot(groupId, date, slotNo)) continue;

            LocalDateTime publishedAt = scheduled.isBefore(group.getCreatedAt()) ? group.getCreatedAt() : scheduled;
            long seed = MissionTemplateSelector.seed(groupId, date, slotNo);
            MissionTemplate template = MissionTemplateSelector.select(groupId, date, slotNo,
                    group.getDailyMissionCount(), yesterdayMedia);
            boolean withTarget = MissionTemplateSelector.needsTarget(template, seed);
            GeneratedMission generated = generator.generate(template, seed,
                    missions.findLatestContentByGroupId(groupId), withTarget);
            Map<String, Object> params = new HashMap<>(generated.params());
            if (withTarget) {
                List<Long> userIds = members.findUserIdsByGroupId(groupId);
                if (userIds.isEmpty()) throw new IllegalStateException("Group has no members");
                params.put("targetUserId", userIds.get(new java.util.Random(seed ^ 0xBB67AE8584CAA73BL)
                        .nextInt(userIds.size())));
            }
            MissionInsertRow mission = new MissionInsertRow();
            mission.setGroupId(groupId);
            mission.setContent(generated.content());
            mission.setCategory(generated.category());
            mission.setMissionDate(date);
            mission.setTemplate(template.name());
            mission.setParams(objectMapper.writeValueAsString(params));
            mission.setSlotNo(slotNo);
            mission.setPublishedAt(publishedAt);
            mission.setDeadlineAt(MissionTiming.deadlineAfter(publishedAt, deadlineTime));
            try { missions.insert(mission); }
            catch (DuplicateKeyException ignored) { /* Another publisher already committed this slot. */ }
        }
    }
}
