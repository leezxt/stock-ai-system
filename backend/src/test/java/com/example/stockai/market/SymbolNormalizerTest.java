package com.example.stockai.market;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SymbolNormalizerTest {
    @Test
    void normalizesUsSymbols() {
        assertThat(SymbolNormalizer.normalize(Market.US, "aapl")).isEqualTo("AAPL");
    }

    @Test
    void normalizesTwSymbols() {
        assertThat(SymbolNormalizer.normalize(Market.TW, "2330")).isEqualTo("2330.TW");
        assertThat(SymbolNormalizer.normalize(Market.TW, "2330.tw")).isEqualTo("2330.TW");
        assertThat(SymbolNormalizer.normalize(Market.TW, "009819")).isEqualTo("009819.TW");
        assertThat(SymbolNormalizer.normalize(Market.TW, "009819.tw")).isEqualTo("009819.TW");
    }

    @Test
    void rejectsUnsupportedSymbols() {
        assertThatThrownBy(() -> SymbolNormalizer.normalize(Market.TW, "AAPL"))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
