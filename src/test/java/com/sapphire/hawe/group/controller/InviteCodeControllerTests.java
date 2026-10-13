package com.sapphire.hawe.group.controller;

import com.sapphire.hawe.global.security.JwtTokenProvider;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class InviteCodeControllerTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtTokenProvider tokens;
    @Autowired ObjectMapper json;

    @Test
    void reissueInvalidatesOldCodeAndFailedLookupsAreLimitedPerUser() throws Exception {
        List<Long> users = new ArrayList<>();
        Long group = null;
        String oldCode = validCode();
        try {
            Long owner = insertUser(users, "Owner");
            Long member = insertUser(users, "Member");
            Long outsider = insertUser(users, "Outsider");
            Long guesser = insertUser(users, "Guesser");
            String ownerAuth = bearer(owner);
            String memberAuth = bearer(member);
            String outsiderAuth = bearer(outsider);
            String guesserAuth = bearer(guesser);
            jdbc.update("INSERT INTO `groups` (name, owner_id, invite_code) VALUES ('Test', ?, ?)", owner, oldCode);
            group = jdbc.queryForObject("SELECT id FROM `groups` WHERE invite_code = ?", Long.class, oldCode);
            jdbc.update("INSERT INTO group_members (group_id, user_id, role) VALUES (?, ?, 'owner'), (?, ?, 'member')",
                    group, owner, group, member);
            String reissue = "/api/groups/" + group + "/invite-code";

            mvc.perform(post(reissue)).andExpect(status().isUnauthorized());
            mvc.perform(post("/api/groups/999999999/invite-code").header("Authorization", ownerAuth))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("GROUP_NOT_FOUND"));
            mvc.perform(post(reissue).header("Authorization", outsiderAuth))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("NOT_GROUP_MEMBER"));
            mvc.perform(post(reissue).header("Authorization", memberAuth))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("NOT_GROUP_OWNER"));
            String body = mvc.perform(post(reissue).header("Authorization", ownerAuth))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            String newCode = json.readTree(body).get("inviteCode").asText();
            assertNotEquals(oldCode, newCode);
            assertTrue(newCode.matches("[A-HJ-NP-Z2-9]{6}"));
            assertEquals("https://hawe.app/invite/" + newCode, json.readTree(body).get("inviteUrl").asText());
            mvc.perform(get("/api/groups/" + group).header("Authorization", ownerAuth))
                    .andExpect(jsonPath("$.inviteCode").value(newCode));
            mvc.perform(get("/api/invites/" + oldCode).header("Authorization", memberAuth))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("INVITE_NOT_FOUND"));
            mvc.perform(post("/api/groups/join").header("Authorization", outsiderAuth)
                    .contentType("application/json").content("{\"code\":\"" + newCode + "\"}"))
                    .andExpect(status().isOk());
            for (int i = 0; i < 10; i++) {
                mvc.perform(get("/api/invites/BAD").header("Authorization", guesserAuth))
                        .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("INVITE_NOT_FOUND"));
            }
            mvc.perform(get("/api/invites/" + newCode).header("Authorization", guesserAuth))
                    .andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"));
            mvc.perform(post("/api/groups/join").header("Authorization", guesserAuth)
                    .contentType("application/json").content("{\"code\":\"" + newCode + "\"}"))
                    .andExpect(status().isTooManyRequests());
            mvc.perform(get("/api/invites/" + newCode).header("Authorization", memberAuth))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ALREADY_MEMBER"));
        } finally {
            if (group != null) {
                jdbc.update("DELETE FROM group_members WHERE group_id = ?", group);
                jdbc.update("DELETE FROM `groups` WHERE id = ?", group);
            }
            for (Long user : users) jdbc.update("DELETE FROM users WHERE id = ?", user);
        }
    }

    private Long insertUser(List<Long> users, String nickname) {
        String email = UUID.randomUUID() + "@test.com";
        jdbc.update("INSERT INTO users (email, nickname, password_hash, terms_agreed_at, privacy_agreed_at) "
                + "VALUES (?, ?, 'unused', NOW(), NOW())", email, nickname);
        Long id = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
        users.add(id);
        return id;
    }

    private String bearer(Long user) { return "Bearer " + tokens.createAccessToken(user, "Tester"); }

    private String validCode() {
        return "K" + UUID.randomUUID().toString().replaceAll("[^A-Fa-f]", "").substring(0, 5).toUpperCase();
    }
}
