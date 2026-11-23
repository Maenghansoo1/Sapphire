package com.sapphire.hawe.badge.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

import com.sapphire.hawe.badge.dao.BadgeMapper;
import com.sapphire.hawe.badge.domain.BadgeStatus;
import com.sapphire.hawe.global.config.ChainProperties;
import com.sapphire.hawe.global.time.SeoulTime;

class BadgeAwardServiceTests {
	@Test
	void returnsWhetherInsertWasNewAndUsesOffchainStatus() {
		BadgeMapper mapper = mock(BadgeMapper.class);
		var service = new BadgeAwardService(mapper, new ChainProperties(false));
		var earnedAt = LocalDateTime.of(2026, 1, 1, 0, 0);
		when(mapper.insertIgnore(1L, "FIRST_SUBMISSION", BadgeStatus.ISSUED_OFFCHAIN, earnedAt))
				.thenReturn(1, 0);
		assertTrue(service.award(1L, "FIRST_SUBMISSION", earnedAt));
		assertFalse(service.award(1L, "FIRST_SUBMISSION", earnedAt));
		assertThrows(IllegalArgumentException.class, () -> service.award(1L, "UNKNOWN", earnedAt));
		verify(mapper, times(2)).insertIgnore(1L, "FIRST_SUBMISSION", BadgeStatus.ISSUED_OFFCHAIN, earnedAt);
	}

	@Test
	void chainOnUsesEarnedAndFutureOrNullTimeIsClamped() {
		BadgeMapper mapper = mock(BadgeMapper.class);
		var service = new BadgeAwardService(mapper, new ChainProperties(true));
		var before = SeoulTime.now();
		service.award(2L, "STREAK_7", before.plusDays(2));
		service.award(2L, "STREAK_7", null);
		var after = SeoulTime.now();
		var times = org.mockito.ArgumentCaptor.forClass(LocalDateTime.class);
		verify(mapper, times(2)).insertIgnore(eq(2L), eq("STREAK_7"), eq(BadgeStatus.EARNED), times.capture());
		for (var time : times.getAllValues()) {
			assertFalse(time.isBefore(before));
			assertFalse(time.isAfter(after));
		}
	}
}
