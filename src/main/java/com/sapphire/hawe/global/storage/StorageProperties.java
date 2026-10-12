package com.sapphire.hawe.global.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("hawe.storage")
public record StorageProperties(String localDir, String publicBaseUrl) {
}
