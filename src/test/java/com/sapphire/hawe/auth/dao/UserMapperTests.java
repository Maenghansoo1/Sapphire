package com.sapphire.hawe.auth.dao;

import com.sapphire.hawe.auth.domain.User;
import com.sapphire.hawe.global.time.SeoulTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
class UserMapperTests {

    @Autowired UserMapper users;

    @Test
    void insertsFindsAndEnforcesUniqueEmail() {
        String email = "mapper-" + UUID.randomUUID() + "@test.com";
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash("unused");
        user.setNickname("하늘");
        user.setTermsAgreedAt(SeoulTime.now());
        user.setPrivacyAgreedAt(SeoulTime.now());
        users.insert(user);
        assertNotNull(user.getId());
        assertTrue(users.existsByEmail(email));
        assertEquals(user.getId(), users.findByEmail(email).getId());
        assertEquals(email, users.findById(user.getId()).getEmail());
        assertNotNull(users.findById(user.getId()).getTermsAgreedAt());
        assertNotNull(users.findById(user.getId()).getPrivacyAgreedAt());
        assertThrows(DuplicateKeyException.class, () -> users.insert(user));
    }
}
