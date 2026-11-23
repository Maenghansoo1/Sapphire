package com.sapphire.hawe.mission.dao;

import java.time.LocalDate;

/** 그룹의 최근 미션 날짜와 틀(반복 패널티·점유율 상한용). */
public class BanditRecentRow {
    private LocalDate missionDate;
    private String template;

    public LocalDate getMissionDate() { return missionDate; }
    public void setMissionDate(LocalDate value) { missionDate = value; }
    public String getTemplate() { return template; }
    public void setTemplate(String value) { template = value; }
}
