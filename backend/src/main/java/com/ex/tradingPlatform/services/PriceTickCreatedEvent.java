package com.ex.tradingPlatform.services;

import java.math.BigDecimal;

/** Published for every new price tick; listeners act after the tick is committed. */
public record PriceTickCreatedEvent(Long instrumentId, BigDecimal price) {
}
