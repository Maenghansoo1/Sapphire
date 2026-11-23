package com.sapphire.hawe.badge.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.sapphire.hawe.global.time.SeoulTime;

@Component
public class BadgeSweepJob {
	private static final Logger log = LoggerFactory.getLogger(BadgeSweepJob.class);
	private final BadgeSweepService sweep;

	public BadgeSweepJob(BadgeSweepService sweep) {
		this.sweep = sweep;
	}

	@Scheduled(cron = "${hawe.badge.sweep-cron:0 30 3 * * *}", zone = "Asia/Seoul")
	public void run() {
		try {
			sweep.sweep(SeoulTime.now());
		} catch (Exception error) {
			log.warn("Badge sweep job failed", error);
		}
	}
}
