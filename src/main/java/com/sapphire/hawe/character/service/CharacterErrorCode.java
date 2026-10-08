package com.sapphire.hawe.character.service;

import com.sapphire.hawe.global.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum CharacterErrorCode implements ErrorCode {

    CHARACTER_NOT_FOUND(HttpStatus.NOT_FOUND, "캐릭터를 찾을 수 없습니다");

    private final HttpStatus httpStatus;
    private final String message;

    CharacterErrorCode(HttpStatus httpStatus, String message) {
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
