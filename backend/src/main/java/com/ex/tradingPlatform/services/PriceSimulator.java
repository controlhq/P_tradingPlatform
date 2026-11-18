package com.ex.tradingPlatform.services;

import com.ex.tradingPlatform.config.MarketProperties;
import com.ex.tradingPlatform.entities.Instrument;
import com.ex.tradingPlatform.entities.PriceTick;
import com.ex.tradingPlatform.repositories.InstrumentRepository;
import com.ex.tradingPlatform.repositories.PriceTickRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Random walk of instrument prices: each tick moves the latest price by
 * {@code N(0,1) * volatility} percent.
 */
@Service
@RequiredArgsConstructor
public class PriceSimulator {

    static final BigDecimal MIN_PRICE = new BigDecimal("0.0001");
    private static final int PRICE_SCALE = 4;
    // caps a single tick at -50% so an extreme draw cannot wipe out the price
    private static final double MIN_FACTOR = 0.5;

    private final InstrumentRepository instrumentRepository;
    private final PriceTickRepository priceTickRepository;
    private final MarketHoursService marketHours;
    private final MarketProperties properties;
    private final Random random;
    private final Clock clock;
    private final ApplicationEventPublisher events;

    /** Generates one tick per active instrument that already has a price; returns the number of ticks saved. */
    @Transactional
    public int tick() {
        if (!marketHours.isOpen()) {
            return 0;
        }
        List<Instrument> instruments = instrumentRepository.findAllByActiveTrueOrderBySymbolAsc();
        if (instruments.isEmpty()) {
            return 0;
        }

        Map<Long, PriceTick> latestByInstrument = priceTickRepository
                .findLatestByInstrumentIds(instruments.stream().map(Instrument::getId).toList())
                .stream()
                .collect(Collectors.toMap(t -> t.getInstrument().getId(), Function.identity()));

        Instant now = clock.instant();
        List<PriceTick> ticks = instruments.stream()
                .filter(i -> latestByInstrument.containsKey(i.getId()))
                .map(i -> new PriceTick(i, nextPrice(latestByInstrument.get(i.getId()).getPrice()), now))
                .toList();
        priceTickRepository.saveAll(ticks);
        ticks.forEach(t -> events.publishEvent(new PriceTickCreatedEvent(t.getInstrument().getId(), t.getPrice())));
        return ticks.size();
    }

    private BigDecimal nextPrice(BigDecimal current) {
        double factor = Math.max(1 + random.nextGaussian() * properties.volatility(), MIN_FACTOR);
        BigDecimal next = current.multiply(BigDecimal.valueOf(factor)).setScale(PRICE_SCALE, RoundingMode.HALF_UP);
        return next.max(MIN_PRICE);
    }
}
