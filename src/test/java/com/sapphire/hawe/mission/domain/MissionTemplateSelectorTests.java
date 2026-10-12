package com.sapphire.hawe.mission.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MissionTemplateSelectorTests {
    @Test void poolAndSelectionAreStable() {
        assertEquals(8, MissionTemplateSelector.MVP_POOL.size());
        assertEquals(Set.of("PHOTO", "VOICE", "TEXT", "DRAW"), MissionTemplateSelector.MVP_POOL.stream()
                .map(MissionTemplate::media).collect(java.util.stream.Collectors.toSet()));
        LocalDate date = LocalDate.of(2026, 10, 12);
        for (long groupId = 1; groupId <= 100; groupId++) {
            var first = MissionTemplateSelector.select(groupId, date, 1, 1, Set.of());
            assertEquals(first, MissionTemplateSelector.select(groupId, date, 1, 1, Set.of()));
            assertFalse(MissionTemplateSelector.select(groupId, date, 1, 1, Set.of(first.media()))
                    .media().equals(first.media()));
            assertTrue(MissionTemplateSelector.MVP_POOL.contains(first));
            assertTrue(MissionTemplateSelector.MVP_POOL.contains(MissionTemplateSelector.select(
                    groupId, date, 1, 1, Set.of("PHOTO", "VOICE", "TEXT", "DRAW"))));
            Set<String> media = new HashSet<>();
            for (int slot = 1; slot <= 4; slot++) media.add(MissionTemplateSelector.select(
                    groupId, date, slot, 4, Set.of()).media());
            assertEquals(4, media.size());
        }
    }

    @Test void targetSelectionIsStableAndNearThirtyPercent() {
        int targeted = 0;
        LocalDate date = LocalDate.of(2026, 10, 12);
        for (long groupId = 1; groupId <= 1000; groupId++) {
            long seed = MissionTemplateSelector.seed(groupId, date, 1);
            boolean selected = MissionTemplateSelector.needsTarget(MissionTemplate.DRAW_SKETCH, seed);
            assertEquals(selected, MissionTemplateSelector.needsTarget(MissionTemplate.DRAW_SKETCH, seed));
            if (selected) targeted++;
            assertTrue(MissionTemplateSelector.needsTarget(MissionTemplate.TEXT_FILL_BLANK, seed));
            assertFalse(MissionTemplateSelector.needsTarget(MissionTemplate.PHOTO_NOW, seed));
        }
        assertTrue(targeted > 200 && targeted < 400);
    }
}
