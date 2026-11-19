package com.sapphire.hawe.search.domain;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MissionSearchDomainTests {
    @Test void searchTextHasOnlyServerMadeValues() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("subject", "해바라기");
        params.put("targetUserId", 42);
        params.put("seconds", 30);
        params.put("text", "사용자가 쓴 글");  // 서버 생성 값 목록에 없는 키는 넣지 않는다
        String text = MissionSearchText.of("[대상]의 등대 찍기", "PHOTO_NOW", params);
        assertTrue(text.contains("등대"));
        assertTrue(text.contains("해바라기"));
        assertTrue(text.contains("지금 이 순간"));  // 틀 한국어 이름
        assertFalse(text.contains("[대상]"));
        assertFalse(text.contains("42"));
        assertFalse(text.contains("30"));
        assertFalse(text.contains("사용자가 쓴 글"));
    }

    @Test void brokenParamsOrUnknownTemplateStillGiveContent() {
        assertEquals("코뿔소 그리기", MissionSearchText.of("코뿔소 그리기", "NO_SUCH_TEMPLATE", null));
        assertEquals("", MissionSearchText.of(null, null, null));
    }

    @Test void displayHidesTargetNickname() {
        assertEquals("친구를 그려 봐", MissionSearchText.display("[대상]을 그려 봐"));
        assertEquals("친구에게 편지", MissionSearchText.display("[대상]에게 편지"));
        assertEquals("고양이 찍기", MissionSearchText.display("고양이 찍기"));
    }

    @Test void flattenCollapsesSpacesAndLowercases() {
        assertEquals("cat 고양이 찍기", MissionSearchText.flatten("  Cat\n고양이   찍기 "));
    }

    @Test void matchesTokensAndLongerSubstrings() {
        Map<String, Double> query = new LinkedHashMap<>();
        query.put("케이크", 1.0);
        query.put("비", 1.0);
        query.put("우산", 0.5);
        // "케이크"는 "생일케이크" 안에 있어서 맞음, 한 글자 "비"는 "비밀" 안에 있어도 낱말이 아니라 안 맞음
        TermMatcher.Match match = TermMatcher.match(query, Set.of("생일케이크", "비밀"), "생일케이크 비밀");
        assertEquals(1.0, match.score(), 1e-9);
        assertEquals(List.of("케이크"), match.matchedTerms());
        // 낱말이 같으면 한 글자도 맞음, 가중치는 더해짐, 순서는 질의 순서
        TermMatcher.Match rain = TermMatcher.match(query, Set.of("비", "우산"), "비 우산");
        assertEquals(1.5, rain.score(), 1e-9);
        assertEquals(List.of("비", "우산"), rain.matchedTerms());
        assertFalse(TermMatcher.match(query, Set.of("고양이"), "고양이").matched());
    }

    @Test void ignoresBlankOrNonPositiveWeights() {
        Map<String, Double> query = new LinkedHashMap<>();
        query.put("등대", 0.0);
        query.put(" ", 1.0);
        query.put("바다", -1.0);
        query.put("코뿔소", null);
        assertFalse(TermMatcher.match(query, Set.of("등대", "바다", "코뿔소"), "등대 바다 코뿔소").matched());
    }
}
