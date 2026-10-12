package com.sapphire.hawe.global.storage;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FileTypeDetectorTests {
    @Test void recognizesMagicBytes() {
        assertEquals("jpg", FileTypeDetector.detect(new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff}).extension());
        assertEquals("png", FileTypeDetector.detect(new byte[]{(byte) 0x89, 80, 78, 71, 13, 10, 26, 10}).extension());
        assertEquals("webp", FileTypeDetector.detect("RIFFabcdWEBP".getBytes()).extension());
        assertEquals("IMAGE", FileTypeDetector.detect("RIFFabcdWEBP".getBytes()).mediaType());
        assertNull(FileTypeDetector.detect(new byte[0]));
        assertNull(FileTypeDetector.detect("text".getBytes()));
        assertNull(FileTypeDetector.detect(new byte[]{(byte) 0x89, 80, 78}));
    }

    @Test void audioIsNoLongerSupported() {
        assertNull(FileTypeDetector.detect("xxxxftyp".getBytes()));
        assertNull(FileTypeDetector.detect("ID3".getBytes()));
        assertNull(FileTypeDetector.detect(new byte[]{(byte) 0xff, (byte) 0xf1}));
        assertNull(FileTypeDetector.detect(new byte[]{(byte) 0xff, (byte) 0xfb}));
        assertNull(FileTypeDetector.detect(new byte[]{0x1a, 0x45, (byte) 0xdf, (byte) 0xa3}));
    }
}
