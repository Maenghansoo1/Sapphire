package com.sapphire.hawe.calendar.dao;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
class CalendarMapperTests {

    private static final LocalDate JAN_1 = LocalDate.of(2026, 1, 1);
    private static final LocalDate JAN_31 = LocalDate.of(2026, 1, 31);

    @Autowired JdbcTemplate jdbc;
    @Autowired CalendarMapper calendar;

    private Long me;
    private Long idle;
    private Long leaver;
    private Long lateJoiner;
    private Long groupId;
    private Long jan5Mission;
    private Long jan6Mission;

    // 1월 5일 미션: 나와 leaver가 냄 → 그 뒤 leaver는 그룹을 나감 (멤버 줄 없음)
    // 1월 6일 미션(현재 미션): lateJoiner가 지각으로 냄. lateJoiner는 1월 5일 미션이 열린 뒤에 들어옴
    @BeforeEach
    void setUp() {
        me = insertUser("me");
        idle = insertUser("idle");
        leaver = insertUser("leaver");
        lateJoiner = insertUser("lateJoiner");
        groupId = insertGroup(me);
        join(groupId, me, "2026-01-01 08:00:00");
        join(groupId, idle, "2026-01-01 08:00:00");
        join(groupId, lateJoiner, "2026-01-05 12:00:00");

        insertMission(groupId, "2025-12-31", "2025-12-31 09:00:00", "PHOTO_NOW", "지난달 미션", "{}");
        jan5Mission = insertMission(groupId, "2026-01-05", "2026-01-05 09:00:00", "PHOTO_NOW", "창밖 풍경", "{}");
        jan6Mission = insertMission(groupId, "2026-01-06", "2026-01-06 09:00:00", "DRAW_A_TO_B",
                "[대상]을 고양이로", "{\"targetUserId\": " + idle + "}");
        submit(jan5Mission, me, false);
        submit(jan5Mission, leaver, false);
        submit(jan6Mission, lateJoiner, true);
    }

    @Test
    void onlyMissionsInTheMonthAreReturnedInDateOrder() {
        List<CalendarMissionRow> rows = calendar.findMissionsInMonth(groupId, me, JAN_1, JAN_31);

        assertEquals(List.of(jan5Mission, jan6Mission), rows.stream().map(CalendarMissionRow::getMissionId).toList());
        assertEquals(LocalDate.of(2026, 1, 5), rows.get(0).getMissionDate());
        assertEquals("DRAW_A_TO_B", rows.get(1).getTemplate());
        assertEquals("idle", rows.get(1).getTargetNickname());
    }

    @Test
    void memberCountIncludesLeftSubmittersAndExcludesLaterJoiners() {
        List<CalendarMissionRow> rows = calendar.findMissionsInMonth(groupId, me, JAN_1, JAN_31);

        assertEquals(2, rows.get(0).getSubmittedCount());
        assertEquals(3, rows.get(0).getMemberCount()); // me, idle + 나간 leaver
        assertEquals(1, rows.get(1).getSubmittedCount());
        assertEquals(3, rows.get(1).getMemberCount()); // me, idle, lateJoiner
    }

    @Test
    void onlyTheLatestMissionInSlotIsOpen() {
        List<CalendarMissionRow> rows = calendar.findMissionsInMonth(groupId, me, JAN_1, JAN_31);

        assertTrue(rows.get(0).isClosed());
        assertFalse(rows.get(1).isClosed());
    }

    @Test
    void myFlagsDependOnWhoIsAsking() {
        CalendarMissionRow mine = calendar.findMissionsInMonth(groupId, me, JAN_1, JAN_31).get(0);
        assertTrue(mine.isSubmittedByMe());
        assertFalse(mine.isLateByMe());
        assertTrue(mine.isMemberWhenPublished());

        CalendarMissionRow idles = calendar.findMissionsInMonth(groupId, idle, JAN_1, JAN_31).get(0);
        assertFalse(idles.isSubmittedByMe());
        assertTrue(idles.isMemberWhenPublished());

        List<CalendarMissionRow> lateJoiners = calendar.findMissionsInMonth(groupId, lateJoiner, JAN_1, JAN_31);
        assertFalse(lateJoiners.get(0).isMemberWhenPublished());
        assertTrue(lateJoiners.get(1).isSubmittedByMe());
        assertTrue(lateJoiners.get(1).isLateByMe());
        assertTrue(lateJoiners.get(1).isMemberWhenPublished());
    }

    @Test
    void ownerWhoJoinedSecondsAfterFirstMissionCountsAsMember() {
        Long owner = insertUser("owner");
        Long newGroup = insertGroup(owner);
        join(newGroup, owner, "2026-03-02 15:20:01");
        insertMission(newGroup, "2026-03-02", "2026-03-02 15:20:00", "PHOTO_NOW", "첫 미션", "{}");

        CalendarMissionRow row = calendar.findMissionsInMonth(newGroup, owner,
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31)).getFirst();

        assertTrue(row.isMemberWhenPublished());
        assertEquals(1, row.getMemberCount());
    }

    @Test
    void statsQueriesCountMySubmissionsAcrossGroups() {
        Long otherGroup = insertGroup(me);
        join(otherGroup, me, "2026-01-01 08:00:00");
        Long other = insertMission(otherGroup, "2026-01-05", "2026-01-05 10:00:00", "TEXT_ACROSTIC", "삼행시", "{}");
        submit(other, me, false);

        Map<String, Long> byTemplate = calendar.countSubmissionsByTemplate(me).stream()
                .collect(Collectors.toMap(TemplateCountRow::getTemplate, TemplateCountRow::getCount));

        assertEquals(Map.of("PHOTO_NOW", 1L, "TEXT_ACROSTIC", 1L), byTemplate);
        assertEquals(List.of(LocalDate.of(2026, 1, 5)), calendar.findSubmittedDates(me));
        assertEquals(2, calendar.countGroups(me));
        assertEquals(List.of(LocalDate.of(2026, 1, 6)), calendar.findSubmittedDates(lateJoiner));
    }

    private Long insertUser(String nickname) {
        String email = "test-" + UUID.randomUUID() + "@test.com";
        jdbc.update("INSERT INTO users (nickname, email, password_hash, terms_agreed_at, privacy_agreed_at) VALUES (?, ?, ?, NOW(), NOW())",
                nickname, email, "unused");
        return jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
    }

    private Long insertGroup(Long ownerId) {
        jdbc.update("INSERT INTO `groups` (name, owner_id, invite_code) VALUES ('Test group', ?, ?)",
                ownerId, UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase());
        return jdbc.queryForObject("SELECT MAX(id) FROM `groups` WHERE owner_id = ?", Long.class, ownerId);
    }

    private void join(Long groupId, Long userId, String joinedAt) {
        jdbc.update("INSERT INTO group_members (group_id, user_id, role, joined_at) VALUES (?, ?, 'MEMBER', ?)",
                groupId, userId, joinedAt);
    }

    private Long insertMission(Long groupId, String date, String publishedAt, String template, String content,
            String params) {
        jdbc.update("INSERT INTO missions (group_id, content, category, mission_date, template, params, published_at, deadline_at) "
                + "VALUES (?, ?, 'TEST', ?, ?, ?, ?, ? + INTERVAL 14 HOUR)",
                groupId, content, date, template, params, publishedAt, publishedAt);
        return jdbc.queryForObject("SELECT MAX(id) FROM missions WHERE group_id = ?", Long.class, groupId);
    }

    private void submit(Long missionId, Long userId, boolean late) {
        jdbc.update("INSERT INTO submissions (mission_id, user_id, media_url, is_late) VALUES (?, ?, 'test.png', ?)",
                missionId, userId, late);
    }
}
