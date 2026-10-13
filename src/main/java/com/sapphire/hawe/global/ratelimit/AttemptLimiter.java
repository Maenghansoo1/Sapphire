package com.sapphire.hawe.global.ratelimit;

import com.sapphire.hawe.global.exception.BusinessException;
import com.sapphire.hawe.global.exception.CommonErrorCode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AttemptLimiter {
    private final int maxFailures;
    private final Duration failureWindow;
    private final Clock clock;
    private final ConcurrentHashMap<Long, Deque<Instant>> failures = new ConcurrentHashMap<>();
    private final AtomicInteger cleanupCount = new AtomicInteger();

    @Autowired
    public AttemptLimiter(@Value("${hawe.invite.max-failures:10}") int maxFailures,
            @Value("${hawe.invite.failure-window:10m}") Duration failureWindow) {
        this(maxFailures, failureWindow, Clock.systemUTC());
    }

    public AttemptLimiter(int maxFailures, Duration failureWindow, Clock clock) {
        if (maxFailures < 1 || failureWindow == null || !failureWindow.isPositive())
            throw new IllegalArgumentException("Invite failure limit and window must be positive");
        this.maxFailures = maxFailures;
        this.failureWindow = failureWindow;
        this.clock = clock;
    }

    public void check(Long userId) {
        Instant cutoff = clock.instant().minus(failureWindow);
        AtomicBoolean blocked = new AtomicBoolean();
        failures.computeIfPresent(userId, (id, timestamps) -> {
            expire(timestamps, cutoff);
            blocked.set(timestamps.size() >= maxFailures);
            return timestamps.isEmpty() ? null : timestamps;
        });
        if (blocked.get()) throw new BusinessException(CommonErrorCode.TOO_MANY_REQUESTS);
    }

    public void recordFailure(Long userId) {
        Instant now = clock.instant();
        Instant cutoff = now.minus(failureWindow);
        failures.compute(userId, (id, timestamps) -> {
            if (timestamps == null) timestamps = new ArrayDeque<>();
            expire(timestamps, cutoff);
            if (timestamps.size() < maxFailures) timestamps.addLast(now);
            return timestamps;
        });
        if (cleanupCount.incrementAndGet() % 64 == 0) {
            failures.forEach((id, ignored) -> failures.computeIfPresent(id, (key, timestamps) -> {
                expire(timestamps, cutoff);
                return timestamps.isEmpty() ? null : timestamps;
            }));
        }
    }

    private void expire(Deque<Instant> timestamps, Instant cutoff) {
        while (!timestamps.isEmpty() && !timestamps.peekFirst().isAfter(cutoff)) timestamps.removeFirst();
    }
}
