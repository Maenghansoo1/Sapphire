package com.sapphire.hawe.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "hawe.chain")
public record ChainProperties(@DefaultValue("false") boolean enabled) {
}
