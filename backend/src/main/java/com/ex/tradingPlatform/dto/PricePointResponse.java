package com.ex.tradingPlatform.dto;

import com.ex.tradingPlatform.entities.PriceTick;

import java.math.BigDecimal;
import java.time.Instant;

public record PricePointResponse(BigDecimal price, Instant ts) {

    public static PricePointResponse from(PriceTick tick) {
        return new PricePointResponse(tick.getPrice(), tick.getTs());
    }
}
