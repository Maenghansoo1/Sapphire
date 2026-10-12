package com.sapphire.hawe.profile.controller;

import com.sapphire.hawe.auth.dto.MeResponse;
import com.sapphire.hawe.global.exception.BusinessException;
import com.sapphire.hawe.global.exception.CommonErrorCode;
import com.sapphire.hawe.profile.dto.ProfileImageResponse;
import com.sapphire.hawe.profile.dto.ProfileUpdateRequest;
import com.sapphire.hawe.profile.service.ProfileService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/me")
public class ProfileController {
    private final ProfileService service;
    public ProfileController(ProfileService service) { this.service = service; }

    @PatchMapping("/profile")
    public MeResponse update(@AuthenticationPrincipal Long userId, @Valid @RequestBody ProfileUpdateRequest request) {
        return service.updateNickname(userId, request);
    }

    @PutMapping(value = "/profile-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ProfileImageResponse upload(@AuthenticationPrincipal Long userId,
            @RequestParam(value = "file", required = false) List<MultipartFile> files) {
        if (files == null || files.size() != 1)
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "사진 파일을 하나 첨부해 주세요");
        return service.uploadImage(userId, files.getFirst());
    }

    @DeleteMapping("/profile-image")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Long userId) {
        service.deleteImage(userId);
        return ResponseEntity.noContent().build();
    }
}
