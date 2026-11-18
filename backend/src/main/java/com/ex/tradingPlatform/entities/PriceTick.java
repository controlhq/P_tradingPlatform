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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "price_ticks")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PriceTick {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "instrument_id", nullable = false, updatable = false)
    private Instrument instrument;

    @Column(nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal price;

    @Column(nullable = false, updatable = false)
    private Instant ts;

    public PriceTick(Instrument instrument, BigDecimal price, Instant ts) {
        this.instrument = instrument;
        this.price = price;
        this.ts = ts;
    }
}
