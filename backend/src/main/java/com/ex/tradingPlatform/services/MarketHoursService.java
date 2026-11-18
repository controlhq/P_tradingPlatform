package com.ex.tradingPlatform.services;

import com.ex.tradingPlatform.config.MarketProperties;
import com.ex.tradingPlatform.dto.MarketStatusResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class MarketHoursService {

    private final MarketProperties properties;
    private final Clock clock;

    /** Open on trading days from open-time (inclusive) to close-time (exclusive), in the market zone. */
    public boolean isOpen() {
        return properties.alwaysOpen() || isOpenAt(now());
    }

    public MarketStatusResponse status() {
        return new MarketStatusResponse(isOpen(), nextChange().orElse(null));
    }

    /** When the market opens or closes next; empty when it is configured as always open. */
    public Optional<Instant> nextChange() {
        if (properties.alwaysOpen()) {
            return Optional.empty();
        }
        ZonedDateTime now = now();
        if (isOpenAt(now)) {
            return Optional.of(at(now.toLocalDate(), properties.closeTime()));
        }
        LocalDate day = now.toLocalTime().isBefore(properties.openTime())
                ? now.toLocalDate()
                : now.toLocalDate().plusDays(1);
        while (!properties.tradingDays().contains(day.getDayOfWeek())) {
            day = day.plusDays(1);
        }
        return Optional.of(at(day, properties.openTime()));
    }

    private boolean isOpenAt(ZonedDateTime time) {
        LocalTime local = time.toLocalTime();
        return properties.tradingDays().contains(time.getDayOfWeek())
                && !local.isBefore(properties.openTime())
                && local.isBefore(properties.closeTime());
    }

    private ZonedDateTime now() {
        return ZonedDateTime.now(clock.withZone(properties.zone()));
    }

    private Instant at(LocalDate day, LocalTime time) {
        return ZonedDateTime.of(day, time, properties.zone()).toInstant();
    }
}
