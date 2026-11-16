package com.sapphire.hawe.search.service;

import com.sapphire.hawe.group.dao.GroupMapper;
import com.sapphire.hawe.group.dao.GroupMemberMapper;
import com.sapphire.hawe.search.dao.MissionSearchMapper;
import com.sapphire.hawe.search.dao.MissionSearchRow;
import com.sapphire.hawe.search.dto.MatchedTerm;
import com.sapphire.hawe.search.dto.MissionSearchItem;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 검색 점수와 AI 확장어 합치기(TODO_2/B2). 권한 SQL 은 SearchControllerTests 에서 실제 DB 로 본다. */
class MissionSearchServiceTests {
    private final MissionSearchMapper mapper = mock(MissionSearchMapper.class);
    private final QueryExpander expander = mock(QueryExpander.class);
    private final MissionSearchService service = new MissionSearchService(mapper, mock(GroupMapper.class),
            mock(GroupMemberMapper.class), new SearchTokenizer(), expander, new ObjectMapper());

    private static MissionSearchRow row(long id, String content, LocalDate date) {
        MissionSearchRow row = new MissionSearchRow();
        row.setId(id);
        row.setGroupId(1L);
        row.setGroupName("가족");
        row.setContent(content);
        row.setTemplate("PHOTO_NOW");
        row.setParams("{}");
        row.setMissionDate(date);
        return row;
    }

    @Test void expansionMatchesRankBelowOriginalMatches() {
        when(mapper.findSearchable(eq(7L), isNull(), anyInt())).thenReturn(List.of(
                row(1, "비 오는 날 창문 찍기", LocalDate.of(2026, 11, 1)),
                row(2, "우산 그리기", LocalDate.of(2026, 11, 10)),
                row(3, "고양이 찍기", LocalDate.of(2026, 11, 11)),
                row(4, "장마철 하늘", LocalDate.of(2026, 11, 12))));
        when(expander.expand(7L, "비 오는 날")).thenReturn(List.of("우산", "장마", "날씨"));

        List<MissionSearchItem> results = service.search(7L, "비 오는 날", null, null);
        // 원 검색어로 맞은 미션(가중 1.0)이 확장어로만 맞은 미션(0.5)보다 위, 같은 점수는 최신 순
        assertEquals(List.of(1L, 4L, 2L), results.stream().map(MissionSearchItem::missionId).toList());
        assertFalse(results.get(0).expanded());
        assertTrue(results.get(0).matchedTerms().stream().noneMatch(MatchedTerm::viaExpansion));
        assertTrue(results.get(1).expanded());
        assertEquals(List.of(new MatchedTerm("장마", true)), results.get(1).matchedTerms());  // "장마철" 안의 "장마"
        assertEquals(List.of(new MatchedTerm("우산", true)), results.get(2).matchedTerms());
    }

    @Test void searchWorksWithoutExpansion() {
        when(mapper.findSearchable(eq(7L), isNull(), anyInt())).thenReturn(List.of(
                row(1, "비 오는 날 창문 찍기", LocalDate.of(2026, 11, 1)), row(2, "우산 그리기", LocalDate.of(2026, 11, 10))));
        when(expander.expand(anyLong(), anyString())).thenReturn(List.of());  // 꺼짐·실패·호출 제한
        List<MissionSearchItem> results = service.search(7L, "비 오는 날", null, null);
        assertEquals(List.of(1L), results.stream().map(MissionSearchItem::missionId).toList());
        assertFalse(results.get(0).expanded());
    }

    @Test void expansionNeverOverridesOriginalWeightOrAddsOneLetterPieces() {
        when(mapper.findSearchable(eq(7L), isNull(), anyInt())).thenReturn(List.of(
                row(1, "비밀 상자", LocalDate.of(2026, 11, 1)), row(2, "비 오는 창문", LocalDate.of(2026, 11, 2))));
        // 확장어에 원 검색어 낱말("비")이나 한 글자가 와도 0.5 로 덮어쓰거나 한 글자로 "비밀"을 끌어오지 않아요
        when(expander.expand(7L, "비 오는 날")).thenReturn(List.of("비", "밀"));
        List<MissionSearchItem> results = service.search(7L, "비 오는 날", null, null);
        assertEquals(List.of(2L), results.stream().map(MissionSearchItem::missionId).toList());
        assertEquals(List.of(new MatchedTerm("비", false)), results.get(0).matchedTerms());
    }

    @Test void invalidRequestDoesNotCallAi() {
        assertThrows(RuntimeException.class, () -> service.search(7L, "코", null, null));
        assertThrows(RuntimeException.class, () -> service.search(7L, "고양이", null, 0));
        verifyNoInteractions(expander);
    }
}
