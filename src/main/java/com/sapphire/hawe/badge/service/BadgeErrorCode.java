package com.sapphire.hawe.badge.service;

import com.sapphire.hawe.global.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum BadgeErrorCode implements ErrorCode {
	BADGE_NOT_EARNED,
	BACKFILL_RUNNING;

	@Override public String code() { return name(); }
	@Override public String message() {
		return this == BACKFILL_RUNNING ? "소급 발급이 이미 실행 중입니다" : "획득하지 않은 업적입니다";
	}
	@Override public HttpStatus httpStatus() {
		return this == BACKFILL_RUNNING ? HttpStatus.CONFLICT : HttpStatus.NOT_FOUND;
	}
}
