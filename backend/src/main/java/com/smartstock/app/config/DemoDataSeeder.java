package com.smartstock.app.config;

import com.smartstock.app.domain.Portfolio;
import com.smartstock.app.domain.Role;
import com.smartstock.app.domain.Stock;
import com.smartstock.app.domain.User;
import com.smartstock.app.repository.PortfolioRepository;
import com.smartstock.app.repository.StockRepository;
import com.smartstock.app.repository.UserRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Set;

@Component
@ConditionalOnProperty(name = "app.demo-data.enabled", havingValue = "true")
public class DemoDataSeeder implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PortfolioRepository portfolioRepository;
    private final StockRepository stockRepository;
    private final PasswordEncoder passwordEncoder;

    public DemoDataSeeder(UserRepository userRepository,
                          PortfolioRepository portfolioRepository,
                          StockRepository stockRepository,
                          PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.portfolioRepository = portfolioRepository;
        this.stockRepository = stockRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.count() == 0) {
            User demoUser = userRepository.save(User.builder()
                    .username("demo")
                    .email("demo@smartstock.app")
                    .password(passwordEncoder.encode("demo123"))
                    .roles(Set.of(Role.USER))
                    .build());

            portfolioRepository.save(Portfolio.builder()
                    .name("Main Portfolio")
                    .cashBalance(new BigDecimal("100000.00"))
                    .user(demoUser)
                    .build());
        }

        if (stockRepository.count() == 0) {
            stockRepository.save(Stock.builder()
                    .symbol("AAPL")
                    .companyName("Apple Inc.")
                    .currentPrice(new BigDecimal("214.86"))
                    .dayChange(new BigDecimal("2.34"))
                    .dayChangePercent(new BigDecimal("1.10"))
                    .marketSource("demo")
                    .sector("Technology")
                    .build());

            stockRepository.save(Stock.builder()
                    .symbol("MSFT")
                    .companyName("Microsoft Corporation")
                    .currentPrice(new BigDecimal("448.12"))
                    .dayChange(new BigDecimal("4.11"))
                    .dayChangePercent(new BigDecimal("0.92"))
                    .marketSource("demo")
                    .sector("Technology")
                    .build());

            stockRepository.save(Stock.builder()
                    .symbol("NVDA")
                    .companyName("NVIDIA Corporation")
                    .currentPrice(new BigDecimal("128.42"))
                    .dayChange(new BigDecimal("-1.53"))
                    .dayChangePercent(new BigDecimal("-1.18"))
                    .marketSource("demo")
                    .sector("Semiconductors")
                    .build());

            stockRepository.save(Stock.builder()
                    .symbol("AMZN")
                    .companyName("Amazon.com, Inc.")
                    .currentPrice(new BigDecimal("191.24"))
                    .dayChange(new BigDecimal("1.89"))
                    .dayChangePercent(new BigDecimal("1.00"))
                    .marketSource("demo")
                    .sector("Consumer Discretionary")
                    .build());
        }
    }
}
