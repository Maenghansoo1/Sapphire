package com.sapphire.hawe.insight.dao;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import com.sapphire.hawe.withdraw.dao.WithdrawMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class InsightMapperTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired InsightMapper insights;
    @Autowired WithdrawMapper withdraw;

    @Test void closedMissionsAndTextAreScopedToMonthAndGroup() {
        long user = user(); long group = group(user); long other = group(user);
        LocalDate date = LocalDate.now().minusMonths(1).withDayOfMonth(10);
        long closed = mission(group, date, 1, "TEXT_REVIEW");
        mission(group, date.plusDays(1), 1, "TEXT_REVIEW");
        mission(other, date, 1, "TEXT_REVIEW");
        jdbc.update("INSERT INTO submissions (mission_id,user_id,answer_data) VALUES (?,?,?)", closed, user,
                "{\"text\":\"비밀 본문\"}");
        List<InsightRow> rows = insights.findClosedMissions(group, date.withDayOfMonth(1), date.withDayOfMonth(date.lengthOfMonth()));
        assertEquals(1, rows.size());
        assertEquals(closed, rows.getFirst().getId());
        assertEquals(1, rows.getFirst().getMemberCount());
        var submissions = insights.findSubmissions(List.of(closed));
        assertEquals("비밀 본문", submissions.getFirst().getText());
        assertFalse(submissions.getFirst().isLate());
    }

    @Test void cacheClaimsAreAtomicAndRespectExpiryAndAbandonment() {
        long user = user(), group = group(user);
        String month = YearMonth.now(com.sapphire.hawe.global.time.SeoulTime.ZONE).toString();
        assertEquals(1, insights.tryStart(group, month));
        assertEquals(0, insights.tryStart(group, month));
        assertEquals(0, insights.restart(group, month));
        assertEquals(1, insights.complete(group, month, "{\"empty\":true}",
                jdbc.queryForObject("SELECT NOW()", LocalDateTime.class).plusDays(2), false, 1800));
        assertEquals(0, insights.restart(group, month));
        jdbc.update("UPDATE group_insights SET expires_at=NOW()-INTERVAL 1 SECOND WHERE group_id=?", group);
        assertTrue(insights.findCache(group, month).isExpired());
        assertEquals(1, insights.restart(group, month));
        jdbc.update("UPDATE group_insights SET updated_at=NOW()-INTERVAL 6 MINUTE WHERE group_id=?", group);
        assertTrue(insights.findCache(group, month).isAbandoned());
        assertEquals(1, insights.restart(group, month));
        assertEquals(1, insights.fail(group, month));
        assertEquals(1, insights.restart(group, month));
    }

    @Test void lastMissionOfPreviousMonthGetsTemporaryCacheThenFinalizesAfterNextPublication() {
        long user = user(), group = group(user);
        LocalDate lastDay = YearMonth.now(com.sapphire.hawe.global.time.SeoulTime.ZONE).minusMonths(1).atEndOfMonth();
        String month = YearMonth.from(lastDay).toString();
        LocalDateTime dbNow = jdbc.queryForObject("SELECT NOW()", LocalDateTime.class);
        long lastMission = mission(group, lastDay, 1, "TEXT_REVIEW");
        assertTrue(insights.findClosedMissions(group, lastDay.withDayOfMonth(1), lastDay).isEmpty());
        assertTrue(insights.hasCurrentMissionInMonth(group, lastDay.withDayOfMonth(1), lastDay));
        assertEquals(1, insights.tryStart(group, month));
        assertEquals(1, insights.complete(group, month, "{\"empty\":true}",
                dbNow.plusDays(1), true, 1800));
        assertNotNull(insights.findCache(group, month).getExpiresAt());

        jdbc.update("UPDATE group_insights SET expires_at=NOW()-INTERVAL 1 SECOND WHERE group_id=?", group);
        assertEquals(1, insights.restart(group, month));
        assertEquals(1, insights.complete(group, month, "{\"empty\":true}",
                dbNow.minusDays(1), true, 1800));
        assertNotNull(insights.findCache(group, month).getExpiresAt());
        jdbc.update("UPDATE group_insights SET expires_at=NOW()-INTERVAL 1 SECOND WHERE group_id=?", group);
        assertEquals(1, insights.restart(group, month));
        mission(group, lastDay.plusDays(1), 1, "TEXT_REVIEW");
        assertFalse(insights.hasCurrentMissionInMonth(group, lastDay.withDayOfMonth(1), lastDay));
        assertEquals(lastMission, insights.findClosedMissions(group, lastDay.withDayOfMonth(1), lastDay).getFirst().getId());
        assertEquals(1, insights.complete(group, month, "{\"empty\":false}",
                dbNow.minusDays(1), false, 1800));
        assertNull(insights.findCache(group, month).getExpiresAt());
    }

    @Test void batchesInteractionsProfilesAndWithdrawalInvalidatesCache() {
        long owner = user(), member = user(), group = group(owner);
        jdbc.update("INSERT INTO group_members(group_id,user_id,role,nickname_override) VALUES (?,?,'member','In group')", group, member);
        long mission = mission(group, LocalDate.now().minusDays(2), 1, "TEXT_REVIEW");
        jdbc.update("INSERT INTO submissions(mission_id,user_id,answer_data) VALUES (?,?,?)", mission, owner, "{\"text\":\"hello\"}");
        long submission = jdbc.queryForObject("SELECT id FROM submissions WHERE mission_id=?", Long.class, mission);
        jdbc.update("INSERT INTO reactions(submission_id,user_id,sticker_type) VALUES (?,?,'heart')", submission, member);
        jdbc.update("INSERT INTO comments(submission_id,user_id,content) VALUES (?,?,'comment')", submission, member);
        assertEquals(1, insights.countReactionsBySubmission(List.of(submission)).getFirst().getCount());
        assertEquals("heart", insights.countStickersBySubmission(List.of(submission)).getFirst().getStickerType());
        assertEquals(1, insights.countCommentsBySubmission(List.of(submission)).getFirst().getCount());
        long mascot = jdbc.queryForObject("SELECT id FROM users WHERE is_ai=1", Long.class);
        jdbc.update("INSERT INTO reactions(submission_id,user_id,sticker_type) VALUES (?,?,'wow')", submission, mascot);
        jdbc.update("INSERT INTO comments(submission_id,user_id,content) VALUES (?,?,'별이')", submission, mascot);
        assertEquals(1, insights.countReactionsBySubmission(List.of(submission)).getFirst().getCount());
        assertEquals(1, insights.countStickersBySubmission(List.of(submission)).size());
        assertEquals(1, insights.countCommentsBySubmission(List.of(submission)).getFirst().getCount());
        assertEquals("In group", insights.findDisplayProfiles(group, List.of(member)).getFirst().getNickname());
        jdbc.update("INSERT INTO group_insights(group_id,month,status) VALUES (?,'2026-09','READY')", group);
        withdraw.deleteInsightsInvolvingUser(member);
        assertNull(insights.findCache(group, "2026-09"));
    }

    private long user() {
        String email = UUID.randomUUID() + "@example.com";
        jdbc.update("INSERT INTO users(email,password_hash,nickname,terms_agreed_at,privacy_agreed_at) VALUES (?, 'x','Test',NOW(),NOW())", email);
        return jdbc.queryForObject("SELECT id FROM users WHERE email=?", Long.class, email);
    }
    private long group(long owner) {
        String code = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        jdbc.update("INSERT INTO `groups`(name,owner_id,invite_code) VALUES ('Test',?,?)", owner, code);
        long id = jdbc.queryForObject("SELECT id FROM `groups` WHERE invite_code=?", Long.class, code);
        jdbc.update("INSERT INTO group_members(group_id,user_id,role) VALUES (?,?,'OWNER')", id, owner);
        return id;
    }
    private long mission(long group, LocalDate date, int slot, String template) {
        jdbc.update("INSERT INTO missions(group_id,content,category,mission_date,template,params,slot_no,published_at,deadline_at) "
                + "VALUES (?,'Question','TEST',?,?,'{}',?,NOW()-INTERVAL 2 DAY,NOW()-INTERVAL 1 DAY)", group, date, template, slot);
        return jdbc.queryForObject("SELECT id FROM missions WHERE group_id=? AND mission_date=? AND slot_no=?", Long.class, group, date, slot);
    }
}
