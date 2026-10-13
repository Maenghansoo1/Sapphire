package com.sapphire.hawe.mission.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.sapphire.hawe.mission.domain.MissionTemplate.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MissionTemplateSelectorTests {
    @Test void poolAndSelectionAreStable() {
        assertEquals(7, MissionTemplateSelector.MVP_POOL.size());
        assertEquals(Set.of("PHOTO", "TEXT", "DRAW"), MissionTemplateSelector.MVP_POOL.stream()
                .map(MissionTemplate::media).collect(java.util.stream.Collectors.toSet()));
        LocalDate date = LocalDate.of(2026, 10, 12);
        for (long groupId = 1; groupId <= 100; groupId++) {
            var first = MissionTemplateSelector.select(groupId, date, 1, 1, Set.of());
            assertEquals(first, MissionTemplateSelector.select(groupId, date, 1, 1, Set.of()));
            assertFalse(MissionTemplateSelector.select(groupId, date, 1, 1, Set.of(first.media()))
                    .media().equals(first.media()));
            assertTrue(MissionTemplateSelector.MVP_POOL.contains(first));
            assertTrue(MissionTemplateSelector.MVP_POOL.contains(MissionTemplateSelector.select(
                    groupId, date, 1, 1, Set.of("PHOTO", "TEXT", "DRAW"))));
            Set<String> media = new HashSet<>();
            for (int slot = 1; slot <= 3; slot++) media.add(MissionTemplateSelector.select(
                    groupId, date, slot, 3, Set.of()).media());
            assertEquals(3, media.size());
        }
    }

    @Test void defaultPoolKeepsSelectionsFromBeforePoolSetting() {
        // B5 전 코드(풀 고정)로 기록한 값. 기본 설정에서는 고르는 틀이 하나도 바뀌지 않아야 한다
        long before = 1274979127005909341L;
        assertEquals(before, fingerprint(MissionTemplateSelector.MVP_POOL));
        assertEquals(List.of(TEXT_FILL_BLANK, TEXT_FILL_BLANK, PHOTO_NOW, DRAW_SKETCH, PHOTO_COLOR, DRAW_SKETCH,
                PHOTO_COLOR, TEXT_ACROSTIC), java.util.stream.LongStream.rangeClosed(1, 8).mapToObj(groupId ->
                MissionTemplateSelector.select(groupId, LocalDate.of(2026, 10, 12), 1, 1, Set.of())).toList());
        // 설정의 순서·중복은 결과에 영향이 없다
        List<MissionTemplate> reordered = new ArrayList<>(MissionTemplateSelector.MVP_POOL);
        Collections.reverse(reordered);
        reordered.add(PHOTO_NOW);
        assertEquals(before, fingerprint(reordered));
    }

    @Test void newTemplatesAddSliderMediaWhenEnabled() {
        List<MissionTemplate> pool = new ArrayList<>(MissionTemplateSelector.MVP_POOL);
        pool.addAll(List.of(TEXT_ONE_WORD, TEXT_REVIEW, TEXT_BALANCE, SLIDER_POSITION, SLIDER_TASTE, SLIDER_CONSENSUS));
        Set<String> allMedia = Set.of("PHOTO", "TEXT", "DRAW", "SLIDER");
        Set<MissionTemplate> seen = EnumSet.noneOf(MissionTemplate.class);
        LocalDate date = LocalDate.of(2026, 10, 13);
        for (long groupId = 1; groupId <= 300; groupId++) {
            MissionTemplate first = MissionTemplateSelector.select(groupId, date, 1, 1, Set.of(), pool);
            assertEquals(first, MissionTemplateSelector.select(groupId, date, 1, 1, Set.of(), pool));
            seen.add(first);
            assertNotEquals(first.media(), MissionTemplateSelector.select(groupId, date, 1, 1,
                    Set.of(first.media()), pool).media());
            // 어제 네 매체를 다 썼으면 제외 규칙을 풀고 고른다
            assertTrue(pool.contains(MissionTemplateSelector.select(groupId, date, 1, 1, allMedia, pool)));
            Set<String> media = new HashSet<>();
            for (int slot = 1; slot <= 4; slot++)
                media.add(MissionTemplateSelector.select(groupId, date, slot, 4, Set.of(), pool).media());
            assertEquals(allMedia, media);
        }
        assertEquals(EnumSet.copyOf(pool), seen);
    }

    @Test void onlyMediaInThePoolAreUsed() {
        List<MissionTemplate> pool = List.of(SLIDER_TASTE, PHOTO_COLOR);
        LocalDate date = LocalDate.of(2026, 10, 13);
        for (long groupId = 1; groupId <= 50; groupId++) {
            assertEquals(PHOTO_COLOR, MissionTemplateSelector.select(groupId, date, 1, 1, Set.of("SLIDER"), pool));
            assertEquals(SLIDER_TASTE, MissionTemplateSelector.select(groupId, date, 1, 1, Set.of("PHOTO"), pool));
            assertEquals(Set.of(SLIDER_TASTE, PHOTO_COLOR), Set.of(
                    MissionTemplateSelector.select(groupId, date, 1, 2, Set.of(), pool),
                    MissionTemplateSelector.select(groupId, date, 2, 2, Set.of(), pool)));
        }
        assertThrows(IllegalArgumentException.class,
                () -> MissionTemplateSelector.select(1, date, 1, 1, Set.of(), List.of()));
    }

    private static long fingerprint(List<MissionTemplate> pool) {
        long hash = 17;
        List<Set<String>> yesterdays = List.of(Set.of(), Set.of("PHOTO"), Set.of("TEXT"), Set.of("DRAW"),
                Set.of("PHOTO", "TEXT"), Set.of("PHOTO", "TEXT", "DRAW"));
        for (LocalDate date : List.of(LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 13), LocalDate.of(2026, 11, 1)))
            for (long groupId = 1; groupId <= 300; groupId++) {
                for (Set<String> yesterday : yesterdays)
                    hash = hash * 31 + MissionTemplateSelector.select(groupId, date, 1, 1, yesterday, pool).ordinal();
                for (int count : new int[] {3, 5})
                    for (int slot = 1; slot <= count; slot++)
                        hash = hash * 31 + MissionTemplateSelector.select(groupId, date, slot, count, Set.of(), pool)
                                .ordinal();
            }
        return hash;
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
