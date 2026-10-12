package com.sapphire.hawe.global.config;

import com.sapphire.hawe.global.time.SeoulTime;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;

import java.util.TimeZone;

@Configuration
public class TimeZoneConfig {

    @PostConstruct
    void init() {
        TimeZone.setDefault(TimeZone.getTimeZone(SeoulTime.ZONE));
    }
}
