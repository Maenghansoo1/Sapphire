package com.sapphire.hawe.mission.dao;

import com.sapphire.hawe.mission.domain.RecentMission;

public class RecentMissionRow {
    private String content;
    private String category;
    private String template;

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getTemplate() { return template; }
    public void setTemplate(String template) { this.template = template; }

    public RecentMission toDomain() {
        return new RecentMission(content, category, template);
    }
}
