package com.sapphire.hawe.mission.dao;

/** 틀(팔) 하나의 Beta 통계 행. 전역 행은 scope=GLOBAL, groupId=0. */
public class BanditStatsRow {
    private String scope;
    private Long groupId;
    private String template;
    private double alphaReal;
    private double betaReal;
    private double alphaSyn;
    private double betaSyn;
    private int missionCount;

    public String getScope() { return scope; }
    public void setScope(String value) { scope = value; }
    public Long getGroupId() { return groupId; }
    public void setGroupId(Long value) { groupId = value; }
    public String getTemplate() { return template; }
    public void setTemplate(String value) { template = value; }
    public double getAlphaReal() { return alphaReal; }
    public void setAlphaReal(double value) { alphaReal = value; }
    public double getBetaReal() { return betaReal; }
    public void setBetaReal(double value) { betaReal = value; }
    public double getAlphaSyn() { return alphaSyn; }
    public void setAlphaSyn(double value) { alphaSyn = value; }
    public double getBetaSyn() { return betaSyn; }
    public void setBetaSyn(double value) { betaSyn = value; }
    public int getMissionCount() { return missionCount; }
    public void setMissionCount(int value) { missionCount = value; }
}
