package com.sapphire.hawe.global.storage;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 파일 저장소 설정. {@code type} 이 {@code s3} 이면 S3, 아니면 로컬 디스크({@code localDir})에 저장한다.
 *
 * @param s3 S3 설정. 키를 비워 두면 기본 자격 증명(EC2 IAM Role, ~/.aws/credentials)을 쓴다
 */
@ConfigurationProperties("hawe.storage")
public record StorageProperties(String type, String localDir, String publicBaseUrl, S3 s3) {

    public StorageProperties {
        type = type == null || type.isBlank() ? "local" : type.strip();
        s3 = s3 == null ? new S3(null, null, null, null, null) : s3;
    }

    /** @param urlTtl 응답에 넣는 presigned URL 의 유효 시간 */
    public record S3(String bucket, String region, String accessKey, String secretKey, Duration urlTtl) {
        public S3 {
            region = region == null || region.isBlank() ? "ap-northeast-2" : region.strip();
            accessKey = accessKey == null ? "" : accessKey.strip();
            secretKey = secretKey == null ? "" : secretKey.strip();
            urlTtl = urlTtl == null ? Duration.ofHours(1) : urlTtl;
        }

        // 키가 로그·오류 화면에 찍히지 않게
        @Override
        public String toString() {
            return "S3[bucket=" + bucket + ", region=" + region + ", accessKey=" + (accessKey.isEmpty() ? "(없음)" : "(설정됨)") + "]";
        }
    }
}
