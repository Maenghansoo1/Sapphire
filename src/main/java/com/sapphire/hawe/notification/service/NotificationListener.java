package com.sapphire.hawe.notification.service;

import com.sapphire.hawe.badge.domain.BadgeAwardedEvent;
import com.sapphire.hawe.comment.domain.CommentCreatedEvent;
import com.sapphire.hawe.mission.domain.MissionsPublishedEvent;
import com.sapphire.hawe.poke.domain.PokeSentEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.context.event.EventListener;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class NotificationListener {
    private static final Logger log = LoggerFactory.getLogger(NotificationListener.class);
    private final NotificationService notifications;

    public NotificationListener(NotificationService notifications) {
        this.notifications = notifications;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPoke(PokeSentEvent event) {
        try { notifications.createPoke(event); }
        catch (Exception error) { log.error("Poke notification failed for mission {}", event.missionId(), error); }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onComment(CommentCreatedEvent event) {
        try { notifications.createComment(event); }
        catch (Exception error) { log.error("Comment notification failed for comment {}", event.commentId(), error); }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMissionsPublished(MissionsPublishedEvent event) {
        try { notifications.createTopics(event); }
        catch (Exception error) { log.error("Topic notification failed for group {}", event.groupId(), error); }
    }

    // BadgeAwardedEvent is published outside a transaction, so a transactional listener would not run.
    @EventListener
    public void onBadgeAwarded(BadgeAwardedEvent event) {
        try { notifications.createBadge(event); }
        catch (Exception error) { log.error("Badge notification failed for user {}", event.userId(), error); }
    }
}
