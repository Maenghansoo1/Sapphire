package com.sapphire.hawe.submission.dao;

import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SubmissionMapper {
    String FIELDS = "s.id, s.mission_id, m.group_id, s.user_id, u.nickname, u.profile_image_url, c.svg_key AS character_key, "
            + "m.template, s.media_url, s.media_type, s.caption, s.answer_data, s.submitted_at, s.is_late AS late ";
    String JOINS = "FROM submissions s JOIN missions m ON m.id = s.mission_id "
            + "JOIN users u ON u.id = s.user_id LEFT JOIN characters c ON c.id = u.character_id ";

    @Insert("INSERT INTO submissions (mission_id, user_id, media_url, caption, submitted_at, is_late, "
            + "media_type, answer_data) VALUES (#{missionId}, #{userId}, #{mediaUrl}, #{caption}, "
            + "#{submittedAt}, #{late}, #{mediaType}, #{answerData})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(SubmissionInsertRow row);

    @Select("SELECT " + FIELDS + JOINS + "WHERE s.id = #{id}")
    SubmissionRow findDetailById(@Param("id") Long id);

    @Select("SELECT " + FIELDS + JOINS + "WHERE s.mission_id = #{missionId} "
            + "ORDER BY s.submitted_at ASC, s.id ASC LIMIT #{limit} OFFSET #{offset}")
    List<SubmissionRow> findFeedByMissionId(@Param("missionId") Long missionId,
            @Param("offset") long offset, @Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM submissions WHERE mission_id = #{missionId}")
    long countByMissionId(@Param("missionId") Long missionId);

    @Select("SELECT COUNT(*) > 0 FROM submissions WHERE mission_id = #{missionId} AND user_id = #{userId}")
    boolean existsByMissionIdAndUserId(@Param("missionId") Long missionId, @Param("userId") Long userId);
}
