package com.ex.tradingPlatform.services;

import com.ex.tradingPlatform.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Open SSE streams of two users, then a simulated tick (called by hand) with pending orders of one of them. */
class LiveEventsTest extends IntegrationTest {

    private static final Instant T0 = Instant.parse("2026-01-01T10:00:00Z");

    @Autowired
    private PriceSimulator priceSimulator;

    @Autowired
    private SseEmitterRegistry registry;

    private String johnToken;
    private String janeToken;
    private long aapl;

    @BeforeEach
    void setUp() throws Exception {
        registerUser("john@example.com", "secret123");
        registerUser("jane@example.com", "secret123");
        johnToken = loginTokens("john@example.com", "secret123").get("accessToken").asText();
        janeToken = loginTokens("jane@example.com", "secret123").get("accessToken").asText();
        aapl = jdbc.queryForObject(
                "INSERT INTO instruments (symbol, name) VALUES ('AAPL', 'Apple') RETURNING id", Long.class);
        jdbc.update("INSERT INTO price_ticks (instrument_id, price, ts) VALUES (?, 100, ?)", aapl, Timestamp.from(T0));
    }

    @AfterEach
    void closeConnections() {
        registry.completeAll();
    }

    @Test
    void BR54_everyClientReceivesNewPrice() throws Exception {
        MockHttpServletResponse john = openStream(johnToken);
        MockHttpServletResponse jane = openStream(janeToken);

        priceSimulator.tick();

        BigDecimal price = latestPrice();
        for (MockHttpServletResponse stream : new MockHttpServletResponse[]{john, jane}) {
            assertThat(stream.getContentAsString())
                    .contains("event:price\ndata:{\"instrumentId\":" + aapl + ",\"symbol\":\"AAPL\",\"price\":"
                            + price.toPlainString() + ",\"ts\":\"")
                    .doesNotContain("event:order");
        }
    }

    @Test
    void BR55_filledOrder_isReportedOnlyToItsOwnerAfterThePrice() throws Exception {
        deposit(johnToken, "2000");
        long orderId = place(johnToken, "LIMIT", "\"limitPrice\":1000");   // fills at any simulated price
        MockHttpServletResponse john = openStream(johnToken);
        MockHttpServletResponse jane = openStream(janeToken);

        priceSimulator.tick();

        String johnEvents = john.getContentAsString();
        assertThat(johnEvents).contains("event:order\ndata:{\"orderId\":" + orderId
                + ",\"status\":\"FILLED\",\"filledPrice\":" + latestPrice().toPlainString() + "}");
        assertThat(johnEvents.indexOf("event:price")).isLessThan(johnEvents.indexOf("event:order"));
        assertThat(jane.getContentAsString()).contains("event:price").doesNotContain("event:order");
    }

    @Test
    void BR55_rejectedOrder_isReportedWithoutFilledPrice() throws Exception {
        deposit(johnToken, "0.0001");
        // a STOP BUY at 0.0001 triggers at any price, but john cannot pay for it
        long orderId = place(johnToken, "STOP", "\"stopPrice\":0.0001");
        MockHttpServletResponse john = openStream(johnToken);

        priceSimulator.tick();

        assertThat(john.getContentAsString())
                .contains("event:order\ndata:{\"orderId\":" + orderId + ",\"status\":\"REJECTED\",\"filledPrice\":null}");
    }

    @Test
    void BR55_untouchedOrder_producesNoOrderEvent() throws Exception {
        deposit(johnToken, "2000");
        place(johnToken, "LIMIT", "\"limitPrice\":1");                   // simulated prices stay far above 1
        MockHttpServletResponse john = openStream(johnToken);

        priceSimulator.tick();

        assertThat(john.getContentAsString()).contains("event:price").doesNotContain("event:order");
    }

    private MockHttpServletResponse openStream(String token) throws Exception {
        return mvc.perform(get("/api/stream").param("token", token).accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted())
                .andReturn()
                .getResponse();
    }

    private BigDecimal latestPrice() {
        return jdbc.queryForObject(
                "SELECT price FROM price_ticks WHERE instrument_id = ? ORDER BY ts DESC, id DESC LIMIT 1",
                BigDecimal.class, aapl);
    }

    private void deposit(String token, String amount) throws Exception {
        mvc.perform(withBearer(post("/api/account/deposit"), token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":" + amount + "}"))
                .andExpect(status().isOk());
    }

    private long place(String token, String type, String priceField) throws Exception {
        String json = "{\"side\":\"BUY\",\"type\":\"" + type + "\",\"instrumentId\":" + aapl + ",\"quantity\":1,"
                + priceField + "}";
        return readJson(mvc.perform(withBearer(post("/api/orders"), token)
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated()))
                .get("id").asLong();
    }
}
