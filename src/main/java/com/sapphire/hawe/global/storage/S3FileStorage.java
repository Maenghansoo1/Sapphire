package com.sapphire.hawe.global.storage;

import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * S3 비공개 버킷에 저장한다. DB 에는 키만 두고, 응답할 때 짧게 유효한 presigned URL 을 만들어 준다.
 * 자격 증명은 hawe.storage.s3.access-key/secret-key 가 있으면 그것을, 없으면 기본 체인(EC2 IAM Role 등)을 쓴다.
 */
@Component
@ConditionalOnProperty(name = "hawe.storage.type", havingValue = "s3")
public class S3FileStorage implements FileStorage {
    private static final Map<String, String> CONTENT_TYPES = Map.of(
            "jpg", "image/jpeg", "png", "image/png", "webp", "image/webp",
            "m4a", "audio/mp4", "mp3", "audio/mpeg", "aac", "audio/aac", "webm", "audio/webm");

    private final String bucket;
    private final Duration urlTtl;
    private final S3Client client;
    private final S3Presigner presigner;

    public S3FileStorage(StorageProperties properties) {
        StorageProperties.S3 s3 = properties.s3();
        if (s3.bucket() == null || s3.bucket().isBlank())
            throw new IllegalStateException("hawe.storage.type=s3 이면 hawe.storage.s3.bucket 이 필요함");
        bucket = s3.bucket().strip();
        urlTtl = s3.urlTtl();
        Region region = Region.of(s3.region());
        AwsCredentialsProvider credentials = s3.accessKey().isEmpty() || s3.secretKey().isEmpty()
                ? DefaultCredentialsProvider.builder().build()
                : StaticCredentialsProvider.create(AwsBasicCredentials.create(s3.accessKey(), s3.secretKey()));
        client = S3Client.builder().region(region).credentialsProvider(credentials).build();
        presigner = S3Presigner.builder().region(region).credentialsProvider(credentials).build();
    }

    public String store(String directory, byte[] data, String extension) {
        if (!directory.matches("[a-z0-9_-]+") || !extension.matches("[a-z0-9]+"))
            throw new IllegalArgumentException("Invalid storage path");
        String key = directory + "/" + UUID.randomUUID() + "." + extension;
        client.putObject(b -> b.bucket(bucket).key(key)
                        .contentType(CONTENT_TYPES.getOrDefault(extension, "application/octet-stream")),
                RequestBody.fromBytes(data));
        return key;
    }

    public byte[] load(String key) {
        return client.getObjectAsBytes(b -> b.bucket(bucket).key(checked(key))).asByteArray();
    }

    public void delete(String key) {
        client.deleteObject(b -> b.bucket(bucket).key(checked(key)));
    }

    /** 네트워크 호출 없이 서명만 하므로 응답마다 만들어도 된다. */
    public String publicUrl(String key) {
        return presigner.presignGetObject(p -> p.signatureDuration(urlTtl)
                .getObjectRequest(g -> g.bucket(bucket).key(key))).url().toString();
    }

    @PreDestroy
    void close() {
        presigner.close();
        client.close();
    }

    private static String checked(String key) {
        if (key == null || !key.matches("[a-z0-9_-]+/[A-Za-z0-9-]+\\.[a-z0-9]+"))
            throw new IllegalArgumentException("Invalid storage key");
        return key;
    }
}
