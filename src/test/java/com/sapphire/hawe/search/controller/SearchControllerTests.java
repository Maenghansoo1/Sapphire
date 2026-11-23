package com.sapphire.hawe.search.controller;

import com.jayway.jsonpath.JsonPath;
import com.sapphire.hawe.global.security.JwtTokenProvider;
import com.sapphire.hawe.group.service.GroupService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
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

/**
 * 지난 미션 검색(TODO_2/B1) — 권한 규칙이 핵심. 미션 날짜는 미래로 잡아서, 테스트 중 매분 도는 발행 스케줄러가
 * 오늘 미션을 넣어도 이 테스트 미션의 "현재/닫힘" 상태가 바뀌지 않게 했다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SearchControllerTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtTokenProvider tokens;
    @Autowired GroupService groups;

    @Test void searchesOnlyMissionsICanSeeAndNeverSubmissions() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        long me = user(suffix + "a");
        long friend = user(suffix + "b");
        long shared = group("Search shared", me, suffix.substring(0, 6).toUpperCase(), 2);
        long othersOnly = group("Search others", friend, suffix.substring(6, 12).toUpperCase(), 1);
        long left = group("Search left", friend, suffix.substring(12, 18).toUpperCase(), 1);
        try {
            member(shared, friend, "member");
            member(left, me, "member");
            LocalDate base = LocalDate.now().plusDays(30);
            long older = mission(shared, 1, base, "코뿔소를 그려 봐", "TEXT_REVIEW", "{}");
            long closed = mission(shared, 1, base.plusDays(2), "등대 옆 코뿔소 찍기", "PHOTO_NOW", "{\"subject\":\"해바라기\"}");
            long openNotMine = mission(shared, 1, base.plusDays(5), "코뿔소 숨은 그림", "TEXT_REVIEW", "{}");
            long openMine = mission(shared, 2, base.plusDays(5), "[대상]에게 코뿔소 편지", "TEXT_FILL_BLANK",
                    "{\"sentence\":\"___ 코뿔소\",\"targetUserId\":" + friend + "}");
            jdbc.update("INSERT INTO submissions(mission_id,user_id,answer_data,submitted_at) VALUES (?,?,?,NOW())",
                    openMine, me, "{\"text\":\"펭귄\"}");
            long notMember = mission(othersOnly, 1, base, "코뿔소 남의 그룹", "TEXT_REVIEW", "{}");
            mission(othersOnly, 1, base.plusDays(2), "다음 미션", "TEXT_REVIEW", "{}");
            long leftGroup = mission(left, 1, base, "코뿔소 나간 그룹", "TEXT_REVIEW", "{}");
            mission(left, 1, base.plusDays(2), "다음 미션", "TEXT_REVIEW", "{}");
            groups.leaveGroup(me, left, null);
            String auth = "Bearer " + tokens.createAccessToken(me, "Me");

            // 보이는 것: 닫힌 미션 2개 + 열려 있어도 내가 낸 미션. 같은 점수는 최신 순
            String body = search(auth, "코뿔소");
            assertEquals(List.of(openMine, closed, older), ids(body));
            assertFalse(ids(body).contains(openNotMine), "열려 있고 내가 안 낸 미션은 안 보여요");
            assertFalse(ids(body).contains(notMember), "남의 그룹 미션은 안 보여요");
            assertFalse(ids(body).contains(leftGroup), "나간 그룹 미션은 안 보여요");
            assertEquals("친구에게 코뿔소 편지", JsonPath.read(body, "$[0].content"));  // 닉네임을 풀지 않아요
            assertEquals(true, JsonPath.read(body, "$[0].submitted"));
            assertEquals(false, JsonPath.read(body, "$[1].submitted"));
            assertEquals("Search shared", JsonPath.read(body, "$[1].groupName"));
            assertEquals(List.of("코뿔소"), JsonPath.read(body, "$[1].matchedTerms[*].term"));
            assertEquals(List.of(false), JsonPath.read(body, "$[1].matchedTerms[*].viaExpansion"));
            assertEquals(false, JsonPath.read(body, "$[1].expanded"));  // 테스트에서는 AI 확장이 꺼져 있어요
            assertFalse(body.contains("score"), "점수는 내려주지 않아요");

            // 많이 겹칠수록 위로
            assertEquals(List.of(closed, openMine, older), ids(search(auth, "등대 코뿔소")));
            // 서버가 만든 params 값으로도 찾아요
            assertEquals(List.of(closed), ids(search(auth, "해바라기")));
            // 제출물 글·[대상] 자리는 검색되지 않아요
            assertEquals(List.of(), ids(search(auth, "펭귄")));
            assertEquals(List.of(), ids(search(auth, "대상")));
            assertEquals(List.of(openMine), ids(mvc.perform(get("/api/search/missions").param("q", "코뿔소")
                    .param("limit", "1").header("Authorization", auth)).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString()));

            // 그룹을 고르면 그 그룹만, 내 그룹이 아니면 403, 없는 그룹이면 404
            assertEquals(List.of(openMine, closed, older), ids(mvc.perform(get("/api/search/missions").param("q", "코뿔소")
                    .param("groupId", String.valueOf(shared)).header("Authorization", auth))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()));
            mvc.perform(get("/api/search/missions").param("q", "코뿔소").param("groupId", String.valueOf(othersOnly))
                    .header("Authorization", auth)).andExpect(status().isForbidden());
            mvc.perform(get("/api/search/missions").param("q", "코뿔소").param("groupId", "999999999")
                    .header("Authorization", auth)).andExpect(status().isNotFound());

            // 친구는 남의 그룹 닫힌 미션을 보지만, 자기가 안 낸 열린 미션은 못 봐요
            String friendAuth = "Bearer " + tokens.createAccessToken(friend, "Friend");
            List<Long> friendIds = ids(search(friendAuth, "코뿔소"));
            assertTrue(friendIds.contains(notMember));
            assertTrue(friendIds.contains(leftGroup));
            assertFalse(friendIds.contains(openMine));
            assertFalse(friendIds.contains(openNotMine));

            // 입력 검증
            for (String q : List.of("코", "!!", "가".repeat(51)))
                mvc.perform(get("/api/search/missions").param("q", q).header("Authorization", auth))
                        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
            mvc.perform(get("/api/search/missions").param("q", "가".repeat(50)).header("Authorization", auth))
                    .andExpect(status().isOk());
            mvc.perform(get("/api/search/missions").header("Authorization", auth)).andExpect(status().isBadRequest());
            for (String limit : List.of("0", "51", "abc"))
                mvc.perform(get("/api/search/missions").param("q", "코뿔소").param("limit", limit)
                        .header("Authorization", auth)).andExpect(status().isBadRequest());
            mvc.perform(get("/api/search/missions").param("q", "코뿔소")).andExpect(status().isUnauthorized());
        } finally {
            for (long group : List.of(shared, othersOnly, left)) {
                for (Long member : jdbc.queryForList(
                        "SELECT user_id FROM group_members WHERE group_id=? ORDER BY role='owner'", Long.class, group))
                    groups.leaveGroup(member, group, null);
            }
            jdbc.update("DELETE FROM users WHERE id IN (?,?)", me, friend);
        }
    }

    private String search(String auth, String q) throws Exception {
        return mvc.perform(get("/api/search/missions").param("q", q).header("Authorization", auth))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private static List<Long> ids(String body) {
        List<Number> raw = JsonPath.read(body, "$[*].missionId");
        return raw.stream().map(Number::longValue).toList();
    }

    private long user(String name) {
        String email = name + "@test.com";
        jdbc.update("INSERT INTO users(email,password_hash,nickname,terms_agreed_at,privacy_agreed_at) VALUES (?,'x',?,NOW(),NOW())",
                email, "S" + name.substring(0, 6));
        return jdbc.queryForObject("SELECT id FROM users WHERE email=?", Long.class, email);
    }

    private long group(String name, long owner, String code, int daily) {
        jdbc.update("INSERT INTO `groups`(name,owner_id,invite_code,daily_mission_count) VALUES (?,?,?,?)", name, owner, code, daily);
        long id = jdbc.queryForObject("SELECT id FROM `groups` WHERE invite_code=?", Long.class, code);
        member(id, owner, "owner");
        return id;
    }

    private void member(long group, long user, String role) {
        jdbc.update("INSERT INTO group_members(group_id,user_id,role) VALUES (?,?,?)", group, user, role);
    }

    private long mission(long group, int slot, LocalDate day, String content, String template, String params) {
        LocalDateTime published = day.atTime(9, 0);
        jdbc.update("INSERT INTO missions(group_id,content,category,mission_date,template,params,slot_no,published_at,deadline_at) "
                + "VALUES (?,?,'TEST',?,?,?,?,?,?)", group, content, day, template, params, slot, published, published.plusHours(12));
        return jdbc.queryForObject("SELECT id FROM missions WHERE group_id=? AND mission_date=? AND slot_no=?",
                Long.class, group, day, slot);
    }
}
