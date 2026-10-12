package com.sapphire.hawe.submission.service;

import com.sapphire.hawe.global.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum SubmissionErrorCode implements ErrorCode {
    ALREADY_SUBMITTED(HttpStatus.CONFLICT, "이미 제출했습니다"),
    FEED_LOCKED(HttpStatus.LOCKED, "제출 후 볼 수 있습니다"),
    SUBMISSION_NOT_FOUND(HttpStatus.NOT_FOUND, "제출물을 찾을 수 없습니다"),
    INVALID_SUBMISSION(HttpStatus.BAD_REQUEST, "제출 내용이 올바르지 않습니다"),
    UNSUPPORTED_FILE_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "지원하지 않는 파일 형식입니다");

    private final HttpStatus status;
    private final String message;
    SubmissionErrorCode(HttpStatus status, String message) { this.status = status; this.message = message; }
    public String code() { return name(); }
    public String message() { return message; }
    public HttpStatus httpStatus() { return status; }
}
