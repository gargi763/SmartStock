package com.smartstock.app.service;

import com.smartstock.app.domain.Stock;
import com.smartstock.app.exception.MarketDataException;
import com.smartstock.app.repository.StockRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withTooManyRequests;
import static org.springframework.http.HttpMethod.GET;

class HistoricalStockPriceServiceTest {

    private StockRepository stockRepository;
    private HistoricalStockPriceService service;
    private MockRestServiceServer mockServer;

    @BeforeEach
    void setUp() {
        stockRepository = mock(StockRepository.class);
        RestClient.Builder clientBuilder = RestClient.builder()
                .baseUrl("https://www.alphavantage.co/query");
        mockServer = MockRestServiceServer.bindTo(clientBuilder).build();
        service = new HistoricalStockPriceService(stockRepository, clientBuilder.build(), "test-api-key");
        when(stockRepository.findBySymbol("AAPL")).thenReturn(Optional.of(Stock.builder()
                .symbol("AAPL")
                .build()));
    }

    @Test
    void getHistory_returnsChronologicalDailyClosesAndCachesProviderResponse() {
        String olderDate = LocalDate.now(ZoneOffset.UTC).minusDays(2).toString();
        String newerDate = LocalDate.now(ZoneOffset.UTC).minusDays(1).toString();
        mockServer.expect(requestTo("https://www.alphavantage.co/query?function=TIME_SERIES_DAILY&symbol=AAPL&outputsize=compact&apikey=test-api-key"))
                .andExpect(method(GET))
                .andRespond(withSuccess("""
                        {
                          "Time Series (Daily)": {
                            "%s": { "4. close": "192.50" },
                            "%s": { "4. close": "191.25" }
                          }
                        }
                        """.formatted(newerDate, olderDate), MediaType.APPLICATION_JSON));

        var firstResponse = service.getHistory("aapl", "1W");
        var cachedResponse = service.getHistory("AAPL", "1M");

        assertThat(firstResponse.prices()).hasSize(2);
        assertThat(firstResponse.prices().get(0).timestamp()).isEqualTo(LocalDate.parse(olderDate));
        assertThat(firstResponse.prices().get(0).close()).isEqualByComparingTo("191.25");
        assertThat(firstResponse.prices().get(1).timestamp()).isEqualTo(LocalDate.parse(newerDate));
        assertThat(firstResponse.prices().get(1).close()).isEqualByComparingTo("192.50");
        assertThat(firstResponse.source()).contains("Alpha Vantage");
        assertThat(firstResponse.dataAsOf()).isEqualTo(LocalDate.parse(newerDate));
        assertThat(firstResponse.retrievedAt()).isNotNull();
        assertThat(firstResponse.cached()).isFalse();
        assertThat(cachedResponse.cached()).isTrue();
        mockServer.verify();
    }

    @Test
    void getHistory_mapsProviderRateLimitToTooManyRequests() {
        mockServer.expect(requestTo("https://www.alphavantage.co/query?function=TIME_SERIES_DAILY&symbol=AAPL&outputsize=compact&apikey=test-api-key"))
                .andExpect(method(GET))
                .andRespond(withTooManyRequests());

        assertThatThrownBy(() -> service.getHistory("AAPL", "1W"))
                .isInstanceOf(MarketDataException.class)
                .satisfies(exception -> assertThat(((MarketDataException) exception).getStatus())
                        .isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
        mockServer.verify();
    }

    @Test
    void getHistory_rejectsUnsupportedRangeBeforeCallingProvider() {
        assertThatThrownBy(() -> service.getHistory("AAPL", "1Y"))
                .isInstanceOf(MarketDataException.class)
                .hasMessageContaining("1W, 1M, 3M");

        mockServer.verify();
    }

    @Test
    void getHistory_reportsMissingApiKeyWithoutCallingProvider() {
        HistoricalStockPriceService unconfiguredService =
                new HistoricalStockPriceService(stockRepository, RestClient.create(), "");

        assertThatThrownBy(() -> unconfiguredService.getHistory("AAPL", "1M"))
                .isInstanceOf(MarketDataException.class)
                .hasMessageContaining("SMARTSTOCK_MARKET_DATA_API_KEY");
    }
}
