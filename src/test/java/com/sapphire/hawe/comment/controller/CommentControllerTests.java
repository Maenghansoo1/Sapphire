package com.sapphire.hawe.comment.controller;

import com.sapphire.hawe.global.security.JwtTokenProvider;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class CommentControllerTests {
    static final Path uploads;
    static { try { uploads = Files.createTempDirectory("hawe-comment-test-"); }
        catch (Exception e) { throw new ExceptionInInitializerError(e); } }
    @DynamicPropertySource static void storage(DynamicPropertyRegistry registry) {
        registry.add("hawe.storage.local-dir", () -> uploads.toString());
    }
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtTokenProvider tokens;
    @Autowired ObjectMapper json;

    @Test void createsListsAndDeletesTextAndImageComments() throws Exception {
        Long owner = user(), friend = user(), waiting = user(), outsider = user();
        Long group = group(owner);
        String imageKey = null;
        try {
            member(group, owner); member(group, friend); member(group, waiting);
            Long mission = mission(group);
            Long submission = submission(mission, owner);
            submission(mission, friend);
            String url = "/api/submissions/" + submission + "/comments";
            String ownerAuth = auth(owner), friendAuth = auth(friend);
            mvc.perform(multipart(url).param("content", "hello")).andExpect(status().isUnauthorized());
            mvc.perform(get(url).header("Authorization", auth(waiting))).andExpect(status().isLocked())
                    .andExpect(jsonPath("$.code").value("FEED_LOCKED"));
            mvc.perform(get(url).header("Authorization", auth(outsider))).andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("NOT_GROUP_MEMBER"));
            mvc.perform(get("/api/submissions/99999999/comments").header("Authorization", ownerAuth))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("SUBMISSION_NOT_FOUND"));
            mvc.perform(post(url).header("Authorization", ownerAuth).contentType("application/json").content("{}"))
                    .andExpect(status().isUnsupportedMediaType());
            mvc.perform(multipart(url).header("Authorization", ownerAuth).param("content", "   "))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
            mvc.perform(multipart(url).header("Authorization", ownerAuth).param("content", "hello")
                    .param("mentionedUserIds", outsider.toString()).file(jpg()))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
            mvc.perform(multipart(url).header("Authorization", ownerAuth).file(jpg()).file(jpg()))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

            String textBody = mvc.perform(multipart(url).header("Authorization", ownerAuth)
                    .param("content", "  @친구 안녕  ")
                    .param("mentionedUserIds", friend.toString(), friend.toString()))
                    .andExpect(status().isCreated()).andExpect(jsonPath("$.content").value("@친구 안녕"))
                    .andExpect(jsonPath("$.mentionedUserIds[0]").value(friend))
                    .andExpect(jsonPath("$.author.userId").value(owner))
                    .andExpect(jsonPath("$.mine").value(true))
                    .andExpect(jsonPath("$.imageUrl").value(org.hamcrest.Matchers.nullValue()))
                    .andExpect(jsonPath("$.createdAt").value(org.hamcrest.Matchers.endsWith("+09:00")))
                    .andReturn().getResponse().getContentAsString();
            Long textId = json.readTree(textBody).get("id").asLong();
            String imageBody = mvc.perform(multipart(url).header("Authorization", friendAuth).file(jpg()))
                    .andExpect(status().isCreated()).andExpect(jsonPath("$.content").value(""))
                    .andExpect(jsonPath("$.mine").value(true))
                    .andExpect(jsonPath("$.imageUrl").isNotEmpty())
                    .andReturn().getResponse().getContentAsString();
            Long imageId = json.readTree(imageBody).get("id").asLong();
            String imageUrl = json.readTree(imageBody).get("imageUrl").asText();
            assertTrue(imageUrl.startsWith("/files/comments/"));
            imageKey = imageUrl.substring("/files/".length());
            assertTrue(Files.exists(uploads.resolve(imageKey)));
            mvc.perform(get(imageUrl)).andExpect(status().isOk());
            mvc.perform(get(url + "?page=0&size=1").header("Authorization", friendAuth))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(textId))
                    .andExpect(jsonPath("$.content[0].mine").value(false))
                    .andExpect(jsonPath("$.totalElements").value(2)).andExpect(jsonPath("$.totalPages").value(2));
            mvc.perform(get(url + "?page=1&size=1").header("Authorization", friendAuth))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(imageId));
            mvc.perform(get("/api/submissions/" + submission).header("Authorization", ownerAuth))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.commentCount").value(2));
            mvc.perform(get("/api/missions/" + mission + "/submissions").header("Authorization", ownerAuth))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].commentCount").value(2));
            mvc.perform(delete("/api/comments/" + imageId).header("Authorization", ownerAuth))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("NOT_COMMENT_OWNER"));
            mvc.perform(delete("/api/comments/99999999").header("Authorization", ownerAuth))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("COMMENT_NOT_FOUND"));
            mvc.perform(delete("/api/comments/" + imageId).header("Authorization", friendAuth))
                    .andExpect(status().isNoContent());
            assertFalse(Files.exists(uploads.resolve(imageKey)));
            mvc.perform(delete("/api/comments/" + imageId).header("Authorization", friendAuth))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("COMMENT_NOT_FOUND"));
            mvc.perform(delete("/api/comments/" + textId).header("Authorization", ownerAuth))
                    .andExpect(status().isNoContent());
        } finally {
            jdbc.update("DELETE FROM comment_mentions WHERE comment_id IN (SELECT co.id FROM comments co "
                    + "JOIN submissions s ON s.id = co.submission_id JOIN missions m ON m.id = s.mission_id WHERE m.group_id = ?)", group);
            jdbc.update("DELETE FROM comments WHERE submission_id IN (SELECT s.id FROM submissions s "
                    + "JOIN missions m ON m.id = s.mission_id WHERE m.group_id = ?)", group);
            jdbc.update("DELETE FROM reactions WHERE submission_id IN (SELECT s.id FROM submissions s "
                    + "JOIN missions m ON m.id = s.mission_id WHERE m.group_id = ?)", group);
            jdbc.update("DELETE FROM submissions WHERE mission_id IN (SELECT id FROM missions WHERE group_id = ?)", group);
            jdbc.update("DELETE FROM missions WHERE group_id = ?", group);
            jdbc.update("DELETE FROM group_members WHERE group_id = ?", group);
            jdbc.update("DELETE FROM `groups` WHERE id = ?", group);
            for (Long user : new Long[]{owner, friend, waiting, outsider}) jdbc.update("DELETE FROM users WHERE id = ?", user);
            if (imageKey != null) Files.deleteIfExists(uploads.resolve(imageKey));
        }
    }

    private String auth(Long user) { return "Bearer " + tokens.createAccessToken(user, "Test"); }
    private static MockMultipartFile jpg() {
        return new MockMultipartFile("file", "image.jpg", "image/jpeg",
                new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff, 1, 2});
    }
    private Long user() {
        String email = UUID.randomUUID() + "@test.com";
        jdbc.update("INSERT INTO users (email, nickname, password_hash, terms_agreed_at, privacy_agreed_at) "
                + "VALUES (?, 'Test', 'unused', NOW(), NOW())", email);
        return jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
    }
    private Long group(Long owner) {
        String code = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        jdbc.update("INSERT INTO `groups` (name, owner_id, invite_code) VALUES ('Comment API', ?, ?)", owner, code);
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
