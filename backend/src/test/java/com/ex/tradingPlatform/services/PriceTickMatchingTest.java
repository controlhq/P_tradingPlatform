package com.ex.tradingPlatform.services;

import com.ex.tradingPlatform.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The real simulator bean, called by hand instead of by the scheduler: its ticks must reach the
 * matching service once they are committed. Prices are random, so the orders are set to match any price.
 */
class PriceTickMatchingTest extends IntegrationTest {

    private static final Instant T0 = Instant.parse("2026-01-01T10:00:00Z");

    @Autowired
    private PriceSimulator priceSimulator;

    private String token;
    private long aapl;
    private long meta;

    @BeforeEach
    void setUp() throws Exception {
        registerUser("john@example.com", "secret123");
        token = loginTokens("john@example.com", "secret123").get("accessToken").asText();
        aapl = insertInstrument("AAPL");
        meta = insertInstrument("META");
        deposit("10000");
    }

    @Test
    void BR51_newSimulatedTick_fillsTriggeredPendingOrderAtThatPrice() throws Exception {
        long order = place(aapl, "LIMIT", "1000");    // any simulated price is below the limit

        assertThat(priceSimulator.tick()).isEqualTo(2);

        BigDecimal latest = jdbc.queryForObject(
                "SELECT price FROM price_ticks WHERE instrument_id = ? ORDER BY ts DESC, id DESC LIMIT 1",
                BigDecimal.class, aapl);
        Map<String, Object> filled = jdbc.queryForMap("SELECT status, filled_price FROM orders WHERE id = ?", order);
        assertThat(filled).containsEntry("status", "FILLED").containsEntry("filled_price", latest);
    }

    @Test
    void BR51_tickOfOneInstrument_doesNotFillOrdersOfAnInactiveOne() throws Exception {
        long order = place(meta, "LIMIT", "1000");
        jdbc.update("UPDATE instruments SET active = false WHERE id = ?", meta);

        priceSimulator.tick();

        assertThat(jdbc.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, order))
                .isEqualTo("NEW");
    }

    @Test
    void BR51_untriggeredOrder_staysNewAfterTick() throws Exception {
        long order = place(aapl, "STOP", "1000");     // BUY STOP needs a price of at least 1000

        priceSimulator.tick();

        assertThat(jdbc.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, order))
                .isEqualTo("NEW");
    }

    private long insertInstrument(String symbol) {
        long id = jdbc.queryForObject("INSERT INTO instruments (symbol, name) VALUES (?, ?) RETURNING id",
                Long.class, symbol, symbol + " Corp");
        jdbc.update("INSERT INTO price_ticks (instrument_id, price, ts) VALUES (?, 100, ?)", id, Timestamp.from(T0));
        return id;
    }

    private void deposit(String amount) throws Exception {
        mvc.perform(withBearer(post("/api/account/deposit"), token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":" + amount + "}"))
                .andExpect(status().isOk());
    }

    private long place(long instrumentId, String type, String price) throws Exception {
        String json = "{\"side\":\"BUY\",\"type\":\"" + type + "\",\"instrumentId\":" + instrumentId + ",\"quantity\":1,"
                + (type.equals("LIMIT") ? "\"limitPrice\":" : "\"stopPrice\":") + price + "}";
        return readJson(mvc.perform(withBearer(post("/api/orders"), token)
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated()))
                .get("id").asLong();
    }
}
