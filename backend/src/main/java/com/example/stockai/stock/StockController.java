package com.example.stockai.stock;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.stockai.common.ApiResponse;
import com.example.stockai.market.Market;

@RestController
@RequestMapping("/api/v1")
public class StockController {
    private final StockService stockService;

    public StockController(StockService stockService) {
        this.stockService = stockService;
    }

    @GetMapping("/markets")
    ApiResponse<List<Market>> markets() {
        return ApiResponse.of(stockService.markets());
    }

    @GetMapping("/stocks/{market}/search")
    ApiResponse<List<StockRecord>> search(@PathVariable Market market, @RequestParam(defaultValue = "") String query) {
        return ApiResponse.of(stockService.search(market, query));
    }

    @GetMapping("/stocks/{market}/{symbol}/summary")
    ApiResponse<StockSummaryResponse> summary(@PathVariable Market market, @PathVariable String symbol) {
        return ApiResponse.of(stockService.summary(market, symbol));
    }

    @GetMapping("/stocks/{market}/{symbol}/prices")
    ApiResponse<PriceHistoryResponse> prices(@PathVariable Market market, @PathVariable String symbol) {
        return ApiResponse.of(stockService.prices(market, symbol));
    }

    @GetMapping("/stocks/{market}/{symbol}/technical-summary")
    ApiResponse<TechnicalSummaryResponse> technicalSummary(@PathVariable Market market, @PathVariable String symbol) {
        return ApiResponse.of(stockService.technicalSummary(market, symbol));
    }

    @GetMapping("/stocks/{market}/{symbol}/prediction")
    ApiResponse<PredictionResponse> prediction(
        @PathVariable Market market,
        @PathVariable String symbol,
        @RequestParam(defaultValue = "5") int horizonDays
    ) {
        return ApiResponse.of(stockService.prediction(market, symbol, horizonDays));
    }
}
