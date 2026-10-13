package com.sapphire.hawe.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.time.MonthDay;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 미션 문장 AI(B8) 설정. 모든 값에 기본값이 있고, {@code enabled} 가 아니거나 키가 없으면 AI 를 부르지 않는다.
 *
 * @param promptPaths   시스템 프롬프트로 이어 붙일 파일들(Spring 리소스 위치). 하나도 읽지 못하면 임시 기본 프롬프트
 * @param totalTimeout  한 미션에 AI 를 기다리는 최대 시간 (발행 트랜잭션 안에서 부르므로 짧게)
 * @param specialDays   {@code MM-dd=이름} 목록 (예: {@code 12-25=크리스마스})
 */
@ConfigurationProperties(prefix = "hawe.mission.ai")
public record MissionAiProperties(boolean enabled, String baseUrl, String apiKey, String model, Integer maxTokens,
        Double temperature, Integer maxAttempts, Duration connectTimeout, Duration readTimeout, Duration totalTimeout,
        List<String> promptPaths, List<String> categories, List<String> blockedWords, List<String> specialDays) {

    public static final List<String> DEFAULT_CATEGORIES = List.of("일상", "감성", "추억", "유머");
    private static final DateTimeFormatter MONTH_DAY = DateTimeFormatter.ofPattern("MM-dd");

    public MissionAiProperties {
        baseUrl = blank(baseUrl) ? "https://api.anthropic.com" : baseUrl.strip().replaceAll("/+$", "");
        apiKey = apiKey == null ? "" : apiKey.strip();
        model = blank(model) ? "claude-haiku-5-5" : model.strip();
        maxTokens = maxTokens == null ? 400 : maxTokens;
        temperature = temperature == null ? 0.9 : temperature;
        maxAttempts = maxAttempts == null ? 2 : maxAttempts;
        connectTimeout = connectTimeout == null ? Duration.ofSeconds(10) : connectTimeout;
        readTimeout = readTimeout == null ? Duration.ofSeconds(20) : readTimeout;
        totalTimeout = totalTimeout == null ? Duration.ofSeconds(40) : totalTimeout;
        promptPaths = clean(promptPaths);
        categories = clean(categories).isEmpty() ? DEFAULT_CATEGORIES : clean(categories);
        blockedWords = clean(blockedWords);
        specialDays = clean(specialDays);
        if (maxTokens < 1 || maxAttempts < 1 || temperature < 0 || temperature > 1)
            throw new IllegalArgumentException("hawe.mission.ai 의 max-tokens·max-attempts·temperature 값이 잘못됨");
        calendarDays(specialDays);  // 형식 오류면 시작할 때 바로 알린다
    }

    /** 켜져 있고 키도 있어야 AI 를 부른다. */
    public boolean usable() {
        return enabled && !apiKey.isEmpty();
    }

    public Map<MonthDay, String> calendarDays() {
        return calendarDays(specialDays);
    }

    // 키가 로그·오류 화면에 찍히지 않게
    @Override
    public String toString() {
        return "MissionAiProperties[enabled=" + enabled + ", model=" + model + ", apiKey=" + (apiKey.isEmpty() ? "(없음)" : "(설정됨)") + "]";
    }

    private static Map<MonthDay, String> calendarDays(List<String> entries) {
        Map<MonthDay, String> days = new LinkedHashMap<>();
        for (String entry : entries) {
            int split = entry.indexOf('=');
            String name = split < 0 ? "" : entry.substring(split + 1).strip();
            try {
                if (split < 0 || name.isEmpty()) throw new DateTimeParseException("", entry, 0);
                days.put(MonthDay.parse(entry.substring(0, split).strip(), MONTH_DAY), name);
            } catch (DateTimeParseException e) {
                throw new IllegalArgumentException("hawe.mission.ai.special-days 는 MM-dd=이름 형식이어야 함: " + entry);
            }
        }
        return days;
    }

    private static List<String> clean(List<String> values) {
        return values == null ? List.of() : values.stream().filter(v -> v != null && !v.isBlank()).map(String::strip).toList();
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
