package com.sapphire.hawe.group.controller;

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
class GroupProfileControllerTests {
    static final Path uploads;
    static { try { uploads = Files.createTempDirectory("hawe-group-profile-test-"); }
        catch (Exception e) { throw new ExceptionInInitializerError(e); } }
    @DynamicPropertySource static void storage(DynamicPropertyRegistry registry) {
        registry.add("hawe.storage.local-dir", () -> uploads.toString());
    }
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtTokenProvider tokens;
    @Autowired ObjectMapper json;

    @Test
    void profileAppearsAcrossGroupResponsesAndCanBeRestored() throws Exception {
        String email = UUID.randomUUID() + "@test.com";
        String code = "K" + UUID.randomUUID().toString().replaceAll("[^A-Fa-f]", "")
                .substring(0, 5).toUpperCase();
        String otherCode = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        Long user = null, outsider = null, group = null, other = null, mission = null, submission = null;
        Path image = null;
        try {
            jdbc.update("INSERT INTO users (email, nickname, password_hash, terms_agreed_at, privacy_agreed_at) "
                    + "VALUES (?, 'Default', 'unused', NOW(), NOW())", email);
            user = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
            String auth = "Bearer " + tokens.createAccessToken(user, "Default");
            String outsiderEmail = UUID.randomUUID() + "@test.com";
            jdbc.update("INSERT INTO users (email, nickname, password_hash, terms_agreed_at, privacy_agreed_at) "
                    + "VALUES (?, 'Outside', 'unused', NOW(), NOW())", outsiderEmail);
            outsider = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, outsiderEmail);
            String outsiderAuth = "Bearer " + tokens.createAccessToken(outsider, "Outside");
            jdbc.update("INSERT INTO `groups` (name, owner_id, invite_code) VALUES ('Main', ?, ?), ('Other', ?, ?)",
                    user, code, user, otherCode);
            group = jdbc.queryForObject("SELECT id FROM `groups` WHERE invite_code = ?", Long.class, code);
            other = jdbc.queryForObject("SELECT id FROM `groups` WHERE invite_code = ?", Long.class, otherCode);
            jdbc.update("INSERT INTO group_members (group_id, user_id, role) VALUES (?, ?, 'owner'), (?, ?, 'owner')",
                    group, user, other, user);
            jdbc.update("INSERT INTO missions (group_id, content, category, mission_date, template, params, published_at, deadline_at) "
                    + "VALUES (?, 'Test', 'TEST', CURDATE(), 'PHOTO_NOW', '{}', NOW(), NOW() + INTERVAL 1 HOUR)", group);
            mission = jdbc.queryForObject("SELECT id FROM missions WHERE group_id = ?", Long.class, group);
            jdbc.update("INSERT INTO submissions (mission_id, user_id) VALUES (?, ?)", mission, user);
            submission = jdbc.queryForObject("SELECT id FROM submissions WHERE mission_id = ?", Long.class, mission);
            jdbc.update("INSERT INTO comments (submission_id, user_id, content) VALUES (?, ?, 'hi')", submission, user);
            Long character = jdbc.queryForObject("SELECT id FROM characters ORDER BY id LIMIT 1", Long.class);
            String url = "/api/groups/" + group + "/members/me/profile";
            String imageUrl = "/api/groups/" + group + "/members/me/profile-image";

            mvc.perform(put(url).contentType("application/json").content("{}"))
                    .andExpect(status().isUnauthorized());
            mvc.perform(put(url).header("Authorization", outsiderAuth).contentType("application/json")
                    .content("{\"nickname\":\"Group\",\"characterId\":" + character + "}"))
                    .andExpect(status().isForbidden());
            mvc.perform(put("/api/groups/999999999/members/me/profile").header("Authorization", auth)
                    .contentType("application/json").content("{\"nickname\":\"Group\",\"characterId\":" + character + "}"))
                    .andExpect(status().isNotFound());
            mvc.perform(put(url).header("Authorization", auth).contentType("application/json")
                    .content("{\"nickname\":\"a\",\"characterId\":" + character + "}"))
                    .andExpect(status().isBadRequest());
            mvc.perform(put(url).header("Authorization", auth).contentType("application/json")
                    .content("{\"nickname\":\"Group\",\"characterId\":999999999}"))
                    .andExpect(status().isNotFound());
            mvc.perform(put(url).header("Authorization", auth).contentType("application/json")
                    .content("{\"nickname\":\" Group \",\"characterId\":" + character + "}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.nickname").value("Group"));
            mvc.perform(get("/api/groups/" + group + "/members").header("Authorization", auth))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].hasGroupProfile").value(true))
                    .andExpect(jsonPath("$.content[0].nickname").value("Group"));
            mvc.perform(get("/api/groups/" + other + "/members").header("Authorization", auth))
                    .andExpect(jsonPath("$.content[0].nickname").value("Default"));
            mvc.perform(get("/api/groups").header("Authorization", auth))
                    .andExpect(jsonPath("$.content[0].members[0].nickname").value("Group"));
            mvc.perform(get("/api/invites/" + code).header("Authorization", outsiderAuth))
                    .andExpect(jsonPath("$.members[0].nickname").value("Group"));
            mvc.perform(get("/api/missions/" + mission + "/submissions").header("Authorization", auth))
                    .andExpect(jsonPath("$.content[0].author.nickname").value("Group"));
            mvc.perform(get("/api/submissions/" + submission).header("Authorization", auth))
                    .andExpect(jsonPath("$.author.nickname").value("Group"));
            mvc.perform(get("/api/submissions/" + submission + "/comments").header("Authorization", auth))
                    .andExpect(jsonPath("$.content[0].author.nickname").value("Group"));

            mvc.perform(put(imageUrl).header("Authorization", auth).contentType("application/json").content("{}"))
                    .andExpect(status().isUnsupportedMediaType());
            mvc.perform(multipart(imageUrl).param("nickname", "Photo")
                    .with(request -> { request.setMethod("PUT"); return request; }).header("Authorization", auth))
                    .andExpect(status().isBadRequest());
            mvc.perform(multipart(imageUrl).file(new MockMultipartFile("file", "bad.jpg", "image/jpeg", new byte[]{1, 2}))
                    .param("nickname", "Photo").with(request -> { request.setMethod("PUT"); return request; })
                    .header("Authorization", auth)).andExpect(status().isUnsupportedMediaType());
            var jpg = new MockMultipartFile("file", "photo.jpg", "image/jpeg",
                    new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff, 1, 2});
            mvc.perform(multipart(imageUrl).file(jpg).param("nickname", " ")
                    .with(request -> { request.setMethod("PUT"); return request; }).header("Authorization", auth))
                    .andExpect(status().isBadRequest());
            mvc.perform(multipart(imageUrl).file(jpg).param("nickname", "Photo")
                    .file(new MockMultipartFile("file", "another.jpg", "image/jpeg", new byte[]{1}))
                    .with(request -> { request.setMethod("PUT"); return request; }).header("Authorization", auth))
                    .andExpect(status().isBadRequest());
            String response = mvc.perform(multipart(imageUrl).file(jpg).param("nickname", " Photo ")
                    .with(request -> { request.setMethod("PUT"); return request; }).header("Authorization", auth))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.nickname").value("Photo"))
                    .andExpect(jsonPath("$.characterKey").value(org.hamcrest.Matchers.nullValue()))
                    .andReturn().getResponse().getContentAsString();
            String fileUrl = json.readTree(response).get("profileImageUrl").asText();
            image = uploads.resolve(fileUrl.substring("/files/".length()));
            assertTrue(Files.exists(image));
            mvc.perform(delete(url).header("Authorization", auth)).andExpect(status().isNoContent());
            assertFalse(Files.exists(image));
            mvc.perform(delete(url).header("Authorization", auth)).andExpect(status().isNoContent());
            mvc.perform(get("/api/groups/" + group + "/members").header("Authorization", auth))
                    .andExpect(jsonPath("$.content[0].hasGroupProfile").value(false))
                    .andExpect(jsonPath("$.content[0].nickname").value("Default"));
        } finally {
            if (submission != null) jdbc.update("DELETE FROM comments WHERE submission_id = ?", submission);
            if (submission != null) jdbc.update("DELETE FROM submissions WHERE id = ?", submission);
            if (mission != null) jdbc.update("DELETE FROM missions WHERE id = ?", mission);
            if (group != null) jdbc.update("DELETE FROM group_members WHERE group_id = ?", group);
            if (other != null) jdbc.update("DELETE FROM group_members WHERE group_id = ?", other);
            if (group != null) jdbc.update("DELETE FROM `groups` WHERE id = ?", group);
            if (other != null) jdbc.update("DELETE FROM `groups` WHERE id = ?", other);
            if (user != null) jdbc.update("DELETE FROM users WHERE id = ?", user);
            if (outsider != null) jdbc.update("DELETE FROM users WHERE id = ?", outsider);
            if (image != null) Files.deleteIfExists(image);
        }
    }
}
