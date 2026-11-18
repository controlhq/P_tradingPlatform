package com.ex.tradingPlatform.dto;

import java.math.BigDecimal;
import java.time.Instant;

/** Payload of the SSE "price" event. */
public record PriceStreamEvent(Long instrumentId, String symbol, BigDecimal price, Instant ts) {
}
