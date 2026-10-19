package com.sapphire.hawe.group.dao;

import com.sapphire.hawe.group.domain.Group;
import com.sapphire.hawe.group.domain.GroupMember;
import com.sapphire.hawe.group.domain.GroupMissionSlot;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
class GroupMapperTests {

    @Autowired JdbcTemplate jdbc;
    @Autowired GroupMapper groups;
    @Autowired GroupMemberMapper members;
    @Autowired GroupMissionSlotMapper slots;

    @Test
    void groupSettingsAndMissionSlotsRoundTrip() {
        Long userId = insertUser();
        Long groupId = insertGroup(userId);
        Group stored = groups.findById(groupId);
        assertEquals("CASUAL", stored.getRelationType());
        assertEquals(1, stored.getDailyMissionCount());

        GroupMissionSlot slot = new GroupMissionSlot();
        slot.setGroupId(groupId);
        slot.setSlotNo(1);
        slot.setPublishTime(LocalTime.of(14, 0));
        slots.insert(slot);
        assertEquals(1, slots.findByGroupId(groupId).size());
        assertEquals(LocalTime.of(14, 0), slots.findByGroupId(groupId).getFirst().getPublishTime());
        slots.deleteByGroupIdAndSlotNoGreaterThan(groupId, 0);
        assertTrue(slots.findByGroupId(groupId).isEmpty());

        slots.insert(slot);
        slots.deleteByGroupId(groupId);
        assertTrue(slots.findByGroupId(groupId).isEmpty());
    }

    @Test
    void summariesAndMembersMapSubmissionValues() {
        Long userId = insertUser();
        Long groupId = insertGroup(userId);
        insertMember(groupId, userId);
        jdbc.update("INSERT INTO missions (group_id, content, category, mission_date, template, params, published_at, deadline_at) "
                + "VALUES (?, 'Test mission', 'TEST', CURDATE(), 'PHOTO_NOW', '{}', NOW(), NOW() + INTERVAL 1 HOUR)", groupId);
        Long missionId = jdbc.queryForObject("SELECT MAX(id) FROM missions WHERE group_id = ?", Long.class, groupId);
        jdbc.update("INSERT INTO submissions (mission_id, user_id, media_url) VALUES (?, ?, 'test.png')",
                missionId, userId);

        var summaries = groups.findSummariesByUserId(userId, 0L, 20);
        assertEquals(1, summaries.size());

        var memberRows = members.findMembersByGroupId(groupId, 0L, 20);
        assertEquals(1, memberRows.size());
        assertEquals(1, memberRows.getFirst().getSubmissionCountThisMonth());
        assertTrue(memberRows.getFirst().isStampedToday());
        assertEquals(1, groups.countByUserId(userId));
        assertEquals(1, members.countByGroupId(groupId));
        assertTrue(groups.findSummariesByUserId(userId, 214748364700L, 100).isEmpty());
        assertTrue(members.findMembersByGroupId(groupId, 214748364700L, 100).isEmpty());
    }

    @Test
    void membershipPairIsUnique() {
        Long userId = insertUser();
        Long groupId = insertGroup(userId);
        insertMember(groupId, userId);

        assertThrows(DuplicateKeyException.class, () -> insertMember(groupId, userId));
        assertFalse(members.findMembersByGroupId(groupId, 0L, 20).isEmpty());
    }

    @Test
    void inviteCodeUpdateImmediatelyInvalidatesOldCodeAndEnforcesUniqueness() {
        Long owner = insertUser();
        Long first = insertGroup(owner);
        Long second = insertGroup(owner);
        String oldCode = groups.findById(first).getInviteCode();
        String secondCode = groups.findById(second).getInviteCode();
        assertEquals(1, groups.updateInviteCode(first, "K7Q2PX"));
        assertEquals(null, groups.findByInviteCode(oldCode));
        assertEquals(first, groups.findByInviteCode("K7Q2PX").getId());
        assertThrows(DuplicateKeyException.class, () -> groups.updateInviteCode(first, secondCode));
    }

    @Test
    void fileKeyQueriesStayWithinTheRequestedGroupAndIgnoreNulls() {
        Long owner = insertUser();
        Long second = insertUser();
        Long target = insertGroup(owner);
        Long other = insertGroup(second);
        insertMember(target, owner);
        insertMember(other, second);
        jdbc.update("INSERT INTO group_members (group_id,user_id,role) VALUES (?,?,'member')", target, second);
        jdbc.update("UPDATE group_members SET profile_image_url_override=? WHERE group_id=? AND user_id=?",
                "target-profile.jpg", target, owner);
        jdbc.update("UPDATE group_members SET profile_image_url_override=? WHERE group_id=? AND user_id=?",
                "other-profile.jpg", other, second);
        Long targetMission = insertMission(target);
        Long otherMission = insertMission(other);
        jdbc.update("INSERT INTO submissions (mission_id,user_id,media_url) VALUES (?,?,?), (?,?,NULL)",
                targetMission, owner, "target-submission.jpg", targetMission, second);
        jdbc.update("INSERT INTO submissions (mission_id,user_id,media_url) VALUES (?,?,?)",
                otherMission, second, "other-submission.jpg");
        Long targetSubmission = jdbc.queryForObject(
                "SELECT id FROM submissions WHERE mission_id=? AND user_id=?", Long.class, targetMission, owner);
        Long nullSubmission = jdbc.queryForObject(
                "SELECT id FROM submissions WHERE mission_id=? AND user_id=?", Long.class, targetMission, second);
        Long otherSubmission = jdbc.queryForObject(
                "SELECT id FROM submissions WHERE mission_id=?", Long.class, otherMission);
        jdbc.update("INSERT INTO comments (submission_id,user_id,content,image_url) VALUES (?,?,'yes',?)",
                targetSubmission, owner, "target-comment.jpg");
        jdbc.update("INSERT INTO comments (submission_id,user_id,content) VALUES (?,?,'no')", nullSubmission, second);
        jdbc.update("INSERT INTO comments (submission_id,user_id,content,image_url) VALUES (?,?,'other',?)",
                otherSubmission, second, "other-comment.jpg");

        assertEquals(List.of("target-submission.jpg"), groups.findSubmissionMediaKeysByGroupId(target));
        assertEquals(List.of("target-comment.jpg"), groups.findCommentImageKeysByGroupId(target));
        assertEquals(List.of("target-profile.jpg"), groups.findGroupProfileImageKeysByGroupId(target));
        assertEquals(List.of("other-submission.jpg"), groups.findSubmissionMediaKeysByGroupId(other));
        assertEquals(List.of("other-comment.jpg"), groups.findCommentImageKeysByGroupId(other));
        assertEquals(List.of("other-profile.jpg"), groups.findGroupProfileImageKeysByGroupId(other));
    }

    private Long insertMission(Long groupId) {
        jdbc.update("INSERT INTO missions (group_id,content,category,mission_date,template,params,published_at,deadline_at) "
                + "VALUES (?,'Test','TEST',CURDATE(),'PHOTO_NOW','{}',NOW(),NOW() + INTERVAL 1 HOUR)", groupId);
        return jdbc.queryForObject("SELECT id FROM missions WHERE group_id=?", Long.class, groupId);
    }

    private Long insertUser() {
        String email = "test-" + UUID.randomUUID() + "@test.com";
        jdbc.update("INSERT INTO users (nickname, email, password_hash, terms_agreed_at, privacy_agreed_at) VALUES (?, ?, ?, NOW(), NOW())",
                "Test", email, "unused");
        return jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
    }

    private Long insertGroup(Long ownerId) {
        Group group = new Group();
        group.setOwnerId(ownerId);
        group.setName("Test group");
        group.setRelationType("CASUAL");
        group.setDailyMissionCount(1);
        group.setInviteCode(UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase());
        groups.insert(group);
        return group.getId();
    }

    private void insertMember(Long groupId, Long userId) {
        GroupMember member = new GroupMember();
        member.setGroupId(groupId);
        member.setUserId(userId);
        member.setRole(GroupMember.ROLE_OWNER);
        members.insert(member);
    }
}
