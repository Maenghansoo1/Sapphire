package com.sapphire.hawe.group.dao;

import com.sapphire.hawe.group.domain.Group;
import com.sapphire.hawe.group.domain.GroupMember;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.UUID;

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

    @Test
    void summariesAndMembersMapSubmissionValues() {
        Long userId = insertUser();
        Long groupId = insertGroup(userId);
        insertMember(groupId, userId);
        jdbc.update("INSERT INTO missions (group_id, content, category, mission_date) "
                + "VALUES (?, 'Test mission', 'TEST', CURDATE())", groupId);
        Long missionId = jdbc.queryForObject("SELECT MAX(id) FROM missions WHERE group_id = ?", Long.class, groupId);
        jdbc.update("INSERT INTO submissions (mission_id, user_id, image_url) VALUES (?, ?, 'test.png')",
                missionId, userId);

        var summaries = groups.findSummariesByUserId(userId, 0L, 20);
        assertEquals(1, summaries.size());
        assertTrue(summaries.getFirst().isSubmittedToday());

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

    private Long insertUser() {
        String loginId = "test-" + UUID.randomUUID();
        jdbc.update("INSERT INTO users (nickname, login_id, password_hash) VALUES (?, ?, ?)",
                "Test", loginId, "unused");
        return jdbc.queryForObject("SELECT id FROM users WHERE login_id = ?", Long.class, loginId);
    }

    private Long insertGroup(Long ownerId) {
        Group group = new Group();
        group.setOwnerId(ownerId);
        group.setName("Test group");
        group.setMissionPublishTime(LocalTime.of(9, 0));
        group.setMissionDeadlineTime(LocalTime.of(23, 0));
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
