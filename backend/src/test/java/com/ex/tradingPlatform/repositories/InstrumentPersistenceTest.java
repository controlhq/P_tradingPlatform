package com.ex.tradingPlatform.repositories;

import com.ex.tradingPlatform.IntegrationTest;
import com.ex.tradingPlatform.entities.Instrument;
import com.ex.tradingPlatform.entities.PriceTick;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InstrumentPersistenceTest extends IntegrationTest {

    private static final Instant T0 = Instant.parse("2026-01-01T10:00:00Z");

    @Autowired
    DataSource dataSource;

    @Autowired
    InstrumentRepository instrumentRepository;

    @Autowired
    PriceTickRepository priceTickRepository;

    @Test
    void seedCreatesEightActiveInstrumentsEachWithStartingPrice() throws Exception {
        // IntegrationTest truncates all tables, so the seed is re-applied here
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V4__seed_instruments.sql"));
        }

        List<Instrument> active = instrumentRepository.findAllByActiveTrueOrderBySymbolAsc();
        assertThat(active).extracting(Instrument::getSymbol)
                .containsExactly("AAPL", "AMZN", "GOOGL", "META", "MSFT", "NVDA", "PKO", "TSLA");

        List<PriceTick> latest = priceTickRepository.findLatestByInstrumentIds(
                active.stream().map(Instrument::getId).toList());
        assertThat(latest).hasSize(8)
                .allSatisfy(tick -> assertThat(tick.getPrice()).isPositive());
    }

    @Test
    void BR18_latestPriceIsMostRecentTick() {
        Instrument instrument = instrumentRepository.save(new Instrument("TEST", "Test Corp", T0));
        priceTickRepository.save(new PriceTick(instrument, new BigDecimal("10.0000"), T0.plusSeconds(60)));
        priceTickRepository.save(new PriceTick(instrument, new BigDecimal("12.5000"), T0.plusSeconds(120)));
        // inserted last but older, must not win
        priceTickRepository.save(new PriceTick(instrument, new BigDecimal("9.0000"), T0));

        assertThat(priceTickRepository.findFirstByInstrumentIdOrderByTsDescIdDesc(instrument.getId()))
                .get().extracting(PriceTick::getPrice).isEqualTo(new BigDecimal("12.5000"));
        assertThat(priceTickRepository.findLatestByInstrumentIds(List.of(instrument.getId())))
                .singleElement().extracting(PriceTick::getPrice).isEqualTo(new BigDecimal("12.5000"));
    }

    @Test
    void BR18_ticksWithSameTimestamp_laterInsertWins() {
        Instrument instrument = instrumentRepository.save(new Instrument("TEST", "Test Corp", T0));
        priceTickRepository.save(new PriceTick(instrument, new BigDecimal("10.0000"), T0));
        priceTickRepository.save(new PriceTick(instrument, new BigDecimal("11.0000"), T0));

        assertThat(priceTickRepository.findFirstByInstrumentIdOrderByTsDescIdDesc(instrument.getId()))
                .get().extracting(PriceTick::getPrice).isEqualTo(new BigDecimal("11.0000"));
        assertThat(priceTickRepository.findLatestByInstrumentIds(List.of(instrument.getId())))
                .singleElement().extracting(PriceTick::getPrice).isEqualTo(new BigDecimal("11.0000"));
    }
}
