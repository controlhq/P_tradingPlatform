package com.ex.tradingPlatform.dto;

import java.math.BigDecimal;

/** Valuation fields are null only if the instrument has no price at all. */
public record PositionResponse(
        Long instrumentId,
        String symbol,
        int quantity,
        int blockedQuantity,
        BigDecimal avgPrice,
        BigDecimal currentPrice,
        BigDecimal marketValue,
        BigDecimal unrealizedPnl,
        BigDecimal unrealizedPnlPct
) {
}
