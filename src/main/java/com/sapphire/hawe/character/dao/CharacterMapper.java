package com.sapphire.hawe.character.dao;

import com.sapphire.hawe.character.domain.ProfileCharacter;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface CharacterMapper {

    @Select("SELECT id, name, svg_key FROM characters ORDER BY id ASC LIMIT #{limit} OFFSET #{offset}")
    List<ProfileCharacter> findAll(@Param("offset") long offset, @Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM characters")
    long count();

    @Select("SELECT id, name, svg_key FROM characters WHERE id = #{id}")
    ProfileCharacter findById(@Param("id") Long id);

    // users 테이블이지만 캐릭터 선택 기능에서만 쓰여서 여기에 둔다.
    @Update("UPDATE users SET character_id = #{characterId} WHERE id = #{userId}")
    int updateUserCharacterId(@Param("userId") Long userId, @Param("characterId") Long characterId);
}
