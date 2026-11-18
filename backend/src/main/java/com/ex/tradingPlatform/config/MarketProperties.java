package com.ex.tradingPlatform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Set;

@ConfigurationProperties("market")
public record MarketProperties(
        @DefaultValue("09:00") LocalTime openTime,
        @DefaultValue("17:00") LocalTime closeTime,
        @DefaultValue("Europe/Warsaw") ZoneId zone,
        @DefaultValue({"MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY"}) Set<DayOfWeek> tradingDays,
        @DefaultValue("false") boolean alwaysOpen,
        @DefaultValue("3000") long tickIntervalMs,
        @DefaultValue("0.005") double volatility,
        @DefaultValue("7") int tickRetentionDays
) {

    public MarketProperties {
        if (!openTime.isBefore(closeTime)) {
            throw new IllegalArgumentException("market.open-time must be before market.close-time");
        }
        if (tradingDays.isEmpty()) {
            throw new IllegalArgumentException("market.trading-days must not be empty");
        }
        if (tickIntervalMs <= 0) {
            throw new IllegalArgumentException("market.tick-interval-ms must be positive");
        }
        if (volatility <= 0 || volatility >= 1) {
            throw new IllegalArgumentException("market.volatility must be in (0, 1)");
        }
        if (tickRetentionDays < 1) {
            throw new IllegalArgumentException("market.tick-retention-days must be at least 1");
        }
        tradingDays = Set.copyOf(tradingDays);
    }
}
