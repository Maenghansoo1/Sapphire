package com.sapphire.hawe.group.controller;

import com.sapphire.hawe.global.exception.BusinessException;
import com.sapphire.hawe.global.exception.CommonErrorCode;
import com.sapphire.hawe.group.dto.GroupProfileRequest;
import com.sapphire.hawe.group.dto.MemberProfileResponse;
import com.sapphire.hawe.group.service.GroupProfileService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/groups/{groupId}/members/me")
public class GroupProfileController {
    private final GroupProfileService service;

    public GroupProfileController(GroupProfileService service) { this.service = service; }

    @PutMapping("/profile")
    public MemberProfileResponse update(@AuthenticationPrincipal Long userId, @PathVariable Long groupId,
            @Valid @RequestBody GroupProfileRequest request) {
        return service.updateCharacter(userId, groupId, request);
    }

    @PutMapping(value = "/profile-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public MemberProfileResponse upload(@AuthenticationPrincipal Long userId, @PathVariable Long groupId,
            @RequestParam(value = "nickname", required = false) String nickname,
            @RequestParam(value = "file", required = false) List<MultipartFile> files) {
        if (files == null || files.size() != 1)
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "Attach one image file");
        return service.updateImage(userId, groupId, nickname, files.getFirst());
    }

    @DeleteMapping("/profile")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Long userId, @PathVariable Long groupId) {
        service.delete(userId, groupId);
        return ResponseEntity.noContent().build();
    }
}
