package com.sapphire.hawe.submission.dao;

import java.util.List;
import com.sapphire.hawe.group.dao.GroupMemberMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SubmissionMapper {
    String FIELDS = "s.id, s.mission_id, m.group_id, s.user_id, "
            + GroupMemberMapper.DISPLAY_NICKNAME + ", " + GroupMemberMapper.DISPLAY_IMAGE + ", "
            + GroupMemberMapper.DISPLAY_CHARACTER + ", "
            + "m.template, s.media_url, s.media_type, s.caption, s.answer_data, s.number_value, s.choice_value, "
            + "s.submitted_at, s.is_late AS late ";
    String JOINS = "FROM submissions s JOIN missions m ON m.id = s.mission_id "
            + "JOIN users u ON u.id = s.user_id LEFT JOIN characters c ON c.id = u.character_id "
            + "LEFT JOIN group_members gm ON gm.group_id = m.group_id AND gm.user_id = s.user_id "
            + GroupMemberMapper.GROUP_CHARACTER_JOIN;

    @Insert("INSERT INTO submissions (mission_id, user_id, media_url, caption, submitted_at, is_late, "
            + "media_type, answer_data, number_value, choice_value) VALUES (#{missionId}, #{userId}, #{mediaUrl}, "
            + "#{caption}, #{submittedAt}, #{late}, #{mediaType}, #{answerData}, #{numberValue}, #{choiceValue})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(SubmissionInsertRow row);

    @Select("SELECT " + FIELDS + JOINS + "WHERE s.id = #{id}")
    SubmissionRow findDetailById(@Param("id") Long id);

    @Select("SELECT " + FIELDS + JOINS + "WHERE s.mission_id = #{missionId} "
            + "ORDER BY s.submitted_at ASC, s.id ASC LIMIT #{limit} OFFSET #{offset}")
    List<SubmissionRow> findFeedByMissionId(@Param("missionId") Long missionId,
            @Param("offset") long offset, @Param("limit") int limit);

    /** 집계 결과(V 유형)용 — 미션의 제출 전부, 제출 순서. 그룹 인원이 적어 한 번에 읽는다. */
    @Select("SELECT " + FIELDS + JOINS + "WHERE s.mission_id = #{missionId} ORDER BY s.submitted_at ASC, s.id ASC")
    List<SubmissionRow> findResultRowsByMissionId(@Param("missionId") Long missionId);

    @Select("SELECT COUNT(*) FROM submissions WHERE mission_id = #{missionId}")
    long countByMissionId(@Param("missionId") Long missionId);

    @Select("SELECT COUNT(*) > 0 FROM submissions WHERE mission_id = #{missionId} AND user_id = #{userId}")
    boolean existsByMissionIdAndUserId(@Param("missionId") Long missionId, @Param("userId") Long userId);
}
