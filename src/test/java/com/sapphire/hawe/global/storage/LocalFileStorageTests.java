package com.sapphire.hawe.global.storage;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class LocalFileStorageTests {
    @TempDir Path dir;
    @Test void storesAndDeletesFiles() throws Exception {
        LocalFileStorage storage = new LocalFileStorage(new StorageProperties("local", dir.toString(), "/files", null));
        byte[] data = {1, 2, 3};
        String key = storage.store("submissions", data, "jpg");
        assertTrue(key.matches("submissions/[0-9a-f-]+\\.jpg"));
        assertArrayEquals(data, Files.readAllBytes(dir.resolve(key)));
        assertArrayEquals(data, storage.load(key));
        assertEquals("/files/" + key, storage.publicUrl(key));
        storage.delete(key);
        storage.delete(key);
        assertFalse(Files.exists(dir.resolve(key)));
        assertThrows(IllegalArgumentException.class, () -> storage.delete("../secret"));
        assertThrows(IllegalArgumentException.class, () -> storage.load("../secret"));
        assertThrows(java.io.UncheckedIOException.class, () -> storage.load(key));
    }
}
