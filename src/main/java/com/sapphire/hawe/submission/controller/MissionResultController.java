package com.sapphire.hawe.submission.controller;

import com.sapphire.hawe.submission.dto.MissionResultResponse;
import com.sapphire.hawe.submission.service.MissionResultService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class MissionResultController {
    private final MissionResultService service;
    public MissionResultController(MissionResultService service) { this.service = service; }

    @GetMapping("/missions/{missionId}/result")
    public MissionResultResponse result(@AuthenticationPrincipal Long userId, @PathVariable Long missionId) {
        return service.result(userId, missionId);
    }
}
