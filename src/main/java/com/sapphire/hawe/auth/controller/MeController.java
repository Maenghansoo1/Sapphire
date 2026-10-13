package com.sapphire.hawe.auth.controller;

import com.sapphire.hawe.auth.dto.MeResponse;
import com.sapphire.hawe.auth.dto.PasswordChangeRequest;
import com.sapphire.hawe.auth.dto.TokenResponse;
import com.sapphire.hawe.auth.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class MeController {

    private final AuthService authService;

    public MeController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/me")
    public MeResponse me(@AuthenticationPrincipal Long userId) {
        return authService.getMe(userId);
    }

    @PatchMapping("/me/password")
    public TokenResponse changePassword(@AuthenticationPrincipal Long userId,
            @Valid @RequestBody PasswordChangeRequest request) {
        return authService.changePassword(userId, request);
    }
}
