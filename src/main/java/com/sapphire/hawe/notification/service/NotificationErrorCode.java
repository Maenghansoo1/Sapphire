package com.sapphire.hawe.notification.service;

import com.sapphire.hawe.global.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum NotificationErrorCode implements ErrorCode {
    NOTIFICATION_NOT_FOUND;

    @Override public String code() { return name(); }
    @Override public String message() { return "알림을 찾을 수 없습니다"; }
    @Override public HttpStatus httpStatus() { return HttpStatus.NOT_FOUND; }
}
