package com.sapphire.hawe.poke.dao;

import com.sapphire.hawe.poke.domain.Poke;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
class PokeMapperTests {

    @Autowired JdbcTemplate jdbc;
    @Autowired PokeMapper pokes;

    @Test
    void insertStoresPokeWithGeneratedId() {
        Long fromUserId = insertUser();
        Long toUserId = insertUser();
        Long groupId = insertGroup(fromUserId);

        Poke poke = new Poke();
        poke.setFromUserId(fromUserId);
        poke.setToUserId(toUserId);
        poke.setGroupId(groupId);
        poke.setType("fling");
        poke.setSentAt(LocalDateTime.now().withNano(0));
        pokes.insert(poke);

        assertNotNull(poke.getId());
        assertEquals("fling", jdbc.queryForObject("SELECT type FROM pokes WHERE id = ?", String.class, poke.getId()));
        assertEquals(toUserId, jdbc.queryForObject("SELECT to_user_id FROM pokes WHERE id = ?", Long.class, poke.getId()));
    }

    @Test
    void todaySubmissionIsCheckedPerGroupUserAndDate() {
        Long submitterId = insertUser();
        Long otherUserId = insertUser();
        Long groupId = insertGroup(submitterId);
        Long otherGroupId = insertGroup(submitterId);
        submit(insertMission(groupId, "CURDATE()"), submitterId);
        submit(insertMission(otherGroupId, "CURDATE() - INTERVAL 1 DAY"), otherUserId);

        assertTrue(pokes.existsTodaySubmission(groupId, submitterId));
        assertFalse(pokes.existsTodaySubmission(groupId, otherUserId));
        assertFalse(pokes.existsTodaySubmission(otherGroupId, submitterId));
        assertFalse(pokes.existsTodaySubmission(otherGroupId, otherUserId));
    }

    private Long insertUser() {
        String loginId = "test-" + UUID.randomUUID();
        jdbc.update("INSERT INTO users (nickname, login_id, password_hash) VALUES (?, ?, ?)",
                "Test", loginId, "unused");
        return jdbc.queryForObject("SELECT id FROM users WHERE login_id = ?", Long.class, loginId);
    }

    private Long insertGroup(Long ownerId) {
        jdbc.update("INSERT INTO `groups` (name, owner_id) VALUES ('Test group', ?)", ownerId);
        return jdbc.queryForObject("SELECT MAX(id) FROM `groups` WHERE owner_id = ?", Long.class, ownerId);
    }

    private Long insertMission(Long groupId, String missionDate) {
        jdbc.update("INSERT INTO missions (group_id, content, category, mission_date, template, params, published_at, deadline_at) "
                + "VALUES (?, 'Test mission', 'TEST', " + missionDate + ", 'PHOTO_NOW', '{}', NOW(), NOW() + INTERVAL 1 HOUR)", groupId);
        return jdbc.queryForObject("SELECT MAX(id) FROM missions WHERE group_id = ?", Long.class, groupId);
    }

    private void submit(Long missionId, Long userId) {
        jdbc.update("INSERT INTO submissions (mission_id, user_id, media_url) VALUES (?, ?, 'test.png')",
                missionId, userId);
    }
}
