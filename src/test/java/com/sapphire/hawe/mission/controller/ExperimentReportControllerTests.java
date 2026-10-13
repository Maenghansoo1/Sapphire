package com.sapphire.hawe.mission.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 주제 비교 실험 리포트 (B9) — 실제 DB, 토큰 보호, 닫힌 실험 미션만. */
@SpringBootTest(properties = "hawe.admin.token=test-admin-token")
@AutoConfigureMockMvc
@Transactional
class ExperimentReportControllerTests {

    private static final String URL = "/internal/experiments/topic?from=2025-03-01&to=2025-03-31";
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @Test
    void hiddenWithoutTheRightToken() throws Exception {
        mvc.perform(get(URL)).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mvc.perform(get(URL).header("X-Admin-Token", "wrong")).andExpect(status().isNotFound());
        // 토큰이 틀리면 형식 오류도 알려 주지 않는다 (엔드포인트가 있다는 것을 숨김)
        mvc.perform(get("/internal/experiments/topic?from=어제").header("X-Admin-Token", "wrong"))
                .andExpect(status().isNotFound());
        // 사용자 JWT 로는 열리지 않는다
        mvc.perform(get(URL).header("Authorization", "Bearer x")).andExpect(status().isUnauthorized());
    }

    @Test
    void validatesThePeriodAndFormat() throws Exception {
        for (String query : new String[] {"?to=2025-03-31", "?from=2025-03-31&to=2025-03-01", "?from=2025-01-01&to=2026-01-02",
                "?from=2025-03-01&to=2025-03-31&format=csv", "?from=3월&to=2025-03-31"})
            mvc.perform(get("/internal/experiments/topic" + query).header("X-Admin-Token", "test-admin-token"))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void reportsClosedExperimentMissionsAsJsonAndMarkdown() throws Exception {
        // 그룹 A: 3/5 AI(2명 모두), 3/6 랜덤(1명), 3/20 실험 밖 미션이 현재 미션
        // 그룹 B: 3/5 랜덤(1명), 3/6 AI(2명 모두, AI 실패로 고정 문장), 3/20 실험 밖 현재 미션
        // 그룹 C: 3/10 AI 미션이 아직 현재 미션 → 빠짐
        Long[] a = group();
        Long[] b = group();
        Long[] c = group();
        submit(mission(a[0], "2025-03-05", "AI", "AI"), a[1], a[2]);
        submit(mission(a[0], "2025-03-06", "RANDOM", "PLACEHOLDER"), a[1]);
        mission(a[0], "2025-03-20", null, "PLACEHOLDER");
        submit(mission(b[0], "2025-03-05", "RANDOM", "PLACEHOLDER"), b[2]);
        submit(mission(b[0], "2025-03-06", "AI", "AI_FALLBACK"), b[1], b[2]);
        mission(b[0], "2025-03-20", null, "PLACEHOLDER");
        submit(mission(c[0], "2025-03-10", "AI", "AI"), c[1], c[2]);

        mvc.perform(get(URL).header("X-Admin-Token", "test-admin-token")).andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value("2025-03-01"))
                .andExpect(jsonPath("$.ai.missions").value(2))
                .andExpect(jsonPath("$.ai.eligible").value(4))
                .andExpect(jsonPath("$.ai.submitted").value(4))
                .andExpect(jsonPath("$.ai.participation").value(1.0))
                .andExpect(jsonPath("$.random.missions").value(2))
                .andExpect(jsonPath("$.random.participation").value(0.5))
                .andExpect(jsonPath("$.difference.differencePoints").value(50.0))
                .andExpect(jsonPath("$.aiFallbackMissions").value(1))
                .andExpect(jsonPath("$.aiPerProtocol.missions").value(1))
                .andExpect(jsonPath("$.paired.groups").value(2))
                .andExpect(jsonPath("$.paired.meanDifferencePoints").value(50.0))
                .andExpect(jsonPath("$.paired.positive").value(2))
                .andExpect(jsonPath("$.underpowered").value(true));

        mvc.perform(get(URL + "&format=md").header("X-Admin-Token", "test-admin-token")).andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/markdown"))
                .andExpect(content().string(containsString("AI 100.0% vs 랜덤 50.0%, 차이 +50.0%p")));
    }

    /** 그룹 하나와 멤버 둘(2025-01-01 가입). [그룹, 멤버1, 멤버2] */
    private Long[] group() {
        Long first = user();
        Long second = user();
        String code = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        jdbc.update("INSERT INTO `groups` (name, owner_id, invite_code) VALUES ('실험', ?, ?)", first, code);
        Long group = jdbc.queryForObject("SELECT id FROM `groups` WHERE invite_code = ?", Long.class, code);
        jdbc.update("INSERT INTO group_members (group_id, user_id, role, joined_at) VALUES "
                + "(?, ?, 'owner', '2025-01-01 00:00:00'), (?, ?, 'member', '2025-01-01 00:00:00')", group, first, group, second);
        return new Long[] {group, first, second};
    }

    private Long user() {
        String email = "experiment-" + UUID.randomUUID() + "@test.com";
        jdbc.update("INSERT INTO users (nickname, email, password_hash, terms_agreed_at, privacy_agreed_at) "
                + "VALUES ('실험참가자', ?, 'unused', NOW(), NOW())", email);
        return jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
    }

    private Long mission(Long group, String date, String arm, String source) {
        jdbc.update("INSERT INTO missions (group_id, content, category, mission_date, template, params, slot_no, "
                + "published_at, deadline_at, generation_source, experiment_arm) VALUES (?, '실험 미션', '일상', ?, "
                + "'PHOTO_NOW', '{}', 1, CONCAT(?, ' 09:00:00'), CONCAT(?, ' 23:00:00'), ?, ?)",
                group, date, date, date, source, arm);
        return jdbc.queryForObject("SELECT id FROM missions WHERE group_id = ? AND mission_date = ?", Long.class, group, date);
    }

    private void submit(Long mission, Long... users) {
        for (Long user : users)
            jdbc.update("INSERT INTO submissions (mission_id, user_id, media_url, submitted_at) "
                    + "VALUES (?, ?, 'x.png', (SELECT published_at + INTERVAL 10 MINUTE FROM missions WHERE id = ?))",
                    mission, user, mission);
    }
}
