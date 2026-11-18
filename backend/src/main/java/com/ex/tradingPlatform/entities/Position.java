package com.ex.tradingPlatform.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

/** Shares of one instrument held by one user. A row exists only while quantity > 0. */
@Entity
@Table(name = "positions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Position {

    private static final int PRICE_SCALE = 4;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "instrument_id", nullable = false, updatable = false)
    private Instrument instrument;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "blocked_quantity", nullable = false)
    private int blockedQuantity;

    @Column(name = "avg_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal avgPrice;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Position(User user, Instrument instrument, int quantity, BigDecimal price, Instant now) {
        this.user = user;
        this.instrument = instrument;
        this.quantity = quantity;
        this.avgPrice = price.setScale(PRICE_SCALE, RoundingMode.HALF_UP);
        this.createdAt = now;
        this.updatedAt = now;
    }

    public int getFreeQuantity() {
        return quantity - blockedQuantity;
    }

    /** Buying more shares moves the average price to the quantity-weighted average. */
    public void add(int shares, BigDecimal price, Instant now) {
        BigDecimal totalCost = avgPrice.multiply(BigDecimal.valueOf(quantity))
                .add(price.multiply(BigDecimal.valueOf(shares)));
        this.quantity += shares;
        this.avgPrice = totalCost.divide(BigDecimal.valueOf(quantity), PRICE_SCALE, RoundingMode.HALF_UP);
        this.updatedAt = now;
    }

    /** Reserves free shares for a pending SELL order so they cannot be sold or reserved twice. */
    public void block(int shares, Instant now) {
        if (shares > getFreeQuantity()) {
            throw new IllegalStateException("Cannot block more than the free quantity");
        }
        this.blockedQuantity += shares;
        this.updatedAt = now;
    }

    public void release(int shares, Instant now) {
        if (shares > blockedQuantity) {
            throw new IllegalStateException("Cannot release more than the blocked quantity");
        }
        this.blockedQuantity -= shares;
        this.updatedAt = now;
    }

    /** Selling keeps the average price; callers delete the position when it reaches zero. */
    public void remove(int shares, Instant now) {
        if (shares > getFreeQuantity()) {
            throw new IllegalStateException("Cannot remove more than the free quantity");
        }
        this.quantity -= shares;
        this.updatedAt = now;
    }
}
