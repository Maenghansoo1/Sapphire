package com.sapphire.hawe.search.service;

import com.sapphire.hawe.global.config.MissionAiProperties;
import com.sapphire.hawe.global.config.SearchAiProperties;
import com.sapphire.hawe.mission.service.AiGenerationException;
import com.sapphire.hawe.mission.service.AnthropicClient;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.LongSupplier;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * 검색어 AI 확장(TODO_2/B2): <b>검색어 문자열만</b> AI 에 보내 뜻이 가까운 낱말 5~10개를 받는다("비" → 우산, 장마).
 * 꺼져 있음·키 없음·호출 제한 초과·시간 초과·실패·형식 오류면 빈 목록 — 검색은 원 검색어로 계속한다.
 * 같은 검색어는 캐시하고, 같은 검색어가 동시에 들어오면 AI 는 한 번만 부른다.
 * 검색어·AI 응답은 로그에 남기지 않고 실패 사유(DISABLED/RATE/TIMEOUT/HTTP/INVALID)만 DEBUG 로 남긴다.
 * 한계: 캐시·호출 제한은 서버 한 대의 메모리 기준이다(여러 대로 늘리면 대마다 따로 센다).
 */
@Component
@EnableConfigurationProperties(SearchAiProperties.class)
public class QueryExpander implements DisposableBean {
    private static final Logger log = LoggerFactory.getLogger(QueryExpander.class);
    static final String PROMPT = "classpath:prompts/search-expand-prompt.md";
    static final int MAX_TERMS = 10;
    static final int MAX_TERM_LENGTH = 20;
    private static final long WINDOW_MILLIS = 60_000;
    /** 줄바꿈·따옴표·괄호·이모지 없이 글자·숫자·띄어쓰기만 */
    private static final Pattern ALLOWED = Pattern.compile("[\\p{L}\\p{N} ]+");
    /** 확장어로 와도 검색을 흐리기만 하는 말 */
    static final Set<String> COMMON = Set.of("것", "일", "거", "사람", "때", "등", "생각", "느낌", "시간");

    static final Map<String, Object> TOOL = Map.of(
            "name", "submit_terms",
            "description", "검색어와 뜻이 가까운 한국어 낱말 목록을 제출한다",
            "input_schema", Map.of(
                    "type", "object",
                    "properties", Map.of("terms", Map.of("type", "array", "items", Map.of("type", "string"),
                            "maxItems", MAX_TERMS)),
                    "required", List.of("terms"),
                    "additionalProperties", false));

    /** AI 호출(테스트에서 가짜로 바꾼다). {@link AnthropicClient#callTool} 과 같은 모양. */
    @FunctionalInterface
    interface ExpansionCall {
        Map<String, Object> call(String system, String userMessage, Map<String, Object> tool);
    }

    private record CacheEntry(List<String> terms, long expiresAt) { }

    private final SearchAiProperties properties;
    private final MissionAiProperties ai;
    private final ExpansionCall caller;
    private final ObjectMapper json;
    private final LongSupplier clock;
    private final String systemPrompt;
    private final ExecutorService executor;
    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CompletableFuture<List<String>>> inFlight = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, ArrayDeque<Long>> calls = new ConcurrentHashMap<>();

    @Autowired
    public QueryExpander(SearchAiProperties properties, MissionAiProperties ai, AnthropicClient client, ObjectMapper json,
            ResourceLoader resources) {
        this(properties, ai, client::callTool, json, System::currentTimeMillis, loadPrompt(resources.getResource(PROMPT)),
                Executors.newVirtualThreadPerTaskExecutor());
    }

    QueryExpander(SearchAiProperties properties, MissionAiProperties ai, ExpansionCall caller, ObjectMapper json,
            LongSupplier clock, String systemPrompt, ExecutorService executor) {
        this.properties = properties;
        this.ai = ai;
        this.caller = caller;
        this.json = json;
        this.clock = clock;
        this.systemPrompt = systemPrompt;
        this.executor = executor;
    }

    /** 켜져 있고 AI 키가 있어야 부른다(미션 AI 의 켜짐 여부와는 따로). */
    public boolean usable() {
        return properties.enabled() && !ai.apiKey().isEmpty();
    }

    /** 검증을 마친 확장어(최대 10개). 쓸 수 없거나 실패하면 빈 목록 — 예외로 검색을 막지 않는다. */
    public List<String> expand(Long userId, String normalizedQuery) {
        if (normalizedQuery == null || normalizedQuery.isBlank()) return List.of();
        if (!usable()) { log.debug("Search expansion skipped: DISABLED"); return List.of(); }
        CacheEntry cached = cache.get(normalizedQuery);
        if (cached != null && cached.expiresAt() > clock.getAsLong()) { log.debug("Search expansion cache hit"); return cached.terms(); }

        CompletableFuture<List<String>> running = inFlight.get(normalizedQuery);
        if (running == null) {
            if (!tryAcquire(userId)) { log.debug("Search expansion skipped: RATE"); return List.of(); }
            CompletableFuture<List<String>> created = new CompletableFuture<>();
            running = inFlight.putIfAbsent(normalizedQuery, created);
            if (running == null) {
                running = created;
                start(normalizedQuery, created);
            }
        }
        try {
            return running.get(properties.timeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            log.debug("Search expansion failed: TIMEOUT");
        } catch (ExecutionException e) {
            log.debug("Search expansion failed: {}", e.getCause() instanceof AiGenerationException ? "HTTP" : "INVALID");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return List.of();
    }

    private void start(String query, CompletableFuture<List<String>> result) {
        try {
            executor.execute(() -> {
                try {
                    Map<String, Object> input = caller.call(systemPrompt, "검색어: " + json.writeValueAsString(query), TOOL);
                    List<String> terms = validate(input == null ? null : input.get("terms"), query, ai.blockedWords());
                    remember(query, terms);  // 기다리던 요청이 시간 초과로 먼저 떠나도 다음 요청은 캐시를 쓴다
                    result.complete(terms);
                } catch (Throwable error) {
                    result.completeExceptionally(error);
                } finally {
                    inFlight.remove(query, result);
                }
            });
        } catch (RuntimeException rejected) {
            inFlight.remove(query, result);
            result.completeExceptionally(rejected);
        }
    }

    /**
     * AI 가 준 목록을 검사한다. 형식이 틀린 항목만 버리고 나머지는 쓴다(최대 10개).
     * 2~20자, 글자·숫자·띄어쓰기만, 검색어와 같은 말·흔한 말·금칙어 제외, 소문자, 중복 제거.
     */
    static List<String> validate(Object raw, String query, List<String> blockedWords) {
        if (!(raw instanceof List<?> items)) return List.of();
        String compactQuery = query.replace(" ", "");
        LinkedHashSet<String> terms = new LinkedHashSet<>();
        for (Object item : items) {
            if (terms.size() == MAX_TERMS) break;
            if (!(item instanceof String text) || text.codePoints().anyMatch(Character::isISOControl)) continue;  // 줄바꿈 등
            String term = text.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
            int length = term.codePointCount(0, term.length());
            if (length < 2 || length > MAX_TERM_LENGTH || !ALLOWED.matcher(term).matches()) continue;
            if (term.equals(query) || term.replace(" ", "").equals(compactQuery)) continue;
            if (COMMON.contains(term) || SearchTokenizer.STOPWORDS.contains(term)) continue;
            if (blockedWords.stream().anyMatch(word -> term.contains(word.toLowerCase(Locale.ROOT)))) continue;
            terms.add(term);
        }
        return List.copyOf(terms);
    }

    /** 사용자별 1분 슬라이딩 창. 캐시 히트·같은 검색어 대기는 세지 않는다(AI 를 새로 부를 때만). */
    private boolean tryAcquire(Long userId) {
        long now = clock.getAsLong();
        boolean[] allowed = {false};
        calls.compute(userId == null ? 0L : userId, (key, times) -> {
            ArrayDeque<Long> window = times == null ? new ArrayDeque<>() : times;
            while (!window.isEmpty() && window.peekFirst() <= now - WINDOW_MILLIS) window.pollFirst();
            if (window.size() < properties.ratePerMinute()) {
                window.addLast(now);
                allowed[0] = true;
            }
            return window.isEmpty() ? null : window;
        });
        return allowed[0];
    }

    private void remember(String query, List<String> terms) {
        long now = clock.getAsLong();
        if (cache.size() >= properties.cacheSize()) {
            cache.entrySet().removeIf(entry -> entry.getValue().expiresAt() <= now);
            while (cache.size() >= properties.cacheSize()) {
                cache.entrySet().stream().min(Map.Entry.comparingByValue(java.util.Comparator.comparingLong(CacheEntry::expiresAt)))
                        .map(Map.Entry::getKey).ifPresentOrElse(cache::remove, cache::clear);
            }
        }
        cache.put(query, new CacheEntry(terms, now + properties.cacheTtl().toMillis()));
    }

    private static String loadPrompt(Resource resource) {
        try (InputStream stream = resource.getInputStream()) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8).strip();
        } catch (IOException e) {
            throw new IllegalStateException("Search expansion prompt is missing: " + PROMPT, e);
        }
    }

    @Override
    public void destroy() {
        executor.shutdownNow();
    }
}
