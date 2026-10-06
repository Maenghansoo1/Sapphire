package com.sapphire.hawe.group.dao;

import com.sapphire.hawe.group.domain.GroupMember;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface GroupMemberMapper {

    @Insert("INSERT INTO group_members (group_id, user_id, role) VALUES (#{groupId}, #{userId}, #{role})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(GroupMember member);

    @Select("SELECT id, group_id, user_id, role, joined_at FROM group_members "
            + "WHERE group_id = #{groupId} AND user_id = #{userId}")
    GroupMember findByGroupIdAndUserId(@Param("groupId") Long groupId, @Param("userId") Long userId);

    @Select("SELECT COUNT(*) > 0 FROM group_members WHERE group_id = #{groupId} AND user_id = #{userId}")
    boolean existsByGroupIdAndUserId(@Param("groupId") Long groupId, @Param("userId") Long userId);

    @Delete("DELETE FROM group_members WHERE group_id = #{groupId} AND user_id = #{userId}")
    void deleteByGroupIdAndUserId(@Param("groupId") Long groupId, @Param("userId") Long userId);

    @Select("SELECT gm.user_id AS user_id, u.nickname AS nickname, gm.role AS role, "
            + "(SELECT COUNT(*) FROM submissions s JOIN missions m ON m.id = s.mission_id "
            + "WHERE s.user_id = gm.user_id AND m.group_id = gm.group_id "
            + "AND m.mission_date >= DATE_FORMAT(CURDATE(), '%Y-%m-01')) AS submission_count_this_month, "
            + "EXISTS (SELECT 1 FROM submissions s JOIN missions m ON m.id = s.mission_id "
            + "WHERE s.user_id = gm.user_id AND m.group_id = gm.group_id AND m.mission_date = CURDATE()) AS stamped_today "
            + "FROM group_members gm JOIN users u ON u.id = gm.user_id "
            + "WHERE gm.group_id = #{groupId} ORDER BY gm.joined_at ASC LIMIT #{limit} OFFSET #{offset}")
    List<GroupMemberRow> findMembersByGroupId(@Param("groupId") Long groupId, @Param("offset") long offset,
            @Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM group_members WHERE group_id = #{groupId}")
    long countByGroupId(@Param("groupId") Long groupId);
}
