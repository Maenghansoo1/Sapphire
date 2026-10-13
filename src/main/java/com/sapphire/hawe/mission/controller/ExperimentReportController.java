package com.sapphire.hawe.mission.controller;

import com.sapphire.hawe.global.exception.BusinessException;
import com.sapphire.hawe.global.exception.CommonErrorCode;
import com.sapphire.hawe.mission.service.ExperimentReportService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;

/**
 * 주제 비교 실험 리포트 (B9, 내부용). 사용자 JWT 와 따로 {@code X-Admin-Token} 헤더가 설정
 * {@code hawe.admin.token} 과 같아야 한다. 토큰을 설정하지 않았거나 다르면 엔드포인트가 없는 것처럼 404.
 */
@RestController
public class ExperimentReportController {

    static final int MAX_DAYS = 366;
    private static final MediaType MARKDOWN = new MediaType("text", "markdown", StandardCharsets.UTF_8);

    private final ExperimentReportService reports;
    private final byte[] adminToken;

    public ExperimentReportController(ExperimentReportService reports, @Value("${hawe.admin.token:}") String adminToken) {
        this.reports = reports;
        this.adminToken = adminToken.strip().getBytes(StandardCharsets.UTF_8);
    }

    // 날짜는 글로 받아 토큰을 먼저 확인한다 (형식 오류 응답으로 엔드포인트가 드러나지 않게)
    @GetMapping("/internal/experiments/topic")
    public ResponseEntity<?> topic(@RequestHeader(value = "X-Admin-Token", required = false) String token,
            @RequestParam(required = false) String from, @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "json") String format) {
        if (adminToken.length == 0 || token == null
                || !MessageDigest.isEqual(adminToken, token.strip().getBytes(StandardCharsets.UTF_8)))
            throw new BusinessException(CommonErrorCode.NOT_FOUND);
        LocalDate start = date(from);
        LocalDate end = date(to);
        if (end.isBefore(start) || ChronoUnit.DAYS.between(start, end) >= MAX_DAYS || !format.matches("json|md"))
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR);
        var report = reports.report(start, end);
        if (format.equals("md")) return ResponseEntity.ok().contentType(MARKDOWN).body(ExperimentReportService.markdown(report));
        return ResponseEntity.ok(report);
    }

    private static LocalDate date(String value) {
        try {
            if (value == null) throw new BusinessException(CommonErrorCode.VALIDATION_ERROR);
            return LocalDate.parse(value.strip());
        } catch (DateTimeParseException e) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR);
        }
    }
}
