package com.smartstock.app.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "portfolios")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Portfolio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal cashBalance;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @OneToMany(mappedBy = "portfolio", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private Set<Holding> holdings = new HashSet<>();

    @OneToMany(mappedBy = "portfolio", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private Set<Transaction> transactions = new HashSet<>();

    public void debitCash(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Cash debit must be greater than zero");
        }
        if (cashBalance.compareTo(amount) < 0) {
            throw new IllegalArgumentException("Cash balance cannot become negative");
        }
        cashBalance = cashBalance.subtract(amount);
    }

    public void creditCash(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Cash credit must be greater than zero");
        }
        BigDecimal newBalance = cashBalance.add(amount);
        if (newBalance.scale() > 4 || newBalance.precision() - newBalance.scale() > 15) {
            throw new IllegalArgumentException("Cash balance exceeds the supported monetary range");
        }
        cashBalance = newBalance;
    }
}
