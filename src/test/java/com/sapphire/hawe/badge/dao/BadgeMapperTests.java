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

import com.sapphire.hawe.badge.domain.BadgeStatus;

@SpringBootTest
@Transactional
class BadgeMapperTests {
	@Autowired BadgeMapper mapper;
	@Autowired JdbcTemplate jdbc;

	private Long user() {
		String email = UUID.randomUUID() + "@test.com";
		jdbc.update("INSERT INTO users (nickname, email, password_hash, terms_agreed_at, privacy_agreed_at) VALUES (?, ?, 'unused', NOW(), NOW())", "badge-test", email);
		return jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
	}

	@Test
	void insertIsIdempotentAndQueriesAreScopedAndOrdered() {
		Long first = user(), second = user();
		var old = LocalDateTime.of(2026, 1, 1, 0, 0);
		var recent = old.plusDays(1);
		assertEquals(1, mapper.insertIgnore(first, "FIRST_SUBMISSION", BadgeStatus.ISSUED_OFFCHAIN, old));
		assertEquals(0, mapper.insertIgnore(first, "FIRST_SUBMISSION", BadgeStatus.EARNED, recent));
		assertEquals(1, mapper.insertIgnore(first, "STREAK_7", BadgeStatus.EARNED, recent));
		assertEquals(1, mapper.insertIgnore(first, "SUBMISSIONS_10", BadgeStatus.EARNED, recent));
		assertEquals(1, mapper.insertIgnore(second, "FIRST_SUBMISSION", BadgeStatus.EARNED, recent));
		assertEquals(1, mapper.countByUser(second));
		assertEquals(3, mapper.countByUser(first));
		var rows = mapper.findByUserId(first);
		assertEquals(List.of("SUBMISSIONS_10", "STREAK_7", "FIRST_SUBMISSION"),
				rows.stream().map(b -> b.badgeCode()).toList());
		assertEquals(BadgeStatus.ISSUED_OFFCHAIN, rows.getLast().status());
		assertEquals(old, mapper.findByUserIdAndCode(first, "FIRST_SUBMISSION").earnedAt());
		assertNull(mapper.findByUserIdAndCode(first, "MISSING"));
		assertEquals(1, mapper.findByUserId(second).size());
	}

	@Test
	void featuredChoiceAndBatchRowsIncludeUsersWithNoBadges() {
		Long first = user(), second = user(), third = user();
		var time = LocalDateTime.of(2026, 1, 1, 0, 0);
		mapper.insertIgnore(first, "STREAK_7", BadgeStatus.EARNED, time);
		mapper.insertIgnore(first, "SUBMISSIONS_10", BadgeStatus.EARNED, time.plusDays(1));
		mapper.insertIgnore(second, "FIRST_SUBMISSION", BadgeStatus.ISSUED_OFFCHAIN, time);
		assertEquals(1, mapper.updateFeaturedBadge(first, "STREAK_7"));
		assertEquals("STREAK_7", mapper.findFeaturedBadgeCode(first));
		var rows = mapper.findFeaturedBadgeRowsByUserIds(List.of(first, second, third));
		assertEquals(4, rows.size());
		assertEquals(2, rows.stream().filter(r -> r.userId().equals(first)).count());
		assertEquals("STREAK_7", rows.getFirst().featuredBadgeCode());
		assertNull(rows.getLast().badgeCode());
		assertEquals(third, rows.getLast().userId());
		assertEquals(List.of(), mapper.findFeaturedBadgeRowsByUserIds(List.of()));
		assertEquals(1, mapper.updateFeaturedBadge(first, null));
		assertNull(mapper.findFeaturedBadgeCode(first));
	}
}
