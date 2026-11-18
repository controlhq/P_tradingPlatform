package com.ex.tradingPlatform.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "accounts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true, updatable = false)
    private User user;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal balance = BigDecimal.ZERO;

    @Column(name = "blocked_balance", nullable = false, precision = 19, scale = 4)
    private BigDecimal blockedBalance = BigDecimal.ZERO;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Account(User user, Instant now) {
        this.user = user;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public BigDecimal getAvailableBalance() {
        return balance.subtract(blockedBalance);
    }

    public void deposit(BigDecimal amount, Instant now) {
        this.balance = balance.add(amount);
        this.updatedAt = now;
    }

    /** Moves funds from available to blocked for a pending BUY order; callers check available funds first. */
    public void reserve(BigDecimal amount, Instant now) {
        if (amount.compareTo(getAvailableBalance()) > 0) {
            throw new IllegalStateException("Reservation exceeds available funds");
        }
        this.blockedBalance = blockedBalance.add(amount);
        this.updatedAt = now;
    }

    public void release(BigDecimal amount, Instant now) {
        if (amount.compareTo(blockedBalance) > 0) {
            throw new IllegalStateException("Release exceeds blocked funds");
        }
        this.blockedBalance = blockedBalance.subtract(amount);
        this.updatedAt = now;
    }

    /** Callers check available funds first; this guard only protects blocked funds from a bug. */
    public void withdraw(BigDecimal amount, Instant now) {
        if (amount.compareTo(getAvailableBalance()) > 0) {
            throw new IllegalStateException("Withdrawal exceeds available funds");
        }
        this.balance = balance.subtract(amount);
        this.updatedAt = now;
    }
}
