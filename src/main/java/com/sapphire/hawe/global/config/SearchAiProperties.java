package com.sapphire.hawe.global.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 검색어 AI 확장(TODO_2/B2) 설정. AI 연결(주소·키·모델)은 미션 AI({@link MissionAiProperties})와 같은 것을 쓴다.
 *
 * @param enabled       켜야 AI 를 부른다(기본 꺼짐). 꺼져 있거나 키가 없으면 원 검색어로만 찾는다
 * @param cacheTtl      같은 검색어의 확장 결과를 다시 쓰는 시간
 * @param cacheSize     캐시에 둘 검색어 수 상한
 * @param ratePerMinute 사용자 한 명이 1분에 AI 확장을 부를 수 있는 횟수. 넘으면 확장만 건너뛴다(검색은 됨)
 * @param timeout       AI 를 기다리는 최대 시간. 넘으면 확장 없이 검색한다
 */
@ConfigurationProperties(prefix = "hawe.search.ai")
public record SearchAiProperties(boolean enabled, Duration cacheTtl, Integer cacheSize, Integer ratePerMinute,
        Duration timeout) {

    public SearchAiProperties {
        cacheTtl = cacheTtl == null ? Duration.ofHours(24) : cacheTtl;
        cacheSize = cacheSize == null ? 1000 : cacheSize;
        ratePerMinute = ratePerMinute == null ? 10 : ratePerMinute;
        timeout = timeout == null ? Duration.ofSeconds(4) : timeout;
        if (cacheTtl.isNegative() || cacheSize < 1 || ratePerMinute < 0 || timeout.isNegative() || timeout.isZero())
            throw new IllegalArgumentException("hawe.search.ai 의 cache-ttl·cache-size·rate-per-minute·timeout 값이 잘못됨");
    }
}
