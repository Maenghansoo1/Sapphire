package com.sapphire.hawe.group.dao;

/** Read-model projection for {@code GET /groups}; not a table-backed entity. */
public class GroupSummaryRow {

    private Long id;
    private String name;
    private boolean submittedToday;

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

    public boolean isSubmittedToday() {
        return submittedToday;
    }

    public void setSubmittedToday(boolean submittedToday) {
        this.submittedToday = submittedToday;
    }
}
