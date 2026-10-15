package com.sapphire.hawe.insight.controller;

import com.sapphire.hawe.insight.service.GroupInsightService;
import java.time.YearMonth;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/groups/{groupId}/insights")
public class GroupInsightController {
    private final GroupInsightService service;

    public GroupInsightController(GroupInsightService service) { this.service = service; }

    @GetMapping
    public ResponseEntity<?> get(@AuthenticationPrincipal Long userId, @PathVariable Long groupId,
            @RequestParam(required = false) YearMonth month) {
        return service.get(userId, groupId, month);
    }
}
