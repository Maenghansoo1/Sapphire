package com.sapphire.hawe.group.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.core.JacksonException;

import static org.junit.jupiter.api.Assertions.*;

class GroupRequestValidationTests {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void namesAreTrimmedAndConstrainedInCreateAndPatch() {
        assertEquals("우리집", new GroupCreateRequest("  우리집  ", null, null, null, null).name());
        assertEquals("우리집", new GroupSettingsUpdateRequest("  우리집  ", null, null, null, null).name());
        for (String name : new String[]{"", "   ", "abcdefghijklmnopq"}) {
            assertFalse(validator.validate(new GroupCreateRequest(name, null, null, null, null)).isEmpty());
            assertFalse(validator.validate(new GroupSettingsUpdateRequest(name, null, null, null, null)).isEmpty());
        }
        assertTrue(validator.validate(new GroupSettingsUpdateRequest(null, null, null, null, null)).isEmpty());
    }

    @Test
    void malformedStartDateCannotDeserialize() {
        assertThrows(JacksonException.class, () -> new ObjectMapper().readValue("""
                {"name":"우리집","relationType":"COUPLE","startDate":"2026-13-40"}
                """, GroupCreateRequest.class));
    }
}
