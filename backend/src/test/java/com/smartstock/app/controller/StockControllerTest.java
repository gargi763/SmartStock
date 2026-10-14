package com.smartstock.app.controller;

import com.smartstock.app.config.GlobalExceptionHandler;
import com.smartstock.app.dto.CompanyFundamentalsResponse;
import com.smartstock.app.dto.HistoricalPriceHistoryResponse;
import com.smartstock.app.dto.HistoricalPricePointResponse;
import com.smartstock.app.exception.MarketDataException;
import com.smartstock.app.service.CompanyFundamentalsService;
import com.smartstock.app.service.HistoricalStockPriceService;
import com.smartstock.app.service.StockService;
import org.springframework.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StockControllerTest {

    private HistoricalStockPriceService historicalStockPriceService;
    private CompanyFundamentalsService companyFundamentalsService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        historicalStockPriceService = mock(HistoricalStockPriceService.class);
        companyFundamentalsService = mock(CompanyFundamentalsService.class);
        StockController controller = new StockController(
                mock(StockService.class),
                historicalStockPriceService,
                companyFundamentalsService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getHistoricalPrices_returnsPricePointsAndFreshnessMetadata() throws Exception {
        when(historicalStockPriceService.getHistory("AAPL", "1W")).thenReturn(
                new HistoricalPriceHistoryResponse(
                        "AAPL",
                        "1W",
                        List.of(new HistoricalPricePointResponse(
                                LocalDate.of(2026, 10, 12),
                                new BigDecimal("192.50"))),
                        "Alpha Vantage (daily, unadjusted)",
                        LocalDate.of(2026, 10, 12),
                        Instant.parse("2026-10-13T00:00:00Z"),
                        false));

        mockMvc.perform(get("/api/v1/stocks/AAPL/history").param("range", "1W"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("AAPL"))
                .andExpect(jsonPath("$.range").value("1W"))
                .andExpect(jsonPath("$.prices[0].timestamp").value("2026-10-12"))
                .andExpect(jsonPath("$.prices[0].close").value(192.50))
                .andExpect(jsonPath("$.source").value("Alpha Vantage (daily, unadjusted)"))
                .andExpect(jsonPath("$.dataAsOf").value("2026-10-12"))
                .andExpect(jsonPath("$.retrievedAt").value("2026-10-13T00:00:00Z"))
                .andExpect(jsonPath("$.cached").value(false));

        verify(historicalStockPriceService).getHistory("AAPL", "1W");
    }

    @Test
    void getCompanyFundamentals_returnsMetricsAndReportingMetadata() throws Exception {
        when(companyFundamentalsService.getFundamentals("AAPL")).thenReturn(
                new CompanyFundamentalsResponse(
                        "AAPL",
                        "Apple Inc",
                        "NASDAQ",
                        "Technology",
                        "USD",
                        new BigDecimal("3000000000000"),
                        new BigDecimal("31.25"),
                        new BigDecimal("6.42"),
                        new BigDecimal("48.1"),
                        new BigDecimal("0.0045"),
                        new BigDecimal("400000000000"),
                        new BigDecimal("100000000000"),
                        new BigDecimal("0.2531"),
                        LocalDate.of(2025, 9, 27),
                        "Alpha Vantage",
                        Instant.parse("2026-10-13T00:00:00Z"),
                        false));

        mockMvc.perform(get("/api/v1/stocks/AAPL/fundamentals"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("AAPL"))
                .andExpect(jsonPath("$.companyName").value("Apple Inc"))
                .andExpect(jsonPath("$.exchange").value("NASDAQ"))
                .andExpect(jsonPath("$.marketCapitalization").value(3000000000000d))
                .andExpect(jsonPath("$.revenue").value(400000000000d))
                .andExpect(jsonPath("$.netIncome").value(100000000000d))
                .andExpect(jsonPath("$.fiscalPeriodEnding").value("2025-09-27"))
                .andExpect(jsonPath("$.source").value("Alpha Vantage"))
                .andExpect(jsonPath("$.retrievedAt").value("2026-10-13T00:00:00Z"))
                .andExpect(jsonPath("$.cached").value(false));

        verify(companyFundamentalsService).getFundamentals("AAPL");
    }

    @Test
    void getCompanyFundamentals_returnsProviderRateLimitAsApiError() throws Exception {
        when(companyFundamentalsService.getFundamentals("AAPL"))
                .thenThrow(new MarketDataException(HttpStatus.TOO_MANY_REQUESTS, "Provider limit reached."));

        mockMvc.perform(get("/api/v1/stocks/AAPL/fundamentals"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value("Provider limit reached."))
                .andExpect(jsonPath("$.path").value("/api/v1/stocks/AAPL/fundamentals"));
    }
}
