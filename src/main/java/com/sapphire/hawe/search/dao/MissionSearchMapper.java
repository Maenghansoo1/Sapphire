package com.sapphire.hawe.search.dao;

import com.sapphire.hawe.mission.dao.MissionMapper;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface MissionSearchMapper {
    /**
     * 검색해도 되는 미션 후보(권한 필터는 여기 SQL 에서 보장한다):
     * 내가 <b>지금 멤버인</b> 그룹의 미션 중 (닫힌 미션 OR 내가 낸 미션). 지금 열려 있고 내가 아직 안 낸 미션은 빠진다.
     * "닫힘"은 기존 규칙과 같다 — 같은 슬롯에 새 미션이 열려 더 이상 현재 미션이 아닌 것({@link MissionMapper#CURRENT}).
     * 그룹을 나가면 멤버 JOIN 에서 빠져 그 그룹 미션은 나오지 않는다. 최신 순으로 [max] 개까지.
     */
    @Select("<script>SELECT m.id, m.group_id, g.name AS group_name, m.content, m.template, m.params, m.mission_date, "
            + "EXISTS (SELECT 1 FROM submissions s WHERE s.mission_id = m.id AND s.user_id = #{userId}) AS submitted "
            + "FROM missions m "
            + "JOIN group_members gm ON gm.group_id = m.group_id AND gm.user_id = #{userId} "
            + "JOIN `groups` g ON g.id = m.group_id "
            + "WHERE (EXISTS (SELECT 1 FROM submissions s WHERE s.mission_id = m.id AND s.user_id = #{userId}) "
            + "OR NOT (" + MissionMapper.CURRENT + ")) "
            + "<if test='groupId != null'>AND m.group_id = #{groupId} </if>"
            + "ORDER BY m.mission_date DESC, m.id DESC LIMIT #{max}</script>")
    List<MissionSearchRow> findSearchable(@Param("userId") Long userId, @Param("groupId") Long groupId,
            @Param("max") int max);
}
