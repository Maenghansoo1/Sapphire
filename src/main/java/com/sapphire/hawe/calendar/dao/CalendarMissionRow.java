package com.sapphire.hawe.calendar.dao;

import java.time.LocalDate;

public class CalendarMissionRow {
    private Long missionId;
    private LocalDate missionDate;
    private String template;
    private String content;
    private String targetNickname;
    private long submittedCount;
    private long memberCount;
    private boolean submittedByMe;
    private boolean lateByMe;
    private boolean memberWhenPublished;
    private boolean closed;

    public Long getMissionId() { return missionId; }
    public void setMissionId(Long missionId) { this.missionId = missionId; }
    public LocalDate getMissionDate() { return missionDate; }
    public void setMissionDate(LocalDate missionDate) { this.missionDate = missionDate; }
    public String getTemplate() { return template; }
    public void setTemplate(String template) { this.template = template; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getTargetNickname() { return targetNickname; }
    public void setTargetNickname(String targetNickname) { this.targetNickname = targetNickname; }
    public long getSubmittedCount() { return submittedCount; }
    public void setSubmittedCount(long submittedCount) { this.submittedCount = submittedCount; }
    public long getMemberCount() { return memberCount; }
    public void setMemberCount(long memberCount) { this.memberCount = memberCount; }
    public boolean isSubmittedByMe() { return submittedByMe; }
    public void setSubmittedByMe(boolean submittedByMe) { this.submittedByMe = submittedByMe; }
    public boolean isLateByMe() { return lateByMe; }
    public void setLateByMe(boolean lateByMe) { this.lateByMe = lateByMe; }
    public boolean isMemberWhenPublished() { return memberWhenPublished; }
    public void setMemberWhenPublished(boolean memberWhenPublished) { this.memberWhenPublished = memberWhenPublished; }
    public boolean isClosed() { return closed; }
    public void setClosed(boolean closed) { this.closed = closed; }
}
