package com.sapphire.hawe.mascot.service;

import com.sapphire.hawe.submission.domain.SubmissionCreatedEvent;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;

class MascotListenerTests {
    MascotService mascot = mock(MascotService.class);
    MascotListener listener = new MascotListener(mascot);

    @Test void photosReactAndDrawingsAlsoCommentWhenGuessExists() {
        listener.onSubmissionCreated(new SubmissionCreatedEvent(1L, "PHOTO_NOW", null));
        listener.onSubmissionCreated(new SubmissionCreatedEvent(2L, "DRAW_SKETCH", "[]"));
        listener.onSubmissionCreated(new SubmissionCreatedEvent(3L, "DRAW_A_TO_B", null));
        listener.onSubmissionCreated(new SubmissionCreatedEvent(4L, "PHOTO_GALLERY", null));
        verify(mascot).reactRandomly(1L);
        verify(mascot).reactRandomly(2L);
        verify(mascot).reactRandomly(3L);
        verify(mascot).commentDrawGuess(2L, "[]");
        verifyNoMoreInteractions(mascot);
    }

    @Test void reactionFailureDoesNotPreventDrawingComment() {
        doThrow(new IllegalStateException()).when(mascot).reactRandomly(2L);
        listener.onSubmissionCreated(new SubmissionCreatedEvent(2L, "DRAW_SKETCH", "[]"));
        verify(mascot).commentDrawGuess(2L, "[]");
    }
}
