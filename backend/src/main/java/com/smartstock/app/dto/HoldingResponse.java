package com.smartstock.app.dto;

import java.math.BigDecimal;

public record HoldingResponse(
        Long id,
        String symbol,
        String companyName,
        BigDecimal quantity,
        BigDecimal averageCost,
        BigDecimal currentPrice,
        BigDecimal marketValue,
        BigDecimal unrealizedProfitLoss
) {}
