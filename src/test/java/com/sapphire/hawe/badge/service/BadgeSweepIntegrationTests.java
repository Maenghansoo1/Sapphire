package com.sapphire.hawe.badge.service;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.sapphire.hawe.badge.dao.UserActivityMapper;
import com.sapphire.hawe.global.config.BadgeProperties;
import com.sapphire.hawe.notification.service.NotificationListener;

@SpringBootTest
@Transactional
class BadgeSweepIntegrationTests {
	@Autowired JdbcTemplate jdbc;
	@Autowired UserActivityMapper activity;
	@Autowired BadgeEvaluationService evaluation;
	@MockitoBean NotificationListener notificationListener;

	private Long user() {
		String email = UUID.randomUUID() + "@test.com";
		jdbc.update("INSERT INTO users (nickname, email, password_hash, terms_agreed_at, privacy_agreed_at) "
				+ "VALUES ('badge-sweep-test', ?, 'unused', NOW(), NOW())", email);
		return jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
	}

	@Test
	void dryRunThenAwardThenRepeatIsIdempotent() {
		Long owner = user(), other = user();
		String invite = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
		jdbc.update("INSERT INTO `groups` (name, owner_id, invite_code) VALUES ('Sweep test', ?, ?)", owner, invite);
		Long group = jdbc.queryForObject("SELECT id FROM `groups` WHERE invite_code = ?", Long.class, invite);
		jdbc.update("INSERT INTO group_members (group_id, user_id, role) VALUES (?, ?, 'MEMBER')", group, owner);
		jdbc.update("INSERT INTO group_members (group_id, user_id, role) VALUES (?, ?, 'MEMBER')", group, other);
		LocalDateTime now = LocalDateTime.of(2099, 4, 4, 3, 30);
		jdbc.update("INSERT INTO missions (group_id, content, category, mission_date, template, params, published_at, deadline_at) "
				+ "VALUES (?, 'test', 'TEST', ?, 'TEXT_FILL_BLANK', '{}', ?, ?)", group, now.toLocalDate(), now.minusHours(1), now.plusHours(1));
		Long mission = jdbc.queryForObject("SELECT id FROM missions WHERE group_id = ?", Long.class, group);
		jdbc.update("INSERT INTO submissions (mission_id, user_id, submitted_at, is_late) VALUES (?, ?, ?, 0)", mission, owner, now.minusMinutes(1));
		var dry = new BadgeSweepService(activity, evaluation,
				new BadgeProperties(true, true, 2, 14, 3, 5, 1));
		assertEquals(1, dry.sweep(now).awardedByCode().get("FIRST_SUBMISSION"));
		assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM user_badges WHERE user_id = ?", Integer.class, owner));
		var live = new BadgeSweepService(activity, evaluation,
				new BadgeProperties(true, false, 2, 14, 3, 5, 1));
		assertEquals(1, live.sweep(now).awardedByCode().get("FIRST_SUBMISSION"));
		assertEquals("ISSUED_OFFCHAIN", jdbc.queryForObject("SELECT status FROM user_badges WHERE user_id = ? AND badge_code = 'FIRST_SUBMISSION'", String.class, owner));
		assertTrue(live.sweep(now).awardedByCode().isEmpty());
		assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM user_badges WHERE user_id = ?", Integer.class, owner));
	}
}
