package com.sapphire.hawe.badge.service;

import com.sapphire.hawe.global.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum BadgeErrorCode implements ErrorCode {
	BADGE_NOT_EARNED;

	@Override public String code() { return name(); }
	@Override public String message() { return "획득하지 않은 업적입니다"; }
	@Override public HttpStatus httpStatus() { return HttpStatus.NOT_FOUND; }
}
