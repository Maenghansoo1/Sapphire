package com.sapphire.hawe.notification.controller;

import com.sapphire.hawe.global.security.JwtTokenProvider;
import com.sapphire.hawe.notification.dao.NotificationMapper;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class NotificationControllerTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtTokenProvider tokens;
    @Autowired NotificationMapper notifications;

    @Test void mergedInboxCountAndReadsAreAuthenticatedAndOwned() throws Exception {
        Long sender = user("서연");
        Long receiver = user("받는이");
        Long firstGroup = group(sender, "첫 그룹");
        Long secondGroup = group(sender, "둘째 그룹");
        LocalDateTime now = LocalDateTime.of(2026, 10, 19, 12, 0);
        notifications.insert(sender, receiver, "poke", firstGroup, 10L, null, now);
        notifications.insert(null, receiver, "topic", secondGroup, 11L, null, now);
        Long firstId = jdbc.queryForObject("SELECT MIN(id) FROM notifications WHERE to_user_id=?", Long.class, receiver);
        String auth = "Bearer " + tokens.createAccessToken(receiver, "받는이");
        String senderAuth = "Bearer " + tokens.createAccessToken(sender, "서연");

        mvc.perform(get("/api/notifications")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/notifications").header("Authorization", auth).param("page", "-1").param("size", "0"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(1)).andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].type").value("topic"))
                .andExpect(jsonPath("$.content[0].groupName").value("둘째 그룹"))
                .andExpect(jsonPath("$.content[0].fromUser").isEmpty())
                .andExpect(jsonPath("$.content[0].isRead").value(false))
                .andExpect(jsonPath("$.content[0].createdAt").value("2026-10-19T12:00:00+09:00"));
        mvc.perform(get("/api/notifications").header("Authorization", auth).param("size", "200"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.size").value(100))
                .andExpect(jsonPath("$.content[1].groupName").value("첫 그룹"))
                .andExpect(jsonPath("$.content[1].fromUser.nickname").value("서연"))
                .andExpect(jsonPath("$.content[1].body").value("서연님이 콕 찔렀어요"));
        mvc.perform(get("/api/notifications/unread-count").header("Authorization", auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.unreadCount").value(2));
        mvc.perform(patch("/api/notifications/" + firstId + "/read").header("Authorization", senderAuth))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOTIFICATION_NOT_FOUND"));
        mvc.perform(patch("/api/notifications/" + firstId + "/read").header("Authorization", auth))
                .andExpect(status().isNoContent());
        mvc.perform(patch("/api/notifications/" + firstId + "/read").header("Authorization", auth))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/notifications/unread-count").header("Authorization", auth))
                .andExpect(jsonPath("$.unreadCount").value(1));
        mvc.perform(patch("/api/notifications/read-all").header("Authorization", auth))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/notifications/unread-count").header("Authorization", auth))
                .andExpect(jsonPath("$.unreadCount").value(0));
    }

    private Long user(String nickname) {
        String email = UUID.randomUUID() + "@test.com";
        jdbc.update("INSERT INTO users (email, nickname, password_hash, terms_agreed_at, privacy_agreed_at) "
                + "VALUES (?, ?, 'unused', NOW(), NOW())", email, nickname);
        return jdbc.queryForObject("SELECT id FROM users WHERE email=?", Long.class, email);
    }

    private Long group(Long owner, String name) {
        String code = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        jdbc.update("INSERT INTO `groups` (name, owner_id, invite_code) VALUES (?, ?, ?)", name, owner, code);
        return jdbc.queryForObject("SELECT id FROM `groups` WHERE invite_code=?", Long.class, code);
    }
}
