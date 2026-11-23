package com.sapphire.hawe.mission.service;

import com.sapphire.hawe.global.config.BanditProperties;
import com.sapphire.hawe.mission.dao.BanditMapper;
import com.sapphire.hawe.mission.dao.BanditRecentRow;
import com.sapphire.hawe.mission.domain.TemplateSampler;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 틀 선택 정책(TODO_2/D4). {@code hawe.bandit.template-policy=random}(기본)이면 늘 null — 예전 무작위 선택 그대로.
 * {@code bandit} 이면 그 그룹·날짜의 통계와 최근 이력으로 {@link BanditSampler} 를 만든다. 통계를 못 읽으면 경고만 남기고
 * null(무작위)로 돌아가 발행을 막지 않는다. 통계는 새벽에 한 번 바뀌므로(D3) 같은 날 슬롯들은 같은 표본기를 본다.
 */
@Component
public class TemplatePolicy {
    public static final String RANDOM = "RANDOM";
    public static final String BANDIT = "BANDIT";
    /** 관리·테스트 경로에서 틀을 직접 지정한 미션(정책 비교에서 뺀다) */
    public static final String FORCED = "FORCED";
    private static final Logger log = LoggerFactory.getLogger(TemplatePolicy.class);

    private final BanditProperties properties;
    private final BanditStatsService stats;
    private final BanditMapper mapper;

    public TemplatePolicy(BanditProperties properties, BanditStatsService stats, BanditMapper mapper) {
        this.properties = properties;
        this.stats = stats;
        this.mapper = mapper;
    }

    /** 늘 무작위인 정책(밴딧 의존성 없이 발행기를 만드는 테스트용). */
    public static TemplatePolicy randomOnly() {
        return new TemplatePolicy(BanditProperties.defaults(), null, null);
    }

    /** 그 그룹·날짜의 표본기. 무작위 정책이거나 통계를 못 읽으면 null. */
    public TemplateSampler samplerFor(long groupId, LocalDate date) {
        if (!properties.banditPolicy()) return null;
        try {
            int days = Math.max(properties.shareWindowDays(), properties.repeatDays());
            Map<String, Integer> window = new HashMap<>();
            Set<String> recent = new HashSet<>();
            for (BanditRecentRow row : mapper.findTemplatesBetween(groupId, date.minusDays(days), date)) {
                long ago = ChronoUnit.DAYS.between(row.getMissionDate(), date);
                if (ago <= properties.shareWindowDays()) window.merge(row.getTemplate(), 1, Integer::sum);
                if (ago <= properties.repeatDays()) recent.add(row.getTemplate());
            }
            return new BanditSampler(properties, stats.snapshot(groupId), window, recent);
        } catch (RuntimeException e) {
            log.warn("Bandit template choice unavailable for a group ({}); using random", e.getClass().getSimpleName());
            return null;
        }
    }

    /** 저장할 정책 이름: 직접 지정이면 FORCED, 표본기가 있으면 BANDIT, 아니면 RANDOM. */
    public static String label(boolean forced, TemplateSampler sampler) {
        return forced ? FORCED : sampler == null ? RANDOM : BANDIT;
    }
}
