package com.sapphire.hawe.mission.service;

import com.sapphire.hawe.global.config.BanditProperties;
import com.sapphire.hawe.mission.domain.BanditArmStats;
import com.sapphire.hawe.mission.domain.MissionTemplate;
import com.sapphire.hawe.mission.domain.TemplateChoice;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** 계층 Thompson Sampling 표본기(TODO_2/D4). 시뮬레이터 ts_hier 와 같은 규칙. */
class BanditSamplerTests {
    private static final BanditProperties DEFAULTS = BanditProperties.defaults();
    private static final List<MissionTemplate> CANDIDATES = List.of(MissionTemplate.PHOTO_NOW, MissionTemplate.PHOTO_GALLERY,
            MissionTemplate.PHOTO_COLOR);

    private static BanditStatsService.Snapshot groupStats(Map<MissionTemplate, double[]> alphaBeta) {
        Map<String, BanditArmStats> group = new java.util.HashMap<>();
        alphaBeta.forEach((t, ab) -> group.put(t.name(), new BanditArmStats(ab[0], ab[1], 0, 0, 10)));
        return new BanditStatsService.Snapshot(Map.of(), group);
    }

    /** PHOTO_NOW 가 훨씬 잘 되는 그룹 */
    private static BanditStatsService.Snapshot dominant() {
        Map<MissionTemplate, double[]> stats = new EnumMap<>(MissionTemplate.class);
        stats.put(MissionTemplate.PHOTO_NOW, new double[]{40, 4});
        stats.put(MissionTemplate.PHOTO_GALLERY, new double[]{8, 30});
        stats.put(MissionTemplate.PHOTO_COLOR, new double[]{8, 30});
        return groupStats(stats);
    }

    private static double[] shares(BanditSampler sampler, int seeds) {
        double[] counts = new double[CANDIDATES.size()];
        for (long seed = 0; seed < seeds; seed++) counts[CANDIDATES.indexOf(sampler.choose(CANDIDATES, seed).template())]++;
        for (int i = 0; i < counts.length; i++) counts[i] /= seeds;
        return counts;
    }

    @Test void sameSeedSameChoice() {
        BanditSampler sampler = new BanditSampler(DEFAULTS, dominant(), Map.of(), Set.of());
        for (long seed = 0; seed < 50; seed++) assertEquals(sampler.choose(CANDIDATES, seed), sampler.choose(CANDIDATES, seed));
    }

    @Test void learnsTheBetterTemplateButKeepsExploring() {
        double[] share = shares(new BanditSampler(DEFAULTS, dominant(), Map.of(), Set.of()), 10_000);
        assertTrue(share[0] > 0.6, "잘 되는 틀을 주로 골라요: " + share[0]);
        double floor = DEFAULTS.epsilon() / CANDIDATES.size();
        assertTrue(share[1] >= floor * 0.8 && share[2] >= floor * 0.8, "다른 후보도 ε/후보 수 이상");
    }

    @Test void shareCapHoldsBackAnOverusedTemplate() {
        // 최근 7일 7개 중 5개가 PHOTO_NOW(71% > 40%) → 미룸, 탐색으로만 가끔
        Map<String, Integer> window = Map.of("PHOTO_NOW", 5, "TEXT_ACROSTIC", 2);
        double[] share = shares(new BanditSampler(DEFAULTS, dominant(), window, Set.of()), 5_000);
        assertTrue(share[0] < 0.05, "상한을 넘은 틀은 거의 안 나와요: " + share[0]);
        // 창 안 미션이 5개 미만이면 상한을 보지 않아요
        double[] few = shares(new BanditSampler(DEFAULTS, dominant(), Map.of("PHOTO_NOW", 3), Set.of()), 2_000);
        assertTrue(few[0] > 0.6);
        // 후보가 모두 넘었으면(후보 둘이 3개씩, 각 50%) 상한을 무시해요
        List<MissionTemplate> two = List.of(MissionTemplate.PHOTO_NOW, MissionTemplate.PHOTO_GALLERY);
        BanditSampler bothOver = new BanditSampler(DEFAULTS, dominant(), Map.of("PHOTO_NOW", 3, "PHOTO_GALLERY", 3), Set.of());
        long picks = java.util.stream.LongStream.range(0, 2_000)
                .filter(seed -> bothOver.choose(two, seed).template() == MissionTemplate.PHOTO_NOW).count();
        assertTrue(picks / 2_000.0 > 0.6);
    }

    @Test void recentTemplateIsPenalised() {
        Map<MissionTemplate, double[]> equal = new EnumMap<>(MissionTemplate.class);
        for (MissionTemplate t : CANDIDATES) equal.put(t, new double[]{3000, 3000});  // 모두 0.5 로 확실히 같음
        double[] share = shares(new BanditSampler(DEFAULTS, groupStats(equal), Map.of(), Set.of("PHOTO_NOW")), 3_000);
        assertTrue(share[0] < 0.05, "최근에 낸 틀은 ×0.85 라 거의 안 나와요: " + share[0]);
    }

    @Test void newTemplateStartsNearBetaOneOne() {
        BanditSampler.Posterior p = new BanditSampler(DEFAULTS, new BanditStatsService.Snapshot(Map.of(), Map.of()), Map.of(), Set.of())
                .posterior(List.of(MissionTemplate.DRAW_A_TO_B));
        assertEquals(1.0, p.a()[0], 1e-9);  // κ=2 × 전역 평균 0.5
        assertEquals(1.0, p.b()[0], 1e-9);
    }

    @Test void globalStatsPullNewGroupsTowardTheOverallMean() {
        BanditStatsService.Snapshot stats = new BanditStatsService.Snapshot(
                Map.of("PHOTO_NOW", new BanditArmStats(799, 199, 0, 0, 300)), Map.of());
        BanditSampler.Posterior p = new BanditSampler(DEFAULTS, stats, Map.of(), Set.of()).posterior(List.of(MissionTemplate.PHOTO_NOW));
        assertEquals(2 * 0.8, p.a()[0], 1e-9);  // 전역 평균 (799+1)/(998+2) = 0.8 쪽으로 κ=2
        assertEquals(2 * 0.2, p.b()[0], 1e-9);
    }

    @Test void selectionProbabilitiesArePositiveAndSumToOne() {
        BanditSampler sampler = new BanditSampler(DEFAULTS, dominant(), Map.of(), Set.of());
        for (long seed = 0; seed < 20; seed++) {
            double[] p = sampler.probabilities(CANDIDATES, seed);
            assertEquals(1.0, java.util.Arrays.stream(p).sum(), 1e-9);
            assertTrue(java.util.Arrays.stream(p).allMatch(x -> x > 0 && x <= 1));
            TemplateChoice choice = sampler.choose(CANDIDATES, seed);
            assertEquals(p[CANDIDATES.indexOf(choice.template())], choice.probability(), 1e-12);
        }
        double[] p = sampler.probabilities(CANDIDATES, 1);
        assertTrue(p[0] > 0.6, "잘 되는 틀의 선택 확률이 높아요");
    }

    /** 하루 1개 그룹이 밴딧으로 14일 발행: 어제와 같은 매체 금지를 지키고, 잘 되는 틀이 가장 많이 나오지만 쏠리지 않는다. */
    @Test void fourteenDaysOfBanditPublishingKeepTheRules() {
        Map<MissionTemplate, double[]> stats = new EnumMap<>(MissionTemplate.class);
        stats.put(MissionTemplate.PHOTO_NOW, new double[]{40, 4});
        for (MissionTemplate t : com.sapphire.hawe.mission.domain.MissionTemplateSelector.MVP_POOL)
            stats.putIfAbsent(t, new double[]{10, 20});
        java.util.List<MissionTemplate> history = new java.util.ArrayList<>();
        java.time.LocalDate start = java.time.LocalDate.of(2026, 12, 1);
        for (int day = 0; day < 14; day++) {
            Map<String, Integer> window = new java.util.HashMap<>();
            Set<String> recent = new java.util.HashSet<>();
            for (int back = 1; back <= 7 && day - back >= 0; back++) {
                String name = history.get(day - back).name();
                window.merge(name, 1, Integer::sum);
                if (back <= 3) recent.add(name);
            }
            Set<String> yesterday = day == 0 ? Set.of() : Set.of(history.get(day - 1).media());
            BanditSampler sampler = new BanditSampler(DEFAULTS, groupStats(stats), window, recent);
            history.add(com.sapphire.hawe.mission.domain.MissionTemplateSelector.choose(42, start.plusDays(day), 1, 1, yesterday,
                    com.sapphire.hawe.mission.domain.MissionTemplateSelector.MVP_POOL, sampler).template());
        }
        for (int day = 1; day < 14; day++)
            assertNotEquals(history.get(day - 1).media(), history.get(day).media(), "어제와 같은 매체는 안 나와요");
        long best = history.stream().filter(t -> t == MissionTemplate.PHOTO_NOW).count();
        assertTrue(best >= 3, "잘 되는 틀이 자주 나와요: " + history);
        assertTrue(best <= 7, "그래도 한 틀이 다 차지하지는 않아요: " + history);
    }

    @Test void betaSamplesHaveTheRightMean() {
        Random random = new Random(7);
        double sum = 0, small = 0;
        for (int i = 0; i < 20_000; i++) {
            sum += BanditSampler.beta(2, 5, random);
            small += BanditSampler.beta(0.5, 0.5, random);  // 모양 < 1 경로
        }
        assertEquals(2.0 / 7, sum / 20_000, 0.01);
        assertEquals(0.5, small / 20_000, 0.01);
    }
}
