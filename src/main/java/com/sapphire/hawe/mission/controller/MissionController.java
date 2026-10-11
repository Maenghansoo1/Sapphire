package com.sapphire.hawe.mission.controller;

import com.sapphire.hawe.mission.dto.MissionResponse;
import com.sapphire.hawe.mission.dto.MissionTemplateListResponse;
import com.sapphire.hawe.mission.dto.TodayMissionsResponse;
import com.sapphire.hawe.mission.service.MissionService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class MissionController {

    private final MissionService missionService;

    public MissionController(MissionService missionService) {
        this.missionService = missionService;
    }

    @GetMapping("/groups/{groupId}/missions/today")
    public TodayMissionsResponse getTodayMissions(@AuthenticationPrincipal Long userId,
            @PathVariable Long groupId) {
        return missionService.getTodayMissions(userId, groupId);
    }

    @GetMapping("/missions/{missionId}")
    public MissionResponse getMission(@AuthenticationPrincipal Long userId, @PathVariable Long missionId) {
        return missionService.getMission(userId, missionId);
    }

    @GetMapping("/mission-templates")
    public MissionTemplateListResponse listTemplates() {
        return missionService.listTemplates();
    }
}
