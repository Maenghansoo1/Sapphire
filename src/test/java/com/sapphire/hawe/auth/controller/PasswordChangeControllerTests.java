package com.sapphire.hawe.auth.controller;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class PasswordChangeControllerTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;

    @Test
    void passwordChangeInvalidatesOldTokensAndReturnsWorkingNewTokens() throws Exception {
        String email = UUID.randomUUID() + "@test.com";
        Long user = null;
        try {
            mvc.perform(post("/api/auth/register").contentType("application/json")
                    .content("{\"email\":\"" + email + "\",\"password\":\"abcd1234\",\"nickname\":\"Tester\","
                            + "\"agreeTerms\":true,\"agreePrivacy\":true}"))
                    .andExpect(status().isCreated());
            user = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
            var original = login(email, "abcd1234");
            String oldAccess = original.get("accessToken").asText();
            String oldRefresh = original.get("refreshToken").asText();
            String url = "/api/me/password";

            mvc.perform(patch(url).contentType("application/json")
                    .content("{\"currentPassword\":\"abcd1234\",\"newPassword\":\"efgh5678\"}"))
                    .andExpect(status().isUnauthorized());
            for (String newPassword : new String[]{"short1", "12345678", "abcdefgh", " ", "a".repeat(101) + "1"}) {
                mvc.perform(patch(url).header("Authorization", bearer(oldAccess))
                        .contentType("application/json")
                        .content("{\"currentPassword\":\"abcd1234\",\"newPassword\":\"" + newPassword + "\"}"))
                        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
            }
            mvc.perform(patch(url).header("Authorization", bearer(oldAccess)).contentType("application/json")
                    .content("{\"currentPassword\":\"wrong123\",\"newPassword\":\"efgh5678\"}"))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("WRONG_CURRENT_PASSWORD"));
            mvc.perform(get("/api/me").header("Authorization", bearer(oldAccess)))
                    .andExpect(status().isOk());

            var changed = json.readTree(mvc.perform(patch(url).header("Authorization", bearer(oldAccess))
                    .contentType("application/json")
                    .content("{\"currentPassword\":\"abcd1234\",\"newPassword\":\"efgh5678\"}"))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            String newAccess = changed.get("accessToken").asText();
            String newRefresh = changed.get("refreshToken").asText();
            assertEquals(1, jdbc.queryForObject("SELECT token_version FROM users WHERE id = ?", Integer.class, user));
            mvc.perform(get("/api/me").header("Authorization", bearer(oldAccess)))
                    .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
            mvc.perform(post("/api/auth/refresh").contentType("application/json")
                    .content("{\"refreshToken\":\"" + oldRefresh + "\"}"))
                    .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
            mvc.perform(get("/api/me").header("Authorization", bearer(newAccess)))
                    .andExpect(status().isOk());
            mvc.perform(post("/api/auth/refresh").contentType("application/json")
                    .content("{\"refreshToken\":\"" + newRefresh + "\"}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.accessToken").exists());
            mvc.perform(post("/api/auth/login").contentType("application/json")
                    .content("{\"email\":\"" + email + "\",\"password\":\"abcd1234\"}"))
                    .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
            login(email, "efgh5678");

            mvc.perform(patch(url).header("Authorization", bearer(newAccess)).contentType("application/json")
                    .content("{\"currentPassword\":\"efgh5678\",\"newPassword\":\"efgh5678\"}"))
                    .andExpect(status().isOk());
            assertEquals(2, jdbc.queryForObject("SELECT token_version FROM users WHERE id = ?", Integer.class, user));
            mvc.perform(get("/api/me").header("Authorization", bearer(newAccess)))
                    .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
        } finally {
            if (user != null) jdbc.update("DELETE FROM users WHERE id = ?", user);
        }
    }

    private tools.jackson.databind.JsonNode login(String email, String password) throws Exception {
        String response = mvc.perform(post("/api/auth/login").contentType("application/json")
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(response);
    }

    private String bearer(String token) { return "Bearer " + token; }
}
