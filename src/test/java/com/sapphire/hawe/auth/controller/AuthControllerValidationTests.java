package com.sapphire.hawe.auth.controller;

import com.sapphire.hawe.auth.dto.RegisterRequest;
import com.sapphire.hawe.auth.dto.RegisterResponse;
import com.sapphire.hawe.auth.service.AuthService;
import com.sapphire.hawe.global.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuthControllerValidationTests {

    @Mock AuthService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new AuthController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void registerRejectsMissingOrFalseConsentAndLongNickname() throws Exception {
        expectValidationError("""
                {"email":"a@test.com","password":"abcd1234","nickname":"하늘"}
                """);
        expectValidationError("""
                {"email":"a@test.com","password":"abcd1234","nickname":"하늘",
                 "agreeTerms":false,"agreePrivacy":true}
                """);
        expectValidationError("""
                {"email":"a@test.com","password":"abcd1234","nickname":"가나다라마바사아자차카",
                 "agreeTerms":true,"agreePrivacy":true}
                """);
    }

    @Test
    void registerAcceptsValidBody() throws Exception {
        when(service.register(any(RegisterRequest.class)))
                .thenReturn(new RegisterResponse(1L, "a@test.com", "하늘"));
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"a@test.com","password":"abcd1234","nickname":"하늘",
                 "agreeTerms":true,"agreePrivacy":true}
                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("a@test.com"));
    }

    private void expectValidationError(String body) throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
