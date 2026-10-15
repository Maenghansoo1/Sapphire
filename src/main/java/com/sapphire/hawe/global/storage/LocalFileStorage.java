package com.sapphire.hawe.global.storage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "hawe.storage.type", havingValue = "local", matchIfMissing = true)
public class LocalFileStorage implements FileStorage {
    private final Path root;
    private final String publicBaseUrl;

    public LocalFileStorage(StorageProperties properties) {
        root = Path.of(properties.localDir()).toAbsolutePath().normalize();
        publicBaseUrl = properties.publicBaseUrl().replaceAll("/+$", "");
    }

    public String store(String directory, byte[] data, String extension) {
        if (!directory.matches("[a-z0-9_-]+") || !extension.matches("[a-z0-9]+"))
            throw new IllegalArgumentException("Invalid storage path");
        String key = directory + "/" + UUID.randomUUID() + "." + extension;
        Path target = root.resolve(key);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, data);
            return key;
        } catch (IOException e) {
            try { Files.deleteIfExists(target); }
            catch (IOException cleanup) { e.addSuppressed(cleanup); }
            throw new UncheckedIOException(e);
        }
    }

    public void delete(String key) {
        Path target = root.resolve(key).normalize();
        if (!target.startsWith(root)) throw new IllegalArgumentException("Invalid storage key");
        try { Files.deleteIfExists(target); }
        catch (IOException e) { throw new UncheckedIOException(e); }
    }

    public byte[] load(String key) {
        Path target = root.resolve(key).normalize();
        if (!target.startsWith(root)) throw new IllegalArgumentException("Invalid storage key");
        try { return Files.readAllBytes(target); }
        catch (IOException e) { throw new UncheckedIOException(e); }
    }

    public String publicUrl(String key) { return publicBaseUrl + "/" + key; }
}
