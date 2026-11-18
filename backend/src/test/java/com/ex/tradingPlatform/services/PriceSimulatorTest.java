package com.ex.tradingPlatform.services;

import com.ex.tradingPlatform.IntegrationTest;
import com.ex.tradingPlatform.config.MarketProperties;
import com.ex.tradingPlatform.repositories.InstrumentRepository;
import com.ex.tradingPlatform.repositories.PriceTickRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class PriceSimulatorTest extends IntegrationTest {

    private static final Instant T0 = Instant.parse("2026-11-18T09:00:00Z");      // Wednesday, open
    private static final Instant SATURDAY = Instant.parse("2026-11-21T12:00:00Z");

    @Autowired
    private InstrumentRepository instrumentRepository;

    @Autowired
    private PriceTickRepository priceTickRepository;

    @Test
    void BR24_tick_movesLatestPriceByGaussianTimesVolatility() {
        long id = insertInstrument("AAPL", true);
        insertTick(id, "100.0000", T0.minusSeconds(3));

        int generated = simulator(T0, false, fixedGaussian(2.0)).tick();

        assertThat(generated).isEqualTo(1);
        assertThat(prices(id)).containsExactly(new BigDecimal("100.0000"), new BigDecimal("101.0000"));
        assertThat(latestTs(id)).isEqualTo(T0);
    }

    @Test
    void BR24_tick_withExtremeNegativeDraws_neverProducesPriceAtOrBelowZero() {
        long id = insertInstrument("PKO", true);
        insertTick(id, "50.0000", T0.minusSeconds(3));
        PriceSimulator simulator = simulator(T0, false, fixedGaussian(-1_000_000));

        for (int i = 0; i < 30; i++) {
            simulator.tick();
        }

        List<BigDecimal> prices = prices(id);
        assertThat(prices).hasSize(31).allMatch(p -> p.signum() > 0);
        assertThat(prices.get(1)).isEqualByComparingTo("25.0000");
        assertThat(prices.getLast()).isEqualByComparingTo(PriceSimulator.MIN_PRICE);
    }

    @Test
    void BR24_tick_withSeededRandom_isDeterministicAndPositive() {
        long id = insertInstrument("AAPL", true);
        insertTick(id, "0.0002", T0.minusSeconds(3));

        List<BigDecimal> firstRun = runTicks(new Random(42), 200);
        jdbc.update("DELETE FROM price_ticks WHERE ts >= ?", Timestamp.from(T0));
        List<BigDecimal> secondRun = runTicks(new Random(42), 200);

        assertThat(firstRun).hasSize(201).allMatch(p -> p.signum() > 0);
        assertThat(secondRun).isEqualTo(firstRun);
    }

    private List<BigDecimal> runTicks(Random random, int count) {
        Long id = jdbc.queryForObject("SELECT id FROM instruments", Long.class);
        for (int i = 0; i < count; i++) {
            simulator(T0.plusSeconds(i), false, random).tick();
        }
        return prices(id);
    }

    @Test
    void BR24_tick_skipsInactiveInstrumentsAndInstrumentsWithoutPrice() {
        long active = insertInstrument("AAPL", true);
        long inactive = insertInstrument("META", false);
        long noPrice = insertInstrument("NVDA", true);
        insertTick(active, "10.0000", T0.minusSeconds(3));
        insertTick(inactive, "10.0000", T0.minusSeconds(3));

        int generated = simulator(T0, false, fixedGaussian(0.0)).tick();

        assertThat(generated).isEqualTo(1);
        assertThat(prices(active)).hasSize(2);
        assertThat(prices(inactive)).hasSize(1);
        assertThat(prices(noPrice)).isEmpty();
    }

    @Test
    void BR24_tick_whenMarketClosed_generatesNothing() {
        long id = insertInstrument("AAPL", true);
        insertTick(id, "10.0000", T0);

        int generated = simulator(SATURDAY, false, fixedGaussian(1.0)).tick();

        assertThat(generated).isZero();
        assertThat(prices(id)).hasSize(1);
    }

    @Test
    void BR24_tick_whenAlwaysOpen_generatesOnWeekend() {
        long id = insertInstrument("AAPL", true);
        insertTick(id, "10.0000", T0);

        assertThat(simulator(SATURDAY, true, fixedGaussian(1.0)).tick()).isEqualTo(1);
    }

    private PriceSimulator simulator(Instant now, boolean alwaysOpen, Random random) {
        MarketProperties properties = new MarketProperties(
                LocalTime.of(9, 0), LocalTime.of(17, 0), ZoneId.of("Europe/Warsaw"),
                EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.FRIDAY), alwaysOpen, 3000, 0.005, 7);
        Clock clock = Clock.fixed(now, ZoneOffset.UTC);
        return new PriceSimulator(instrumentRepository, priceTickRepository,
                new MarketHoursService(properties, clock), properties, random, clock, event -> { });
    }

    private static Random fixedGaussian(double value) {
        return new Random() {
            @Override
            public double nextGaussian() {
                return value;
            }
        };
    }

    private long insertInstrument(String symbol, boolean active) {
        return jdbc.queryForObject(
                "INSERT INTO instruments (symbol, name, active) VALUES (?, ?, ?) RETURNING id",
                Long.class, symbol, symbol + " Corp", active);
    }

    private void insertTick(long instrumentId, String price, Instant ts) {
        jdbc.update("INSERT INTO price_ticks (instrument_id, price, ts) VALUES (?, ?, ?)",
                instrumentId, new BigDecimal(price), Timestamp.from(ts));
    }

    private List<BigDecimal> prices(long instrumentId) {
        return jdbc.queryForList("SELECT price FROM price_ticks WHERE instrument_id = ? ORDER BY ts, id",
                BigDecimal.class, instrumentId);
    }

    private Instant latestTs(long instrumentId) {
        return jdbc.queryForObject("SELECT max(ts) FROM price_ticks WHERE instrument_id = ?",
                Timestamp.class, instrumentId).toInstant();
    }
}
