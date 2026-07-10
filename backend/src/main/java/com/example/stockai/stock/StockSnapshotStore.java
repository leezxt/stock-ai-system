package com.example.stockai.stock;

import java.math.BigDecimal;
import java.sql.Array;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
class StockSnapshotStore {
    private final JdbcTemplate jdbcTemplate;

    StockSnapshotStore() {
        this.jdbcTemplate = null;
    }

    @Autowired
    StockSnapshotStore(Optional<JdbcTemplate> jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate.orElse(null);
    }

    void save(StockRecord stock) {
        if (jdbcTemplate == null || stock == null) {
            return;
        }
        jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
            Array prices = connection.createArrayOf("numeric", stock.prices().toArray(BigDecimal[]::new));
            try (var statement = connection.prepareStatement("""
                INSERT INTO stockai_stock_snapshots (
                    market, symbol, name, currency, last_price, change_percent, prices, source, captured_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, now())
                ON CONFLICT (market, symbol) DO UPDATE SET
                    name = EXCLUDED.name,
                    currency = EXCLUDED.currency,
                    last_price = EXCLUDED.last_price,
                    change_percent = EXCLUDED.change_percent,
                    prices = EXCLUDED.prices,
                    source = EXCLUDED.source,
                    captured_at = now()
                """)) {
                statement.setString(1, stock.market().name());
                statement.setString(2, stock.symbol());
                statement.setString(3, stock.name());
                statement.setString(4, stock.currency());
                statement.setBigDecimal(5, stock.lastPrice());
                statement.setBigDecimal(6, stock.changePercent());
                statement.setArray(7, prices);
                statement.setString(8, stock.source());
                statement.executeUpdate();
                return null;
            } finally {
                prices.free();
            }
        });
    }
}
