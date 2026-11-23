package com.sapphire.hawe.badge.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "hawe.admin.token=")
@AutoConfigureMockMvc
class BadgeAdminDisabledTests {
	@Autowired MockMvc mvc;

	@Test
	void unsetTokenAlwaysHidesEndpoint() throws Exception {
		mvc.perform(post("/internal/badges/backfill?batch=bad").header("X-Admin-Token", "anything"))
			.andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
	}
}
