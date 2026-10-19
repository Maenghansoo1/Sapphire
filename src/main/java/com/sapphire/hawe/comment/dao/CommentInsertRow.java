package com.sapphire.hawe.comment.dao;

import java.time.LocalDateTime;

public class CommentInsertRow {
    private Long id, submissionId, userId;
    private String content, imageUrl, aiDetail;
    private LocalDateTime createdAt;
    public CommentInsertRow(Long submissionId, Long userId, String content, String imageUrl, LocalDateTime createdAt) {
        this.submissionId = submissionId; this.userId = userId; this.content = content;
        this.imageUrl = imageUrl; this.createdAt = createdAt;
    }
    public CommentInsertRow(Long submissionId, Long userId, String content, String imageUrl, String aiDetail, LocalDateTime createdAt) {
        this(submissionId, userId, content, imageUrl, createdAt);
        this.aiDetail = aiDetail;
    }
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getSubmissionId() { return submissionId; }
    public Long getUserId() { return userId; }
    public String getContent() { return content; }
    public String getImageUrl() { return imageUrl; }
    public String getAiDetail() { return aiDetail; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
