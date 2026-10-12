package com.sapphire.hawe.withdraw.service;

import com.sapphire.hawe.global.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum WithdrawErrorCode implements ErrorCode {
    OWNER_MUST_TRANSFER;

    @Override public String code() { return name(); }
    @Override public String message() { return "그룹장인 그룹에서 먼저 나가 주세요"; }
    @Override public HttpStatus httpStatus() { return HttpStatus.CONFLICT; }
}
