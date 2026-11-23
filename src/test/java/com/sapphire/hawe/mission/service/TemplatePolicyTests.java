package com.sapphire.hawe.mission.service;

import com.sapphire.hawe.global.config.BanditProperties;
import com.sapphire.hawe.group.dao.GroupMapper;
import com.sapphire.hawe.group.dao.GroupMemberMapper;
import com.sapphire.hawe.group.dao.GroupMissionSlotMapper;
import com.sapphire.hawe.group.domain.Group;
import com.sapphire.hawe.global.config.MissionProperties;
import com.sapphire.hawe.mission.dao.BanditMapper;
import com.sapphire.hawe.mission.dao.BanditRecentRow;
import com.sapphire.hawe.mission.dao.MissionInsertRow;
import com.sapphire.hawe.mission.dao.MissionMapper;
import com.sapphire.hawe.mission.domain.GeneratedMission;
import com.sapphire.hawe.mission.domain.GenerationContext;
import com.sapphire.hawe.mission.domain.GroupSignals;
import com.sapphire.hawe.mission.domain.MissionTemplate;
import com.sapphire.hawe.mission.domain.MissionTemplateSelector;
import com.sapphire.hawe.mission.domain.TemplateChoice;
import com.sapphire.hawe.mission.domain.TemplateSampler;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 틀 선택 정책 스위치와 발행 기록(TODO_2/D4). */
class TemplatePolicyTests {
    private static final LocalDate DAY = LocalDate.of(2026, 11, 24);
    private final BanditStatsService stats = mock(BanditStatsService.class);
    private final BanditMapper mapper = mock(BanditMapper.class);

    private static BanditProperties policy(String name) {
        return new BanditProperties(null, null, null, null, null, null, null, null, null, null, null, null, null, null, name);
    }

    private static BanditRecentRow row(LocalDate date, String template) {
        BanditRecentRow row = new BanditRecentRow();
        row.setMissionDate(date);
        row.setTemplate(template);
        return row;
    }

    @Test void randomPolicyNeverTouchesStats() {
        assertNull(new TemplatePolicy(policy("random"), stats, mapper).samplerFor(1, DAY));
        assertNull(TemplatePolicy.randomOnly().samplerFor(1, DAY));
        verifyNoInteractions(stats, mapper);
        assertThrows(IllegalArgumentException.class, () -> policy("greedy"));
        assertTrue(policy(" BANDIT ").banditPolicy());
    }

    @Test void banditPolicyReadsTodaysStatsAndRecentHistory() {
        when(stats.snapshot(1)).thenReturn(new BanditStatsService.Snapshot(Map.of(), Map.of()));
        when(mapper.findTemplatesBetween(1, DAY.minusDays(7), DAY)).thenReturn(List.of(row(DAY.minusDays(1), "PHOTO_NOW")));
        TemplateSampler sampler = new TemplatePolicy(policy("bandit"), stats, mapper).samplerFor(1, DAY);
        assertNotNull(sampler);
        // 어제 낸 PHOTO_NOW 는 반복 패널티를 받아요(나머지는 같은 출발점)
        BanditSampler bandit = (BanditSampler) sampler;
        BanditSampler.Posterior p = bandit.posterior(List.of(MissionTemplate.PHOTO_NOW, MissionTemplate.PHOTO_COLOR));
        assertEquals(0.85, p.factor()[0], 1e-12);
        assertEquals(1.0, p.factor()[1], 1e-12);
    }

    @Test void statsFailureFallsBackToRandom() {
        when(stats.snapshot(anyLong())).thenThrow(new IllegalStateException("db down"));
        assertNull(new TemplatePolicy(policy("bandit"), stats, mapper).samplerFor(1, DAY));
    }

    @Test void labels() {
        assertEquals("FORCED", TemplatePolicy.label(true, null));
        assertEquals("RANDOM", TemplatePolicy.label(false, null));
        assertEquals("BANDIT", TemplatePolicy.label(false, (c, s) -> new TemplateChoice(c.getFirst(), 1)));
    }

    // ---- 발행 기록 ----

    private final MissionMapper missions = mock(MissionMapper.class);
    private final GroupMapper groups = mock(GroupMapper.class);
    private final GroupMemberMapper members = mock(GroupMemberMapper.class);
    private final MissionGenerator generator = mock(MissionGenerator.class);
    private final GroupContextBuilder contexts = mock(GroupContextBuilder.class);
    private final ExperimentAssigner experiments = mock(ExperimentAssigner.class);

    private MissionInsertRow publishOnce(TemplatePolicy templatePolicy, MissionTemplate requested) {
        LocalDateTime now = DAY.atTime(10, 0);
        Group group = new Group();
        group.setId(11L);
        group.setCreatedAt(now.minusDays(3));
        group.setDailyMissionCount(1);
        when(groups.findById(11L)).thenReturn(group);
        when(members.findUserIdsByGroupId(11L)).thenReturn(List.of(7L));
        when(contexts.build(any(), any())).thenReturn(new GenerationContext(DAY, "FRIEND", List.of(), List.of(), GroupSignals.none(1, 3)));
        when(experiments.assign(anyLong(), any())).thenReturn(Optional.empty());
        when(generator.generate(any(), anyLong(), any(), anyBoolean())).thenReturn(new GeneratedMission("문장", "일상", Map.of()));
        MissionPublisher publisher = new MissionPublisher(missions, groups, members, mock(GroupMissionSlotMapper.class),
                new MissionProperties(LocalTime.of(9, 0), LocalTime.of(23, 0), 1, MissionTemplateSelector.MVP_POOL),
                generator, contexts, experiments, new ObjectMapper(), mock(ApplicationEventPublisher.class), templatePolicy);
        if (requested == null) publisher.publishIfDue(11L, now);
        else publisher.forcePublish(11L, now, requested);
        ArgumentCaptor<MissionInsertRow> inserted = ArgumentCaptor.forClass(MissionInsertRow.class);
        verify(missions).insert(inserted.capture());
        return inserted.getValue();
    }

    @Test void publisherRecordsRandomPolicyByDefault() {
        MissionInsertRow row = publishOnce(TemplatePolicy.randomOnly(), null);
        assertEquals("RANDOM", row.getTemplatePolicy());
        long sameMedia = MissionTemplateSelector.MVP_POOL.stream()
                .filter(t -> t.media().equals(MissionTemplate.valueOf(row.getTemplate()).media())).count();
        assertEquals(1.0 / sameMedia, row.getSelectionProb(), 1e-12);
        // 기본 정책의 틀은 예전 선택기와 같아요
        assertEquals(MissionTemplateSelector.select(11L, DAY, 1, 1, java.util.Set.of()).name(), row.getTemplate());
    }

    @Test void publisherRecordsBanditChoiceAndForcedTemplates() {
        TemplatePolicy bandit = mock(TemplatePolicy.class);
        when(bandit.samplerFor(11L, DAY)).thenReturn((candidates, seed) -> new TemplateChoice(candidates.getLast(), 0.25));
        MissionInsertRow row = publishOnce(bandit, null);
        assertEquals("BANDIT", row.getTemplatePolicy());
        assertEquals(0.25, row.getSelectionProb());

        clearInvocations(missions);
        MissionInsertRow forced = publishOnce(bandit, MissionTemplate.TEXT_ACROSTIC);
        assertEquals("FORCED", forced.getTemplatePolicy());
        assertNull(forced.getSelectionProb());
        assertEquals("TEXT_ACROSTIC", forced.getTemplate());
    }
}
