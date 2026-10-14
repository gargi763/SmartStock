package com.smartstock.app.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record InvestmentSimulationRequest(
        @NotNull(message = "Initial investment is required")
        @DecimalMin(value = "0.00", message = "Initial investment must be nonnegative")
        @Digits(integer = 12, fraction = 2, message = "Initial investment must have at most 12 integer and 2 fractional digits")
        BigDecimal initialInvestment,

        @DecimalMin(value = "0.00", message = "Monthly contribution must be nonnegative")
        @Digits(integer = 12, fraction = 2, message = "Monthly contribution must have at most 12 integer and 2 fractional digits")
        BigDecimal monthlyContribution,

        @NotNull(message = "Duration is required")
        @Min(value = 1, message = "Duration must be between 1 and 600 months")
        @Max(value = 600, message = "Duration must be between 1 and 600 months")
        Integer durationMonths,

        @NotNull(message = "Annual return is required")
        @DecimalMin(value = "-100.0000", message = "Annual return must be between -100% and 100%")
        @DecimalMax(value = "100.0000", message = "Annual return must be between -100% and 100%")
        @Digits(integer = 3, fraction = 4, message = "Annual return must have at most 3 integer and 4 fractional digits")
        BigDecimal annualReturnPercent,

        @Pattern(regexp = "(?:[A-Za-z0-9][A-Za-z0-9.-]{0,9})?",
                message = "Stock symbol must be 1-10 letters, digits, dots, or hyphens")
        String stockSymbol,

        @Size(max = 60, message = "Scenario name must be at most 60 characters")
        String scenarioName
) {
    @AssertTrue(message = "Initial investment and monthly contribution cannot both be zero")
    public boolean isContributionPositive() {
        return initialInvestment != null
                && (initialInvestment.signum() > 0
                || (monthlyContribution != null && monthlyContribution.signum() > 0));
    }
}
