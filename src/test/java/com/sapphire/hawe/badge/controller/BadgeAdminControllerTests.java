package com.sapphire.hawe.badge.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.sapphire.hawe.badge.service.BackfillResult;
import com.sapphire.hawe.badge.service.BadgeBackfillService;
import com.sapphire.hawe.global.security.JwtTokenProvider;

@SpringBootTest(properties = "hawe.admin.token=test-admin-token")
@AutoConfigureMockMvc
@Transactional
class BadgeAdminControllerTests {
	private static final String URL = "/internal/badges/backfill";
	@Autowired MockMvc mvc;
	@Autowired JdbcTemplate jdbc;
	@Autowired JwtTokenProvider tokens;
	@MockitoBean BadgeBackfillService backfill;

	@Test
	void wrongOrMissingTokenIsHiddenBeforeParameterParsing() throws Exception {
		mvc.perform(post(URL)).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
		mvc.perform(post(URL + "?batch=bad&dryRun=maybe").header("X-Admin-Token", "wrong"))
			.andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
		mvc.perform(post(URL).header("Authorization", "Bearer invalid"))
			.andExpect(status().isUnauthorized());
		String email = UUID.randomUUID() + "@test.com";
		jdbc.update("INSERT INTO users (nickname, email, password_hash, terms_agreed_at, privacy_agreed_at) "
				+ "VALUES ('badge-admin-test', ?, 'unused', NOW(), NOW())", email);
		Long userId = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
		mvc.perform(post(URL).header("Authorization", "Bearer " + tokens.createAccessToken(userId, "test")))
			.andExpect(status().isNotFound());
		verifyNoInteractions(backfill);
	}

	@Test
	void validatesParametersOnlyAfterCorrectToken() throws Exception {
		for (String query : new String[] {"?batch=0", "?batch=501", "?batch=abc", "?from=-1",
				"?from=abc", "?dryRun=maybe"})
			mvc.perform(post(URL + query).header("X-Admin-Token", "test-admin-token"))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
		verifyNoInteractions(backfill);
	}

	@Test
	void defaultsToDryRunAndReturnsOnlySummaryFields() throws Exception {
		when(backfill.run(200, 0, true)).thenReturn(new BackfillResult(1, 0,
				Map.of("FIRST_SUBMISSION", 1), 42, true, true));
		mvc.perform(post(URL).header("X-Admin-Token", "test-admin-token"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.processedUsers").value(1))
			.andExpect(jsonPath("$.failedUsers").value(0))
			.andExpect(jsonPath("$.awarded.FIRST_SUBMISSION").value(1))
			.andExpect(jsonPath("$.lastUserId").value(42))
			.andExpect(jsonPath("$.done").value(true))
			.andExpect(jsonPath("$.dryRun").value(true))
			.andExpect(jsonPath("$.userIds").doesNotExist());
		when(backfill.run(5, 42, false)).thenReturn(new BackfillResult(0, 0, Map.of(), 42, true, false));
		mvc.perform(post(URL + "?batch=5&from=42&dryRun=false")
				.header("X-Admin-Token", "test-admin-token"))
			.andExpect(status().isOk()).andExpect(jsonPath("$.dryRun").value(false));
	}
}
