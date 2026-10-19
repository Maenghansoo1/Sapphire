package com.sapphire.hawe.reaction.dao;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class ReactionMapperTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired ReactionMapper reactions;

    @Test void uniqueTypesToggleAndBatchOrdering() {
        Long firstUser = user();
        Long secondUser = user();
        Long group = group(firstUser);
        Long mission = mission(group);
        Long first = submission(mission, firstUser);
        Long second = submission(mission, secondUser);
        reactions.insert(first, firstUser, "heart");
        reactions.insert(first, secondUser, "heart");
        reactions.insert(first, firstUser, "fire");
        reactions.insert(second, secondUser, "wow");
        assertThrows(DuplicateKeyException.class, () -> reactions.insert(first, firstUser, "heart"));
        assertEquals(List.of(firstUser, secondUser, firstUser), reactions.findBySubmissionId(first).stream()
                .map(ReactionRow::getUserId).toList());
        assertEquals(List.of(first, first, first, second), reactions.findBySubmissionIds(List.of(second, first))
                .stream().map(ReactionRow::getSubmissionId).toList());
        assertEquals(1, reactions.delete(first, firstUser, "heart"));
        assertEquals(0, reactions.delete(first, firstUser, "heart"));
        assertEquals(2, reactions.findBySubmissionId(first).size());
        Long mascot = jdbc.queryForObject("SELECT id FROM users WHERE is_ai=1", Long.class);
        reactions.insert(first, mascot, "wow");
        assertTrue(reactions.existsBySubmissionIdAndUserId(first, mascot));
        assertTrue(reactions.findBySubmissionId(first).stream().anyMatch(row -> row.getUserId().equals(mascot) && row.isAi()));
    }

    private Long user() {
        String email = UUID.randomUUID() + "@test.com";
        jdbc.update("INSERT INTO users (email, nickname, password_hash, terms_agreed_at, privacy_agreed_at) "
                + "VALUES (?, 'Test', 'unused', NOW(), NOW())", email);
        return jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
    }
    private Long group(Long owner) {
        String code = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        jdbc.update("INSERT INTO `groups` (name, owner_id, invite_code) VALUES ('Reaction test', ?, ?)", owner, code);
        return jdbc.queryForObject("SELECT id FROM `groups` WHERE invite_code = ?", Long.class, code);
    }
    private Long mission(Long group) {
        jdbc.update("INSERT INTO missions (group_id, content, category, mission_date, template, params, published_at, deadline_at) "
                + "VALUES (?, 'Test', 'TEST', CURDATE(), 'PHOTO_NOW', '{}', NOW(), NOW() + INTERVAL 1 HOUR)", group);
        return jdbc.queryForObject("SELECT id FROM missions WHERE group_id = ?", Long.class, group);
    }
    private Long submission(Long mission, Long user) {
        jdbc.update("INSERT INTO submissions (mission_id, user_id) VALUES (?, ?)", mission, user);
        return jdbc.queryForObject("SELECT id FROM submissions WHERE mission_id = ? AND user_id = ?", Long.class, mission, user);
    }
}
