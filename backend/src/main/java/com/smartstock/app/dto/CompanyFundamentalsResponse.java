package com.smartstock.app.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record CompanyFundamentalsResponse(
        String symbol,
        String companyName,
        String exchange,
        String sector,
        String currency,
        BigDecimal marketCapitalization,
        BigDecimal peRatio,
        BigDecimal earningsPerShare,
        BigDecimal priceToBookRatio,
        BigDecimal dividendYield,
        BigDecimal revenue,
        BigDecimal netIncome,
        BigDecimal profitMargin,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
        LocalDate fiscalPeriodEnding,
        String source,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Instant retrievedAt,
        boolean cached
) {}
