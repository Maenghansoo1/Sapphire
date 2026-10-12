package com.sapphire.hawe.mission.domain;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Set;

public final class MissionTemplateSelector {

    public static final double DRAW_TARGET_PROBABILITY = 0.3;
    public static final List<MissionTemplate> MVP_POOL = List.of(
            MissionTemplate.PHOTO_NOW, MissionTemplate.PHOTO_GALLERY, MissionTemplate.PHOTO_COLOR,
            MissionTemplate.VOICE_LINE, MissionTemplate.DRAW_SKETCH, MissionTemplate.DRAW_A_TO_B,
            MissionTemplate.TEXT_ACROSTIC, MissionTemplate.TEXT_FILL_BLANK);
    private static final List<String> MEDIA = List.of("PHOTO", "VOICE", "TEXT", "DRAW");

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
        List<String> mediaOrder = new ArrayList<>(MEDIA);
        Collections.shuffle(mediaOrder, new Random(seed(groupId, date, 0)));
        String media = dailyMissionCount == 1
                ? mediaOrder.stream().filter(candidate -> !yesterdayMedia.contains(candidate))
                        .findFirst().orElse(mediaOrder.getFirst())
                : mediaOrder.get((slotNo - 1) % mediaOrder.size());
        List<MissionTemplate> candidates = MVP_POOL.stream()
                .filter(template -> template.media().equals(media)).toList();
        return candidates.get(new Random(seed(groupId, date, slotNo)).nextInt(candidates.size()));
    }

    public static boolean needsTarget(MissionTemplate template, long seed) {
        if (template == MissionTemplate.TEXT_FILL_BLANK) return true;
        return (template == MissionTemplate.DRAW_SKETCH || template == MissionTemplate.DRAW_A_TO_B)
                && new Random(seed ^ 0x6A09E667F3BCC909L).nextDouble() < DRAW_TARGET_PROBABILITY;
    }
}
