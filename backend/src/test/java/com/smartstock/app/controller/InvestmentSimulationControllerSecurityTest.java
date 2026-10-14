package com.smartstock.app.controller;

import com.smartstock.app.config.SecurityConfig;
import com.smartstock.app.dto.InvestmentSimulationResponse;
import com.smartstock.app.service.InvestmentSimulationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InvestmentSimulationController.class)
@Import(SecurityConfig.class)
class InvestmentSimulationControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private InvestmentSimulationService simulationService;

    @Test
    void calculate_rejectsUnauthenticatedRequests() throws Exception {
        mockMvc.perform(post("/api/v1/simulations/calculate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "alice")
    void calculateReturnsServiceProjectionForAuthenticatedUser() throws Exception {
        var finalPoint = new InvestmentSimulationResponse.TimelinePoint(
                1, new BigDecimal("100.00"), new BigDecimal("110.00"), new BigDecimal("10.00"));
        when(simulationService.calculate(any())).thenReturn(new InvestmentSimulationResponse(
                null, null, 1, BigDecimal.TEN, BigDecimal.valueOf(100), BigDecimal.ZERO,
                new BigDecimal("110.00"), new BigDecimal("100.00"), new BigDecimal("10.00"),
                List.of(finalPoint)));

        mockMvc.perform(post("/api/v1/simulations/calculate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectedEndingValue").value(110.00))
                .andExpect(jsonPath("$.timeline[0].month").value(1));

        verify(simulationService).calculate(any());
    }

    @Test
    @WithMockUser
    void calculateRejectsInvalidAmountsDurationsAndSymbols() throws Exception {
        mockMvc.perform(post("/api/v1/simulations/calculate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"initialInvestment":-1,"monthlyContribution":0,"durationMonths":601,
                                 "annualReturnPercent":101,"stockSymbol":"invalid symbol"}
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/simulations/calculate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"initialInvestment":0,"monthlyContribution":0,"durationMonths":12,
                                 "annualReturnPercent":0}
                                """))
                .andExpect(status().isBadRequest());
    }

    private String validRequest() {
        return """
                {"initialInvestment":100,"durationMonths":1,"annualReturnPercent":10}
                """;
    }
}
