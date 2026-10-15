package com.sapphire.hawe.insight.service;

import com.sapphire.hawe.global.config.InsightProperties;
import com.sapphire.hawe.global.exception.BusinessException;
import com.sapphire.hawe.global.storage.FileStorage;
import com.sapphire.hawe.group.dao.GroupMapper;
import com.sapphire.hawe.group.dao.GroupMemberMapper;
import com.sapphire.hawe.group.domain.Group;
import com.sapphire.hawe.insight.dao.InsightMapper;
import com.sapphire.hawe.insight.dao.InsightRow;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GroupInsightServiceTests {
    GroupMapper groups = mock(GroupMapper.class);
    GroupMemberMapper members = mock(GroupMemberMapper.class);
    InsightMapper mapper = mock(InsightMapper.class);
    InsightGenerator generator = mock(InsightGenerator.class);
    GroupInsightService service = new GroupInsightService(groups, members, mapper, generator,
            new InsightProperties(true, Duration.ofMinutes(30), 10, 2), mock(FileStorage.class), new ObjectMapper());
    YearMonth month = YearMonth.now(com.sapphire.hawe.global.time.SeoulTime.ZONE);

    private void member() {
        when(groups.findById(4L)).thenReturn(new Group());
        when(members.existsByGroupIdAndUserId(4L, 2L)).thenReturn(true);
    }

    @Test void firstClaimAndConcurrentRequestSubmitOnce() {
        member();
        when(mapper.tryStart(4L, month.toString())).thenReturn(1);
        assertEquals(HttpStatus.ACCEPTED, service.get(2L, 4L, month).getStatusCode());
        verify(generator).generate(4L, month);
        InsightRow running = new InsightRow(); running.setStatus("GENERATING");
        when(mapper.findCache(4L, month.toString())).thenReturn(running);
        assertEquals(HttpStatus.ACCEPTED, service.get(2L, 4L, month).getStatusCode());
        verify(generator, times(1)).generate(4L, month);
    }

    @Test void expiredResultIsServedStaleAndRegenerated() {
        member();
        InsightRow ready = new InsightRow(); ready.setStatus("READY"); ready.setExpired(true);
        ready.setGeneratedAt(LocalDateTime.now());
        ready.setPayload("{\"empty\":true,\"activityIndex\":null,\"fastest\":null,\"topWords\":[],\"mostReacted\":null}");
        when(mapper.findCache(4L, month.toString())).thenReturn(ready);
        when(mapper.restart(4L, month.toString())).thenReturn(1);
        var result = service.get(2L, 4L, month);
        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertTrue(((com.sapphire.hawe.insight.dto.GroupInsightResponse) result.getBody()).stale());
        verify(generator).generate(4L, month);
    }

    @Test void failedRefreshKeepsPreviousPayloadVisible() {
        member();
        InsightRow failed = new InsightRow(); failed.setStatus("FAILED");
        failed.setGeneratedAt(LocalDateTime.now());
        failed.setPayload("{\"empty\":true,\"activityIndex\":null,\"fastest\":null,\"topWords\":[],\"mostReacted\":null}");
        when(mapper.findCache(4L, month.toString())).thenReturn(failed);
        when(mapper.restart(4L, month.toString())).thenReturn(1);
        var result = service.get(2L, 4L, month);
        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertTrue(((com.sapphire.hawe.insight.dto.GroupInsightResponse) result.getBody()).stale());
        verify(generator).generate(4L, month);
    }

    @Test void checksAccessAndMonthRangeBeforeCache() {
        assertThrows(BusinessException.class, () -> service.get(2L, 4L, month));
        when(groups.findById(4L)).thenReturn(new Group());
        assertThrows(BusinessException.class, () -> service.get(2L, 4L, month));
        when(members.existsByGroupIdAndUserId(4L, 2L)).thenReturn(true);
        assertThrows(BusinessException.class, () -> service.get(2L, 4L, month.plusMonths(1)));
        assertThrows(BusinessException.class, () -> service.get(2L, 4L, month.minusMonths(13)));
        verifyNoInteractions(generator);
    }
}
