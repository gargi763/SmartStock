package com.smartstock.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdatePortfolioNameRequest(
        @NotBlank(message = "Portfolio name is required")
        @Size(max = 80, message = "Portfolio name must be at most 80 characters")
        String name
) {}
