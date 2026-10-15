package com.sapphire.hawe.withdraw.controller;

import com.sapphire.hawe.global.security.JwtTokenProvider;
import com.sapphire.hawe.global.storage.FileStorage;
import com.sapphire.hawe.group.service.GroupService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class WithdrawControllerTests {
    static final Path uploads;
    static { try { uploads = Files.createTempDirectory("hawe-withdraw-test-"); }
        catch (Exception e) { throw new ExceptionInInitializerError(e); } }
    @DynamicPropertySource static void storage(DynamicPropertyRegistry registry) {
        registry.add("hawe.storage.local-dir", () -> uploads.toString());
    }
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtTokenProvider tokens;
    @Autowired FileStorage storage;
    @Autowired GroupService groups;

    @Test void memberWithdrawalDeletesDependentDataAndInvalidatesToken() throws Exception {
        Long owner = user(), leaving = user();
        Long group = group(owner);
        String email = jdbc.queryForObject("SELECT email FROM users WHERE id=?", String.class, leaving);
        List<String> keys = new ArrayList<>();
        try {
            member(group, owner, "owner"); member(group, leaving, "member");
            Long mission = mission(group);
            String submissionKey = file("submissions"); keys.add(submissionKey);
            String ownCommentKey = file("comments"); keys.add(ownCommentKey);
            String otherCommentKey = file("comments"); keys.add(otherCommentKey);
            String profileKey = file("profiles"); keys.add(profileKey);
            jdbc.update("UPDATE users SET profile_image_url=? WHERE id=?", profileKey, leaving);
            jdbc.update("INSERT INTO submissions (mission_id,user_id,media_url) VALUES (?,?,?)", mission, leaving, submissionKey);
            jdbc.update("INSERT INTO submissions (mission_id,user_id) VALUES (?,?)", mission, owner);
            Long mine = id("SELECT id FROM submissions WHERE user_id=?", leaving);
            Long theirs = id("SELECT id FROM submissions WHERE user_id=?", owner);
            jdbc.update("INSERT INTO submission_ai_reviews (submission_id,status) VALUES (?,'DONE')", mine);
            jdbc.update("INSERT INTO comments (submission_id,user_id,content,image_url) VALUES (?,?,'other',?)", mine, owner, otherCommentKey);
            jdbc.update("INSERT INTO comments (submission_id,user_id,content,image_url) VALUES (?,?,'mine',?)", theirs, leaving, ownCommentKey);
            Long myComment = id("SELECT id FROM comments WHERE user_id=?", leaving);
            jdbc.update("INSERT INTO comments (submission_id,user_id,content) VALUES (?,?,'keep')", theirs, owner);
            Long keptComment = id("SELECT id FROM comments WHERE content='keep' AND user_id=?", owner);
            jdbc.update("INSERT INTO comment_mentions (comment_id,mentioned_user_id) VALUES (?,?)", myComment, owner);
            jdbc.update("INSERT INTO comment_mentions (comment_id,mentioned_user_id) VALUES (?,?)", keptComment, leaving);
            jdbc.update("INSERT INTO reactions (submission_id,user_id,sticker_type) VALUES (?,?,'heart')", mine, owner);
            jdbc.update("INSERT INTO reactions (submission_id,user_id,sticker_type) VALUES (?,?,'fire')", theirs, leaving);
            jdbc.update("INSERT INTO submission_guesses (submission_id,user_id,guess_text,is_correct) VALUES (?,?,'a',0)", mine, owner);
            jdbc.update("INSERT INTO submission_guesses (submission_id,user_id,guess_text,is_correct) VALUES (?,?,'b',0)", theirs, leaving);
            jdbc.update("INSERT INTO pokes (from_user_id,to_user_id,group_id,mission_id,type) VALUES (?,?,?,?,'poke')", owner, leaving, group, mission);
            jdbc.update("INSERT INTO notifications (from_user_id,to_user_id,type) VALUES (?,?,'comment')", leaving, owner);
            String auth = auth(leaving);

            mvc.perform(delete("/api/me")).andExpect(status().isUnauthorized());
            mvc.perform(delete("/api/me").header("Authorization", auth)).andExpect(status().isNoContent());
            mvc.perform(get("/api/me").header("Authorization", auth)).andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
            assertEquals(0, count("SELECT COUNT(*) FROM users WHERE id=?", leaving));
            assertEquals(0, count("SELECT COUNT(*) FROM submissions WHERE id=?", mine));
            assertEquals(0, count("SELECT COUNT(*) FROM submission_ai_reviews WHERE submission_id=?", mine));
            assertEquals(1, count("SELECT COUNT(*) FROM submissions WHERE id=?", theirs));
            assertEquals(1, count("SELECT COUNT(*) FROM comments WHERE id=?", keptComment));
            assertEquals(0, count("SELECT COUNT(*) FROM comment_mentions WHERE mentioned_user_id=?", leaving));
            assertEquals(0, count("SELECT COUNT(*) FROM pokes WHERE to_user_id=?", leaving));
            for (String key : keys) assertFalse(Files.exists(uploads.resolve(key)), key);
            mvc.perform(post("/api/auth/register").contentType("application/json")
                    .content("{\"email\":\"" + email + "\",\"password\":\"abcd1234\",\"nickname\":\"Again\","
                            + "\"agreeTerms\":true,\"agreePrivacy\":true}"))
                    .andExpect(status().isCreated());
            assertNotEquals(leaving, id("SELECT id FROM users WHERE email=?", email));
        } finally {
            if (count("SELECT COUNT(*) FROM users WHERE id=?", leaving) > 0)
                groups.leaveGroup(leaving, group, null);
            if (count("SELECT COUNT(*) FROM `groups` WHERE id=?", group) > 0)
                groups.leaveGroup(owner, group, null);
            jdbc.update("DELETE FROM users WHERE id IN (?,?)", owner, leaving);
            jdbc.update("DELETE FROM users WHERE email=?", email);
            for (String key : keys) storage.delete(key);
        }
    }

    @Test void ownerWithOtherMemberMustTransferAndSingletonOwnerCanWithdraw() throws Exception {
        Long owner = user(), friend = user();
        Long group = group(owner);
        try {
            member(group, owner, "owner"); member(group, friend, "member");
            mvc.perform(delete("/api/me").header("Authorization", auth(owner)))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("OWNER_MUST_TRANSFER"));
            assertEquals(1, count("SELECT COUNT(*) FROM users WHERE id=?", owner));
            groups.leaveGroup(friend, group, null);
            mvc.perform(delete("/api/me").header("Authorization", auth(owner))).andExpect(status().isNoContent());
            assertEquals(0, count("SELECT COUNT(*) FROM `groups` WHERE id=?", group));
            assertEquals(0, count("SELECT COUNT(*) FROM users WHERE id=?", owner));
        } finally {
            if (count("SELECT COUNT(*) FROM `groups` WHERE id=?", group) > 0) {
                if (count("SELECT COUNT(*) FROM group_members WHERE group_id=? AND user_id=?", group, friend) > 0)
                    groups.leaveGroup(friend, group, null);
                groups.leaveGroup(owner, group, null);
            }
            jdbc.update("DELETE FROM users WHERE id IN (?,?)", owner, friend);
        }
    }

    @Test void userWithoutGroupsCanWithdraw() throws Exception {
        Long user = user();
        try {
            mvc.perform(delete("/api/me").header("Authorization", auth(user))).andExpect(status().isNoContent());
            mvc.perform(delete("/api/me").header("Authorization", auth(user))).andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
        } finally { jdbc.update("DELETE FROM users WHERE id=?", user); }
    }

    private Long user() {
        String email = UUID.randomUUID() + "@test.com";
        jdbc.update("INSERT INTO users (email,nickname,password_hash,terms_agreed_at,privacy_agreed_at) VALUES (?,'Test','unused',NOW(),NOW())", email);
        return jdbc.queryForObject("SELECT id FROM users WHERE email=?", Long.class, email);
    }
    private Long group(Long owner) {
        String code = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        jdbc.update("INSERT INTO `groups` (name,owner_id,invite_code) VALUES ('Withdraw',?,?)", owner, code);
        return jdbc.queryForObject("SELECT id FROM `groups` WHERE invite_code=?", Long.class, code);
    }
    private void member(Long group, Long user, String role) {
        jdbc.update("INSERT INTO group_members (group_id,user_id,role) VALUES (?,?,?)", group, user, role);
    }
    private Long mission(Long group) {
        jdbc.update("INSERT INTO missions (group_id,content,category,mission_date,template,params,published_at,deadline_at) "
                + "VALUES (?,'Test','TEST',CURDATE(),'PHOTO_NOW','{}',NOW(),NOW()+INTERVAL 1 HOUR)", group);
        return id("SELECT id FROM missions WHERE group_id=?", group);
    }
    private String file(String directory) { return storage.store(directory, new byte[]{1, 2, 3}, "jpg"); }
    private String auth(Long user) { return "Bearer " + tokens.createAccessToken(user, "Test"); }
    private Long id(String sql, Object argument) { return jdbc.queryForObject(sql, Long.class, argument); }
    private int count(String sql, Object... args) { return jdbc.queryForObject(sql, Integer.class, args); }
}
