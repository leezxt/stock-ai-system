package com.example.stockai.stock;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.example.stockai.common.AtomicFileWriter;
import com.example.stockai.common.UserScopedFileLocator;
import com.example.stockai.market.Market;

@Service
class WatchlistService {
    private final Path file;
    private final JdbcTemplate jdbcTemplate;
    private final Map<String, WatchlistItem> items = new LinkedHashMap<>();

    WatchlistService() {
        this(Path.of(System.getProperty("stockai.watchlist.file", "data/watchlist.txt")), null);
    }

    @Autowired
    WatchlistService(Optional<JdbcTemplate> jdbcTemplate) {
        this(Path.of(System.getProperty("stockai.watchlist.file", "data/watchlist.txt")), jdbcTemplate.orElse(null));
    }

    WatchlistService(Path file) {
        this(file, null);
    }

    private WatchlistService(Path file, JdbcTemplate jdbcTemplate) {
        this.file = file;
        this.jdbcTemplate = jdbcTemplate;
        if (jdbcTemplate == null) {
            load();
        }
    }

    synchronized List<WatchlistItem> list() {
        if (jdbcTemplate != null) {
            return listForEmail("");
        }
        return List.copyOf(items.values());
    }

    synchronized void add(WatchlistItem item) {
        if (jdbcTemplate != null) {
            addForEmail("", item);
            return;
        }
        items.put(key(item.market(), item.symbol()), item);
        save();
    }

    synchronized void delete(Market market, String symbol) {
        if (jdbcTemplate != null) {
            deleteForEmail("", market, symbol);
            return;
        }
        items.remove(key(market, symbol));
        save();
    }

    synchronized List<WatchlistItem> list(String userEmail) {
        if (jdbcTemplate != null) {
            return listForEmail(userEmail);
        }
        return listFor(fileForUserLoad(userEmail));
    }

    synchronized void add(String userEmail, WatchlistItem item) {
        if (jdbcTemplate != null) {
            addForEmail(userEmail, item);
            return;
        }
        Map<String, WatchlistItem> scoped = mapFor(fileForUserLoad(userEmail));
        scoped.put(key(item.market(), item.symbol()), item);
        save(fileForUserSave(userEmail), scoped);
    }

    synchronized void delete(String userEmail, Market market, String symbol) {
        if (jdbcTemplate != null) {
            deleteForEmail(userEmail, market, symbol);
            return;
        }
        Map<String, WatchlistItem> scoped = mapFor(fileForUserLoad(userEmail));
        scoped.remove(key(market, symbol));
        save(fileForUserSave(userEmail), scoped);
    }

    synchronized boolean contains(String userEmail, Market market, String symbol) {
        String normalized = symbol == null ? "" : symbol.trim().toUpperCase();
        if (jdbcTemplate != null) {
            Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM stockai_watchlist
                WHERE email = ? AND market = ? AND symbol = ?
                """, Integer.class, normalizeEmail(userEmail), market.name(), normalized);
            return count != null && count > 0;
        }
        return listFor(fileForUserLoad(userEmail)).stream()
            .anyMatch(item -> item.market() == market && item.symbol().equalsIgnoreCase(normalized));
    }

    private List<WatchlistItem> listForEmail(String userEmail) {
        List<WatchlistItem> rows = jdbcTemplate.query("""
            SELECT market, symbol
            FROM stockai_watchlist
            WHERE email = ?
            ORDER BY created_at, market, symbol
            """, (rs, rowNum) -> new WatchlistItem(Market.valueOf(rs.getString("market")), rs.getString("symbol")), normalizeEmail(userEmail));
        if (rows.isEmpty()) {
            addForEmail(userEmail, new WatchlistItem(Market.US, "AAPL"));
            addForEmail(userEmail, new WatchlistItem(Market.TW, "2330.TW"));
            return jdbcTemplate.query("""
                SELECT market, symbol
                FROM stockai_watchlist
                WHERE email = ?
                ORDER BY created_at, market, symbol
                """, (rs, rowNum) -> new WatchlistItem(Market.valueOf(rs.getString("market")), rs.getString("symbol")), normalizeEmail(userEmail));
        }
        return rows;
    }

    private void addForEmail(String userEmail, WatchlistItem item) {
        jdbcTemplate.update("""
            INSERT INTO stockai_watchlist (email, market, symbol)
            VALUES (?, ?, ?)
            ON CONFLICT (email, market, symbol) DO NOTHING
            """, normalizeEmail(userEmail), item.market().name(), item.symbol());
    }

    private void deleteForEmail(String userEmail, Market market, String symbol) {
        jdbcTemplate.update("""
            DELETE FROM stockai_watchlist
            WHERE email = ? AND market = ? AND symbol = ?
            """, normalizeEmail(userEmail), market.name(), symbol);
    }

    private void load() {
        items.clear();
        if (Files.exists(file)) {
            try {
                for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                    String[] parts = line.split(":", 2);
                    if (parts.length == 2) {
                        items.put(line, new WatchlistItem(Market.valueOf(parts[0]), parts[1]));
                    }
                }
                return;
            } catch (IOException | IllegalArgumentException ex) {
                throw new IllegalStateException("cannot load watchlist: " + file, ex);
            }
        }
        items.put("US:AAPL", new WatchlistItem(Market.US, "AAPL"));
        items.put("TW:2330.TW", new WatchlistItem(Market.TW, "2330.TW"));
        save();
    }

    private void save() {
        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            List<String> lines = items.values().stream()
                .map(item -> key(item.market(), item.symbol()))
                .toList();
            AtomicFileWriter.writeLines(file, lines);
        } catch (IOException ex) {
            throw new IllegalStateException("cannot save watchlist: " + file, ex);
        }
    }

    private List<WatchlistItem> listFor(Path target) {
        return List.copyOf(mapFor(target).values());
    }

    private Map<String, WatchlistItem> mapFor(Path target) {
        Map<String, WatchlistItem> scoped = new LinkedHashMap<>();
        if (Files.exists(target)) {
            try {
                for (String line : Files.readAllLines(target, StandardCharsets.UTF_8)) {
                    String[] parts = line.split(":", 2);
                    if (parts.length == 2) {
                        scoped.put(line, new WatchlistItem(Market.valueOf(parts[0]), parts[1]));
                    }
                }
            } catch (IOException | IllegalArgumentException ex) {
                throw new IllegalStateException("cannot load watchlist: " + target, ex);
            }
        }
        if (scoped.isEmpty()) {
            scoped.put("US:AAPL", new WatchlistItem(Market.US, "AAPL"));
            scoped.put("TW:2330.TW", new WatchlistItem(Market.TW, "2330.TW"));
            save(target, scoped);
        }
        return scoped;
    }

    private void save(Path target, Map<String, WatchlistItem> scoped) {
        try {
            Path parent = target.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            List<String> lines = scoped.values().stream()
                .map(item -> key(item.market(), item.symbol()))
                .toList();
            AtomicFileWriter.writeLines(target, lines);
        } catch (IOException ex) {
            throw new IllegalStateException("cannot save watchlist: " + target, ex);
        }
    }

    private Path fileForUserLoad(String userEmail) {
        Path current = fileForUserSave(userEmail);
        if (Files.exists(current)) {
            return current;
        }
        Path parent = file.getParent() == null ? Path.of("data") : file.getParent();
        Path legacy = UserScopedFileLocator.resolveLegacy(parent, "watchlist-", ".txt", userEmail);
        return Files.exists(legacy) ? legacy : current;
    }

    private Path fileForUserSave(String userEmail) {
        Path parent = file.getParent() == null ? Path.of("data") : file.getParent();
        return UserScopedFileLocator.resolve(parent, "watchlist-", ".txt", userEmail);
    }

    private static String key(Market market, String symbol) {
        return market + ":" + symbol;
    }

    private static String normalizeEmail(String userEmail) {
        return userEmail == null ? "" : userEmail.trim().toLowerCase();
    }
}
