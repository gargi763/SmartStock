package com.smartstock.app.repository;

import com.smartstock.app.domain.Holding;
import com.smartstock.app.domain.Portfolio;
import com.smartstock.app.domain.Stock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.List;
import java.util.Optional;

public interface HoldingRepository extends JpaRepository<Holding, Long> {
    Optional<Holding> findByPortfolioAndStock(Portfolio portfolio, Stock stock);
    boolean existsByStock(Stock stock);

    @EntityGraph(attributePaths = "stock")
    List<Holding> findAllByPortfolio(Portfolio portfolio);
}
