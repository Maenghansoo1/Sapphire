package com.sapphire.hawe.global.storage;

public final class FileTypeDetector {
    public record Detected(String mediaType, String extension) { }
    private FileTypeDetector() { }

    public static Detected detect(byte[] b) {
        if (b.length >= 3 && u(b[0]) == 0xff && u(b[1]) == 0xd8 && u(b[2]) == 0xff)
            return new Detected("IMAGE", "jpg");
        if (b.length >= 8 && u(b[0]) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G'
                && u(b[4]) == 13 && u(b[5]) == 10 && u(b[6]) == 26 && u(b[7]) == 10)
            return new Detected("IMAGE", "png");
        if (b.length >= 12 && text(b, 0, "RIFF") && text(b, 8, "WEBP"))
            return new Detected("IMAGE", "webp");
        return null;
    }

    private static int u(byte b) { return b & 0xff; }
    private static boolean text(byte[] b, int offset, String value) {
        for (int i = 0; i < value.length(); i++) if (b[offset + i] != value.charAt(i)) return false;
        return true;
    }
}
