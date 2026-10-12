package com.sapphire.hawe.mission.dao;

public class MissionMemberRow {
    private Long userId;
    private boolean submitted;

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public boolean isSubmitted() { return submitted; }
    public void setSubmitted(boolean submitted) { this.submitted = submitted; }
}
