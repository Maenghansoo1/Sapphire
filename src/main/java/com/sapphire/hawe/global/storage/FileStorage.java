package com.sapphire.hawe.global.storage;

public interface FileStorage {
    String store(String directory, byte[] data, String extension);
    void delete(String key);
    String publicUrl(String key);
}
