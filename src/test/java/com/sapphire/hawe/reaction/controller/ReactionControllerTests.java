package com.sapphire.hawe.reaction.controller;

import com.sapphire.hawe.global.security.JwtTokenProvider;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ReactionControllerTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtTokenProvider tokens;

    @Test void togglesAndEnrichesFeedForEachViewer() throws Exception {
        Long owner = user();
        Long friend = user();
        Long waiting = user();
        Long outsider = user();
        Long group = group(owner);
        member(group, owner); member(group, friend); member(group, waiting);
        Long mission = mission(group);
        Long submission = submission(mission, owner);
        submission(mission, friend);
        String url = "/api/submissions/" + submission + "/reactions";
        String ownerAuth = auth(owner), friendAuth = auth(friend), waitingAuth = auth(waiting), outsiderAuth = auth(outsider);

        mvc.perform(post(url).contentType("application/json").content("{\"stickerType\":\"heart\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(url).header("Authorization", waitingAuth).contentType("application/json")
                .content("{\"stickerType\":\"heart\"}"))
                .andExpect(status().isLocked()).andExpect(jsonPath("$.code").value("FEED_LOCKED"));
        mvc.perform(post(url).header("Authorization", outsiderAuth).contentType("application/json")
                .content("{\"stickerType\":\"heart\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("NOT_GROUP_MEMBER"));
        mvc.perform(post("/api/submissions/99999999/reactions").header("Authorization", ownerAuth)
                .contentType("application/json").content("{\"stickerType\":\"heart\"}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("SUBMISSION_NOT_FOUND"));
        for (String body : new String[]{"{\"stickerType\":\"HEART\"}", "{\"stickerType\":\"\"}", "{}"})
            mvc.perform(post(url).header("Authorization", ownerAuth).contentType("application/json").content(body))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mvc.perform(post(url).header("Authorization", ownerAuth).contentType("application/json")
                .content("{\"stickerType\":\"heart\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.reactions[0].stickerType").value("heart"))
                .andExpect(jsonPath("$.reactions[0].count").value(1))
                .andExpect(jsonPath("$.reactions[0].reactedByMe").value(true))
                .andExpect(jsonPath("$.reactions[0].userIds[0]").value(owner));
        mvc.perform(get("/api/missions/" + mission + "/submissions").header("Authorization", friendAuth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].reactions[0].reactedByMe").value(false));
        mvc.perform(get("/api/submissions/" + submission).header("Authorization", ownerAuth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.reactions[0].reactedByMe").value(true));
        mvc.perform(post(url).header("Authorization", friendAuth).contentType("application/json")
                .content("{\"stickerType\":\"fire\"}")).andExpect(status().isOk());
        mvc.perform(post(url).header("Authorization", ownerAuth).contentType("application/json")
                .content("{\"stickerType\":\"heart\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.reactions[0].stickerType").value("fire"));
        mvc.perform(post(url).header("Authorization", friendAuth).contentType("application/json")
                .content("{\"stickerType\":\"fire\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.reactions").isEmpty());
    }

    private String auth(Long user) { return "Bearer " + tokens.createAccessToken(user, "Test"); }
    private Long user() {
        String email = UUID.randomUUID() + "@test.com";
        jdbc.update("INSERT INTO users (email, nickname, password_hash, terms_agreed_at, privacy_agreed_at) "
                + "VALUES (?, 'Test', 'unused', NOW(), NOW())", email);
        return jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
    }
    private Long group(Long owner) {
        String code = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        jdbc.update("INSERT INTO `groups` (name, owner_id, invite_code) VALUES ('Reaction API', ?, ?)", owner, code);
        return jdbc.queryForObject("SELECT id FROM `groups` WHERE invite_code = ?", Long.class, code);
    }
    private void member(Long group, Long user) {
        jdbc.update("INSERT INTO group_members (group_id, user_id, role) VALUES (?, ?, 'MEMBER')", group, user);
    }
    private Long mission(Long group) {
        jdbc.update("INSERT INTO missions (group_id, content, category, mission_date, template, params, published_at, deadline_at) "
                + "VALUES (?, 'Test', 'TEST', CURDATE(), 'PHOTO_NOW', '{}', NOW(), NOW() + INTERVAL 1 HOUR)", group);
        return jdbc.queryForObject("SELECT id FROM missions WHERE group_id = ?", Long.class, group);
    }
    private Long submission(Long mission, Long user) {
        jdbc.update("INSERT INTO submissions (mission_id, user_id) VALUES (?, ?)", mission, user);
        return jdbc.queryForObject("SELECT id FROM submissions WHERE mission_id = ? AND user_id = ?", Long.class, mission, user);
    }
}
