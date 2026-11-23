package com.sapphire.hawe.mission.dao;

/**
 * 보상 계산에 쓰는 제출물 숫자만(본문은 가져오지 않는다). [textLength] 는 글 본문의 공백 뺀 글자 수, [fitScore] 는 사진 적합도
 * (서버 안 계산 전용), [engagement] 는 AI 마스코트를 뺀 반응+댓글 수.
 */
public class BanditSubmissionRow {
    private Long missionId;
    private Integer textLength;
    private boolean hasCaption;
    private Double fitScore;
    private long engagement;

    public Long getMissionId() { return missionId; }
    public void setMissionId(Long value) { missionId = value; }
    public Integer getTextLength() { return textLength; }
    public void setTextLength(Integer value) { textLength = value; }
    public boolean isHasCaption() { return hasCaption; }
    public void setHasCaption(boolean value) { hasCaption = value; }
    public Double getFitScore() { return fitScore; }
    public void setFitScore(Double value) { fitScore = value; }
    public long getEngagement() { return engagement; }
    public void setEngagement(long value) { engagement = value; }
}
