package com.example.stockai.auth;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class GoogleTokenVerifier {
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private static final Pattern JSON_FIELD = Pattern.compile("\"([^\"]+)\"\\s*:\\s*\"([^\"]*)\"");
    private final String clientId;

    public GoogleTokenVerifier(@Value("${stockai.google.client-id:}") String clientId) {
        this.clientId = clientId == null ? "" : clientId.trim();
    }

    public GoogleProfile verify(String credential) {
        if (credential == null || credential.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "google credential is required");
        }
        try {
            String encoded = URLEncoder.encode(credential, StandardCharsets.UTF_8);
            HttpRequest request = HttpRequest.newBuilder(URI.create("https://oauth2.googleapis.com/tokeninfo?id_token=" + encoded)).GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                throw new ResponseStatusException(UNAUTHORIZED, "google token verification failed");
            }
            String body = response.body();
            String email = readJsonField(body, "email");
            boolean emailVerified = "true".equalsIgnoreCase(readJsonField(body, "email_verified"));
            String audience = readJsonField(body, "aud");
            if (email.isBlank() || !emailVerified) {
                throw new ResponseStatusException(UNAUTHORIZED, "google account email is not verified");
            }
            if (!clientId.isBlank() && !clientId.equals(audience)) {
                throw new ResponseStatusException(UNAUTHORIZED, "google audience mismatch");
            }
            return new GoogleProfile(email.trim().toLowerCase(), readJsonField(body, "name"));
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(UNAUTHORIZED, "google token verification failed", ex);
        }
    }

    private static String readJsonField(String body, String field) {
        Matcher matcher = JSON_FIELD.matcher(body);
        while (matcher.find()) {
            if (field.equals(matcher.group(1))) {
                return matcher.group(2);
            }
        }
        return "";
    }

    public record GoogleProfile(String email, String name) {}
}
