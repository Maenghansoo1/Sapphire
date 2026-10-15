package com.sapphire.hawe.insight.service;

import com.sapphire.hawe.global.time.SeoulTime;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class InsightScheduler {
    private final GroupInsightService service;

    public InsightScheduler(GroupInsightService service) { this.service = service; }

    @Scheduled(cron = "0 10 0 1 * *", zone = "Asia/Seoul")
    public void generatePreviousMonth() { service.generatePreviousMonth(SeoulTime.today()); }
}
