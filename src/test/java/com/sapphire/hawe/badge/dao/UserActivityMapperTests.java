package com.sapphire.hawe.badge.dao;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class UserActivityMapperTests {
	@Autowired JdbcTemplate jdbc;
	@Autowired UserActivityMapper activity;

	private Long user() {
		String email = UUID.randomUUID() + "@test.com";
		jdbc.update("INSERT INTO users (nickname, email, password_hash, terms_agreed_at, privacy_agreed_at) VALUES ('badge-test', ?, 'unused', NOW(), NOW())", email);
		return jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
	}

	private Long group(Long owner) {
		String invite = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
		jdbc.update("INSERT INTO `groups` (name, owner_id, invite_code) VALUES ('Badge test', ?, ?)", owner, invite);
		return jdbc.queryForObject("SELECT id FROM `groups` WHERE invite_code = ?", Long.class, invite);
	}

	private void member(Long group, Long user) {
		jdbc.update("INSERT INTO group_members (group_id, user_id, role) VALUES (?, ?, 'MEMBER')", group, user);
	}

	private Long mission(Long group, String template, LocalDateTime published, int slot) {
		jdbc.update("INSERT INTO missions (group_id, content, category, mission_date, template, params, slot_no, published_at, deadline_at) "
				+ "VALUES (?, 'test', 'TEST', '2026-01-01', ?, '{}', ?, ?, ?)",
				group, template, slot, published, published.plusDays(1));
		return jdbc.queryForObject("SELECT id FROM missions WHERE group_id = ? AND slot_no = ?", Long.class, group, slot);
	}

	private Long submit(Long mission, Long user, LocalDateTime when, boolean late) {
		jdbc.update("INSERT INTO submissions (mission_id, user_id, submitted_at, is_late) VALUES (?, ?, ?, ?)", mission, user, when, late);
		return jdbc.queryForObject("SELECT id FROM submissions WHERE mission_id = ? AND user_id = ?", Long.class, mission, user);
	}

	@Test
	void includesOnlyCurrentTwoMemberGroupsAndMapsOrderedRows() {
		Long me = user(), other = user(), third = user();
		Long shared = group(me), solo = group(me);
		member(shared, me); member(shared, other); member(shared, third);
		member(solo, me);
		var published = LocalDateTime.of(2026, 1, 1, 9, 0);
		Long laterMission = mission(shared, "TEXT_FILL_BLANK", published, 1);
		Long earlierMission = mission(shared, "PHOTO_NOW", published.minusHours(1), 2);
		Long soloMission = mission(solo, "PHOTO_COLOR", published, 1);
		Long laterId = submit(laterMission, me, published.plusHours(2), true);
		Long earlierId = submit(earlierMission, me, published.plusHours(1), false);
		submit(soloMission, me, published, false);
		submit(laterMission, other, published.plusHours(2), false);
		var rows = activity.findValidSubmissionsByUserId(me);
		assertEquals(List.of(earlierId, laterId), rows.stream().map(r -> r.submissionId()).toList());
		assertFalse(rows.getFirst().late());
		assertTrue(rows.getLast().late());
		assertEquals("PHOTO_NOW", rows.getFirst().template());
		assertEquals(published.minusHours(1), rows.getFirst().publishedAt());
		jdbc.update("DELETE FROM group_members WHERE group_id = ? AND user_id = ?", shared, me);
		assertEquals(2, activity.findValidSubmissionsByUserId(me).size());
	}
}
