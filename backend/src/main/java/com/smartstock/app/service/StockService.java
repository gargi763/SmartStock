package com.smartstock.app.service;

import com.smartstock.app.domain.Stock;
import com.smartstock.app.dto.StockResponse;
import com.smartstock.app.exception.ResourceNotFoundException;
import com.smartstock.app.repository.StockRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
public class StockService {

    private final StockRepository stockRepository;

    public StockService(StockRepository stockRepository) {
        this.stockRepository = stockRepository;
    }

    public List<StockResponse> getAllStocks() {
        return stockRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    public StockResponse getStock(String symbol) {
        Stock stock = stockRepository.findBySymbol(symbol.toUpperCase())
                .orElseThrow(() -> new ResourceNotFoundException("Stock not found: " + symbol));
        return toResponse(stock);
    }

    public Stock createOrUpdateDemoStock(String symbol, String companyName, String sector, BigDecimal price) {
        return stockRepository.findBySymbol(symbol.toUpperCase())
                .map(existing -> {
                    existing.setCurrentPrice(price);
                    existing.setCompanyName(companyName);
                    existing.setSector(sector);
                    existing.setMarketSource("demo");
                    return stockRepository.save(existing);
                })
                .orElseGet(() -> stockRepository.save(Stock.builder()
                        .symbol(symbol.toUpperCase())
                        .companyName(companyName)
                        .sector(sector)
                        .currentPrice(price)
                        .marketSource("demo")
                        .build()));
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
