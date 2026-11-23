package com.sapphire.hawe.badge.dao;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.sapphire.hawe.badge.domain.BadgeStatus;
import com.sapphire.hawe.badge.domain.UserBadge;

@Mapper
public interface BadgeMapper {
	String BADGE_COLUMNS = "id, user_id, badge_code, status, earned_at, token_id, tx_hash, chain_id, confirmed_at, created_at";

	@Insert("INSERT IGNORE INTO user_badges (user_id, badge_code, status, earned_at) "
			+ "VALUES (#{userId}, #{code}, #{status}, #{earnedAt})")
	int insertIgnore(@Param("userId") Long userId, @Param("code") String code,
			@Param("status") BadgeStatus status, @Param("earnedAt") LocalDateTime earnedAt);

	@ConstructorArgs({
		@Arg(column = "id", javaType = Long.class),
		@Arg(column = "user_id", javaType = Long.class),
		@Arg(column = "badge_code", javaType = String.class),
		@Arg(column = "status", javaType = BadgeStatus.class),
		@Arg(column = "earned_at", javaType = LocalDateTime.class),
		@Arg(column = "token_id", javaType = String.class),
		@Arg(column = "tx_hash", javaType = String.class),
		@Arg(column = "chain_id", javaType = Integer.class),
		@Arg(column = "confirmed_at", javaType = LocalDateTime.class),
		@Arg(column = "created_at", javaType = LocalDateTime.class)
	})
	@Select("SELECT " + BADGE_COLUMNS + " FROM user_badges WHERE user_id = #{userId} ORDER BY earned_at DESC, id DESC")
	List<UserBadge> findByUserId(@Param("userId") Long userId);

	@ConstructorArgs({
		@Arg(column = "id", javaType = Long.class),
		@Arg(column = "user_id", javaType = Long.class),
		@Arg(column = "badge_code", javaType = String.class),
		@Arg(column = "status", javaType = BadgeStatus.class),
		@Arg(column = "earned_at", javaType = LocalDateTime.class),
		@Arg(column = "token_id", javaType = String.class),
		@Arg(column = "tx_hash", javaType = String.class),
		@Arg(column = "chain_id", javaType = Integer.class),
		@Arg(column = "confirmed_at", javaType = LocalDateTime.class),
		@Arg(column = "created_at", javaType = LocalDateTime.class)
	})
	@Select("SELECT " + BADGE_COLUMNS + " FROM user_badges WHERE user_id = #{userId} AND badge_code = #{code}")
	UserBadge findByUserIdAndCode(@Param("userId") Long userId, @Param("code") String code);

	@Select("SELECT COUNT(*) FROM user_badges WHERE user_id = #{userId}")
	int countByUser(@Param("userId") Long userId);

	@Update("UPDATE users SET featured_badge_code = #{code} WHERE id = #{userId}")
	int updateFeaturedBadge(@Param("userId") Long userId, @Param("code") String codeOrNull);

	@Select("SELECT featured_badge_code FROM users WHERE id = #{userId}")
	String findFeaturedBadgeCode(@Param("userId") Long userId);

	@ConstructorArgs({
		@Arg(column = "user_id", javaType = Long.class),
		@Arg(column = "featured_badge_code", javaType = String.class),
		@Arg(column = "badge_code", javaType = String.class),
		@Arg(column = "earned_at", javaType = LocalDateTime.class)
	})
	@Select("<script>SELECT u.id AS user_id, u.featured_badge_code, b.badge_code, b.earned_at "
			+ "FROM users u LEFT JOIN user_badges b ON b.user_id = u.id "
			+ "WHERE <choose><when test='userIds != null and userIds.size() > 0'>"
			+ "u.id IN <foreach collection='userIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>"
			+ "</when><otherwise>1 = 0</otherwise></choose> "
			+ "ORDER BY u.id, b.earned_at DESC, b.id DESC</script>")
	List<FeaturedBadgeRow> findFeaturedBadgeRowsByUserIds(@Param("userIds") List<Long> userIds);
}
