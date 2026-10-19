package com.sapphire.hawe.mission.service;

import com.sapphire.hawe.global.config.MissionProperties;
import com.sapphire.hawe.global.exception.BusinessException;
import com.sapphire.hawe.group.dao.GroupMapper;
import com.sapphire.hawe.group.dao.GroupMemberMapper;
import com.sapphire.hawe.group.dao.GroupMissionSlotMapper;
import com.sapphire.hawe.group.domain.Group;
import com.sapphire.hawe.group.domain.GroupMissionSlot;
import com.sapphire.hawe.mission.dao.MissionInsertRow;
import com.sapphire.hawe.mission.dao.MissionMapper;
import com.sapphire.hawe.mission.dao.MissionRow;
import com.sapphire.hawe.mission.dao.RecentMissionRow;
import com.sapphire.hawe.mission.domain.GeneratedMission;
import com.sapphire.hawe.mission.domain.GenerationContext;
import com.sapphire.hawe.mission.domain.MissionTemplate;
import com.sapphire.hawe.mission.domain.RecentMission;
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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class MissionPublisher {

    /** 문장이 겹치지 않게 피할 그 전 날까지의 최근 미션 수 (같은 날 먼저 열린 슬롯은 따로 더한다). */
    static final int RECENT_CONTENTS = 5;

    private final MissionMapper missions;
    private final GroupMapper groups;
    private final GroupMemberMapper members;
    private final GroupMissionSlotMapper slots;
    private final MissionProperties properties;
    private final MissionGenerator generator;
    private final GroupContextBuilder contexts;
    private final ExperimentAssigner experiments;
    private final ObjectMapper objectMapper;

    public MissionPublisher(MissionMapper missions, GroupMapper groups, GroupMemberMapper members,
            GroupMissionSlotMapper slots, MissionProperties properties, MissionGenerator generator,
            GroupContextBuilder contexts, ExperimentAssigner experiments, ObjectMapper objectMapper) {
        this.missions = missions;
        this.groups = groups;
        this.members = members;
        this.slots = slots;
        this.properties = properties;
        this.generator = generator;
        this.contexts = contexts;
        this.experiments = experiments;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void publishIfDue(Long groupId, LocalDateTime now) {
        publish(groupId, now, false, null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<Long> forcePublish(Long groupId, LocalDateTime now, MissionTemplate template) {
        return publish(groupId, now, true, template);
    }

    /** Joins the dev service transaction so its submission cleanup and this update commit together. */
    @Transactional
    public void reroll(Long missionId, LocalDateTime now, MissionTemplate requested) {
        MissionRow current = missions.findById(missionId, 0L);
        if (current == null || !missions.isCurrent(missionId))
            throw new BusinessException(MissionErrorCode.MISSION_NOT_FOUND);
        Group group = groups.findById(current.getGroupId());
        if (group == null) throw new BusinessException(MissionErrorCode.MISSION_NOT_FOUND);
        List<MissionTemplate> pool = properties.enabledTemplates().stream()
                .filter(template -> !template.name().equals(current.getTemplate())).toList();
        if (pool.isEmpty()) pool = properties.enabledTemplates();
        MissionTemplate template = requested == null ? pool.get(ThreadLocalRandom.current().nextInt(pool.size())) : requested;
        long seed = MissionTemplateSelector.seed(current.getGroupId(), current.getMissionDate(), current.getSlotNo())
                ^ ThreadLocalRandom.current().nextLong();
        boolean withTarget = MissionTemplateSelector.needsTarget(template, seed);
        GenerationContext context = contexts.build(group, current.getMissionDate()).withExperimentArm(
                experiments.assign(current.getGroupId(), current.getMissionDate()).orElse(null));
        List<RecentMission> recent = missions.findRecentByGroupId(current.getGroupId(),
                RECENT_CONTENTS + group.getDailyMissionCount() - 1).stream().map(RecentMissionRow::toDomain).toList();
        GeneratedMission generated = generator.generate(template, seed, context.withRecentMissions(recent), withTarget);
        Map<String, Object> params = new HashMap<>(generated.params());
        if (withTarget) {
            List<Long> userIds = members.findUserIdsByGroupId(current.getGroupId());
            if (userIds.isEmpty()) throw new IllegalStateException("Group has no members");
            params.put("targetUserId", userIds.get(new java.util.Random(seed ^ 0xBB67AE8584CAA73BL)
                    .nextInt(userIds.size())));
        }
        if (missions.updateGenerated(missionId, generated.content(), generated.category(), template.name(),
                objectMapper.writeValueAsString(params), generated.source().name(),
                context.experimentArm() == null ? null : context.experimentArm().name()) == 0)
            throw new BusinessException(MissionErrorCode.MISSION_NOT_FOUND);
    }

    private List<Long> publish(Long groupId, LocalDateTime now, boolean forced, MissionTemplate requestedTemplate) {
        Group group = groups.findById(groupId);
        if (group == null) return List.of();
        List<Long> created = new ArrayList<>();
        LocalDate date = now.toLocalDate();
        Map<Integer, GroupMissionSlot> configured = new HashMap<>();
        for (GroupMissionSlot slot : slots.findByGroupId(groupId)) configured.put(slot.getSlotNo(), slot);
        Set<String> yesterdayMedia = new HashSet<>();
        for (String code : missions.findTemplatesByGroupIdAndDate(groupId, date.minusDays(1))) {
            try { yesterdayMedia.add(MissionTemplate.valueOf(code).media()); }
            catch (IllegalArgumentException | NullPointerException ignored) { }
        }
        GenerationContext groupContext = null;  // 실제로 발행할 슬롯이 있을 때만 만든다
        for (int slotNo = 1; slotNo <= group.getDailyMissionCount(); slotNo++) {
            GroupMissionSlot slot = configured.get(slotNo);
            LocalTime publishTime = slot != null && slot.getPublishTime() != null
                    ? slot.getPublishTime() : properties.defaultPublishTime();
            LocalTime deadlineTime = slot != null && slot.getDeadlineTime() != null
                    ? slot.getDeadlineTime() : properties.defaultDeadlineTime();
            LocalDateTime scheduled = MissionTiming.scheduledPublishAt(date, publishTime);
            if ((!forced && now.isBefore(scheduled)) || missions.existsByGroupIdAndDateAndSlot(groupId, date, slotNo)) continue;

            LocalDateTime publishedAt = forced ? now :
                    (scheduled.isBefore(group.getCreatedAt()) ? group.getCreatedAt() : scheduled);
            long seed = MissionTemplateSelector.seed(groupId, date, slotNo);
            MissionTemplate template = requestedTemplate == null ? MissionTemplateSelector.select(groupId, date, slotNo,
                    group.getDailyMissionCount(), yesterdayMedia, properties.enabledTemplates()) : requestedTemplate;
            boolean withTarget = MissionTemplateSelector.needsTarget(template, seed);
            // 실험 군은 (그룹, 날짜) 단위라 같은 날 슬롯은 모두 같은 군이다. 위의 틀·대상 선택은 군과 무관하다
            if (groupContext == null)
                groupContext = contexts.build(group, date).withExperimentArm(experiments.assign(groupId, date).orElse(null));
            // 슬롯 순서대로 같은 트랜잭션에서 저장하므로 앞 슬롯 문장도 여기에 보인다
            List<RecentMission> recent = missions.findRecentByGroupId(groupId,
                    RECENT_CONTENTS + group.getDailyMissionCount() - 1).stream().map(RecentMissionRow::toDomain).toList();
            // AI 를 쓰면 여기서 외부 호출을 기다린다(발행 트랜잭션 안) — 대기 시간은 hawe.mission.ai.total-timeout 으로 제한
            GeneratedMission generated = generator.generate(template, seed, groupContext.withRecentMissions(recent),
                    withTarget);
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
            mission.setGenerationSource(generated.source().name());
            mission.setExperimentArm(groupContext.experimentArm() == null ? null : groupContext.experimentArm().name());
            try { missions.insert(mission); if (mission.getId() != null) created.add(mission.getId()); }
            catch (DuplicateKeyException ignored) { /* Another publisher already committed this slot. */ }
        }
        return created;
    }
}
