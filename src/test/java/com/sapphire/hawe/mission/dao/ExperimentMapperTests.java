package com.sapphire.hawe.mission.dao;

import com.sapphire.hawe.global.time.SeoulTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@Transactional
class ExperimentMapperTests {

    @Autowired JdbcTemplate jdbc;
    @Autowired ExperimentMapper experiments;

    @Test
    void engagementExcludesMascotReactionAndComment() {
        String email = "experiment-test-" + UUID.randomUUID() + "@test.com";
        jdbc.update("INSERT INTO users (nickname, email, password_hash, terms_agreed_at, privacy_agreed_at) "
                + "VALUES ('Test', ?, 'unused', NOW(), NOW())", email);
        long user = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
        jdbc.update("INSERT INTO `groups` (name, owner_id, invite_code) VALUES ('Test group', ?, ?)",
                user, UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase());
        long group = jdbc.queryForObject("SELECT MAX(id) FROM `groups` WHERE owner_id = ?", Long.class, user);
        LocalDate day = SeoulTime.today().minusDays(2);
        jdbc.update("INSERT INTO missions (group_id, content, category, mission_date, template, params, "
                + "slot_no, published_at, deadline_at, experiment_arm) "
                + "VALUES (?, 'Experiment', 'TEST', ?, 'PHOTO_NOW', '{}', 1, ?, ?, 'AI')",
                group, day, day.atTime(9, 0), day.atTime(23, 0));
        long mission = jdbc.queryForObject("SELECT MAX(id) FROM missions WHERE group_id = ?", Long.class, group);
        jdbc.update("INSERT INTO missions (group_id, content, category, mission_date, template, params, "
                + "slot_no, published_at, deadline_at) "
                + "VALUES (?, 'Next', 'TEST', ?, 'PHOTO_NOW', '{}', 1, ?, ?)",
                group, day.plusDays(1), day.plusDays(1).atTime(9, 0), day.plusDays(1).atTime(23, 0));
        jdbc.update("INSERT INTO submissions (mission_id, user_id, media_url) VALUES (?, ?, 'test.png')", mission, user);
        long submission = jdbc.queryForObject("SELECT id FROM submissions WHERE mission_id = ?", Long.class, mission);
        long mascot = jdbc.queryForObject("SELECT id FROM users WHERE is_ai = 1", Long.class);
        jdbc.update("INSERT INTO reactions (submission_id, user_id, sticker_type) VALUES (?, ?, 'heart'), (?, ?, 'wow')",
                submission, user, submission, mascot);
        jdbc.update("INSERT INTO comments (submission_id, user_id, content) VALUES (?, ?, 'human'), (?, ?, 'mascot')",
                submission, user, submission, mascot);

        var rows = experiments.findClosedExperimentMissions(day, day.plusDays(1), SeoulTime.now());

        assertEquals(1, rows.size());
        assertEquals(mission, rows.getFirst().getMissionId());
        assertEquals(2, rows.getFirst().getEngagement());
    }
}
