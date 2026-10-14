package com.smartstock.app.service;

import com.smartstock.app.domain.*;
import com.smartstock.app.dto.HoldingResponse;
import com.smartstock.app.dto.PortfolioSummaryResponse;
import com.smartstock.app.dto.TradeRequest;
import com.smartstock.app.dto.TransactionResponse;
import com.smartstock.app.exception.InsufficientFundsException;
import com.smartstock.app.exception.InvalidTradeException;
import com.smartstock.app.exception.ResourceNotFoundException;
import com.smartstock.app.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
public class PortfolioService {

    private static final int MONEY_SCALE = 4;
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);

    private final UserRepository userRepository;
    private final PortfolioRepository portfolioRepository;
    private final HoldingRepository holdingRepository;
    private final TransactionRepository transactionRepository;
    private final StockRepository stockRepository;

    public PortfolioService(UserRepository userRepository,
                            PortfolioRepository portfolioRepository,
                            HoldingRepository holdingRepository,
                            TransactionRepository transactionRepository,
                            StockRepository stockRepository) {
        this.userRepository = userRepository;
        this.portfolioRepository = portfolioRepository;
        this.holdingRepository = holdingRepository;
        this.transactionRepository = transactionRepository;
        this.stockRepository = stockRepository;
    }

    @Transactional(readOnly = true)
    public List<PortfolioSummaryResponse> getPortfoliosForUser(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));
        return portfolioRepository.findByUser(user).stream()
                .map(this::summaryForPortfolio)
                .toList();
    }

    @Transactional
    public PortfolioSummaryResponse createPortfolio(String username, String name) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));
        if (name == null || name.isBlank()) {
            throw new InvalidTradeException("Portfolio name is required");
        }

        Portfolio portfolio = Portfolio.builder()
                .name(name)
                .cashBalance(new BigDecimal("100000.00"))
                .user(user)
                .build();

        Portfolio saved = portfolioRepository.save(portfolio);
        return summaryForPortfolio(saved);
    }

    @Transactional
    public PortfolioSummaryResponse buyStock(String username, Long portfolioId, TradeRequest request) {
        User user = validateUser(username);
        Portfolio portfolio = validatePortfolioForUpdate(user, portfolioId);
        Stock stock = findStock(request);
        BigDecimal quantity = validateQuantity(request.quantity());
        BigDecimal unitPrice = resolveExecutionPrice(stock);

        BigDecimal totalCost = calculateMoney(quantity.multiply(unitPrice));
        if (portfolio.getCashBalance().compareTo(totalCost) < 0) {
            throw new InsufficientFundsException("Insufficient cash balance to complete this purchase");
        }

        Holding holding = holdingRepository.findByPortfolioAndStock(portfolio, stock)
                .orElseGet(() -> Holding.builder()
                        .portfolio(portfolio)
                        .stock(stock)
                        .quantity(BigDecimal.ZERO)
                        .averageCost(BigDecimal.ZERO)
                        .build());

        BigDecimal resultingQuantity = holding.getQuantity().add(quantity);
        if (resultingQuantity.scale() > 6 || resultingQuantity.precision() - resultingQuantity.scale() > 13) {
            throw new InvalidTradeException("The resulting holding quantity exceeds the supported range");
        }
        holding.addShares(quantity, unitPrice);
        holdingRepository.save(holding);

        portfolio.debitCash(totalCost);

        Transaction transaction = Transaction.builder()
                .portfolio(portfolio)
                .stock(stock)
                .tradeType(TradeType.BUY)
                .quantity(quantity)
                .unitPrice(unitPrice)
                .totalAmount(totalCost)
                .realizedProfitLoss(ZERO)
                .status(TransactionStatus.COMPLETED)
                .executedAt(LocalDateTime.now())
                .build();
        transactionRepository.save(transaction);

        return summaryForPortfolio(portfolio);
    }

    @Transactional
    public PortfolioSummaryResponse sellStock(String username, Long portfolioId, TradeRequest request) {
        User user = validateUser(username);
        Portfolio portfolio = validatePortfolioForUpdate(user, portfolioId);
        Stock stock = findStock(request);
        BigDecimal quantity = validateQuantity(request.quantity());
        BigDecimal unitPrice = resolveExecutionPrice(stock);

        Holding holding = holdingRepository.findByPortfolioAndStock(portfolio, stock)
                .orElseThrow(() -> new InvalidTradeException("No holding exists for symbol: " + request.symbol()));

        if (holding.getQuantity().compareTo(quantity) < 0) {
            throw new InvalidTradeException("Cannot sell more than the current holdings");
        }

        BigDecimal proceeds = calculateMoney(quantity.multiply(unitPrice));
        calculateMoney(portfolio.getCashBalance().add(proceeds));
        BigDecimal realizedProfitLoss = proceeds.subtract(holding.costBasisFor(quantity));
        holding.removeShares(quantity);
        if (holding.getQuantity().compareTo(BigDecimal.ZERO) == 0) {
            holdingRepository.delete(holding);
        } else {
            holdingRepository.save(holding);
        }

        portfolio.creditCash(proceeds);

        Transaction transaction = Transaction.builder()
                .portfolio(portfolio)
                .stock(stock)
                .tradeType(TradeType.SELL)
                .quantity(quantity)
                .unitPrice(unitPrice)
                .totalAmount(proceeds)
                .realizedProfitLoss(realizedProfitLoss)
                .status(TransactionStatus.COMPLETED)
                .executedAt(LocalDateTime.now())
                .build();
        transactionRepository.save(transaction);

        return summaryForPortfolio(portfolio);
    }

    private User validateUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));
    }

    private Portfolio validatePortfolioForUpdate(User user, Long portfolioId) {
        return portfolioRepository.findByIdForUpdate(portfolioId)
                .filter(portfolio -> Objects.equals(portfolio.getUser().getId(), user.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Portfolio not found for user: " + user.getUsername()));
    }

    private Stock findStock(TradeRequest request) {
        return stockRepository.findBySymbol(request.symbol().trim().toUpperCase())
                .orElseThrow(() -> new ResourceNotFoundException("Stock not found: " + request.symbol()));
    }

    private BigDecimal validateQuantity(BigDecimal quantity) {
        if (quantity == null || quantity.signum() <= 0 || quantity.scale() > 6
                || quantity.precision() - quantity.scale() > 13) {
            throw new InvalidTradeException("Quantity must be positive, within the supported range, and have at most 6 decimal places");
        }
        return quantity;
    }

    private BigDecimal resolveExecutionPrice(Stock stock) {
        String source = stock.getMarketSource();
        if (source == null || source.isBlank()
                || source.equalsIgnoreCase("unavailable")
                || source.equalsIgnoreCase("unknown")
                || source.equalsIgnoreCase("not available")) {
            throw new InvalidTradeException(
                    "A valid server-side stock price is unavailable; this trade cannot be executed");
        }
        BigDecimal price = stock.getCurrentPrice();
        if (price == null || price.signum() <= 0 || price.scale() > 4
                || price.precision() - price.scale() > 15) {
            throw new InvalidTradeException(
                    "A valid server-side stock price is unavailable; this trade cannot be executed");
        }
        return price;
    }

    private BigDecimal calculateMoney(BigDecimal amount) {
        BigDecimal rounded = amount.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        if (rounded.precision() - rounded.scale() > 15) {
            throw new InvalidTradeException("Trade amount exceeds the supported monetary range");
        }
        return rounded;
    }

    private PortfolioSummaryResponse summaryForPortfolio(Portfolio portfolio) {
        List<HoldingResponse> holdings = holdingRepository.findAllByPortfolio(portfolio).stream()
                .map(holding -> {
                    BigDecimal marketPrice = holding.getStock().getCurrentPrice();
                    BigDecimal marketValue = calculateMoney(holding.getQuantity().multiply(marketPrice));
                    BigDecimal costBasis = holding.costBasisFor(holding.getQuantity());
                    BigDecimal unrealized = marketValue.subtract(costBasis);
                    return new HoldingResponse(
                            holding.getId(),
                            holding.getStock().getSymbol(),
                            holding.getStock().getCompanyName(),
                            holding.getQuantity(),
                            holding.getAverageCost(),
                            marketPrice,
                            marketValue,
                            unrealized
                    );
                })
                .toList();

        BigDecimal holdingsValue = holdings.stream()
                .map(HoldingResponse::marketValue)
                .reduce(ZERO, BigDecimal::add);
        BigDecimal unrealizedProfitLoss = holdings.stream()
                .map(HoldingResponse::unrealizedProfitLoss)
                .reduce(ZERO, BigDecimal::add);

        List<TransactionResponse> transactions = transactionRepository.findByPortfolioOrderByExecutedAtDesc(portfolio)
                .stream()
                .map(tx -> new TransactionResponse(
                        tx.getId(),
                        tx.getStock().getSymbol(),
                        tx.getTradeType(),
                        tx.getQuantity(),
                        tx.getUnitPrice(),
                        tx.getTotalAmount(),
                        tx.getRealizedProfitLoss(),
                        tx.getStatus(),
                        tx.getExecutedAt()))
                .toList();

        BigDecimal realizedProfitLoss = transactions.stream()
                .map(TransactionResponse::realizedProfitLoss)
                .reduce(ZERO, BigDecimal::add);
        BigDecimal totalProfitLoss = realizedProfitLoss.add(unrealizedProfitLoss);
        BigDecimal totalValue = portfolio.getCashBalance().add(holdingsValue);

        return new PortfolioSummaryResponse(
                portfolio.getId(),
                portfolio.getName(),
                portfolio.getCashBalance(),
                totalValue,
                totalProfitLoss,
                realizedProfitLoss,
                unrealizedProfitLoss,
                holdings,
                transactions
        );
    }
}
