package com.sapphire.hawe.notification.dao;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class NotificationMapperTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired NotificationMapper notifications;
    @Autowired SqlSessionTemplate sqlSession;

    @Test void inboxOrdersPagesAndUsesGroupProfileThenFallsBackToUser() {
        Long sender = user("기본닉", "profiles/base.png");
        Long receiver = user("받는이", null);
        Long group = group(sender);
        member(group, sender);
        member(group, receiver);
        jdbc.update("UPDATE group_members SET nickname_override='그룹닉', profile_image_url_override='profiles/group.png' "
                + "WHERE group_id=? AND user_id=?", group, sender);
        Long mission = mission(group);
        LocalDateTime now = LocalDateTime.of(2026, 10, 19, 12, 0);
        notifications.insert(null, receiver, "topic", group, mission, null, now);
        notifications.insert(sender, receiver, "poke", group, mission, null, now);
        notifications.insert(sender, receiver, "comment", group, mission, 100L, now);
        notifications.insert(null, receiver, "mission_open", null, null, null, now);
        Long newest = jdbc.queryForObject("SELECT MAX(id) FROM notifications WHERE to_user_id=? AND group_id=?",
                Long.class, receiver, group);

        assertEquals(3, notifications.count(receiver));
        assertEquals(3, notifications.countUnread(receiver));
        assertEquals(List.of("comment", "poke", "topic"),
                notifications.findPage(receiver, 0, 20).stream().map(NotificationRow::getType).toList());
        var first = notifications.findPage(receiver, 0, 1).getFirst();
        assertEquals("그룹닉", first.getNickname());
        assertEquals("profiles/group.png", first.getProfileImageUrl());
        assertEquals(group, first.getGroupId());
        assertEquals(mission, first.getMissionId());
        assertEquals(100L, first.getSubmissionId());
        assertEquals("topic", notifications.findPage(receiver, 2, 1).getFirst().getType());
        assertNull(notifications.findPage(receiver, 2, 1).getFirst().getFromUserId());
        assertFalse(first.isRead());
        assertEquals(0, notifications.markRead(newest, sender));
        assertFalse(notifications.existsOwned(newest, sender));
        assertEquals(1, notifications.markRead(newest, receiver));
        assertTrue(notifications.existsOwned(newest, receiver));
        assertEquals(2, notifications.countUnread(receiver));
        notifications.markAllRead(receiver);
        assertEquals(0, notifications.countUnread(receiver));

        jdbc.update("DELETE FROM group_members WHERE group_id=? AND user_id=?", group, sender);
        sqlSession.clearCache();
        var fallback = notifications.findPage(receiver, 0, 1).getFirst();
        assertEquals("기본닉", fallback.getNickname());
        assertEquals("profiles/base.png", fallback.getProfileImageUrl());
    }

    @Test void deletionIsScopedByGroupRecipientAndSubmission() {
        Long sender = user("보낸이", null);
        Long first = user("첫째", null);
        Long second = user("둘째", null);
        Long group = group(sender);
        Long otherGroup = group(sender);
        LocalDateTime now = LocalDateTime.of(2026, 10, 19, 12, 0);
        notifications.insert(sender, first, "comment", group, null, 11L, now);
        notifications.insert(sender, second, "comment", group, null, 12L, now);
        notifications.insert(sender, first, "poke", otherGroup, null, null, now);
        notifications.deleteByGroupIdAndToUserId(group, first);
        assertEquals(1, notifications.count(first));
        assertEquals(1, notifications.count(second));
        notifications.deleteBySubmissionIds(List.of(12L));
        assertEquals(0, notifications.count(second));
        notifications.deleteByGroupId(otherGroup);
        assertEquals(0, notifications.count(first));
    }

    private Long user(String nickname, String image) {
        String email = UUID.randomUUID() + "@test.com";
        jdbc.update("INSERT INTO users (email, nickname, password_hash, profile_image_url, terms_agreed_at, privacy_agreed_at) "
                + "VALUES (?, ?, 'unused', ?, NOW(), NOW())", email, nickname, image);
        return jdbc.queryForObject("SELECT id FROM users WHERE email=?", Long.class, email);
    }

    private Long group(Long owner) {
        String code = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        jdbc.update("INSERT INTO `groups` (name, owner_id, invite_code) VALUES ('알림 그룹', ?, ?)", owner, code);
        return jdbc.queryForObject("SELECT id FROM `groups` WHERE invite_code=?", Long.class, code);
    }

    private void member(Long group, Long user) {
        jdbc.update("INSERT INTO group_members (group_id, user_id, role) VALUES (?, ?, 'MEMBER')", group, user);
    }

    private Long mission(Long group) {
        jdbc.update("INSERT INTO missions (group_id, content, category, mission_date, template, params, published_at, deadline_at) "
                + "VALUES (?, '알림 테스트', 'TEST', CURDATE(), 'PHOTO_NOW', '{}', NOW(), NOW() + INTERVAL 1 HOUR)", group);
        return jdbc.queryForObject("SELECT MAX(id) FROM missions WHERE group_id=?", Long.class, group);
    }
}
