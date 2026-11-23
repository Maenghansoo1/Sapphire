package com.sapphire.hawe.global.config;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class BadgePropertiesTests {
	@Test
	void defaultsAndBounds() {
		var defaults = new BadgeProperties(false, false, null, null, null, null, null);
		assertFalse(defaults.enabled());
		assertFalse(defaults.dryRun());
		assertEquals(2, defaults.lookbackDays());
		assertEquals(14, defaults.crowdWindowDays());
		assertEquals(3, defaults.perfectMonthDays());
		assertEquals(5, defaults.perfectMonthMinMissions());
		assertEquals(500, defaults.batchSize());
		assertThrows(IllegalArgumentException.class, () -> new BadgeProperties(false, false, 0, null, null, null, null));
		assertThrows(IllegalArgumentException.class, () -> new BadgeProperties(false, false, null, 0, null, null, null));
		assertThrows(IllegalArgumentException.class, () -> new BadgeProperties(false, false, null, null, 29, null, null));
		assertThrows(IllegalArgumentException.class, () -> new BadgeProperties(false, false, null, null, null, 0, null));
		assertThrows(IllegalArgumentException.class, () -> new BadgeProperties(false, false, null, null, null, null, 0));
	}
}
