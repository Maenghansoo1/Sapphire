package com.sapphire.hawe.poke.dao;

import com.sapphire.hawe.poke.domain.Poke;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface PokeMapper {

    @Insert("INSERT INTO pokes (from_user_id, to_user_id, group_id, type, sent_at) "
            + "VALUES (#{fromUserId}, #{toUserId}, #{groupId}, #{type}, #{sentAt})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(Poke poke);

    @Select("SELECT EXISTS (SELECT 1 FROM submissions s JOIN missions m ON m.id = s.mission_id "
            + "WHERE s.user_id = #{userId} AND m.group_id = #{groupId} "
            + "AND m.id = (SELECT m2.id FROM missions m2 WHERE m2.group_id = m.group_id "
            + "AND m2.slot_no = m.slot_no ORDER BY m2.published_at DESC, m2.id DESC LIMIT 1))")
    boolean existsCurrentMissionSubmission(@Param("groupId") Long groupId, @Param("userId") Long userId);
}
