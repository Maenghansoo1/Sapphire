package com.sapphire.hawe.mission.dao;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class MissionInsertRow {
    private Long id;
    private Long groupId;
    private String content;
    private String category;
    private LocalDate missionDate;
    private String template;
    private String params;
    private int slotNo;
    private LocalDateTime publishedAt;
    private LocalDateTime deadlineAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getGroupId() { return groupId; }
    public void setGroupId(Long groupId) { this.groupId = groupId; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public LocalDate getMissionDate() { return missionDate; }
    public void setMissionDate(LocalDate missionDate) { this.missionDate = missionDate; }
    public String getTemplate() { return template; }
    public void setTemplate(String template) { this.template = template; }
    public String getParams() { return params; }
    public void setParams(String params) { this.params = params; }
    public int getSlotNo() { return slotNo; }
    public void setSlotNo(int slotNo) { this.slotNo = slotNo; }
    public LocalDateTime getPublishedAt() { return publishedAt; }
    public void setPublishedAt(LocalDateTime publishedAt) { this.publishedAt = publishedAt; }
    public LocalDateTime getDeadlineAt() { return deadlineAt; }
    public void setDeadlineAt(LocalDateTime deadlineAt) { this.deadlineAt = deadlineAt; }
}
