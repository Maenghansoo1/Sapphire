package com.sapphire.hawe.mission.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface MissionMapper {

    @Select("SELECT m.id, m.group_id, m.content, m.category, m.template, m.params, m.slot_no, "
            + "m.published_at, m.deadline_at, EXISTS (SELECT 1 FROM submissions s "
            + "WHERE s.mission_id = m.id AND s.user_id = #{userId}) AS submitted_by_me "
            + "FROM missions m WHERE m.group_id = #{groupId} AND m.mission_date = CURDATE() "
            + "ORDER BY m.slot_no ASC")
    List<MissionRow> findTodayByGroupId(@Param("groupId") Long groupId, @Param("userId") Long userId);

    @Select("SELECT m.id, m.group_id, m.content, m.category, m.template, m.params, m.slot_no, "
            + "m.published_at, m.deadline_at, EXISTS (SELECT 1 FROM submissions s "
            + "WHERE s.mission_id = m.id AND s.user_id = #{userId}) AS submitted_by_me "
            + "FROM missions m WHERE m.id = #{missionId}")
    MissionRow findById(@Param("missionId") Long missionId, @Param("userId") Long userId);
}
