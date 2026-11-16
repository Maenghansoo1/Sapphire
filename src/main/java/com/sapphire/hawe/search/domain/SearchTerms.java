package com.sapphire.hawe.search.domain;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 검색 낱말 → 가중치(넣은 순서 유지). 원 검색어 낱말은 1.0, AI 확장어(B2)는 0.5.
 * [expansions] 는 그중 AI 확장으로 들어온 낱말 — 결과의 {@code viaExpansion} 표시에 쓴다.
 */
public record SearchTerms(Map<String, Double> weights, Set<String> expansions) {
    public SearchTerms {
        weights = Collections.unmodifiableMap(new LinkedHashMap<>(weights));
        expansions = Collections.unmodifiableSet(new LinkedHashSet<>(expansions));
    }

    public static SearchTerms original(Map<String, Double> weights) {
        return new SearchTerms(weights, Set.of());
    }

    public boolean isEmpty() { return weights.isEmpty(); }
}
