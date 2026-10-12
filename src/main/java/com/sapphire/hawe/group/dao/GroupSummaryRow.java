package com.sapphire.hawe.group.dao;

import java.time.LocalDate;

/** Read-model projection for {@code GET /groups}; not a table-backed entity. */
public class GroupSummaryRow {

    private Long id;
    private String name;
    private String relationType;
    private LocalDate startDate;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getRelationType() {
        return relationType;
    }

    public void setRelationType(String relationType) {
        this.relationType = relationType;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

}
