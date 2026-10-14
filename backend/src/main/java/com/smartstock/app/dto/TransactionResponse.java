package com.smartstock.app.dto;

import com.smartstock.app.domain.TradeType;
import com.smartstock.app.domain.TransactionStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TransactionResponse(
        Long id,
        String symbol,
        TradeType tradeType,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal totalAmount,
        BigDecimal realizedProfitLoss,
        TransactionStatus status,
        LocalDateTime executedAt
) {}
