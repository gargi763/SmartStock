package com.smartstock.app.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "holdings", uniqueConstraints = @UniqueConstraint(columnNames = {"portfolio_id", "stock_id"}))
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Holding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "portfolio_id", nullable = false)
    private Portfolio portfolio;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_id", nullable = false)
    private Stock stock;

    @Column(nullable = false, precision = 19, scale = 6)
    private BigDecimal quantity;

    @Column(nullable = false, precision = 23, scale = 8)
    private BigDecimal averageCost;

    public void addShares(BigDecimal addedQuantity, BigDecimal purchasePrice) {
        if (addedQuantity == null || addedQuantity.signum() <= 0
                || purchasePrice == null || purchasePrice.signum() <= 0) {
            throw new IllegalArgumentException("Quantity and purchase price must be greater than zero");
        }
        if (addedQuantity.scale() > 6 || purchasePrice.scale() > 4) {
            throw new IllegalArgumentException("Quantity or purchase price exceeds supported precision");
        }

        BigDecimal newQuantity = quantity.add(addedQuantity);
        if (newQuantity.scale() > 6 || newQuantity.precision() - newQuantity.scale() > 13) {
            throw new IllegalArgumentException("Holding quantity exceeds the supported range");
        }
        averageCost = quantity.multiply(averageCost)
                .add(addedQuantity.multiply(purchasePrice))
                .divide(newQuantity, 8, java.math.RoundingMode.HALF_UP);
        quantity = newQuantity;
    }

    public void removeShares(BigDecimal removedQuantity) {
        if (removedQuantity == null || removedQuantity.signum() <= 0) {
            throw new IllegalArgumentException("Quantity to remove must be greater than zero");
        }
        if (quantity.compareTo(removedQuantity) < 0) {
            throw new IllegalArgumentException("Cannot remove more shares than are held");
        }
        quantity = quantity.subtract(removedQuantity);
    }

    public BigDecimal costBasisFor(BigDecimal shares) {
        return shares.multiply(averageCost).setScale(4, java.math.RoundingMode.HALF_UP);
    }
}
