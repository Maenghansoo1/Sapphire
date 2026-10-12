package com.sapphire.hawe.profile.controller;

import com.sapphire.hawe.global.security.JwtTokenProvider;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ProfileControllerTests {
    static final Path uploads;
    static { try { uploads = Files.createTempDirectory("hawe-profile-test-"); }
        catch (Exception e) { throw new ExceptionInInitializerError(e); } }
    @DynamicPropertySource static void storage(DynamicPropertyRegistry registry) {
        registry.add("hawe.storage.local-dir", () -> uploads.toString());
    }
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtTokenProvider tokens;
    @Autowired ObjectMapper json;

    @Test void nicknameAndImageAppearAcrossResponses() throws Exception {
        String email = UUID.randomUUID() + "@test.com";
        jdbc.update("INSERT INTO users (email, nickname, password_hash, terms_agreed_at, privacy_agreed_at) "
                + "VALUES (?, 'Before', 'unused', NOW(), NOW())", email);
        Long user = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
        String code = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        String auth = "Bearer " + tokens.createAccessToken(user, "Before");
        String firstKey = null, secondKey = null;
        Long group = null;
        try {
            mvc.perform(patch("/api/me/profile").contentType("application/json")
                    .content("{\"nickname\":\"new\"}")).andExpect(status().isUnauthorized());
            mvc.perform(patch("/api/me/profile").header("Authorization", auth).contentType("application/json")
                    .content("{\"nickname\":\" a \"}")).andExpect(status().isBadRequest());
            mvc.perform(patch("/api/me/profile").header("Authorization", auth).contentType("application/json")
                    .content("{}" )).andExpect(status().isBadRequest());
            mvc.perform(patch("/api/me/profile").header("Authorization", auth).contentType("application/json")
                    .content("{\"nickname\":\" New Name \"}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.nickname").value("New Name"));

            jdbc.update("INSERT INTO `groups` (name, owner_id, invite_code) VALUES ('Profile', ?, ?)", user, code);
            group = jdbc.queryForObject("SELECT id FROM `groups` WHERE invite_code = ?", Long.class, code);
            jdbc.update("INSERT INTO group_members (group_id, user_id, role) VALUES (?, ?, 'OWNER')", group, user);
            jdbc.update("INSERT INTO missions (group_id, content, category, mission_date, template, params, published_at, deadline_at) "
                    + "VALUES (?, 'Test', 'TEST', CURDATE(), 'PHOTO_NOW', '{}', NOW(), NOW() + INTERVAL 1 HOUR)", group);
            Long mission = jdbc.queryForObject("SELECT id FROM missions WHERE group_id = ?", Long.class, group);
            jdbc.update("INSERT INTO submissions (mission_id, user_id) VALUES (?, ?)", mission, user);
            Long submission = jdbc.queryForObject("SELECT id FROM submissions WHERE mission_id = ?", Long.class, mission);
            jdbc.update("INSERT INTO comments (submission_id, user_id, content) VALUES (?, ?, 'hi')", submission, user);

            mvc.perform(multipartPut(new MockMultipartFile("file", "bad.jpg", "image/jpeg", new byte[]{1, 2}))
                    .header("Authorization", auth)).andExpect(status().isUnsupportedMediaType());
            mvc.perform(multipartPut(new MockMultipartFile("file", "empty.jpg", "image/jpeg", new byte[0]))
                    .header("Authorization", auth)).andExpect(status().isBadRequest());
            mvc.perform(put("/api/me/profile-image").header("Authorization", auth).contentType("application/json")
                    .content("{}" )).andExpect(status().isUnsupportedMediaType());
            var jpg = new MockMultipartFile("file", "photo.jpg", "image/jpeg",
                    new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff, 1, 2});
            String firstUrl = json.readTree(mvc.perform(multipartPut(jpg).header("Authorization", auth))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
                    .get("profileImageUrl").asText();
            firstKey = firstUrl.substring("/files/".length());
            assertTrue(Files.exists(uploads.resolve(firstKey)));
            mvc.perform(get(firstUrl)).andExpect(status().isOk());
            mvc.perform(get("/api/me").header("Authorization", auth))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.profileImageUrl").value(firstUrl));
            mvc.perform(get("/api/groups/" + group + "/members").header("Authorization", auth))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].profileImageUrl").value(firstUrl));
            mvc.perform(get("/api/groups").header("Authorization", auth))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].members[0].profileImageUrl").value(firstUrl));
            mvc.perform(get("/api/missions/" + mission + "/submissions").header("Authorization", auth))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].author.profileImageUrl").value(firstUrl));
            mvc.perform(get("/api/submissions/" + submission + "/comments").header("Authorization", auth))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].author.profileImageUrl").value(firstUrl));

            String secondUrl = json.readTree(mvc.perform(multipartPut(jpg).header("Authorization", auth))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
                    .get("profileImageUrl").asText();
            secondKey = secondUrl.substring("/files/".length());
            assertFalse(Files.exists(uploads.resolve(firstKey)));
            assertTrue(Files.exists(uploads.resolve(secondKey)));
            mvc.perform(delete("/api/me/profile-image").header("Authorization", auth)).andExpect(status().isNoContent());
            assertFalse(Files.exists(uploads.resolve(secondKey)));
            mvc.perform(delete("/api/me/profile-image").header("Authorization", auth)).andExpect(status().isNoContent());
            mvc.perform(get("/api/me").header("Authorization", auth))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.profileImageUrl").value(org.hamcrest.Matchers.nullValue()));
        } finally {
            if (group != null) {
                jdbc.update("DELETE FROM comments WHERE submission_id IN (SELECT s.id FROM submissions s JOIN missions m ON m.id=s.mission_id WHERE m.group_id=?)", group);
                jdbc.update("DELETE FROM submissions WHERE mission_id IN (SELECT id FROM missions WHERE group_id=?)", group);
                jdbc.update("DELETE FROM missions WHERE group_id=?", group);
                jdbc.update("DELETE FROM group_members WHERE group_id=?", group);
                jdbc.update("DELETE FROM `groups` WHERE id=?", group);
            }
            jdbc.update("DELETE FROM users WHERE id=?", user);
            if (firstKey != null) Files.deleteIfExists(uploads.resolve(firstKey));
            if (secondKey != null) Files.deleteIfExists(uploads.resolve(secondKey));
        }
    }

    private static org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder multipartPut(MockMultipartFile file) {
        return multipart("/api/me/profile-image").file(file).with(request -> { request.setMethod("PUT"); return request; });
    }
}
