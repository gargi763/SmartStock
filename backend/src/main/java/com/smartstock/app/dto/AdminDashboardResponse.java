package com.smartstock.app.dto;

public record AdminDashboardResponse(
        long totalRegisteredUsers,
        long usersWithUserRole,
        long usersWithAdminRole,
        long totalStocks,
        long totalPortfolios,
        long totalHoldings,
        long totalVirtualTransactions,
        long buyTransactions,
        long sellTransactions,
        long totalWatchlistEntries,
        long stocksWithAvailablePriceSource,
        long stocksWithUnavailablePriceSource
) {}
