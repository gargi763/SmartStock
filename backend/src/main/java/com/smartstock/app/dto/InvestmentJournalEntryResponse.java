package com.smartstock.app.dto;

import com.smartstock.app.domain.TradeType;

import java.time.LocalDateTime;
import java.util.List;

public record InvestmentJournalEntryResponse(
        Long id,
        String title,
        String thesis,
        String lessonsLearned,
        List<String> tags,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        Long transactionId,
        String transactionSymbol,
        TradeType transactionType,
        LocalDateTime transactionDate
) {}
