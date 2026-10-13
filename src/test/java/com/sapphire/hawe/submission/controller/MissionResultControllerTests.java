package com.sapphire.hawe.submission.controller;

import com.sapphire.hawe.global.security.JwtTokenProvider;
import com.sapphire.hawe.global.time.SeoulTime;
import com.sapphire.hawe.group.dto.GroupCreateRequest;
import com.sapphire.hawe.group.dto.MissionSlotRequest;
import com.sapphire.hawe.group.service.GroupService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 새 틀(B5)을 HTTP 로 제출하고 집계 결과를 읽는 흐름 — 실제 DB. */
@SpringBootTest
@AutoConfigureMockMvc
class MissionResultControllerTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired GroupService groups;
    @Autowired JwtTokenProvider tokens;

    @Test void scalarTemplatesSubmitAndShowGroupResults() throws Exception {
        String suffix = UUID.randomUUID().toString();
        Long owner = user("result-owner-" + suffix, "하늘");
        Long member = user("result-member-" + suffix, "민준");
        Long groupId = null;
        try {
            groupId = groups.createGroup(owner, new GroupCreateRequest("Result group", null,
                    null, 1, List.of(new MissionSlotRequest(1, "00:00", null)))).id();
            jdbc.update("INSERT INTO group_members (group_id, user_id, role) VALUES (?, ?, 'member')", groupId, member);
            Long consensus = jdbc.queryForObject("SELECT id FROM missions WHERE group_id = ?", Long.class, groupId);
            jdbc.update("UPDATE missions SET template = 'SLIDER_CONSENSUS', params = ? WHERE id = ?",
                    "{\"question\":\"우리 그룹은 아침형일까?\",\"left\":\"아침형\",\"right\":\"저녁형\"}", consensus);
            String ownerAuth = "Bearer " + tokens.createAccessToken(owner, "하늘");
            String memberAuth = "Bearer " + tokens.createAccessToken(member, "민준");

            mvc.perform(get(result(consensus)).header("Authorization", ownerAuth)).andExpect(status().isLocked())
                    .andExpect(jsonPath("$.code").value("FEED_LOCKED"));
            mvc.perform(multipart(submissions(consensus)).param("number", "80").header("Authorization", ownerAuth))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_SUBMISSION"));
            mvc.perform(multipart(submissions(consensus)).param("number", "80").param("guess", "60")
                    .header("Authorization", ownerAuth)).andExpect(status().isCreated())
                    .andExpect(jsonPath("$.number").value(80)).andExpect(jsonPath("$.guess").value(60))
                    .andExpect(jsonPath("$.choice").doesNotExist()).andExpect(jsonPath("$.text").doesNotExist());
            mvc.perform(multipart(submissions(consensus)).param("number", "45").param("guess", "70")
                    .header("Authorization", memberAuth)).andExpect(status().isCreated());
            // 평균 62.5 — 하늘 |60 − 62.5| = 2.5 로 1등, 민준 |70 − 62.5| = 7.5
            mvc.perform(get(result(consensus)).header("Authorization", memberAuth)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.template").value("SLIDER_CONSENSUS"))
                    .andExpect(jsonPath("$.totalCount").value(2))
                    .andExpect(jsonPath("$.average").value(62.5))
                    .andExpect(jsonPath("$.ranking[0].userId").value(owner))
                    .andExpect(jsonPath("$.ranking[0].nickname").value("하늘"))
                    .andExpect(jsonPath("$.ranking[0].guess").value(60))
                    .andExpect(jsonPath("$.ranking[0].diff").value(2.5))
                    .andExpect(jsonPath("$.ranking[1].diff").value(7.5))
                    .andExpect(jsonPath("$.winnerUserIds.length()").value(1))
                    .andExpect(jsonPath("$.winnerUserIds[0]").value(owner))
                    .andExpect(jsonPath("$.choices").doesNotExist())
                    .andExpect(jsonPath("$.distribution").doesNotExist());
            mvc.perform(get(submissions(consensus)).header("Authorization", memberAuth)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].number").value(80))
                    .andExpect(jsonPath("$.content[0].guess").value(60))
                    .andExpect(jsonPath("$.content[1].number").value(45));

            // 다음 날 밸런스 게임이 열려도 지난 미션의 결과는 계속 볼 수 있다
            Long balance = insertMission(groupId, "TEXT_BALANCE", "{\"optionA\":\"산\",\"optionB\":\"바다\"}", 1);
            mvc.perform(multipart(submissions(balance)).param("choice", "b").header("Authorization", ownerAuth))
                    .andExpect(status().isBadRequest());
            mvc.perform(multipart(submissions(balance)).param("choice", "B").param("caption", " 시원해서 ")
                    .header("Authorization", ownerAuth)).andExpect(status().isCreated())
                    .andExpect(jsonPath("$.choice").value("B")).andExpect(jsonPath("$.caption").value("시원해서"))
                    .andExpect(jsonPath("$.number").doesNotExist());
            mvc.perform(multipart(submissions(balance)).param("choice", "B").header("Authorization", memberAuth))
                    .andExpect(status().isCreated());
            mvc.perform(get(result(balance)).header("Authorization", ownerAuth)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalCount").value(2))
                    .andExpect(jsonPath("$.choices[0].choice").value("A")).andExpect(jsonPath("$.choices[0].count").value(0))
                    .andExpect(jsonPath("$.choices[1].choice").value("B")).andExpect(jsonPath("$.choices[1].count").value(2))
                    .andExpect(jsonPath("$.myChoice").value("B"))
                    .andExpect(jsonPath("$.average").doesNotExist());
            mvc.perform(get(result(consensus)).header("Authorization", ownerAuth)).andExpect(status().isOk());

            // 집계가 없는 틀은 제출해도 결과가 없다
            Long text = insertMission(groupId, "TEXT_ONE_WORD", "{}", 2);
            mvc.perform(multipart(submissions(text)).param("text", "설렘").header("Authorization", ownerAuth))
                    .andExpect(status().isCreated()).andExpect(jsonPath("$.text").value("설렘"));
            mvc.perform(get(result(text)).header("Authorization", ownerAuth)).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("RESULT_NOT_SUPPORTED"));
        } finally {
            if (groupId != null) {
                jdbc.update("DELETE FROM group_members WHERE group_id = ? AND user_id = ?", groupId, member);
                groups.leaveGroup(owner, groupId, null);
            }
            jdbc.update("DELETE FROM users WHERE id IN (?, ?)", owner, member);
        }
    }

    private Long user(String emailPrefix, String nickname) {
        String email = emailPrefix + "@test.com";
        jdbc.update("INSERT INTO users (nickname, email, password_hash, terms_agreed_at, privacy_agreed_at) "
                + "VALUES (?, ?, 'unused', NOW(), NOW())", nickname, email);
        return jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
    }

    private Long insertMission(Long groupId, String template, String params, int daysLater) {
        LocalDateTime published = SeoulTime.now().plusDays(daysLater);
        jdbc.update("INSERT INTO missions (group_id, content, category, mission_date, template, params, "
                + "slot_no, published_at, deadline_at) VALUES (?, '새 틀', '일상', ?, ?, ?, 1, ?, ?)",
                groupId, published.toLocalDate(), template, params, published, published.plusHours(1));
        return jdbc.queryForObject("SELECT id FROM missions WHERE group_id = ? "
                + "ORDER BY published_at DESC, id DESC LIMIT 1", Long.class, groupId);
    }

    private static String submissions(Long missionId) { return "/api/missions/" + missionId + "/submissions"; }
    private static String result(Long missionId) { return "/api/missions/" + missionId + "/result"; }
}
