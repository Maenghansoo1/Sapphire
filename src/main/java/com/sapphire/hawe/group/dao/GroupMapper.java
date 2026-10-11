package com.sapphire.hawe.group.dao;

import com.sapphire.hawe.group.domain.Group;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface GroupMapper {

    @Insert("INSERT INTO `groups` (name, owner_id, relation_type, daily_mission_count) "
            + "VALUES (#{name}, #{ownerId}, #{relationType}, #{dailyMissionCount})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(Group group);

    @Select("SELECT id, name, owner_id, relation_type, daily_mission_count, created_at "
            + "FROM `groups` WHERE id = #{id}")
    Group findById(@Param("id") Long id);

    @Update("UPDATE `groups` SET name = #{name}, relation_type = #{relationType}, "
            + "daily_mission_count = #{dailyMissionCount} WHERE id = #{id}")
    void update(Group group);

    @Select("SELECT g.id AS id, g.name AS name, "
            + "EXISTS (SELECT 1 FROM missions m JOIN submissions s ON s.mission_id = m.id AND s.user_id = #{userId} "
            + "WHERE m.group_id = g.id AND m.mission_date = CURDATE()) AS submitted_today "
            + "FROM `groups` g JOIN group_members gm ON gm.group_id = g.id "
            + "WHERE gm.user_id = #{userId} ORDER BY g.id DESC LIMIT #{limit} OFFSET #{offset}")
    List<GroupSummaryRow> findSummariesByUserId(@Param("userId") Long userId, @Param("offset") long offset,
            @Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM group_members WHERE user_id = #{userId}")
    long countByUserId(@Param("userId") Long userId);
}
