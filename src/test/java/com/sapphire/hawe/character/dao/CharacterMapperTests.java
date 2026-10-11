package com.sapphire.hawe.character.dao;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@SpringBootTest
@Transactional
class CharacterMapperTests {

    @Autowired JdbcTemplate jdbc;
    @Autowired CharacterMapper characters;

    @Test
    void seededCharactersAreListedInIdOrder() {
        var rows = characters.findAll(0L, 20);

        assertEquals(7, rows.size());
        assertEquals(7, characters.count());
        assertEquals("mongsil", rows.getFirst().getSvgKey());
        assertEquals("곰돌이", rows.get(2).getName());
        assertEquals("pilleum", rows.getLast().getSvgKey());
        assertEquals(3, characters.findAll(3L, 3).size());
    }

    @Test
    void findByIdReturnsNullForUnknownCharacter() {
        assertEquals("gomdol", characters.findById(3L).getSvgKey());
        assertNull(characters.findById(4L));
        assertNull(characters.findById(999L));
    }

    @Test
    void updateUserCharacterIdChangesOnlyThatUser() {
        Long userId = insertUser();
        Long otherUserId = insertUser();

        assertEquals(1, characters.updateUserCharacterId(userId, 3L));
        assertEquals(0, characters.updateUserCharacterId(Long.MAX_VALUE, 3L));

        assertEquals(3L, characterIdOf(userId));
        assertNull(characterIdOf(otherUserId));
    }

    private Long insertUser() {
        String loginId = "test-" + UUID.randomUUID();
        jdbc.update("INSERT INTO users (nickname, login_id, password_hash) VALUES (?, ?, ?)",
                "Test", loginId, "unused");
        return jdbc.queryForObject("SELECT id FROM users WHERE login_id = ?", Long.class, loginId);
    }

    private Long characterIdOf(Long userId) {
        return jdbc.queryForObject("SELECT character_id FROM users WHERE id = ?", Long.class, userId);
    }
}
