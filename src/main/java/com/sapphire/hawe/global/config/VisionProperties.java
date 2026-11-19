package com.sapphire.hawe.global.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

/**
 * @param apiKey       비전 서비스가 HAWE_VISION_API_KEY 로 인증을 켰을 때 X-Hawe-Vision-Key 헤더로 보내는 값. 비우면 보내지 않는다.
 * @param sendImageKey true 면 사진 바이트 대신 저장소 키(imageKey)를 보내 비전 서비스가 S3 에서 직접 읽게 한다.
 *                     저장소가 S3 이고 비전 서비스에 HAWE_VISION_S3_BUCKET 이 있을 때(Lambda 배포)만 켠다.
 * @param lambdaFunction 비우지 않으면 HTTP 대신 이 Lambda 함수(이름 또는 ARN)를 AWS SDK 로 직접 호출한다.
 *                       인증은 서버의 IAM 역할(EC2)로 하고, 사진은 항상 저장소 키로 보낸다.
 * @param lambdaRegion   Lambda 리전. 비우면 ap-northeast-2
 */
@ConfigurationProperties(prefix = "hawe.vision")
public record VisionProperties(boolean enabled, String baseUrl, Duration connectTimeout, Duration readTimeout,
        Duration retryDelay, Threshold photoNow, Threshold photoColor, String apiKey, boolean sendImageKey,
        String lambdaFunction, String lambdaRegion) {
    public record Threshold(double perfectThreshold, double goodThreshold) {
        public Threshold {
            if (!Double.isFinite(goodThreshold) || !Double.isFinite(perfectThreshold)
                    || goodThreshold <= 0 || goodThreshold > perfectThreshold || perfectThreshold > 1)
                throw new IllegalArgumentException("Invalid vision thresholds");
        }
        public double perfect() { return perfectThreshold; }
        public double good() { return goodThreshold; }
    }

    // 생성자가 둘이라 설정 바인딩에 쓸 생성자를 지정한다.
    @ConstructorBinding
    public VisionProperties {
        baseUrl = baseUrl == null || baseUrl.isBlank() ? "http://localhost:8001" : baseUrl.strip().replaceAll("/+$", "");
        connectTimeout = connectTimeout == null ? Duration.ofSeconds(3) : connectTimeout;
        readTimeout = readTimeout == null ? Duration.ofSeconds(10) : readTimeout;
        retryDelay = retryDelay == null ? Duration.ofSeconds(2) : retryDelay;
        photoNow = photoNow == null ? new Threshold(0.024, 0.017) : photoNow;
        photoColor = photoColor == null ? new Threshold(0.132, 0.104) : photoColor;
        apiKey = apiKey == null ? "" : apiKey.strip();
        lambdaFunction = lambdaFunction == null ? "" : lambdaFunction.strip();
        lambdaRegion = lambdaRegion == null || lambdaRegion.isBlank() ? "ap-northeast-2" : lambdaRegion.strip();
        if (connectTimeout.isNegative() || connectTimeout.isZero() || readTimeout.isNegative() || readTimeout.isZero()
                || retryDelay.isNegative()) throw new IllegalArgumentException("Invalid vision timeout");
    }

    /** 인증·키 전송 없이 사진 바이트를 보내는 기존 방식. */
    public VisionProperties(boolean enabled, String baseUrl, Duration connectTimeout, Duration readTimeout,
            Duration retryDelay, Threshold photoNow, Threshold photoColor) {
        this(enabled, baseUrl, connectTimeout, readTimeout, retryDelay, photoNow, photoColor, null, false);
    }

    /** HTTP 로 부르는 방식(로컬·EC2 컨테이너·Function URL). */
    public VisionProperties(boolean enabled, String baseUrl, Duration connectTimeout, Duration readTimeout,
            Duration retryDelay, Threshold photoNow, Threshold photoColor, String apiKey, boolean sendImageKey) {
        this(enabled, baseUrl, connectTimeout, readTimeout, retryDelay, photoNow, photoColor, apiKey, sendImageKey,
                null, null);
    }

    /** Lambda 를 SDK 로 직접 호출하는지. */
    public boolean usesLambda() {
        return !lambdaFunction.isEmpty();
    }

    /** 사진 대신 저장소 키를 보내는지. Lambda 직접 호출은 요청 크기 한도(6MB) 때문에 항상 키로 보낸다. */
    public boolean sendsImageKey() {
        return sendImageKey || usesLambda();
    }

    public Threshold thresholdFor(String template) {
        return switch (template) {
            case "PHOTO_NOW" -> photoNow;
            case "PHOTO_COLOR" -> photoColor;
            default -> null;
        };
    }

    // 인증 값이 로그·오류 화면에 찍히지 않게
    @Override
    public String toString() {
        return "VisionProperties[enabled=" + enabled + ", baseUrl=" + baseUrl + ", readTimeout=" + readTimeout
                + ", apiKey=" + (apiKey.isEmpty() ? "(없음)" : "(설정됨)") + ", sendImageKey=" + sendImageKey
                + ", lambdaFunction=" + lambdaFunction + ", lambdaRegion=" + lambdaRegion + "]";
    }
}
