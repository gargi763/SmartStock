package com.smartstock.app.service;

import com.smartstock.app.domain.Stock;
import com.smartstock.app.domain.User;
import com.smartstock.app.domain.WatchlistItem;
import com.smartstock.app.dto.WatchlistItemResponse;
import com.smartstock.app.exception.DuplicateWatchlistItemException;
import com.smartstock.app.exception.InvalidWatchlistSymbolException;
import com.smartstock.app.exception.ResourceNotFoundException;
import com.smartstock.app.repository.StockRepository;
import com.smartstock.app.repository.UserRepository;
import com.smartstock.app.repository.WatchlistItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
public class WatchlistService {

    private static final String SYMBOL_PATTERN = "[A-Z0-9.^-]{1,10}";

    private final UserRepository userRepository;
    private final StockRepository stockRepository;
    private final WatchlistItemRepository watchlistItemRepository;

    public WatchlistService(UserRepository userRepository,
                            StockRepository stockRepository,
                            WatchlistItemRepository watchlistItemRepository) {
        this.userRepository = userRepository;
        this.stockRepository = stockRepository;
        this.watchlistItemRepository = watchlistItemRepository;
    }

    @Transactional(readOnly = true)
    public List<WatchlistItemResponse> getWatchlist(String username) {
        User user = findUser(username);
        return watchlistItemRepository.findByUserOrderByIdDesc(user).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public WatchlistItemResponse addStock(String username, String requestedSymbol) {
        User user = findUser(username);
        String symbol = normalizeSymbol(requestedSymbol);
        Stock stock = stockRepository.findBySymbol(symbol)
                .orElseThrow(() -> new ResourceNotFoundException("Stock not found: " + symbol));

        if (watchlistItemRepository.findByUserAndStock_Symbol(user, symbol).isPresent()) {
            throw new DuplicateWatchlistItemException(symbol);
        }

        WatchlistItem item = watchlistItemRepository.save(WatchlistItem.builder()
                .user(user)
                .stock(stock)
                .build());
        return toResponse(item);
    }

    @Transactional
    public void removeStock(String username, String requestedSymbol) {
        User user = findUser(username);
        String symbol = normalizeSymbol(requestedSymbol);
        WatchlistItem item = watchlistItemRepository.findByUserAndStock_Symbol(user, symbol)
                .orElseThrow(() -> new ResourceNotFoundException("Stock not found in watchlist: " + symbol));
        watchlistItemRepository.delete(item);
    }

    private User findUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));
    }

    private String normalizeSymbol(String requestedSymbol) {
        if (requestedSymbol == null) {
            throw new InvalidWatchlistSymbolException();
        }
        String symbol = requestedSymbol.trim().toUpperCase(Locale.ROOT);
        if (!symbol.matches(SYMBOL_PATTERN)) {
            throw new InvalidWatchlistSymbolException();
        }
        return symbol;
    }

    private WatchlistItemResponse toResponse(WatchlistItem item) {
        Stock stock = item.getStock();
        return new WatchlistItemResponse(
                item.getId(),
                stock.getSymbol(),
                stock.getCompanyName(),
                stock.getSector(),
                stock.getCurrentPrice(),
                stock.getDayChange(),
                stock.getDayChangePercent(),
                stock.getMarketSource()
        );
    }
}
