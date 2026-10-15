package com.sapphire.hawe.group.dao;

import com.sapphire.hawe.group.domain.GroupMember;
import com.sapphire.hawe.mission.dao.MissionMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.time.LocalDate;
import com.sapphire.hawe.global.time.SeoulTime;

@Mapper
public interface GroupMemberMapper {
    String DISPLAY_NICKNAME = "CASE WHEN gm.nickname_override IS NOT NULL THEN gm.nickname_override ELSE u.nickname END AS nickname";
    String DISPLAY_CHARACTER = "CASE WHEN gm.nickname_override IS NOT NULL THEN gc.svg_key ELSE c.svg_key END AS character_key";
    String DISPLAY_IMAGE = "CASE WHEN gm.nickname_override IS NOT NULL THEN gm.profile_image_url_override ELSE u.profile_image_url END AS profile_image_url";
    String GROUP_CHARACTER_JOIN = "LEFT JOIN characters gc ON gc.id = gm.character_id_override ";

    @Insert("INSERT INTO group_members (group_id, user_id, role) VALUES (#{groupId}, #{userId}, #{role})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(GroupMember member);

    @Select("SELECT id, group_id, user_id, role, joined_at, nickname_override, character_id_override, "
            + "profile_image_url_override FROM group_members "
            + "WHERE group_id = #{groupId} AND user_id = #{userId}")
    GroupMember findByGroupIdAndUserId(@Param("groupId") Long groupId, @Param("userId") Long userId);

    @Update("UPDATE group_members SET nickname_override = #{nickname,jdbcType=VARCHAR}, "
            + "character_id_override = #{characterId,jdbcType=BIGINT}, "
            + "profile_image_url_override = #{imageKey,jdbcType=VARCHAR} "
            + "WHERE group_id = #{groupId} AND user_id = #{userId}")
    int setGroupProfile(@Param("groupId") Long groupId, @Param("userId") Long userId,
            @Param("nickname") String nickname, @Param("characterId") Long characterId,
            @Param("imageKey") String imageKey);

    @Select("SELECT COUNT(*) > 0 FROM group_members WHERE group_id = #{groupId} AND user_id = #{userId}")
    boolean existsByGroupIdAndUserId(@Param("groupId") Long groupId, @Param("userId") Long userId);

    @Delete("DELETE FROM group_members WHERE group_id = #{groupId} AND user_id = #{userId}")
    void deleteByGroupIdAndUserId(@Param("groupId") Long groupId, @Param("userId") Long userId);

    @Delete("DELETE FROM group_members WHERE group_id = #{groupId}")
    void deleteByGroupId(@Param("groupId") Long groupId);

    @Update("UPDATE group_members SET role = #{role} WHERE group_id = #{groupId} AND user_id = #{userId}")
    void updateRole(@Param("groupId") Long groupId, @Param("userId") Long userId, @Param("role") String role);

    // submission_count_this_month = 이번 달 제출한 날 수 (하루에 여러 개 내도 1), stamped_today = 현재 미션 중 하나라도 제출
    @Select("SELECT gm.user_id AS user_id, " + DISPLAY_NICKNAME + ", " + DISPLAY_IMAGE + ", "
            + DISPLAY_CHARACTER + ", (gm.nickname_override IS NOT NULL) AS group_profile, gm.role AS role, "
            + "(SELECT COUNT(DISTINCT m.mission_date) FROM submissions s JOIN missions m ON m.id = s.mission_id "
            + "WHERE s.user_id = gm.user_id AND m.group_id = gm.group_id "
            + "AND m.mission_date >= #{monthStart}) AS submission_count_this_month, "
            + "EXISTS (SELECT 1 FROM submissions s JOIN missions m ON m.id = s.mission_id "
            + "WHERE s.user_id = gm.user_id AND m.group_id = gm.group_id AND " + MissionMapper.CURRENT
            + ") AS stamped_today "
            + "FROM group_members gm JOIN users u ON u.id = gm.user_id "
            + "LEFT JOIN characters c ON c.id = u.character_id "
            + GROUP_CHARACTER_JOIN
            + "WHERE gm.group_id = #{groupId} ORDER BY gm.joined_at ASC, gm.id ASC LIMIT #{limit} OFFSET #{offset}")
    List<GroupMemberRow> findMembersByGroupId(@Param("groupId") Long groupId, @Param("offset") long offset,
            @Param("limit") int limit, @Param("monthStart") LocalDate monthStart);

    default List<GroupMemberRow> findMembersByGroupId(Long groupId, long offset, int limit) {
        return findMembersByGroupId(groupId, offset, limit, SeoulTime.today().withDayOfMonth(1));
    }

    @Select("SELECT COUNT(*) FROM group_members WHERE group_id = #{groupId}")
    long countByGroupId(@Param("groupId") Long groupId);

    @Select("SELECT user_id FROM group_members WHERE group_id = #{groupId} ORDER BY joined_at, id")
    List<Long> findUserIdsByGroupId(@Param("groupId") Long groupId);
}
