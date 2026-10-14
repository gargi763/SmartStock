package com.smartstock.app.dto;

import java.math.BigDecimal;
import java.util.List;

public record InvestmentSimulationResponse(
        String scenarioName,
        String stockSymbol,
        int durationMonths,
        BigDecimal annualReturnPercent,
        BigDecimal initialInvestment,
        BigDecimal monthlyContribution,
        BigDecimal projectedEndingValue,
        BigDecimal totalContributions,
        BigDecimal hypotheticalGainLoss,
        List<TimelinePoint> timeline
) {
    public record TimelinePoint(
            int month,
            BigDecimal cumulativeContributions,
            BigDecimal projectedValue,
            BigDecimal hypotheticalGainLoss
    ) {}
}
