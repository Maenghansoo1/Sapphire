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

    /**
     * 풀은 설정 순서와 상관없이 enum 순서로 쓴다(기본 풀이면 예전 결과와 같다). 매체는 풀에 틀이 있는 것만 돈다.
     * 하루 여러 개면 매체를 슬롯 순서대로 돌리고, 슬롯이 매체보다 많아 같은 매체가 다시 오면 그 매체의 다음 틀을 쓴다
     * (매체에 틀이 하나뿐일 때만 같은 날 같은 틀이 나온다). 오늘 다른 슬롯의 DB 결과는 읽지 않는다.
     */
    public static MissionTemplate select(long groupId, LocalDate date, int slotNo,
            int dailyMissionCount, Set<String> yesterdayMedia, Collection<MissionTemplate> pool) {
        List<MissionTemplate> templates = pool.stream().distinct().sorted().toList();
        if (templates.isEmpty()) throw new IllegalArgumentException("Mission template pool is empty");
        List<String> mediaOrder = new ArrayList<>(MEDIA.stream()
                .filter(media -> templates.stream().anyMatch(template -> template.media().equals(media))).toList());
        Collections.shuffle(mediaOrder, new Random(seed(groupId, date, 0)));
        if (dailyMissionCount == 1) {
            String media = mediaOrder.stream().filter(candidate -> !yesterdayMedia.contains(candidate))
                    .findFirst().orElse(mediaOrder.getFirst());
            List<MissionTemplate> candidates = ofMedia(templates, media);
            return candidates.get(new Random(seed(groupId, date, slotNo)).nextInt(candidates.size()));
        }
        int position = (slotNo - 1) % mediaOrder.size();
        int round = (slotNo - 1) / mediaOrder.size();
        List<MissionTemplate> candidates = ofMedia(templates, mediaOrder.get(position));
        // 시작 틀은 그 매체가 처음 나오는 슬롯의 시드로 정한다 → 첫 바퀴는 예전 결과와 같다
        int start = new Random(seed(groupId, date, position + 1)).nextInt(candidates.size());
        return candidates.get((start + round) % candidates.size());
    }

    private static List<MissionTemplate> ofMedia(List<MissionTemplate> templates, String media) {
        return templates.stream().filter(template -> template.media().equals(media)).toList();
    }

    public static boolean needsTarget(MissionTemplate template, long seed) {
        if (template == MissionTemplate.TEXT_FILL_BLANK) return true;
        return (template == MissionTemplate.DRAW_SKETCH || template == MissionTemplate.DRAW_A_TO_B)
                && new Random(seed ^ 0x6A09E667F3BCC909L).nextDouble() < DRAW_TARGET_PROBABILITY;
    }
}
