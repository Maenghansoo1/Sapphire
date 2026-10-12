package com.sapphire.hawe.mission.service;

import com.sapphire.hawe.group.dao.GroupMapper;
import com.sapphire.hawe.group.domain.GroupCreatedEvent;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MissionTriggerTests {
    @Test
    void schedulerContinuesAfterOneGroupFails() {
        GroupMapper groups = mock(GroupMapper.class);
        MissionPublisher publisher = mock(MissionPublisher.class);
        when(groups.findAllIds()).thenReturn(List.of(1L, 2L));
        doThrow(new IllegalStateException("temporary")).when(publisher).publishIfDue(eq(1L), any(LocalDateTime.class));

        assertDoesNotThrow(() -> new MissionScheduler(groups, publisher).publishDueMissions());

        verify(publisher).publishIfDue(eq(2L), any(LocalDateTime.class));
    }

    @Test
    void afterCommitListenerSwallowsPublishFailure() {
        MissionPublisher publisher = mock(MissionPublisher.class);
        doThrow(new IllegalStateException("temporary")).when(publisher).publishIfDue(eq(1L), any(LocalDateTime.class));

        assertDoesNotThrow(() -> new MissionPublishListener(publisher).onGroupCreated(new GroupCreatedEvent(1L)));
    }
}
