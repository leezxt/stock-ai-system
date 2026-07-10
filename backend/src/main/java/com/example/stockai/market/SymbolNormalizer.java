package com.example.stockai.market;

public final class SymbolNormalizer {
    private SymbolNormalizer() {
    }

    public static String normalize(Market market, String symbol) {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("symbol is required");
        }

        String value = symbol.trim().toUpperCase();
        if (market == Market.TW && value.matches("\\d{4,6}(\\.TW)?")) {
            return value.replace(".TW", "") + ".TW";
        }
        if (market == Market.US && value.matches("[A-Z.]{1,10}")) {
            return value;
        }
        throw new IllegalArgumentException("unsupported symbol: " + symbol);
    }
}
