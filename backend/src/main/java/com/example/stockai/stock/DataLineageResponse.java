package com.example.stockai.stock;

import java.time.Instant;
import java.util.List;

import com.example.stockai.market.Market;

/**
 * Provenance and integrity details for the exact price series used by the
 * chart and derived indicators.
 */
public record DataLineageResponse(
    String symbol,
    Market market,
    String source,
    String adjustmentStatus,
    Instant observedAt,
    int pricePointCount,
    String dataFrom,
    String dataTo,
    List<String> priceSources,
    String integrityStatus,
    List<String> findings,
    List<CorporateAction> corporateActions,
    MarketDataQuality technicalQuality,
    MarketDataQuality predictionQuality
) {
    public DataLineageResponse {
        source = source == null || source.isBlank() ? "unknown" : source;
        adjustmentStatus = adjustmentStatus == null || adjustmentStatus.isBlank() ? "UNVERIFIED" : adjustmentStatus;
        priceSources = priceSources == null ? List.of() : List.copyOf(priceSources);
        findings = findings == null ? List.of() : List.copyOf(findings);
        corporateActions = corporateActions == null ? List.of() : List.copyOf(corporateActions);
        integrityStatus = integrityStatus == null || integrityStatus.isBlank() ? "UNKNOWN" : integrityStatus;
    }
}
