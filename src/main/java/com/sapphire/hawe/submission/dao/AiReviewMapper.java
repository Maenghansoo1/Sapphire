package com.sapphire.hawe.submission.dao;

import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface AiReviewMapper {
    @Insert("INSERT IGNORE INTO submission_ai_reviews (submission_id, kind, status, attempts) "
            + "VALUES (#{submissionId}, 'TOPIC_FIT', 'PENDING', 0)")
    int insertPendingIfAbsent(@Param("submissionId") Long submissionId);

    @Update("UPDATE submission_ai_reviews SET status=#{status}, fit_score=#{fitScore}, badge=#{badge}, "
            + "comment=#{comment}, model_version=#{modelVersion}, attempts=#{attempts} "
            + "WHERE submission_id=#{submissionId} AND kind='TOPIC_FIT'")
    int complete(@Param("submissionId") Long submissionId, @Param("status") String status,
            @Param("fitScore") Double fitScore, @Param("badge") String badge,
            @Param("comment") String comment, @Param("modelVersion") String modelVersion,
            @Param("attempts") int attempts);

    @Select("<script>SELECT submission_id, status, fit_score, badge, comment, model_version, attempts "
            + "FROM submission_ai_reviews WHERE kind='TOPIC_FIT' AND status='DONE' AND submission_id IN "
            + "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>")
    List<AiReviewRow> findBySubmissionIds(@Param("ids") List<Long> ids);

    @Select("SELECT submission_id, status, fit_score, badge, comment, model_version, attempts "
            + "FROM submission_ai_reviews WHERE submission_id=#{submissionId} AND kind='TOPIC_FIT'")
    AiReviewRow findBySubmissionId(@Param("submissionId") Long submissionId);
}
