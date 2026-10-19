package com.sapphire.hawe.group.service;

import com.sapphire.hawe.global.storage.FileStorage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class GroupFileCleanupIntegrationTests {
    static final Path uploads;
    static { try { uploads = Files.createTempDirectory("hawe-group-cleanup-"); }
        catch (Exception e) { throw new ExceptionInInitializerError(e); } }
    @DynamicPropertySource static void storagePath(DynamicPropertyRegistry registry) {
        registry.add("hawe.storage.local-dir", () -> uploads.toString());
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired GroupService groups;
    @Autowired FileStorage storage;

    @Test void deletingOneGroupRemovesItsThreeFileKindsButKeepsTheOtherGroupsFiles() {
        Long ownerA = user(), ownerB = user();
        Long groupA = group(ownerA), groupB = group(ownerB);
        List<String> keys = new ArrayList<>();
        try {
            member(groupA, ownerA, "owner");
            member(groupB, ownerB, "owner");
            String aSubmission = file("submissions", keys), aComment = file("comments", keys), aProfile = file("profiles", keys);
            String bSubmission = file("submissions", keys), bComment = file("comments", keys), bProfile = file("profiles", keys);
            filesForGroup(groupA, ownerA, aSubmission, aComment, aProfile);
            filesForGroup(groupB, ownerB, bSubmission, bComment, bProfile);

            groups.leaveGroup(ownerA, groupA, null);

            for (String key : List.of(aSubmission, aComment, aProfile)) assertFalse(Files.exists(uploads.resolve(key)), key);
            for (String key : List.of(bSubmission, bComment, bProfile)) assertTrue(Files.exists(uploads.resolve(key)), key);
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM `groups` WHERE id=?", Integer.class, groupB));
        } finally {
            if (exists(groupA)) groups.leaveGroup(ownerA, groupA, null);
            if (exists(groupB)) groups.leaveGroup(ownerB, groupB, null);
            jdbc.update("DELETE FROM users WHERE id IN (?,?)", ownerA, ownerB);
            for (String key : keys) storage.delete(key);
        }
    }

    @Test void memberAndSuccessorOwnerLeaveOnlyRemoveTheirGroupProfileFiles() {
        Long owner = user(), member = user(), successor = user();
        Long group = group(owner);
        List<String> keys = new ArrayList<>();
        try {
            member(group, owner, "owner");
            member(group, member, "member");
            member(group, successor, "member");
            String memberProfile = file("profiles", keys), ownerProfile = file("profiles", keys);
            String memberWork = file("submissions", keys), ownerWork = file("submissions", keys);
            jdbc.update("UPDATE group_members SET profile_image_url_override=? WHERE group_id=? AND user_id=?",
                    memberProfile, group, member);
            jdbc.update("UPDATE group_members SET profile_image_url_override=? WHERE group_id=? AND user_id=?",
                    ownerProfile, group, owner);
            Long mission = mission(group);
            jdbc.update("INSERT INTO submissions (mission_id,user_id,media_url) VALUES (?,?,?), (?,?,?)",
                    mission, member, memberWork, mission, owner, ownerWork);

            groups.leaveGroup(member, group, null);
            assertFalse(Files.exists(uploads.resolve(memberProfile)));
            assertTrue(Files.exists(uploads.resolve(ownerProfile)));
            assertTrue(Files.exists(uploads.resolve(memberWork)));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM submissions WHERE mission_id=? AND user_id=?",
                    Integer.class, mission, member));

            groups.leaveGroup(owner, group, successor);
            assertFalse(Files.exists(uploads.resolve(ownerProfile)));
            assertTrue(Files.exists(uploads.resolve(memberWork)));
            assertTrue(Files.exists(uploads.resolve(ownerWork)));
            assertEquals(successor, jdbc.queryForObject("SELECT owner_id FROM `groups` WHERE id=?", Long.class, group));
        } finally {
            if (exists(group)) cleanupGroup(group);
            jdbc.update("DELETE FROM users WHERE id IN (?,?,?)", owner, member, successor);
            for (String key : keys) storage.delete(key);
        }
    }

    private Long user() {
        String email = UUID.randomUUID() + "@test.com";
        jdbc.update("INSERT INTO users (nickname,email,password_hash,terms_agreed_at,privacy_agreed_at) "
                + "VALUES ('Test',?,'unused',NOW(),NOW())", email);
        return jdbc.queryForObject("SELECT id FROM users WHERE email=?", Long.class, email);
    }

    private Long group(Long owner) {
        String code = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        jdbc.update("INSERT INTO `groups` (name,owner_id,invite_code) VALUES ('Test',?,?)", owner, code);
        return jdbc.queryForObject("SELECT id FROM `groups` WHERE invite_code=?", Long.class, code);
    }

    private void member(Long group, Long user, String role) {
        jdbc.update("INSERT INTO group_members (group_id,user_id,role) VALUES (?,?,?)", group, user, role);
    }

    private Long mission(Long group) {
        jdbc.update("INSERT INTO missions (group_id,content,category,mission_date,template,params,published_at,deadline_at) "
                + "VALUES (?,'Photo','TEST',CURDATE(),'PHOTO_NOW','{}',NOW(),NOW() + INTERVAL 1 HOUR)", group);
        return jdbc.queryForObject("SELECT id FROM missions WHERE group_id=?", Long.class, group);
    }

    private void filesForGroup(Long group, Long owner, String submissionKey, String commentKey, String profileKey) {
        jdbc.update("UPDATE group_members SET profile_image_url_override=? WHERE group_id=? AND user_id=?",
                profileKey, group, owner);
        Long mission = mission(group);
        jdbc.update("INSERT INTO submissions (mission_id,user_id,media_url) VALUES (?,?,?)", mission, owner, submissionKey);
        Long submission = jdbc.queryForObject("SELECT id FROM submissions WHERE mission_id=?", Long.class, mission);
        jdbc.update("INSERT INTO comments (submission_id,user_id,content,image_url) VALUES (?,?,'photo',?)",
                submission, owner, commentKey);
    }

    private String file(String directory, List<String> keys) {
        String key = storage.store(directory, new byte[] {1, 2, 3}, "jpg");
        keys.add(key);
        return key;
    }

    private boolean exists(Long group) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM `groups` WHERE id=?", Integer.class, group) > 0;
    }

    private void cleanupGroup(Long group) {
        List<Long> ordinary = jdbc.queryForList(
                "SELECT user_id FROM group_members WHERE group_id=? AND role <> 'owner'", Long.class, group);
        for (Long user : ordinary) groups.leaveGroup(user, group, null);
        Long owner = jdbc.queryForObject("SELECT owner_id FROM `groups` WHERE id=?", Long.class, group);
        groups.leaveGroup(owner, group, null);
    }
}
