package com.sapphire.hawe.group.dao;

import com.sapphire.hawe.group.domain.Group;
import com.sapphire.hawe.group.domain.GroupMember;
import com.sapphire.hawe.group.service.GroupService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class GroupContractMapperTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired GroupMapper groups;
    @Autowired GroupMemberMapper members;
    @Autowired GroupService service;

    @Test
    void inviteCodeIsUniqueAndStartDateRoundTrips() {
        Long owner = user("Owner");
        Long first = group(owner, "K7Q2PX", "COUPLE", LocalDate.of(2025, 1, 1));
        assertEquals(first, groups.findByInviteCode("K7Q2PX").getId());
        assertEquals(first, groups.findByInviteCodeForUpdate("K7Q2PX").getId());
        assertEquals(LocalDate.of(2025, 1, 1), groups.findById(first).getStartDate());
        assertEquals("K7Q2PX", groups.findById(first).getInviteCode());
        assertThrows(DuplicateKeyException.class, () -> group(owner, "K7Q2PX", "FAMILY", null));
    }

    @Test
    void summariesFollowJoinOrderAndProfilesFollowMemberOrder() {
        Long owner = user("Owner");
        Long friend = user("Friend");
        Long first = group(owner, "K7Q2PX", "FRIEND", null);
        Long second = group(owner, "M8R3TQ", "FAMILY", null);
        member(first, owner, GroupMember.ROLE_OWNER);
        member(second, owner, GroupMember.ROLE_OWNER);
        member(first, friend, GroupMember.ROLE_MEMBER);
        jdbc.update("UPDATE group_members SET joined_at = '2025-01-01 12:00:00' WHERE group_id = ? AND user_id = ?", first, owner);
        jdbc.update("UPDATE group_members SET joined_at = '2025-01-02 12:00:00' WHERE group_id = ? AND user_id = ?", second, owner);
        jdbc.update("UPDATE group_members SET joined_at = '2025-01-03 12:00:00' WHERE group_id = ? AND user_id = ?", first, friend);
        Long characterId = jdbc.queryForObject("SELECT id FROM characters WHERE svg_key = 'mongsil'", Long.class);
        jdbc.update("UPDATE users SET character_id = ? WHERE id = ?", characterId, friend);

        var summaries = groups.findSummariesByUserId(owner, 0, 20);
        assertEquals(List.of(first, second), summaries.stream().map(GroupSummaryRow::getId).toList());
        assertEquals("FRIEND", summaries.getFirst().getRelationType());
        var previews = groups.findMemberPreviewsByGroupIds(List.of(first, second));
        assertEquals(List.of(owner, friend), previews.stream().filter(row -> row.getGroupId().equals(first))
                .map(GroupMemberPreviewRow::getUserId).toList());
        assertNull(previews.getFirst().getCharacterKey());
        assertEquals("mongsil", previews.get(1).getCharacterKey());
        assertEquals("mongsil", members.findMembersByGroupId(first, 0, 20).get(1).getCharacterKey());
    }

    @Test
    void lastOwnerLeaveDeletesDependentsAndPreservesOtherGroup() {
        Long owner = user("Owner");
        Long other = user("Other");
        Long removed = group(owner, "K7Q2PX", "FRIEND", null);
        Long preserved = group(other, "M8R3TQ", "FAMILY", null);
        member(removed, owner, GroupMember.ROLE_OWNER);
        member(preserved, other, GroupMember.ROLE_OWNER);
        jdbc.update("INSERT INTO group_mission_slots (group_id, slot_no, publish_time) VALUES (?, 1, '09:00:00')", removed);
        Long mission = mission(removed);
        Long retainedMission = mission(preserved);
        jdbc.update("INSERT INTO submissions (mission_id, user_id, media_url) VALUES (?, ?, 'test.png')", mission, owner);
        Long submission = jdbc.queryForObject("SELECT id FROM submissions WHERE mission_id = ?", Long.class, mission);
        jdbc.update("INSERT INTO submission_ai_reviews (submission_id,status) VALUES (?,'DONE')", submission);
        jdbc.update("INSERT INTO comments (submission_id, user_id, content) VALUES (?, ?, 'hello')", submission, owner);
        Long comment = jdbc.queryForObject("SELECT id FROM comments WHERE submission_id = ?", Long.class, submission);
        jdbc.update("INSERT INTO comment_mentions (comment_id, mentioned_user_id) VALUES (?, ?)", comment, other);
        jdbc.update("INSERT INTO reactions (submission_id, user_id, sticker_type) VALUES (?, ?, 'HEART')", submission, owner);
        jdbc.update("INSERT INTO submission_guesses (submission_id, user_id, guess_text, is_correct) VALUES (?, ?, 'yes', 0)", submission, other);
        jdbc.update("INSERT INTO pokes (from_user_id, to_user_id, group_id, mission_id, type) VALUES (?, ?, ?, ?, 'poke')",
                owner, other, removed, mission);

        service.leaveGroup(owner, removed, null);
        assertNull(groups.findById(removed));
        for (String table : List.of("group_members", "group_mission_slots", "missions", "pokes")) {
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE group_id = ?", Integer.class, removed));
        }
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM submissions WHERE id = ?", Integer.class, submission));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM submission_ai_reviews WHERE submission_id = ?", Integer.class, submission));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM comments WHERE id = ?", Integer.class, comment));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM comment_mentions WHERE comment_id = ?", Integer.class, comment));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM reactions WHERE submission_id = ?", Integer.class, submission));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM submission_guesses WHERE submission_id = ?", Integer.class, submission));
        assertNotNull(groups.findById(preserved));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM missions WHERE id = ?", Integer.class, retainedMission));
    }

    @Test
    void successorTransferKeepsOneOwnerRole() {
        Long owner = user("Owner");
        Long successor = user("Next");
        Long groupId = group(owner, "K7Q2PX", "FRIEND", null);
        member(groupId, owner, GroupMember.ROLE_OWNER);
        member(groupId, successor, GroupMember.ROLE_MEMBER);
        service.leaveGroup(owner, groupId, successor);
        assertEquals(successor, groups.findById(groupId).getOwnerId());
        assertNull(members.findByGroupIdAndUserId(groupId, owner));
        assertEquals(GroupMember.ROLE_OWNER, members.findByGroupIdAndUserId(groupId, successor).getRole());
        assertEquals(1, members.countByGroupId(groupId));
    }

    private Long user(String nickname) {
        String email = UUID.randomUUID() + "@test.com";
        jdbc.update("INSERT INTO users (email, nickname, password_hash, terms_agreed_at, privacy_agreed_at) "
                + "VALUES (?, ?, 'unused', NOW(), NOW())", email, nickname);
        return jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
    }

    private Long group(Long owner, String code, String relation, LocalDate startDate) {
        Group group = new Group();
        group.setOwnerId(owner);
        group.setName("Group " + code);
        group.setInviteCode(code);
        group.setRelationType(relation);
        group.setStartDate(startDate);
        group.setDailyMissionCount(1);
        groups.insert(group);
        return group.getId();
    }

    private void member(Long group, Long user, String role) {
        GroupMember membership = new GroupMember();
        membership.setGroupId(group);
        membership.setUserId(user);
        membership.setRole(role);
        members.insert(membership);
    }

    private Long mission(Long group) {
        jdbc.update("INSERT INTO missions (group_id, content, category, mission_date, template, params, published_at, deadline_at) "
                + "VALUES (?, 'Test', 'TEST', CURDATE(), 'PHOTO_NOW', '{}', NOW(), NOW() + INTERVAL 1 HOUR)", group);
        return jdbc.queryForObject("SELECT id FROM missions WHERE group_id = ?", Long.class, group);
    }
}
