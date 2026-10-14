package com.smartstock.app.service;

import com.smartstock.app.dto.InvestmentSimulationRequest;
import com.smartstock.app.dto.InvestmentSimulationResponse;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class InvestmentSimulationService {

    private static final MathContext CALCULATION_CONTEXT = MathContext.DECIMAL128;
    private static final int MONEY_SCALE = 2;
    private static final RoundingMode MONEY_ROUNDING = RoundingMode.HALF_EVEN;
    private static final BigDecimal ONE = BigDecimal.ONE;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    /**
     * Converts the effective annual return to a monthly factor using Math.pow's
     * double precision, then performs monetary arithmetic with DECIMAL128 and
     * rounds all reported currency values to cents using HALF_EVEN.
     */
    public InvestmentSimulationResponse calculate(InvestmentSimulationRequest request) {
        BigDecimal initial = request.initialInvestment();
        BigDecimal contribution = request.monthlyContribution() == null
                ? BigDecimal.ZERO : request.monthlyContribution();
        BigDecimal annualRate = request.annualReturnPercent().divide(HUNDRED, CALCULATION_CONTEXT);
        BigDecimal monthlyGrowth = monthlyGrowthFactor(annualRate);
        BigDecimal monthlyRate = monthlyGrowth.subtract(ONE);
        int duration = request.durationMonths();

        List<InvestmentSimulationResponse.TimelinePoint> timeline = new ArrayList<>(duration + 1);
        for (int month = 0; month <= duration; month++) {
            BigDecimal cumulativeContributions = initial.add(contribution.multiply(BigDecimal.valueOf(month)));
            BigDecimal projectedValue = projectedValue(initial, contribution, monthlyGrowth, monthlyRate, month);
            BigDecimal roundedContributions = money(cumulativeContributions);
            BigDecimal roundedValue = money(projectedValue);
            timeline.add(new InvestmentSimulationResponse.TimelinePoint(
                    month,
                    roundedContributions,
                    roundedValue,
                    money(roundedValue.subtract(roundedContributions))));
        }

        InvestmentSimulationResponse.TimelinePoint finalPoint = timeline.get(duration);
        return new InvestmentSimulationResponse(
                normalizeOptional(request.scenarioName()),
                normalizeSymbol(request.stockSymbol()),
                duration,
                request.annualReturnPercent().stripTrailingZeros(),
                money(initial),
                money(contribution),
                finalPoint.projectedValue(),
                finalPoint.cumulativeContributions(),
                finalPoint.hypotheticalGainLoss(),
                List.copyOf(timeline));
    }

    private BigDecimal monthlyGrowthFactor(BigDecimal annualRate) {
        if (annualRate.compareTo(ONE.negate()) == 0) {
            return BigDecimal.ZERO;
        }
        double growth = Math.pow(ONE.add(annualRate).doubleValue(), 1.0 / 12.0);
        return BigDecimal.valueOf(growth);
    }

    private BigDecimal projectedValue(
            BigDecimal initial,
            BigDecimal contribution,
            BigDecimal monthlyGrowth,
            BigDecimal monthlyRate,
            int month) {
        BigDecimal initialValue = initial.multiply(monthlyGrowth.pow(month, CALCULATION_CONTEXT),
                CALCULATION_CONTEXT);
        BigDecimal contributionValue;
        if (month == 0 || contribution.signum() == 0) {
            contributionValue = BigDecimal.ZERO;
        } else if (monthlyRate.signum() == 0) {
            contributionValue = contribution.multiply(BigDecimal.valueOf(month));
        } else {
            BigDecimal accumulationFactor = monthlyGrowth.pow(month, CALCULATION_CONTEXT)
                    .subtract(ONE)
                    .divide(monthlyRate, CALCULATION_CONTEXT);
            contributionValue = contribution.multiply(accumulationFactor, CALCULATION_CONTEXT);
        }
        return initialValue.add(contributionValue, CALCULATION_CONTEXT);
    }

    private BigDecimal money(BigDecimal amount) {
        return amount.setScale(MONEY_SCALE, MONEY_ROUNDING);
    }

    private String normalizeSymbol(String symbol) {
        String normalized = normalizeOptional(symbol);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    private String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
