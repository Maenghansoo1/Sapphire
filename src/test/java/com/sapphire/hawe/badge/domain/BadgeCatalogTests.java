package com.sapphire.hawe.badge.domain;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import org.junit.jupiter.api.Test;

class BadgeCatalogTests {
	@Test
	void definitionsAreValidAndImmutable() {
		var codes = new HashSet<String>();
		for (var badge : BadgeCatalog.all()) {
			assertTrue(codes.add(badge.code()));
			assertTrue(badge.code().length() <= 40);
			assertTrue(badge.target() >= 1);
			assertNotNull(badge.tier());
			assertNotNull(badge.kind());
			assertEquals(badge.code().toLowerCase(java.util.Locale.ROOT), badge.imageKey());
			assertEquals(badge, BadgeCatalog.byCode(badge.code()).orElseThrow());
		}
		assertEquals(13, codes.size());
		assertEquals(6, BadgeCatalog.byCode("ALL_TEMPLATES").orElseThrow().target());
		assertEquals(BadgeKind.SCHEDULED, BadgeCatalog.byCode("PHOTO_PERFECT_5").orElseThrow().kind());
		assertTrue(BadgeCatalog.byCode("UNKNOWN").isEmpty());
		assertTrue(BadgeCatalog.byCode(null).isEmpty());
		assertThrows(UnsupportedOperationException.class, () -> BadgeCatalog.all().clear());
	}
}
