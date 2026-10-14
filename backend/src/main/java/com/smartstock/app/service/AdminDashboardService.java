package com.smartstock.app.service;

import com.smartstock.app.domain.Role;
import com.smartstock.app.domain.TradeType;
import com.smartstock.app.dto.AdminDashboardResponse;
import com.smartstock.app.repository.HoldingRepository;
import com.smartstock.app.repository.PortfolioRepository;
import com.smartstock.app.repository.StockRepository;
import com.smartstock.app.repository.TransactionRepository;
import com.smartstock.app.repository.UserRepository;
import com.smartstock.app.repository.WatchlistItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminDashboardService {

    private final UserRepository userRepository;
    private final StockRepository stockRepository;
    private final PortfolioRepository portfolioRepository;
    private final HoldingRepository holdingRepository;
    private final TransactionRepository transactionRepository;
    private final WatchlistItemRepository watchlistItemRepository;

    public AdminDashboardService(UserRepository userRepository,
                                 StockRepository stockRepository,
                                 PortfolioRepository portfolioRepository,
                                 HoldingRepository holdingRepository,
                                 TransactionRepository transactionRepository,
                                 WatchlistItemRepository watchlistItemRepository) {
        this.userRepository = userRepository;
        this.stockRepository = stockRepository;
        this.portfolioRepository = portfolioRepository;
        this.holdingRepository = holdingRepository;
        this.transactionRepository = transactionRepository;
        this.watchlistItemRepository = watchlistItemRepository;
    }

    @Transactional(readOnly = true)
    public AdminDashboardResponse getDashboard() {
        return new AdminDashboardResponse(
                userRepository.count(),
                userRepository.countUsersWithRole(Role.USER),
                userRepository.countUsersWithRole(Role.ADMIN),
                stockRepository.count(),
                portfolioRepository.count(),
                holdingRepository.count(),
                transactionRepository.count(),
                transactionRepository.countByTradeType(TradeType.BUY),
                transactionRepository.countByTradeType(TradeType.SELL),
                watchlistItemRepository.count(),
                stockRepository.countStocksWithAvailablePriceSource(),
                stockRepository.countStocksWithUnavailablePriceSource()
        );
    }
}
