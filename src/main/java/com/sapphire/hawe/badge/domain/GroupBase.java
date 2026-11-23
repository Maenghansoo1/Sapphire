package com.sapphire.hawe.badge.domain;

import java.time.LocalDate;

public record GroupBase(Long groupId, LocalDate base, LocalDate joinedOn, boolean coupleStartDate) {
}
