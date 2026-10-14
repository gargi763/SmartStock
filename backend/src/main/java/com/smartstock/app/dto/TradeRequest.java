package com.smartstock.app.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record TradeRequest(
        @NotBlank(message = "Stock symbol is required")
        String symbol,

        @NotNull(message = "Quantity is required")
        @DecimalMin(value = "0", inclusive = false, message = "Quantity must be greater than zero")
        @Digits(integer = 13, fraction = 6, message = "Quantity can have at most 6 decimal places")
        BigDecimal quantity,

        // Retained for request compatibility; execution always uses the server-side stock quote.
        BigDecimal price
) {}
