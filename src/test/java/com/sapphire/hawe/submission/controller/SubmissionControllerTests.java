package com.sapphire.hawe.submission.controller;

import com.sapphire.hawe.global.security.JwtTokenProvider;
import com.sapphire.hawe.global.exception.BusinessException;
import com.sapphire.hawe.group.dto.GroupCreateRequest;
import com.sapphire.hawe.group.dto.MissionSlotRequest;
import com.sapphire.hawe.group.service.GroupService;
import com.sapphire.hawe.submission.service.SubmissionService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class SubmissionControllerTests {
    static final Path uploads;
    static { try { uploads = Files.createTempDirectory("hawe-submission-test-"); }
        catch (Exception e) { throw new ExceptionInInitializerError(e); } }
    @DynamicPropertySource static void storage(DynamicPropertyRegistry registry) {
        registry.add("hawe.storage.local-dir", () -> uploads.toString());
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired GroupService groups;
    @Autowired JwtTokenProvider tokens;
    @Autowired ObjectMapper json;
    @Autowired SubmissionService submissions;

    @Test void uploadIsPublicButSubmissionAndFeedRequireAccess() throws Exception {
        String email = "upload-" + UUID.randomUUID() + "@test.com";
        jdbc.update("INSERT INTO users (nickname, email, password_hash, terms_agreed_at, privacy_agreed_at) "
                + "VALUES ('Uploader', ?, 'unused', NOW(), NOW())", email);
        Long userId = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
        Long groupId = null;
        String key = null;
        try {
            groupId = groups.createGroup(userId, new GroupCreateRequest("Upload group", null,
                    null, 1, List.of(new MissionSlotRequest(1, "00:00", null)))).id();
            Long missionId = jdbc.queryForObject("SELECT id FROM missions WHERE group_id = ?", Long.class, groupId);
            jdbc.update("UPDATE missions SET template = 'PHOTO_NOW' WHERE id = ?", missionId);
            String endpoint = "/api/missions/" + missionId + "/submissions";
            String auth = "Bearer " + tokens.createAccessToken(userId, "Uploader");
            byte[] jpg = {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 1, 2, 3};
            var file = new MockMultipartFile("file", "other.png", "text/plain", jpg);
            mvc.perform(multipart(endpoint).file(file)).andExpect(status().isUnauthorized());
            mvc.perform(get(endpoint).header("Authorization", auth)).andExpect(status().isLocked())
                    .andExpect(jsonPath("$.code").value("FEED_LOCKED"));
            mvc.perform(post(endpoint).header("Authorization", auth).contentType("application/json")
                    .content("{}")) .andExpect(status().isUnsupportedMediaType())
                    .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
            mvc.perform(multipart(endpoint).file(new MockMultipartFile("file", "fake.jpg", "image/jpeg", "bad".getBytes()))
                    .header("Authorization", auth)).andExpect(status().isUnsupportedMediaType())
                    .andExpect(jsonPath("$.code").value("UNSUPPORTED_FILE_TYPE"));
            mvc.perform(multipart(endpoint).file(new MockMultipartFile("file", "huge.jpg", "image/jpeg",
                    new byte[10 * 1024 * 1024 + 1])).header("Authorization", auth))
                    .andExpect(status().isContentTooLarge()).andExpect(jsonPath("$.code").value("FILE_TOO_LARGE"));
            String body = mvc.perform(multipart(endpoint).file(file).header("Authorization", auth))
                    .andExpect(status().isCreated()).andExpect(jsonPath("$.isLate").value(false))
                    .andExpect(jsonPath("$.author.userId").value(userId))
                    .andExpect(jsonPath("$.mediaUrl").isNotEmpty())
                    .andReturn().getResponse().getContentAsString();
            var parsed = json.readTree(body);
            String url = parsed.get("mediaUrl").asText();
            assertTrue(url.startsWith("/files/submissions/"));
            key = url.substring("/files/".length());
            assertArrayEquals(jpg, mvc.perform(get(url)).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsByteArray());
            mvc.perform(get(endpoint).header("Authorization", auth)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].id").value(parsed.get("id").asLong()));
            mvc.perform(get("/api/submissions/" + parsed.get("id").asLong()).header("Authorization", auth))
                    .andExpect(status().isOk());
            mvc.perform(multipart(endpoint).file(file).header("Authorization", auth))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ALREADY_SUBMITTED"));
            mvc.perform(get("/files/../application.properties")).andExpect(result ->
                    assertNotEquals(200, result.getResponse().getStatus()));
        } finally {
            if (groupId != null) groups.leaveGroup(userId, groupId, null);
            jdbc.update("DELETE FROM users WHERE id = ?", userId);
            if (key != null) Files.deleteIfExists(uploads.resolve(key));
        }
    }

    @Test void concurrentSubmissionsLeaveOneFile() throws Exception {
        String email = "race-" + UUID.randomUUID() + "@test.com";
        jdbc.update("INSERT INTO users (nickname, email, password_hash, terms_agreed_at, privacy_agreed_at) "
                + "VALUES ('Racer', ?, 'unused', NOW(), NOW())", email);
        Long userId = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
        Long groupId = null;
        String key = null;
        try {
            groupId = groups.createGroup(userId, new GroupCreateRequest("Race group", null,
                    null, 1, List.of(new MissionSlotRequest(1, "00:00", null)))).id();
            Long missionId = jdbc.queryForObject("SELECT id FROM missions WHERE group_id = ?", Long.class, groupId);
            jdbc.update("UPDATE missions SET template = 'PHOTO_NOW' WHERE id = ?", missionId);
            byte[] jpg = {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 1};
            var files = new LinkedMultiValueMap<String, MultipartFile>();
            files.add("file", new MockMultipartFile("file", "a.png", "text/plain", jpg));
            CountDownLatch start = new CountDownLatch(1);
            try (var pool = Executors.newFixedThreadPool(2)) {
                var calls = List.of(pool.submit(() -> submitConcurrent(userId, missionId, files, start)),
                        pool.submit(() -> submitConcurrent(userId, missionId, files, start)));
                start.countDown();
                var outcomes = List.of(calls.get(0).get(), calls.get(1).get());
                assertEquals(1, outcomes.stream().filter("created"::equals).count());
                assertEquals(1, outcomes.stream().filter("ALREADY_SUBMITTED"::equals).count());
            }
            String mediaUrl = submissions.feed(userId, missionId, 0, 20).content().getFirst().mediaUrl();
            key = mediaUrl.substring("/files/".length());
            assertTrue(Files.exists(uploads.resolve(key)));
            try (var paths = Files.list(uploads.resolve("submissions"))) {
                assertEquals(1, paths.count());
            }
        } finally {
            if (groupId != null) groups.leaveGroup(userId, groupId, null);
            jdbc.update("DELETE FROM users WHERE id = ?", userId);
            if (key != null) Files.deleteIfExists(uploads.resolve(key));
        }
    }

    private String submitConcurrent(Long userId, Long missionId,
            LinkedMultiValueMap<String, MultipartFile> files, CountDownLatch start) throws Exception {
        start.await();
        try {
            submissions.submit(userId, missionId, new LinkedMultiValueMap<>(), files);
            return "created";
        } catch (BusinessException e) { return e.errorCode().code(); }
    }
}
