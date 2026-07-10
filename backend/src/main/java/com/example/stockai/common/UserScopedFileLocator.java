package com.example.stockai.common;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.file.Path;

public final class UserScopedFileLocator {
    private UserScopedFileLocator() {}

    public static Path resolve(Path parent, String prefix, String suffix, String email) {
        return parent.resolve(prefix + sha256(normalizeEmail(email)) + suffix);
    }

    public static Path resolveLegacy(Path parent, String prefix, String suffix, String email) {
        return parent.resolve(prefix + Integer.toHexString(normalizeEmail(email).hashCode()) + suffix);
    }

    private static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte current : bytes) {
                builder.append(Character.forDigit((current >> 4) & 0xf, 16));
                builder.append(Character.forDigit(current & 0xf, 16));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }
}
