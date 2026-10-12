package com.sapphire.hawe.mission.service;

import com.sapphire.hawe.global.time.SeoulTime;
import com.sapphire.hawe.group.domain.GroupCreatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class MissionPublishListener {
    private static final Logger log = LoggerFactory.getLogger(MissionPublishListener.class);
    private final MissionPublisher publisher;

    public MissionPublishListener(MissionPublisher publisher) {
        this.publisher = publisher;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onGroupCreated(GroupCreatedEvent event) {
        try { publisher.publishIfDue(event.groupId(), SeoulTime.now()); }
        catch (Exception e) { log.error("First mission publish failed for group {}", event.groupId(), e); }
    }
}
