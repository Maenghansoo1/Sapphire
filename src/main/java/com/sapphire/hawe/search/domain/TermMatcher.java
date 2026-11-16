package com.sapphire.hawe.search.domain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 질의 낱말(가중치)과 미션 글을 맞춰 점수를 매긴다(TODO_2/B1). 원 질의 낱말은 1.0, B2 의 AI 확장어는 0.5 로 들어온다.
 * <ul>
 *   <li>형태소 낱말이 같으면 맞음("고양이를" ↔ "고양이").</li>
 *   <li>두 글자 이상이면 글 안에 그대로 들어 있어도 맞음("케이크" ↔ "생일케이크"). 한 글자("비")는 "비밀" 같은 오탐을 막으려고 낱말이 같을 때만.</li>
 * </ul>
 */
public final class TermMatcher {
    public record Match(double score, List<String> matchedTerms) {
        public boolean matched() { return score > 0; }
    }

    private TermMatcher() { }

    /**
     * @param queryTerms 질의 낱말 → 가중치(넣은 순서대로 matchedTerms 에 나온다)
     * @param docTerms 미션 글의 형태소 낱말
     * @param flatText 미션 글({@link MissionSearchText#flatten})
     */
    public static Match match(Map<String, Double> queryTerms, Collection<String> docTerms, String flatText) {
        double score = 0;
        List<String> matched = new ArrayList<>();
        for (Map.Entry<String, Double> entry : queryTerms.entrySet()) {
            String term = entry.getKey();
            double weight = entry.getValue() == null ? 0 : entry.getValue();
            if (term == null || term.isBlank() || weight <= 0) continue;
            boolean hit = docTerms.contains(term)
                    || (term.codePointCount(0, term.length()) >= 2 && flatText.contains(term));
            if (hit) { score += weight; matched.add(term); }
        }
        return new Match(score, List.copyOf(matched));
    }
}
