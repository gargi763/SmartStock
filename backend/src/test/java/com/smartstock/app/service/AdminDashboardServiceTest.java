package com.smartstock.app.service;

import com.smartstock.app.domain.*;
import com.smartstock.app.dto.AdminDashboardResponse;
import com.smartstock.app.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(AdminDashboardService.class)
class AdminDashboardServiceTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private PortfolioRepository portfolioRepository;

    @Autowired
    private HoldingRepository holdingRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private WatchlistItemRepository watchlistItemRepository;

    @Autowired
    private AdminDashboardService adminDashboardService;

    @Test
    void getDashboard_returnsAccurateAggregateCountsWithoutExposingRecords() {
        User administrator = saveUser("admin", Role.ADMIN);
        User investor = saveUser("investor", Role.USER);
        User operator = saveUser("operator", Role.USER, Role.ADMIN);

        Stock demoStock = saveStock("DEMO", "demo");
        Stock providerStock = saveStock("PROV", "Alpha Vantage");
        Stock unavailableStock = saveStock("NONE", "unavailable");

        Portfolio investorPortfolio = savePortfolio("Investor", investor);
        Portfolio operatorPortfolio = savePortfolio("Operator", operator);

        holdingRepository.save(Holding.builder()
                .portfolio(investorPortfolio)
                .stock(demoStock)
                .quantity(new BigDecimal("3.000000"))
                .averageCost(new BigDecimal("100.00000000"))
                .build());

        transactionRepository.save(createTransaction(investorPortfolio, demoStock, TradeType.BUY));
        transactionRepository.save(createTransaction(operatorPortfolio, providerStock, TradeType.SELL));

        watchlistItemRepository.save(WatchlistItem.builder().user(investor).stock(providerStock).build());
        watchlistItemRepository.save(WatchlistItem.builder().user(operator).stock(unavailableStock).build());

        AdminDashboardResponse summary = adminDashboardService.getDashboard();

        assertThat(summary.totalRegisteredUsers()).isEqualTo(3);
        assertThat(summary.usersWithUserRole()).isEqualTo(2);
        assertThat(summary.usersWithAdminRole()).isEqualTo(2);
        assertThat(summary.totalStocks()).isEqualTo(3);
        assertThat(summary.totalPortfolios()).isEqualTo(2);
        assertThat(summary.totalHoldings()).isEqualTo(1);
        assertThat(summary.totalVirtualTransactions()).isEqualTo(2);
        assertThat(summary.buyTransactions()).isEqualTo(1);
        assertThat(summary.sellTransactions()).isEqualTo(1);
        assertThat(summary.totalWatchlistEntries()).isEqualTo(2);
        assertThat(summary.stocksWithAvailablePriceSource()).isEqualTo(2);
        assertThat(summary.stocksWithUnavailablePriceSource()).isEqualTo(1);
        assertThat(summary.toString()).doesNotContain("investor", "operator", "password", "100.00000000");

        assertThat(administrator.getId()).isNotNull();
    }

    private User saveUser(String username, Role... roles) {
        return userRepository.save(User.builder()
                .username(username)
                .email(username + "@example.test")
                .password("encoded-sensitive-password")
                .roles(Set.of(roles))
                .build());
    }

    private Stock saveStock(String symbol, String marketSource) {
        return stockRepository.save(Stock.builder()
                .symbol(symbol)
                .companyName(symbol + " Company")
                .sector("Technology")
                .currentPrice(new BigDecimal("100.0000"))
                .marketSource(marketSource)
                .build());
    }

    private Portfolio savePortfolio(String name, User owner) {
        return portfolioRepository.save(Portfolio.builder()
                .name(name)
                .cashBalance(new BigDecimal("1000.0000"))
                .user(owner)
                .build());
    }

    private Transaction createTransaction(Portfolio portfolio, Stock stock, TradeType tradeType) {
        return Transaction.builder()
                .portfolio(portfolio)
                .stock(stock)
                .tradeType(tradeType)
                .quantity(BigDecimal.ONE)
                .unitPrice(new BigDecimal("100.0000"))
                .totalAmount(new BigDecimal("100.0000"))
                .realizedProfitLoss(BigDecimal.ZERO)
                .status(TransactionStatus.COMPLETED)
                .executedAt(LocalDateTime.now())
                .build();
    }
}
