package com.sapphire.hawe.poke.dao;

import com.sapphire.hawe.poke.domain.Poke;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
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
    @Autowired SqlSessionTemplate sqlSession;

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
        poke.setType("poke");
        poke.setSentAt(LocalDateTime.now().withNano(0));
        pokes.insert(poke);

        assertNotNull(poke.getId());
        assertEquals("poke", jdbc.queryForObject("SELECT type FROM pokes WHERE id = ?", String.class, poke.getId()));
        assertEquals(toUserId, jdbc.queryForObject("SELECT to_user_id FROM pokes WHERE id = ?", Long.class, poke.getId()));
        assertEquals(missionId, jdbc.queryForObject("SELECT mission_id FROM pokes WHERE id = ?", Long.class, poke.getId()));
        assertTrue(pokes.existsPoke(fromUserId, toUserId, missionId, "poke"));
        assertEquals("poke", pokes.findSentTypes(missionId, fromUserId).getFirst().getType());
        assertEquals(toUserId, pokes.findSentTypes(missionId, fromUserId).getFirst().getToUserId());
        assertThrows(DuplicateKeyException.class, () -> pokes.insert(poke));
        assertTrue(pokes.findSentTypes(missionId, toUserId).isEmpty());
        Long nextMission = insertMission(groupId, "CURDATE() + INTERVAL 1 DAY");
        poke.setMissionId(nextMission);
        pokes.insert(poke);
        assertEquals(1, pokes.findSentTypes(nextMission, fromUserId).size());
        assertEquals(1, pokes.findSentTypes(missionId, fromUserId).size());
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

    @Test
    void defaultIsTheFirstOpenSlotAndChosenMissionMustBeCurrentInThatGroup() {
        Long userId = insertUser();
        Long groupId = insertGroup(userId);
        Long otherGroupId = insertGroup(userId);
        jdbc.update("UPDATE `groups` SET daily_mission_count = 3 WHERE id = ?", groupId);
        Long previousSlot2 = insertSlotMission(groupId, 2, "CURDATE() - INTERVAL 1 DAY");
        Long slot3 = insertSlotMission(groupId, 3, "CURDATE()");
        Long slot2 = insertSlotMission(groupId, 2, "CURDATE()");
        Long other = insertSlotMission(otherGroupId, 1, "CURDATE()");

        // 1번 슬롯이 아직 열리지 않았으면 열린 것 중 가장 작은 슬롯
        assertEquals(slot2, pokes.findCurrentMissionId(groupId));
        Long slot1 = insertSlotMission(groupId, 1, "CURDATE()");
        sqlSession.clearCache();  // JdbcTemplate 로 넣은 행이 같은 조회에 보이게
        assertEquals(slot1, pokes.findCurrentMissionId(groupId));

        assertTrue(pokes.isCurrentMissionOfGroup(slot3, groupId));
        assertTrue(pokes.isCurrentMissionOfGroup(slot2, groupId));
        assertFalse(pokes.isCurrentMissionOfGroup(previousSlot2, groupId));  // 다음 미션이 열린 지난 미션
        assertFalse(pokes.isCurrentMissionOfGroup(other, groupId));          // 남의 그룹 미션
        assertTrue(pokes.isCurrentMissionOfGroup(other, otherGroupId));
        assertFalse(pokes.isCurrentMissionOfGroup(Long.MAX_VALUE, groupId));
    }

    private Long insertSlotMission(Long groupId, int slotNo, String missionDate) {
        jdbc.update("INSERT INTO missions (group_id, content, category, mission_date, template, params, slot_no, "
                + "published_at, deadline_at) VALUES (?, 'Test mission', 'TEST', " + missionDate + ", 'PHOTO_NOW', '{}', ?, "
                + missionDate + ", NOW() + INTERVAL 1 HOUR)", groupId, slotNo);
        return jdbc.queryForObject("SELECT MAX(id) FROM missions WHERE group_id = ?", Long.class, groupId);
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
