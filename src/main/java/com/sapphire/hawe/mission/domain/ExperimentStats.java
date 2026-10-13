package com.sapphire.hawe.mission.domain;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.random.RandomGenerator;

/**
 * 주제 비교 실험 리포트(B9)의 통계 계산. 외부 라이브러리 없이 직접 구현했다 — 정확도:
 * 정규분포 누적확률은 erfc 근사(상대 오차 1.2e-7 이하), t 분위수는 자유도 30 이하 표값(소수 셋째 자리)과
 * 그 위 Cornish–Fisher 전개(오차 1e-4 이하)다. 값이 없으면(표본 0 등) null 을 돌려준다.
 */
public final class ExperimentStats {

    public static final double Z_95 = 1.959963984540054;
    // t 분포 0.975 분위수, 자유도 1~30
    private static final double[] T_975 = {12.706, 4.303, 3.182, 2.776, 2.571, 2.447, 2.365, 2.306, 2.262, 2.228,
            2.201, 2.179, 2.160, 2.145, 2.131, 2.120, 2.110, 2.101, 2.093, 2.086,
            2.080, 2.074, 2.069, 2.064, 2.060, 2.056, 2.052, 2.048, 2.045, 2.042};

    private ExperimentStats() {
    }

    public record Interval(double low, double high) {
    }

    /** 비율 successes/n 의 Wilson 95% 신뢰구간. */
    public static Interval wilson(long successes, long n) {
        if (n <= 0) return null;
        double p = (double) successes / n;
        double z2 = Z_95 * Z_95;
        double denominator = 1 + z2 / n;
        double center = (p + z2 / (2 * n)) / denominator;
        double half = Z_95 * Math.sqrt(p * (1 - p) / n + z2 / (4.0 * n * n)) / denominator;
        return new Interval(Math.max(0, center - half), Math.min(1, center + half));
    }

    /** 두 비율 차이(1 − 2)의 합동 z 검정. [z, 양측 p]. 한쪽 표본이 0 이거나 합동 비율이 0·1 이면 null. */
    public static double[] twoProportionZ(long successes1, long n1, long successes2, long n2) {
        if (n1 <= 0 || n2 <= 0) return null;
        double pooled = (double) (successes1 + successes2) / (n1 + n2);
        double se = Math.sqrt(pooled * (1 - pooled) * (1.0 / n1 + 1.0 / n2));
        if (se == 0) return null;
        double z = ((double) successes1 / n1 - (double) successes2 / n2) / se;
        return new double[] {z, 2 * (1 - normalCdf(Math.abs(z)))};
    }

    /** 표준정규분포 누적확률 Φ(x). */
    public static double normalCdf(double x) {
        return 0.5 * erfc(-x / Math.sqrt(2));
    }

    // Numerical Recipes erfcc (Chebyshev 근사)
    private static double erfc(double x) {
        double z = Math.abs(x);
        double t = 1 / (1 + 0.5 * z);
        double answer = t * Math.exp(-z * z - 1.26551223 + t * (1.00002368 + t * (0.37409196 + t * (0.09678418
                + t * (-0.18628806 + t * (0.27886807 + t * (-1.13520398 + t * (1.48851587
                + t * (-0.82215223 + t * 0.17087277)))))))));
        return x >= 0 ? answer : 2 - answer;
    }

    /** t 분포 0.975 분위수 (양측 95%). */
    public static double tQuantile975(int degreesOfFreedom) {
        if (degreesOfFreedom < 1) throw new IllegalArgumentException("degrees of freedom < 1");
        if (degreesOfFreedom <= T_975.length) return T_975[degreesOfFreedom - 1];
        double z = Z_95;
        double v = degreesOfFreedom;
        return z + (Math.pow(z, 3) + z) / (4 * v)
                + (5 * Math.pow(z, 5) + 16 * Math.pow(z, 3) + 3 * z) / (96 * v * v)
                + (3 * Math.pow(z, 7) + 19 * Math.pow(z, 5) + 17 * Math.pow(z, 3) - 15 * z) / (384 * v * v * v);
    }

    public static Double mean(List<Double> values) {
        return values.isEmpty() ? null : values.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
    }

    public static Double median(List<Double> values) {
        if (values.isEmpty()) return null;
        double[] sorted = values.stream().mapToDouble(Double::doubleValue).sorted().toArray();
        int middle = sorted.length / 2;
        return sorted.length % 2 == 1 ? sorted[middle] : (sorted[middle - 1] + sorted[middle]) / 2;
    }

    /** 평균의 t 95% 신뢰구간. 값이 2개 미만이면 null. */
    public static Interval meanCi(List<Double> values) {
        int n = values.size();
        if (n < 2) return null;
        double mean = mean(values);
        double squares = values.stream().mapToDouble(v -> (v - mean) * (v - mean)).sum();
        double half = tQuantile975(n - 1) * Math.sqrt(squares / (n - 1)) / Math.sqrt(n);
        return new Interval(mean - half, mean + half);
    }

    /** 평균의 부트스트랩 95% 백분위 구간 (복원 추출 {@code resamples}번). 값이 2개 미만이면 null. */
    public static Interval bootstrapMeanCi(List<Double> values, int resamples, RandomGenerator random) {
        int n = values.size();
        if (n < 2 || resamples < 40) return null;
        double[] data = values.stream().mapToDouble(Double::doubleValue).toArray();
        double[] means = new double[resamples];
        for (int r = 0; r < resamples; r++) {
            double sum = 0;
            for (int i = 0; i < n; i++) sum += data[random.nextInt(n)];
            means[r] = sum / n;
        }
        Arrays.sort(means);
        return new Interval(means[(int) Math.floor(0.025 * resamples)], means[(int) Math.ceil(0.975 * resamples) - 1]);
    }

    /** 부호 검정 양측 p (차이가 0 인 것은 뺀다). 둘 다 0 이면 1. */
    public static double signTestP(int positive, int negative) {
        int n = positive + negative;
        if (n == 0) return 1;
        int k = Math.min(positive, negative);
        List<Double> logFactorials = new ArrayList<>(n + 1);
        logFactorials.add(0.0);
        for (int i = 1; i <= n; i++) logFactorials.add(logFactorials.get(i - 1) + Math.log(i));
        double tail = 0;
        for (int i = 0; i <= k; i++)
            tail += Math.exp(logFactorials.get(n) - logFactorials.get(i) - logFactorials.get(n - i) - n * Math.log(2));
        return Math.min(1, 2 * tail);
    }
}
