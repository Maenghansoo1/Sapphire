package com.sapphire.hawe.badge.dao;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class UserActivityMapperTests {
	@Autowired JdbcTemplate jdbc;
	@Autowired UserActivityMapper activity;
	@Autowired SqlSessionTemplate sqlSession;

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

	@Test
	void scheduledActivityQueriesFilterLateSoloAiAndReviewStatus() {
		Long me = user(), other = user(), ai = user();
		jdbc.update("UPDATE users SET is_ai = 1 WHERE id = ?", ai);
		Long shared = group(me), solo = group(me);
		member(shared, me); member(shared, other); member(shared, ai); member(solo, me);
		var published = LocalDateTime.of(2026, 1, 1, 9, 0);
		Long first = submit(mission(shared, "PHOTO_NOW", published, 1), me, published.plusMinutes(10), false);
		Long late = submit(mission(shared, "PHOTO_COLOR", published, 2), me, published.plusMinutes(20), true);
		Long soloSubmission = submit(mission(solo, "PHOTO_NOW", published, 1), me, published.plusMinutes(5), false);
		assertEquals(1, activity.findOnTimeSubmissionDays(me).size());
		assertEquals(published.plusMinutes(10), activity.findOnTimeSubmissionDays(me).getFirst().firstOnTimeSubmittedAt());
		assertTrue(activity.findOnTimeSubmissionDays(other).isEmpty());
		jdbc.update("INSERT INTO submission_ai_reviews (submission_id, kind, status, badge, updated_at) VALUES (?, 'TOPIC_FIT', 'DONE', 'PERFECT_FIT', ?)", first, published.plusHours(1));
		jdbc.update("INSERT INTO submission_ai_reviews (submission_id, kind, status, badge) VALUES (?, 'TOPIC_FIT', 'FAILED', 'PERFECT_FIT')", late);
		jdbc.update("INSERT INTO submission_ai_reviews (submission_id, kind, status, badge) VALUES (?, 'TOPIC_FIT', 'DONE', 'PERFECT_FIT')", soloSubmission);
		assertEquals(List.of(published.plusHours(1)), activity.findPerfectFitTimes(me));
		jdbc.update("INSERT INTO reactions (submission_id, user_id, sticker_type) VALUES (?, ?, 'heart')", first, other);
		jdbc.update("INSERT INTO reactions (submission_id, user_id, sticker_type) VALUES (?, ?, 'heart')", first, ai);
		jdbc.update("INSERT INTO reactions (submission_id, user_id, sticker_type) VALUES (?, ?, 'heart')", first, me);
		jdbc.update("INSERT INTO comments (submission_id, user_id, content) VALUES (?, ?, 'ok')", first, other);
		jdbc.update("INSERT INTO comments (submission_id, user_id, content) VALUES (?, ?, 'ai')", first, ai);
		jdbc.update("INSERT INTO comments (submission_id, user_id, content) VALUES (?, ?, 'own')", first, me);
		assertEquals(2, activity.findCrowdMaxInteractions(me));
		assertEquals(0, activity.findCrowdMaxInteractions(other));
	}

	@Test
	void groupMonthAndCandidateQueriesRunAgainstMariaDb() {
		Long me = user(), other = user(), soloOwner = user();
		Long shared = group(me), solo = group(soloOwner);
		member(shared, me); member(shared, other); member(solo, soloOwner);
		jdbc.update("UPDATE `groups` SET relation_type = 'COUPLE', start_date = '2024-02-29' WHERE id = ?", shared);
		jdbc.update("UPDATE `groups` SET created_at = '2026-01-01 08:00:00' WHERE id = ?", solo);
		jdbc.update("UPDATE group_members SET joined_at = '2025-03-01 09:00:00' WHERE group_id = ? AND user_id = ?", shared, me);
		assertEquals(List.of(new com.sapphire.hawe.badge.domain.GroupBase(shared,
				java.time.LocalDate.of(2024, 2, 29), java.time.LocalDate.of(2025, 3, 1), true)),
				activity.findMemberGroupBases(me));
		assertTrue(activity.findMemberGroupBases(soloOwner).isEmpty());
		var published = LocalDateTime.of(2026, 1, 1, 9, 0);
		Long mission = mission(shared, "TEXT_FILL_BLANK", published, 1);
		submit(mission, me, published.plusMinutes(10), false);
		var open = activity.findPerfectMonthRows(me, published.toLocalDate(), published.toLocalDate());
		assertEquals(1, open.size());
		assertEquals(1, open.getFirst().total());
		assertEquals(1, open.getFirst().submitted());
		assertEquals(1, open.getFirst().openCount());
		assertEquals(2, open.getFirst().memberCount());
		jdbc.update("INSERT INTO missions (group_id, content, category, mission_date, template, params, slot_no, published_at, deadline_at) "
				+ "VALUES (?, 'next', 'TEST', '2026-01-03', 'PHOTO_NOW', '{}', 1, '2026-01-03 09:00:00', '2026-01-03 23:00:00')", shared);
		sqlSession.clearCache();
		assertEquals(0, activity.findPerfectMonthRows(me, published.toLocalDate(), published.toLocalDate()).getFirst().openCount());
		assertTrue(activity.findRecentSubmitterIds(published).contains(me));
		assertTrue(activity.findSubmitterIdsInRange(published.toLocalDate(), published.toLocalDate()).contains(me));
		var milestoneUsers = activity.findMilestoneUserIds(java.time.LocalDate.of(2025, 2, 28),
				java.time.LocalDate.of(2025, 2, 28));
		assertTrue(milestoneUsers.containsAll(List.of(me, other)));
		assertEquals(milestoneUsers.size(), new java.util.HashSet<>(milestoneUsers).size());
		jdbc.update("DELETE FROM group_members WHERE group_id = ? AND user_id = ?", shared, me);
		sqlSession.clearCache();
		assertTrue(activity.findMemberGroupBases(me).isEmpty());
	}

	@Test
	void memberGroupBaseIncludesJoinDateForCreationDateGroups() {
		Long me = user(), other = user();
		Long shared = group(me);
		member(shared, me); member(shared, other);
		jdbc.update("UPDATE `groups` SET created_at = '2025-01-01 08:00:00' WHERE id = ?", shared);
		jdbc.update("UPDATE group_members SET joined_at = '2026-01-02 09:00:00' WHERE group_id = ? AND user_id = ?", shared, me);
		assertEquals(List.of(new com.sapphire.hawe.badge.domain.GroupBase(shared,
				java.time.LocalDate.of(2025, 1, 1), java.time.LocalDate.of(2026, 1, 2), false)),
				activity.findMemberGroupBases(me));
	}
}
