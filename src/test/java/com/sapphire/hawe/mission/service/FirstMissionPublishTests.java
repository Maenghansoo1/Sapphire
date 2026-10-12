package com.sapphire.hawe.mission.service;

import com.sapphire.hawe.group.dto.GroupCreateRequest;
import com.sapphire.hawe.group.dto.MissionSlotRequest;
import com.sapphire.hawe.group.service.GroupService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class FirstMissionPublishTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired GroupService groups;
    @Autowired MissionService missions;

    @Test
    void groupCreationPublishesFirstMissionAfterCommit() {
        String email = "first-mission-" + UUID.randomUUID() + "@test.com";
        jdbc.update("INSERT INTO users (nickname, email, password_hash, terms_agreed_at, privacy_agreed_at) "
                + "VALUES ('Owner', ?, 'unused', NOW(), NOW())", email);
        Long userId = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
        Long groupId = null;
        try {
            var created = groups.createGroup(userId, new GroupCreateRequest("Mission group", null,
                    null, 1, List.of(new MissionSlotRequest(1, "00:00", null))));
            groupId = created.id();
            var current = missions.getTodayMissions(userId, groupId);
            assertEquals(1, current.missions().size());
            assertEquals(1, current.missions().getFirst().number());
            assertEquals(1, current.missions().getFirst().members().size());
            assertEquals(userId, current.missions().getFirst().members().getFirst().userId());
            assertEquals(current.missions().getFirst().closesAt(), current.nextPublishAt());
            var summary = groups.listMyGroups(userId, 0, 20).content().stream()
                    .filter(row -> row.id().equals(created.id())).findFirst().orElseThrow();
            assertEquals(current.missions().getFirst().missionId(), summary.todayMission().missionId());
            assertFalse(summary.submittedToday());
        } finally {
            if (groupId != null) groups.leaveGroup(userId, groupId, null);
            jdbc.update("DELETE FROM users WHERE id = ?", userId);
        }
    }
}
