package com.sapphire.hawe.auth.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.exc.MismatchedInputException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RegisterRequestValidationTests {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void normalizesDeserializedFieldsAndKeepsPasswordUntouched() {
        var request = new ObjectMapper().readValue("""
                {"email":" Sky@Email.com ","password":"abcd 1234","nickname":" 하늘 ",
                 "agreeTerms":true,"agreePrivacy":true}
                """, RegisterRequest.class);
        assertEquals("sky@email.com", request.email());
        assertEquals("하늘", request.nickname());
        assertEquals("abcd 1234", request.password());
        assertTrue(validator.validate(request).isEmpty());
    }

    @Test
    void rejectsInvalidEmailNicknamePasswordAndConsent() {
        for (String email : new String[]{"", "bad", "   ", "a".repeat(256) + "@test.com"}) {
            assertInvalid(new RegisterRequest(email, "abcd1234", "하늘", true, true));
        }
        for (String nickname : new String[]{" ", "  하  ", "가나다라마바사아자차카"}) {
            assertInvalid(new RegisterRequest("a@test.com", "abcd1234", nickname, true, true));
        }
        for (String password : new String[]{"abcdefg", "abcdefgh", "12345678", "a1".repeat(51)}) {
            assertInvalid(new RegisterRequest("a@test.com", password, "하늘", true, true));
        }
        assertInvalid(new RegisterRequest("a@test.com", "abcd1234", "하늘", false, true));
        assertInvalid(new RegisterRequest("a@test.com", "abcd1234", "하늘", true, false));
        assertInvalid(new RegisterRequest("a@test.com", "abcd1234", "하늘", false, false));
        assertInvalid(new RegisterRequest(null, "abcd1234", "하늘", true, true));
        assertInvalid(new RegisterRequest("a@test.com", "abcd1234", null, true, true));
    }

    @Test
    void missingConsentIsRejectedAndMalformedLoginEmailRemainsValidForCredentialCheck() {
        assertThrows(MismatchedInputException.class, () -> new ObjectMapper().readValue("""
                {"email":"a@test.com","password":"abcd1234","nickname":"하늘"}
                """, RegisterRequest.class));
        assertTrue(validator.validate(new LoginRequest("invalid", "abcd1234")).isEmpty());
        assertFalse(validator.validate(new LoginRequest(null, "abcd1234")).isEmpty());
    }

    private void assertInvalid(RegisterRequest request) {
        assertFalse(validator.validate(request).isEmpty(), request.toString());
    }
}
