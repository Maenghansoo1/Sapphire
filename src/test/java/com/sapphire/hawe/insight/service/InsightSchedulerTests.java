package com.sapphire.hawe.insight.service;

import com.sapphire.hawe.global.config.InsightProperties;
import com.sapphire.hawe.insight.dao.InsightMapper;
import com.sapphire.hawe.insight.dao.InsightRow;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;

class InsightSchedulerTests {
    @Test void startsOnlyGroupsWithoutReadyCacheAndContinuesAfterError() {
        InsightMapper mapper = mock(InsightMapper.class);
        InsightGenerator generator = mock(InsightGenerator.class);
        var service = new GroupInsightService(null, null, mapper, generator,
                new InsightProperties(true, Duration.ofMinutes(30), 10, 2), null, null);
        LocalDate today = LocalDate.of(2026, 10, 1);
        when(mapper.findGroupIdsWithMissions(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)))
                .thenReturn(List.of(1L, 2L, 3L));
        when(mapper.findCache(1L, "2026-09")).thenThrow(new IllegalStateException("one group failed"));
        InsightRow ready = new InsightRow(); ready.setStatus("READY");
        when(mapper.findCache(2L, "2026-09")).thenReturn(ready);
        when(mapper.tryStart(3L, "2026-09")).thenReturn(1);
        service.generatePreviousMonth(today);
        verify(generator).generate(3L, java.time.YearMonth.of(2026, 9));
        verify(generator, times(1)).generate(any(), any());
    }

    @Test void disabledSkipsDatabase() {
        InsightMapper mapper = mock(InsightMapper.class);
        var service = new GroupInsightService(null, null, mapper, mock(InsightGenerator.class),
                new InsightProperties(false, Duration.ofMinutes(30), 10, 2), null, null);
        service.generatePreviousMonth(LocalDate.of(2026, 10, 1));
        verifyNoInteractions(mapper);
    }
}
