package com.sapphire.hawe.global.storage;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FileTypeDetectorTests {
    @Test void recognizesMagicBytes() {
        assertEquals("jpg", FileTypeDetector.detect(new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff}).extension());
        assertEquals("png", FileTypeDetector.detect(new byte[]{(byte) 0x89, 80, 78, 71, 13, 10, 26, 10}).extension());
        assertEquals("webp", FileTypeDetector.detect("RIFFabcdWEBP".getBytes()).extension());
        assertEquals("m4a", FileTypeDetector.detect("xxxxftyp".getBytes()).extension());
        assertEquals("mp3", FileTypeDetector.detect("ID3".getBytes()).extension());
        assertEquals("aac", FileTypeDetector.detect(new byte[]{(byte) 0xff, (byte) 0xf1}).extension());
        assertEquals("webm", FileTypeDetector.detect(new byte[]{0x1a, 0x45, (byte) 0xdf, (byte) 0xa3}).extension());
        assertEquals("IMAGE", FileTypeDetector.detect("RIFFabcdWEBP".getBytes()).mediaType());
        assertEquals("AUDIO", FileTypeDetector.detect("ID3".getBytes()).mediaType());
        assertNull(FileTypeDetector.detect(new byte[0]));
        assertNull(FileTypeDetector.detect("text".getBytes()));
        assertNull(FileTypeDetector.detect(new byte[]{(byte) 0x89, 80, 78}));
    }
}
