package com.sapphire.hawe.global.storage;

public interface FileStorage {
    String store(String directory, byte[] data, String extension);
    byte[] load(String key);
    void delete(String key);
    String publicUrl(String key);
}
