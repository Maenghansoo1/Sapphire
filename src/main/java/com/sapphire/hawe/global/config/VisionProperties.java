package com.sapphire.hawe.global.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "hawe.vision")
public record VisionProperties(boolean enabled, String baseUrl, Duration connectTimeout, Duration readTimeout,
        Duration retryDelay, Threshold photoNow, Threshold photoColor) {
    public record Threshold(double perfectThreshold, double goodThreshold) {
        public Threshold {
            if (!Double.isFinite(goodThreshold) || !Double.isFinite(perfectThreshold)
                    || goodThreshold <= 0 || goodThreshold > perfectThreshold || perfectThreshold > 1)
                throw new IllegalArgumentException("Invalid vision thresholds");
        }
        public double perfect() { return perfectThreshold; }
        public double good() { return goodThreshold; }
    }

    public VisionProperties {
        baseUrl = baseUrl == null || baseUrl.isBlank() ? "http://localhost:8001" : baseUrl.strip().replaceAll("/+$", "");
        connectTimeout = connectTimeout == null ? Duration.ofSeconds(3) : connectTimeout;
        readTimeout = readTimeout == null ? Duration.ofSeconds(10) : readTimeout;
        retryDelay = retryDelay == null ? Duration.ofSeconds(2) : retryDelay;
        photoNow = photoNow == null ? new Threshold(0.024, 0.017) : photoNow;
        photoColor = photoColor == null ? new Threshold(0.132, 0.104) : photoColor;
        if (connectTimeout.isNegative() || connectTimeout.isZero() || readTimeout.isNegative() || readTimeout.isZero()
                || retryDelay.isNegative()) throw new IllegalArgumentException("Invalid vision timeout");
    }

    public Threshold thresholdFor(String template) {
        return switch (template) {
            case "PHOTO_NOW" -> photoNow;
            case "PHOTO_COLOR" -> photoColor;
            default -> null;
        };
    }
}
