package com.smartstock.app.service;

import com.smartstock.app.domain.Stock;
import com.smartstock.app.dto.AdminStockRequest;
import com.smartstock.app.dto.StockResponse;
import com.smartstock.app.dto.UpdateStockMetadataRequest;
import com.smartstock.app.exception.DuplicateStockException;
import com.smartstock.app.exception.InvalidStockSymbolException;
import com.smartstock.app.exception.ResourceNotFoundException;
import com.smartstock.app.exception.StockDeletionNotAllowedException;
import com.smartstock.app.repository.HoldingRepository;
import com.smartstock.app.repository.StockRepository;
import com.smartstock.app.repository.TransactionRepository;
import com.smartstock.app.repository.WatchlistItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class AdminStockService {

    private static final String UNAVAILABLE_PRICE_SOURCE = "unavailable";
    private static final Pattern VALID_SYMBOL = Pattern.compile("[A-Z0-9.^-]{1,10}");

    private final StockRepository stockRepository;
    private final HoldingRepository holdingRepository;
    private final TransactionRepository transactionRepository;
    private final WatchlistItemRepository watchlistItemRepository;

    public AdminStockService(StockRepository stockRepository,
                             HoldingRepository holdingRepository,
                             TransactionRepository transactionRepository,
                             WatchlistItemRepository watchlistItemRepository) {
        this.stockRepository = stockRepository;
        this.holdingRepository = holdingRepository;
        this.transactionRepository = transactionRepository;
        this.watchlistItemRepository = watchlistItemRepository;
    }

    @Transactional(readOnly = true)
    public List<StockResponse> getStocks() {
        return stockRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public StockResponse getStock(String requestedSymbol) {
        return toResponse(findStock(normalizeSymbol(requestedSymbol)));
    }

    @Transactional
    public StockResponse createStock(AdminStockRequest request) {
        String symbol = normalizeSymbol(request.symbol());
        if (stockRepository.existsBySymbol(symbol)) {
            throw new DuplicateStockException(symbol);
        }

        Stock stock = Stock.builder()
                .symbol(symbol)
                .companyName(request.companyName().trim())
                .sector(request.sector().trim())
                .currentPrice(BigDecimal.ZERO)
                .marketSource(UNAVAILABLE_PRICE_SOURCE)
                .build();
        return toResponse(stockRepository.save(stock));
    }

    @Transactional
    public StockResponse updateMetadata(String requestedSymbol, UpdateStockMetadataRequest request) {
        Stock stock = findStock(normalizeSymbol(requestedSymbol));
        stock.setCompanyName(request.companyName().trim());
        stock.setSector(request.sector().trim());
        return toResponse(stockRepository.save(stock));
    }

    @Transactional
    public void deleteStock(String requestedSymbol) {
        Stock stock = findStock(normalizeSymbol(requestedSymbol));
        if (holdingRepository.existsByStock(stock)
                || transactionRepository.existsByStock(stock)
                || watchlistItemRepository.existsByStock(stock)) {
            throw new StockDeletionNotAllowedException(stock.getSymbol());
        }
        stockRepository.delete(stock);
    }

    private Stock findStock(String symbol) {
        return stockRepository.findBySymbol(symbol)
                .orElseThrow(() -> new ResourceNotFoundException("Stock not found: " + symbol));
    }

    private String normalizeSymbol(String requestedSymbol) {
        if (requestedSymbol == null) {
            throw new InvalidStockSymbolException();
        }
        String symbol = requestedSymbol.trim().toUpperCase(Locale.ROOT);
        if (!VALID_SYMBOL.matcher(symbol).matches()) {
            throw new InvalidStockSymbolException();
        }
        return symbol;
    }

    private StockResponse toResponse(Stock stock) {
        return new StockResponse(
                stock.getId(),
                stock.getSymbol(),
                stock.getCompanyName(),
                stock.getCurrentPrice(),
                stock.getDayChange(),
                stock.getDayChangePercent(),
                stock.getMarketSource(),
                stock.getSector()
        );
    }
}
