package com.sapphire.hawe.badge.controller;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sapphire.hawe.badge.service.BackfillResult;
import com.sapphire.hawe.badge.service.BadgeBackfillService;
import com.sapphire.hawe.global.exception.BusinessException;
import com.sapphire.hawe.global.exception.CommonErrorCode;

@RestController
public class BadgeAdminController {
	private final BadgeBackfillService backfill;
	private final byte[] adminToken;

	public BadgeAdminController(BadgeBackfillService backfill,
			@Value("${hawe.admin.token:}") String adminToken) {
		this.backfill = backfill;
		this.adminToken = adminToken.strip().getBytes(StandardCharsets.UTF_8);
	}

	@PostMapping("/internal/badges/backfill")
	public ResponseEntity<BackfillResult> backfill(
			@RequestHeader(value = "X-Admin-Token", required = false) String token,
			@RequestParam(required = false) String batch,
			@RequestParam(required = false) String from,
			@RequestParam(required = false) String dryRun) {
		if (adminToken.length == 0 || token == null
				|| !MessageDigest.isEqual(adminToken, token.strip().getBytes(StandardCharsets.UTF_8)))
			throw new BusinessException(CommonErrorCode.NOT_FOUND);
		int batchSize = parseBatch(batch);
		long fromUserId = parseFrom(from);
		boolean preview = parseDryRun(dryRun);
		return ResponseEntity.ok(backfill.run(batchSize, fromUserId, preview));
	}

	private static int parseBatch(String value) {
		if (value == null) return 200;
		try {
			int parsed = Integer.parseInt(value);
			if (parsed >= 1 && parsed <= 500) return parsed;
		} catch (NumberFormatException ignored) {
		}
		throw new BusinessException(CommonErrorCode.VALIDATION_ERROR);
	}

	private static long parseFrom(String value) {
		if (value == null) return 0;
		try {
			long parsed = Long.parseLong(value);
			if (parsed >= 0) return parsed;
		} catch (NumberFormatException ignored) {
		}
		throw new BusinessException(CommonErrorCode.VALIDATION_ERROR);
	}

	private static boolean parseDryRun(String value) {
		if (value == null || value.equals("true")) return true;
		if (value.equals("false")) return false;
		throw new BusinessException(CommonErrorCode.VALIDATION_ERROR);
	}
}
