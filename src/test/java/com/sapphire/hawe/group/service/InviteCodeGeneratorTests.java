package com.sapphire.hawe.group.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InviteCodeGeneratorTests {
    @Test
    void generatedCodesUseSixUnambiguousCharacters() {
        for (int i = 0; i < 200; i++) {
            assertTrue(InviteCodeGenerator.generate().matches("[A-HJ-NP-Z2-9]{6}"));
        }
    }

    @Test
    void normalizationRejectsInvalidCodesBeforeLookup() {
        assertEquals("K7Q2PX", InviteCodeGenerator.normalize(" k7q2px "));
        for (String value : new String[]{null, "", "ABC", "ABCDEFG", "0AAAAA", "OAAAAA", "1AAAAA", "IAAAAA"}) {
            assertNull(InviteCodeGenerator.normalize(value));
        }
    }
}
