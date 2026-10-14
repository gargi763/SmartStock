package com.smartstock.app.service;

import com.smartstock.app.domain.*;
import com.smartstock.app.dto.InvestmentJournalEntryRequest;
import com.smartstock.app.dto.InvestmentJournalEntryResponse;
import com.smartstock.app.exception.ResourceNotFoundException;
import com.smartstock.app.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import(InvestmentJournalService.class)
class InvestmentJournalServiceTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PortfolioRepository portfolioRepository;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private InvestmentJournalEntryRepository journalRepository;

    @Autowired
    private InvestmentJournalService journalService;

    private User alice;
    private User bob;
    private Transaction alicesTrade;
    private Transaction bobsTrade;

    @BeforeEach
    void setUp() {
        alice = saveUser("alice");
        bob = saveUser("bob");
        Portfolio alicesPortfolio = savePortfolio(alice, "Alice");
        Portfolio bobsPortfolio = savePortfolio(bob, "Bob");
        Stock stock = stockRepository.save(Stock.builder()
                .symbol("AAPL")
                .companyName("Apple Inc.")
                .sector("Technology")
                .currentPrice(new BigDecimal("200.00"))
                .marketSource("demo")
                .build());
        alicesTrade = saveTransaction(alicesPortfolio, stock, TransactionStatus.COMPLETED);
        bobsTrade = saveTransaction(bobsPortfolio, stock, TransactionStatus.COMPLETED);
    }

    @Test
    void createAndListEntries_persistsNotesTagsAndOwnedCompletedTrade() {
        InvestmentJournalEntryResponse created = journalService.createEntry("alice", request(
                "  First entry  ", List.of("long-term", "quality"), alicesTrade.getId()));

        assertThat(created.title()).isEqualTo("First entry");
        assertThat(created.thesis()).isEqualTo("Strong fundamentals");
        assertThat(created.lessonsLearned()).isEqualTo("Stay with the plan");
        assertThat(created.tags()).containsExactly("long-term", "quality");
        assertThat(created.transactionId()).isEqualTo(alicesTrade.getId());
        assertThat(created.transactionSymbol()).isEqualTo("AAPL");
        assertThat(created.transactionType()).isEqualTo(TradeType.BUY);
        assertThat(created.transactionDate()).isEqualTo(alicesTrade.getExecutedAt());
        assertThat(journalService.getEntries("alice")).containsExactly(created);
        assertThat(journalService.getEntries("bob")).isEmpty();
    }

    @Test
    void updateAndDeleteEntry_changesOnlyOwnersJournalRecord() {
        InvestmentJournalEntryResponse created = journalService.createEntry(
                "alice", request("Original", List.of("value"), null));

        InvestmentJournalEntryResponse updated = journalService.updateEntry("alice", created.id(),
                request("Revised", List.of("lessons", "risk"), alicesTrade.getId()));

        assertThat(updated.title()).isEqualTo("Revised");
        assertThat(updated.tags()).containsExactly("lessons", "risk");
        assertThat(updated.transactionId()).isEqualTo(alicesTrade.getId());

        journalService.deleteEntry("alice", created.id());

        assertThat(journalService.getEntries("alice")).isEmpty();
        assertThat(transactionRepository.findById(alicesTrade.getId())).isPresent();
    }

    @Test
    void journalOperations_cannotReadChangeOrDeleteAnotherUsersEntry() {
        InvestmentJournalEntryResponse bobsEntry = journalService.createEntry(
                "bob", request("Bob's note", List.of(), bobsTrade.getId()));

        assertThat(journalService.getEntries("alice")).isEmpty();
        assertThatThrownBy(() -> journalService.updateEntry(
                "alice", bobsEntry.id(), request("Changed", List.of(), null)))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> journalService.deleteEntry("alice", bobsEntry.id()))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThat(journalService.getEntries("bob")).singleElement()
                .satisfies(entry -> assertThat(entry.title()).isEqualTo("Bob's note"));
    }

    @Test
    void journalRejectsTransactionsNotOwnedByUserOrNotCompleted() {
        Transaction pendingTrade = saveTransaction(
                portfolioRepository.findByUser(alice).get(0),
                stockRepository.findBySymbol("AAPL").orElseThrow(),
                TransactionStatus.PENDING);

        assertThatThrownBy(() -> journalService.createEntry(
                "alice", request("Other user's trade", List.of(), bobsTrade.getId())))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Completed transaction");
        assertThatThrownBy(() -> journalService.createEntry(
                "alice", request("Pending trade", List.of(), pendingTrade.getId())))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void completedTradeOptions_areFilteredToAuthenticatedUser() {
        saveTransaction(portfolioRepository.findByUser(alice).get(0),
                stockRepository.findBySymbol("AAPL").orElseThrow(), TransactionStatus.REJECTED);

        assertThat(journalService.getCompletedTrades("alice"))
                .singleElement()
                .satisfies(trade -> {
                    assertThat(trade.id()).isEqualTo(alicesTrade.getId());
                    assertThat(trade.symbol()).isEqualTo("AAPL");
                });
        assertThat(journalService.getCompletedTrades("bob"))
                .extracting(trade -> trade.id())
                .containsExactly(bobsTrade.getId());
    }

    private User saveUser(String username) {
        return userRepository.save(User.builder()
                .username(username)
                .email(username + "@example.test")
                .password("encoded-test-password")
                .roles(Set.of(Role.USER))
                .build());
    }

    private Portfolio savePortfolio(User owner, String name) {
        return portfolioRepository.save(Portfolio.builder()
                .name(name + " portfolio")
                .cashBalance(new BigDecimal("10000.00"))
                .user(owner)
                .build());
    }

    private Transaction saveTransaction(Portfolio portfolio, Stock stock, TransactionStatus status) {
        return transactionRepository.save(Transaction.builder()
                .portfolio(portfolio)
                .stock(stock)
                .tradeType(TradeType.BUY)
                .quantity(BigDecimal.ONE)
                .unitPrice(new BigDecimal("200.00"))
                .totalAmount(new BigDecimal("200.00"))
                .realizedProfitLoss(BigDecimal.ZERO)
                .status(status)
                .executedAt(LocalDateTime.of(2026, 10, 14, 10, 0))
                .build());
    }

    private InvestmentJournalEntryRequest request(String title, List<String> tags, Long transactionId) {
        return new InvestmentJournalEntryRequest(
                title,
                "Strong fundamentals",
                "Stay with the plan",
                tags,
                transactionId);
    }
}
