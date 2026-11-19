package com.sapphire.hawe.search.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 검색 결과 한 줄. 점수는 넣지 않는다. [matchedTerms] 는 실제로 겹친 낱말(원 검색어 낱말 먼저, 그다음 AI 확장어).
 * [expanded] 는 AI 확장어로 맞은 낱말이 하나라도 있는지. [content] 의 [대상]은 닉네임 대신 "친구"로 바꿔 보여 준다.
 * [submitted] 가 false 면 피드는 기존 잠금 규칙(423)을 따른다.
 */
public record MissionSearchItem(Long missionId, Long groupId, String groupName, String content, String template,
        LocalDate missionDate, List<MatchedTerm> matchedTerms, boolean expanded, boolean submitted) {
}
