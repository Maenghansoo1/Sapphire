package com.sapphire.hawe.group.dao;

import com.sapphire.hawe.group.domain.GroupMissionSlot;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface GroupMissionSlotMapper {

    @Select("SELECT group_id, slot_no, publish_time, deadline_time FROM group_mission_slots "
            + "WHERE group_id = #{groupId} ORDER BY slot_no")
    List<GroupMissionSlot> findByGroupId(@Param("groupId") Long groupId);

    @Insert("INSERT INTO group_mission_slots (group_id, slot_no, publish_time, deadline_time) "
            + "VALUES (#{groupId}, #{slotNo}, #{publishTime}, #{deadlineTime})")
    void insert(GroupMissionSlot slot);

    @Delete("DELETE FROM group_mission_slots WHERE group_id = #{groupId}")
    void deleteByGroupId(@Param("groupId") Long groupId);

    @Delete("DELETE FROM group_mission_slots WHERE group_id = #{groupId} AND slot_no > #{slotNo}")
    void deleteByGroupIdAndSlotNoGreaterThan(@Param("groupId") Long groupId, @Param("slotNo") int slotNo);
}
