package com.smartstock.app.dto;

import com.smartstock.app.domain.TradeType;

import java.time.LocalDateTime;

public record InvestmentJournalTradeResponse(
        Long id,
        String symbol,
        TradeType tradeType,
        LocalDateTime executedAt
) {}
