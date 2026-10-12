package com.sapphire.hawe.mission.dao;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
class MissionMapperTests {

    @Autowired JdbcTemplate jdbc;
    @Autowired MissionMapper missions;

    @Test
    void todayRowsAreOrderedAndSubmissionStatusIsPerUser() {
        Long userId = insertUser();
        Long otherUserId = insertUser();
        Long groupId = insertGroup(userId);
        jdbc.update("INSERT INTO group_members (group_id, user_id, role) VALUES (?, ?, 'owner')",
                groupId, userId);
        Long secondMissionId = insertMission(groupId, 2, "CURDATE()");
        Long firstMissionId = insertMission(groupId, 1, "CURDATE()");
        insertMission(groupId, 1, "CURDATE() - INTERVAL 1 DAY");
        jdbc.update("INSERT INTO submissions (mission_id, user_id, media_url) VALUES (?, ?, 'test.png')",
                firstMissionId, userId);

        var rows = missions.findTodayByGroupId(groupId, userId);
        assertEquals(2, rows.size());
        assertEquals(firstMissionId, rows.getFirst().getId());
        assertEquals(secondMissionId, rows.get(1).getId());
        assertTrue(rows.getFirst().isSubmittedByMe());
        assertFalse(rows.get(1).isSubmittedByMe());
        assertEquals("{}", rows.getFirst().getParams());
        assertFalse(missions.findById(firstMissionId, otherUserId).isSubmittedByMe());
        assertNull(missions.findById(Long.MAX_VALUE, userId));
    }

    private Long insertUser() {
        String email = "mission-test-" + UUID.randomUUID() + "@test.com";
        jdbc.update("INSERT INTO users (nickname, email, password_hash, terms_agreed_at, privacy_agreed_at) VALUES ('Test', ?, 'unused', NOW(), NOW())", email);
        return jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
    }

    private Long insertGroup(Long ownerId) {
        jdbc.update("INSERT INTO `groups` (name, owner_id) VALUES ('Test group', ?)", ownerId);
        return jdbc.queryForObject("SELECT MAX(id) FROM `groups` WHERE owner_id = ?", Long.class, ownerId);
    }

    private Long insertMission(Long groupId, int slotNo, String missionDate) {
        jdbc.update("INSERT INTO missions (group_id, content, category, mission_date, template, params, "
                + "slot_no, published_at, deadline_at) VALUES (?, 'Test mission', 'TEST', " + missionDate
                + ", 'PHOTO_NOW', '{}', ?, NOW(), NOW() + INTERVAL 1 HOUR)", groupId, slotNo);
        return jdbc.queryForObject("SELECT MAX(id) FROM missions WHERE group_id = ?", Long.class, groupId);
    }
}
