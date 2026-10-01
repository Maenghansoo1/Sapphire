package com.sapphire.hawe.group.service;

import com.sapphire.hawe.global.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum GroupErrorCode implements ErrorCode {

    GROUP_NOT_FOUND(HttpStatus.NOT_FOUND, "그룹을 찾을 수 없습니다"),
    NOT_GROUP_MEMBER(HttpStatus.FORBIDDEN, "그룹 멤버가 아닙니다"),
    NOT_GROUP_OWNER(HttpStatus.FORBIDDEN, "그룹장만 할 수 있습니다"),
    INVALID_INVITE_TOKEN(HttpStatus.BAD_REQUEST, "유효하지 않은 초대 링크입니다"),
    INVITE_EXPIRED(HttpStatus.BAD_REQUEST, "만료된 초대 링크입니다"),
    ALREADY_MEMBER(HttpStatus.CONFLICT, "이미 가입된 그룹입니다"),
    OWNER_CANNOT_LEAVE(HttpStatus.CONFLICT, "그룹장은 그룹을 나갈 수 없습니다");

    private final HttpStatus httpStatus;
    private final String message;

    GroupErrorCode(HttpStatus httpStatus, String message) {
        this.httpStatus = httpStatus;
        this.message = message;
    }

    @Override
    public String code() {
        return name();
    }

    @Override
    public String message() {
        return message;
    }

    @Override
    public HttpStatus httpStatus() {
        return httpStatus;
    }
}
