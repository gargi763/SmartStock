package com.smartstock.app.service;

import com.smartstock.app.domain.Role;
import com.smartstock.app.domain.Stock;
import com.smartstock.app.domain.User;
import com.smartstock.app.dto.WatchlistItemResponse;
import com.smartstock.app.exception.DuplicateWatchlistItemException;
import com.smartstock.app.exception.InvalidWatchlistSymbolException;
import com.smartstock.app.exception.ResourceNotFoundException;
import com.smartstock.app.repository.StockRepository;
import com.smartstock.app.repository.UserRepository;
import com.smartstock.app.repository.WatchlistItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import(WatchlistService.class)
class WatchlistServiceTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private WatchlistItemRepository watchlistItemRepository;

    @Autowired
    private WatchlistService watchlistService;

    private User alice;
    private User bob;

    @BeforeEach
    void setUp() {
        alice = saveUser("alice");
        bob = saveUser("bob");
        stockRepository.save(Stock.builder()
                .symbol("AAPL")
                .companyName("Apple Inc")
                .sector("Technology")
                .currentPrice(new BigDecimal("200.00"))
                .marketSource("demo")
                .build());
        stockRepository.save(Stock.builder()
                .symbol("MSFT")
                .companyName("Microsoft Corporation")
                .sector("Technology")
                .currentPrice(new BigDecimal("400.00"))
                .marketSource("demo")
                .build());
    }

    @Test
    void addStock_normalizesSymbolAndReturnsStockDetails() {
        WatchlistItemResponse response = watchlistService.addStock("alice", " aapl ");

        assertThat(response.symbol()).isEqualTo("AAPL");
        assertThat(response.companyName()).isEqualTo("Apple Inc");
        assertThat(response.currentPrice()).isEqualByComparingTo("200.00");
        assertThat(response.marketSource()).isEqualTo("demo");
        assertThat(watchlistService.getWatchlist("alice")).containsExactly(response);
    }

    @Test
    void addStock_whenAlreadyOnUsersWatchlist_throwsDuplicate() {
        watchlistService.addStock("alice", "AAPL");

        assertThatThrownBy(() -> watchlistService.addStock("alice", "aapl"))
                .isInstanceOf(DuplicateWatchlistItemException.class)
                .hasMessageContaining("AAPL");
        assertThat(watchlistItemRepository.findByUserOrderByIdDesc(alice)).hasSize(1);
    }

    @Test
    void addStock_whenSymbolIsUnknown_throwsNotFound() {
        assertThatThrownBy(() -> watchlistService.addStock("alice", "UNKNOWN"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("UNKNOWN");
        assertThat(watchlistService.getWatchlist("alice")).isEmpty();
    }

    @Test
    void addAndRemoveStock_areIsolatedPerAuthenticatedUser() {
        watchlistService.addStock("bob", "MSFT");

        assertThat(watchlistService.getWatchlist("alice")).isEmpty();
        assertThat(watchlistService.getWatchlist("bob")).extracting(WatchlistItemResponse::symbol)
                .containsExactly("MSFT");
        assertThatThrownBy(() -> watchlistService.removeStock("alice", "MSFT"))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThat(watchlistService.getWatchlist("bob")).hasSize(1);

        watchlistService.addStock("alice", "MSFT");
        watchlistService.removeStock("alice", "msft");

        assertThat(watchlistService.getWatchlist("alice")).isEmpty();
        assertThat(watchlistService.getWatchlist("bob")).hasSize(1);
    }

    @Test
    void addAndRemoveStock_rejectInvalidSymbols() {
        assertThatThrownBy(() -> watchlistService.addStock("alice", "invalid symbol"))
                .isInstanceOf(InvalidWatchlistSymbolException.class);
        assertThatThrownBy(() -> watchlistService.removeStock("alice", "bad/symbol"))
                .isInstanceOf(InvalidWatchlistSymbolException.class);
    }

    private User saveUser(String username) {
        return userRepository.save(User.builder()
                .username(username)
                .email(username + "@test.com")
                .password("pw")
                .roles(Set.of(Role.USER))
                .build());
    }
}
