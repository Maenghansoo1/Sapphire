package com.sapphire.hawe.mission.domain;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Set;

public final class MissionTemplateSelector {

    public static final double DRAW_TARGET_PROBABILITY = 0.3;
    /** 기본 발행 풀 ({@code hawe.mission.enabled-templates} 를 설정하지 않았을 때). */
    public static final List<MissionTemplate> MVP_POOL = List.of(
            MissionTemplate.PHOTO_NOW, MissionTemplate.PHOTO_GALLERY, MissionTemplate.PHOTO_COLOR,
            MissionTemplate.DRAW_SKETCH, MissionTemplate.DRAW_A_TO_B,
            MissionTemplate.TEXT_ACROSTIC, MissionTemplate.TEXT_FILL_BLANK);
    // 매체 순서는 고정한다 — 섞는 시드가 같으면 풀에 같은 매체가 있을 때 같은 순서가 나온다
    private static final List<String> MEDIA = List.of("PHOTO", "TEXT", "DRAW", "SLIDER");

    private MissionTemplateSelector() {
    }

    public static long seed(long groupId, LocalDate date, int slotNo) {
        long value = groupId * 0x9E3779B97F4A7C15L;
        value ^= date.toEpochDay() * 0xBF58476D1CE4E5B9L;
        value ^= (long) slotNo * 0x94D049BB133111EBL;
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }

    public static MissionTemplate select(long groupId, LocalDate date, int slotNo,
            int dailyMissionCount, Set<String> yesterdayMedia) {
        return select(groupId, date, slotNo, dailyMissionCount, yesterdayMedia, MVP_POOL);
    }

    /** 풀은 설정 순서와 상관없이 enum 순서로 쓴다(기본 풀이면 예전 결과와 같다). 매체는 풀에 틀이 있는 것만 돈다. */
    public static MissionTemplate select(long groupId, LocalDate date, int slotNo,
            int dailyMissionCount, Set<String> yesterdayMedia, Collection<MissionTemplate> pool) {
        List<MissionTemplate> templates = pool.stream().distinct().sorted().toList();
        if (templates.isEmpty()) throw new IllegalArgumentException("Mission template pool is empty");
        List<String> mediaOrder = new ArrayList<>(MEDIA.stream()
                .filter(media -> templates.stream().anyMatch(template -> template.media().equals(media))).toList());
        Collections.shuffle(mediaOrder, new Random(seed(groupId, date, 0)));
        String media = dailyMissionCount == 1
                ? mediaOrder.stream().filter(candidate -> !yesterdayMedia.contains(candidate))
                        .findFirst().orElse(mediaOrder.getFirst())
                : mediaOrder.get((slotNo - 1) % mediaOrder.size());
        List<MissionTemplate> candidates = templates.stream()
                .filter(template -> template.media().equals(media)).toList();
        return candidates.get(new Random(seed(groupId, date, slotNo)).nextInt(candidates.size()));
    }

    public static boolean needsTarget(MissionTemplate template, long seed) {
        if (template == MissionTemplate.TEXT_FILL_BLANK) return true;
        return (template == MissionTemplate.DRAW_SKETCH || template == MissionTemplate.DRAW_A_TO_B)
                && new Random(seed ^ 0x6A09E667F3BCC909L).nextDouble() < DRAW_TARGET_PROBABILITY;
    }
}
