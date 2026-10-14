package com.smartstock.app;

import com.smartstock.app.domain.*;
import com.smartstock.app.dto.PortfolioSummaryResponse;
import com.smartstock.app.dto.TradeRequest;
import com.smartstock.app.repository.*;
import com.smartstock.app.service.PortfolioService;
import com.smartstock.app.exception.InsufficientFundsException;
import com.smartstock.app.exception.InvalidTradeException;
import com.smartstock.app.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import(PortfolioService.class)
class PortfolioServiceTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PortfolioRepository portfolioRepository;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private HoldingRepository holdingRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private PortfolioService portfolioService;

    private User user;
    private Portfolio portfolio;

    @BeforeEach
    void setUp() {
        user = userRepository.save(User.builder()
                .username("alice")
                .email("alice@test.com")
                .password("pw")
                .roles(java.util.Set.of(Role.USER))
                .build());

        portfolio = portfolioRepository.save(Portfolio.builder()
                .name("Growth")
                .cashBalance(new BigDecimal("10000.00"))
                .user(user)
                .build());

        stockRepository.save(Stock.builder()
                .symbol("AAPL")
                .companyName("Apple Inc")
                .currentPrice(new BigDecimal("200.00"))
                .marketSource("demo")
                .sector("Technology")
                .build());
    }

    @Test
    void buyStock_withValidTrade_updatesHoldingAndCash() {
        PortfolioSummaryResponse summary = portfolioService.buyStock(
                "alice", portfolio.getId(), new TradeRequest("AAPL", new BigDecimal("2.0"), new BigDecimal("9999.99")));

        assertThat(summary.cashBalance()).isEqualByComparingTo("9600.00");
        assertThat(summary.holdings()).hasSize(1);
        assertThat(summary.transactions()).hasSize(1);
        assertThat(summary.holdings().get(0).quantity()).isEqualByComparingTo("2.0");
        assertThat(summary.holdings().get(0).averageCost()).isEqualByComparingTo("200.00");
        assertThat(summary.totalValue()).isEqualByComparingTo("10000.00");
        assertThat(summary.unrealizedProfitLoss()).isEqualByComparingTo("0.00");
        assertThat(summary.realizedProfitLoss()).isEqualByComparingTo("0.00");
        assertThat(summary.totalProfitLoss()).isEqualByComparingTo("0.00");
        assertThat(summary.transactions().get(0).unitPrice()).isEqualByComparingTo("200.00");
        assertThat(summary.transactions().get(0).totalAmount()).isEqualByComparingTo("400.00");
    }

    @Test
    void buyStock_withInsufficientFunds_throws() {
        assertThatThrownBy(() -> portfolioService.buyStock(
                "alice", portfolio.getId(), new TradeRequest("AAPL", new BigDecimal("100"), new BigDecimal("200.00")))
        ).isInstanceOf(InsufficientFundsException.class);

        assertThat(portfolioRepository.findById(portfolio.getId()).orElseThrow().getCashBalance())
                .isEqualByComparingTo("10000.00");
        assertThat(holdingRepository.findAllByPortfolio(portfolio)).isEmpty();
        assertThat(transactionRepository.findByPortfolioOrderByExecutedAtDesc(portfolio)).isEmpty();
    }

    @Test
    void getPortfoliosForUser_returnsOnlyThatUsersPortfoliosAndRelatedData() {
        User bob = userRepository.save(User.builder()
                .username("bob")
                .email("bob@test.com")
                .password("pw")
                .roles(java.util.Set.of(Role.USER))
                .build());
        Portfolio bobsPortfolio = portfolioRepository.save(Portfolio.builder()
                .name("Bob's portfolio")
                .cashBalance(new BigDecimal("5000.00"))
                .user(bob)
                .build());
        portfolioService.buyStock("bob", bobsPortfolio.getId(),
                new TradeRequest("AAPL", BigDecimal.ONE, BigDecimal.ZERO));

        var alicesPortfolios = portfolioService.getPortfoliosForUser("alice");
        var bobsPortfolios = portfolioService.getPortfoliosForUser("bob");

        assertThat(alicesPortfolios).singleElement()
                .satisfies(summary -> {
                    assertThat(summary.id()).isEqualTo(portfolio.getId());
                    assertThat(summary.holdings()).isEmpty();
                    assertThat(summary.transactions()).isEmpty();
                });
        assertThat(bobsPortfolios).singleElement()
                .satisfies(summary -> {
                    assertThat(summary.id()).isEqualTo(bobsPortfolio.getId());
                    assertThat(summary.holdings()).hasSize(1);
                    assertThat(summary.transactions()).hasSize(1);
                });
    }

    @Test
    void buyAndSell_withAnotherUsersPortfolioId_areRejectedWithoutChangingTheirData() {
        User bob = userRepository.save(User.builder()
                .username("bob")
                .email("bob@test.com")
                .password("pw")
                .roles(java.util.Set.of(Role.USER))
                .build());
        Portfolio bobsPortfolio = portfolioRepository.save(Portfolio.builder()
                .name("Bob's portfolio")
                .cashBalance(new BigDecimal("5000.00"))
                .user(bob)
                .build());
        portfolioService.buyStock("bob", bobsPortfolio.getId(),
                new TradeRequest("AAPL", BigDecimal.ONE, BigDecimal.ZERO));
        BigDecimal bobCashBefore = bobsPortfolio.getCashBalance();
        BigDecimal bobQuantityBefore = holdingRepository.findAllByPortfolio(bobsPortfolio).get(0).getQuantity();
        int bobTransactionsBefore = transactionRepository.findByPortfolioOrderByExecutedAtDesc(bobsPortfolio).size();

        assertThatThrownBy(() -> portfolioService.buyStock("alice", bobsPortfolio.getId(),
                new TradeRequest("AAPL", BigDecimal.ONE, BigDecimal.ZERO)))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> portfolioService.sellStock("alice", bobsPortfolio.getId(),
                new TradeRequest("AAPL", BigDecimal.ONE, BigDecimal.ZERO)))
                .isInstanceOf(ResourceNotFoundException.class);

        Portfolio unchangedPortfolio = portfolioRepository.findById(bobsPortfolio.getId()).orElseThrow();
        assertThat(unchangedPortfolio.getCashBalance()).isEqualByComparingTo(bobCashBefore);
        assertThat(holdingRepository.findAllByPortfolio(bobsPortfolio)).singleElement()
                .satisfies(holding -> assertThat(holding.getQuantity()).isEqualByComparingTo(bobQuantityBefore));
        assertThat(transactionRepository.findByPortfolioOrderByExecutedAtDesc(bobsPortfolio))
                .hasSize(bobTransactionsBefore);
    }

    @Test
    void sellStock_withQuantityMoreThanHolding_throws() {
        portfolioService.buyStock("alice", portfolio.getId(), new TradeRequest("AAPL", new BigDecimal("1"), new BigDecimal("200.00")));

        assertThatThrownBy(() -> portfolioService.sellStock(
                "alice", portfolio.getId(), new TradeRequest("AAPL", new BigDecimal("2"), new BigDecimal("210.00")))
        ).isInstanceOf(InvalidTradeException.class);

        assertThat(portfolioRepository.findById(portfolio.getId()).orElseThrow().getCashBalance())
                .isEqualByComparingTo("9800.00");
        assertThat(holdingRepository.findAllByPortfolio(portfolio)).singleElement()
                .satisfies(holding -> assertThat(holding.getQuantity()).isEqualByComparingTo("1"));
        assertThat(transactionRepository.findByPortfolioOrderByExecutedAtDesc(portfolio)).hasSize(1);
    }

    @Test
    void sellStock_withValidTrade_updatesHoldingAndCash() {
        portfolioService.buyStock("alice", portfolio.getId(), new TradeRequest("AAPL", new BigDecimal("2"), new BigDecimal("200.00")));
        setServerPrice("210.00");
        PortfolioSummaryResponse summary = portfolioService.sellStock("alice", portfolio.getId(), new TradeRequest("AAPL", new BigDecimal("1"), new BigDecimal("9999.99")));

        assertThat(summary.cashBalance()).isEqualByComparingTo("9810.00");
        assertThat(summary.holdings()).hasSize(1);
        assertThat(summary.holdings().get(0).quantity()).isEqualByComparingTo("1");
        assertThat(summary.holdings().get(0).averageCost()).isEqualByComparingTo("200.00");
        assertThat(summary.holdings().get(0).currentPrice()).isEqualByComparingTo("210.00");
        assertThat(summary.totalValue()).isEqualByComparingTo("10020.00");
        assertThat(summary.realizedProfitLoss()).isEqualByComparingTo("10.00");
        assertThat(summary.unrealizedProfitLoss()).isEqualByComparingTo("10.00");
        assertThat(summary.totalProfitLoss()).isEqualByComparingTo("20.00");
        assertThat(summary.transactions().get(0).realizedProfitLoss()).isEqualByComparingTo("10.00");
        assertThat(summary.transactions().get(0).unitPrice()).isEqualByComparingTo("210.00");
        assertThat(summary.transactions().get(0).totalAmount()).isEqualByComparingTo("210.00");
        assertThat(summary.transactions().get(1).realizedProfitLoss()).isEqualByComparingTo("0.00");
    }

    @Test
    void buyStock_withMultiplePrices_calculatesWeightedAverageCost() {
        portfolioService.buyStock("alice", portfolio.getId(),
                new TradeRequest("AAPL", new BigDecimal("2"), new BigDecimal("200.00")));
        setServerPrice("230.00");
        PortfolioSummaryResponse summary = portfolioService.buyStock("alice", portfolio.getId(),
                new TradeRequest("AAPL", new BigDecimal("1"), new BigDecimal("9999.99")));

        assertThat(summary.cashBalance()).isEqualByComparingTo("9370.00");
        assertThat(summary.holdings()).singleElement().satisfies(holding -> {
            assertThat(holding.quantity()).isEqualByComparingTo("3");
            assertThat(holding.averageCost()).isEqualByComparingTo("210.00");
            assertThat(holding.marketValue()).isEqualByComparingTo("690.00");
            assertThat(holding.unrealizedProfitLoss()).isEqualByComparingTo("60.00");
        });
        assertThat(summary.totalValue()).isEqualByComparingTo("10060.00");
        assertThat(summary.realizedProfitLoss()).isEqualByComparingTo("0.00");
        assertThat(summary.totalProfitLoss()).isEqualByComparingTo("60.00");
        assertThat(summary.transactions().get(0).unitPrice()).isEqualByComparingTo("230.00");
    }

    @Test
    void sellStock_afterMultiplePurchasesAndPartialSale_tracksProfitThroughLiquidation() {
        portfolioService.buyStock("alice", portfolio.getId(),
                new TradeRequest("AAPL", new BigDecimal("2"), new BigDecimal("200.00")));
        setServerPrice("230.00");
        PortfolioSummaryResponse afterPurchases = portfolioService.buyStock("alice", portfolio.getId(),
                new TradeRequest("AAPL", new BigDecimal("1"), new BigDecimal("0.01")));

        assertThat(afterPurchases.cashBalance()).isEqualByComparingTo("9370.00");
        assertThat(afterPurchases.holdings()).singleElement().satisfies(holding -> {
            assertThat(holding.quantity()).isEqualByComparingTo("3");
            assertThat(holding.averageCost()).isEqualByComparingTo("210.00");
        });
        assertThat(afterPurchases.realizedProfitLoss()).isEqualByComparingTo("0.00");
        assertThat(afterPurchases.unrealizedProfitLoss()).isEqualByComparingTo("60.00");
        assertThat(afterPurchases.totalValue()).isEqualByComparingTo("10060.00");

        PortfolioSummaryResponse afterPartialSale = portfolioService.sellStock("alice", portfolio.getId(),
                new TradeRequest("AAPL", new BigDecimal("1"), new BigDecimal("250.00")));

        assertThat(afterPartialSale.cashBalance()).isEqualByComparingTo("9600.00");
        assertThat(afterPartialSale.holdings()).singleElement().satisfies(holding -> {
            assertThat(holding.quantity()).isEqualByComparingTo("2");
            assertThat(holding.averageCost()).isEqualByComparingTo("210.00");
        });
        assertThat(afterPartialSale.realizedProfitLoss()).isEqualByComparingTo("20.00");
        assertThat(afterPartialSale.unrealizedProfitLoss()).isEqualByComparingTo("40.00");
        assertThat(afterPartialSale.totalValue()).isEqualByComparingTo("10060.00");
        assertThat(afterPartialSale.transactions().get(0).unitPrice()).isEqualByComparingTo("230.00");

        setServerPrice("190.00");
        PortfolioSummaryResponse afterLiquidation = portfolioService.sellStock("alice", portfolio.getId(),
                new TradeRequest("AAPL", new BigDecimal("2"), new BigDecimal("9999.99")));

        assertThat(afterLiquidation.cashBalance()).isEqualByComparingTo("9980.00");
        assertThat(afterLiquidation.holdings()).isEmpty();
        assertThat(afterLiquidation.realizedProfitLoss()).isEqualByComparingTo("-20.00");
        assertThat(afterLiquidation.unrealizedProfitLoss()).isEqualByComparingTo("0.00");
        assertThat(afterLiquidation.totalValue()).isEqualByComparingTo("9980.00");
        assertThat(afterLiquidation.transactions().get(0).unitPrice()).isEqualByComparingTo("190.00");
    }

    @Test
    void buyStock_withFractionalShares_preservesAverageCostPrecision() {
        setServerPrice("100.00");
        portfolioService.buyStock("alice", portfolio.getId(),
                new TradeRequest("AAPL", new BigDecimal("0.1"), new BigDecimal("0.01")));
        setServerPrice("200.00");
        PortfolioSummaryResponse summary = portfolioService.buyStock("alice", portfolio.getId(),
                new TradeRequest("AAPL", new BigDecimal("0.2"), new BigDecimal("9999.99")));

        assertThat(summary.cashBalance()).isEqualByComparingTo("9950.00");
        assertThat(summary.holdings()).singleElement().satisfies(holding -> {
            assertThat(holding.quantity()).isEqualByComparingTo("0.3");
            assertThat(holding.averageCost()).isEqualByComparingTo("166.66666667");
            assertThat(holding.marketValue()).isEqualByComparingTo("60.00");
            assertThat(holding.unrealizedProfitLoss()).isEqualByComparingTo("10.00");
        });
    }

    @Test
    void sellStock_fullPosition_removesHoldingAndRecordsRealizedProfit() {
        portfolioService.buyStock("alice", portfolio.getId(),
                new TradeRequest("AAPL", new BigDecimal("2"), new BigDecimal("200.00")));
        setServerPrice("190.00");
        PortfolioSummaryResponse summary = portfolioService.sellStock("alice", portfolio.getId(),
                new TradeRequest("AAPL", new BigDecimal("2"), new BigDecimal("0.01")));

        assertThat(summary.cashBalance()).isEqualByComparingTo("9980.00");
        assertThat(summary.holdings()).isEmpty();
        assertThat(summary.realizedProfitLoss()).isEqualByComparingTo("-20.00");
        assertThat(summary.unrealizedProfitLoss()).isEqualByComparingTo("0.00");
        assertThat(summary.totalProfitLoss()).isEqualByComparingTo("-20.00");
        assertThat(summary.transactions()).hasSize(2);
    }

    @Test
    void buyStock_withNonPositiveQuantity_throws() {
        assertInvalidTrade(new TradeRequest("AAPL", BigDecimal.ZERO, new BigDecimal("200.00")));
        assertInvalidTrade(new TradeRequest("AAPL", BigDecimal.ONE.negate(), new BigDecimal("200.00")));
    }

    @Test
    void buyStock_ignoresClientPricePrecisionAndRange() {
        PortfolioSummaryResponse summary = portfolioService.buyStock("alice", portfolio.getId(),
                new TradeRequest("AAPL", BigDecimal.ONE, new BigDecimal("999999999999999.999999")));

        assertThat(summary.cashBalance()).isEqualByComparingTo("9800.00");
        assertThat(summary.holdings()).singleElement()
                .satisfies(holding -> assertThat(holding.averageCost()).isEqualByComparingTo("200.00"));
        assertThat(summary.transactions().get(0).unitPrice()).isEqualByComparingTo("200.00");
    }

    @Test
    void trade_withUnavailableServerQuote_isRejectedWithoutChangingPortfolio() {
        setServerPrice("0.00");

        assertThatThrownBy(() -> portfolioService.buyStock("alice", portfolio.getId(),
                new TradeRequest("AAPL", BigDecimal.ONE, new BigDecimal("200.00"))))
                .isInstanceOf(InvalidTradeException.class)
                .hasMessageContaining("server-side stock price is unavailable");

        setServerPrice("200.00");
        setServerPriceSource("unavailable");
        assertThatThrownBy(() -> portfolioService.sellStock("alice", portfolio.getId(),
                new TradeRequest("AAPL", BigDecimal.ONE, new BigDecimal("200.00"))))
                .isInstanceOf(InvalidTradeException.class)
                .hasMessageContaining("server-side stock price is unavailable");

        assertThat(portfolioRepository.findById(portfolio.getId()).orElseThrow().getCashBalance())
                .isEqualByComparingTo("10000.00");
        assertThat(holdingRepository.findAllByPortfolio(portfolio)).isEmpty();
        assertThat(transactionRepository.findByPortfolioOrderByExecutedAtDesc(portfolio)).isEmpty();
    }

    @Test
    void buyStock_withInvalidServerPricePrecision_throws() {
        setServerPrice("200.00001");

        assertThatThrownBy(() -> portfolioService.buyStock("alice", portfolio.getId(),
                new TradeRequest("AAPL", BigDecimal.ONE, new BigDecimal("200.00"))))
                .isInstanceOf(InvalidTradeException.class)
                .hasMessageContaining("server-side stock price is unavailable");

        assertThat(portfolioRepository.findById(portfolio.getId()).orElseThrow().getCashBalance())
                .isEqualByComparingTo("10000.00");
        assertThat(holdingRepository.findAllByPortfolio(portfolio)).isEmpty();
        assertThat(transactionRepository.findByPortfolioOrderByExecutedAtDesc(portfolio)).isEmpty();
    }

    @Test
    void buyStock_withTradeAmountOutsideDatabaseRange_throws() {
        setServerPrice("999999999999999.9999");
        assertThatThrownBy(() -> portfolioService.buyStock("alice", portfolio.getId(),
                new TradeRequest("AAPL", new BigDecimal("9999999999999"), new BigDecimal("0.01"))))
                .isInstanceOf(InvalidTradeException.class)
                .hasMessageContaining("monetary range");
    }

    private void setServerPrice(String price) {
        Stock stock = stockRepository.findBySymbol("AAPL").orElseThrow();
        stock.setCurrentPrice(new BigDecimal(price));
        stockRepository.save(stock);
    }

    private void setServerPriceSource(String source) {
        Stock stock = stockRepository.findBySymbol("AAPL").orElseThrow();
        stock.setMarketSource(source);
        stockRepository.save(stock);
    }

    private void assertInvalidTrade(TradeRequest request) {
        assertThatThrownBy(() -> portfolioService.buyStock("alice", portfolio.getId(), request))
                .isInstanceOf(InvalidTradeException.class);
    }
}
