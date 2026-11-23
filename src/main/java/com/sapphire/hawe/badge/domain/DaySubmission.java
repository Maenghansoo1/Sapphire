package com.sapphire.hawe.badge.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record DaySubmission(LocalDate missionDate, LocalDateTime firstOnTimeSubmittedAt) {
}
