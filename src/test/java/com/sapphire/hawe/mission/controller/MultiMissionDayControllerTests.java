package com.sapphire.hawe.mission.controller;

import com.jayway.jsonpath.JsonPath;
import com.sapphire.hawe.global.security.JwtTokenProvider;
import com.sapphire.hawe.group.dto.GroupCreateRequest;
import com.sapphire.hawe.group.dto.MissionSlotRequest;
import com.sapphire.hawe.group.service.GroupService;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 하루 미션 3개(B7): 그룹을 만들면 같은 시각의 세 슬롯이 함께 열리고, 목록·콕·멤버 지표·설정이 슬롯마다 동작한다 — 실제 DB. */
@SpringBootTest
@AutoConfigureMockMvc
class MultiMissionDayControllerTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired GroupService groups;
    @Autowired JwtTokenProvider tokens;

    @Test void threeSlotsOpenTogetherAndWorkPerMission() throws Exception {
        String suffix = UUID.randomUUID().toString();
        Long owner = user("daily-owner-" + suffix, "하늘");
        Long member = user("daily-member-" + suffix, "민준");
        Long groupId = null;
        try {
            // 세 슬롯 모두 00:00 → 그룹 생성 커밋 뒤 첫 발행에서 한꺼번에 열린다
            groupId = groups.createGroup(owner, new GroupCreateRequest("Daily group", null, null, 3, List.of(
                    new MissionSlotRequest(1, "00:00", null), new MissionSlotRequest(2, "00:00", null),
                    new MissionSlotRequest(3, "00:00", null)))).id();
            jdbc.update("INSERT INTO group_members (group_id, user_id, role) VALUES (?, ?, 'member')", groupId, member);
            String ownerAuth = "Bearer " + tokens.createAccessToken(owner, "하늘");
            String memberAuth = "Bearer " + tokens.createAccessToken(member, "민준");

            String today = mvc.perform(get("/api/groups/" + groupId + "/missions/today").header("Authorization", ownerAuth))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.dailyMissionCount").value(3))
                    .andExpect(jsonPath("$.missions.length()").value(3))
                    .andExpect(jsonPath("$.missions[0].slotNo").value(1))
                    .andExpect(jsonPath("$.missions[1].slotNo").value(2))
                    .andExpect(jsonPath("$.missions[2].slotNo").value(3))
                    .andReturn().getResponse().getContentAsString();
            List<Long> ids = longs(JsonPath.read(today, "$.missions[*].missionId"));
            List<String> templates = JsonPath.read(today, "$.missions[*].template");
            List<String> contents = JsonPath.read(today, "$.missions[*].content");
            assertEquals(3, new HashSet<>(templates).size());
            assertEquals(3, new HashSet<>(contents).size());
            long slot1 = ids.get(0), slot2 = ids.get(1), slot3 = ids.get(2);

            // 그룹 목록: todayMissions 는 세 개 전부(슬롯 순), 예전 todayMission·submittedToday 는 1번 슬롯
            String mine = groupList(ownerAuth, groupId);
            assertEquals(ids, longs(JsonPath.read(mine, "$.todayMissions[*].missionId")));
            assertEquals(slot1, ((Number) JsonPath.read(mine, "$.todayMission.missionId")).longValue());
            assertEquals(Boolean.FALSE, JsonPath.read(mine, "$.submittedToday"));

            // 콕: missionId 를 고르면 그 미션, 안 고르면 1번 슬롯. 미션이 다르면 같은 종류도 따로 보낼 수 있다
            String pokes = "/api/groups/" + groupId + "/pokes";
            mvc.perform(post(pokes).header("Authorization", ownerAuth).contentType("application/json")
                    .content(poke(member, "poke", slot3))).andExpect(status().isCreated())
                    .andExpect(jsonPath("$.missionId").value((int) slot3));
            mvc.perform(post(pokes).header("Authorization", ownerAuth).contentType("application/json")
                    .content(poke(member, "poke", null))).andExpect(status().isCreated())
                    .andExpect(jsonPath("$.missionId").value((int) slot1));
            mvc.perform(post(pokes).header("Authorization", ownerAuth).contentType("application/json")
                    .content(poke(member, "poke", slot3))).andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("ALREADY_POKED"));
            mvc.perform(post(pokes).header("Authorization", ownerAuth).contentType("application/json")
                    .content(poke(member, "poke", Long.MAX_VALUE))).andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("NO_OPEN_MISSION"));
            mvc.perform(get("/api/missions/" + slot3).header("Authorization", ownerAuth)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.members[1].pokedByMe[0]").value("poke"));
            mvc.perform(get("/api/missions/" + slot2).header("Authorization", ownerAuth)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.members[1].pokedByMe").isEmpty());

            // 같은 날 두 개를 내도 이번 달 제출한 날은 1일, 2번 슬롯만 냈어도 오늘 도장은 있다
            jdbc.update("INSERT INTO submissions (mission_id, user_id) VALUES (?, ?), (?, ?)", slot2, member, slot3, member);
            mvc.perform(post(pokes).header("Authorization", ownerAuth).contentType("application/json")
                    .content(poke(member, "poke", slot2))).andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("TARGET_ALREADY_SUBMITTED"));
            mvc.perform(get("/api/groups/" + groupId + "/members").header("Authorization", memberAuth))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[1].userId").value(member))
                    .andExpect(jsonPath("$.content[1].submissionCountThisMonth").value(1))
                    .andExpect(jsonPath("$.content[1].stampedToday").value(true))
                    .andExpect(jsonPath("$.content[0].stampedToday").value(false));
            String members = groupList(memberAuth, groupId);
            assertEquals(List.of(false, true, true), JsonPath.read(members, "$.todayMissions[*].submittedByMe"));
            assertEquals(List.of(0, 1, 1), JsonPath.read(members, "$.todayMissions[*].submittedCount"));
            assertEquals(Boolean.FALSE, JsonPath.read(members, "$.submittedToday"));

            // 하루 미션 수 상한은 5 (실제 설정값). 늘리는 쪽은 매분 발행기가 새 슬롯을 열 수 있어 단위 테스트에서 확인한다
            mvc.perform(patch("/api/groups/" + groupId + "/settings").header("Authorization", ownerAuth)
                    .contentType("application/json").content("{\"dailyMissionCount\":6}"))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
            // 줄여도 오늘 이미 열린 미션은 그대로 (다음 날 첫 발행에서 없어진 슬롯이 닫힌다)
            mvc.perform(patch("/api/groups/" + groupId + "/settings").header("Authorization", ownerAuth)
                    .contentType("application/json").content("{\"dailyMissionCount\":1}")).andExpect(status().isOk());
            mvc.perform(get("/api/groups/" + groupId + "/missions/today").header("Authorization", ownerAuth))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.dailyMissionCount").value(1))
                    .andExpect(jsonPath("$.missions.length()").value(3));
        } finally {
            if (groupId != null) {
                jdbc.update("DELETE FROM group_members WHERE group_id = ? AND user_id = ?", groupId, member);
                groups.leaveGroup(owner, groupId, null);
            }
            jdbc.update("DELETE FROM users WHERE id IN (?, ?)", owner, member);
        }
    }

    /** 내 그룹 목록에서 이 그룹 항목만 JSON 으로. */
    private String groupList(String auth, Long groupId) throws Exception {
        String page = mvc.perform(get("/api/groups").header("Authorization", auth)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<Object> found = JsonPath.read(page, "$.content[?(@.id == " + groupId + ")]");
        assertEquals(1, found.size());
        return JsonPath.parse(found.getFirst()).jsonString();
    }

    private static List<Long> longs(List<Number> numbers) {
        return numbers.stream().map(Number::longValue).toList();
    }

    private static String poke(Long target, String type, Long missionId) {
        return "{\"toUserId\":" + target + ",\"type\":\"" + type + "\""
                + (missionId == null ? "" : ",\"missionId\":" + missionId) + "}";
    }

    private Long user(String emailPrefix, String nickname) {
        String email = emailPrefix + "@test.com";
        jdbc.update("INSERT INTO users (nickname, email, password_hash, terms_agreed_at, privacy_agreed_at) "
                + "VALUES (?, ?, 'unused', NOW(), NOW())", nickname, email);
        return jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
    }
}
