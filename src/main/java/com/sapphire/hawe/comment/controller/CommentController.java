package com.sapphire.hawe.comment.controller;

import com.sapphire.hawe.comment.dto.CommentResponse;
import com.sapphire.hawe.comment.service.CommentService;
import com.sapphire.hawe.global.dto.PageResponse;
import com.sapphire.hawe.global.exception.BusinessException;
import com.sapphire.hawe.global.exception.CommonErrorCode;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
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
public class CommentController {
    private final CommentService service;
    public CommentController(CommentService service) { this.service = service; }

    @PostMapping(value = "/submissions/{submissionId}/comments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CommentResponse> create(@AuthenticationPrincipal Long userId,
            @PathVariable Long submissionId, @RequestParam(required = false) String content,
            @RequestParam(required = false) List<Long> mentionedUserIds, MultipartHttpServletRequest request) {
        List<MultipartFile> files = request.getMultiFileMap().get("file");
        if (files != null && files.size() > 1)
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "파일은 하나만 첨부해 주세요");
        MultipartFile file = files == null || files.isEmpty() ? null : files.getFirst();
        return ResponseEntity.status(201).body(service.create(userId, submissionId, content, mentionedUserIds, file));
    }

    @GetMapping("/submissions/{submissionId}/comments")
    public PageResponse<CommentResponse> list(@AuthenticationPrincipal Long userId, @PathVariable Long submissionId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.list(userId, submissionId, page, size);
    }

    @DeleteMapping("/comments/{commentId}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Long userId, @PathVariable Long commentId) {
        service.delete(userId, commentId);
        return ResponseEntity.noContent().build();
    }
}
