package com.sapphire.hawe.group.controller;

import com.sapphire.hawe.group.dto.InvitePreviewResponse;
import com.sapphire.hawe.group.service.GroupService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/invites")
public class InviteController {
    private final GroupService groupService;

    public InviteController(GroupService groupService) {
        this.groupService = groupService;
    }

    @GetMapping("/{code}")
    public InvitePreviewResponse preview(@AuthenticationPrincipal Long userId, @PathVariable String code) {
        return groupService.previewInvite(userId, code);
    }
}
