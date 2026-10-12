package com.sapphire.hawe.reaction.controller;

import com.sapphire.hawe.reaction.dto.ReactionRequest;
import com.sapphire.hawe.reaction.dto.ReactionsResponse;
import com.sapphire.hawe.reaction.service.ReactionService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/submissions/{submissionId}/reactions")
public class ReactionController {
    private final ReactionService service;
    public ReactionController(ReactionService service) { this.service = service; }
    @PostMapping
    public ReactionsResponse toggle(@AuthenticationPrincipal Long userId, @PathVariable Long submissionId,
            @Valid @RequestBody ReactionRequest request) {
        return service.toggle(userId, submissionId, request);
    }
}
