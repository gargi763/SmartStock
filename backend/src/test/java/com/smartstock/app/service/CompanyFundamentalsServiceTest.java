package com.smartstock.app.service;

import com.smartstock.app.domain.Stock;
import com.smartstock.app.exception.MarketDataException;
import com.smartstock.app.repository.StockRepository;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class CompanyFundamentalsServiceTest {

    private StockRepository stockRepository;
    private CompanyFundamentalsService service;
    private MockRestServiceServer mockServer;
    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    void setUp() {
        Logger logger = (Logger) LoggerFactory.getLogger(CompanyFundamentalsService.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        logger.addAppender(logAppender);

        stockRepository = mock(StockRepository.class);
        RestClient.Builder clientBuilder = RestClient.builder()
                .baseUrl("https://www.alphavantage.co/query");
        mockServer = MockRestServiceServer.bindTo(clientBuilder).build();
        service = new CompanyFundamentalsService(stockRepository, clientBuilder.build(), "test-api-key");
        when(stockRepository.findBySymbol("AAPL")).thenReturn(Optional.of(Stock.builder()
                .symbol("AAPL")
                .companyName("Apple Inc (catalog)")
                .sector("Technology")
                .build()));
    }

    @AfterEach
    void tearDown() {
        Logger logger = (Logger) LoggerFactory.getLogger(CompanyFundamentalsService.class);
        logger.detachAppender(logAppender);
        logAppender.stop();
    }

    @Test
    void getFundamentals_parsesProviderDataAndCachesSuccessfulResponse() {
        expectOverview("""
                {
                  "Symbol": "AAPL",
                  "Name": "Apple Inc",
                  "Exchange": "NASDAQ",
                  "Sector": "Technology",
                  "Currency": "USD",
                  "MarketCapitalization": "3000000000000",
                  "PERatio": "31.25",
                  "EPS": "6.42",
                  "PriceToBookRatio": "48.1",
                  "DividendYield": "0.0045",
                  "ProfitMargin": "0.2531"
                }
                """);
        expectIncomeStatement("""
                {
                  "annualReports": [
                    {
                      "fiscalDateEnding": "2025-09-27",
                      "totalRevenue": "400000000000",
                      "netIncome": "100000000000"
                    }
                  ],
                  "quarterlyReports": [
                    {
                      "fiscalDateEnding": "2026-06-27",
                      "totalRevenue": "94000000000",
                      "netIncome": "25000000000"
                    }
                  ]
                }
                """);

        var response = service.getFundamentals("aapl");
        var cachedResponse = service.getFundamentals("AAPL");

        assertThat(response.symbol()).isEqualTo("AAPL");
        assertThat(response.companyName()).isEqualTo("Apple Inc");
        assertThat(response.exchange()).isEqualTo("NASDAQ");
        assertThat(response.sector()).isEqualTo("Technology");
        assertThat(response.currency()).isEqualTo("USD");
        assertThat(response.marketCapitalization()).isEqualByComparingTo("3000000000000");
        assertThat(response.peRatio()).isEqualByComparingTo("31.25");
        assertThat(response.earningsPerShare()).isEqualByComparingTo("6.42");
        assertThat(response.priceToBookRatio()).isEqualByComparingTo("48.1");
        assertThat(response.dividendYield()).isEqualByComparingTo("0.0045");
        assertThat(response.revenue()).isEqualByComparingTo("94000000000");
        assertThat(response.netIncome()).isEqualByComparingTo("25000000000");
        assertThat(response.profitMargin()).isEqualByComparingTo("0.2531");
        assertThat(response.fiscalPeriodEnding()).hasToString("2026-06-27");
        assertThat(response.source()).isEqualTo("Alpha Vantage");
        assertThat(response.cached()).isFalse();
        assertThat(cachedResponse.cached()).isTrue();
        assertThat(cachedResponse.retrievedAt()).isEqualTo(response.retrievedAt());
        mockServer.verify();
    }

    @Test
    void getFundamentals_leavesMissingMetricsUnavailableAndUsesLatestQuarterWhenNoAnnualReports() {
        expectOverview("""
                {
                  "Symbol": "AAPL",
                  "Name": "Apple Inc",
                  "MarketCapitalization": "None",
                  "PERatio": "N/A",
                  "EPS": "None"
                }
                """);
        expectIncomeStatement("""
                {
                  "annualReports": [],
                  "quarterlyReports": [
                    {
                      "fiscalDateEnding": "2026-06-27",
                      "reportedCurrency": "USD",
                      "totalRevenue": "94000000000",
                      "netIncome": "None"
                    }
                  ]
                }
                """);

        var response = service.getFundamentals("AAPL");

        assertThat(response.companyName()).isEqualTo("Apple Inc");
        assertThat(response.marketCapitalization()).isNull();
        assertThat(response.peRatio()).isNull();
        assertThat(response.earningsPerShare()).isNull();
        assertThat(response.revenue()).isEqualByComparingTo("94000000000");
        assertThat(response.netIncome()).isNull();
        assertThat(response.fiscalPeriodEnding()).hasToString("2026-06-27");
        mockServer.verify();
    }

    @Test
    void getFundamentals_mapsProviderRateLimitToTooManyRequests() {
        expectOverview("""
                {
                  "Note": "Thank you for using Alpha Vantage! Our standard API call frequency is 25 requests per day."
                }
                """);

        assertThatThrownBy(() -> service.getFundamentals("AAPL"))
                .isInstanceOf(MarketDataException.class)
                .satisfies(exception -> assertThat(((MarketDataException) exception).getStatus())
                        .isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
        assertLoggedClassification("OVERVIEW", "quota/rate-limit notice");
        mockServer.verify();
    }

    @Test
    void getFundamentals_classifiesQuotaMessageMentioningPremiumMembershipAsRateLimit() {
        expectOverview("""
                {
                  "Information": "The daily request limit has been reached. Premium membership allows higher call frequency."
                }
                """);

        assertThatThrownBy(() -> service.getFundamentals("AAPL"))
                .isInstanceOf(MarketDataException.class)
                .satisfies(exception -> assertThat(((MarketDataException) exception).getStatus())
                        .isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
        assertLoggedClassification("OVERVIEW", "quota/rate-limit notice");
        mockServer.verify();
    }

    @Test
    void getFundamentals_rejectsProviderResponseForUnknownSymbol() {
        expectOverview("""
                {
                  "Error Message": "Invalid API call. Please retry or visit the documentation."
                }
                """);

        assertThatThrownBy(() -> service.getFundamentals("AAPL"))
                .isInstanceOf(MarketDataException.class)
                .hasMessageContaining("rejected the symbol");
        assertLoggedClassification("OVERVIEW", "invalid request");
        mockServer.verify();
    }

    @Test
    void getFundamentals_fallsBackToCatalogWhenOverviewIsPlanRestricted() {
        expectOverview("""
                {
                  "Information": "This is a premium endpoint. Please subscribe to access it."
                }
                """);
        expectIncomeStatement("""
                {
                  "annualReports": [
                    {
                      "fiscalDateEnding": "2025-09-27",
                      "reportedCurrency": "USD",
                      "totalRevenue": "400000000000",
                      "netIncome": "100000000000"
                    }
                  ],
                  "quarterlyReports": []
                }
                """);

        var response = service.getFundamentals("AAPL");

        assertThat(response.companyName()).isEqualTo("Apple Inc (catalog)");
        assertThat(response.symbol()).isEqualTo("AAPL");
        assertThat(response.exchange()).isNull();
        assertThat(response.sector()).isEqualTo("Technology");
        assertThat(response.marketCapitalization()).isNull();
        assertThat(response.peRatio()).isNull();
        assertThat(response.revenue()).isEqualByComparingTo("400000000000");
        assertThat(response.netIncome()).isEqualByComparingTo("100000000000");
        assertThat(response.fiscalPeriodEnding()).hasToString("2025-09-27");
        assertThat(response.source()).contains("partial", "plan-restricted");
        assertThat(service.getFundamentals("AAPL").cached()).isTrue();
        assertLoggedClassification("OVERVIEW", "premium restriction");
        mockServer.verify();
    }

    @Test
    void getFundamentals_keepsOverviewMetricsWhenIncomeStatementIsPlanRestricted() {
        expectOverview("""
                {
                  "Symbol": "AAPL",
                  "Name": "Apple Inc",
                  "Exchange": "NASDAQ",
                  "Sector": "Technology",
                  "Currency": "USD",
                  "MarketCapitalization": "3000000000000",
                  "PERatio": "31.25"
                }
                """);
        expectIncomeStatement("""
                {
                  "Information": "This is a premium endpoint. Please subscribe to access it."
                }
                """);

        var response = service.getFundamentals("AAPL");

        assertThat(response.companyName()).isEqualTo("Apple Inc");
        assertThat(response.exchange()).isEqualTo("NASDAQ");
        assertThat(response.marketCapitalization()).isEqualByComparingTo("3000000000000");
        assertThat(response.peRatio()).isEqualByComparingTo("31.25");
        assertThat(response.revenue()).isNull();
        assertThat(response.netIncome()).isNull();
        assertThat(response.fiscalPeriodEnding()).isNull();
        assertThat(response.source()).contains("partial", "plan-restricted");
        assertLoggedClassification("INCOME_STATEMENT", "premium restriction");
        mockServer.verify();
    }

    @Test
    void getFundamentals_logsHttp429WithoutLoggingProviderDetails() {
        mockServer.expect(requestTo("https://www.alphavantage.co/query?function=OVERVIEW&symbol=AAPL&apikey=test-api-key"))
                .andExpect(method(GET))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(() -> service.getFundamentals("AAPL"))
                .isInstanceOf(MarketDataException.class)
                .satisfies(exception -> assertThat(((MarketDataException) exception).getStatus())
                        .isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
        assertLoggedClassification("OVERVIEW", "HTTP 429");
        mockServer.verify();
    }

    @Test
    void getFundamentals_reportsMissingApiKeyWithoutCallingProvider() {
        CompanyFundamentalsService unconfiguredService =
                new CompanyFundamentalsService(stockRepository, RestClient.create(), "");

        assertThatThrownBy(() -> unconfiguredService.getFundamentals("AAPL"))
                .isInstanceOf(MarketDataException.class)
                .hasMessageContaining("SMARTSTOCK_MARKET_DATA_API_KEY");
    }

    private void expectOverview(String body) {
        mockServer.expect(requestTo("https://www.alphavantage.co/query?function=OVERVIEW&symbol=AAPL&apikey=test-api-key"))
                .andExpect(method(GET))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }

    private void expectIncomeStatement(String body) {
        mockServer.expect(requestTo("https://www.alphavantage.co/query?function=INCOME_STATEMENT&symbol=AAPL&apikey=test-api-key"))
                .andExpect(method(GET))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }

    private void assertLoggedClassification(String endpoint, String category) {
        assertThat(logAppender.list)
                .extracting(ILoggingEvent::getFormattedMessage)
                .containsExactly("Alpha Vantage fundamentals request failed: endpoint="
                        + endpoint + ", category=" + category)
                .allSatisfy(message -> assertThat(message)
                        .doesNotContain("test-api-key", "https://", "apikey"));
    }
}
