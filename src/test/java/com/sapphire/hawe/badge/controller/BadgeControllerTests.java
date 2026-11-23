package com.sapphire.hawe.badge.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.sapphire.hawe.global.security.JwtTokenProvider;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BadgeControllerTests {
	@Autowired MockMvc mvc;
	@Autowired JdbcTemplate jdbc;
	@Autowired JwtTokenProvider tokens;
	@Autowired ObjectMapper json;

	private Long user() {
		String email = UUID.randomUUID() + "@test.com";
		jdbc.update("INSERT INTO users (email, nickname, password_hash, terms_agreed_at, privacy_agreed_at) "
				+ "VALUES (?, 'badge-test', 'unused', NOW(), NOW())", email);
		return jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
	}

	@Test
	void badgeEndpointsAreAuthenticatedAndOnlyShowOwnSafeFields() throws Exception {
		Long mine = user(), other = user();
		jdbc.update("INSERT INTO user_badges (user_id, badge_code, status, earned_at, tx_hash) "
				+ "VALUES (?, 'FIRST_SUBMISSION', 'ISSUED_OFFCHAIN', NOW(), 'secret-tx')", mine);
		String auth = "Bearer " + tokens.createAccessToken(mine, "badge-test");
		mvc.perform(get("/api/me/badges")).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/badges/catalog")).andExpect(status().isUnauthorized());
		mvc.perform(put("/api/me/badges/featured").contentType("application/json").content("{\"code\":null}"))
				.andExpect(status().isUnauthorized());
		var response = mvc.perform(get("/api/me/badges").header("Authorization", auth))
				.andExpect(status().isOk()).andExpect(jsonPath("$[0].code").value("FIRST_SUBMISSION"))
				.andExpect(jsonPath("$[0].status").value("RECORDING"))
				.andExpect(jsonPath("$[0].featured").value(true)).andReturn().getResponse().getContentAsString();
		var node = json.readTree(response).get(0);
		assertEquals(java.util.Set.of("code", "name", "description", "tier", "imageKey", "earnedAt",
				"status", "tokenId", "explorerUrl", "featured"),
				java.util.Set.copyOf(node.propertyNames()));
		assertTrue(node.get("tokenId").isNull());
		assertTrue(node.get("explorerUrl").isNull());
		mvc.perform(get("/api/badges/catalog").header("Authorization", auth))
				.andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(13))
				.andExpect(jsonPath("$[0].earned").value(true));
		mvc.perform(put("/api/me/badges/featured").header("Authorization", auth)
				.contentType("application/json").content("{\"code\":\"FIRST_SUBMISSION\"}"))
				.andExpect(status().isNoContent());
		mvc.perform(put("/api/me/badges/featured").header("Authorization", auth)
				.contentType("application/json").content("{\"code\":\"STREAK_7\"}"))
				.andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("BADGE_NOT_EARNED"));
		mvc.perform(put("/api/me/badges/featured").header("Authorization", auth)
				.contentType("application/json").content("{\"code\":null}"))
				.andExpect(status().isNoContent());
		assertNull(jdbc.queryForObject("SELECT featured_badge_code FROM users WHERE id = ?", String.class, mine));
		String otherAuth = "Bearer " + tokens.createAccessToken(other, "badge-test");
		mvc.perform(get("/api/me/badges").header("Authorization", otherAuth))
				.andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
	}
}
