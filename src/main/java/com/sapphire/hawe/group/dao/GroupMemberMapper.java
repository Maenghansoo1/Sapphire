package com.sapphire.hawe.group.dao;

import com.sapphire.hawe.group.domain.GroupMember;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

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

    @Delete("DELETE FROM group_members WHERE group_id = #{groupId}")
    void deleteByGroupId(@Param("groupId") Long groupId);

    @Update("UPDATE group_members SET role = #{role} WHERE group_id = #{groupId} AND user_id = #{userId}")
    void updateRole(@Param("groupId") Long groupId, @Param("userId") Long userId, @Param("role") String role);

    @Select("SELECT gm.user_id AS user_id, u.nickname AS nickname, u.profile_image_url, c.svg_key AS character_key, gm.role AS role, "
            + "(SELECT COUNT(*) FROM submissions s JOIN missions m ON m.id = s.mission_id "
            + "WHERE s.user_id = gm.user_id AND m.group_id = gm.group_id "
            + "AND m.mission_date >= DATE_FORMAT(CURDATE(), '%Y-%m-01')) AS submission_count_this_month, "
            + "EXISTS (SELECT 1 FROM submissions s JOIN missions m ON m.id = s.mission_id "
            + "WHERE s.user_id = gm.user_id AND m.group_id = gm.group_id "
            + "AND m.id = (SELECT m2.id FROM missions m2 WHERE m2.group_id = m.group_id "
            + "AND m2.slot_no = m.slot_no ORDER BY m2.published_at DESC, m2.id DESC LIMIT 1)) AS stamped_today "
            + "FROM group_members gm JOIN users u ON u.id = gm.user_id "
            + "LEFT JOIN characters c ON c.id = u.character_id "
            + "WHERE gm.group_id = #{groupId} ORDER BY gm.joined_at ASC, gm.id ASC LIMIT #{limit} OFFSET #{offset}")
    List<GroupMemberRow> findMembersByGroupId(@Param("groupId") Long groupId, @Param("offset") long offset,
            @Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM group_members WHERE group_id = #{groupId}")
    long countByGroupId(@Param("groupId") Long groupId);

    @Select("SELECT user_id FROM group_members WHERE group_id = #{groupId} ORDER BY joined_at, id")
    List<Long> findUserIdsByGroupId(@Param("groupId") Long groupId);
}
