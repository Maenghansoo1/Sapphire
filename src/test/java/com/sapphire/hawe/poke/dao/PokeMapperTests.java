package com.sapphire.hawe.poke.dao;

import com.sapphire.hawe.poke.domain.Poke;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
        Long missionId = insertMission(groupId, "CURDATE()");

        Poke poke = new Poke();
        poke.setFromUserId(fromUserId);
        poke.setToUserId(toUserId);
        poke.setGroupId(groupId);
        poke.setMissionId(missionId);
        poke.setType("fling");
        poke.setSentAt(LocalDateTime.now().withNano(0));
        pokes.insert(poke);

        assertNotNull(poke.getId());
        assertEquals("fling", jdbc.queryForObject("SELECT type FROM pokes WHERE id = ?", String.class, poke.getId()));
        assertEquals(toUserId, jdbc.queryForObject("SELECT to_user_id FROM pokes WHERE id = ?", Long.class, poke.getId()));
        assertEquals(missionId, jdbc.queryForObject("SELECT mission_id FROM pokes WHERE id = ?", Long.class, poke.getId()));
        assertTrue(pokes.existsPoke(fromUserId, toUserId, missionId, "fling"));
        assertEquals("fling", pokes.findSentTypes(missionId, fromUserId).getFirst().getType());
        assertEquals(toUserId, pokes.findSentTypes(missionId, fromUserId).getFirst().getToUserId());
        assertThrows(DuplicateKeyException.class, () -> pokes.insert(poke));
        poke.setType("poke");
        pokes.insert(poke);
        assertEquals(2, pokes.findSentTypes(missionId, fromUserId).size());
        assertTrue(pokes.findSentTypes(missionId, toUserId).isEmpty());
        Long nextMission = insertMission(groupId, "CURDATE() + INTERVAL 1 DAY");
        poke.setMissionId(nextMission);
        poke.setType("fling");
        pokes.insert(poke);
        assertEquals(1, pokes.findSentTypes(nextMission, fromUserId).size());
        assertEquals(2, pokes.findSentTypes(missionId, fromUserId).size());
    }

    @Test
    void currentMissionAndSubmissionAreScopedToMission() {
        Long submitterId = insertUser();
        Long otherUserId = insertUser();
        Long groupId = insertGroup(submitterId);
        Long otherGroupId = insertGroup(submitterId);
        Long emptyGroupId = insertGroup(otherUserId);
        assertNull(pokes.findCurrentMissionId(emptyGroupId));
        Long previous = insertMission(groupId, "CURDATE() - INTERVAL 1 DAY");
        submit(previous, submitterId);
        Long current = insertMission(groupId, "CURDATE()");
        Long other = insertMission(otherGroupId, "CURDATE()");
        submit(other, otherUserId);

        assertEquals(current, pokes.findCurrentMissionId(groupId));
        assertEquals(other, pokes.findCurrentMissionId(otherGroupId));
        assertTrue(pokes.existsSubmission(previous, submitterId));
        assertFalse(pokes.existsSubmission(current, submitterId));
        assertFalse(pokes.existsSubmission(current, otherUserId));
        assertTrue(pokes.existsSubmission(other, otherUserId));
    }

    private Long insertUser() {
        String email = "test-" + UUID.randomUUID() + "@test.com";
        jdbc.update("INSERT INTO users (nickname, email, password_hash, terms_agreed_at, privacy_agreed_at) VALUES (?, ?, ?, NOW(), NOW())",
                "Test", email, "unused");
        return jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
    }

    private Long insertGroup(Long ownerId) {
        jdbc.update("INSERT INTO `groups` (name, owner_id, invite_code) VALUES ('Test group', ?, ?)",
                ownerId, UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase());
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
