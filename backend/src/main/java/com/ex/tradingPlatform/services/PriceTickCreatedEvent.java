package com.ex.tradingPlatform.services;

import java.math.BigDecimal;
import java.time.Instant;

/** Published for every new price tick; listeners act after the tick is committed. */
public record PriceTickCreatedEvent(Long instrumentId, String symbol, BigDecimal price, Instant ts) {
}
