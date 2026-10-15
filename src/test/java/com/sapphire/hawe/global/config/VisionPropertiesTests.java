package com.sapphire.hawe.global.config;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VisionPropertiesTests {
    @Test void defaultsAndValidation() {
        var defaults = new VisionProperties(false, null, null, null, null, null, null);
        assertFalse(defaults.enabled());
        assertEquals(0.024, defaults.photoNow().perfect());
        assertEquals(0.017, defaults.photoNow().good());
        assertEquals(0.132, defaults.photoColor().perfect());
        assertEquals(0.104, defaults.photoColor().good());
        assertNull(defaults.thresholdFor("PHOTO_GALLERY"));
        var custom = new VisionProperties(true, "http://localhost:8001/", Duration.ofSeconds(1),
                Duration.ofSeconds(2), Duration.ZERO, null, null);
        assertEquals("http://localhost:8001", custom.baseUrl());
        assertThrows(IllegalArgumentException.class, () -> new VisionProperties.Threshold(0.1, 0.2));
        assertThrows(IllegalArgumentException.class, () -> new VisionProperties(false, null,
                Duration.ZERO, null, null, null, null));
    }
}
