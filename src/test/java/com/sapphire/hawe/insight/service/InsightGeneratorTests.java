package com.sapphire.hawe.insight.service;

import com.sapphire.hawe.global.config.InsightProperties;
import com.sapphire.hawe.insight.dao.InsightMapper;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class InsightGeneratorTests {
    InsightMapper mapper = mock(InsightMapper.class);
    InsightGenerator generator = new InsightGenerator(mapper,
            new InsightProperties(true, Duration.ofMinutes(30), 10, 2), text -> List.of(), new ObjectMapper());
    YearMonth month = YearMonth.of(2026, 9);

    @Test void completesEmptyMonth() {
        when(mapper.findClosedMissions(4L, month.atDay(1), month.atEndOfMonth())).thenReturn(List.of());
        generator.generate(4L, month);
        verify(mapper).hasCurrentMissionInMonth(4L, month.atDay(1), month.atEndOfMonth());
        verify(mapper).complete(eq(4L), eq("2026-09"), contains("\"empty\":true"),
                eq(LocalDateTime.of(2026, 10, 3, 0, 0)), eq(false), eq(1800L));
    }

    @Test void marksFailureAndAllowsDeletedRow() {
        when(mapper.findClosedMissions(4L, month.atDay(1), month.atEndOfMonth()))
                .thenThrow(new IllegalStateException("DB failed"));
        when(mapper.fail(4L, "2026-09")).thenReturn(0);
        generator.generate(4L, month);
        verify(mapper).fail(4L, "2026-09");
        verify(mapper, never()).complete(any(), any(), any(), any(), anyBoolean(), anyLong());
    }
}
