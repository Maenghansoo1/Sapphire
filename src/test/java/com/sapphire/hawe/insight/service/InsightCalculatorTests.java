package com.sapphire.hawe.insight.service;

import com.sapphire.hawe.insight.dao.InsightRow;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class InsightCalculatorTests {
    private final InsightCalculator calculator = new InsightCalculator(text -> List.of("여행", "여행", "사진"), 10, 2);

    @Test void calculatesActivityWinnersAndDeduplicatedWords() {
        InsightRow first = mission(1, 2), second = mission(2, 2);
        InsightRow a = submission(10, 1, 1, 90, false);
        InsightRow b = submission(11, 1, 2, 120, true);
        InsightRow c = submission(12, 2, 2, 30, false);
        var result = calculator.calculate(List.of(first, second), List.of(a, b, c),
                List.of(count(10, 2), count(12, 1)), List.of(count(12, 1)), List.of());
        assertFalse(result.empty());
        assertEquals(72, result.activityIndex().score()); // .75 participation, 2/3 on time, 4/6 interactions
        assertEquals(0.75, result.activityIndex().participationRate());
        assertEquals(2, result.fastest().userId());
        assertEquals(1, result.fastest().firstCount());
        assertEquals(75, result.fastest().averageResponseSeconds());
        assertEquals(12, result.mostReacted().submissionId()); // 1 reaction + 2 points for comment
        assertEquals(3, result.topWords().getFirst().count());
        assertEquals(2, result.topWords().getFirst().memberCount());
    }

    @Test void emptyAndZeroSubmissionMonthsAreSafe() {
        assertTrue(calculator.calculate(List.of(), List.of(), List.of(), List.of(), List.of()).empty());
        var result = calculator.calculate(List.of(mission(1, 1)), List.of(), List.of(), List.of(), List.of());
        assertFalse(result.empty());
        assertEquals(0, result.activityIndex().score());
        assertNull(result.fastest());
        assertNull(result.mostReacted());
    }

    private static InsightRow mission(long id, int members) {
        InsightRow row = new InsightRow(); row.setId(id); row.setMemberCount(members);
        row.setContent("Question"); row.setPublishedAt(LocalDateTime.of(2026, 1, 1, 12, 0)); return row;
    }
    private static InsightRow submission(long id, long mission, long user, int seconds, boolean late) {
        InsightRow row = new InsightRow(); row.setId(id); row.setMissionId(mission); row.setUserId(user);
        row.setSubmittedAt(LocalDateTime.of(2026, 1, 1, 12, 0).plusSeconds(seconds));
        row.setTemplate("TEXT_REVIEW"); row.setText("여행 사진"); row.setLate(late); return row;
    }
    private static InsightRow count(long submission, int count) {
        InsightRow row = new InsightRow(); row.setSubmissionId(submission); row.setCount(count); return row;
    }
}
