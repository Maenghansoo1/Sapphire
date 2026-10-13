package com.sapphire.hawe.calendar.dao;

import com.sapphire.hawe.mission.dao.MissionMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface CalendarMapper {

    String JOINED_BY_PUBLISH = MissionMapper.JOINED_BY_PUBLISH;

    @Select("SELECT m.id AS mission_id, m.mission_date, m.template, m.content, "
            + "(SELECT u.nickname FROM users u WHERE u.id = JSON_VALUE(m.params, '$.targetUserId')) AS target_nickname, "
            + "(SELECT COUNT(*) FROM submissions s WHERE s.mission_id = m.id) AS submitted_count, "
            + MissionMapper.MEMBER_COUNT + "AS member_count, "
            + "EXISTS (SELECT 1 FROM submissions s WHERE s.mission_id = m.id "
            + "AND s.user_id = #{userId}) AS submitted_by_me, "
            + "EXISTS (SELECT 1 FROM submissions s WHERE s.mission_id = m.id "
            + "AND s.user_id = #{userId} AND s.is_late = 1) AS late_by_me, "
            + "EXISTS (SELECT 1 FROM group_members gm WHERE gm.group_id = m.group_id "
            + "AND gm.user_id = #{userId} AND " + JOINED_BY_PUBLISH + ") AS member_when_published, "
            // 현재 미션이 아니면 닫혔다 (같은 슬롯에 다음 미션이 열렸거나, 없어진·건너뛴 슬롯 — MissionMapper.CURRENT)
            + "NOT (" + MissionMapper.CURRENT + ") AS closed "
            + "FROM missions m WHERE m.group_id = #{groupId} AND m.mission_date BETWEEN #{from} AND #{to} "
            + "ORDER BY m.mission_date, m.slot_no")
    List<CalendarMissionRow> findMissionsInMonth(@Param("groupId") Long groupId, @Param("userId") Long userId,
            @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Select("SELECT m.template, COUNT(*) AS count FROM submissions s JOIN missions m ON m.id = s.mission_id "
            + "WHERE s.user_id = #{userId} GROUP BY m.template")
    List<TemplateCountRow> countSubmissionsByTemplate(@Param("userId") Long userId);

    @Select("SELECT DISTINCT m.mission_date FROM submissions s JOIN missions m ON m.id = s.mission_id "
            + "WHERE s.user_id = #{userId} ORDER BY m.mission_date DESC")
    List<LocalDate> findSubmittedDates(@Param("userId") Long userId);

    @Select("SELECT COUNT(*) FROM group_members WHERE user_id = #{userId}")
    long countGroups(@Param("userId") Long userId);
}
