package com.sapphire.hawe.search.dao;

import java.time.LocalDate;

/** 검색 후보 한 줄. 서버·AI 가 만든 미션 값만 담는다(제출물·댓글 없음). */
public class MissionSearchRow {
    private Long id;
    private Long groupId;
    private String groupName;
    private String content;
    private String template;
    private String params;
    private LocalDate missionDate;
    private boolean submitted;

    public Long getId() { return id; }
    public void setId(Long value) { id = value; }
    public Long getGroupId() { return groupId; }
    public void setGroupId(Long value) { groupId = value; }
    public String getGroupName() { return groupName; }
    public void setGroupName(String value) { groupName = value; }
    public String getContent() { return content; }
    public void setContent(String value) { content = value; }
    public String getTemplate() { return template; }
    public void setTemplate(String value) { template = value; }
    public String getParams() { return params; }
    public void setParams(String value) { params = value; }
    public LocalDate getMissionDate() { return missionDate; }
    public void setMissionDate(LocalDate value) { missionDate = value; }
    public boolean isSubmitted() { return submitted; }
    public void setSubmitted(boolean value) { submitted = value; }
}
