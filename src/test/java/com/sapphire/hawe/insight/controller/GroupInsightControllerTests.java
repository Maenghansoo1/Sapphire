package com.sapphire.hawe.insight.controller;

import com.jayway.jsonpath.JsonPath;
import com.sapphire.hawe.global.security.JwtTokenProvider;
import com.sapphire.hawe.group.service.GroupService;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class GroupInsightControllerTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtTokenProvider tokens;
    @Autowired GroupService groups;

    @Test void generatesThenServesCachedMetadataOnly() throws Exception {
        String suffix = UUID.randomUUID().toString();
        String email = suffix + "@test.com";
        jdbc.update("INSERT INTO users(email,password_hash,nickname,terms_agreed_at,privacy_agreed_at) VALUES (?,'x','Base',NOW(),NOW())", email);
        long user = jdbc.queryForObject("SELECT id FROM users WHERE email=?", Long.class, email);
        String code = suffix.substring(0, 6).toUpperCase();
        jdbc.update("INSERT INTO `groups`(name,owner_id,invite_code) VALUES ('Insight test',?,?)", user, code);
        long group = jdbc.queryForObject("SELECT id FROM `groups` WHERE invite_code=?", Long.class, code);
        try {
            jdbc.update("INSERT INTO group_members(group_id,user_id,role,nickname_override) VALUES (?,?,'owner','Group name')", group, user);
            LocalDate day = LocalDate.now().minusMonths(1).withDayOfMonth(10);
            long first = mission(group, day);
            mission(group, day.plusDays(1));
            jdbc.update("INSERT INTO submissions(mission_id,user_id,answer_data,submitted_at) VALUES (?,?,?,NOW()-INTERVAL 1 DAY)",
                    first, user, "{\"text\":\"비밀본문 여행 사진\"}");
            long submission = jdbc.queryForObject("SELECT id FROM submissions WHERE mission_id=?", Long.class, first);
            jdbc.update("INSERT INTO reactions(submission_id,user_id,sticker_type) VALUES (?,?,'heart')", submission, user);
            String auth = "Bearer " + tokens.createAccessToken(user, "Base");
            String path = "/api/groups/" + group + "/insights?month=" + YearMonth.from(day);
            mvc.perform(get(path).header("Authorization", auth)).andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.status").value("GENERATING"));
            String body = null;
            for (int i = 0; i < 100; i++) {
                var response = mvc.perform(get(path).header("Authorization", auth)).andReturn().getResponse();
                if (response.getStatus() == 200) { body = response.getContentAsString(); break; }
                Thread.sleep(50);
            }
            assertNotNull(body, "insight generation did not finish within 5 seconds");
            assertEquals("READY", JsonPath.read(body, "$.status"));
            assertEquals("Group name", JsonPath.read(body, "$.fastest.nickname"));
            assertTrue(((Number) JsonPath.read(body, "$.activityIndex.score")).intValue() > 0);
            assertEquals(submission, ((Number) JsonPath.read(body, "$.mostReacted.submissionId")).longValue());
            assertFalse(body.contains("비밀본문"));
            mvc.perform(get(path).header("Authorization", auth)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.stale").value(false));
            mvc.perform(get("/api/groups/" + group + "/insights?month=2999-01").header("Authorization", auth))
                    .andExpect(status().isBadRequest());
            mvc.perform(get("/api/groups/" + group + "/insights?month=2026-13").header("Authorization", auth))
                    .andExpect(status().isBadRequest());
        } finally {
            groups.leaveGroup(user, group, null);
            jdbc.update("DELETE FROM users WHERE id=?", user);
        }
    }

    private long mission(long group, LocalDate day) {
        jdbc.update("INSERT INTO missions(group_id,content,category,mission_date,template,params,published_at,deadline_at) "
                + "VALUES (?,'Question','TEST',?,'TEXT_REVIEW','{}',NOW()-INTERVAL 2 DAY,NOW()-INTERVAL 1 DAY)", group, day);
        return jdbc.queryForObject("SELECT id FROM missions WHERE group_id=? AND mission_date=?", Long.class, group, day);
    }
}
