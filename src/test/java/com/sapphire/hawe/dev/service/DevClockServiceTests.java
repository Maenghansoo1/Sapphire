package com.sapphire.hawe.dev.service;

import com.sapphire.hawe.dev.dto.DevDtos.ClockRequest;
import com.sapphire.hawe.global.time.SeoulTime;
import com.sapphire.hawe.mission.service.MissionScheduler;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DevClockServiceTests {
    @AfterEach void reset() { SeoulTime.reset(); }

    @Test void relativeMovePublishesAndResetRestoresRealTime() {
        MissionScheduler scheduler = mock(MissionScheduler.class);
        DevClockService service = new DevClockService(scheduler);
        var result = service.set(new ClockRequest(null, 86400L, null));
        assertEquals(86400L, result.offsetSeconds());
        assertTrue(result.offsetActive());
        verify(scheduler).publishDueMissions();
        assertTrue(Duration.between(result.realNow().plusDays(1), result.serverNow()).abs().toSeconds() < 2);
        assertEquals(0, service.reset().offsetSeconds());
    }

    @Test void absolutePastMoveWarnsAndCanSkipPublishing() {
        MissionScheduler scheduler = mock(MissionScheduler.class);
        DevClockService service = new DevClockService(scheduler);
        String past = SeoulTime.realNow().minusDays(2).withNano(0).toString();
        var result = service.set(new ClockRequest(past, null, false));
        assertNotNull(result.warning());
        assertTrue(result.offsetSeconds() < 0);
        verifyNoInteractions(scheduler);
    }
}
