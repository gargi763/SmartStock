package com.smartstock.app.service;

import com.smartstock.app.dto.InvestmentSimulationRequest;
import com.smartstock.app.dto.InvestmentSimulationResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class InvestmentSimulationServiceTest {

    private final InvestmentSimulationService service = new InvestmentSimulationService();

    @Test
    void calculate_zeroReturnWithInitialAndMonthlyContributions() {
        InvestmentSimulationResponse response = service.calculate(
                request("1000", "100", 12, "0", null, null));

        assertThat(response.projectedEndingValue()).isEqualByComparingTo("2200.00");
        assertThat(response.totalContributions()).isEqualByComparingTo("2200.00");
        assertThat(response.hypotheticalGainLoss()).isEqualByComparingTo("0.00");
        assertThat(response.timeline()).hasSize(13);
        assertThat(response.timeline().get(6).projectedValue()).isEqualByComparingTo("1600.00");
    }

    @Test
    void calculate_positiveReturnUsesEffectiveMonthlyRateAndEndOfMonthContributions() {
        InvestmentSimulationResponse response = service.calculate(
                request("1000", "100", 12, "12", "aapl", "One year"));

        assertThat(response.projectedEndingValue()).isEqualByComparingTo("2384.65");
        assertThat(response.totalContributions()).isEqualByComparingTo("2200.00");
        assertThat(response.hypotheticalGainLoss()).isEqualByComparingTo("184.65");
        assertThat(response.stockSymbol()).isEqualTo("AAPL");
        assertThat(response.scenarioName()).isEqualTo("One year");
        assertThat(response.timeline().get(12).projectedValue())
                .isEqualTo(response.projectedEndingValue());
        assertThat(response.timeline().get(12).cumulativeContributions())
                .isEqualTo(response.totalContributions());
    }

    @Test
    void calculate_negativeReturnProducesHypotheticalLoss() {
        InvestmentSimulationResponse response = service.calculate(
                request("1000", "0", 12, "-12", null, null));

        assertThat(response.projectedEndingValue()).isLessThan(new BigDecimal("1000.00"));
        assertThat(response.hypotheticalGainLoss()).isNegative();
    }

    @Test
    void calculate_minusOneHundredPercentReturnKeepsOnlyFinalMonthContribution() {
        InvestmentSimulationResponse response = service.calculate(
                request("1000", "100", 3, "-100", null, null));

        assertThat(response.projectedEndingValue()).isEqualByComparingTo("100.00");
        assertThat(response.totalContributions()).isEqualByComparingTo("1300.00");
        assertThat(response.timeline().get(1).projectedValue()).isEqualByComparingTo("100.00");
    }

    @Test
    void calculateRoundsCurrencyToCentsUsingHalfEven() {
        InvestmentSimulationResponse response = service.calculate(
                request("1.005", "0", 1, "0", null, null));

        assertThat(response.projectedEndingValue()).isEqualByComparingTo("1.00");
        assertThat(response.initialInvestment().scale()).isEqualTo(2);
    }

    private InvestmentSimulationRequest request(
            String initial,
            String monthly,
            int months,
            String annualReturnPercent,
            String symbol,
            String scenarioName) {
        return new InvestmentSimulationRequest(
                new BigDecimal(initial),
                monthly == null ? null : new BigDecimal(monthly),
                months,
                new BigDecimal(annualReturnPercent),
                symbol,
                scenarioName);
    }
}
