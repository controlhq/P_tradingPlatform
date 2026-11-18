package com.ex.tradingPlatform.services;

import com.ex.tradingPlatform.config.MarketProperties;
import com.ex.tradingPlatform.repositories.PriceTickRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class PriceTickRetentionService {

    private final PriceTickRepository priceTickRepository;
    private final MarketProperties properties;
    private final Clock clock;

    /** Deletes expired ticks, always keeping the latest tick of each instrument; returns the number deleted. */
    @Transactional
    public int purgeExpired() {
        Instant cutoff = clock.instant().minus(Duration.ofDays(properties.tickRetentionDays()));
        int deleted = priceTickRepository.deleteOlderThanExceptLatest(cutoff);
        if (deleted > 0) {
            log.info("Deleted {} price ticks older than {}", deleted, cutoff);
        }
        return deleted;
    }
}
