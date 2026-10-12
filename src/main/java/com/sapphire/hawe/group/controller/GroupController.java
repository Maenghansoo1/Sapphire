package com.sapphire.hawe.group.controller;

import com.sapphire.hawe.global.dto.PageResponse;
import com.sapphire.hawe.group.dto.GroupCreateRequest;
import com.sapphire.hawe.group.dto.GroupMemberResponse;
import com.sapphire.hawe.group.dto.GroupResponse;
import com.sapphire.hawe.group.dto.GroupSettingsUpdateRequest;
import com.sapphire.hawe.group.dto.GroupSummaryResponse;
import com.sapphire.hawe.group.dto.JoinRequest;
import com.sapphire.hawe.group.dto.JoinResponse;
import com.sapphire.hawe.group.service.GroupService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/groups")
public class GroupController {

    private final GroupService groupService;

    public GroupController(GroupService groupService) {
        this.groupService = groupService;
    }

    @GetMapping
    public PageResponse<GroupSummaryResponse> listMyGroups(
            @AuthenticationPrincipal Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return groupService.listMyGroups(userId, page, size);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GroupResponse createGroup(@AuthenticationPrincipal Long userId,
            @Valid @RequestBody GroupCreateRequest request) {
        return groupService.createGroup(userId, request);
    }

    @GetMapping("/{groupId}")
    public GroupResponse getGroup(@AuthenticationPrincipal Long userId, @PathVariable Long groupId) {
        return groupService.getGroup(userId, groupId);
    }

    @PatchMapping("/{groupId}/settings")
    public GroupResponse updateSettings(@AuthenticationPrincipal Long userId, @PathVariable Long groupId,
            @Valid @RequestBody GroupSettingsUpdateRequest request) {
        return groupService.updateSettings(userId, groupId, request);
    }

    @GetMapping("/{groupId}/members")
    public PageResponse<GroupMemberResponse> listMembers(
            @AuthenticationPrincipal Long userId, @PathVariable Long groupId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return groupService.listMembers(userId, groupId, page, size);
    }

    @PostMapping("/join")
    public JoinResponse join(@AuthenticationPrincipal Long userId, @Valid @RequestBody JoinRequest request) {
        return groupService.join(userId, request);
    }

    @DeleteMapping("/{groupId}/members/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void leaveGroup(@AuthenticationPrincipal Long userId, @PathVariable Long groupId,
            @RequestParam(required = false) Long successorUserId) {
        groupService.leaveGroup(userId, groupId, successorUserId);
    }
}
