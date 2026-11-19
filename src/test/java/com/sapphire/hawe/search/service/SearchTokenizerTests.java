package com.sapphire.hawe.search.service;

import com.sapphire.hawe.global.exception.BusinessException;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SearchTokenizerTests {
    private final SearchTokenizer tokenizer = new SearchTokenizer();

    @Test void keepsNounsWithoutParticlesIncludingOneLetterNouns() {
        assertTrue(tokenizer.terms("고양이를 그려 봐").contains("고양이"));
        List<String> rain = tokenizer.terms("비 오는 날");
        assertTrue(rain.contains("비"), rain::toString);
        assertTrue(rain.contains("날"), rain::toString);
        assertFalse(rain.contains("오"), rain::toString);  // 동사는 빼요
    }

    @Test void englishIsLowercasedAndCommonFillerDropped() {
        List<String> terms = tokenizer.terms("오늘 Cat 사진");
        assertTrue(terms.contains("cat"), terms::toString);
        assertTrue(terms.contains("사진"), terms::toString);
        assertFalse(terms.contains("오늘"), terms::toString);
        assertEquals(List.of(), tokenizer.terms("  "));
        assertEquals(List.of(), tokenizer.terms(null));
    }

    @Test void queryIsNotDecompoundedButDocumentsAre() {
        List<String> query = tokenizer.queryTerms("코뿔소");
        assertTrue(query.contains("코뿔소"), query::toString);
        assertFalse(query.contains("소"), query::toString);  // 한 글자 조각으로 엉뚱한 미션이 찾히지 않게
        assertTrue(tokenizer.terms("코뿔소 그리기").contains("코뿔소"));
    }

    @Test void queryFallsBackToWordsWhenNoNouns() {
        assertFalse(tokenizer.queryTerms("고양이 찾기").isEmpty());
        List<String> verbs = tokenizer.queryTerms("달려 보자");  // 명사가 없으면 띄어쓴 낱말 그대로
        assertFalse(verbs.isEmpty());
    }

    @Test void normalizeKeepsLettersDigitsAndChecksLength() {
        assertEquals("고양이 사진", MissionSearchService.normalize("  고양이!!   사진?? "));
        assertEquals("cat 2024", MissionSearchService.normalize("CAT_2024"));
        assertEquals("코뿔", MissionSearchService.normalize("코뿔"));  // 2자 OK
        assertThrows(BusinessException.class, () -> MissionSearchService.normalize("코"));  // 1자
        assertThrows(BusinessException.class, () -> MissionSearchService.normalize("!!!"));  // 남는 글자 없음
        assertThrows(BusinessException.class, () -> MissionSearchService.normalize(null));
        assertEquals(50, MissionSearchService.normalize("가".repeat(50)).length());
        assertThrows(BusinessException.class, () -> MissionSearchService.normalize("가".repeat(51)));
        // SQL 메타문자는 띄어쓰기로 바뀌어 낱말만 남아요
        assertEquals("a or 1 1", MissionSearchService.normalize("a' OR 1=1 --"));
    }
}
