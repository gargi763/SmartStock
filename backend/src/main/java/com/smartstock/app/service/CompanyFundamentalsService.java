package com.smartstock.app.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartstock.app.domain.Stock;
import com.smartstock.app.dto.CompanyFundamentalsResponse;
import com.smartstock.app.exception.MarketDataException;
import com.smartstock.app.exception.ResourceNotFoundException;
import com.smartstock.app.repository.StockRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class CompanyFundamentalsService {

    private static final Logger logger = LoggerFactory.getLogger(CompanyFundamentalsService.class);
    private static final Duration CACHE_DURATION = Duration.ofHours(12);
    private static final String SOURCE = "Alpha Vantage";

    private final StockRepository stockRepository;
    private final RestClient restClient;
    private final String apiKey;
    private final Map<String, CachedFundamentals> cache = new ConcurrentHashMap<>();

    public CompanyFundamentalsService(
            StockRepository stockRepository,
            RestClient alphaVantageRestClient,
            @Value("${app.market-data.alpha-vantage.api-key:}") String apiKey) {
        this.stockRepository = stockRepository;
        this.restClient = alphaVantageRestClient;
        this.apiKey = apiKey;
    }

    public CompanyFundamentalsResponse getFundamentals(String requestedSymbol) {
        String symbol = requestedSymbol.trim().toUpperCase();
        Stock stock = stockRepository.findBySymbol(symbol)
                .orElseThrow(() -> new ResourceNotFoundException("Stock not found: " + requestedSymbol));
        if (apiKey.isBlank()) {
            throw new MarketDataException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Company fundamentals are not configured. Set SMARTSTOCK_MARKET_DATA_API_KEY in the backend environment.");
        }

        Instant now = Instant.now();
        CachedFundamentals cachedData = cache.get(stock.getSymbol());
        boolean cached = cachedData != null && cachedData.retrievedAt().plus(CACHE_DURATION).isAfter(now);
        if (!cached) {
            cachedData = fetchFundamentals(stock, now);
            cache.put(stock.getSymbol(), cachedData);
        }

        CompanyFundamentalsResponse data = cachedData.data();
        return new CompanyFundamentalsResponse(
                data.symbol(),
                data.companyName(),
                data.exchange(),
                data.sector(),
                data.currency(),
                data.marketCapitalization(),
                data.peRatio(),
                data.earningsPerShare(),
                data.priceToBookRatio(),
                data.dividendYield(),
                data.revenue(),
                data.netIncome(),
                data.profitMargin(),
                data.fiscalPeriodEnding(),
                data.source(),
                cachedData.retrievedAt(),
                cached
        );
    }

    private CachedFundamentals fetchFundamentals(Stock stock, Instant retrievedAt) {
        String symbol = stock.getSymbol();
        JsonNode overview = fetchProviderData("OVERVIEW", symbol);
        JsonNode incomeStatement = fetchProviderData("INCOME_STATEMENT", symbol);
        LatestReport latestReport = latestReport(incomeStatement);
        JsonNode report = latestReport == null ? null : latestReport.report();
        boolean planRestricted = overview == null || incomeStatement == null;

        CompanyFundamentalsResponse data = new CompanyFundamentalsResponse(
                symbol,
                firstAvailable(text(overview, "Name"), stock.getCompanyName()),
                text(overview, "Exchange"),
                firstAvailable(text(overview, "Sector"), stock.getSector()),
                firstAvailable(text(overview, "Currency"), report == null ? null : text(report, "reportedCurrency")),
                decimal(overview, "MarketCapitalization"),
                decimal(overview, "PERatio"),
                decimal(overview, "EPS"),
                decimal(overview, "PriceToBookRatio"),
                decimal(overview, "DividendYield"),
                report == null ? null : decimal(report, "totalRevenue"),
                report == null ? null : decimal(report, "netIncome"),
                decimal(overview, "ProfitMargin"),
                latestReport == null ? null : latestReport.periodEnding(),
                planRestricted ? SOURCE + " (partial; plan-restricted metrics unavailable)" : SOURCE,
                retrievedAt,
                false
        );
        return new CachedFundamentals(data, retrievedAt);
    }

    private LatestReport latestReport(JsonNode incomeStatement) {
        if (incomeStatement == null) {
            return null;
        }
        LatestReport latest = null;
        for (String reportType : new String[]{"annualReports", "quarterlyReports"}) {
            JsonNode reports = incomeStatement.path(reportType);
            if (!reports.isArray()) {
                continue;
            }
            for (JsonNode report : reports) {
                LocalDate periodEnding = parseDate(text(report, "fiscalDateEnding"));
                if (periodEnding != null && (latest == null || periodEnding.isAfter(latest.periodEnding()))) {
                    latest = new LatestReport(report, periodEnding);
                }
            }
        }
        return latest;
    }

    private JsonNode fetchProviderData(String function, String symbol) {
        JsonNode response;
        try {
            response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .queryParam("function", function)
                            .queryParam("symbol", symbol)
                            .queryParam("apikey", apiKey)
                            .build())
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException exception) {
            HttpStatus status = exception.getStatusCode().value() == HttpStatus.TOO_MANY_REQUESTS.value()
                    ? HttpStatus.TOO_MANY_REQUESTS
                    : HttpStatus.BAD_GATEWAY;
            logProviderFailure(function, status == HttpStatus.TOO_MANY_REQUESTS ? "HTTP 429" : "other");
            throw new MarketDataException(status,
                    status == HttpStatus.TOO_MANY_REQUESTS
                            ? "Alpha Vantage request limit reached. Please try again later."
                            : "Alpha Vantage returned an HTTP error while retrieving company fundamentals.");
        } catch (ResourceAccessException exception) {
            HttpStatus status = hasTimeoutCause(exception)
                    ? HttpStatus.GATEWAY_TIMEOUT
                    : HttpStatus.SERVICE_UNAVAILABLE;
            logProviderFailure(function, "other");
            throw new MarketDataException(status,
                    status == HttpStatus.GATEWAY_TIMEOUT
                            ? "Alpha Vantage timed out while retrieving company fundamentals."
                            : "Alpha Vantage could not be reached.");
        } catch (RestClientException exception) {
            logProviderFailure(function, "other");
            throw new MarketDataException(HttpStatus.BAD_GATEWAY,
                    "Alpha Vantage returned an unreadable company-fundamentals response.");
        }

        if (response == null) {
            logProviderFailure(function, "other");
            throw invalidProviderResponse();
        }
        if (!response.isObject() || response.isEmpty()) {
            logProviderFailure(function, "other");
            throw invalidProviderResponse();
        }
        String providerMessage = response.path("Note").asText(
                response.path("Information").asText(""));
        if (!providerMessage.isBlank()) {
            String normalizedMessage = providerMessage.toLowerCase();
            if (normalizedMessage.contains("rate limit")
                    || normalizedMessage.contains("call frequency")
                    || normalizedMessage.contains("requests per day")) {
                logProviderFailure(function, "quota/rate-limit notice");
                throw new MarketDataException(HttpStatus.TOO_MANY_REQUESTS,
                        "Alpha Vantage request limit reached. Please try again later.");
            }
            if (normalizedMessage.contains("premium")
                    || normalizedMessage.contains("subscription")
                    || normalizedMessage.contains("entitlement")) {
                logProviderFailure(function, "premium restriction");
                return null;
            }
            logProviderFailure(function, "other");
            throw new MarketDataException(HttpStatus.BAD_GATEWAY,
                    "Alpha Vantage did not return company fundamentals.");
        }
        if (response.has("Error Message")) {
            logProviderFailure(function, "invalid request");
            throw new MarketDataException(HttpStatus.BAD_GATEWAY,
                    "Alpha Vantage rejected the symbol request.");
        }
        if ("OVERVIEW".equals(function) && text(response, "Symbol") == null) {
            logProviderFailure(function, "other");
            throw new MarketDataException(HttpStatus.BAD_GATEWAY,
                    "Alpha Vantage returned no company overview for this symbol.");
        }
        return response;
    }

    private void logProviderFailure(String endpoint, String category) {
        logger.warn("Alpha Vantage fundamentals request failed: endpoint={}, category={}", endpoint, category);
    }

    private String text(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        String value = node.path(field).asText("");
        return value.isBlank()
                || value.equalsIgnoreCase("None")
                || value.equalsIgnoreCase("N/A")
                || value.equals("-")
                ? null
                : value;
    }

    private String firstAvailable(String primary, String fallback) {
        return primary != null ? primary : fallback;
    }

    private BigDecimal decimal(JsonNode node, String field) {
        String value = text(node, field);
        if (value == null) {
            return null;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException exception) {
            throw invalidProviderResponse();
        }
    }

    private LocalDate parseDate(String value) {
        if (value == null) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException exception) {
            throw invalidProviderResponse();
        }
    }

    private MarketDataException invalidProviderResponse() {
        return new MarketDataException(HttpStatus.BAD_GATEWAY,
                "Alpha Vantage returned invalid company-fundamentals data.");
    }

    private boolean hasTimeoutCause(Throwable exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof SocketTimeoutException) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    private record CachedFundamentals(CompanyFundamentalsResponse data, Instant retrievedAt) {}

    private record LatestReport(JsonNode report, LocalDate periodEnding) {}
}
