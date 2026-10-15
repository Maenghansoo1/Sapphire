package com.sapphire.hawe.dev.service;

import com.sapphire.hawe.dev.dao.DevMapper;
import com.sapphire.hawe.dev.dto.DevDtos.DeadlineResponse;
import com.sapphire.hawe.dev.dto.DevDtos.PublishRequest;
import com.sapphire.hawe.dev.dto.DevDtos.PublishResponse;
import com.sapphire.hawe.dev.dto.DevDtos.PublishedMission;
import com.sapphire.hawe.dev.dto.DevDtos.RegenerateResponse;
import com.sapphire.hawe.dev.dto.DevDtos.UpdatedResponse;
import com.sapphire.hawe.global.exception.BusinessException;
import com.sapphire.hawe.global.exception.CommonErrorCode;
import com.sapphire.hawe.global.time.SeoulTime;
import com.sapphire.hawe.group.dao.GroupMapper;
import com.sapphire.hawe.group.dto.GroupResponse;
import com.sapphire.hawe.group.dto.GroupSettingsUpdateRequest;
import com.sapphire.hawe.group.service.GroupErrorCode;
import com.sapphire.hawe.group.service.GroupService;
import com.sapphire.hawe.insight.dao.InsightMapper;
import com.sapphire.hawe.insight.service.InsightGenerator;
import com.sapphire.hawe.mission.dao.MissionRow;
import com.sapphire.hawe.mission.domain.MissionTemplate;
import com.sapphire.hawe.mission.service.MissionErrorCode;
import com.sapphire.hawe.mission.service.MissionPublisher;
import java.time.YearMonth;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class DevMissionService {
    private final DevMapper dev;
    private final GroupMapper groups;
    private final MissionPublisher publisher;
    private final GroupService groupService;
    private final InsightMapper insights;
    private final InsightGenerator generator;

    public DevMissionService(DevMapper dev, GroupMapper groups, MissionPublisher publisher,
            GroupService groupService, InsightMapper insights, InsightGenerator generator) {
        this.dev = dev; this.groups = groups; this.publisher = publisher;
        this.groupService = groupService; this.insights = insights; this.generator = generator;
    }

    public PublishResponse publish(PublishRequest request) {
        MissionTemplate template = null;
        if (request != null && request.template() != null) try {
            template = MissionTemplate.valueOf(request.template());
        } catch (IllegalArgumentException error) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR);
        }
        Long requestedGroup = request == null ? null : request.groupId();
        if (requestedGroup != null && groups.findById(requestedGroup) == null)
            throw new BusinessException(GroupErrorCode.GROUP_NOT_FOUND);
        List<Long> groupIds = requestedGroup == null ? groups.findAllIds() : List.of(requestedGroup);
        List<PublishedMission> created = new ArrayList<>();
        for (Long groupId : groupIds) for (Long id : publisher.forcePublish(groupId, SeoulTime.now(), template)) {
            MissionRow row = dev.findMission(id);
            if (row != null) created.add(new PublishedMission(row.getId(), row.getGroupId(), row.getSlotNo(),
                    row.getTemplate(), row.getContent()));
        }
        return new PublishResponse(created, created.isEmpty() ? "오늘 발행할 빈 슬롯이 없어요" : null);
    }

    public DeadlineResponse deadlineNow(Long missionId) {
        LocalDateTime now = SeoulTime.now();
        if (dev.deadlineNow(missionId, now) == 0)
            throw new BusinessException(MissionErrorCode.MISSION_NOT_FOUND);
        return new DeadlineResponse(missionId, SeoulTime.toOffset(now));
    }

    public UpdatedResponse deadlineCurrent(Long groupId) {
        if (groups.findById(groupId) == null) throw new BusinessException(GroupErrorCode.GROUP_NOT_FOUND);
        return new UpdatedResponse(dev.deadlineCurrent(groupId, SeoulTime.now()));
    }

    @Transactional
    public RegenerateResponse regenerate(Long groupId, String monthText) {
        if (groups.findById(groupId) == null) throw new BusinessException(GroupErrorCode.GROUP_NOT_FOUND);
        YearMonth month;
        try { month = YearMonth.parse(monthText); }
        catch (RuntimeException error) { throw new BusinessException(CommonErrorCode.VALIDATION_ERROR); }
        dev.deleteInsight(groupId, month.toString());
        insights.tryStart(groupId, month.toString());
        if (TransactionSynchronizationManager.isSynchronizationActive())
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { generator.generate(groupId, month); }
            });
        else generator.generate(groupId, month);
        return new RegenerateResponse("REQUESTED", month.toString());
    }

    public GroupResponse settings(Long groupId, GroupSettingsUpdateRequest request) {
        var group = groups.findById(groupId);
        if (group == null) throw new BusinessException(GroupErrorCode.GROUP_NOT_FOUND);
        return groupService.updateSettings(group.getOwnerId(), groupId, request);
    }
}
