package com.sapphire.hawe.comment.dao;

public class CommentMentionRow {
    private Long commentId, mentionedUserId;
    public Long getCommentId() { return commentId; }
    public void setCommentId(Long commentId) { this.commentId = commentId; }
    public Long getMentionedUserId() { return mentionedUserId; }
    public void setMentionedUserId(Long mentionedUserId) { this.mentionedUserId = mentionedUserId; }
}
