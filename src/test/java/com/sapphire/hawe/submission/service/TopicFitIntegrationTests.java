package com.sapphire.hawe.submission.service;

import com.sapphire.hawe.group.dto.GroupCreateRequest;
import com.sapphire.hawe.group.dto.MissionSlotRequest;
import com.sapphire.hawe.group.service.GroupService;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.multipart.MultipartFile;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@TestPropertySource(properties = "hawe.vision.enabled=true")
class TopicFitIntegrationTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired GroupService groups;
    @Autowired SubmissionService submissions;
    @MockitoBean VisionClient client;

    @Test void committedPhotoGetsReviewAndGroupDeleteCleansIt() throws Exception {
        String email = "vision-" + UUID.randomUUID() + "@test.com";
        jdbc.update("INSERT INTO users (nickname, email, password_hash, terms_agreed_at, privacy_agreed_at) "
                + "VALUES ('Writer', ?, 'unused', NOW(), NOW())", email);
        Long userId = jdbc.queryForObject("SELECT id FROM users WHERE email=?", Long.class, email);
        Long groupId = null;
        try {
            groupId = groups.createGroup(userId, new GroupCreateRequest("Vision group", null,
                    null, 1, List.of(new MissionSlotRequest(1, "00:00", null)))).id();
            Long missionId = jdbc.queryForObject("SELECT id FROM missions WHERE group_id=?", Long.class, groupId);
            jdbc.update("UPDATE missions SET template='PHOTO_NOW', params=? WHERE id=?",
                    "{\"subject\":\"창밖 풍경\"}", missionId);
            when(client.call(eq("PHOTO_NOW"), eq("창밖 풍경"), any(), anyString()))
                    .thenReturn(new TopicFitResult(0.5, "clip-ViT-B-32+multilingual-v1", "1"));
            var files = new LinkedMultiValueMap<String, MultipartFile>();
            files.add("file", new MockMultipartFile("file", "photo.jpg", "image/jpeg",
                    new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff}));
            var created = submissions.submit(userId, missionId, new LinkedMultiValueMap<>(), files);
            assertNull(created.aiReview());
            long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
            while (System.nanoTime() < deadline && jdbc.queryForObject(
                    "SELECT COUNT(*) FROM submission_ai_reviews WHERE submission_id=? AND status='DONE'",
                    Integer.class, created.id()) == 0) Thread.sleep(50);
            var reviewed = submissions.detail(userId, created.id()).aiReview();
            assertNotNull(reviewed, "Vision review did not complete within five seconds");
            assertEquals("PERFECT_FIT", reviewed.badge());
            groups.leaveGroup(userId, groupId, null);
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM submission_ai_reviews WHERE submission_id=?",
                    Integer.class, created.id()));
            groupId = null;
        } finally {
            if (groupId != null) groups.leaveGroup(userId, groupId, null);
            jdbc.update("DELETE FROM users WHERE id=?", userId);
        }
    }
}
