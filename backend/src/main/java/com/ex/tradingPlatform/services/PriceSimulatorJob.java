package com.ex.tradingPlatform.services;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Kept separate from {@link PriceSimulator} so the simulator can be switched off while staying testable. */
@Component
@ConditionalOnProperty(name = "market.simulator-enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class PriceSimulatorJob {

    private final PriceSimulator priceSimulator;

    @Scheduled(fixedDelayString = "${market.tick-interval-ms}")
    public void run() {
        priceSimulator.tick();
    }
}
