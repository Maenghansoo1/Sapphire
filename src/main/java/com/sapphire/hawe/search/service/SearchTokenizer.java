package com.sapphire.hawe.search.service;

import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.ko.KoreanTokenizer;
import org.apache.lucene.analysis.ko.POS;
import org.apache.lucene.analysis.ko.tokenattributes.PartOfSpeechAttribute;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.util.AttributeFactory;
import org.springframework.stereotype.Component;

/**
 * 검색용 형태소 낱말(TODO_2/B1). 인사이트의 {@code NoriKeywordExtractor} 와 달리
 * <ul>
 *   <li>한 글자 명사도 남긴다("비", "꽃", "달" — 미션 주제로 흔하다).</li>
 *   <li>미션 글의 복합명사는 통째와 쪼갠 것을 모두 남긴다("생일케이크" → 생일케이크, 생일, 케이크) — "케이크"로도 찾히게.</li>
 *   <li>질의는 쪼개지 않는다 — "코뿔소"가 "코뿔"+"소"로 갈려 "소"(한 글자)로 엉뚱한 미션이 찾히지 않게.</li>
 *   <li>인사이트 불용어 대신, 미션 문장 대부분에 들어가 검색을 흐리는 몇 낱말만 뺀다.</li>
 * </ul>
 * 품사 규칙은 질의와 미션 글이 같아야 맞으므로 둘 다 이 클래스로 자른다.
 */
@Component
public class SearchTokenizer {
    /** 미션 문장 대부분에 들어가는 낱말 — 이것만 맞은 결과가 쏟아지지 않게 */
    static final Set<String> STOPWORDS = Set.of("오늘", "지금", "하루", "요즘");

    private final Analyzer documentAnalyzer = analyzer(KoreanTokenizer.DecompoundMode.MIXED);
    private final Analyzer queryAnalyzer = analyzer(KoreanTokenizer.DecompoundMode.NONE);

    private static Analyzer analyzer(KoreanTokenizer.DecompoundMode mode) {
        return new Analyzer() {
            @Override protected TokenStreamComponents createComponents(String fieldName) {
                return new TokenStreamComponents(new KoreanTokenizer(AttributeFactory.DEFAULT_ATTRIBUTE_FACTORY,
                        null, mode, false));
            }
        };
    }

    /** 미션 글 낱말: 명사·어근·영어 낱말·숫자(소문자, 중복 없이 나온 순서대로), 복합명사는 쪼갠 것도 함께 */
    public List<String> terms(String text) {
        return terms(documentAnalyzer, text);
    }

    private static List<String> terms(Analyzer analyzer, String text) {
        if (text == null || text.isBlank()) return List.of();
        LinkedHashSet<String> words = new LinkedHashSet<>();
        try (TokenStream tokens = analyzer.tokenStream("text", text)) {
            CharTermAttribute term = tokens.addAttribute(CharTermAttribute.class);
            PartOfSpeechAttribute pos = tokens.addAttribute(PartOfSpeechAttribute.class);
            tokens.reset();
            while (tokens.incrementToken()) {
                String word = term.toString().toLowerCase(Locale.ROOT);
                POS.Tag tag = pos.getLeftPOS();
                boolean keep = switch (tag) {
                    case NNG, NNP, XR -> true;
                    case SL -> word.length() >= 2;
                    case SN -> true;
                    default -> false;
                };
                if (keep && !STOPWORDS.contains(word)) words.add(word);
            }
            tokens.end();
        } catch (IOException e) { throw new IllegalStateException("Nori tokenization failed", e); }
        return List.copyOf(words);
    }

    /** 질의 낱말(복합명사를 쪼개지 않음). 형태소로 남는 게 없으면(동사만 있는 질의 등) 띄어쓰기로 자른 낱말을 그대로 쓴다. */
    public List<String> queryTerms(String normalizedQuery) {
        List<String> terms = terms(queryAnalyzer, normalizedQuery);
        if (!terms.isEmpty()) return terms;
        return Arrays.stream(normalizedQuery.split(" ")).filter(word -> !word.isBlank() && !STOPWORDS.contains(word))
                .distinct().toList();
    }
}
