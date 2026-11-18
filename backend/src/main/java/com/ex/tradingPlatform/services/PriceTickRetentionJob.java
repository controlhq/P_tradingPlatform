package com.ex.tradingPlatform.services;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "market.retention-enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class PriceTickRetentionJob {

    private final PriceTickRetentionService retentionService;

    @Scheduled(cron = "0 0 * * * *")
    public void run() {
        retentionService.purgeExpired();
    }
}
