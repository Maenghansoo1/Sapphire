package com.sapphire.hawe.comment.dao;

public class CommentCountRow {
    private Long submissionId;
    private int count;
    public Long getSubmissionId() { return submissionId; }
    public void setSubmissionId(Long submissionId) { this.submissionId = submissionId; }
    public int getCount() { return count; }
    public void setCount(int count) { this.count = count; }
}
