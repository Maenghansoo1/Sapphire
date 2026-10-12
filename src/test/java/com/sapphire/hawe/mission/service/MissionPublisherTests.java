package com.sapphire.hawe.mission.service;

import com.sapphire.hawe.global.config.MissionProperties;
import com.sapphire.hawe.group.dao.GroupMapper;
import com.sapphire.hawe.group.dao.GroupMemberMapper;
import com.sapphire.hawe.group.dao.GroupMissionSlotMapper;
import com.sapphire.hawe.group.domain.Group;
import com.sapphire.hawe.mission.dao.MissionInsertRow;
import com.sapphire.hawe.mission.dao.MissionMapper;
import com.sapphire.hawe.mission.domain.GeneratedMission;
import com.sapphire.hawe.mission.domain.MissionTemplate;
import com.sapphire.hawe.mission.domain.MissionTemplateSelector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MissionPublisherTests {
    @Mock MissionMapper missions;
    @Mock GroupMapper groups;
    @Mock GroupMemberMapper members;
    @Mock GroupMissionSlotMapper slots;
    @Mock MissionGenerator generator;
    MissionPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new MissionPublisher(missions, groups, members, slots,
                new MissionProperties(LocalTime.of(9, 0), LocalTime.of(23, 0), 1), generator,
                new ObjectMapper());
        lenient().when(members.findUserIdsByGroupId(11L)).thenReturn(List.of(7L));
    }

    @Test
    void publishesScheduledMissionOnceAndCatchesUpToday() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 12, 11, 0);
        when(groups.findById(11L)).thenReturn(group(now.minusDays(2)));
        when(generator.generate(any(), anyLong(), isNull(), anyBoolean()))
                .thenReturn(new GeneratedMission("A", "일상", Map.of("subject", "A")));

        publisher.publishIfDue(11L, now);

        ArgumentCaptor<MissionInsertRow> inserted = ArgumentCaptor.forClass(MissionInsertRow.class);
        verify(missions).insert(inserted.capture());
        MissionInsertRow row = inserted.getValue();
        assertEquals(11L, row.getGroupId());
        assertEquals(now.toLocalDate(), row.getMissionDate());
        assertEquals(1, row.getSlotNo());
        assertEquals(now.toLocalDate().atTime(9, 0), row.getPublishedAt());
        assertEquals(now.toLocalDate().atTime(23, 0), row.getDeadlineAt());
        assertTrue(MissionTemplateSelector.MVP_POOL.contains(MissionTemplate.valueOf(row.getTemplate())));

        when(missions.existsByGroupIdAndDateAndSlot(11L, now.toLocalDate(), 1)).thenReturn(true);
        publisher.publishIfDue(11L, now);
        verify(missions, times(1)).insert(any());
    }

    @Test
    void waitsUntilPublishTimeAndUsesCreationTimeForLateCreatedGroup() {
        LocalDateTime before = LocalDateTime.of(2026, 10, 12, 8, 59);
        when(groups.findById(11L)).thenReturn(group(before));
        publisher.publishIfDue(11L, before);
        verify(missions, never()).insert(any());

        LocalDateTime created = LocalDateTime.of(2026, 10, 12, 14, 0);
        when(groups.findById(11L)).thenReturn(group(created));
        when(generator.generate(any(), anyLong(), isNull(), anyBoolean()))
                .thenReturn(new GeneratedMission("A", "일상", Map.of()));
        publisher.publishIfDue(11L, created);
        ArgumentCaptor<MissionInsertRow> inserted = ArgumentCaptor.forClass(MissionInsertRow.class);
        verify(missions).insert(inserted.capture());
        assertEquals(created, inserted.getValue().getPublishedAt());
    }

    @Test
    void duplicateInsertAndMissingGroupAreHarmless() {
        publisher.publishIfDue(99L, LocalDateTime.of(2026, 10, 12, 12, 0));
        verify(missions, never()).insert(any());
        when(groups.findById(11L)).thenReturn(group(LocalDateTime.of(2026, 10, 11, 12, 0)));
        when(generator.generate(any(), anyLong(), isNull(), anyBoolean()))
                .thenReturn(new GeneratedMission("A", "일상", Map.of()));
        doThrow(new DuplicateKeyException("duplicate")).when(missions).insert(any());
        assertDoesNotThrow(() -> publisher.publishIfDue(11L, LocalDateTime.of(2026, 10, 12, 12, 0)));
    }

    private Group group(LocalDateTime createdAt) {
        Group group = new Group();
        group.setId(11L);
        group.setCreatedAt(createdAt);
        group.setDailyMissionCount(1);
        return group;
    }
}
