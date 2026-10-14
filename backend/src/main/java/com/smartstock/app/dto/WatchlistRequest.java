package com.smartstock.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record WatchlistRequest(
        @NotBlank(message = "Stock symbol is required")
        @Pattern(regexp = "[A-Za-z0-9.^-]{1,10}", message = "Stock symbol must contain 1 to 10 valid characters")
        String symbol
) {}
