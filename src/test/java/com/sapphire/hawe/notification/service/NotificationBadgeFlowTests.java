package com.sapphire.hawe.notification.service;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;

import com.sapphire.hawe.badge.domain.BadgeAwardedEvent;

@SpringBootTest
class NotificationBadgeFlowTests {
	@Autowired JdbcTemplate jdbc;
	@Autowired ApplicationEventPublisher events;

	@Test
	void eventOutsideTransactionCreatesOneBadgeNotification() {
		String email = UUID.randomUUID() + "@test.com";
		jdbc.update("INSERT INTO users (email, nickname, password_hash, terms_agreed_at, privacy_agreed_at) "
				+ "VALUES (?, 'badge-notification-test', 'unused', NOW(), NOW())", email);
		Long userId = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
		try {
			events.publishEvent(new BadgeAwardedEvent(userId, "FIRST_SUBMISSION"));
			assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM notifications "
					+ "WHERE to_user_id = ? AND type = 'badge' AND payload = 'FIRST_SUBMISSION'", Integer.class, userId));
		} finally {
			jdbc.update("DELETE FROM notifications WHERE to_user_id = ?", userId);
			jdbc.update("DELETE FROM users WHERE id = ?", userId);
		}
	}
}
