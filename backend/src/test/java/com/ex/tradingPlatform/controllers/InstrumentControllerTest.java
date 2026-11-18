package com.ex.tradingPlatform.controllers;

import com.ex.tradingPlatform.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
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

    // ------------------------------------------------------------------ price history

    @Test
    void prices_withoutToken_returns401() throws Exception {
        long id = insertInstrument("AAPL", true);

        mvc.perform(get("/api/instruments/{id}/prices", id))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void BR25_prices_byDefault_returnsLast200TicksOldestFirst() throws Exception {
        long id = insertInstrument("AAPL", true);
        for (int i = 0; i < 205; i++) {
            insertTick(id, (100 + i) + ".0000", T0.plusSeconds(i));
        }

        mvc.perform(withBearer(get("/api/instruments/{id}/prices", id), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(200)))
                .andExpect(jsonPath("$[0].price").value(105))
                .andExpect(jsonPath("$[0].ts").value("2026-01-01T10:00:05Z"))
                .andExpect(jsonPath("$[199].price").value(304))
                .andExpect(jsonPath("$[199].ts").value("2026-01-01T10:03:24Z"));
    }

    @Test
    void BR25_prices_withLimit_returnsMostRecentTicksOldestFirst() throws Exception {
        long id = insertInstrument("AAPL", true);
        insertTick(id, "1.0000", T0);
        insertTick(id, "2.0000", T0.plusSeconds(1));
        insertTick(id, "3.0000", T0.plusSeconds(2));

        mvc.perform(withBearer(get("/api/instruments/{id}/prices", id).param("limit", "2"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].price").value(2.0))
                .andExpect(jsonPath("$[1].price").value(3.0));
    }

    @Test
    void BR25_prices_withFromAndTo_returnsTicksInInclusiveRange() throws Exception {
        long id = insertInstrument("AAPL", true);
        long other = insertInstrument("MSFT", true);
        for (int i = 0; i < 5; i++) {
            insertTick(id, (10 + i) + ".0000", T0.plusSeconds(60L * i));
        }
        insertTick(other, "99.0000", T0.plusSeconds(60));

        mvc.perform(withBearer(get("/api/instruments/{id}/prices", id)
                        .param("from", "2026-01-01T10:01:00Z")
                        .param("to", "2026-01-01T10:03:00Z"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].price").value(11.0))
                .andExpect(jsonPath("$[2].price").value(13.0));
    }

    @Test
    void BR25_prices_inactiveInstrument_returnsHistory() throws Exception {
        long id = insertInstrument("META", false);
        insertTick(id, "5.0000", T0);

        mvc.perform(withBearer(get("/api/instruments/{id}/prices", id), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void BR25_prices_fromAfterTo_returns400InvalidTimeRange() throws Exception {
        long id = insertInstrument("AAPL", true);

        mvc.perform(withBearer(get("/api/instruments/{id}/prices", id)
                        .param("from", "2026-01-02T00:00:00Z")
                        .param("to", "2026-01-01T00:00:00Z"), token))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("INVALID_TIME_RANGE"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "1001"})
    void BR25_prices_limitOutOfRange_returns400WithFieldError(String limit) throws Exception {
        long id = insertInstrument("AAPL", true);

        mvc.perform(withBearer(get("/api/instruments/{id}/prices", id).param("limit", limit), token))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors[0].field").value("limit"));
    }

    @Test
    void BR25_prices_limit1000_isAccepted() throws Exception {
        long id = insertInstrument("AAPL", true);

        mvc.perform(withBearer(get("/api/instruments/{id}/prices", id).param("limit", "1000"), token))
                .andExpect(status().isOk());
    }

    @ParameterizedTest
    @CsvSource({"limit, abc", "from, yesterday", "to, 2026-13-01T00:00:00Z"})
    void prices_malformedParameter_returns400(String name, String value) throws Exception {
        long id = insertInstrument("AAPL", true);

        mvc.perform(withBearer(get("/api/instruments/{id}/prices", id).param(name, value), token))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void prices_unknownInstrument_returns404() throws Exception {
        mvc.perform(withBearer(get("/api/instruments/{id}/prices", 999), token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("INSTRUMENT_NOT_FOUND"));
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
