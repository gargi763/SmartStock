package com.smartstock.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminStockRequest(
        @NotBlank(message = "Stock symbol is required")
        @Size(max = 12, message = "Stock symbol must be at most 12 characters")
        String symbol,
        @NotBlank(message = "Company name is required")
        @Size(max = 255, message = "Company name must be at most 255 characters")
        String companyName,
        @NotBlank(message = "Sector is required")
        @Size(max = 255, message = "Sector must be at most 255 characters")
        String sector
) {}
