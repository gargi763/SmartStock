package com.smartstock.app.dto;

import java.math.BigDecimal;

public record StockResponse(
        Long id,
        String symbol,
        String companyName,
        BigDecimal currentPrice,
        BigDecimal dayChange,
        BigDecimal dayChangePercent,
        String marketSource,
        String sector
) {}
