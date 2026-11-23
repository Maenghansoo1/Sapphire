package com.sapphire.hawe.badge.domain;

import java.time.LocalDateTime;

public record ValidSubmission(Long submissionId, LocalDateTime submittedAt, boolean late,
		LocalDateTime publishedAt, String template) {
}
