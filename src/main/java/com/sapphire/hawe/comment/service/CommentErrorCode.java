package com.sapphire.hawe.comment.service;

import com.sapphire.hawe.global.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum CommentErrorCode implements ErrorCode {
    COMMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "댓글을 찾을 수 없습니다"),
    NOT_COMMENT_OWNER(HttpStatus.FORBIDDEN, "본인 댓글만 삭제할 수 있습니다");

    private final HttpStatus status;
    private final String message;
    CommentErrorCode(HttpStatus status, String message) { this.status = status; this.message = message; }
    @Override public String code() { return name(); }
    @Override public String message() { return message; }
    @Override public HttpStatus httpStatus() { return status; }
}
