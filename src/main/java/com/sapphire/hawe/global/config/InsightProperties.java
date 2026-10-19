package com.sapphire.hawe.global.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "hawe.insight")
public record InsightProperties(@DefaultValue("true") boolean enabled, Duration currentMonthTtl,
        Integer topWords, Integer minWordCount) {
    public InsightProperties {
        currentMonthTtl = currentMonthTtl == null ? Duration.ofMinutes(30) : currentMonthTtl;
        topWords = topWords == null ? 10 : topWords;
        minWordCount = minWordCount == null ? 2 : minWordCount;
        if (currentMonthTtl.isZero() || currentMonthTtl.isNegative() || topWords < 1 || topWords > 50 || minWordCount < 1)
            throw new IllegalArgumentException("Invalid insight properties");
    }
}
