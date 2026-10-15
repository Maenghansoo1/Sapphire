package com.sapphire.hawe.insight.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.ko.KoreanTokenizer;
import org.apache.lucene.analysis.ko.POS;
import org.apache.lucene.analysis.ko.tokenattributes.PartOfSpeechAttribute;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.util.AttributeFactory;
import org.springframework.stereotype.Component;

@Component
public class NoriKeywordExtractor implements KeywordExtractor {
    private static final Pattern URL = Pattern.compile("https?://\\S+", Pattern.CASE_INSENSITIVE);
    private final Set<String> stopwords;
    private final Analyzer analyzer = new Analyzer() {
        @Override protected TokenStreamComponents createComponents(String fieldName) {
            KoreanTokenizer tokenizer = new KoreanTokenizer(AttributeFactory.DEFAULT_ATTRIBUTE_FACTORY,
                    null, KoreanTokenizer.DecompoundMode.DISCARD, false);
            return new TokenStreamComponents(tokenizer);
        }
    };

    public NoriKeywordExtractor() {
        try (InputStream stream = getClass().getResourceAsStream("/insight/stopwords.txt")) {
            if (stream == null) throw new IllegalStateException("Insight stopwords missing");
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                stopwords = reader.lines().map(String::strip).filter(line -> !line.isEmpty() && !line.startsWith("#"))
                        .collect(java.util.stream.Collectors.toUnmodifiableSet());
            }
        } catch (IOException e) { throw new IllegalStateException("Could not read insight stopwords", e); }
    }

    @Override public List<String> nouns(String text) {
        if (text == null || text.isBlank()) return List.of();
        LinkedHashSet<String> words = new LinkedHashSet<>();
        try (TokenStream tokens = analyzer.tokenStream("text", URL.matcher(text).replaceAll(" "))) {
            CharTermAttribute term = tokens.addAttribute(CharTermAttribute.class);
            PartOfSpeechAttribute pos = tokens.addAttribute(PartOfSpeechAttribute.class);
            tokens.reset();
            while (tokens.incrementToken()) {
                String word = term.toString().toLowerCase(Locale.ROOT);
                boolean noun = pos.getLeftPOS() == POS.Tag.NNG || pos.getLeftPOS() == POS.Tag.NNP;
                boolean english = pos.getLeftPOS() == POS.Tag.SL && word.matches("[a-z]+(?:'[a-z]+)?");
                if ((noun || english) && word.codePointCount(0, word.length()) >= 2
                        && word.codePoints().anyMatch(Character::isLetter) && !stopwords.contains(word)) words.add(word);
            }
            tokens.end();
        } catch (IOException e) { throw new IllegalStateException("Nori tokenization failed", e); }
        return List.copyOf(words);
    }
}
