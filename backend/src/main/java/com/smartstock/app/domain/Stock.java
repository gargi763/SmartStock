package com.smartstock.app.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "stocks")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Stock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 10)
    private String symbol;

    @Column(nullable = false)
    private String companyName;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal currentPrice;

    @Column(precision = 19, scale = 4)
    private BigDecimal dayChange;

    @Column(precision = 19, scale = 4)
    private BigDecimal dayChangePercent;

    @Column(nullable = false)
    private String marketSource;

    @Column(nullable = false)
    private String sector;
}
