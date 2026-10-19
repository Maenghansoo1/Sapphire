package com.sapphire.hawe.mascot.service;

import com.sapphire.hawe.submission.domain.SubmissionCreatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class MascotListener {
    private static final Logger log = LoggerFactory.getLogger(MascotListener.class);
    private final MascotService mascot;

    public MascotListener(MascotService mascot) { this.mascot = mascot; }

    @Async("visionExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSubmissionCreated(SubmissionCreatedEvent event) {
        if (!switch (event.template()) {
            case "PHOTO_NOW", "PHOTO_COLOR", "DRAW_SKETCH", "DRAW_A_TO_B" -> true;
            default -> false;
        }) return;
        try { mascot.reactRandomly(event.submissionId()); }
        catch (Exception error) { log.warn("Mascot reaction failed for submission {}", event.submissionId(), error); }
        if (event.template().startsWith("DRAW") && event.aiGuess() != null) {
            try { mascot.commentDrawGuess(event.submissionId(), event.aiGuess()); }
            catch (Exception error) { log.warn("Mascot comment failed for submission {}", event.submissionId(), error); }
        }
    }
}
