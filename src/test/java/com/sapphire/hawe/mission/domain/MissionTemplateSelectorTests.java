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
        assertEquals(6, MissionTemplateSelector.MVP_POOL.size());
        assertFalse(MissionTemplateSelector.MVP_POOL.contains(DRAW_SKETCH));  // 시간 제한 그리기는 내지 않는다(2026-10-19)
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

    @Test void oneMissionADayAndFirstRoundKeepEarlierSelections() {
        // B7 전 코드로 기록한 값. 하루 1개, 그리고 하루 여러 개의 첫 바퀴(슬롯 ≤ 매체 수)는 고르는 틀이 바뀌지 않아야 한다
        // (B5 전 값을 고정하던 지문의 하루 1개 부분도 여기에 들어 있다)
        List<MissionTemplate> all = List.of(MissionTemplate.values());
        // 2026-10-19 기본 풀에서 30초 스케치를 빼서 기본 풀 값만 다시 기록했다. 스케치 자리만 A를 B로 바뀌고 매체 순서는 같다
        assertEquals(1252476401242372528L, single(MissionTemplateSelector.MVP_POOL));
        assertEquals(1447420503076334194L, firstRound(MissionTemplateSelector.MVP_POOL));
        assertEquals(4940000468824773898L, single(all));
        assertEquals(-7350718843695646376L, firstRound(all));
        assertEquals(List.of(TEXT_FILL_BLANK, TEXT_FILL_BLANK, PHOTO_NOW, DRAW_A_TO_B, PHOTO_COLOR, DRAW_A_TO_B,
                PHOTO_COLOR, TEXT_ACROSTIC), java.util.stream.LongStream.rangeClosed(1, 8).mapToObj(groupId ->
                MissionTemplateSelector.select(groupId, LocalDate.of(2026, 10, 12), 1, 1, Set.of())).toList());
        // 설정의 순서·중복은 결과에 영향이 없다
        List<MissionTemplate> reordered = new ArrayList<>(MissionTemplateSelector.MVP_POOL);
        Collections.reverse(reordered);
        reordered.add(PHOTO_NOW);
        assertEquals(single(MissionTemplateSelector.MVP_POOL), single(reordered));
        assertEquals(firstRound(MissionTemplateSelector.MVP_POOL), firstRound(reordered));
        assertEquals(day(7, LocalDate.of(2026, 10, 13), 5, MissionTemplateSelector.MVP_POOL),
                day(7, LocalDate.of(2026, 10, 13), 5, reordered));
    }

    @Test void severalMissionsADayRotateMediaWithoutRepeatingTemplates() {
        for (List<MissionTemplate> pool : List.of(MissionTemplateSelector.MVP_POOL, List.of(MissionTemplate.values()))) {
            int mediaCount = (int) pool.stream().map(MissionTemplate::media).distinct().count();
            for (LocalDate date : List.of(LocalDate.of(2026, 10, 13), LocalDate.of(2026, 12, 31)))
                for (long groupId = 1; groupId <= 300; groupId++)
                    for (int count = 2; count <= 5; count++) {
                        List<MissionTemplate> day = day(groupId, date, count, pool);
                        assertEquals(day, day(groupId, date, count, pool));
                        // 같은 날 같은 틀은 그 매체에 틀이 하나뿐일 때만 다시 나온다(기본 풀의 그림 = A를 B로)
                        for (MissionTemplate template : new HashSet<>(day))
                            if (java.util.Collections.frequency(day, template) > 1)
                                assertEquals(1, pool.stream().distinct().filter(t -> t.media().equals(template.media())).count(),
                                        pool.size() + "개 풀, 그룹 " + groupId + ", " + template);
                        for (int slot = mediaCount + 1; slot <= count; slot++)
                            assertEquals(day.get(slot - 1 - mediaCount).media(), day.get(slot - 1).media());
                        assertEquals(Math.min(count, mediaCount),
                                day.stream().map(MissionTemplate::media).distinct().count());
                    }
        }
    }

    @Test void fewMediaOrTemplatesCycleInsteadOfFailing() {
        LocalDate date = LocalDate.of(2026, 10, 13);
        for (long groupId = 1; groupId <= 50; groupId++) {
            // 매체가 하나뿐이면 모든 슬롯이 그 매체이고 틀이 차례로 돈다
            List<MissionTemplate> photos = day(groupId, date, 5, List.of(PHOTO_NOW, PHOTO_GALLERY, PHOTO_COLOR));
            assertEquals(Set.of(PHOTO_NOW, PHOTO_GALLERY, PHOTO_COLOR), Set.copyOf(photos.subList(0, 3)));
            assertEquals(photos.subList(0, 2), photos.subList(3, 5));
            // 매체에 틀이 하나뿐이면 같은 날 같은 틀이 다시 나올 수밖에 없다
            List<MissionTemplate> oneEach = day(groupId, date, 4, List.of(PHOTO_NOW, TEXT_ACROSTIC));
            assertEquals(Set.of(PHOTO_NOW, TEXT_ACROSTIC), Set.copyOf(oneEach.subList(0, 2)));
            assertEquals(oneEach.subList(0, 2), oneEach.subList(2, 4));
        }
    }

    private static List<MissionTemplate> day(long groupId, LocalDate date, int count, List<MissionTemplate> pool) {
        List<MissionTemplate> templates = new ArrayList<>();
        for (int slot = 1; slot <= count; slot++)
            templates.add(MissionTemplateSelector.select(groupId, date, slot, count, Set.of(), pool));
        return templates;
    }

    @Test void newTemplatesJoinThePoolWhenEnabled() {
        List<MissionTemplate> pool = new ArrayList<>(MissionTemplateSelector.MVP_POOL);
        pool.addAll(List.of(TEXT_ONE_WORD, TEXT_REVIEW, TEXT_BALANCE));
        Set<String> allMedia = Set.of("PHOTO", "TEXT", "DRAW");
        Set<MissionTemplate> seen = EnumSet.noneOf(MissionTemplate.class);
        LocalDate date = LocalDate.of(2026, 10, 13);
        for (long groupId = 1; groupId <= 300; groupId++) {
            MissionTemplate first = MissionTemplateSelector.select(groupId, date, 1, 1, Set.of(), pool);
            assertEquals(first, MissionTemplateSelector.select(groupId, date, 1, 1, Set.of(), pool));
            seen.add(first);
            assertNotEquals(first.media(), MissionTemplateSelector.select(groupId, date, 1, 1,
                    Set.of(first.media()), pool).media());
            // 어제 세 매체를 다 썼으면 제외 규칙을 풀고 고른다
            assertTrue(pool.contains(MissionTemplateSelector.select(groupId, date, 1, 1, allMedia, pool)));
            Set<String> media = new HashSet<>();
            for (int slot = 1; slot <= 3; slot++)
                media.add(MissionTemplateSelector.select(groupId, date, slot, 3, Set.of(), pool).media());
            assertEquals(allMedia, media);
        }
        assertEquals(EnumSet.copyOf(pool), seen);
    }

    @Test void onlyMediaInThePoolAreUsed() {
        List<MissionTemplate> pool = List.of(TEXT_REVIEW, PHOTO_COLOR);
        LocalDate date = LocalDate.of(2026, 10, 13);
        for (long groupId = 1; groupId <= 50; groupId++) {
            assertEquals(PHOTO_COLOR, MissionTemplateSelector.select(groupId, date, 1, 1, Set.of("TEXT"), pool));
            assertEquals(TEXT_REVIEW, MissionTemplateSelector.select(groupId, date, 1, 1, Set.of("PHOTO"), pool));
            assertEquals(Set.of(TEXT_REVIEW, PHOTO_COLOR), Set.of(
                    MissionTemplateSelector.select(groupId, date, 1, 2, Set.of(), pool),
                    MissionTemplateSelector.select(groupId, date, 2, 2, Set.of(), pool)));
        }
        assertThrows(IllegalArgumentException.class,
                () -> MissionTemplateSelector.select(1, date, 1, 1, Set.of(), List.of()));
    }

    private static final List<LocalDate> DATES =
            List.of(LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 13), LocalDate.of(2026, 11, 1));

    /** 하루 1개: 어제 매체 조합마다 고른 틀. */
    private static long single(List<MissionTemplate> pool) {
        long hash = 17;
        List<Set<String>> yesterdays = List.of(Set.of(), Set.of("PHOTO"), Set.of("TEXT"), Set.of("DRAW"),
                Set.of("PHOTO", "TEXT"), Set.of("PHOTO", "TEXT", "DRAW"));
        for (LocalDate date : DATES)
            for (long groupId = 1; groupId <= 300; groupId++)
                for (Set<String> yesterday : yesterdays)
                    hash = hash * 31 + MissionTemplateSelector.select(groupId, date, 1, 1, yesterday, pool).ordinal();
        return hash;
    }

    /** 하루 2~5개의 첫 바퀴(같은 매체가 아직 다시 나오지 않은 슬롯)에서 고른 틀. */
    private static long firstRound(List<MissionTemplate> pool) {
        long mediaCount = pool.stream().map(MissionTemplate::media).distinct().count();
        long hash = 17;
        for (LocalDate date : DATES)
            for (long groupId = 1; groupId <= 300; groupId++)
                for (int count = 2; count <= 5; count++)
                    for (int slot = 1; slot <= Math.min(count, mediaCount); slot++)
                        hash = hash * 31 + MissionTemplateSelector.select(groupId, date, slot, count, Set.of(), pool)
                                .ordinal();
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
