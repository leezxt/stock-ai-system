package com.example.stockai.stock;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A close price together with the trading date and the provider that supplied it.
 * Keeping the date beside the value prevents callers from re-indexing prices
 * against a synthetic weekday calendar.
 */
record PriceBar(LocalDate date, BigDecimal close, String source) {
    PriceBar {
        if (date == null) {
            throw new IllegalArgumentException("date must not be null");
        }
        if (close == null) {
            throw new IllegalArgumentException("close must not be null");
        }
        source = source == null ? "" : source.trim();
    }

    PriceBar(LocalDate date, BigDecimal close) {
        this(date, close, "");
    }
}
