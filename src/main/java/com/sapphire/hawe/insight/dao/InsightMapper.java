package com.sapphire.hawe.insight.dao;

import com.sapphire.hawe.group.dao.GroupMemberMapper;
import com.sapphire.hawe.mission.dao.MissionMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface InsightMapper {
    @Select("SELECT m.id, m.content, m.template, m.published_at, " + MissionMapper.MEMBER_COUNT
            + "AS member_count FROM missions m WHERE m.group_id=#{groupId} "
            + "AND m.mission_date BETWEEN #{from} AND #{to} AND NOT (" + MissionMapper.CURRENT + ")")
    List<InsightRow> findClosedMissions(@Param("groupId") Long groupId,
            @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Select("SELECT EXISTS(SELECT 1 FROM missions m WHERE m.group_id=#{groupId} "
            + "AND m.mission_date BETWEEN #{from} AND #{to} AND (" + MissionMapper.CURRENT + "))")
    boolean hasCurrentMissionInMonth(@Param("groupId") Long groupId,
            @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Select("<script>SELECT s.id, s.mission_id, s.user_id, s.submitted_at, s.is_late AS late, "
            + "m.template, JSON_VALUE(s.answer_data, '$.text') AS text FROM submissions s "
            + "JOIN missions m ON m.id=s.mission_id WHERE s.mission_id IN "
            + "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>")
    List<InsightRow> findSubmissions(@Param("ids") List<Long> ids);

    @Select("<script>SELECT submission_id, COUNT(*) AS count FROM reactions WHERE submission_id IN "
            + "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach> "
            + "GROUP BY submission_id</script>")
    List<InsightRow> countReactionsBySubmission(@Param("ids") List<Long> ids);

    @Select("<script>SELECT submission_id, sticker_type, COUNT(*) AS count FROM reactions WHERE submission_id IN "
            + "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach> "
            + "GROUP BY submission_id, sticker_type</script>")
    List<InsightRow> countStickersBySubmission(@Param("ids") List<Long> ids);

    @Select("<script>SELECT submission_id, COUNT(*) AS count FROM comments WHERE submission_id IN "
            + "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach> "
            + "GROUP BY submission_id</script>")
    List<InsightRow> countCommentsBySubmission(@Param("ids") List<Long> ids);

    @Select("<script>SELECT u.id AS user_id, " + GroupMemberMapper.DISPLAY_NICKNAME + ", "
            + GroupMemberMapper.DISPLAY_CHARACTER + ", " + GroupMemberMapper.DISPLAY_IMAGE
            + " FROM users u LEFT JOIN characters c ON c.id=u.character_id "
            + "LEFT JOIN group_members gm ON gm.group_id=#{groupId} AND gm.user_id=u.id "
            + GroupMemberMapper.GROUP_CHARACTER_JOIN + "WHERE u.id IN "
            + "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>")
    List<InsightRow> findDisplayProfiles(@Param("groupId") Long groupId, @Param("ids") List<Long> ids);

    @Select("SELECT group_id, month, status, payload, generated_at, expires_at, updated_at, "
            + "(expires_at IS NOT NULL AND expires_at <= NOW()) AS expired, "
            + "(updated_at < NOW() - INTERVAL 5 MINUTE) AS abandoned "
            + "FROM group_insights WHERE group_id=#{groupId} AND month=#{month}")
    InsightRow findCache(@Param("groupId") Long groupId, @Param("month") String month);

    @Insert("INSERT IGNORE INTO group_insights (group_id, month, status, attempts) "
            + "VALUES (#{groupId}, #{month}, 'GENERATING', 1)")
    int tryStart(@Param("groupId") Long groupId, @Param("month") String month);

    @Update("UPDATE group_insights SET status='GENERATING', attempts=attempts+1, updated_at=NOW() "
            + "WHERE group_id=#{groupId} AND month=#{month} AND (status='FAILED' "
            + "OR (status='READY' AND expires_at IS NOT NULL AND expires_at <= NOW()) "
            + "OR (status='GENERATING' AND updated_at < NOW() - INTERVAL 5 MINUTE))")
    int restart(@Param("groupId") Long groupId, @Param("month") String month);

    @Update("UPDATE group_insights SET status='READY', payload=#{payload}, generated_at=NOW(), "
            + "expires_at=CASE WHEN NOW() >= #{finalizeAt} AND NOT (#{hasCurrentMission}) THEN NULL "
            + "WHEN NOW() < #{finalizeAt} THEN LEAST(DATE_ADD(NOW(), INTERVAL #{ttlSeconds} SECOND), #{finalizeAt}) "
            + "ELSE DATE_ADD(NOW(), INTERVAL #{ttlSeconds} SECOND) END "
            + "WHERE group_id=#{groupId} AND month=#{month} AND status='GENERATING'")
    int complete(@Param("groupId") Long groupId, @Param("month") String month,
            @Param("payload") String payload, @Param("finalizeAt") LocalDateTime finalizeAt,
            @Param("hasCurrentMission") boolean hasCurrentMission,
            @Param("ttlSeconds") long ttlSeconds);

    @Update("UPDATE group_insights SET status='FAILED' WHERE group_id=#{groupId} AND month=#{month} AND status='GENERATING'")
    int fail(@Param("groupId") Long groupId, @Param("month") String month);

    @Select("SELECT DISTINCT group_id FROM missions WHERE mission_date BETWEEN #{from} AND #{to}")
    List<Long> findGroupIdsWithMissions(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
