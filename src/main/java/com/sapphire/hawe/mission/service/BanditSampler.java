package com.sapphire.hawe.mission.service;

import com.sapphire.hawe.global.config.BanditProperties;
import com.sapphire.hawe.mission.domain.BanditArmStats;
import com.sapphire.hawe.mission.domain.MissionTemplate;
import com.sapphire.hawe.mission.domain.TemplateChoice;
import com.sapphire.hawe.mission.domain.TemplateSampler;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * 계층 Thompson Sampling 으로 후보 틀을 고른다(TODO_2/D4, AI_FEATURES.md §4.3, 시뮬레이터 {@code ml/bandit-sim} 의 ts_hier 와 같은 식).
 * <ul>
 *   <li>그룹 분포: 전역 평균 m 쪽으로 κ 만큼 당긴 Beta(κ·m + 그룹 α, κ·(1−m) + 그룹 β). 통계가 없는 새 틀은 Beta(1,1) 근처에서 시작.</li>
 *   <li>최근 {@code repeat-days} 일 안에 나온 틀은 표본값 ×{@code repeat-penalty}.</li>
 *   <li>최근 {@code share-window-days} 일 점유율이 {@code share-cap} 을 넘은 틀은 미룬다(후보가 모두 넘었으면 상한 무시, 창 안 미션 5개 미만이면 보지 않음).</li>
 *   <li>탐색 하한: 확률 ε 로 후보 중 무작위 → 어떤 후보도 ε/후보 수 이상.</li>
 * </ul>
 * 같은 (후보, 시드, 통계)면 같은 결과다. 선택 확률은 같은 통계로 표본을 {@value #PROBABILITY_SAMPLES}번 다시 뽑아 센 근사값이다.
 * 관계 유형은 입력으로 쓰지 않는다(규칙 4).
 */
public class BanditSampler implements TemplateSampler {
    static final int PROBABILITY_SAMPLES = 200;
    static final int CAP_MIN_MISSIONS = 5;
    /** 실측 미션이 이만큼 쌓이면 합성 prior 영향이 1/e 로 준다(합성 prior 는 지금 0) */
    static final double SYN_DECAY_MISSIONS = 50.0;
    private static final long CHOICE_SALT = 0x2545F4914F6CDD1DL;
    private static final long PROBABILITY_SALT = 0x9E3779B97F4A7C15L;

    private final BanditProperties properties;
    private final BanditStatsService.Snapshot stats;
    private final Map<String, Integer> windowCounts;
    private final int windowTotal;
    private final Set<String> recent;

    /**
     * @param windowCounts 최근 점유율 창(오늘 제외)에서 틀별 미션 수
     * @param recent       최근 {@code repeat-days} 일(오늘 제외)에 나온 틀
     */
    public BanditSampler(BanditProperties properties, BanditStatsService.Snapshot stats, Map<String, Integer> windowCounts,
            Set<String> recent) {
        this.properties = properties;
        this.stats = stats;
        this.windowCounts = Map.copyOf(windowCounts);
        this.windowTotal = windowCounts.values().stream().mapToInt(Integer::intValue).sum();
        this.recent = Set.copyOf(recent);
    }

    @Override
    public TemplateChoice choose(List<MissionTemplate> candidates, long seed) {
        Posterior posterior = posterior(candidates);
        Random random = new Random(seed ^ CHOICE_SALT);
        int index = random.nextDouble() < properties.epsilon() ? random.nextInt(candidates.size()) : thompson(posterior, random);
        return new TemplateChoice(candidates.get(index), probabilities(candidates, seed)[index]);
    }

    /** 후보별 선택 확률(합 1): ε/k + (1−ε)·(TS 로 뽑힌 비율, 0 이 되지 않게 1/k 를 더해 다듬음). */
    double[] probabilities(List<MissionTemplate> candidates, long seed) {
        Posterior posterior = posterior(candidates);
        int k = candidates.size();
        int[] hits = new int[k];
        Random random = new Random(seed ^ PROBABILITY_SALT);
        for (int i = 0; i < PROBABILITY_SAMPLES; i++) hits[thompson(posterior, random)]++;
        double[] result = new double[k];
        for (int i = 0; i < k; i++)
            result[i] = properties.epsilon() / k
                    + (1 - properties.epsilon()) * (hits[i] + 1.0 / k) / (PROBABILITY_SAMPLES + 1.0);
        return result;
    }

    /** 후보마다 Beta(a, b), 반복 패널티 배율, 점유율 상한으로 미룰지. */
    Posterior posterior(List<MissionTemplate> candidates) {
        int k = candidates.size();
        double[] a = new double[k], b = new double[k], factor = new double[k];
        boolean[] capped = new boolean[k];
        boolean allCapped = true;
        for (int i = 0; i < k; i++) {
            String name = candidates.get(i).name();
            BanditArmStats global = stats.global(name);
            BanditArmStats group = stats.group(name);
            double synWeight = Math.exp(-global.missionCount() / SYN_DECAY_MISSIONS);
            double globalA = global.alphaReal() + global.alphaSyn() * synWeight + 1.0;
            double globalB = global.betaReal() + global.betaSyn() * synWeight + 1.0;
            double mean = globalA / (globalA + globalB);
            double kappa = properties.kappa();
            a[i] = Math.max(kappa > 0 ? kappa * mean + group.alphaReal() : 1.0 + group.alphaReal(), 1e-6);
            b[i] = Math.max(kappa > 0 ? kappa * (1 - mean) + group.betaReal() : 1.0 + group.betaReal(), 1e-6);
            factor[i] = recent.contains(name) ? properties.repeatPenalty() : 1.0;
            capped[i] = windowTotal >= CAP_MIN_MISSIONS
                    && windowCounts.getOrDefault(name, 0) / (double) windowTotal > properties.shareCap();
            allCapped &= capped[i];
        }
        if (allCapped) java.util.Arrays.fill(capped, false);
        return new Posterior(a, b, factor, capped);
    }

    record Posterior(double[] a, double[] b, double[] factor, boolean[] capped) { }

    private static int thompson(Posterior p, Random random) {
        int best = 0;
        double bestScore = -1;
        for (int i = 0; i < p.a().length; i++) {
            double score = p.capped()[i] ? 0.0 : beta(p.a()[i], p.b()[i], random) * p.factor()[i];
            if (score > bestScore) { bestScore = score; best = i; }
        }
        return best;
    }

    /** Beta(a, b) 표본 = Gamma(a) / (Gamma(a) + Gamma(b)). */
    static double beta(double a, double b, Random random) {
        double x = gamma(a, random);
        double y = gamma(b, random);
        return x + y == 0 ? 0.5 : x / (x + y);
    }

    /** Marsaglia–Tsang. 모양이 1 보다 작으면 Gamma(a+1)·U^(1/a). */
    static double gamma(double shape, Random random) {
        if (shape < 1) return gamma(shape + 1, random) * Math.pow(random.nextDouble(), 1.0 / shape);
        double d = shape - 1.0 / 3, c = 1 / Math.sqrt(9 * d);
        while (true) {
            double x = random.nextGaussian();
            double v = 1 + c * x;
            if (v <= 0) continue;
            v = v * v * v;
            double u = random.nextDouble();
            if (u < 1 - 0.0331 * x * x * x * x || Math.log(u) < 0.5 * x * x + d * (1 - v + Math.log(v))) return d * v;
        }
    }
}
