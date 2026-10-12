package com.sapphire.hawe.reaction.dao;

import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ReactionMapper {
    @Delete("DELETE FROM reactions WHERE submission_id = #{submissionId} AND user_id = #{userId} "
            + "AND sticker_type = #{stickerType}")
    int delete(@Param("submissionId") Long submissionId, @Param("userId") Long userId,
            @Param("stickerType") String stickerType);

    @Insert("INSERT INTO reactions (submission_id, user_id, sticker_type) "
            + "VALUES (#{submissionId}, #{userId}, #{stickerType})")
    void insert(@Param("submissionId") Long submissionId, @Param("userId") Long userId,
            @Param("stickerType") String stickerType);

    @Select("SELECT submission_id, user_id, sticker_type FROM reactions "
            + "WHERE submission_id = #{submissionId} ORDER BY id")
    List<ReactionRow> findBySubmissionId(@Param("submissionId") Long submissionId);

    @Select("<script>SELECT submission_id, user_id, sticker_type FROM reactions WHERE submission_id IN "
            + "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach> "
            + "ORDER BY submission_id, id</script>")
    List<ReactionRow> findBySubmissionIds(@Param("ids") List<Long> ids);
}
