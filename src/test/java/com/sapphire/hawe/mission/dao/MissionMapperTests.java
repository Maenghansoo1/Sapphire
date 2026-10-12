package com.sapphire.hawe.mission.dao;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import com.sapphire.hawe.global.time.SeoulTime;

import java.util.UUID;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.springframework.dao.DuplicateKeyException;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
    void currentRowsAreOrderedAndSubmissionStatusIsPerUser() {
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

        var rows = missions.findCurrentByGroupId(groupId, userId);
        assertEquals(2, rows.size());
        assertEquals(firstMissionId, rows.getFirst().getId());
        assertEquals(secondMissionId, rows.get(1).getId());
        assertTrue(rows.getFirst().isSubmittedByMe());
        assertFalse(rows.get(1).isSubmittedByMe());
        assertEquals("{}", rows.getFirst().getParams());
        assertFalse(missions.findById(firstMissionId, otherUserId).isSubmittedByMe());
        assertEquals(3, rows.getFirst().getNumber());
        assertEquals(2, rows.get(1).getNumber());
        assertEquals(1, missions.findMemberStatuses(firstMissionId).size());
        assertTrue(missions.findMemberStatuses(firstMissionId).getFirst().isSubmitted());
        var summaries = missions.findCurrentSummariesByGroupIds(java.util.List.of(groupId), userId);
        assertEquals(1, summaries.size());
        assertEquals(firstMissionId, summaries.getFirst().getMissionId());
        assertEquals(1, summaries.getFirst().getSubmittedCount());
        assertTrue(summaries.getFirst().isSubmittedByMe());
        assertNull(missions.findById(Long.MAX_VALUE, userId));
    }

    @Test
    void insertIsUniquePerGroupDateAndSlotAndYesterdayCanBeCurrent() {
        Long userId = insertUser();
        Long groupId = insertGroup(userId);
        LocalDate yesterday = SeoulTime.today().minusDays(1);
        MissionInsertRow row = new MissionInsertRow();
        row.setGroupId(groupId);
        row.setContent("Yesterday");
        row.setCategory("일상");
        row.setMissionDate(yesterday);
        row.setTemplate("PHOTO_NOW");
        row.setParams("{}");
        row.setSlotNo(1);
        row.setPublishedAt(yesterday.atTime(23, 30));
        row.setDeadlineAt(yesterday.plusDays(1).atTime(23, 0));
        missions.insert(row);

        assertTrue(missions.existsByGroupIdAndDateAndSlot(groupId, yesterday, 1));
        assertFalse(missions.existsByGroupIdAndDateAndSlot(groupId, yesterday.plusDays(1), 1));
        assertEquals(java.util.List.of("PHOTO_NOW"), missions.findTemplatesByGroupIdAndDate(groupId, yesterday));
        assertEquals("Yesterday", missions.findLatestContentByGroupId(groupId));
        assertEquals(row.getId(), missions.findCurrentByGroupId(groupId, userId).getFirst().getId());
        assertEquals(yesterday, missions.findCurrentByGroupId(groupId, userId).getFirst().getMissionDate());
        assertThrows(DuplicateKeyException.class, () -> missions.insert(row));
    }

    private Long insertUser() {
        String email = "mission-test-" + UUID.randomUUID() + "@test.com";
        jdbc.update("INSERT INTO users (nickname, email, password_hash, terms_agreed_at, privacy_agreed_at) VALUES ('Test', ?, 'unused', NOW(), NOW())", email);
        return jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
    }

    private Long insertGroup(Long ownerId) {
        jdbc.update("INSERT INTO `groups` (name, owner_id, invite_code) VALUES ('Test group', ?, ?)",
                ownerId, UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase());
        return jdbc.queryForObject("SELECT MAX(id) FROM `groups` WHERE owner_id = ?", Long.class, ownerId);
    }

    private Long insertMission(Long groupId, int slotNo, String missionDate) {
        jdbc.update("INSERT INTO missions (group_id, content, category, mission_date, template, params, "
                + "slot_no, published_at, deadline_at) VALUES (?, 'Test mission', 'TEST', " + missionDate
                + ", 'PHOTO_NOW', '{}', ?, " + missionDate + ", NOW() + INTERVAL 1 HOUR)", groupId, slotNo);
        return jdbc.queryForObject("SELECT MAX(id) FROM missions WHERE group_id = ?", Long.class, groupId);
    }
}
