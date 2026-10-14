package com.smartstock.app.repository;

import com.smartstock.app.domain.Portfolio;
import com.smartstock.app.domain.Stock;
import com.smartstock.app.domain.Transaction;
import com.smartstock.app.domain.TradeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    boolean existsByStock(Stock stock);
    long countByTradeType(TradeType tradeType);

    @EntityGraph(attributePaths = "stock")
    List<Transaction> findByPortfolioOrderByExecutedAtDesc(Portfolio portfolio);
}
