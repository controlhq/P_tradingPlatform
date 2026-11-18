package com.ex.tradingPlatform.controllers;

import com.ex.tradingPlatform.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InstrumentControllerTest extends IntegrationTest {

    private static final Instant T0 = Instant.parse("2026-01-01T10:00:00Z");

    private String token;

    @BeforeEach
    void logInUser() throws Exception {
        registerUser("john@example.com", "secret123");
        token = loginTokens("john@example.com", "secret123").get("accessToken").asText();
    }

    @Test
    void list_withoutToken_returns401() throws Exception {
        mvc.perform(get("/api/instruments"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void get_withoutToken_returns401() throws Exception {
        long id = insertInstrument("AAPL", true);

        mvc.perform(get("/api/instruments/{id}", id))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void BR18_list_returnsLatestPriceOfEachInstrument() throws Exception {
        long aapl = insertInstrument("AAPL", true);
        insertTick(aapl, "100.0000", T0);
        insertTick(aapl, "101.2500", T0.plusSeconds(60));

        mvc.perform(withBearer(get("/api/instruments"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(aapl))
                .andExpect(jsonPath("$[0].symbol").value("AAPL"))
                .andExpect(jsonPath("$[0].name").value("AAPL Corp"))
                .andExpect(jsonPath("$[0].active").value(true))
                .andExpect(jsonPath("$[0].lastPrice").value(101.25))
                .andExpect(jsonPath("$[0].lastPriceAt").value("2026-01-01T10:01:00Z"));
    }

    @Test
    void BR19_list_returnsOnlyActiveInstrumentsOrderedBySymbol() throws Exception {
        insertInstrument("TSLA", true);
        insertInstrument("AAPL", true);
        insertInstrument("META", false);

        mvc.perform(withBearer(get("/api/instruments"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].symbol").value("AAPL"))
                .andExpect(jsonPath("$[1].symbol").value("TSLA"));
    }

    @Test
    void list_withNoInstruments_returnsEmptyArray() throws Exception {
        mvc.perform(withBearer(get("/api/instruments"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void BR18_get_returnsInstrumentWithLatestPrice() throws Exception {
        long id = insertInstrument("PKO", true);
        insertTick(id, "58.9200", T0);

        mvc.perform(withBearer(get("/api/instruments/{id}", id), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("PKO"))
                .andExpect(jsonPath("$.lastPrice").value(58.92));
    }

    @Test
    void BR19_get_inactiveInstrument_returns200WithActiveFalse() throws Exception {
        long id = insertInstrument("META", false);

        mvc.perform(withBearer(get("/api/instruments/{id}", id), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void get_unknownId_returns404InstrumentNotFound() throws Exception {
        mvc.perform(withBearer(get("/api/instruments/{id}", 999), token))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("INSTRUMENT_NOT_FOUND"));
    }

    @Test
    void get_nonNumericId_returns400() throws Exception {
        mvc.perform(withBearer(get("/api/instruments/abc"), token))
                .andExpect(status().isBadRequest());
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
}
