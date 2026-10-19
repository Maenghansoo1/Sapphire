package com.sapphire.hawe.mascot.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface MascotMapper {
    @Select("SELECT id FROM users WHERE is_ai = 1 ORDER BY id LIMIT 1")
    Long findUserId();
}
