package com.ex.tradingPlatform.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import java.time.Instant;

@Entity
@Table(name = "orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "instrument_id", nullable = false, updatable = false)
    private Instrument instrument;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 4, updatable = false)
    private OrderSide side;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 6, updatable = false)
    private OrderType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private OrderStatus status = OrderStatus.NEW;

    @Column(nullable = false, updatable = false)
    private int quantity;

    @Column(name = "limit_price", precision = 19, scale = 4, updatable = false)
    private BigDecimal limitPrice;

    @Column(name = "stop_price", precision = 19, scale = 4, updatable = false)
    private BigDecimal stopPrice;

    /** Funds blocked for a pending BUY order; always 0 for SELL. */
    @Column(name = "reserved_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal reservedAmount = BigDecimal.ZERO;

    @Column(name = "filled_price", precision = 19, scale = 4)
    private BigDecimal filledPrice;

    @Column(name = "filled_at")
    private Instant filledAt;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Order(User user, Instrument instrument, OrderSide side, OrderType type, int quantity,
                 BigDecimal limitPrice, BigDecimal stopPrice, Instant now) {
        this.user = user;
        this.instrument = instrument;
        this.side = side;
        this.type = type;
        this.quantity = quantity;
        this.limitPrice = limitPrice;
        this.stopPrice = stopPrice;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** Records the funds blocked for this pending BUY order (mirrored in the account's blocked balance). */
    public void reserveFunds(BigDecimal amount) {
        if (side != OrderSide.BUY || status != OrderStatus.NEW) {
            throw new IllegalStateException("Only a NEW BUY order can reserve funds");
        }
        this.reservedAmount = amount;
    }

    /** Callers release the reserved funds or blocked shares before cancelling. */
    public void cancel(Instant now) {
        if (status != OrderStatus.NEW) {
            throw new IllegalStateException("Only a NEW order can be cancelled, was " + status);
        }
        this.status = OrderStatus.CANCELLED;
        this.reservedAmount = BigDecimal.ZERO;
        this.updatedAt = now;
    }

    public void fill(BigDecimal price, Instant now) {
        if (status != OrderStatus.NEW) {
            throw new IllegalStateException("Only a NEW order can be filled, was " + status);
        }
        this.status = OrderStatus.FILLED;
        this.filledPrice = price;
        this.filledAt = now;
        this.reservedAmount = BigDecimal.ZERO;
        this.updatedAt = now;
    }
}
