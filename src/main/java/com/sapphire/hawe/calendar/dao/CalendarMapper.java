package com.sapphire.hawe.calendar.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface CalendarMapper {

    // "미션이 열릴 때 그룹에 있었는지". 그룹을 만들면 첫 미션 발행 시각과 그룹장 가입 시각이
    // 몇 초 어긋날 수 있어서 1분 여유를 둔다.
    String JOINED_BY_PUBLISH = "gm.joined_at <= m.published_at + INTERVAL 1 MINUTE";

    @Select("SELECT m.id AS mission_id, m.mission_date, m.template, m.content, "
            + "(SELECT u.nickname FROM users u WHERE u.id = JSON_VALUE(m.params, '$.targetUserId')) AS target_nickname, "
            + "(SELECT COUNT(*) FROM submissions s WHERE s.mission_id = m.id) AS submitted_count, "
            // 전체 인원 = 미션이 열릴 때 있던 지금 멤버 + 그 미션에 냈지만 지금은 위에 없는 사람(나간 사람 등)
            + "(SELECT COUNT(*) FROM group_members gm WHERE gm.group_id = m.group_id AND " + JOINED_BY_PUBLISH + ") "
            + "+ (SELECT COUNT(*) FROM submissions s WHERE s.mission_id = m.id AND NOT EXISTS ("
            + "SELECT 1 FROM group_members gm WHERE gm.group_id = m.group_id AND gm.user_id = s.user_id "
            + "AND " + JOINED_BY_PUBLISH + ")) AS member_count, "
            + "EXISTS (SELECT 1 FROM submissions s WHERE s.mission_id = m.id "
            + "AND s.user_id = #{userId}) AS submitted_by_me, "
            + "EXISTS (SELECT 1 FROM submissions s WHERE s.mission_id = m.id "
            + "AND s.user_id = #{userId} AND s.is_late = 1) AS late_by_me, "
            + "EXISTS (SELECT 1 FROM group_members gm WHERE gm.group_id = m.group_id "
            + "AND gm.user_id = #{userId} AND " + JOINED_BY_PUBLISH + ") AS member_when_published, "
            // 같은 슬롯에 더 나중 미션이 열렸으면 이 미션은 닫혔다 (MissionMapper.CURRENT와 같은 기준)
            + "EXISTS (SELECT 1 FROM missions n WHERE n.group_id = m.group_id AND n.slot_no = m.slot_no "
            + "AND (n.published_at > m.published_at OR (n.published_at = m.published_at AND n.id > m.id))) AS closed "
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
