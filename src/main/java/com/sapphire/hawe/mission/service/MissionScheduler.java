package com.sapphire.hawe.mission.service;

import com.sapphire.hawe.global.time.SeoulTime;
import com.sapphire.hawe.group.dao.GroupMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class MissionScheduler {
    private static final Logger log = LoggerFactory.getLogger(MissionScheduler.class);
    private final GroupMapper groups;
    private final MissionPublisher publisher;

    public MissionScheduler(GroupMapper groups, MissionPublisher publisher) {
        this.groups = groups;
        this.publisher = publisher;
    }

    @Scheduled(cron = "0 * * * * *", zone = "Asia/Seoul")
    public void publishDueMissions() {
        for (Long groupId : groups.findAllIds()) {
            try { publisher.publishIfDue(groupId, SeoulTime.now()); }
            catch (Exception e) { log.error("Mission publish failed for group {}", groupId, e); }
        }
    }
}
