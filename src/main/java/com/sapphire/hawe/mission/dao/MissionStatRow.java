package com.sapphire.hawe.mission.dao;

/** 닫힌 미션 하나의 참여 숫자 (미션 단위 집계 — 사람·답 내용 없음). */
public class MissionStatRow {
    private String template;
    private String content;
    private long submittedCount;
    private long memberCount;
    private long engagementCount;

    public String getTemplate() { return template; }
    public void setTemplate(String template) { this.template = template; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public long getSubmittedCount() { return submittedCount; }
    public void setSubmittedCount(long submittedCount) { this.submittedCount = submittedCount; }
    public long getMemberCount() { return memberCount; }
    public void setMemberCount(long memberCount) { this.memberCount = memberCount; }
    public long getEngagementCount() { return engagementCount; }
    public void setEngagementCount(long engagementCount) { this.engagementCount = engagementCount; }
}
