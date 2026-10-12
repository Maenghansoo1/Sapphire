package com.sapphire.hawe.group.service;

import com.sapphire.hawe.global.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum GroupErrorCode implements ErrorCode {

    GROUP_NOT_FOUND(HttpStatus.NOT_FOUND, "그룹을 찾을 수 없습니다"),
    NOT_GROUP_MEMBER(HttpStatus.FORBIDDEN, "그룹 멤버가 아닙니다"),
    NOT_GROUP_OWNER(HttpStatus.FORBIDDEN, "그룹장만 할 수 있습니다"),
    INVITE_NOT_FOUND(HttpStatus.NOT_FOUND, "초대 코드를 찾을 수 없습니다"),
    GROUP_FULL(HttpStatus.CONFLICT, "그룹 정원이 가득 찼습니다"),
    SUCCESSOR_REQUIRED(HttpStatus.CONFLICT, "다음 그룹장을 지정해 주세요"),
    INVALID_SUCCESSOR(HttpStatus.BAD_REQUEST, "다음 그룹장을 올바르게 지정해 주세요"),
    ALREADY_MEMBER(HttpStatus.CONFLICT, "이미 가입된 그룹입니다");

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
