package com.sapphire.hawe.mission.domain;

import com.sapphire.hawe.mission.domain.ExperimentStats.Interval;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/** 손으로(또는 표로) 확인한 값과 비교한다. */
class ExperimentStatsTests {

    @Test
    void wilsonIntervalMatchesKnownValues() {
        assertInterval(0.3968, 0.8922, ExperimentStats.wilson(7, 10));
        assertInterval(0.0, 0.2775, ExperimentStats.wilson(0, 10));      // 0% 여도 위쪽은 0 이 아님
        assertInterval(0.7225, 1.0, ExperimentStats.wilson(10, 10));
        assertInterval(0.4038, 0.5962, ExperimentStats.wilson(50, 100));
        assertNull(ExperimentStats.wilson(0, 0));
    }

    @Test
    void twoProportionZTestMatchesKnownValues() {
        double[] test = ExperimentStats.twoProportionZ(50, 100, 30, 100);
        assertEquals(2.8868, test[0], 1e-4);
        assertEquals(0.003892, test[1], 1e-5);
        double[] reverse = ExperimentStats.twoProportionZ(30, 100, 50, 100);
        assertEquals(-2.8868, reverse[0], 1e-4);
        assertEquals(test[1], reverse[1], 1e-12);
        assertEquals(1.0, ExperimentStats.twoProportionZ(5, 10, 5, 10)[1], 1e-6);
        assertNull(ExperimentStats.twoProportionZ(0, 10, 0, 10));   // 둘 다 0% — 검정할 수 없음
        assertNull(ExperimentStats.twoProportionZ(3, 10, 0, 0));
    }

    @Test
    void normalCdfAndTQuantiles() {
        assertEquals(0.5, ExperimentStats.normalCdf(0), 1e-7);
        assertEquals(0.9750021, ExperimentStats.normalCdf(1.96), 1e-6);
        assertEquals(0.975, ExperimentStats.normalCdf(ExperimentStats.Z_95), 1e-6);
        assertEquals(0.1586553, ExperimentStats.normalCdf(-1), 1e-6);
        assertEquals(12.706, ExperimentStats.tQuantile975(1));
        assertEquals(2.776, ExperimentStats.tQuantile975(4));
        assertEquals(2.042, ExperimentStats.tQuantile975(30));
        assertEquals(2.0211, ExperimentStats.tQuantile975(40), 1e-4);    // 표값 2.021
        assertEquals(1.9799, ExperimentStats.tQuantile975(120), 1e-4);   // 표값 1.980
        assertEquals(1.9623, ExperimentStats.tQuantile975(1000), 1e-4);
        assertThrows(IllegalArgumentException.class, () -> ExperimentStats.tQuantile975(0));
    }

    @Test
    void meanIntervalUsesTDistribution() {
        // 평균 3, 표준편차 √2.5, t(4) = 2.776 → 3 ± 1.963
        assertInterval(1.0371, 4.9629, ExperimentStats.meanCi(List.of(1.0, 2.0, 3.0, 4.0, 5.0)));
        assertNull(ExperimentStats.meanCi(List.of(7.0)));
        assertEquals(3.0, ExperimentStats.mean(List.of(1.0, 2.0, 6.0)));
        assertNull(ExperimentStats.mean(List.of()));
        assertEquals(2.0, ExperimentStats.median(List.of(3.0, 1.0, 2.0)));
        assertEquals(2.5, ExperimentStats.median(List.of(4.0, 1.0, 3.0, 2.0)));
        assertNull(ExperimentStats.median(List.of()));
    }

    @Test
    void signTestIsTheExactBinomial() {
        assertEquals(0.109375, ExperimentStats.signTestP(8, 2), 1e-12);    // 2 × (1 + 10 + 45) / 1024
        assertEquals(0.109375, ExperimentStats.signTestP(2, 8), 1e-12);
        assertEquals(0.001953125, ExperimentStats.signTestP(10, 0), 1e-12);
        assertEquals(1.0, ExperimentStats.signTestP(5, 5));
        assertEquals(1.0, ExperimentStats.signTestP(0, 0));
        assertEquals(0.5, ExperimentStats.signTestP(2, 0), 1e-12);
        assertTrue(ExperimentStats.signTestP(600, 400) < 1e-9);           // 큰 표본에서도 넘치지 않는다
    }

    @Test
    void bootstrapIsRepeatableWithTheSameSeed() {
        List<Double> values = List.of(4.0, 9.0, -2.0, 7.5, 3.0, 12.0, 0.5, 6.0);
        Interval first = ExperimentStats.bootstrapMeanCi(values, 5000, new Random(42));
        Interval second = ExperimentStats.bootstrapMeanCi(values, 5000, new Random(42));
        assertEquals(first, second);
        double mean = ExperimentStats.mean(values);
        assertTrue(first.low() < mean && mean < first.high());
        assertTrue(first.low() >= -2.0 && first.high() <= 12.0);
        assertEquals(new Interval(5.0, 5.0), ExperimentStats.bootstrapMeanCi(List.of(5.0, 5.0, 5.0), 1000, new Random(1)));
        assertNull(ExperimentStats.bootstrapMeanCi(List.of(5.0), 1000, new Random(1)));
    }

    private static void assertInterval(double low, double high, Interval interval) {
        assertNotNull(interval);
        assertEquals(low, interval.low(), 1e-4);
        assertEquals(high, interval.high(), 1e-4);
    }
}
