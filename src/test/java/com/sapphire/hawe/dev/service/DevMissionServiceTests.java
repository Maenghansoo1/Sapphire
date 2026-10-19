package com.sapphire.hawe.dev.service;

import com.sapphire.hawe.dev.dao.DevMapper;
import com.sapphire.hawe.dev.dto.DevDtos.RerollRequest;
import com.sapphire.hawe.global.exception.BusinessException;
import com.sapphire.hawe.global.exception.CommonErrorCode;
import com.sapphire.hawe.group.dao.GroupMapper;
import com.sapphire.hawe.group.service.GroupService;
import com.sapphire.hawe.insight.dao.InsightMapper;
import com.sapphire.hawe.insight.service.InsightGenerator;
import com.sapphire.hawe.mission.dao.MissionMapper;
import com.sapphire.hawe.mission.dao.MissionRow;
import com.sapphire.hawe.mission.domain.MissionTemplate;
import com.sapphire.hawe.mission.service.MissionPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DevMissionServiceTests {
    @Mock DevMapper dev;
    @Mock MissionMapper missions;
    @Mock GroupMapper groups;
    @Mock MissionPublisher publisher;
    @Mock GroupService groupService;
    @Mock InsightMapper insights;
    @Mock InsightGenerator generator;

    private DevMissionService service() {
        return new DevMissionService(dev, missions, groups, publisher, groupService, insights, generator);
    }

    @Test void invalidTemplateLeavesMissionDataUntouched() {
        BusinessException error = assertThrows(BusinessException.class,
                () -> service().reroll(7L, new RerollRequest("INVALID")));
        assertEquals(CommonErrorCode.VALIDATION_ERROR, error.errorCode());
        verifyNoInteractions(dev, missions, publisher);
    }

    @Test void rerollDeletesChildrenBeforeRegeneration() {
        MissionRow row = new MissionRow();
        row.setId(7L); row.setGroupId(4L); row.setSlotNo(1);
        row.setTemplate("PHOTO_NOW"); row.setContent("new");
        when(missions.findById(7L, 0L)).thenReturn(row);
        when(missions.isCurrent(7L)).thenReturn(true);
        when(dev.findMission(7L)).thenReturn(row);
        var result = service().reroll(7L, new RerollRequest("PHOTO_NOW"));
        assertEquals(7L, result.missionId());
        assertEquals("new", result.content());
        InOrder order = inOrder(dev, publisher);
        order.verify(dev).deleteCommentMentionsByMissionId(7L);
        order.verify(dev).deleteCommentsByMissionId(7L);
        order.verify(dev).deleteReactionsByMissionId(7L);
        order.verify(dev).deleteSubmissionGuessesByMissionId(7L);
        order.verify(dev).deleteAiReviewsByMissionId(7L);
        order.verify(dev).deleteSubmissionsByMissionId(7L);
        order.verify(dev).deletePokesByMissionId(7L);
        order.verify(publisher).reroll(eq(7L), any(), eq(MissionTemplate.PHOTO_NOW));
        order.verify(dev).findMission(7L);
    }
}
