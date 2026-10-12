package com.sapphire.hawe.comment.dao;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class CommentMapperTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired CommentMapper comments;

    @Test void insertsPagesCountsMentionsAndDeletes() {
        Long author = user();
        Long mentioned = user();
        Long group = group(author);
        Long mission = mission(group);
        Long firstSubmission = submission(mission, author);
        Long secondSubmission = submission(mission, mentioned);
        LocalDateTime at = LocalDateTime.of(2026, 9, 28, 21, 10);
        CommentInsertRow first = new CommentInsertRow(firstSubmission, author, "hello", "comments/a.jpg", at);
        CommentInsertRow second = new CommentInsertRow(firstSubmission, mentioned, "next", null, at);
        comments.insert(first);
        comments.insert(second);
        comments.insertMention(first.getId(), mentioned);
        comments.insertMention(first.getId(), author);
        assertNotNull(first.getId());
        assertEquals("hello", comments.findDetailById(first.getId()).getContent());
        assertEquals(author, comments.findDetailById(first.getId()).getUserId());
        assertEquals("comments/a.jpg", comments.findDetailById(first.getId()).getImageUrl());
        assertEquals(List.of(first.getId()), comments.findPageBySubmissionId(firstSubmission, 0, 1)
                .stream().map(CommentRow::getId).toList());
        assertEquals(List.of(second.getId()), comments.findPageBySubmissionId(firstSubmission, 1, 1)
                .stream().map(CommentRow::getId).toList());
        assertTrue(comments.findPageBySubmissionId(secondSubmission, 0, 20).isEmpty());
        assertEquals(List.of(mentioned, author), comments.findMentionsByCommentIds(List.of(first.getId()))
                .stream().map(CommentMentionRow::getMentionedUserId).toList());
        assertEquals(2, comments.countBySubmissionId(firstSubmission));
        assertEquals(0, comments.countBySubmissionId(secondSubmission));
        assertEquals(firstSubmission, comments.countBySubmissionIds(List.of(firstSubmission, secondSubmission))
                .getFirst().getSubmissionId());
        assertEquals(2, comments.countBySubmissionIds(List.of(firstSubmission, secondSubmission))
                .getFirst().getCount());
        comments.deleteMentionsByCommentId(first.getId());
        comments.deleteById(first.getId());
        assertNull(comments.findDetailById(first.getId()));
        assertTrue(comments.findMentionsByCommentIds(List.of(first.getId())).isEmpty());
    }

    private Long user() {
        String email = UUID.randomUUID() + "@test.com";
        jdbc.update("INSERT INTO users (email, nickname, password_hash, terms_agreed_at, privacy_agreed_at) "
                + "VALUES (?, 'Test', 'unused', NOW(), NOW())", email);
        return jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
    }
    private Long group(Long owner) {
        String code = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        jdbc.update("INSERT INTO `groups` (name, owner_id, invite_code) VALUES ('Comment test', ?, ?)", owner, code);
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
