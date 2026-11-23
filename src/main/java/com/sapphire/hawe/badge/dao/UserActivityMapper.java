package com.sapphire.hawe.badge.dao;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.sapphire.hawe.badge.domain.ValidSubmission;

@Mapper
public interface UserActivityMapper {
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
}
