package com.sapphire.hawe.mission.dao;

import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import com.sapphire.hawe.global.time.SeoulTime;

import java.util.UUID;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.springframework.dao.DuplicateKeyException;
import static org.junit.jupiter.api.Assertions.assertThrows;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
class MissionMapperTests {

    @Autowired JdbcTemplate jdbc;
    @Autowired MissionMapper missions;
    @Autowired SqlSessionTemplate sqlSession;

    @Test
    void currentRowsAreOrderedAndSubmissionStatusIsPerUser() {
        Long userId = insertUser();
        Long otherUserId = insertUser();
        Long groupId = insertGroup(userId);
        jdbc.update("INSERT INTO group_members (group_id, user_id, role) VALUES (?, ?, 'owner')",
                groupId, userId);
        Long secondMissionId = insertMission(groupId, 2, "CURDATE()");
        Long firstMissionId = insertMission(groupId, 1, "CURDATE()");
        insertMission(groupId, 1, "CURDATE() - INTERVAL 1 DAY");
        jdbc.update("INSERT INTO submissions (mission_id, user_id, media_url) VALUES (?, ?, 'test.png')",
                firstMissionId, userId);

        var rows = missions.findCurrentByGroupId(groupId, userId);
        assertEquals(2, rows.size());
        assertEquals(firstMissionId, rows.getFirst().getId());
        assertEquals(secondMissionId, rows.get(1).getId());
        assertTrue(rows.getFirst().isSubmittedByMe());
        assertFalse(rows.get(1).isSubmittedByMe());
        assertEquals("{}", rows.getFirst().getParams());
        assertFalse(missions.findById(firstMissionId, otherUserId).isSubmittedByMe());
        assertEquals(3, rows.getFirst().getNumber());
        assertEquals(2, rows.get(1).getNumber());
        assertEquals(1, missions.findMemberStatuses(firstMissionId).size());
        assertTrue(missions.findMemberStatuses(firstMissionId).getFirst().isSubmitted());
        // 그룹 목록 요약도 슬롯마다 하나씩, 슬롯 순서
        var summaries = missions.findCurrentSummariesByGroupIds(java.util.List.of(groupId), userId);
        assertEquals(java.util.List.of(firstMissionId, secondMissionId),
                summaries.stream().map(MissionSummaryRow::getMissionId).toList());
        assertEquals(1, summaries.getFirst().getSubmittedCount());
        assertTrue(summaries.getFirst().isSubmittedByMe());
        assertEquals(0, summaries.get(1).getSubmittedCount());
        assertFalse(summaries.get(1).isSubmittedByMe());
        assertNull(missions.findById(Long.MAX_VALUE, userId));
    }

    @Test
    void removedOrSkippedSlotsCloseAtTheNextDaysPublish() {
        Long userId = insertUser();
        Long groupId = insertGroup(userId);
        jdbc.update("UPDATE `groups` SET daily_mission_count = 3 WHERE id = ?", groupId);
        Long first = insertMission(groupId, 1, "CURDATE() - INTERVAL 4 DAY");
        Long second = insertMission(groupId, 2, "CURDATE() - INTERVAL 4 DAY");
        Long third = insertMission(groupId, 3, "CURDATE() - INTERVAL 4 DAY");
        assertEquals(java.util.List.of(first, second, third), currentIds(groupId, userId));

        // 하루 미션 수를 1로 줄인 날: 이미 열린 2·3번은 그날 계속 열려 있다
        jdbc.update("UPDATE `groups` SET daily_mission_count = 1 WHERE id = ?", groupId);
        assertEquals(java.util.List.of(first, second, third), currentIds(groupId, userId));
        assertTrue(missions.isCurrent(third));
        // 다음 날 첫 발행에서 없어진 슬롯은 닫힌다
        Long nextDay = insertMission(groupId, 1, "CURDATE() - INTERVAL 3 DAY");
        assertEquals(java.util.List.of(nextDay), currentIds(groupId, userId));
        assertFalse(missions.isCurrent(second));
        assertEquals(java.util.List.of(nextDay), missions.findCurrentSummariesByGroupIds(java.util.List.of(groupId), userId)
                .stream().map(MissionSummaryRow::getMissionId).toList());

        // 며칠 뒤 다시 3개로 늘려도 하루 넘게 비었던 슬롯의 옛 미션은 다시 열리지 않는다
        Long later = insertMission(groupId, 1, "CURDATE() - INTERVAL 1 DAY");
        jdbc.update("UPDATE `groups` SET daily_mission_count = 3 WHERE id = ?", groupId);
        assertEquals(java.util.List.of(later), currentIds(groupId, userId));
        Long reopened = insertMission(groupId, 2, "CURDATE() - INTERVAL 1 DAY");
        assertEquals(java.util.List.of(later, reopened), currentIds(groupId, userId));
    }

    private java.util.List<Long> currentIds(Long groupId, Long userId) {
        // JdbcTemplate 로 바꾼 행은 MyBatis 가 모르므로, 같은 조회를 다시 하기 전에 캐시를 비운다
        sqlSession.clearCache();
        return missions.findCurrentByGroupId(groupId, userId).stream().map(MissionRow::getId).toList();
    }

    @Test
    void insertIsUniquePerGroupDateAndSlotAndYesterdayCanBeCurrent() {
        Long userId = insertUser();
        Long groupId = insertGroup(userId);
        LocalDate yesterday = SeoulTime.today().minusDays(1);
        MissionInsertRow row = new MissionInsertRow();
        row.setGroupId(groupId);
        row.setContent("Yesterday");
        row.setCategory("일상");
        row.setMissionDate(yesterday);
        row.setTemplate("PHOTO_NOW");
        row.setParams("{}");
        row.setSlotNo(1);
        row.setPublishedAt(yesterday.atTime(23, 30));
        row.setDeadlineAt(yesterday.plusDays(1).atTime(23, 0));
        missions.insert(row);

        assertTrue(missions.existsByGroupIdAndDateAndSlot(groupId, yesterday, 1));
        assertFalse(missions.existsByGroupIdAndDateAndSlot(groupId, yesterday.plusDays(1), 1));
        assertEquals(java.util.List.of("PHOTO_NOW"), missions.findTemplatesByGroupIdAndDate(groupId, yesterday));
        assertEquals(java.util.List.of("Yesterday"), recentContents(groupId, 5));
        assertEquals(row.getId(), missions.findCurrentByGroupId(groupId, userId).getFirst().getId());
        assertEquals(yesterday, missions.findCurrentByGroupId(groupId, userId).getFirst().getMissionDate());
        assertThrows(DuplicateKeyException.class, () -> missions.insert(row));
    }

    @Test
    void recentContentsAreNewestFirstAndLimited() {
        Long groupId = insertGroup(insertUser());
        LocalDate today = SeoulTime.today();
        insertContent(groupId, "그저께", today.minusDays(2), 1, 9);
        insertContent(groupId, "오늘 2번", today, 2, 14);
        insertContent(groupId, "어제", today.minusDays(1), 1, 9);
        insertContent(groupId, "오늘 1번", today, 1, 9);
        assertEquals(java.util.List.of("오늘 2번", "오늘 1번", "어제", "그저께"),
                recentContents(groupId, 5));
        assertEquals(java.util.List.of("오늘 2번", "오늘 1번"), recentContents(groupId, 2));
        assertTrue(recentContents(insertGroup(insertUser()), 5).isEmpty());
        RecentMissionRow newest = missions.findRecentByGroupId(groupId, 1).getFirst();
        assertEquals("일상", newest.getCategory());
        assertEquals("PHOTO_NOW", newest.getTemplate());
    }

    @Test
    void closedMissionStatsCountParticipationAndReactions() {
        Long owner = insertUser();
        Long member = insertUser();
        Long lateJoiner = insertUser();
        Long groupId = insertGroup(owner);
        join(groupId, owner, "CURDATE() - INTERVAL 10 DAY");
        join(groupId, member, "CURDATE() - INTERVAL 10 DAY");
        insertMission(groupId, 1, "CURDATE() - INTERVAL 40 DAY");      // 30일보다 오래됨 → 제외
        Long older = insertMission(groupId, 1, "CURDATE() - INTERVAL 3 DAY");
        Long previous = insertMission(groupId, 1, "CURDATE() - INTERVAL 2 DAY");
        insertMission(groupId, 1, "CURDATE()");                         // 현재 미션 → 제외
        join(groupId, lateJoiner, "CURDATE() - INTERVAL 2 DAY + INTERVAL 1 HOUR");  // previous 가 열린 뒤 들어옴
        Long ownerAnswer = submit(older, owner);
        submit(older, member);
        submit(previous, owner);
        submit(previous, lateJoiner);
        jdbc.update("INSERT INTO reactions (submission_id, user_id, sticker_type) VALUES (?, ?, 'heart'), (?, ?, 'fire')",
                ownerAnswer, member, ownerAnswer, owner);
        jdbc.update("INSERT INTO comments (submission_id, user_id, content) VALUES (?, ?, 'ㅋㅋ')", ownerAnswer, member);
        long mascot = jdbc.queryForObject("SELECT id FROM users WHERE is_ai=1", Long.class);
        jdbc.update("INSERT INTO reactions (submission_id, user_id, sticker_type) VALUES (?, ?, 'wow')", ownerAnswer, mascot);
        jdbc.update("INSERT INTO comments (submission_id, user_id, content) VALUES (?, ?, '별이')", ownerAnswer, mascot);

        var stats = missions.findClosedStatsByGroupId(groupId, SeoulTime.today().minusDays(30), 150);

        assertEquals(2, stats.size());
        // 최근 것부터: previous(2명 중 1명 + 열린 뒤 들어와서 낸 1명 → 인원 3, 제출 2), older(2명 모두 제출, 반응 2 + 댓글 1)
        assertEquals(java.util.List.of(2L, 2L), stats.stream().map(MissionStatRow::getSubmittedCount).toList());
        assertEquals(java.util.List.of(3L, 2L), stats.stream().map(MissionStatRow::getMemberCount).toList());
        assertEquals(java.util.List.of(0L, 3L), stats.stream().map(MissionStatRow::getEngagementCount).toList());
        assertEquals("PHOTO_NOW", stats.getFirst().getTemplate());
        assertEquals(1, missions.findClosedStatsByGroupId(groupId, SeoulTime.today().minusDays(30), 1).size());
    }

    private java.util.List<String> recentContents(Long groupId, int limit) {
        return missions.findRecentByGroupId(groupId, limit).stream().map(RecentMissionRow::getContent).toList();
    }

    private void join(Long groupId, Long userId, String joinedAt) {
        jdbc.update("INSERT INTO group_members (group_id, user_id, role, joined_at) VALUES (?, ?, 'member', "
                + joinedAt + ")", groupId, userId);
    }

    private Long submit(Long missionId, Long userId) {
        jdbc.update("INSERT INTO submissions (mission_id, user_id, media_url) VALUES (?, ?, 'test.png')", missionId, userId);
        return jdbc.queryForObject("SELECT id FROM submissions WHERE mission_id = ? AND user_id = ?", Long.class,
                missionId, userId);
    }

    @Test
    void insertStoresGenerationSourceAndExperimentArm() {
        Long groupId = insertGroup(insertUser());
        insertContent(groupId, "실험 밖", SeoulTime.today().minusDays(1), 1, 9);
        MissionInsertRow row = new MissionInsertRow();
        row.setGroupId(groupId);
        row.setContent("AI 문장");
        row.setCategory("일상");
        row.setMissionDate(SeoulTime.today());
        row.setTemplate("PHOTO_NOW");
        row.setParams("{}");
        row.setSlotNo(1);
        row.setPublishedAt(SeoulTime.today().atTime(9, 0));
        row.setDeadlineAt(SeoulTime.today().atTime(23, 0));
        row.setGenerationSource("AI_FALLBACK");
        row.setExperimentArm("AI");
        missions.insert(row);
        assertEquals(java.util.Map.of("generation_source", "AI_FALLBACK", "experiment_arm", "AI"), jdbc.queryForMap(
                "SELECT generation_source, experiment_arm FROM missions WHERE id = ?", row.getId()));
        var outside = jdbc.queryForMap("SELECT generation_source, experiment_arm FROM missions WHERE group_id = ? "
                + "AND content = '실험 밖'", groupId);
        assertNull(outside.get("generation_source"));
        assertNull(outside.get("experiment_arm"));
    }

    private void insertContent(Long groupId, String content, LocalDate date, int slotNo, int hour) {
        MissionInsertRow row = new MissionInsertRow();
        row.setGroupId(groupId);
        row.setContent(content);
        row.setCategory("일상");
        row.setMissionDate(date);
        row.setTemplate("PHOTO_NOW");
        row.setParams("{}");
        row.setSlotNo(slotNo);
        row.setPublishedAt(date.atTime(hour, 0));
        row.setDeadlineAt(date.atTime(23, 0));
        missions.insert(row);
    }

    private Long insertUser() {
        String email = "mission-test-" + UUID.randomUUID() + "@test.com";
        jdbc.update("INSERT INTO users (nickname, email, password_hash, terms_agreed_at, privacy_agreed_at) VALUES ('Test', ?, 'unused', NOW(), NOW())", email);
        return jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
    }

    private Long insertGroup(Long ownerId) {
        jdbc.update("INSERT INTO `groups` (name, owner_id, invite_code) VALUES ('Test group', ?, ?)",
                ownerId, UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase());
        return jdbc.queryForObject("SELECT MAX(id) FROM `groups` WHERE owner_id = ?", Long.class, ownerId);
    }

    private Long insertMission(Long groupId, int slotNo, String missionDate) {
        jdbc.update("INSERT INTO missions (group_id, content, category, mission_date, template, params, "
                + "slot_no, published_at, deadline_at) VALUES (?, 'Test mission', 'TEST', " + missionDate
                + ", 'PHOTO_NOW', '{}', ?, " + missionDate + ", NOW() + INTERVAL 1 HOUR)", groupId, slotNo);
        return jdbc.queryForObject("SELECT MAX(id) FROM missions WHERE group_id = ?", Long.class, groupId);
    }
}
