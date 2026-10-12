package com.sapphire.hawe.withdraw.controller;

import com.sapphire.hawe.withdraw.service.WithdrawService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me")
public class WithdrawController {
    private final WithdrawService service;
    public WithdrawController(WithdrawService service) { this.service = service; }

    @DeleteMapping
    public ResponseEntity<Void> withdraw(@AuthenticationPrincipal Long userId) {
        service.withdraw(userId);
        return ResponseEntity.noContent().build();
    }
}
