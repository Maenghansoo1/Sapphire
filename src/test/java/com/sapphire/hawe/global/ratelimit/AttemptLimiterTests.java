package com.sapphire.hawe.global.ratelimit;

import com.sapphire.hawe.global.exception.BusinessException;
import com.sapphire.hawe.global.exception.CommonErrorCode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AttemptLimiterTests {
    @Test
    void tenthFailureBlocksNextAttemptWithoutAffectingOtherUser() {
        MutableClock clock = new MutableClock();
        AttemptLimiter limiter = new AttemptLimiter(10, Duration.ofMinutes(10), clock);
        for (int i = 0; i < 9; i++) limiter.recordFailure(1L);
        assertDoesNotThrow(() -> limiter.check(1L));
        limiter.recordFailure(1L);
        assertEquals(CommonErrorCode.TOO_MANY_REQUESTS,
                assertThrows(BusinessException.class, () -> limiter.check(1L)).errorCode());
        assertDoesNotThrow(() -> limiter.check(2L));
    }

    @Test
    void expiredFailuresReleaseBlockAndBlockedAttemptsDoNotExtendIt() {
        MutableClock clock = new MutableClock();
        AttemptLimiter limiter = new AttemptLimiter(2, Duration.ofMinutes(10), clock);
        limiter.recordFailure(1L);
        clock.advance(Duration.ofMinutes(1));
        limiter.recordFailure(1L);
        assertThrows(BusinessException.class, () -> limiter.check(1L));
        clock.advance(Duration.ofMinutes(1));
        limiter.recordFailure(1L);
        clock.advance(Duration.ofMinutes(8));
        assertDoesNotThrow(() -> limiter.check(1L));
        limiter.recordFailure(1L);
        assertThrows(BusinessException.class, () -> limiter.check(1L));
        clock.advance(Duration.ofMinutes(2));
        assertDoesNotThrow(() -> limiter.check(1L));
    }

    @Test
    void configuredDefaultValuesAllowTenFailuresThenExpireAfterTenMinutes() {
        AttemptLimiter limiter = new AttemptLimiter(10, Duration.ofMinutes(10));
        for (int i = 0; i < 10; i++) limiter.recordFailure(42L);
        assertThrows(BusinessException.class, () -> limiter.check(42L));
    }

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-10-13T00:00:00Z");
        void advance(Duration duration) { now = now.plus(duration); }
        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
