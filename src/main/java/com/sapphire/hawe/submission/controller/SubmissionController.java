package com.sapphire.hawe.submission.controller;

import com.sapphire.hawe.global.dto.PageResponse;
import com.sapphire.hawe.submission.dto.SubmissionResponse;
import com.sapphire.hawe.submission.service.SubmissionService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

@RestController
@RequestMapping("/api")
public class SubmissionController {
    private final SubmissionService service;
    public SubmissionController(SubmissionService service) { this.service = service; }

    @PostMapping(value = "/missions/{missionId}/submissions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<SubmissionResponse> submit(@AuthenticationPrincipal Long userId,
            @PathVariable Long missionId, MultipartHttpServletRequest request) {
        MultiValueMap<String, String> fields = new LinkedMultiValueMap<>();
        request.getParameterMap().forEach((key, values) -> {
            for (String value : values) fields.add(key, value);
        });
        MultiValueMap<String, MultipartFile> files = request.getMultiFileMap();
        return ResponseEntity.status(201).body(service.submit(userId, missionId, fields, files));
    }

    @GetMapping("/missions/{missionId}/submissions")
    public PageResponse<SubmissionResponse> feed(@AuthenticationPrincipal Long userId,
            @PathVariable Long missionId, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.feed(userId, missionId, page, size);
    }

    @GetMapping("/submissions/{submissionId}")
    public SubmissionResponse detail(@AuthenticationPrincipal Long userId, @PathVariable Long submissionId) {
        return service.detail(userId, submissionId);
    }
}
