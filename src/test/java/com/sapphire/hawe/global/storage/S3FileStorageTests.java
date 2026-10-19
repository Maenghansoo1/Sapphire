package com.sapphire.hawe.global.storage;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// 네트워크 없이 확인할 수 있는 것만 본다 (서명 URL 생성, 키 검증, 설정 오류)
class S3FileStorageTests {

    private static StorageProperties properties(String bucket) {
        return new StorageProperties("s3", "./uploads", "/files",
                new StorageProperties.S3(bucket, "ap-northeast-2", "AKIAEXAMPLE", "secret", Duration.ofMinutes(10)));
    }

    @Test
    void publicUrlIsPresignedForPrivateBucket() {
        S3FileStorage storage = new S3FileStorage(properties("hawe-media"));
        String url = storage.publicUrl("submissions/0b1c2d3e-aaaa-bbbb-cccc-123456789abc.jpg");

        assertTrue(url.startsWith("https://hawe-media.s3.ap-northeast-2.amazonaws.com/submissions/"), url);
        assertTrue(url.contains("X-Amz-Signature="), url);
        assertTrue(url.contains("X-Amz-Expires=600"), url);
    }

    @Test
    void rejectsKeysOutsideStoredFormat() {
        S3FileStorage storage = new S3FileStorage(properties("hawe-media"));
        assertThrows(IllegalArgumentException.class, () -> storage.delete("../secret.txt"));
        assertThrows(IllegalArgumentException.class, () -> storage.load("submissions/../../x.jpg"));
        assertThrows(IllegalArgumentException.class, () -> storage.store("Bad Dir", new byte[1], "jpg"));
    }

    @Test
    void requiresBucket() {
        assertThrows(IllegalStateException.class, () -> new S3FileStorage(properties(" ")));
    }
}
