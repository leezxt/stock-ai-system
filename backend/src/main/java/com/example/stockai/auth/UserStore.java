package com.example.stockai.auth;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.example.stockai.common.AtomicFileWriter;

import static org.springframework.http.HttpStatus.CONFLICT;

@Service
public class UserStore {
    private final Path file;
    private final JdbcTemplate jdbcTemplate;
    private final Map<String, UserRecord> users = new LinkedHashMap<>();

    public UserStore() {
        this(Path.of(System.getProperty("stockai.users.file", "data/users.txt")), null);
    }

    @Autowired
    public UserStore(Optional<JdbcTemplate> jdbcTemplate) {
        this(Path.of(System.getProperty("stockai.users.file", "data/users.txt")), jdbcTemplate.orElse(null));
    }

    public UserStore(Path file) {
        this(file, null);
    }

    private UserStore(Path file, JdbcTemplate jdbcTemplate) {
        this.file = file;
        this.jdbcTemplate = jdbcTemplate;
        if (jdbcTemplate == null) {
            load();
        }
    }

    synchronized Optional<UserRecord> find(String email) {
        if (jdbcTemplate != null) {
            return jdbcTemplate.query("""
                SELECT email, password_salt, password_hash, created_at, auth_provider
                FROM stockai_users
                WHERE email = ?
                """, (rs, rowNum) -> new UserRecord(
                    rs.getString("email"),
                    rs.getString("password_salt"),
                    rs.getString("password_hash"),
                    rs.getTimestamp("created_at").toInstant(),
                    rs.getString("auth_provider")
                ), email).stream().findFirst();
        }
        return Optional.ofNullable(users.get(email));
    }

    synchronized UserRecord create(String email, String salt, String hash) {
        if (jdbcTemplate != null) {
            UserRecord user = new UserRecord(email, salt, hash, Instant.now(), "LOCAL");
            try {
                jdbcTemplate.update("""
                    INSERT INTO stockai_users (email, password_salt, password_hash, created_at, auth_provider)
                    VALUES (?, ?, ?, ?, ?)
                    """, user.email(), user.passwordSalt(), user.passwordHash(), Timestamp.from(user.createdAt()), user.authProvider());
            } catch (DuplicateKeyException ex) {
                throw new ResponseStatusException(CONFLICT, "email already registered", ex);
            }
            return user;
        }
        if (users.containsKey(email)) {
            throw new ResponseStatusException(CONFLICT, "email already registered");
        }
        UserRecord user = new UserRecord(email, salt, hash, Instant.now(), "LOCAL");
        users.put(email, user);
        save();
        return user;
    }

    synchronized UserRecord findOrCreateGoogle(String email) {
        if (jdbcTemplate != null) {
            Optional<UserRecord> existing = find(email);
            if (existing.isPresent()) {
                return existing.get();
            }
            UserRecord user = new UserRecord(email, "", "", Instant.now(), "GOOGLE");
            jdbcTemplate.update("""
                INSERT INTO stockai_users (email, password_salt, password_hash, created_at, auth_provider)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (email) DO NOTHING
                """, user.email(), user.passwordSalt(), user.passwordHash(), Timestamp.from(user.createdAt()), user.authProvider());
            return find(email).orElse(user);
        }
        UserRecord existing = users.get(email);
        if (existing != null) {
            return existing;
        }
        UserRecord user = new UserRecord(email, "", "", Instant.now(), "GOOGLE");
        users.put(email, user);
        save();
        return user;
    }

    private void load() {
        users.clear();
        if (!Files.exists(file)) {
            return;
        }
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                String[] parts = line.split("\\|", 5);
                if (parts.length == 4) {
                    users.put(parts[0], new UserRecord(parts[0], parts[1], parts[2], Instant.parse(parts[3]), "LOCAL"));
                } else if (parts.length == 5) {
                    users.put(parts[0], new UserRecord(parts[0], parts[1], parts[2], Instant.parse(parts[3]), parts[4]));
                }
            }
        } catch (IOException ex) {
            throw new IllegalStateException("cannot load users: " + file, ex);
        }
    }

    private void save() {
        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            List<String> lines = users.values().stream()
                .map(user -> String.join("|", user.email(), user.passwordSalt(), user.passwordHash(), user.createdAt().toString(), user.authProvider()))
                .toList();
            AtomicFileWriter.writeLines(file, lines);
        } catch (IOException ex) {
            throw new IllegalStateException("cannot save users: " + file, ex);
        }
    }
}
