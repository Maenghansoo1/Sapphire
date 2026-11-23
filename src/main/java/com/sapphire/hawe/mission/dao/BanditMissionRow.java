package com.sapphire.hawe.mission.dao;

/** 보상을 아직 반영하지 않은 닫힌 미션. [eligible] 은 대상 인원(달력·B9 실험과 같은 기준). */
public class BanditMissionRow {
    private Long missionId;
    private Long groupId;
    private String template;
    private long eligible;

    public Long getMissionId() { return missionId; }
    public void setMissionId(Long value) { missionId = value; }
    public Long getGroupId() { return groupId; }
    public void setGroupId(Long value) { groupId = value; }
    public String getTemplate() { return template; }
    public void setTemplate(String value) { template = value; }
    public long getEligible() { return eligible; }
    public void setEligible(long value) { eligible = value; }
}
