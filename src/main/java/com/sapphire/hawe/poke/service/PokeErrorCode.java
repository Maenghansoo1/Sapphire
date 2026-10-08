package com.sapphire.hawe.poke.service;

import com.sapphire.hawe.global.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum PokeErrorCode implements ErrorCode {

    CANNOT_POKE_SELF(HttpStatus.BAD_REQUEST, "자기 자신에게는 보낼 수 없습니다"),
    TARGET_NOT_GROUP_MEMBER(HttpStatus.NOT_FOUND, "상대방이 그룹 멤버가 아닙니다"),
    TARGET_ALREADY_SUBMITTED(HttpStatus.CONFLICT, "오늘 미션을 이미 제출한 멤버입니다");

    private final HttpStatus httpStatus;
    private final String message;

    PokeErrorCode(HttpStatus httpStatus, String message) {
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
