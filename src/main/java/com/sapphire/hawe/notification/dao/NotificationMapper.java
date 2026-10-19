package com.sapphire.hawe.notification.dao;

import com.sapphire.hawe.group.dao.GroupMemberMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface NotificationMapper {
    @Insert("INSERT INTO notifications (from_user_id, to_user_id, type, group_id, mission_id, submission_id, created_at) "
            + "VALUES (#{fromUserId}, #{toUserId}, #{type}, #{groupId}, #{missionId}, #{submissionId}, #{createdAt})")
    void insert(@Param("fromUserId") Long fromUserId, @Param("toUserId") Long toUserId,
            @Param("type") String type, @Param("groupId") Long groupId, @Param("missionId") Long missionId,
            @Param("submissionId") Long submissionId, @Param("createdAt") LocalDateTime createdAt);

    @Select("SELECT n.id, n.type, n.group_id, g.name AS group_name, n.from_user_id, "
            + GroupMemberMapper.DISPLAY_NICKNAME + ", " + GroupMemberMapper.DISPLAY_CHARACTER + ", "
            + GroupMemberMapper.DISPLAY_IMAGE + ", n.mission_id, n.submission_id, "
            + "n.is_read AS `read`, n.created_at FROM notifications n "
            + "JOIN `groups` g ON g.id=n.group_id "
            + "LEFT JOIN users u ON u.id=n.from_user_id "
            + "LEFT JOIN characters c ON c.id=u.character_id "
            + "LEFT JOIN group_members gm ON gm.group_id=n.group_id AND gm.user_id=n.from_user_id "
            + GroupMemberMapper.GROUP_CHARACTER_JOIN
            + "WHERE n.to_user_id=#{userId} ORDER BY n.created_at DESC, n.id DESC "
            + "LIMIT #{limit} OFFSET #{offset}")
    List<NotificationRow> findPage(@Param("userId") Long userId, @Param("offset") long offset,
            @Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM notifications n JOIN `groups` g ON g.id=n.group_id "
            + "WHERE n.to_user_id=#{userId}")
    long count(@Param("userId") Long userId);

    @Select("SELECT COUNT(*) FROM notifications n JOIN `groups` g ON g.id=n.group_id "
            + "WHERE n.to_user_id=#{userId} AND n.is_read=0")
    long countUnread(@Param("userId") Long userId);

    @Update("UPDATE notifications SET is_read=1 WHERE id=#{id} AND to_user_id=#{userId}")
    int markRead(@Param("id") Long id, @Param("userId") Long userId);

    @Select("SELECT COUNT(*) > 0 FROM notifications WHERE id=#{id} AND to_user_id=#{userId}")
    boolean existsOwned(@Param("id") Long id, @Param("userId") Long userId);

    @Update("UPDATE notifications SET is_read=1 WHERE to_user_id=#{userId} AND is_read=0")
    void markAllRead(@Param("userId") Long userId);

    @Delete("DELETE FROM notifications WHERE group_id=#{groupId}")
    void deleteByGroupId(@Param("groupId") Long groupId);

    @Delete("DELETE FROM notifications WHERE group_id=#{groupId} AND to_user_id=#{userId}")
    void deleteByGroupIdAndToUserId(@Param("groupId") Long groupId, @Param("userId") Long userId);

    @Delete("<script>DELETE FROM notifications WHERE submission_id IN "
            + "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>")
    void deleteBySubmissionIds(@Param("ids") List<Long> ids);
}
