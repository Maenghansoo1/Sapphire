package com.sapphire.hawe.insight.service;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class KeywordExtractorTests {
    private final NoriKeywordExtractor extractor = new NoriKeywordExtractor();

    @Test void normalizesAndFiltersWords() {
        List<String> words = extractor.nouns("여행 여행 여행에서 오늘 123 https://example.com/path 😊 JAVA java");
        assertEquals(1, words.stream().filter("여행"::equals).count());
        assertTrue(extractor.nouns("여행에서").contains("여행"));
        assertTrue(words.contains("java"));
        assertFalse(words.contains("오늘"));
        assertFalse(words.contains("123"));
        assertFalse(words.contains("example"));
    }

    @Test void tokenizesKoreanSamples() {
        for (String sentence : List.of("퇴근길에서 꽃을 봤다", "아침에 커피를 마셨다", "주말에 여행을 갔다",
                "바다에서 사진을 찍었다", "친구들과 영화를 봤다", "공원에서 강아지를 만났다",
                "학교에서 책을 읽었다", "비오는 날 우산을 썼다", "저녁에 음악을 들었다",
                "산책하면서 나무를 봤다")) assertFalse(extractor.nouns(sentence).isEmpty(), sentence);
    }
}
