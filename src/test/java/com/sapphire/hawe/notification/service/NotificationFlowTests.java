package com.sapphire.hawe.notification.service;

import com.sapphire.hawe.group.dto.GroupCreateRequest;
import com.sapphire.hawe.group.dto.MissionSlotRequest;
import com.sapphire.hawe.group.service.GroupService;
import com.sapphire.hawe.poke.dto.PokeRequest;
import com.sapphire.hawe.poke.service.PokeService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
class NotificationFlowTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired GroupService groups;
    @Autowired PokeService pokes;

    @Test void missionAndPokeCreateNotificationsOnlyAfterCommit() {
        Long owner = user();
        Long target = user();
        Long group = null;
        try {
            group = groups.createGroup(owner, new GroupCreateRequest("알림 흐름", "FRIEND", null, 1,
                    List.of(new MissionSlotRequest(1, "00:00", null)))).id();
            Long mission = jdbc.queryForObject("SELECT id FROM missions WHERE group_id=?", Long.class, group);
            assertNotNull(mission);
            assertEquals(1, count("SELECT COUNT(*) FROM notifications WHERE group_id=? AND to_user_id=? "
                    + "AND type='topic' AND mission_id=?", group, owner, mission));
            jdbc.update("INSERT INTO group_members (group_id, user_id, role) VALUES (?, ?, 'MEMBER')", group, target);
            pokes.sendPoke(owner, group, new PokeRequest(target, "poke", mission));
            assertEquals(1, count("SELECT COUNT(*) FROM notifications WHERE group_id=? AND to_user_id=? "
                    + "AND from_user_id=? AND type='poke' AND mission_id=?", group, target, owner, mission));
        } finally {
            if (group != null) {
                groups.leaveGroup(target, group, null);
                groups.leaveGroup(owner, group, null);
            }
            jdbc.update("DELETE FROM users WHERE id IN (?, ?)", owner, target);
        }
    }

    private long count(String sql, Object... params) {
        return jdbc.queryForObject(sql, Long.class, params);
    }

    private Long user() {
        String email = UUID.randomUUID() + "@test.com";
        jdbc.update("INSERT INTO users (email, nickname, password_hash, terms_agreed_at, privacy_agreed_at) "
                + "VALUES (?, '알림테스트', 'unused', NOW(), NOW())", email);
        return jdbc.queryForObject("SELECT id FROM users WHERE email=?", Long.class, email);
    }
}
