package com.smartstock.app.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record HistoricalPriceHistoryResponse(
        String symbol,
        String range,
        List<HistoricalPricePointResponse> prices,
        String source,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
        LocalDate dataAsOf,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Instant retrievedAt,
        boolean cached
) {}
