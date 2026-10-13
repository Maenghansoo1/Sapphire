package com.sapphire.hawe.mission.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ExperimentMapper {

    /**
     * 기간 안의 실험 미션 중 닫힌 것(마감이 지났고 현재 미션이 아님)의 참여 숫자. 대상 인원은 달력과 같은 기준
     * ({@link MissionMapper#MEMBER_COUNT}) — 미션이 열린 뒤 들어온 사람은 빼고 냈다가 나간 사람은 넣는다.
     */
    @Select("SELECT m.id AS mission_id, m.group_id, m.mission_date, m.experiment_arm, m.generation_source, "
            + MissionMapper.MEMBER_COUNT + "AS eligible, "
            + "(SELECT COUNT(*) FROM submissions s WHERE s.mission_id = m.id) AS submitted, "
            + "(SELECT COUNT(*) FROM submissions s WHERE s.mission_id = m.id AND s.is_late = 0) AS on_time, "
            + "(SELECT COUNT(*) FROM reactions r JOIN submissions s ON s.id = r.submission_id WHERE s.mission_id = m.id) "
            + "+ (SELECT COUNT(*) FROM comments c JOIN submissions s ON s.id = c.submission_id "
            + "WHERE s.mission_id = m.id) AS engagement, "
            + "(SELECT TIMESTAMPDIFF(SECOND, m.published_at, MIN(s.submitted_at)) FROM submissions s "
            + "WHERE s.mission_id = m.id) AS first_submit_seconds "
            + "FROM missions m WHERE m.experiment_arm IS NOT NULL AND m.mission_date BETWEEN #{from} AND #{to} "
            + "AND m.deadline_at < #{now} AND NOT (" + MissionMapper.CURRENT + ") "
            + "ORDER BY m.mission_date, m.group_id, m.slot_no")
    List<ExperimentMissionRow> findClosedExperimentMissions(@Param("from") LocalDate from, @Param("to") LocalDate to,
            @Param("now") LocalDateTime now);
}
