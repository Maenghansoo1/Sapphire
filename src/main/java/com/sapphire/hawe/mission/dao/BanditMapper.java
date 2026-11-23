package com.sapphire.hawe.mission.dao;

import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 미션 틀 선택 밴딧 통계(TODO_2/D2). 닫힘·대상 인원 기준은 B9 실험 리포트({@link ExperimentMapper})와 같다. */
@Mapper
public interface BanditMapper {

    /**
     * 보상을 아직 반영하지 않은 닫힌 미션(마감이 지났고 현재 미션이 아님 — 제출이 더 들어올 수 없는 것). 오래된 것부터 [limit] 개.
     * 대상 인원은 {@link MissionMapper#MEMBER_COUNT}: 열릴 때 그룹에 있던 사람 + 그 미션에 냈다가 나간 사람.
     */
    @Select("SELECT m.id AS mission_id, m.group_id, m.template, " + MissionMapper.MEMBER_COUNT + "AS eligible "
            + "FROM missions m WHERE m.deadline_at < #{now} AND NOT (" + MissionMapper.CURRENT + ") "
            + "AND NOT EXISTS (SELECT 1 FROM template_bandit_applied a WHERE a.mission_id = m.id) "
            + "ORDER BY m.published_at, m.id LIMIT #{limit}")
    List<BanditMissionRow> findUnappliedClosed(@Param("now") LocalDateTime now, @Param("limit") int limit);

    /** 미션들의 제출물 숫자(글자 수·캡션 여부·사진 점수·반응+댓글). 본문은 DB 밖으로 가져오지 않는다. */
    @Select("<script>SELECT s.mission_id, "
            + "CHAR_LENGTH(REGEXP_REPLACE(JSON_VALUE(s.answer_data, '$.text'), '[[:space:]]', '')) AS text_length, "
            + "(s.caption IS NOT NULL AND TRIM(s.caption) != '') AS has_caption, "
            + "(SELECT ar.fit_score FROM submission_ai_reviews ar WHERE ar.submission_id = s.id "
            + "AND ar.kind = 'TOPIC_FIT' AND ar.status = 'DONE') AS fit_score, "
            + "(SELECT COUNT(*) FROM reactions r WHERE r.submission_id = s.id "
            + "AND NOT EXISTS (SELECT 1 FROM users au WHERE au.id = r.user_id AND au.is_ai = 1)) "
            + "+ (SELECT COUNT(*) FROM comments c WHERE c.submission_id = s.id "
            + "AND NOT EXISTS (SELECT 1 FROM users au WHERE au.id = c.user_id AND au.is_ai = 1)) AS engagement "
            + "FROM submissions s WHERE s.mission_id IN "
            + "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>")
    List<BanditSubmissionRow> findSubmissions(@Param("ids") List<Long> missionIds);

    /** 반영 기록. 이미 있으면 DuplicateKeyException — 같은 미션을 두 번 반영하지 않는다. */
    @Insert("INSERT INTO template_bandit_applied (mission_id, applied_at) VALUES (#{missionId}, #{now})")
    int insertApplied(@Param("missionId") long missionId, @Param("now") LocalDateTime now);

    /** 팔 통계에 더한다(행이 없으면 만든다): α += alpha, β += beta, 미션 수 + 1. */
    @Insert("INSERT INTO template_bandit_stats (scope, group_id, template, alpha_real, beta_real, n_missions, updated_at) "
            + "VALUES (#{scope}, #{groupId}, #{template}, #{alpha}, #{beta}, 1, #{now}) "
            + "ON DUPLICATE KEY UPDATE alpha_real = alpha_real + VALUES(alpha_real), "
            + "beta_real = beta_real + VALUES(beta_real), n_missions = n_missions + 1, updated_at = VALUES(updated_at)")
    int addStats(@Param("scope") String scope, @Param("groupId") long groupId, @Param("template") String template,
            @Param("alpha") double alpha, @Param("beta") double beta, @Param("now") LocalDateTime now);

    /** 전역 통계 + 그 그룹의 통계. */
    @Select("SELECT scope, group_id, template, alpha_real, beta_real, alpha_syn, beta_syn, n_missions AS mission_count "
            + "FROM template_bandit_stats WHERE (scope = 'GLOBAL' AND group_id = 0) "
            + "OR (scope = 'GROUP' AND group_id = #{groupId})")
    List<BanditStatsRow> findStats(@Param("groupId") long groupId);
}
