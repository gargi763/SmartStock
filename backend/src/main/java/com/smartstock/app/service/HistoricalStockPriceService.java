package com.smartstock.app.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartstock.app.domain.Stock;
import com.smartstock.app.dto.HistoricalPriceHistoryResponse;
import com.smartstock.app.dto.HistoricalPricePointResponse;
import com.smartstock.app.exception.MarketDataException;
import com.smartstock.app.exception.ResourceNotFoundException;
import com.smartstock.app.repository.StockRepository;
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
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.StreamSupport;

@Service
public class HistoricalStockPriceService {

    private static final Duration CACHE_DURATION = Duration.ofMinutes(30);
    private static final String SOURCE = "Alpha Vantage (daily, unadjusted)";
    private static final String TIME_SERIES_KEY = "Time Series (Daily)";

    private final StockRepository stockRepository;
    private final RestClient restClient;
    private final String apiKey;
    private final Map<String, CachedHistory> cache = new ConcurrentHashMap<>();

    public HistoricalStockPriceService(
            StockRepository stockRepository,
            RestClient alphaVantageRestClient,
            @Value("${app.market-data.alpha-vantage.api-key:}") String apiKey) {
        this.stockRepository = stockRepository;
        this.restClient = alphaVantageRestClient;
        this.apiKey = apiKey;
    }

    public HistoricalPriceHistoryResponse getHistory(String requestedSymbol, String requestedRange) {
        String symbol = requestedSymbol.trim().toUpperCase();
        Stock stock = stockRepository.findBySymbol(symbol)
                .orElseThrow(() -> new ResourceNotFoundException("Stock not found: " + requestedSymbol));
        PriceRange range = PriceRange.from(requestedRange);

        if (apiKey.isBlank()) {
            throw new MarketDataException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Historical prices are not configured. Set SMARTSTOCK_MARKET_DATA_API_KEY in the backend environment.");
        }

        Instant now = Instant.now();
        CachedHistory cachedHistory = cache.get(stock.getSymbol());
        boolean cached = cachedHistory != null && cachedHistory.retrievedAt().plus(CACHE_DURATION).isAfter(now);
        if (!cached) {
            cachedHistory = new CachedHistory(fetchDailyPrices(stock.getSymbol()), now);
            cache.put(stock.getSymbol(), cachedHistory);
        }

        LocalDate cutoff = LocalDate.now(ZoneOffset.UTC).minusDays(range.days());
        List<HistoricalPricePointResponse> prices = cachedHistory.prices().stream()
                .filter(point -> !point.timestamp().isBefore(cutoff))
                .toList();
        LocalDate dataAsOf = cachedHistory.prices().stream()
                .map(HistoricalPricePointResponse::timestamp)
                .max(Comparator.naturalOrder())
                .orElse(null);

        return new HistoricalPriceHistoryResponse(
                stock.getSymbol(),
                range.label(),
                prices,
                SOURCE,
                dataAsOf,
                cachedHistory.retrievedAt(),
                cached
        );
    }

    private List<HistoricalPricePointResponse> fetchDailyPrices(String symbol) {
        JsonNode response;
        try {
            response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .queryParam("function", "TIME_SERIES_DAILY")
                            .queryParam("symbol", symbol)
                            .queryParam("outputsize", "compact")
                            .queryParam("apikey", apiKey)
                            .build())
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException exception) {
            HttpStatus status = exception.getStatusCode().value() == HttpStatus.TOO_MANY_REQUESTS.value()
                    ? HttpStatus.TOO_MANY_REQUESTS
                    : HttpStatus.BAD_GATEWAY;
            throw new MarketDataException(status,
                    status == HttpStatus.TOO_MANY_REQUESTS
                            ? "Historical market-data provider rate limit reached. Please try again later."
                            : "Historical market-data provider returned an HTTP error.");
        } catch (ResourceAccessException exception) {
            HttpStatus status = hasTimeoutCause(exception)
                    ? HttpStatus.GATEWAY_TIMEOUT
                    : HttpStatus.SERVICE_UNAVAILABLE;
            throw new MarketDataException(status,
                    status == HttpStatus.GATEWAY_TIMEOUT
                            ? "Historical market-data provider timed out."
                            : "Historical market-data provider could not be reached.");
        } catch (RestClientException exception) {
            throw new MarketDataException(HttpStatus.BAD_GATEWAY,
                    "Historical market-data provider returned an unreadable response.");
        }

        if (response == null) {
            throw new MarketDataException(HttpStatus.BAD_GATEWAY,
                    "Historical market-data provider returned an empty response.");
        }

        String providerMessage = response.path("Note").asText(
                response.path("Information").asText(""));
        if (!providerMessage.isBlank()) {
            String normalizedMessage = providerMessage.toLowerCase();
            if (normalizedMessage.contains("rate limit")
                    || normalizedMessage.contains("call frequency")
                    || normalizedMessage.contains("requests per day")) {
                throw new MarketDataException(HttpStatus.TOO_MANY_REQUESTS,
                        "Historical market-data provider rate limit reached. Please try again later.");
            }
            throw new MarketDataException(HttpStatus.BAD_GATEWAY,
                    "Historical market-data provider did not return price history.");
        }
        if (response.has("Error Message")) {
            throw new MarketDataException(HttpStatus.BAD_GATEWAY,
                    "Historical market-data provider rejected the symbol request.");
        }

        JsonNode timeSeries = response.get(TIME_SERIES_KEY);
        if (timeSeries == null || !timeSeries.isObject()) {
            throw new MarketDataException(HttpStatus.BAD_GATEWAY,
                    "Historical market-data provider response did not contain daily prices.");
        }

        try {
            return StreamSupport.stream(
                            java.util.Spliterators.spliteratorUnknownSize(timeSeries.fields(), 0), false)
                    .map(entry -> new HistoricalPricePointResponse(
                            LocalDate.parse(entry.getKey()),
                            new BigDecimal(entry.getValue().path("4. close").asText())))
                    .sorted(Comparator.comparing(HistoricalPricePointResponse::timestamp))
                    .toList();
        } catch (DateTimeParseException | NumberFormatException exception) {
            throw new MarketDataException(HttpStatus.BAD_GATEWAY,
                    "Historical market-data provider returned invalid daily price data.");
        }
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

    private record CachedHistory(List<HistoricalPricePointResponse> prices, Instant retrievedAt) {}

    private enum PriceRange {
        WEEK("1W", 7),
        MONTH("1M", 30),
        THREE_MONTHS("3M", 90);

        private final String label;
        private final long days;

        PriceRange(String label, long days) {
            this.label = label;
            this.days = days;
        }

        private String label() {
            return label;
        }

        private long days() {
            return days;
        }

        private static PriceRange from(String value) {
            for (PriceRange range : values()) {
                if (range.label.equalsIgnoreCase(value)) {
                    return range;
                }
            }
            throw new MarketDataException(HttpStatus.BAD_REQUEST,
                    "Range must be one of: 1W, 1M, 3M.");
        }
    }
}
