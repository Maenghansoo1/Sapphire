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
import java.util.ArrayList;
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
                new MissionProperties(LocalTime.of(9, 0), LocalTime.of(23, 0), 1, MissionTemplateSelector.MVP_POOL), generator,
                new ObjectMapper());
        lenient().when(members.findUserIdsByGroupId(11L)).thenReturn(List.of(7L));
    }

    @Test
    void publishesScheduledMissionOnceAndCatchesUpToday() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 12, 11, 0);
        when(groups.findById(11L)).thenReturn(group(now.minusDays(2)));
        when(generator.generate(any(), anyLong(), anyList(), anyBoolean()))
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
        verify(missions).findRecentContentsByGroupId(11L, MissionPublisher.RECENT_CONTENTS);

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
        when(generator.generate(any(), anyLong(), anyList(), anyBoolean()))
                .thenReturn(new GeneratedMission("A", "일상", Map.of()));
        publisher.publishIfDue(11L, created);
        ArgumentCaptor<MissionInsertRow> inserted = ArgumentCaptor.forClass(MissionInsertRow.class);
        verify(missions).insert(inserted.capture());
        assertEquals(created, inserted.getValue().getPublishedAt());
    }

    @Test
    void publishesOnlyFromTheConfiguredPool() {
        publisher = new MissionPublisher(missions, groups, members, slots,
                new MissionProperties(LocalTime.of(9, 0), LocalTime.of(23, 0), 1, List.of(MissionTemplate.SLIDER_TASTE)),
                generator, new ObjectMapper());
        LocalDateTime now = LocalDateTime.of(2026, 10, 13, 10, 0);
        when(groups.findById(11L)).thenReturn(group(now.minusDays(2)));
        when(generator.generate(eq(MissionTemplate.SLIDER_TASTE), anyLong(), anyList(), eq(false)))
                .thenReturn(new GeneratedMission("민트초코, 내 취향은 어디쯤?", "일상",
                        Map.of("topic", "민트초코", "left", "별로", "right", "최고")));

        publisher.publishIfDue(11L, now);

        ArgumentCaptor<MissionInsertRow> inserted = ArgumentCaptor.forClass(MissionInsertRow.class);
        verify(missions).insert(inserted.capture());
        assertEquals("SLIDER_TASTE", inserted.getValue().getTemplate());
    }

    @Test
    void duplicateInsertAndMissingGroupAreHarmless() {
        publisher.publishIfDue(99L, LocalDateTime.of(2026, 10, 12, 12, 0));
        verify(missions, never()).insert(any());
        when(groups.findById(11L)).thenReturn(group(LocalDateTime.of(2026, 10, 11, 12, 0)));
        when(generator.generate(any(), anyLong(), anyList(), anyBoolean()))
                .thenReturn(new GeneratedMission("A", "일상", Map.of()));
        doThrow(new DuplicateKeyException("duplicate")).when(missions).insert(any());
        assertDoesNotThrow(() -> publisher.publishIfDue(11L, LocalDateTime.of(2026, 10, 12, 12, 0)));
    }

    @Test
    void severalSlotsAtTheSameTimeOpenTogetherWithDifferentTemplates() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 13, 9, 0);
        List<MissionInsertRow> saved = publishThreeSlots(now, MissionTemplateSelector.MVP_POOL);

        assertEquals(List.of(1, 2, 3), saved.stream().map(MissionInsertRow::getSlotNo).toList());
        assertEquals(3, saved.stream().map(MissionInsertRow::getTemplate).distinct().count());
        assertEquals(3, saved.stream().map(MissionInsertRow::getTemplate).map(MissionTemplate::valueOf)
                .map(MissionTemplate::media).distinct().count());
        assertTrue(saved.stream().allMatch(row -> row.getPublishedAt().equals(now)));
        // 앞 슬롯 문장 + 그 전 5개를 피하도록 5 + 3 − 1 개를 읽는다
        verify(missions, times(3)).findRecentContentsByGroupId(11L, MissionPublisher.RECENT_CONTENTS + 2);

        // 다시 실행해도 이미 있는 슬롯은 건너뛴다
        when(missions.existsByGroupIdAndDateAndSlot(eq(11L), eq(now.toLocalDate()), anyInt())).thenReturn(true);
        publisher.publishIfDue(11L, now);
        verify(missions, times(3)).insert(any());
    }

    @Test
    void sameTemplateTwiceADayGetsADifferentSentence() {
        // 매체에 틀이 하나뿐인 풀 → 세 슬롯이 모두 같은 틀. 앞 슬롯 문장을 피해 변형 3개를 하나씩 쓴다
        List<MissionInsertRow> saved = publishThreeSlots(LocalDateTime.of(2026, 10, 13, 9, 0),
                List.of(MissionTemplate.PHOTO_COLOR));
        assertEquals(List.of("PHOTO_COLOR", "PHOTO_COLOR", "PHOTO_COLOR"),
                saved.stream().map(MissionInsertRow::getTemplate).toList());
        assertEquals(3, saved.stream().map(MissionInsertRow::getContent).distinct().count());
    }

    /** 하루 3개·모두 09:00 인 그룹을 실제 생성기로 발행한다. 저장한 문장은 같은 트랜잭션처럼 바로 최근 문장에 보인다. */
    private List<MissionInsertRow> publishThreeSlots(LocalDateTime now, List<MissionTemplate> pool) {
        publisher = new MissionPublisher(missions, groups, members, slots,
                new MissionProperties(LocalTime.of(9, 0), LocalTime.of(23, 0), 5, pool),
                new PlaceholderMissionGenerator(), new ObjectMapper());
        Group group = group(now.minusDays(2));
        group.setDailyMissionCount(3);
        when(groups.findById(11L)).thenReturn(group);
        List<MissionInsertRow> saved = new ArrayList<>();
        doAnswer(invocation -> saved.add(invocation.getArgument(0))).when(missions).insert(any());
        when(missions.findRecentContentsByGroupId(eq(11L), anyInt())).thenAnswer(invocation ->
                saved.reversed().stream().map(MissionInsertRow::getContent).toList());

        publisher.publishIfDue(11L, now);
        return saved;
    }

    private Group group(LocalDateTime createdAt) {
        Group group = new Group();
        group.setId(11L);
        group.setCreatedAt(createdAt);
        group.setDailyMissionCount(1);
        return group;
    }
}
