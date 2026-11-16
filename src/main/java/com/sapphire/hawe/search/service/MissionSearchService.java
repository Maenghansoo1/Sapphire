package com.sapphire.hawe.search.service;

import com.sapphire.hawe.global.exception.BusinessException;
import com.sapphire.hawe.global.exception.CommonErrorCode;
import com.sapphire.hawe.group.dao.GroupMapper;
import com.sapphire.hawe.group.dao.GroupMemberMapper;
import com.sapphire.hawe.group.service.GroupErrorCode;
import com.sapphire.hawe.search.dao.MissionSearchMapper;
import com.sapphire.hawe.search.dao.MissionSearchRow;
import com.sapphire.hawe.search.domain.MissionSearchText;
import com.sapphire.hawe.search.domain.SearchTerms;
import com.sapphire.hawe.search.domain.TermMatcher;
import com.sapphire.hawe.search.dto.MatchedTerm;
import com.sapphire.hawe.search.dto.MissionSearchItem;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * 지난 미션 검색(TODO_2/B1·B2). 형태소(Nori) 낱말로 <b>내가 볼 수 있는 미션 문장</b>만 찾는다.
 * 권한은 SQL({@link MissionSearchMapper#findSearchable})에서 먼저 거른다. 질의 원문은 로그에 남기지 않는다.
 * AI 확장(B2, {@link QueryExpander})이 켜져 있으면 확장어를 가중치 0.5 로 더한다 — 원 검색어로 맞은 미션이 위에 온다.
 */
@Service
public class MissionSearchService {
    public static final int MIN_QUERY_LENGTH = 2;
    public static final int MAX_QUERY_LENGTH = 50;
    public static final int DEFAULT_LIMIT = 20;
    public static final int MAX_LIMIT = 50;
    /** 한 번에 맞춰 볼 후보 상한(최신 순). 미션이 아주 많아지면 보조 색인 테이블을 검토한다. */
    static final int CANDIDATE_LIMIT = 1000;
    public static final double ORIGINAL_WEIGHT = 1.0;
    public static final double EXPANSION_WEIGHT = 0.5;

    private final MissionSearchMapper mapper;
    private final GroupMapper groups;
    private final GroupMemberMapper members;
    private final SearchTokenizer tokenizer;
    private final QueryExpander expander;
    private final ObjectMapper json;

    public MissionSearchService(MissionSearchMapper mapper, GroupMapper groups, GroupMemberMapper members,
            SearchTokenizer tokenizer, QueryExpander expander, ObjectMapper json) {
        this.mapper = mapper;
        this.groups = groups;
        this.members = members;
        this.tokenizer = tokenizer;
        this.expander = expander;
        this.json = json;
    }

    /** 트랜잭션을 걸지 않는다 — AI 확장을 기다리는 동안 DB 연결을 붙잡지 않게(조회는 한 번씩 따로 실행). */
    public List<MissionSearchItem> search(Long userId, String query, Long groupId, Integer limit) {
        String normalized = normalize(query);
        int size = limit == null ? DEFAULT_LIMIT : limit;
        if (size < 1 || size > MAX_LIMIT) throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "limit 은 1~50 이에요");
        requireGroup(userId, groupId);  // 잘못된 요청이면 AI 를 부르기 전에 막는다
        Map<String, Double> weights = new LinkedHashMap<>();
        for (String term : tokenizer.queryTerms(normalized)) weights.put(term, ORIGINAL_WEIGHT);
        Set<String> expansions = new LinkedHashSet<>();
        for (String expansion : expander.expand(userId, normalized)) {
            for (String token : tokenizer.queryTerms(expansion)) {
                // 한 글자 조각("빗소리" → "빗")은 엉뚱한 미션을 끌어와서 뺀다
                if (token.codePointCount(0, token.length()) < 2 || weights.containsKey(token)) continue;
                weights.put(token, EXPANSION_WEIGHT);
                expansions.add(token);
            }
        }
        return find(userId, groupId, new SearchTerms(weights, expansions), size);
    }

    /** 낱말 → 가중치로 찾는다. 점수 내림차순 → 같으면 최신 미션 → 같으면 id 큰 것. */
    @Transactional(readOnly = true)
    public List<MissionSearchItem> search(Long userId, Long groupId, SearchTerms terms, int limit) {
        requireGroup(userId, groupId);
        return find(userId, groupId, terms, limit);
    }

    private List<MissionSearchItem> find(Long userId, Long groupId, SearchTerms terms, int limit) {
        if (terms.isEmpty()) return List.of();
        List<Scored> scored = new ArrayList<>();
        for (MissionSearchRow row : mapper.findSearchable(userId, groupId, CANDIDATE_LIMIT)) {
            String text = MissionSearchText.of(row.getContent(), row.getTemplate(), params(row.getParams()));
            TermMatcher.Match match = TermMatcher.match(terms.weights(), tokenizer.terms(text), MissionSearchText.flatten(text));
            if (match.matched()) scored.add(new Scored(row, match));
        }
        return scored.stream()
                .sorted(Comparator.comparingDouble((Scored s) -> s.match().score()).reversed()
                        .thenComparing((Scored s) -> s.row().getMissionDate(), Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing((Scored s) -> s.row().getId(), Comparator.reverseOrder()))
                .limit(limit)
                .map(s -> item(s, terms.expansions()))
                .toList();
    }

    private static MissionSearchItem item(Scored scored, Set<String> expansions) {
        MissionSearchRow row = scored.row();
        List<MatchedTerm> matched = scored.match().matchedTerms().stream()
                .map(term -> new MatchedTerm(term, expansions.contains(term))).toList();
        return new MissionSearchItem(row.getId(), row.getGroupId(), row.getGroupName(),
                MissionSearchText.display(row.getContent()), row.getTemplate(), row.getMissionDate(), matched,
                matched.stream().anyMatch(MatchedTerm::viaExpansion), row.isSubmitted());
    }

    private void requireGroup(Long userId, Long groupId) {
        if (groupId == null) return;
        if (groups.findById(groupId) == null) throw new BusinessException(GroupErrorCode.GROUP_NOT_FOUND);
        if (!members.existsByGroupIdAndUserId(groupId, userId)) throw new BusinessException(GroupErrorCode.NOT_GROUP_MEMBER);
    }

    /**
     * 질의 정규화: 글자·숫자·공백만 남기고(특수문자는 띄어쓰기로), 공백을 하나로 줄이고 소문자로.
     * 결과가 2~50자가 아니면 400.
     */
    public static String normalize(String query) {
        if (query == null) throw invalidQuery();
        String normalized = query.replaceAll("[^\\p{L}\\p{N}\\s]", " ").replaceAll("\\s+", " ").strip().toLowerCase(Locale.ROOT);
        int length = normalized.codePointCount(0, normalized.length());
        if (length < MIN_QUERY_LENGTH || length > MAX_QUERY_LENGTH) throw invalidQuery();
        return normalized;
    }

    private static BusinessException invalidQuery() {
        return new BusinessException(CommonErrorCode.VALIDATION_ERROR, "검색어는 2~50자로 입력해 주세요");
    }

    /** 서버가 만든 params JSON. 깨졌으면 문장만으로 찾는다(예외 없음). */
    private Map<?, ?> params(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try { return json.readValue(raw, Map.class); }
        catch (Exception e) { return null; }
    }

    private record Scored(MissionSearchRow row, TermMatcher.Match match) { }
}
