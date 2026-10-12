package com.sapphire.hawe.poke.controller;

import com.sapphire.hawe.global.security.JwtTokenProvider;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PokeControllerTests {
	@Autowired MockMvc mvc;
	@Autowired JdbcTemplate jdbc;
	@Autowired JwtTokenProvider tokens;

	@Test
	void pokeAndFlingAreIndependentAndVisibleOnlyToSender() throws Exception {
		Long sender = user("Sender");
		Long target = user("Target");
		Long group = group(sender);
		member(group, sender, "OWNER");
		member(group, target, "MEMBER");
		Long mission = mission(group);
		String endpoint = "/api/groups/" + group + "/pokes";
		String senderAuth = "Bearer " + tokens.createAccessToken(sender, "Sender");
		String targetAuth = "Bearer " + tokens.createAccessToken(target, "Target");

		mvc.perform(post(endpoint).contentType("application/json")
				.content(request(target, "poke"))).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/missions/" + mission).header("Authorization", senderAuth))
				.andExpect(status().isOk()).andExpect(jsonPath("$.members[1].pokedByMe").isEmpty());
		mvc.perform(post(endpoint).header("Authorization", senderAuth).contentType("application/json")
				.content(request(target, "POKE"))).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
		mvc.perform(post(endpoint).header("Authorization", senderAuth).contentType("application/json")
				.content(request(target, "poke"))).andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").isNumber())
				.andExpect(jsonPath("$.toUserId").value(target))
				.andExpect(jsonPath("$.type").value("poke"))
				.andExpect(jsonPath("$.missionId").value(mission))
				.andExpect(jsonPath("$.sentAt").value(org.hamcrest.Matchers.endsWith("+09:00")));
		mvc.perform(post(endpoint).header("Authorization", senderAuth).contentType("application/json")
				.content(request(target, "poke"))).andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("ALREADY_POKED"));
		mvc.perform(post(endpoint).header("Authorization", senderAuth).contentType("application/json")
				.content(request(target, "fling"))).andExpect(status().isCreated());
		mvc.perform(get("/api/missions/" + mission).header("Authorization", senderAuth))
				.andExpect(status().isOk()).andExpect(jsonPath("$.members[1].pokedByMe[0]").value("poke"))
				.andExpect(jsonPath("$.members[1].pokedByMe[1]").value("fling"));
		mvc.perform(get("/api/missions/" + mission).header("Authorization", targetAuth))
				.andExpect(status().isOk()).andExpect(jsonPath("$.members[1].pokedByMe").isEmpty());

		jdbc.update("INSERT INTO submissions (mission_id, user_id, media_url) VALUES (?, ?, 'test.png')", mission, target);
		mvc.perform(post(endpoint).header("Authorization", senderAuth).contentType("application/json")
				.content(request(target, "poke"))).andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("TARGET_ALREADY_SUBMITTED"));
	}

	private static String request(Long target, String type) {
		return "{\"toUserId\":" + target + ",\"type\":\"" + type + "\"}";
	}

	private Long user(String nickname) {
		String email = UUID.randomUUID() + "@test.com";
		jdbc.update("INSERT INTO users (email, nickname, password_hash, terms_agreed_at, privacy_agreed_at) "
				+ "VALUES (?, ?, 'unused', NOW(), NOW())", email, nickname);
		return jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
	}

	private Long group(Long owner) {
		String code = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
		jdbc.update("INSERT INTO `groups` (name, owner_id, invite_code) VALUES ('Poke test', ?, ?)", owner, code);
		return jdbc.queryForObject("SELECT id FROM `groups` WHERE invite_code = ?", Long.class, code);
	}

	private void member(Long group, Long user, String role) {
		jdbc.update("INSERT INTO group_members (group_id, user_id, role) VALUES (?, ?, ?)", group, user, role);
	}

	private Long mission(Long group) {
		jdbc.update("INSERT INTO missions (group_id, content, category, mission_date, template, params, published_at, deadline_at) "
				+ "VALUES (?, 'Poke test', 'TEST', CURDATE(), 'PHOTO_NOW', '{}', NOW(), NOW() + INTERVAL 1 HOUR)", group);
		return jdbc.queryForObject("SELECT id FROM missions WHERE group_id = ?", Long.class, group);
	}
}
