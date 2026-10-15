package com.sapphire.hawe.submission.service;

import com.sapphire.hawe.global.config.VisionProperties;
import com.sapphire.hawe.submission.domain.SubmissionCreatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class TopicFitReviewListener {
    private static final Logger log = LoggerFactory.getLogger(TopicFitReviewListener.class);
    private final VisionProperties properties;
    private final TopicFitService reviews;

    public TopicFitReviewListener(VisionProperties properties, TopicFitService reviews) {
        this.properties = properties;
        this.reviews = reviews;
    }

    @Async("visionExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSubmissionCreated(SubmissionCreatedEvent event) {
        if (!properties.enabled()) return;
        try { reviews.review(event.submissionId()); }
        catch (Exception e) { log.error("Vision review failed for submission {}", event.submissionId(), e); }
    }
}
