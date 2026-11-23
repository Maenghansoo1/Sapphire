package com.sapphire.hawe.badge.dao;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.sapphire.hawe.badge.domain.ValidSubmission;
import com.sapphire.hawe.badge.domain.DaySubmission;
import com.sapphire.hawe.badge.domain.GroupBase;
import com.sapphire.hawe.badge.domain.PerfectMonthRow;
import com.sapphire.hawe.mission.dao.MissionMapper;

@Mapper
public interface UserActivityMapper {
	@Select("SELECT id FROM users WHERE id > #{afterId} AND is_ai = 0 ORDER BY id LIMIT #{limit}")
	List<Long> findUserIdsAfter(@Param("afterId") long afterId, @Param("limit") int limit);

	String VALID_GROUP = "(SELECT COUNT(*) FROM group_members gm WHERE gm.group_id = m.group_id) >= 2";
	String BASE_DATE = "CASE WHEN g.relation_type = 'COUPLE' AND g.start_date IS NOT NULL "
			+ "THEN g.start_date ELSE DATE(g.created_at) END";
	@ConstructorArgs({
		@Arg(column = "submission_id", javaType = Long.class),
		@Arg(column = "submitted_at", javaType = LocalDateTime.class),
		@Arg(column = "is_late", javaType = boolean.class),
		@Arg(column = "published_at", javaType = LocalDateTime.class),
		@Arg(column = "template", javaType = String.class)
	})
	@Select("SELECT s.id AS submission_id, s.submitted_at, s.is_late, m.published_at, m.template "
			+ "FROM submissions s JOIN missions m ON m.id = s.mission_id "
			+ "WHERE s.user_id = #{userId} "
			+ "AND (SELECT COUNT(*) FROM group_members gm WHERE gm.group_id = m.group_id) >= 2 "
			+ "ORDER BY s.submitted_at, s.id")
	List<ValidSubmission> findValidSubmissionsByUserId(@Param("userId") Long userId);

	@ConstructorArgs({
		@Arg(column = "mission_date", javaType = LocalDate.class),
		@Arg(column = "first_submitted_at", javaType = LocalDateTime.class)
	})
	@Select("SELECT m.mission_date, MIN(s.submitted_at) AS first_submitted_at "
			+ "FROM submissions s JOIN missions m ON m.id = s.mission_id "
			+ "WHERE s.user_id = #{userId} AND s.is_late = 0 AND " + VALID_GROUP
			+ " GROUP BY m.mission_date ORDER BY m.mission_date")
	List<DaySubmission> findOnTimeSubmissionDays(@Param("userId") Long userId);

	@Select("SELECT ar.updated_at FROM submission_ai_reviews ar "
			+ "JOIN submissions s ON s.id = ar.submission_id JOIN missions m ON m.id = s.mission_id "
			+ "WHERE s.user_id = #{userId} AND ar.kind = 'TOPIC_FIT' AND ar.status = 'DONE' "
			+ "AND ar.badge = 'PERFECT_FIT' AND " + VALID_GROUP + " ORDER BY ar.updated_at, ar.id")
	List<LocalDateTime> findPerfectFitTimes(@Param("userId") Long userId);

	@Select("SELECT COALESCE(MAX((SELECT COUNT(*) FROM reactions r JOIN users ru ON ru.id = r.user_id "
			+ "WHERE r.submission_id = s.id AND r.user_id != s.user_id AND ru.is_ai = 0) "
			+ "+ (SELECT COUNT(*) FROM comments c JOIN users cu ON cu.id = c.user_id "
			+ "WHERE c.submission_id = s.id AND c.user_id != s.user_id AND cu.is_ai = 0)), 0) "
			+ "FROM submissions s JOIN missions m ON m.id = s.mission_id "
			+ "WHERE s.user_id = #{userId} AND " + VALID_GROUP)
	int findCrowdMaxInteractions(@Param("userId") Long userId);

	@ConstructorArgs({
		@Arg(column = "group_id", javaType = Long.class),
		@Arg(column = "base", javaType = LocalDate.class),
		@Arg(column = "joined_on", javaType = LocalDate.class),
		@Arg(column = "couple_start_date", javaType = boolean.class)
	})
	@Select("SELECT g.id AS group_id, " + BASE_DATE + " AS base, DATE(gm.joined_at) AS joined_on, "
			+ "(g.relation_type = 'COUPLE' AND g.start_date IS NOT NULL) AS couple_start_date FROM `groups` g "
			+ "JOIN group_members gm ON gm.group_id = g.id AND gm.user_id = #{userId} "
			+ "WHERE (SELECT COUNT(*) FROM group_members gm2 WHERE gm2.group_id = g.id) >= 2")
	List<GroupBase> findMemberGroupBases(@Param("userId") Long userId);

	@ConstructorArgs({
		@Arg(column = "group_id", javaType = Long.class),
		@Arg(column = "total", javaType = int.class),
		@Arg(column = "submitted", javaType = int.class),
		@Arg(column = "open_count", javaType = int.class),
		@Arg(column = "member_count", javaType = int.class)
	})
	@Select("SELECT m.group_id, COUNT(*) AS total, "
			+ "SUM(CASE WHEN s.id IS NOT NULL THEN 1 ELSE 0 END) AS submitted, "
			+ "SUM(CASE WHEN (" + MissionMapper.CURRENT + ") THEN 1 ELSE 0 END) AS open_count, "
			+ "(SELECT COUNT(*) FROM group_members gm2 WHERE gm2.group_id = m.group_id) AS member_count "
			+ "FROM missions m LEFT JOIN submissions s ON s.mission_id = m.id AND s.user_id = #{userId} "
			+ "WHERE m.group_id IN (SELECT gm.group_id FROM group_members gm WHERE gm.user_id = #{userId}) "
			+ "AND m.mission_date BETWEEN #{from} AND #{to} GROUP BY m.group_id")
	List<PerfectMonthRow> findPerfectMonthRows(@Param("userId") Long userId,
			@Param("from") LocalDate from, @Param("to") LocalDate to);

	@Select("SELECT DISTINCT user_id FROM submissions WHERE submitted_at >= #{since} ORDER BY user_id")
	List<Long> findRecentSubmitterIds(@Param("since") LocalDateTime since);

	@Select("SELECT DISTINCT gm.user_id FROM `groups` g JOIN group_members gm ON gm.group_id = g.id "
			+ "WHERE (SELECT COUNT(*) FROM group_members gm2 WHERE gm2.group_id = g.id) >= 2 "
			+ "AND (DATE_ADD((" + BASE_DATE + "), INTERVAL 99 DAY) BETWEEN #{from} AND #{to} "
			+ "OR DATE_ADD((" + BASE_DATE + "), INTERVAL 1 YEAR) BETWEEN #{from} AND #{to}) "
			+ "ORDER BY gm.user_id")
	List<Long> findMilestoneUserIds(@Param("from") LocalDate from, @Param("to") LocalDate to);

	@Select("SELECT DISTINCT s.user_id FROM submissions s JOIN missions m ON m.id = s.mission_id "
			+ "WHERE m.mission_date BETWEEN #{from} AND #{to} ORDER BY s.user_id")
	List<Long> findSubmitterIdsInRange(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
