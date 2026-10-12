package com.sapphire.hawe.group.dao;

import com.sapphire.hawe.group.domain.Group;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface GroupMapper {

    @Insert("INSERT INTO `groups` (name, owner_id, invite_code, relation_type, daily_mission_count, start_date) "
            + "VALUES (#{name}, #{ownerId}, #{inviteCode}, #{relationType}, #{dailyMissionCount}, #{startDate})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(Group group);

    @Select("SELECT id, name, owner_id, invite_code, relation_type, daily_mission_count, start_date, created_at "
            + "FROM `groups` WHERE id = #{id}")
    Group findById(@Param("id") Long id);

    @Select("SELECT id FROM `groups` ORDER BY id")
    List<Long> findAllIds();

    @Select("SELECT id, name, owner_id, invite_code, relation_type, daily_mission_count, start_date, created_at "
            + "FROM `groups` WHERE id = #{id} FOR UPDATE")
    Group findByIdForUpdate(@Param("id") Long id);

    @Select("SELECT id, name, owner_id, invite_code, relation_type, daily_mission_count, start_date, created_at "
            + "FROM `groups` WHERE invite_code = #{code}")
    Group findByInviteCode(@Param("code") String code);

    @Select("SELECT id, name, owner_id, invite_code, relation_type, daily_mission_count, start_date, created_at "
            + "FROM `groups` WHERE invite_code = #{code} FOR UPDATE")
    Group findByInviteCodeForUpdate(@Param("code") String code);

    @Update("UPDATE `groups` SET name = #{name}, owner_id = #{ownerId}, relation_type = #{relationType}, "
            + "daily_mission_count = #{dailyMissionCount}, start_date = #{startDate} WHERE id = #{id}")
    void update(Group group);

    @Select("SELECT g.id AS id, g.name AS name, g.relation_type, g.start_date "
            + "FROM `groups` g JOIN group_members gm ON gm.group_id = g.id "
            + "WHERE gm.user_id = #{userId} ORDER BY gm.joined_at ASC, g.id ASC LIMIT #{limit} OFFSET #{offset}")
    List<GroupSummaryRow> findSummariesByUserId(@Param("userId") Long userId, @Param("offset") long offset,
            @Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM group_members WHERE user_id = #{userId}")
    long countByUserId(@Param("userId") Long userId);

    @Select("SELECT COUNT(*) FROM missions WHERE group_id = #{groupId}")
    long countMissionsByGroupId(@Param("groupId") Long groupId);

    @Select("<script>SELECT gm.group_id, gm.user_id, u.nickname, u.profile_image_url, c.svg_key AS character_key "
            + "FROM group_members gm JOIN users u ON u.id = gm.user_id "
            + "LEFT JOIN characters c ON c.id = u.character_id WHERE gm.group_id IN "
            + "<foreach collection='groupIds' item='id' open='(' separator=',' close=')'>#{id}</foreach> "
            + "ORDER BY gm.group_id, gm.joined_at, gm.id</script>")
    List<GroupMemberPreviewRow> findMemberPreviewsByGroupIds(@Param("groupIds") List<Long> groupIds);

    @Delete("DELETE cm FROM comment_mentions cm JOIN comments c ON c.id = cm.comment_id "
            + "JOIN submissions s ON s.id = c.submission_id JOIN missions m ON m.id = s.mission_id "
            + "WHERE m.group_id = #{groupId}")
    void deleteCommentMentionsByGroupId(@Param("groupId") Long groupId);

    @Delete("DELETE c FROM comments c JOIN submissions s ON s.id = c.submission_id "
            + "JOIN missions m ON m.id = s.mission_id WHERE m.group_id = #{groupId}")
    void deleteCommentsByGroupId(@Param("groupId") Long groupId);

    @Delete("DELETE r FROM reactions r JOIN submissions s ON s.id = r.submission_id "
            + "JOIN missions m ON m.id = s.mission_id WHERE m.group_id = #{groupId}")
    void deleteReactionsByGroupId(@Param("groupId") Long groupId);

    @Delete("DELETE sg FROM submission_guesses sg JOIN submissions s ON s.id = sg.submission_id "
            + "JOIN missions m ON m.id = s.mission_id WHERE m.group_id = #{groupId}")
    void deleteSubmissionGuessesByGroupId(@Param("groupId") Long groupId);

    @Delete("DELETE s FROM submissions s JOIN missions m ON m.id = s.mission_id WHERE m.group_id = #{groupId}")
    void deleteSubmissionsByGroupId(@Param("groupId") Long groupId);

    @Delete("DELETE FROM missions WHERE group_id = #{groupId}")
    void deleteMissionsByGroupId(@Param("groupId") Long groupId);

    @Delete("DELETE FROM pokes WHERE group_id = #{groupId}")
    void deletePokesByGroupId(@Param("groupId") Long groupId);

    @Delete("DELETE FROM `groups` WHERE id = #{groupId}")
    void deleteById(@Param("groupId") Long groupId);
}
