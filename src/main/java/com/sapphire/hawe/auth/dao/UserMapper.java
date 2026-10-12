package com.sapphire.hawe.auth.dao;

import com.sapphire.hawe.auth.domain.User;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserMapper {

    @Select("SELECT id, email, password_hash, nickname, character_id, terms_agreed_at, privacy_agreed_at, created_at "
            + "FROM users WHERE email = #{email}")
    User findByEmail(@Param("email") String email);

    @Select("SELECT id, email, password_hash, nickname, character_id, terms_agreed_at, privacy_agreed_at, created_at "
            + "FROM users WHERE id = #{id}")
    User findById(@Param("id") Long id);

    @Select("SELECT COUNT(*) > 0 FROM users WHERE email = #{email}")
    boolean existsByEmail(@Param("email") String email);

    @Insert("INSERT INTO users (email, password_hash, nickname, terms_agreed_at, privacy_agreed_at) "
            + "VALUES (#{email}, #{passwordHash}, #{nickname}, #{termsAgreedAt}, #{privacyAgreedAt})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(User user);
}
