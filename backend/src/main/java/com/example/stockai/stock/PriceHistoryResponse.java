package com.example.stockai.stock;

import java.math.BigDecimal;
import java.util.List;

public record PriceHistoryResponse(List<PricePoint> prices) {
    public record PricePoint(String date, BigDecimal close) {}
}
