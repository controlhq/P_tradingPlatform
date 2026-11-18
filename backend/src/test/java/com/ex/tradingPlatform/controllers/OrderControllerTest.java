package com.ex.tradingPlatform.controllers;

import com.ex.tradingPlatform.IntegrationTest;
import com.ex.tradingPlatform.services.MarketHoursService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OrderControllerTest extends IntegrationTest {

    private static final Instant T0 = Instant.parse("2026-01-01T10:00:00Z");

    // tests run with market.always-open=true; the spy lets single tests close the market
    @MockitoSpyBean
    private MarketHoursService marketHoursService;

    private long userId;
    private String token;
    private long aapl;

    @BeforeEach
    void setUp() throws Exception {
        userId = registerUser("john@example.com", "secret123");
        token = loginTokens("john@example.com", "secret123").get("accessToken").asText();
        aapl = insertInstrument("AAPL", true);
        insertTick(aapl, "100.0000", T0);
    }

    // ------------------------------------------------------------------ validation

    @Test
    void place_withoutToken_returns401() throws Exception {
        mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON)
                        .content(order("BUY", "MARKET", aapl, 1, null, null)))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "10001", "null"})
    void BR03_quantityOutOfRange_returns400(String quantity) throws Exception {
        place("{\"side\":\"BUY\",\"type\":\"MARKET\",\"instrumentId\":" + aapl + ",\"quantity\":" + quantity + "}")
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors[0].field").value("quantity"));
    }

    @Test
    void BR04_limitWithoutLimitPrice_returns400OnLimitPrice() throws Exception {
        place(order("BUY", "LIMIT", aapl, 1, null, null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("limitPrice"))
                .andExpect(jsonPath("$.errors[0].message").value("is required for LIMIT orders"));
    }

    @Test
    void BR04_BR05_limitWithStopPrice_returns400OnStopPrice() throws Exception {
        place(order("BUY", "LIMIT", aapl, 1, "90", "95"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].field").value("stopPrice"))
                .andExpect(jsonPath("$.errors[0].message").value("must be empty for LIMIT orders"));
    }

    @Test
    void BR05_stopWithoutStopPrice_returns400OnStopPrice() throws Exception {
        place(order("SELL", "STOP", aapl, 1, null, null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("stopPrice"))
                .andExpect(jsonPath("$.errors[0].message").value("is required for STOP orders"));
    }

    @Test
    void BR04_BR05_stopWithLimitPrice_returns400OnLimitPrice() throws Exception {
        place(order("SELL", "STOP", aapl, 1, "90", "95"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].field").value("limitPrice"));
    }

    @Test
    void BR04_BR05_marketWithAnyPrice_returns400ForEachPrice() throws Exception {
        place(order("BUY", "MARKET", aapl, 1, "90", "95"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(2))
                .andExpect(jsonPath("$.errors[*].field",
                        containsInAnyOrder("limitPrice", "stopPrice")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "1.00001"})
    void limitPrice_invalidValue_returns400(String price) throws Exception {
        place(order("BUY", "LIMIT", aapl, 1, price, null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("limitPrice"));
    }

    @Test
    void place_missingFields_returns400ForEachField() throws Exception {
        place("{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", containsInAnyOrder(
                        "side", "type", "instrumentId", "quantity")));
    }

    @Test
    void place_unknownEnumValue_returns400() throws Exception {
        place("{\"side\":\"HOLD\",\"type\":\"MARKET\",\"instrumentId\":" + aapl + ",\"quantity\":1}")
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------ preconditions

    @ParameterizedTest
    @ValueSource(strings = {"LIMIT", "STOP"})
    void BR34_limitAndStopOrders_return501(String type) throws Exception {
        deposit("1000");
        String limit = type.equals("LIMIT") ? "90" : null;
        String stop = type.equals("STOP") ? "90" : null;

        place(order("BUY", type, aapl, 1, limit, stop))
                .andExpect(status().isNotImplemented())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("ORDER_TYPE_NOT_SUPPORTED"));
        assertNothingExecuted("1000.0000");
    }

    @Test
    void BR35_marketClosed_returns409AndNothingChanges() throws Exception {
        deposit("1000");
        doReturn(false).when(marketHoursService).isOpen();

        place(order("BUY", "MARKET", aapl, 1, null, null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MARKET_CLOSED"));
        assertNothingExecuted("1000.0000");
    }

    @Test
    void BR36_unknownInstrument_returns404() throws Exception {
        place(order("BUY", "MARKET", 999_999, 1, null, null))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("INSTRUMENT_NOT_FOUND"));
    }

    @Test
    void BR36_inactiveInstrument_returns422() throws Exception {
        deposit("1000");
        long meta = insertInstrument("META", false);
        insertTick(meta, "10.0000", T0);

        place(order("BUY", "MARKET", meta, 1, null, null))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INSTRUMENT_INACTIVE"));
        assertNothingExecuted("1000.0000");
    }

    // ------------------------------------------------------------------ BUY

    @Test
    void BR37_buyAboveAvailableFunds_returns409AndNothingChanges() throws Exception {
        deposit("999.9999");

        place(order("BUY", "MARKET", aapl, 10, null, null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"));
        assertNothingExecuted("999.9999");
    }

    @Test
    void BR37_buy_cannotUseBlockedFunds() throws Exception {
        deposit("2000");
        jdbc.update("UPDATE accounts SET blocked_balance = 1500 WHERE user_id = ?", userId);

        place(order("BUY", "MARKET", aapl, 6, null, null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"));
        place(order("BUY", "MARKET", aapl, 5, null, null))
                .andExpect(status().isCreated());
    }

    @Test
    void BR37_buyForExactlyAvailableFunds_leavesZeroBalance() throws Exception {
        deposit("1000");

        place(order("BUY", "MARKET", aapl, 10, null, null))
                .andExpect(status().isCreated());

        assertThat(balance()).isEqualTo(new BigDecimal("0.0000"));
    }

    @Test
    void BR39_marketBuy_isFilledAtLatestPriceAndUpdatesAccountPositionAndLedger() throws Exception {
        deposit("5000");
        insertTick(aapl, "123.4567", T0.plusSeconds(60));

        long orderId = readJson(place(order("BUY", "MARKET", aapl, 10, null, null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.instrumentId").value(aapl))
                .andExpect(jsonPath("$.symbol").value("AAPL"))
                .andExpect(jsonPath("$.side").value("BUY"))
                .andExpect(jsonPath("$.type").value("MARKET"))
                .andExpect(jsonPath("$.status").value("FILLED"))
                .andExpect(jsonPath("$.quantity").value(10))
                .andExpect(jsonPath("$.filledPrice").value(123.4567))
                .andExpect(jsonPath("$.filledAt").isNotEmpty()))
                .get("id").asLong();

        assertThat(balance()).isEqualTo(new BigDecimal("3765.4330"));
        assertThat(jdbc.queryForMap(
                "SELECT quantity, blocked_quantity, avg_price FROM positions WHERE user_id = ? AND instrument_id = ?",
                userId, aapl))
                .containsEntry("quantity", 10)
                .containsEntry("blocked_quantity", 0)
                .containsEntry("avg_price", new BigDecimal("123.4567"));
        assertThat(jdbc.queryForMap(
                "SELECT type, amount, balance_after, order_id FROM transactions WHERE type = 'BUY'"))
                .containsEntry("amount", new BigDecimal("1234.5670"))
                .containsEntry("balance_after", new BigDecimal("3765.4330"))
                .containsEntry("order_id", orderId);
        assertThat(jdbc.queryForMap("SELECT status, filled_price, reserved_amount FROM orders WHERE id = ?", orderId))
                .containsEntry("status", "FILLED")
                .containsEntry("filled_price", new BigDecimal("123.4567"))
                .containsEntry("reserved_amount", new BigDecimal("0.0000"));
    }

    @Test
    void BR33_BR39_severalBuys_updateWeightedAveragePrice() throws Exception {
        deposit("100000");

        place(order("BUY", "MARKET", aapl, 10, null, null)).andExpect(status().isCreated());   // 10 @ 100
        insertTick(aapl, "130.0000", T0.plusSeconds(60));
        place(order("BUY", "MARKET", aapl, 5, null, null)).andExpect(status().isCreated());    // 5 @ 130
        insertTick(aapl, "70.0000", T0.plusSeconds(120));
        place(order("BUY", "MARKET", aapl, 15, null, null)).andExpect(status().isCreated());   // 15 @ 70

        // (1000 + 650 + 1050) / 30 = 90
        Map<String, Object> position = jdbc.queryForMap(
                "SELECT quantity, avg_price FROM positions WHERE user_id = ?", userId);
        assertThat(position).containsEntry("quantity", 30).containsEntry("avg_price", new BigDecimal("90.0000"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM positions", Long.class)).isEqualTo(1);
        assertThat(balance()).isEqualTo(new BigDecimal("97300.0000"));
    }

    // ------------------------------------------------------------------ helpers

    private ResultActions place(String json) throws Exception {
        return mvc.perform(withBearer(post("/api/orders"), token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private static String order(String side, String type, long instrumentId, int quantity,
                                String limitPrice, String stopPrice) {
        return "{\"side\":\"" + side + "\",\"type\":\"" + type + "\",\"instrumentId\":" + instrumentId
                + ",\"quantity\":" + quantity + ",\"limitPrice\":" + limitPrice + ",\"stopPrice\":" + stopPrice + "}";
    }

    private void deposit(String amount) throws Exception {
        mvc.perform(withBearer(post("/api/account/deposit"), token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":" + amount + "}"))
                .andExpect(status().isOk());
    }

    private BigDecimal balance() {
        return jdbc.queryForObject("SELECT balance FROM accounts WHERE user_id = ?", BigDecimal.class, userId);
    }

    private void assertNothingExecuted(String expectedBalance) {
        assertThat(balance()).isEqualTo(new BigDecimal(expectedBalance));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM orders", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM positions", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM transactions WHERE type IN ('BUY', 'SELL')", Long.class))
                .isZero();
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
