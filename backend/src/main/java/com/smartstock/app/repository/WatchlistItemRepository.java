package com.smartstock.app.repository;

import com.smartstock.app.domain.User;
import com.smartstock.app.domain.Stock;
import com.smartstock.app.domain.WatchlistItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WatchlistItemRepository extends JpaRepository<WatchlistItem, Long> {
    List<WatchlistItem> findByUserOrderByIdDesc(User user);
    Optional<WatchlistItem> findByUserAndStock_Symbol(User user, String stockSymbol);
    boolean existsByStock(Stock stock);
}
