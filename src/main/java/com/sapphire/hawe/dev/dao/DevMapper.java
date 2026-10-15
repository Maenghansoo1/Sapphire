package com.sapphire.hawe.dev.dao;

import com.sapphire.hawe.mission.dao.MissionMapper;
import com.sapphire.hawe.mission.dao.MissionRow;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface DevMapper {
    @Update("UPDATE missions SET deadline_at=#{now} WHERE id=#{missionId}")
    int deadlineNow(@Param("missionId") Long missionId, @Param("now") LocalDateTime now);

    @Update("UPDATE missions m SET m.deadline_at=#{now} WHERE m.group_id=#{groupId} AND " + MissionMapper.CURRENT)
    int deadlineCurrent(@Param("groupId") Long groupId, @Param("now") LocalDateTime now);

    @Select("SELECT id, group_id, slot_no, template, content FROM missions WHERE id=#{missionId}")
    MissionRow findMission(@Param("missionId") Long missionId);

    @Select("SELECT m.id, m.group_id, m.slot_no, m.template, m.content FROM missions m "
            + "WHERE m.group_id=#{groupId} AND " + MissionMapper.CURRENT + " ORDER BY m.slot_no")
    List<MissionRow> findCurrentMissions(@Param("groupId") Long groupId);

    @Select("SELECT s.id AS submission_id, s.user_id, u.nickname, m.template, s.submitted_at, "
            + "s.is_late AS late FROM submissions s JOIN users u ON u.id=s.user_id "
            + "JOIN missions m ON m.id=s.mission_id WHERE s.mission_id=#{missionId} "
            + "ORDER BY s.submitted_at, s.id")
    List<DevSubmissionRow> findSubmissions(@Param("missionId") Long missionId);

    @Update("UPDATE `groups` SET created_at=#{createdAt} WHERE id=#{groupId}")
    int backdateGroup(@Param("groupId") Long groupId, @Param("createdAt") LocalDateTime createdAt);

    @Update("UPDATE group_members SET joined_at=#{joinedAt} WHERE group_id=#{groupId}")
    int backdateMembers(@Param("groupId") Long groupId, @Param("joinedAt") LocalDateTime joinedAt);

    @Delete("DELETE FROM group_insights WHERE group_id=#{groupId} AND month=#{month}")
    int deleteInsight(@Param("groupId") Long groupId, @Param("month") String month);

    @Select("SELECT id FROM `groups` WHERE name LIKE '[DEV] %' ORDER BY id")
    List<Long> findDevGroupIds();

    @Select("SELECT COUNT(*) FROM `groups` WHERE name=#{name}")
    int countGroupsNamed(@Param("name") String name);
}
