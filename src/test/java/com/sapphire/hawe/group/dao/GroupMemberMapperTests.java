package com.sapphire.hawe.group.dao;

import java.util.List;
import java.util.UUID;
import com.sapphire.hawe.comment.dao.CommentMapper;
import com.sapphire.hawe.submission.dao.SubmissionMapper;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class GroupMemberMapperTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired GroupMemberMapper members;
    @Autowired GroupMapper groups;
    @Autowired SubmissionMapper submissions;
    @Autowired CommentMapper comments;
    @Autowired SqlSessionTemplate sqlSession;

    @Test
    void groupProfileSwitchesAsOneUnitAndStaysInItsGroup() {
        String email = UUID.randomUUID() + "@test.com";
        jdbc.update("INSERT INTO users (email, nickname, password_hash, profile_image_url, terms_agreed_at, privacy_agreed_at) "
                + "VALUES (?, 'Default', 'unused', 'base.jpg', NOW(), NOW())", email);
        Long user = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
        String codeA = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        String codeB = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        jdbc.update("INSERT INTO `groups` (name, owner_id, invite_code) VALUES ('A', ?, ?), ('B', ?, ?)",
                user, codeA, user, codeB);
        Long a = jdbc.queryForObject("SELECT id FROM `groups` WHERE invite_code = ?", Long.class, codeA);
        Long b = jdbc.queryForObject("SELECT id FROM `groups` WHERE invite_code = ?", Long.class, codeB);
        jdbc.update("INSERT INTO group_members (group_id, user_id, role) VALUES (?, ?, 'owner'), (?, ?, 'owner')",
                a, user, b, user);
        Long character = jdbc.queryForObject("SELECT id FROM characters ORDER BY id LIMIT 1", Long.class);
        String characterKey = jdbc.queryForObject("SELECT svg_key FROM characters WHERE id = ?", String.class, character);

        assertFalse(members.findMembersByGroupId(a, 0, 20).getFirst().isGroupProfile());
        assertEquals("Default", groups.findMemberPreviewsByGroupIds(List.of(a)).getFirst().getNickname());
        members.setGroupProfile(a, user, "Group", character, null);
        var characterRow = members.findMembersByGroupId(a, 0, 20).getFirst();
        assertTrue(characterRow.isGroupProfile());
        assertEquals("Group", characterRow.getNickname());
        assertEquals(characterKey, characterRow.getCharacterKey());
        assertNull(characterRow.getProfileImageUrl());
        assertEquals(character, members.findByGroupIdAndUserId(a, user).getCharacterIdOverride());
        assertEquals("Group", groups.findMemberPreviewsByGroupIds(List.of(a)).getFirst().getNickname());

        members.setGroupProfile(a, user, "Photo", null, "group.jpg");
        var photoRow = members.findMembersByGroupId(a, 0, 20).getFirst();
        assertEquals("Photo", photoRow.getNickname());
        assertNull(photoRow.getCharacterKey());
        assertEquals("group.jpg", photoRow.getProfileImageUrl());
        assertEquals("Default", members.findMembersByGroupId(b, 0, 20).getFirst().getNickname());
        assertEquals("Default", groups.findMemberPreviewsByGroupIds(List.of(b)).getFirst().getNickname());

        members.setGroupProfile(a, user, null, null, null);
        var restored = members.findMembersByGroupId(a, 0, 20).getFirst();
        assertFalse(restored.isGroupProfile());
        assertEquals("Default", restored.getNickname());
        assertEquals("base.jpg", restored.getProfileImageUrl());
    }

    @Test
    void monthlyCountIsSubmittedDaysAndStampFollowsCurrentMissions() {
        String email = UUID.randomUUID() + "@test.com";
        jdbc.update("INSERT INTO users (email, nickname, password_hash, terms_agreed_at, privacy_agreed_at) "
                + "VALUES (?, 'Member', 'unused', NOW(), NOW())", email);
        Long user = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
        String code = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        jdbc.update("INSERT INTO `groups` (name, owner_id, invite_code, daily_mission_count) VALUES ('Daily', ?, ?, 2)",
                user, code);
        Long group = jdbc.queryForObject("SELECT id FROM `groups` WHERE invite_code = ?", Long.class, code);
        jdbc.update("INSERT INTO group_members (group_id, user_id, role) VALUES (?, ?, 'owner')", group, user);
        // 이번 달 1일에 두 개, 2일에 한 개 (오늘이 며칠이든 이번 달 안)
        Long firstDaySlot1 = mission(group, "DATE_FORMAT(CURDATE(), '%Y-%m-01')", 1);
        Long firstDaySlot2 = mission(group, "DATE_FORMAT(CURDATE(), '%Y-%m-01')", 2);
        Long secondDaySlot1 = mission(group, "DATE_FORMAT(CURDATE(), '%Y-%m-02')", 1);
        jdbc.update("INSERT INTO submissions (mission_id, user_id) VALUES (?, ?), (?, ?)",
                firstDaySlot1, user, firstDaySlot2, user);
        assertEquals(1, members.findMembersByGroupId(group, 0, 20).getFirst().getSubmissionCountThisMonth());
        // 지금 열린 미션(2일 1번, 1일 2번) 중 2번에 냈으니 도장
        assertTrue(members.findMembersByGroupId(group, 0, 20).getFirst().isStampedToday());

        jdbc.update("INSERT INTO submissions (mission_id, user_id) VALUES (?, ?)", secondDaySlot1, user);
        sqlSession.clearCache();  // JdbcTemplate 로 바꾼 행이 같은 조회에 보이게
        assertEquals(2, members.findMembersByGroupId(group, 0, 20).getFirst().getSubmissionCountThisMonth());

        // 하루 1개로 줄이면 2일에 1번이 열렸으므로 1일 2번은 닫힌 미션이다 — 2일 1번 제출만 남는다
        jdbc.update("DELETE FROM submissions WHERE mission_id = ?", secondDaySlot1);
        jdbc.update("UPDATE `groups` SET daily_mission_count = 1 WHERE id = ?", group);
        sqlSession.clearCache();
        assertFalse(members.findMembersByGroupId(group, 0, 20).getFirst().isStampedToday());
    }

    private Long mission(Long group, String date, int slotNo) {
        jdbc.update("INSERT INTO missions (group_id, content, category, mission_date, template, params, slot_no, "
                + "published_at, deadline_at) VALUES (?, 'Test', 'TEST', " + date + ", 'PHOTO_NOW', '{}', ?, "
                + date + ", " + date + " + INTERVAL 23 HOUR)", group, slotNo);
        return jdbc.queryForObject("SELECT id FROM missions WHERE group_id = ? AND mission_date = " + date
                + " AND slot_no = ?", Long.class, group, slotNo);
    }

    @Test
    void feedAndCommentsUseTheirAuthorsProfilesThenFallBackAfterLeaving() {
        String firstEmail = UUID.randomUUID() + "@test.com";
        String secondEmail = UUID.randomUUID() + "@test.com";
        jdbc.update("INSERT INTO users (email, nickname, password_hash, terms_agreed_at, privacy_agreed_at) "
                + "VALUES (?, 'Writer', 'unused', NOW(), NOW()), (?, 'Commenter', 'unused', NOW(), NOW())",
                firstEmail, secondEmail);
        Long writer = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, firstEmail);
        Long commenter = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, secondEmail);
        String code = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        jdbc.update("INSERT INTO `groups` (name, owner_id, invite_code) VALUES ('Test', ?, ?)", writer, code);
        Long group = jdbc.queryForObject("SELECT id FROM `groups` WHERE invite_code = ?", Long.class, code);
        jdbc.update("INSERT INTO group_members (group_id, user_id, role) VALUES (?, ?, 'owner'), (?, ?, 'member')",
                group, writer, group, commenter);
        jdbc.update("INSERT INTO missions (group_id, content, category, mission_date, template, params, published_at, deadline_at) "
                + "VALUES (?, 'Test', 'TEST', CURDATE(), 'PHOTO_NOW', '{}', NOW(), NOW() + INTERVAL 1 HOUR)", group);
        Long mission = jdbc.queryForObject("SELECT id FROM missions WHERE group_id = ?", Long.class, group);
        jdbc.update("INSERT INTO submissions (mission_id, user_id) VALUES (?, ?)", mission, writer);
        Long submission = jdbc.queryForObject("SELECT id FROM submissions WHERE mission_id = ?", Long.class, mission);
        jdbc.update("INSERT INTO comments (submission_id, user_id, content) VALUES (?, ?, 'hello')", submission, commenter);
        Long comment = jdbc.queryForObject("SELECT id FROM comments WHERE submission_id = ?", Long.class, submission);
        Long character = jdbc.queryForObject("SELECT id FROM characters ORDER BY id LIMIT 1", Long.class);
        members.setGroupProfile(group, writer, "Group Writer", character, null);
        members.setGroupProfile(group, commenter, "Group Commenter", null, "comment.jpg");

        assertEquals("Group Writer", submissions.findDetailById(submission).getNickname());
        assertEquals("Group Writer", submissions.findFeedByMissionId(mission, 0, 20).getFirst().getNickname());
        assertEquals("Group Commenter", comments.findDetailById(comment).getNickname());
        assertEquals("comment.jpg", comments.findPageBySubmissionId(submission, 0, 20).getFirst().getProfileImageUrl());
        members.deleteByGroupIdAndUserId(group, commenter);
        assertEquals("Commenter", comments.findDetailById(comment).getNickname());
        members.deleteByGroupIdAndUserId(group, writer);
        assertEquals("Writer", submissions.findDetailById(submission).getNickname());
    }
}
