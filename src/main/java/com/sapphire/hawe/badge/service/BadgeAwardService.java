package com.sapphire.hawe.badge.service;

import java.time.LocalDateTime;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sapphire.hawe.badge.dao.BadgeMapper;
import com.sapphire.hawe.badge.domain.BadgeCatalog;
import com.sapphire.hawe.badge.domain.BadgeStatus;
import com.sapphire.hawe.global.config.ChainProperties;
import com.sapphire.hawe.global.time.SeoulTime;

@Service
@EnableConfigurationProperties(ChainProperties.class)
public class BadgeAwardService {
	private final BadgeMapper mapper;
	private final ChainProperties chainProperties;

	public BadgeAwardService(BadgeMapper mapper, ChainProperties chainProperties) {
		this.mapper = mapper;
		this.chainProperties = chainProperties;
	}

	@Transactional
	public boolean award(Long userId, String code, LocalDateTime earnedAt) {
		if (BadgeCatalog.byCode(code).isEmpty()) {
			throw new IllegalArgumentException("Unknown badge code: " + code);
		}
		LocalDateTime now = SeoulTime.now();
		LocalDateTime actualEarnedAt = earnedAt == null || earnedAt.isAfter(now) ? now : earnedAt;
		BadgeStatus status = chainProperties.enabled() ? BadgeStatus.EARNED : BadgeStatus.ISSUED_OFFCHAIN;
		return mapper.insertIgnore(userId, code, status, actualEarnedAt) == 1;
	}
}
