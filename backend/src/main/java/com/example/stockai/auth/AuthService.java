package com.example.stockai.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@Service
public class AuthService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64D = Base64.getUrlDecoder();

    private final UserStore userStore;
    private final byte[] secret;
    private final long ttlSeconds;

    public AuthService(
        UserStore userStore,
        @Value("${stockai.auth.secret:stock-ai-local-dev-secret}") String secret,
        @Value("${stockai.auth.token-ttl-seconds:604800}") long ttlSeconds
    ) {
        this.userStore = userStore;
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.ttlSeconds = ttlSeconds;
    }

    public AuthResult register(String email, String password) {
        String normalized = normalizeEmail(email);
        validatePassword(password);
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        String saltText = B64.encodeToString(salt);
        String hash = hash(password, salt);
        UserRecord user = userStore.create(normalized, saltText, hash);
        return issue(user.email());
    }

    public AuthResult login(String email, String password) {
        UserRecord user = userStore.find(normalizeEmail(email))
            .orElseThrow(() -> new ResponseStatusException(UNAUTHORIZED, "invalid credentials"));
        if (!"LOCAL".equalsIgnoreCase(user.authProvider()) || user.passwordHash().isBlank()) {
            throw new ResponseStatusException(UNAUTHORIZED, "use google login for this account");
        }
        String actual = hash(password, B64D.decode(user.passwordSalt()));
        if (!MessageDigest.isEqual(actual.getBytes(StandardCharsets.UTF_8), user.passwordHash().getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(UNAUTHORIZED, "invalid credentials");
        }
        return issue(user.email());
    }

    public AuthResult loginGoogleVerifiedEmail(String email) {
        String normalized = normalizeEmail(email);
        UserRecord user = userStore.findOrCreateGoogle(normalized);
        return issue(user.email());
    }

    public AuthUser requireUser(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            throw new ResponseStatusException(UNAUTHORIZED, "missing bearer token");
        }
        String token = authorizationHeader.substring("Bearer ".length()).trim();
        String[] parts = token.split("\\.");
        if (parts.length != 2) {
            throw new ResponseStatusException(UNAUTHORIZED, "invalid token");
        }
        String payload = new String(B64D.decode(parts[0]), StandardCharsets.UTF_8);
        String expectedSignature = sign(parts[0]);
        if (!MessageDigest.isEqual(expectedSignature.getBytes(StandardCharsets.UTF_8), parts[1].getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(UNAUTHORIZED, "invalid token");
        }
        String[] fields = payload.split("\\|");
        if (fields.length != 2) {
            throw new ResponseStatusException(UNAUTHORIZED, "invalid token");
        }
        long expiresAt = Long.parseLong(fields[1]);
        if (Instant.now().getEpochSecond() > expiresAt) {
            throw new ResponseStatusException(UNAUTHORIZED, "token expired");
        }
        UserRecord user = userStore.find(fields[0]).orElseThrow(() -> new ResponseStatusException(UNAUTHORIZED, "user not found"));
        return new AuthUser(user.email(), user.createdAt(), user.authProvider());
    }

    private AuthResult issue(String email) {
        long expiresAt = Instant.now().plusSeconds(ttlSeconds).getEpochSecond();
        String payload = B64.encodeToString((email + "|" + expiresAt).getBytes(StandardCharsets.UTF_8));
        String signature = sign(payload);
        UserRecord user = userStore.find(email).orElseThrow();
        return new AuthResult(new AuthUser(email, user.createdAt(), user.authProvider()), payload + "." + signature, expiresAt);
    }

    private String sign(String payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(secret);
            digest.update(payload.getBytes(StandardCharsets.UTF_8));
            return B64.encodeToString(digest.digest());
        } catch (Exception ex) {
            throw new IllegalStateException("cannot sign token", ex);
        }
    }

    private static String hash(String password, byte[] salt) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, 120_000, 256);
            byte[] bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            return B64.encodeToString(bytes);
        } catch (Exception ex) {
            throw new IllegalStateException("cannot hash password", ex);
        }
    }

    private static String normalizeEmail(String email) {
        if (email == null || email.isBlank() || !email.contains("@")) {
            throw new ResponseStatusException(BAD_REQUEST, "email is invalid");
        }
        return email.trim().toLowerCase();
    }

    private static void validatePassword(String password) {
        if (password == null || password.length() < 6) {
            throw new ResponseStatusException(BAD_REQUEST, "password must be at least 6 characters");
        }
    }

    public record AuthUser(String email, Instant createdAt, String authProvider) {}
    public record AuthResult(AuthUser user, String token, long expiresAt) {}
}
