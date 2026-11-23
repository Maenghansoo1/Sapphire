package com.sapphire.hawe.withdraw.dao;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class WithdrawBadgeMapperTests {
	@Autowired JdbcTemplate jdbc;
	@Autowired WithdrawMapper mapper;

	@Test
	void badgeRowsCanBeDeletedBeforeUser() {
		String email = UUID.randomUUID() + "@test.com";
		jdbc.update("INSERT INTO users (nickname, email, password_hash, terms_agreed_at, privacy_agreed_at) VALUES ('badge-test', ?, 'unused', NOW(), NOW())", email);
		Long userId = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
		jdbc.update("INSERT INTO user_badges (user_id, badge_code, status, earned_at) VALUES (?, 'FIRST_SUBMISSION', 'ISSUED_OFFCHAIN', NOW())", userId);
		mapper.deleteMyBadges(userId);
		assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM user_badges WHERE user_id = ?", Integer.class, userId));
		assertEquals(1, mapper.deleteUserById(userId));
	}
}
