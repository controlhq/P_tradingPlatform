package com.ex.tradingPlatform.services;

import com.ex.tradingPlatform.IntegrationTest;
import com.ex.tradingPlatform.config.MarketProperties;
import com.ex.tradingPlatform.repositories.PriceTickRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

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

import static org.assertj.core.api.Assertions.assertThat;

class PriceTickRetentionServiceTest extends IntegrationTest {

    private static final Instant NOW = Instant.parse("2026-11-18T12:00:00Z");
    private static final Instant CUTOFF = Instant.parse("2026-11-11T12:00:00Z");   // NOW - 7 days

    @Autowired
    private PriceTickRepository priceTickRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void BR26_purge_deletesTicksOlderThanRetentionPeriod() {
        long id = insertInstrument("AAPL");
        insertTick(id, "1.0000", Instant.parse("2026-11-01T00:00:00Z"));
        insertTick(id, "2.0000", CUTOFF.minusSeconds(1));
        insertTick(id, "3.0000", CUTOFF);
        insertTick(id, "4.0000", NOW.minusSeconds(60));

        int deleted = purge();

        assertThat(deleted).isEqualTo(2);
        assertThat(prices(id)).containsExactly(new BigDecimal("3.0000"), new BigDecimal("4.0000"));
    }

    @Test
    void BR26_purge_alwaysKeepsLatestTickOfEachInstrument() {
        long stale = insertInstrument("PKO");
        long fresh = insertInstrument("MSFT");
        insertTick(stale, "10.0000", Instant.parse("2026-10-01T00:00:00Z"));
        insertTick(stale, "11.0000", Instant.parse("2026-10-02T00:00:00Z"));
        insertTick(fresh, "20.0000", Instant.parse("2026-10-01T00:00:00Z"));
        insertTick(fresh, "21.0000", NOW);

        purge();

        assertThat(prices(stale)).containsExactly(new BigDecimal("11.0000"));
        assertThat(prices(fresh)).containsExactly(new BigDecimal("21.0000"));
    }

    @Test
    void BR26_purge_withEqualTimestamps_keepsTickWithHighestId() {
        long id = insertInstrument("AAPL");
        Instant old = Instant.parse("2026-10-01T00:00:00Z");
        insertTick(id, "1.0000", old);
        insertTick(id, "2.0000", old);

        assertThat(purge()).isEqualTo(1);
        assertThat(prices(id)).containsExactly(new BigDecimal("2.0000"));
    }

    @Test
    void BR26_purge_withNothingExpired_deletesNothing() {
        long id = insertInstrument("AAPL");
        insertTick(id, "1.0000", CUTOFF);
        insertTick(id, "2.0000", NOW);

        assertThat(purge()).isZero();
        assertThat(prices(id)).hasSize(2);
    }

    private int purge() {
        MarketProperties properties = new MarketProperties(
                LocalTime.of(9, 0), LocalTime.of(17, 0), ZoneId.of("Europe/Warsaw"),
                EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.FRIDAY), false, 3000, 0.005, 7);
        PriceTickRetentionService service = new PriceTickRetentionService(
                priceTickRepository, properties, Clock.fixed(NOW, ZoneOffset.UTC));
        // built by hand for the fixed clock, so it needs its own transaction for the bulk delete
        return new TransactionTemplate(transactionManager).execute(status -> service.purgeExpired());
    }

    private long insertInstrument(String symbol) {
        return jdbc.queryForObject(
                "INSERT INTO instruments (symbol, name) VALUES (?, ?) RETURNING id",
                Long.class, symbol, symbol + " Corp");
    }

    private void insertTick(long instrumentId, String price, Instant ts) {
        jdbc.update("INSERT INTO price_ticks (instrument_id, price, ts) VALUES (?, ?, ?)",
                instrumentId, new BigDecimal(price), Timestamp.from(ts));
    }

    private List<BigDecimal> prices(long instrumentId) {
        return jdbc.queryForList("SELECT price FROM price_ticks WHERE instrument_id = ? ORDER BY ts, id",
                BigDecimal.class, instrumentId);
    }
}
