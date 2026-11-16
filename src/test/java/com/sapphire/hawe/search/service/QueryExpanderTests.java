package com.sapphire.hawe.search.service;

import com.sapphire.hawe.global.config.MissionAiProperties;
import com.sapphire.hawe.global.config.SearchAiProperties;
import com.sapphire.hawe.mission.service.AiGenerationException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;

/** 검색어 AI 확장(TODO_2/B2). 실제 AI 대신 가짜 호출을 넣는다. */
class QueryExpanderTests {
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final AtomicLong now = new AtomicLong(1_000_000);
    private final AtomicInteger calls = new AtomicInteger();
    private final List<String> sentMessages = new ArrayList<>();

    @AfterEach void close() { executor.shutdownNow(); }

    private static SearchAiProperties settings(boolean enabled, int rate, Duration timeout) {
        return new SearchAiProperties(enabled, Duration.ofHours(24), 1000, rate, timeout);
    }

    private static MissionAiProperties ai(String key, List<String> blocked) {
        return new MissionAiProperties(false, null, key, null, null, null, null, null, null, null, null, null, blocked, null);
    }

    private QueryExpander expander(SearchAiProperties settings, MissionAiProperties ai, QueryExpander.ExpansionCall call) {
        QueryExpander.ExpansionCall counted = (system, message, tool) -> {
            calls.incrementAndGet();
            synchronized (sentMessages) { sentMessages.add(message); }
            return call.call(system, message, tool);
        };
        return new QueryExpander(settings, ai, counted, new ObjectMapper(), now::get, "시스템", executor);
    }

    private QueryExpander working(List<Object> terms) {
        return expander(settings(true, 10, Duration.ofSeconds(2)), ai("key", List.of()), (s, m, t) -> Map.of("terms", terms));
    }

    @Test void expandsAndSendsOnlyTheQuery() {
        QueryExpander expander = working(List.of("우산", "장마", "빗소리"));
        assertEquals(List.of("우산", "장마", "빗소리"), expander.expand(7L, "비 오는 날"));
        // 보내는 건 검색어 한 줄뿐(JSON 따옴표로 감싸서 지시문처럼 읽히지 않게)
        assertEquals(List.of("검색어: \"비 오는 날\""), sentMessages);
        assertEquals("submit_terms", QueryExpander.TOOL.get("name"));
    }

    @Test void invalidItemsAreDroppedAndTheRestKept() {
        List<Object> raw = new ArrayList<>(List.of("우산", "우산", "  장마  ", "비 오는 날", "비오는날", "것", "a", "줄\n바꿈",
                "{json}", "🌧️", "가".repeat(21), "금칙어포함", "Umbrella"));
        raw.add(42);
        raw.add(null);
        List<String> terms = QueryExpander.validate(raw, "비 오는 날", List.of("금칙어"));
        assertEquals(List.of("우산", "장마", "umbrella"), terms);
        assertEquals(10, QueryExpander.validate(java.util.stream.IntStream.range(0, 20).mapToObj(i -> "낱말" + i).toList(),
                "비", List.of()).size());  // 최대 10개
        assertEquals(List.of(), QueryExpander.validate("우산", "비", List.of()));  // 목록이 아니면 버림
        assertEquals(List.of(), QueryExpander.validate(List.of("것", "{}"), "비", List.of()));  // 전부 무효면 빈 목록
    }

    @Test void allInvalidGivesEmpty() {
        assertEquals(List.of(), working(List.of("것", "!!", "")).expand(7L, "고양이"));
    }

    @Test void failureOrTimeoutGivesEmpty() {
        QueryExpander broken = expander(settings(true, 10, Duration.ofSeconds(2)), ai("key", List.of()),
                (s, m, t) -> { throw new AiGenerationException("AI 응답 오류 HTTP 500"); });
        assertEquals(List.of(), broken.expand(7L, "고양이"));
        QueryExpander noTerms = expander(settings(true, 10, Duration.ofSeconds(2)), ai("key", List.of()), (s, m, t) -> Map.of());
        assertEquals(List.of(), noTerms.expand(7L, "고양이"));

        CountDownLatch release = new CountDownLatch(1);
        QueryExpander slow = expander(settings(true, 10, Duration.ofMillis(100)), ai("key", List.of()), (s, m, t) -> {
            try { release.await(5, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            return Map.of("terms", List.of("우산"));
        });
        long started = System.nanoTime();
        assertEquals(List.of(), slow.expand(7L, "비 오는 날"));
        assertTrue(System.nanoTime() - started < TimeUnit.SECONDS.toNanos(2), "시간 초과면 기다리지 않아요");
        release.countDown();
    }

    @Test void disabledOrNoKeyNeverCalls() {
        assertEquals(List.of(), expander(settings(false, 10, Duration.ofSeconds(2)), ai("key", List.of()),
                (s, m, t) -> Map.of("terms", List.of("우산"))).expand(7L, "비 오는 날"));
        QueryExpander noKey = expander(settings(true, 10, Duration.ofSeconds(2)), ai("", List.of()),
                (s, m, t) -> Map.of("terms", List.of("우산")));
        assertFalse(noKey.usable());
        assertEquals(List.of(), noKey.expand(7L, "비 오는 날"));
        assertEquals(0, calls.get());
    }

    @Test void cacheHitsUntilTtlExpires() {
        QueryExpander expander = working(List.of("우산"));
        assertEquals(List.of("우산"), expander.expand(7L, "비 오는 날"));
        assertEquals(List.of("우산"), expander.expand(8L, "비 오는 날"));  // 다른 사람이 같은 검색어여도 캐시
        assertEquals(1, calls.get());
        now.addAndGet(Duration.ofHours(24).toMillis() + 1);
        assertEquals(List.of("우산"), expander.expand(7L, "비 오는 날"));
        assertEquals(2, calls.get());
    }

    @Test void rateLimitSkipsOnlyTheExpansion() {
        QueryExpander expander = expander(settings(true, 2, Duration.ofSeconds(2)), ai("key", List.of()),
                (s, m, t) -> Map.of("terms", List.of("우산")));
        assertEquals(List.of("우산"), expander.expand(7L, "비 하나"));
        assertEquals(List.of("우산"), expander.expand(7L, "비 둘"));
        assertEquals(List.of(), expander.expand(7L, "비 셋"));  // 1분에 2번까지
        assertEquals(List.of("우산"), expander.expand(7L, "비 하나"));  // 캐시는 제한과 상관없어요
        assertEquals(List.of("우산"), expander.expand(9L, "비 넷"));  // 사람마다 따로 세요
        now.addAndGet(60_001);
        assertEquals(List.of("우산"), expander.expand(7L, "비 셋"));
        assertEquals(4, calls.get());
    }

    @Test void sameQueryAtOnceCallsOnce() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        QueryExpander expander = expander(settings(true, 10, Duration.ofSeconds(3)), ai("key", List.of()), (s, m, t) -> {
            entered.countDown();
            try { release.await(5, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            return Map.of("terms", List.of("우산"));
        });
        var first = executor.submit(() -> expander.expand(7L, "비 오는 날"));
        assertTrue(entered.await(2, TimeUnit.SECONDS));
        var second = executor.submit(() -> expander.expand(8L, "비 오는 날"));
        Thread.sleep(100);
        release.countDown();
        assertEquals(List.of("우산"), first.get(3, TimeUnit.SECONDS));
        assertEquals(List.of("우산"), second.get(3, TimeUnit.SECONDS));
        assertEquals(1, calls.get());
    }

    @Test void settingsHaveSafeDefaults() {
        SearchAiProperties defaults = new SearchAiProperties(false, null, null, null, null);
        assertEquals(Duration.ofHours(24), defaults.cacheTtl());
        assertEquals(10, defaults.ratePerMinute());
        assertEquals(Duration.ofSeconds(4), defaults.timeout());
        assertThrows(IllegalArgumentException.class, () -> new SearchAiProperties(true, null, 0, null, null));
    }
}
