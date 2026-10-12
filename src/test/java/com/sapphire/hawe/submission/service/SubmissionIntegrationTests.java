package com.sapphire.hawe.submission.service;

import com.sapphire.hawe.global.exception.BusinessException;
import com.sapphire.hawe.global.time.SeoulTime;
import com.sapphire.hawe.group.dto.GroupCreateRequest;
import com.sapphire.hawe.group.dto.MissionSlotRequest;
import com.sapphire.hawe.group.service.GroupService;
import com.sapphire.hawe.mission.service.MissionService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.LinkedMultiValueMap;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class SubmissionIntegrationTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired GroupService groups;
    @Autowired MissionService missions;
    @Autowired SubmissionService submissions;

    @Test void submissionUpdatesMissionAndGroupViewsAndLocksFeed() {
        String email = "submission-" + UUID.randomUUID() + "@test.com";
        jdbc.update("INSERT INTO users (nickname, email, password_hash, terms_agreed_at, privacy_agreed_at) "
                + "VALUES ('Writer', ?, 'unused', NOW(), NOW())", email);
        Long userId = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
        Long groupId = null;
        try {
            groupId = groups.createGroup(userId, new GroupCreateRequest("Submit group", null,
                    null, 1, List.of(new MissionSlotRequest(1, "00:00", null)))).id();
            Long missionId = jdbc.queryForObject("SELECT id FROM missions WHERE group_id = ?", Long.class, groupId);
            jdbc.update("UPDATE missions SET template = 'TEXT_FILL_BLANK', params = '{}', "
                    + "deadline_at = ? WHERE id = ?", SeoulTime.now().minusSeconds(1), missionId);
            Long currentGroupId = groupId;
            var current = missions.getTodayMissions(userId, groupId).missions().getFirst();
            assertEquals(missionId, current.missionId());
            assertFalse(current.submittedByMe());
            assertEquals("FEED_LOCKED", assertThrows(BusinessException.class,
                    () -> submissions.feed(userId, missionId, 0, 20)).errorCode().code());
            var fields = new LinkedMultiValueMap<String, String>(); fields.add("text", "hello");
            var created = submissions.submit(userId, missionId, fields, new LinkedMultiValueMap<>());
            assertEquals("hello", created.text());
            assertTrue(created.isLate());
            assertEquals(1, submissions.feed(userId, missionId, -1, 1000).totalElements());
            assertEquals(100, submissions.feed(userId, missionId, -1, 1000).size());
            assertEquals(created.id(), submissions.detail(userId, created.id()).id());
            current = missions.getTodayMissions(userId, groupId).missions().getFirst();
            assertTrue(current.submittedByMe());
            assertTrue(current.members().getFirst().submitted());
            var summary = groups.listMyGroups(userId, 0, 20).content().stream()
                    .filter(g -> g.id().equals(currentGroupId)).findFirst().orElseThrow();
            assertTrue(summary.submittedToday());
            assertTrue(summary.todayMission().submittedByMe());
            assertEquals(1, summary.todayMission().submittedCount());
            assertEquals("ALREADY_SUBMITTED", assertThrows(BusinessException.class,
                    () -> submissions.submit(userId, missionId, fields, new LinkedMultiValueMap<>()))
                    .errorCode().code());
            insertTextMission(groupId, SeoulTime.now().plusDays(1), SeoulTime.now().plusDays(1).plusHours(1));
            assertEquals("MISSION_CLOSED", assertThrows(BusinessException.class,
                    () -> submissions.submit(userId, missionId, fields, new LinkedMultiValueMap<>()))
                    .errorCode().code());
        } finally {
            if (groupId != null) groups.leaveGroup(userId, groupId, null);
            jdbc.update("DELETE FROM users WHERE id = ?", userId);
        }
    }

    @Test void feedIncludesBothMembersInSubmissionOrder() {
        String suffix = UUID.randomUUID().toString();
        String ownerEmail = "feed-owner-" + suffix + "@test.com";
        String memberEmail = "feed-member-" + suffix + "@test.com";
        for (String email : List.of(ownerEmail, memberEmail))
            jdbc.update("INSERT INTO users (nickname, email, password_hash, terms_agreed_at, privacy_agreed_at) "
                    + "VALUES ('Reader', ?, 'unused', NOW(), NOW())", email);
        Long owner = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, ownerEmail);
        Long member = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, memberEmail);
        Long groupId = null;
        try {
            groupId = groups.createGroup(owner, new GroupCreateRequest("Feed group", null,
                    null, 1, List.of(new MissionSlotRequest(1, "00:00", null)))).id();
            jdbc.update("INSERT INTO group_members (group_id, user_id, role) VALUES (?, ?, 'member')", groupId, member);
            Long missionId = jdbc.queryForObject("SELECT id FROM missions WHERE group_id = ?", Long.class, groupId);
            jdbc.update("UPDATE missions SET template = 'TEXT_FILL_BLANK' WHERE id = ?", missionId);
            var first = new LinkedMultiValueMap<String, String>(); first.add("text", "owner answer");
            var second = new LinkedMultiValueMap<String, String>(); second.add("text", "member answer");
            assertEquals("FEED_LOCKED", assertThrows(BusinessException.class,
                    () -> submissions.feed(member, missionId, 0, 20)).errorCode().code());
            var ownerSubmission = submissions.submit(owner, missionId, first, new LinkedMultiValueMap<>());
            var memberSubmission = submissions.submit(member, missionId, second, new LinkedMultiValueMap<>());
            var feed = submissions.feed(member, missionId, 0, 20);
            assertEquals(2, feed.totalElements());
            assertEquals(List.of(ownerSubmission.id(), memberSubmission.id()),
                    feed.content().stream().map(item -> item.id()).toList());
        } finally {
            if (groupId != null) {
                jdbc.update("DELETE FROM group_members WHERE group_id = ? AND user_id = ?", groupId, member);
                groups.leaveGroup(owner, groupId, null);
            }
            jdbc.update("DELETE FROM users WHERE id IN (?, ?)", owner, member);
        }
    }

    private Long insertTextMission(Long groupId, LocalDateTime published, LocalDateTime deadline) {
        jdbc.update("INSERT INTO missions (group_id, content, category, mission_date, template, params, "
                + "slot_no, published_at, deadline_at) VALUES (?, 'Write something', 'DAILY', ?, "
                + "'TEXT_FILL_BLANK', '{}', 1, ?, ?)", groupId, published.toLocalDate(), published, deadline);
        return jdbc.queryForObject("SELECT id FROM missions WHERE group_id = ? "
                + "ORDER BY published_at DESC, id DESC LIMIT 1", Long.class, groupId);
    }
}
