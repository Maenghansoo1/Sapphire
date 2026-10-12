package com.sapphire.hawe.group.service;

import java.security.SecureRandom;
import java.util.Locale;

public final class InviteCodeGenerator {
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private InviteCodeGenerator() {
    }

    public static String generate() {
        StringBuilder code = new StringBuilder(6);
        for (int i = 0; i < 6; i++) code.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        return code.toString();
    }

    public static String normalize(String raw) {
        if (raw == null) return null;
        String code = raw.trim().toUpperCase(Locale.ROOT);
        if (code.length() != 6) return null;
        for (int i = 0; i < code.length(); i++) {
            if (ALPHABET.indexOf(code.charAt(i)) < 0) return null;
        }
        return code;
    }
}
