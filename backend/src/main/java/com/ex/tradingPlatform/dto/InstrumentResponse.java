package com.ex.tradingPlatform.dto;

import com.ex.tradingPlatform.entities.Instrument;
import com.ex.tradingPlatform.entities.PriceTick;

import java.math.BigDecimal;
import java.time.Instant;

public record InstrumentResponse(
        Long id,
        String symbol,
        String name,
        boolean active,
        BigDecimal lastPrice,
        Instant lastPriceAt
) {

    /** {@code latestTick} may be null for an instrument that has no price yet. */
    public static InstrumentResponse from(Instrument instrument, PriceTick latestTick) {
        return new InstrumentResponse(
                instrument.getId(),
                instrument.getSymbol(),
                instrument.getName(),
                instrument.isActive(),
                latestTick != null ? latestTick.getPrice() : null,
                latestTick != null ? latestTick.getTs() : null);
    }
}
