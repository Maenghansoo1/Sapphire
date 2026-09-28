package com.sapphire.hawe.auth.dao;

import com.sapphire.hawe.auth.domain.User;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserMapper {

    @Select("SELECT id, nickname, login_id, password_hash, email, character_id, created_at "
            + "FROM users WHERE login_id = #{loginId}")
    User findByLoginId(@Param("loginId") String loginId);

    @Select("SELECT id, nickname, login_id, password_hash, email, character_id, created_at "
            + "FROM users WHERE id = #{id}")
    User findById(@Param("id") Long id);

    @Select("SELECT COUNT(*) > 0 FROM users WHERE login_id = #{loginId}")
    boolean existsByLoginId(@Param("loginId") String loginId);

    @Insert("INSERT INTO users (nickname, login_id, password_hash, email) "
            + "VALUES (#{nickname}, #{loginId}, #{passwordHash}, #{email})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(User user);
}
