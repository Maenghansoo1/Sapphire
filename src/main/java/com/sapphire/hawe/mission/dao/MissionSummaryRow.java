package com.sapphire.hawe.mission.dao;

public class MissionSummaryRow {
    private Long groupId;
    private Long missionId;
    private String template;
    private String content;
    private String params;
    private long submittedCount;
    private boolean submittedByMe;

    public Long getGroupId() { return groupId; }
    public void setGroupId(Long groupId) { this.groupId = groupId; }
    public Long getMissionId() { return missionId; }
    public void setMissionId(Long missionId) { this.missionId = missionId; }
    public String getTemplate() { return template; }
    public void setTemplate(String template) { this.template = template; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getParams() { return params; }
    public void setParams(String params) { this.params = params; }
    public long getSubmittedCount() { return submittedCount; }
    public void setSubmittedCount(long submittedCount) { this.submittedCount = submittedCount; }
    public boolean isSubmittedByMe() { return submittedByMe; }
    public void setSubmittedByMe(boolean submittedByMe) { this.submittedByMe = submittedByMe; }
}
