package com.sapphire.hawe.search.dto;

/** 결과에서 실제로 겹친 낱말. [viaExpansion] 이 true 면 AI 가 넓혀 준 낱말이다 — 앱이 "'우산' 때문에 찾았어요"를 만든다. */
public record MatchedTerm(String term, boolean viaExpansion) {
}
