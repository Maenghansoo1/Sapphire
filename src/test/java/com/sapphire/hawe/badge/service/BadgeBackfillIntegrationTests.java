package com.sapphire.hawe.badge.service;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class BadgeBackfillIntegrationTests {
	@Autowired JdbcTemplate jdbc;
	@Autowired BadgeBackfillService backfill;

	private Long user() {
		String email = UUID.randomUUID() + "@test.com";
		jdbc.update("INSERT INTO users (nickname, email, password_hash, terms_agreed_at, privacy_agreed_at) "
				+ "VALUES ('badge-backfill-test', ?, 'unused', NOW(), NOW())", email);
		return jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
	}

	@Test
	void dryRunAwardAndRepeatKeepOriginalEarnedTimeWithoutNotifications() {
		Long owner = user(), other = user();
		String invite = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
		jdbc.update("INSERT INTO `groups` (name, owner_id, invite_code) VALUES ('Backfill test', ?, ?)", owner, invite);
		Long group = jdbc.queryForObject("SELECT id FROM `groups` WHERE invite_code = ?", Long.class, invite);
		jdbc.update("INSERT INTO group_members (group_id, user_id, role) VALUES (?, ?, 'MEMBER')", group, owner);
		jdbc.update("INSERT INTO group_members (group_id, user_id, role) VALUES (?, ?, 'MEMBER')", group, other);
		LocalDateTime earnedAt = LocalDateTime.of(2025, 1, 5, 9, 30);
		jdbc.update("INSERT INTO missions (group_id, content, category, mission_date, template, params, "
				+ "published_at, deadline_at) VALUES (?, 'test', 'TEST', '2025-01-05', 'PHOTO_NOW', '{}', ?, ?)",
				group, earnedAt.minusMinutes(30), earnedAt.plusHours(1));
		Long mission = jdbc.queryForObject("SELECT id FROM missions WHERE group_id = ?", Long.class, group);
		jdbc.update("INSERT INTO submissions (mission_id, user_id, submitted_at, is_late) VALUES (?, ?, ?, 0)",
				mission, owner, earnedAt);
		var preview = backfill.run(1, owner - 1, true);
		assertEquals(1, preview.awarded().get("FIRST_SUBMISSION"));
		assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM user_badges WHERE user_id = ?", Integer.class, owner));
		var live = backfill.run(1, owner - 1, false);
		assertEquals(1, live.awarded().get("FIRST_SUBMISSION"));
		assertEquals(earnedAt, jdbc.queryForObject("SELECT earned_at FROM user_badges "
				+ "WHERE user_id = ? AND badge_code = 'FIRST_SUBMISSION'", LocalDateTime.class, owner));
		assertEquals("ISSUED_OFFCHAIN", jdbc.queryForObject("SELECT status FROM user_badges "
				+ "WHERE user_id = ? AND badge_code = 'FIRST_SUBMISSION'", String.class, owner));
		assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE to_user_id = ? AND type = 'badge'",
				Integer.class, owner));
		assertTrue(backfill.run(1, owner - 1, false).awarded().isEmpty());
	}
}
