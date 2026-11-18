package com.ex.tradingPlatform.dto;

import java.time.Instant;

/** {@code nextChange} is null when the market is configured as always open. */
public record MarketStatusResponse(boolean open, Instant nextChange) {
}
