package com.sapphire.hawe.global.time;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.Duration;

public final class SeoulTime {

    public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private static volatile Duration offset = Duration.ZERO;

    private SeoulTime() {
    }

    public static LocalDate today() {
        return now().toLocalDate();
    }

    public static LocalDateTime now() {
        return realNow().plus(offset);
    }

    public static LocalDateTime realNow() { return LocalDateTime.now(ZONE); }
    public static Duration offset() { return offset; }
    public static void setOffset(Duration value) { offset = java.util.Objects.requireNonNull(value); }
    public static void reset() { offset = Duration.ZERO; }

    public static OffsetDateTime toOffset(LocalDateTime dateTime) {
        return dateTime == null ? null : dateTime.atZone(ZONE).toOffsetDateTime();
    }
}
