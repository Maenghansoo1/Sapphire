package com.sapphire.hawe.insight.service;

import com.sapphire.hawe.insight.dto.GroupInsightResponse;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HighlightComposerTests {
    @Test void composesOnlyAvailablePositiveFactsAndTruncatesMission() {
        var composer = new HighlightComposer();
        assertTrue(composer.compose(YearMonth.of(2026, 1), null, null, List.of(), null).isEmpty());
        var most = new GroupInsightResponse.MostReacted(1L, 2L, "가나다라마바사아자차카타파하가나다라마바사아자차", "TEXT_REVIEW", 3L,
                "Kim", 2, 1, "heart");
        var lines = composer.compose(YearMonth.of(2026, 1), null, null, List.of(), most);
        assertEquals(1, lines.size());
        assertTrue(lines.getFirst().contains("…"));
        assertFalse(lines.getFirst().contains("차카타파하가나다라마바사아자차"));
    }
}
