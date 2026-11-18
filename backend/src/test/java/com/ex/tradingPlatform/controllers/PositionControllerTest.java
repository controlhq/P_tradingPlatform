package com.ex.tradingPlatform.controllers;

import com.ex.tradingPlatform.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PositionControllerTest extends IntegrationTest {

    private static final Instant T0 = Instant.parse("2026-01-01T10:00:00Z");

    private long userId;
    private String token;

    @BeforeEach
    void logInUser() throws Exception {
        userId = registerUser("john@example.com", "secret123");
        token = loginTokens("john@example.com", "secret123").get("accessToken").asText();
    }

    @Test
    void list_withoutToken_returns401() throws Exception {
        mvc.perform(get("/api/positions"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void list_withNoPositions_returnsEmptyArray() throws Exception {
        mvc.perform(withBearer(get("/api/positions"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void BR41_list_valuesPositionAtLatestPriceWithProfit() throws Exception {
        long aapl = insertInstrument("AAPL");
        insertTick(aapl, "90.0000", T0);
        insertTick(aapl, "112.3456", T0.plusSeconds(60));
        insertPosition(userId, aapl, 10, 2, "100.0000");

        mvc.perform(withBearer(get("/api/positions"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].instrumentId").value(aapl))
                .andExpect(jsonPath("$[0].symbol").value("AAPL"))
                .andExpect(jsonPath("$[0].quantity").value(10))
                .andExpect(jsonPath("$[0].blockedQuantity").value(2))
                .andExpect(jsonPath("$[0].avgPrice").value(100.0))
                .andExpect(jsonPath("$[0].currentPrice").value(112.3456))
                .andExpect(jsonPath("$[0].marketValue").value(1123.456))
                .andExpect(jsonPath("$[0].unrealizedPnl").value(123.456))
                .andExpect(jsonPath("$[0].unrealizedPnlPct").value(12.35));
    }

    @Test
    void BR41_list_showsLossAsNegativeValues() throws Exception {
        long pko = insertInstrument("PKO");
        insertTick(pko, "40.0000", T0);
        insertPosition(userId, pko, 3, 0, "50.0000");

        mvc.perform(withBearer(get("/api/positions"), token))
                .andExpect(jsonPath("$[0].marketValue").value(120.0))
                .andExpect(jsonPath("$[0].unrealizedPnl").value(-30.0))
                .andExpect(jsonPath("$[0].unrealizedPnlPct").value(-20.0));
    }

    @Test
    void BR41_list_roundsPercentHalfUpToTwoPlaces() throws Exception {
        long msft = insertInstrument("MSFT");
        insertTick(msft, "31.0000", T0);
        insertPosition(userId, msft, 3, 0, "30.0000");   // 3 / 90 = 3.333...%

        mvc.perform(withBearer(get("/api/positions"), token))
                .andExpect(jsonPath("$[0].unrealizedPnl").value(3.0))
                .andExpect(jsonPath("$[0].unrealizedPnlPct").value(3.33));
    }

    @Test
    void BR41_list_returnsOnlyOwnPositionsOrderedBySymbol() throws Exception {
        long otherId = registerUser("jane@example.com", "secret123");
        long tsla = insertInstrument("TSLA");
        long aapl = insertInstrument("AAPL");
        insertTick(tsla, "10.0000", T0);
        insertTick(aapl, "10.0000", T0);
        insertPosition(userId, tsla, 1, 0, "10.0000");
        insertPosition(userId, aapl, 1, 0, "10.0000");
        insertPosition(otherId, aapl, 50, 0, "10.0000");

        mvc.perform(withBearer(get("/api/positions"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].symbol", contains("AAPL", "TSLA")))
                .andExpect(jsonPath("$[0].quantity").value(1));
    }

    @Test
    void BR39_BR41_positionFromMarketOrders_isValuedAtNewPrice() throws Exception {
        long aapl = insertInstrument("AAPL");
        insertTick(aapl, "100.0000", T0);
        mvc.perform(withBearer(post("/api/account/deposit"), token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":1000}"))
                .andExpect(status().isOk());
        mvc.perform(withBearer(post("/api/orders"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"side\":\"BUY\",\"type\":\"MARKET\",\"instrumentId\":" + aapl + ",\"quantity\":5}"))
                .andExpect(status().isCreated());
        insertTick(aapl, "110.0000", T0.plusSeconds(60));

        mvc.perform(withBearer(get("/api/positions"), token))
                .andExpect(jsonPath("$[0].quantity").value(5))
                .andExpect(jsonPath("$[0].avgPrice").value(100.0))
                .andExpect(jsonPath("$[0].marketValue").value(550.0))
                .andExpect(jsonPath("$[0].unrealizedPnl").value(50.0))
                .andExpect(jsonPath("$[0].unrealizedPnlPct").value(10.0));
    }

    private long insertInstrument(String symbol) {
        return jdbc.queryForObject("INSERT INTO instruments (symbol, name) VALUES (?, ?) RETURNING id",
                Long.class, symbol, symbol + " Corp");
    }

    private void insertTick(long instrumentId, String price, Instant ts) {
        jdbc.update("INSERT INTO price_ticks (instrument_id, price, ts) VALUES (?, ?, ?)",
                instrumentId, new BigDecimal(price), Timestamp.from(ts));
    }

    private void insertPosition(long userId, long instrumentId, int quantity, int blocked, String avgPrice) {
        jdbc.update("""
                        INSERT INTO positions (user_id, instrument_id, quantity, blocked_quantity, avg_price)
                        VALUES (?, ?, ?, ?, ?)""",
                userId, instrumentId, quantity, blocked, new BigDecimal(avgPrice));
    }
}
