package com.sapphire.hawe.mission.dao;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface MissionMapper {

    // "미션이 열릴 때 그룹에 있었는지". 그룹을 만들면 첫 미션 발행 시각과 그룹장 가입 시각이
    // 몇 초 어긋날 수 있어서 1분 여유를 둔다.
    String JOINED_BY_PUBLISH = "gm.joined_at <= m.published_at + INTERVAL 1 MINUTE";
    // 미션의 전체 인원 = 미션이 열릴 때 있던 지금 멤버 + 그 미션에 냈지만 지금은 위에 없는 사람(나간 사람 등)
    String MEMBER_COUNT = "((SELECT COUNT(*) FROM group_members gm WHERE gm.group_id = m.group_id AND "
            + JOINED_BY_PUBLISH + ") + (SELECT COUNT(*) FROM submissions s WHERE s.mission_id = m.id AND NOT EXISTS ("
            + "SELECT 1 FROM group_members gm WHERE gm.group_id = m.group_id AND gm.user_id = s.user_id "
            + "AND " + JOINED_BY_PUBLISH + "))) ";
    String NUMBER = "(SELECT COUNT(*) FROM missions x WHERE x.group_id = m.group_id "
            + "AND (x.published_at &lt; m.published_at OR "
            + "(x.published_at = m.published_at AND x.id &lt;= m.id))) AS number ";
    // 현재 미션 = 슬롯마다 가장 최근 미션. 단, 그 슬롯이 다시 열리지 않으면 닫는다:
    // 하루 미션 수를 줄여 없어진 슬롯은 그룹의 다음 날 첫 발행에서, 남은 슬롯도 하루를 통째로 건너뛰었으면
    // (늘렸다가 다시 줄인 경우·서버 중단) 그 뒤 첫 발행에서. 하루 1개만 쓰는 그룹에서는 결과가 예전과 같다.
    String CURRENT = "m.id = (SELECT m2.id FROM missions m2 WHERE m2.group_id = m.group_id "
            + "AND m2.slot_no = m.slot_no ORDER BY m2.published_at DESC, m2.id DESC LIMIT 1) "
            + "AND NOT EXISTS (SELECT 1 FROM missions m3 WHERE m3.group_id = m.group_id "
            + "AND (m3.mission_date > m.mission_date + INTERVAL 1 DAY OR (m3.mission_date > m.mission_date "
            + "AND m.slot_no > (SELECT g3.daily_mission_count FROM `groups` g3 WHERE g3.id = m.group_id)))) ";

    @Select("<script>SELECT m.id, m.group_id, m.content, m.category, m.template, m.params, "
            + "m.slot_no, m.mission_date, m.published_at, m.deadline_at, " + NUMBER + ", "
            + "EXISTS (SELECT 1 FROM submissions s WHERE s.mission_id = m.id "
            + "AND s.user_id = #{userId}) AS submitted_by_me "
            + "FROM missions m WHERE m.group_id = #{groupId} AND " + CURRENT
            + "ORDER BY m.slot_no ASC</script>")
    List<MissionRow> findCurrentByGroupId(@Param("groupId") Long groupId, @Param("userId") Long userId);

    @Select("<script>SELECT m.id, m.group_id, m.content, m.category, m.template, m.params, "
            + "m.slot_no, m.mission_date, m.published_at, m.deadline_at, " + NUMBER + ", "
            + "EXISTS (SELECT 1 FROM submissions s WHERE s.mission_id = m.id "
            + "AND s.user_id = #{userId}) AS submitted_by_me FROM missions m WHERE m.id = #{missionId}</script>")
    MissionRow findById(@Param("missionId") Long missionId, @Param("userId") Long userId);

    @Select("<script>SELECT COUNT(*) > 0 FROM missions m WHERE m.id = #{missionId} AND " + CURRENT + "</script>")
    boolean isCurrent(@Param("missionId") Long missionId);

    @Insert("INSERT INTO missions (group_id, content, category, mission_date, template, params, "
            + "slot_no, published_at, deadline_at) VALUES (#{groupId}, #{content}, #{category}, "
            + "#{missionDate}, #{template}, #{params}, #{slotNo}, #{publishedAt}, #{deadlineAt})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(MissionInsertRow mission);

    @Select("SELECT COUNT(*) > 0 FROM missions WHERE group_id = #{groupId} "
            + "AND mission_date = #{date} AND slot_no = #{slotNo}")
    boolean existsByGroupIdAndDateAndSlot(@Param("groupId") Long groupId,
            @Param("date") LocalDate date, @Param("slotNo") int slotNo);

    @Select("SELECT template FROM missions WHERE group_id = #{groupId} AND mission_date = #{date}")
    List<String> findTemplatesByGroupIdAndDate(@Param("groupId") Long groupId, @Param("date") LocalDate date);

    @Select("SELECT content, category, template FROM missions WHERE group_id = #{groupId} "
            + "ORDER BY published_at DESC, id DESC LIMIT #{limit}")
    List<RecentMissionRow> findRecentByGroupId(@Param("groupId") Long groupId, @Param("limit") int limit);

    /** {@code since} 이후 닫힌 미션(현재 미션이 아님)의 참여 숫자, 최근 것부터. 사람·답 내용은 읽지 않는다. */
    @Select("SELECT m.template, m.content, "
            + "(SELECT COUNT(*) FROM submissions s WHERE s.mission_id = m.id) AS submitted_count, "
            + MEMBER_COUNT + "AS member_count, "
            + "(SELECT COUNT(*) FROM reactions r JOIN submissions s ON s.id = r.submission_id WHERE s.mission_id = m.id) "
            + "+ (SELECT COUNT(*) FROM comments c JOIN submissions s ON s.id = c.submission_id "
            + "WHERE s.mission_id = m.id) AS engagement_count "
            + "FROM missions m WHERE m.group_id = #{groupId} AND m.mission_date >= #{since} AND NOT (" + CURRENT + ") "
            + "ORDER BY m.published_at DESC, m.id DESC LIMIT #{limit}")
    List<MissionStatRow> findClosedStatsByGroupId(@Param("groupId") Long groupId, @Param("since") LocalDate since,
            @Param("limit") int limit);

    @Select("SELECT gm.user_id, EXISTS (SELECT 1 FROM submissions s WHERE s.mission_id = #{missionId} "
            + "AND s.user_id = gm.user_id) AS submitted FROM group_members gm "
            + "JOIN missions m ON m.group_id = gm.group_id WHERE m.id = #{missionId} "
            + "ORDER BY gm.joined_at, gm.id")
    List<MissionMemberRow> findMemberStatuses(@Param("missionId") Long missionId);

    @Select("<script>SELECT m.group_id, m.id AS mission_id, m.template, m.content, m.params, "
            + "(SELECT COUNT(*) FROM submissions s WHERE s.mission_id = m.id) AS submitted_count, "
            + "EXISTS (SELECT 1 FROM submissions s WHERE s.mission_id = m.id "
            + "AND s.user_id = #{userId}) AS submitted_by_me FROM missions m WHERE m.group_id IN "
            + "<foreach collection='groupIds' item='id' open='(' separator=',' close=')'>#{id}</foreach> "
            + "AND " + CURRENT + "ORDER BY m.group_id, m.slot_no</script>")
    List<MissionSummaryRow> findCurrentSummariesByGroupIds(@Param("groupIds") List<Long> groupIds,
            @Param("userId") Long userId);
}
