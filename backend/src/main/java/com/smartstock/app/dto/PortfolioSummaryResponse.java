package com.smartstock.app.dto;

import java.math.BigDecimal;
import java.util.List;

public record PortfolioSummaryResponse(
        Long id,
        String name,
        BigDecimal cashBalance,
        BigDecimal totalValue,
        BigDecimal totalProfitLoss,
        BigDecimal realizedProfitLoss,
        BigDecimal unrealizedProfitLoss,
        List<HoldingResponse> holdings,
        List<TransactionResponse> transactions
) {}
