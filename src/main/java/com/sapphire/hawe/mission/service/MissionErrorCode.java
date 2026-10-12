package com.sapphire.hawe.mission.service;

import com.sapphire.hawe.global.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum MissionErrorCode implements ErrorCode {
    MISSION_NOT_FOUND(HttpStatus.NOT_FOUND, "미션을 찾을 수 없습니다"),
    MISSION_CLOSED(HttpStatus.CONFLICT, "종료된 미션입니다");

    private final HttpStatus httpStatus;
    private final String message;

    MissionErrorCode(HttpStatus httpStatus, String message) {
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
