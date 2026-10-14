package com.smartstock.app.repository;

import com.smartstock.app.domain.Portfolio;
import com.smartstock.app.domain.Stock;
import com.smartstock.app.domain.Transaction;
import com.smartstock.app.domain.TransactionStatus;
import com.smartstock.app.domain.TradeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.List;
import java.util.Optional;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    boolean existsByStock(Stock stock);
    long countByTradeType(TradeType tradeType);

    @EntityGraph(attributePaths = "stock")
    List<Transaction> findByPortfolioOrderByExecutedAtDesc(Portfolio portfolio);

    @EntityGraph(attributePaths = "stock")
    List<Transaction> findByPortfolio_User_UsernameAndStatusOrderByExecutedAtDesc(
            String username, TransactionStatus status);

    @EntityGraph(attributePaths = "stock")
    Optional<Transaction> findByIdAndPortfolio_User_UsernameAndStatus(
            Long id, String username, TransactionStatus status);
}
