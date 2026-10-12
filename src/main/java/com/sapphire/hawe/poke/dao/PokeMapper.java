package com.sapphire.hawe.poke.dao;

import com.sapphire.hawe.poke.domain.Poke;
import com.sapphire.hawe.mission.dao.MissionMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface PokeMapper {

    @Insert("INSERT INTO pokes (from_user_id, to_user_id, group_id, mission_id, type, sent_at) "
            + "VALUES (#{fromUserId}, #{toUserId}, #{groupId}, #{missionId}, #{type}, #{sentAt})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(Poke poke);

    @Select("SELECT m.id FROM missions m WHERE m.group_id = #{groupId} AND " + MissionMapper.CURRENT
            + "AND m.slot_no = (SELECT MIN(slot_no) FROM missions c WHERE c.group_id = m.group_id) LIMIT 1")
    Long findCurrentMissionId(@Param("groupId") Long groupId);

    @Select("SELECT EXISTS (SELECT 1 FROM submissions WHERE mission_id = #{missionId} AND user_id = #{userId})")
    boolean existsSubmission(@Param("missionId") Long missionId, @Param("userId") Long userId);

    @Select("SELECT EXISTS (SELECT 1 FROM pokes WHERE from_user_id = #{fromUserId} "
            + "AND to_user_id = #{toUserId} AND mission_id = #{missionId} AND type = #{type})")
    boolean existsPoke(@Param("fromUserId") Long fromUserId, @Param("toUserId") Long toUserId,
            @Param("missionId") Long missionId, @Param("type") String type);

    @Select("SELECT to_user_id, type FROM pokes WHERE mission_id = #{missionId} AND from_user_id = #{fromUserId}")
    List<PokedRow> findSentTypes(@Param("missionId") Long missionId, @Param("fromUserId") Long fromUserId);
}
