package com.smartstock.app.repository;

import com.smartstock.app.domain.Stock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface StockRepository extends JpaRepository<Stock, Long> {
    Optional<Stock> findBySymbol(String symbol);
    boolean existsBySymbol(String symbol);

    @Query("""
            select count(stock) from Stock stock
            where lower(trim(stock.marketSource)) not in ('unavailable', 'unknown', 'not available')
              and trim(stock.marketSource) <> ''
              and stock.currentPrice > 0
            """)
    long countStocksWithAvailablePriceSource();

    @Query("""
            select count(stock) from Stock stock
            where stock.marketSource is null
               or trim(stock.marketSource) = ''
               or lower(trim(stock.marketSource)) in ('unavailable', 'unknown', 'not available')
               or stock.currentPrice is null
               or stock.currentPrice <= 0
            """)
    long countStocksWithUnavailablePriceSource();
}
