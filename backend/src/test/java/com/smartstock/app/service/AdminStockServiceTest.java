package com.smartstock.app.service;

import com.smartstock.app.domain.*;
import com.smartstock.app.dto.AdminStockRequest;
import com.smartstock.app.dto.StockResponse;
import com.smartstock.app.dto.UpdateStockMetadataRequest;
import com.smartstock.app.exception.DuplicateStockException;
import com.smartstock.app.exception.InvalidStockSymbolException;
import com.smartstock.app.exception.ResourceNotFoundException;
import com.smartstock.app.exception.StockDeletionNotAllowedException;
import com.smartstock.app.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import(AdminStockService.class)
class AdminStockServiceTest {

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PortfolioRepository portfolioRepository;

    @Autowired
    private HoldingRepository holdingRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private WatchlistItemRepository watchlistItemRepository;

    @Autowired
    private AdminStockService adminStockService;

    private Stock stock;

    @BeforeEach
    void setUp() {
        stock = stockRepository.save(Stock.builder()
                .symbol("AAPL")
                .companyName("Apple Inc.")
                .sector("Technology")
                .currentPrice(new BigDecimal("214.8600"))
                .dayChange(new BigDecimal("2.3400"))
                .dayChangePercent(new BigDecimal("1.1000"))
                .marketSource("demo")
                .build());
    }

    @Test
    void createStock_normalizesSymbolAndDoesNotInventMarketPrice() {
        StockResponse created = adminStockService.createStock(
                new AdminStockRequest(" msft ", "Microsoft Corporation", "Technology"));

        assertThat(created.symbol()).isEqualTo("MSFT");
        assertThat(created.companyName()).isEqualTo("Microsoft Corporation");
        assertThat(created.sector()).isEqualTo("Technology");
        assertThat(created.currentPrice()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(created.marketSource()).isEqualTo("unavailable");
        assertThat(created.dayChange()).isNull();
        assertThat(created.dayChangePercent()).isNull();
    }

    @Test
    void createStock_rejectsDuplicateSymbolCaseInsensitively() {
        assertThatThrownBy(() -> adminStockService.createStock(
                new AdminStockRequest(" aapl ", "Different Company", "Technology")))
                .isInstanceOf(DuplicateStockException.class)
                .hasMessageContaining("AAPL");
    }

    @Test
    void createAndRead_rejectInvalidSymbolsAndMissingStocks() {
        assertThatThrownBy(() -> adminStockService.createStock(
                new AdminStockRequest("BAD SYMBOL", "Company", "Sector")))
                .isInstanceOf(InvalidStockSymbolException.class);
        assertThatThrownBy(() -> adminStockService.getStock("???"))
                .isInstanceOf(InvalidStockSymbolException.class);
        assertThatThrownBy(() -> adminStockService.getStock("UNKNOWN"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateMetadata_changesCompanyAndSectorButPreservesMarketData() {
        StockResponse updated = adminStockService.updateMetadata(
                "aapl", new UpdateStockMetadataRequest("Apple Incorporated", "Consumer Electronics"));

        assertThat(updated.companyName()).isEqualTo("Apple Incorporated");
        assertThat(updated.sector()).isEqualTo("Consumer Electronics");
        assertThat(updated.currentPrice()).isEqualByComparingTo("214.8600");
        assertThat(updated.dayChange()).isEqualByComparingTo("2.3400");
        assertThat(updated.dayChangePercent()).isEqualByComparingTo("1.1000");
        assertThat(updated.marketSource()).isEqualTo("demo");
    }

    @Test
    void updateMetadata_whenStockIsMissing_throwsNotFound() {
        assertThatThrownBy(() -> adminStockService.updateMetadata(
                "UNKNOWN", new UpdateStockMetadataRequest("Company", "Sector")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deleteStock_whenUnreferenced_removesStock() {
        adminStockService.deleteStock("aapl");

        assertThat(stockRepository.findBySymbol("AAPL")).isEmpty();
    }

    @Test
    void deleteStock_whenReferenced_preservesHoldingsTransactionsAndWatchlist() {
        User user = userRepository.save(User.builder()
                .username("owner")
                .email("owner@example.test")
                .password("encoded")
                .roles(Set.of(Role.USER))
                .build());
        Portfolio portfolio = portfolioRepository.save(Portfolio.builder()
                .name("Main")
                .cashBalance(new BigDecimal("1000.00"))
                .user(user)
                .build());
        Holding holding = holdingRepository.save(Holding.builder()
                .portfolio(portfolio)
                .stock(stock)
                .quantity(new BigDecimal("2.000000"))
                .averageCost(new BigDecimal("200.00000000"))
                .build());
        Transaction transaction = transactionRepository.save(Transaction.builder()
                .portfolio(portfolio)
                .stock(stock)
                .tradeType(TradeType.BUY)
                .quantity(new BigDecimal("2.000000"))
                .unitPrice(new BigDecimal("200.0000"))
                .totalAmount(new BigDecimal("400.0000"))
                .realizedProfitLoss(BigDecimal.ZERO)
                .status(TransactionStatus.COMPLETED)
                .executedAt(LocalDateTime.now())
                .build());
        WatchlistItem watchlistItem = watchlistItemRepository.save(WatchlistItem.builder()
                .user(user)
                .stock(stock)
                .build());

        assertThatThrownBy(() -> adminStockService.deleteStock("AAPL"))
                .isInstanceOf(StockDeletionNotAllowedException.class);

        assertThat(stockRepository.findById(stock.getId())).isPresent();
        assertThat(holdingRepository.findById(holding.getId())).isPresent();
        assertThat(transactionRepository.findById(transaction.getId())).isPresent();
        assertThat(watchlistItemRepository.findById(watchlistItem.getId())).isPresent();
    }
}
