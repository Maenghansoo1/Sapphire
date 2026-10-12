package com.sapphire.hawe.mission.dao;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Projection for published mission reads. */
public class MissionRow {
    private Long id;
    private Long groupId;
    private String content;
    private String category;
    private String template;
    private String params;
    private int slotNo;
    private LocalDate missionDate;
    private int number;
    private LocalDateTime publishedAt;
    private LocalDateTime deadlineAt;
    private boolean submittedByMe;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getGroupId() {
        return groupId;
    }

    public void setGroupId(Long groupId) {
        this.groupId = groupId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getTemplate() {
        return template;
    }

    public void setTemplate(String template) {
        this.template = template;
    }

    public String getParams() {
        return params;
    }

    public void setParams(String params) {
        this.params = params;
    }

    public int getSlotNo() {
        return slotNo;
    }

    public void setSlotNo(int slotNo) {
        this.slotNo = slotNo;
    }

    public LocalDate getMissionDate() { return missionDate; }
    public void setMissionDate(LocalDate missionDate) { this.missionDate = missionDate; }
    public int getNumber() { return number; }
    public void setNumber(int number) { this.number = number; }

    public LocalDateTime getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(LocalDateTime publishedAt) {
        this.publishedAt = publishedAt;
    }

    public LocalDateTime getDeadlineAt() {
        return deadlineAt;
    }

    public void setDeadlineAt(LocalDateTime deadlineAt) {
        this.deadlineAt = deadlineAt;
    }

    public boolean isSubmittedByMe() {
        return submittedByMe;
    }

    public void setSubmittedByMe(boolean submittedByMe) {
        this.submittedByMe = submittedByMe;
    }
}
