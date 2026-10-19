package com.sapphire.hawe.comment.dao;

import java.util.List;
import com.sapphire.hawe.group.dao.GroupMemberMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CommentMapper {
    String FIELDS = "co.id, co.submission_id, co.user_id, "
            + GroupMemberMapper.DISPLAY_NICKNAME + ", " + GroupMemberMapper.DISPLAY_IMAGE + ", "
            + GroupMemberMapper.DISPLAY_CHARACTER + ", "
            + "co.content, co.image_url, co.ai_detail, u.is_ai AS ai, co.created_at ";
    String JOINS = "FROM comments co JOIN submissions s ON s.id = co.submission_id "
            + "JOIN missions m ON m.id = s.mission_id JOIN users u ON u.id = co.user_id "
            + "LEFT JOIN characters c ON c.id = u.character_id "
            + "LEFT JOIN group_members gm ON gm.group_id = m.group_id AND gm.user_id = co.user_id "
            + GroupMemberMapper.GROUP_CHARACTER_JOIN;

    @Insert("INSERT INTO comments (submission_id, user_id, content, image_url, ai_detail, created_at) "
            + "VALUES (#{submissionId}, #{userId}, #{content}, #{imageUrl}, #{aiDetail}, #{createdAt})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(CommentInsertRow row);

    @Insert("INSERT INTO comment_mentions (comment_id, mentioned_user_id) VALUES (#{commentId}, #{userId})")
    void insertMention(@Param("commentId") Long commentId, @Param("userId") Long userId);

    @Select("SELECT " + FIELDS + JOINS + "WHERE co.id = #{id}")
    CommentRow findDetailById(@Param("id") Long id);

    @Select("SELECT " + FIELDS + JOINS + "WHERE co.submission_id = #{submissionId} "
            + "ORDER BY co.created_at, co.id LIMIT #{limit} OFFSET #{offset}")
    List<CommentRow> findPageBySubmissionId(@Param("submissionId") Long submissionId,
            @Param("offset") long offset, @Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM comments WHERE submission_id = #{submissionId}")
    long countBySubmissionId(@Param("submissionId") Long submissionId);

    @Select("SELECT COUNT(*) > 0 FROM comments WHERE submission_id = #{submissionId} AND user_id = #{userId}")
    boolean existsBySubmissionIdAndUserId(@Param("submissionId") Long submissionId, @Param("userId") Long userId);

    @Select("<script>SELECT comment_id, mentioned_user_id FROM comment_mentions WHERE comment_id IN "
            + "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach> "
            + "ORDER BY comment_id, id</script>")
    List<CommentMentionRow> findMentionsByCommentIds(@Param("ids") List<Long> ids);

    @Select("<script>SELECT submission_id, COUNT(*) AS count FROM comments WHERE submission_id IN "
            + "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach> "
            + "GROUP BY submission_id</script>")
    List<CommentCountRow> countBySubmissionIds(@Param("ids") List<Long> ids);

    @Delete("DELETE FROM comment_mentions WHERE comment_id = #{commentId}")
    void deleteMentionsByCommentId(@Param("commentId") Long commentId);

    @Delete("DELETE FROM comments WHERE id = #{commentId}")
    void deleteById(@Param("commentId") Long commentId);
}
