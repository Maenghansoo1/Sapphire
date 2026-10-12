package com.sapphire.hawe.global.config;

import com.sapphire.hawe.global.time.SeoulTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Duration;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class TimeZoneConfigTests {

    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void jvmAndDatabaseSessionUseSeoulTime() {
        assertEquals(SeoulTime.ZONE, TimeZone.getDefault().toZoneId());
        assertEquals("+09:00", jdbcTemplate.queryForObject("SELECT @@session.time_zone", String.class));
        var databaseNow = jdbcTemplate.queryForObject("SELECT NOW()", Timestamp.class).toLocalDateTime();
        assertTrue(Duration.between(databaseNow, SeoulTime.now()).abs().compareTo(Duration.ofSeconds(5)) < 0);
    }
}
