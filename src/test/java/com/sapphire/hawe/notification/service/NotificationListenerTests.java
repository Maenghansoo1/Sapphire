package com.sapphire.hawe.notification.service;

import com.sapphire.hawe.comment.domain.CommentCreatedEvent;
import com.sapphire.hawe.mission.domain.MissionsPublishedEvent;
import com.sapphire.hawe.poke.domain.PokeSentEvent;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.*;

class NotificationListenerTests {
    NotificationService service = mock(NotificationService.class);
    NotificationListener listener = new NotificationListener(service);

    @Test void delegatesAllEventsAndIsolatesFailure() {
        var poke = new PokeSentEvent(1L, 2L, 3L, 4L);
        var comment = new CommentCreatedEvent(6L, 1L, 2L, 5L, 3L, 4L, List.of(4L));
        var topic = new MissionsPublishedEvent(1L, List.of(2L), List.of(3L, 4L));
        doThrow(new IllegalStateException("database down")).when(service).createPoke(poke);
        assertDoesNotThrow(() -> listener.onPoke(poke));
        listener.onComment(comment);
        listener.onMissionsPublished(topic);
        verify(service).createComment(comment);
        verify(service).createTopics(topic);
    }
}
