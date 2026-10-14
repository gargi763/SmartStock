package com.smartstock.app.dto;

import java.math.BigDecimal;

public record WatchlistItemResponse(
        Long id,
        String symbol,
        String companyName,
        String sector,
        BigDecimal currentPrice,
        BigDecimal dayChange,
        BigDecimal dayChangePercent,
        String marketSource
) {}
