package com.sapphire.hawe.mission.dao;

import java.time.LocalDate;

/** 실험 리포트용 닫힌 미션 하나의 숫자 (사람을 알아볼 정보 없음). */
public class ExperimentMissionRow {
    private Long missionId;
    private Long groupId;
    private LocalDate missionDate;
    private String experimentArm;
    private String generationSource;
    private long eligible;
    private long submitted;
    private long onTime;
    private long engagement;
    private Long firstSubmitSeconds;

    public Long getMissionId() { return missionId; }
    public void setMissionId(Long missionId) { this.missionId = missionId; }
    public Long getGroupId() { return groupId; }
    public void setGroupId(Long groupId) { this.groupId = groupId; }
    public LocalDate getMissionDate() { return missionDate; }
    public void setMissionDate(LocalDate missionDate) { this.missionDate = missionDate; }
    public String getExperimentArm() { return experimentArm; }
    public void setExperimentArm(String experimentArm) { this.experimentArm = experimentArm; }
    public String getGenerationSource() { return generationSource; }
    public void setGenerationSource(String generationSource) { this.generationSource = generationSource; }
    public long getEligible() { return eligible; }
    public void setEligible(long eligible) { this.eligible = eligible; }
    public long getSubmitted() { return submitted; }
    public void setSubmitted(long submitted) { this.submitted = submitted; }
    public long getOnTime() { return onTime; }
    public void setOnTime(long onTime) { this.onTime = onTime; }
    public long getEngagement() { return engagement; }
    public void setEngagement(long engagement) { this.engagement = engagement; }
    public Long getFirstSubmitSeconds() { return firstSubmitSeconds; }
    public void setFirstSubmitSeconds(Long firstSubmitSeconds) { this.firstSubmitSeconds = firstSubmitSeconds; }
}
