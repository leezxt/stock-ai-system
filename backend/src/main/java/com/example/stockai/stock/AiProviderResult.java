package com.example.stockai.stock;

import java.math.BigDecimal;

record AiProviderResult(
    String provider,
    String trend,
    int aiScore,
    BigDecimal bullishProbability,
    String riskLevel,
    String summary,
    String source
) {}
