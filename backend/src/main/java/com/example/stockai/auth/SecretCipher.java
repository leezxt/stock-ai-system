package com.example.stockai.auth;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.MessageDigest;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SecretCipher {
    private static final String PREFIX = "enc:v1:";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final byte[] key;

    public SecretCipher(@Value("${stockai.secrets.encryption-key:${STOCKAI_SECRETS_ENCRYPTION_KEY:${STOCKAI_AUTH_SECRET:}}}") String configuredKey) {
        this.key = decodeKey(configuredKey);
    }

    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) {
            return "";
        }
        if (plaintext.startsWith(PREFIX)) {
            return plaintext;
        }
        requireConfigured();
        try {
            byte[] nonce = new byte[12];
            RANDOM.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(nonce) + ":"
                + Base64.getUrlEncoder().withoutPadding().encodeToString(encrypted);
        } catch (Exception ex) {
            throw new IllegalStateException("cannot encrypt account secret", ex);
        }
    }

    public String decrypt(String stored) {
        if (stored == null || stored.isBlank()) {
            return "";
        }
        if (!stored.startsWith(PREFIX)) {
            return stored.trim();
        }
        requireConfigured();
        try {
            String[] parts = stored.split(":", 4);
            if (parts.length != 4) {
                throw new IllegalArgumentException("invalid encrypted secret format");
            }
            byte[] nonce = Base64.getUrlDecoder().decode(parts[2]);
            byte[] encrypted = Base64.getUrlDecoder().decode(parts[3]);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalStateException("cannot decrypt account secret", ex);
        }
    }

    public boolean isEncrypted(String value) {
        return value != null && value.startsWith(PREFIX);
    }

    private void requireConfigured() {
        if (key == null) {
            throw new IllegalStateException("STOCKAI_SECRETS_ENCRYPTION_KEY must be configured before storing account API keys");
        }
    }

    private static byte[] decodeKey(String configured) {
        if (configured == null || configured.isBlank()) {
            return null;
        }
        String trimmed = configured.trim();
        try {
            byte[] decoded = Base64.getDecoder().decode(trimmed);
            if (decoded.length == 32) {
                return decoded;
            }
        } catch (IllegalArgumentException ignored) {
        }
        byte[] raw = trimmed.getBytes(StandardCharsets.UTF_8);
        if (raw.length < 16) {
            throw new IllegalStateException("STOCKAI_SECRETS_ENCRYPTION_KEY must contain at least 16 bytes or be a base64-encoded 32-byte key");
        }
        try {
            return MessageDigest.getInstance("SHA-256").digest(("stockai-secrets-v1:" + trimmed).getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new IllegalStateException("cannot derive encryption key", ex);
        }
    }
}
