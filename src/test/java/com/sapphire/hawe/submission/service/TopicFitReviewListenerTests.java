package com.sapphire.hawe.submission.service;

import com.sapphire.hawe.global.config.VisionProperties;
import com.sapphire.hawe.submission.domain.SubmissionCreatedEvent;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.*;

class TopicFitReviewListenerTests {
    TopicFitService reviews = mock(TopicFitService.class);
    SubmissionCreatedEvent event = new SubmissionCreatedEvent(7L, "PHOTO_NOW");

    @Test void disabledReviewDoesNotCallService() {
        var properties = new VisionProperties(false, null, null, null, null, null, null);
        new TopicFitReviewListener(properties, reviews).onSubmissionCreated(event);
        verifyNoInteractions(reviews);
    }

    @Test void enabledReviewCallsServiceAndContainsFailure() {
        var properties = new VisionProperties(true, null, null, null, null, null, null);
        var listener = new TopicFitReviewListener(properties, reviews);
        listener.onSubmissionCreated(event);
        verify(reviews).review(7L);
        doThrow(new IllegalStateException("review failed")).when(reviews).review(7L);
        assertDoesNotThrow(() -> listener.onSubmissionCreated(event));
        verify(reviews, times(2)).review(7L);
    }
}
