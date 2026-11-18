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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
    @ValueSource(strings = {"MARKET", "LIMIT", "STOP"})
    void BR35_marketClosed_returns409AndNothingChanges(String type) throws Exception {
        deposit("1000");
        doReturn(false).when(marketHoursService).isOpen();

        place(order("BUY", type, aapl, 1, type.equals("LIMIT") ? "90" : null, type.equals("STOP") ? "90" : null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MARKET_CLOSED"));
        assertNothingExecuted("1000.0000");
    }

    // ------------------------------------------------------------------ pending BUY

    @Test
    void BR34_BR42_buyLimit_isStoredAsNewAndReservesQuantityTimesLimitPrice() throws Exception {
        deposit("1000");

        long orderId = readJson(place(order("BUY", "LIMIT", aapl, 5, "90.5", null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("LIMIT"))
                .andExpect(jsonPath("$.status").value("NEW"))
                .andExpect(jsonPath("$.limitPrice").value(90.5))
                .andExpect(jsonPath("$.reservedAmount").value(452.5))
                .andExpect(jsonPath("$.filledPrice").doesNotExist())
                .andExpect(jsonPath("$.filledAt").doesNotExist()))
                .get("id").asLong();

        assertAccount("1000.0000", "452.5000");
        assertThat(jdbc.queryForObject("SELECT reserved_amount FROM orders WHERE id = ?", BigDecimal.class, orderId))
                .isEqualTo(new BigDecimal("452.5000"));
        // nothing is bought yet
        assertThat(jdbc.queryForObject("SELECT count(*) FROM positions", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM transactions WHERE type = 'BUY'", Long.class)).isZero();
    }

    @Test
    void BR42_buyStop_reservesStopPriceWithSlippageBuffer() throws Exception {
        deposit("1000");

        // 3 * 100.0001 * 1.05 = 315.000315 -> 315.0003
        place(order("BUY", "STOP", aapl, 3, null, "100.0001"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("NEW"))
                .andExpect(jsonPath("$.reservedAmount").value(315.0003));

        assertAccount("1000.0000", "315.0003");
    }

    @ParameterizedTest
    @ValueSource(strings = {"LIMIT", "STOP"})
    void BR42_pendingBuyAboveAvailableFunds_returns409AndNothingChanges(String type) throws Exception {
        deposit("1000");

        // LIMIT needs 1000.01, STOP needs 952.39 * 1.05 = 1000.0095
        place(order("BUY", type, aapl, 1, type.equals("LIMIT") ? "1000.01" : null,
                type.equals("STOP") ? "952.39" : null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"));

        assertNothingExecuted("1000.0000");
        assertAccount("1000.0000", "0.0000");
    }

    @Test
    void BR42_reservedFunds_cannotBeSpentOrWithdrawnAgain() throws Exception {
        deposit("1000");
        place(order("BUY", "LIMIT", aapl, 6, "100", null)).andExpect(status().isCreated());   // reserves 600

        place(order("BUY", "LIMIT", aapl, 5, "100", null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"));
        place(order("BUY", "MARKET", aapl, 5, null, null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"));
        mvc.perform(withBearer(post("/api/account/withdraw"), token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":400.0001}"))
                .andExpect(status().isConflict());

        place(order("BUY", "LIMIT", aapl, 4, "100", null)).andExpect(status().isCreated());
        assertAccount("1000.0000", "1000.0000");
    }

    @Test
    void BR42_blockedBalanceEqualsSumOfReservationsOfNewOrders() throws Exception {
        deposit("10000");
        place(order("BUY", "LIMIT", aapl, 3, "10.1234", null)).andExpect(status().isCreated());
        place(order("BUY", "STOP", aapl, 7, null, "99.99")).andExpect(status().isCreated());
        place(order("BUY", "MARKET", aapl, 2, null, null)).andExpect(status().isCreated());
        place(order("BUY", "LIMIT", aapl, 1, "0.0001", null)).andExpect(status().isCreated());

        BigDecimal reserved = jdbc.queryForObject(
                "SELECT SUM(reserved_amount) FROM orders WHERE user_id = ? AND status = 'NEW'", BigDecimal.class, userId);
        BigDecimal blocked = jdbc.queryForObject(
                "SELECT blocked_balance FROM accounts WHERE user_id = ?", BigDecimal.class, userId);
        // 30.3702 + 734.9265 + 0.0001
        assertThat(blocked).isEqualTo(new BigDecimal("765.2968")).isEqualByComparingTo(reserved);
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

    // ------------------------------------------------------------------ SELL

    @Test
    void BR38_sellWithoutPosition_returns409AndNothingChanges() throws Exception {
        deposit("1000");

        place(order("SELL", "MARKET", aapl, 1, null, null))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_SHARES"));
        assertNothingExecuted("1000.0000");
    }

    @Test
    void BR38_sellMoreThanHeld_returns409AndPositionStays() throws Exception {
        deposit("1000");
        place(order("BUY", "MARKET", aapl, 5, null, null)).andExpect(status().isCreated());

        place(order("SELL", "MARKET", aapl, 6, null, null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_SHARES"));

        assertThat(positionQuantity()).isEqualTo(5);
        assertThat(balance()).isEqualTo(new BigDecimal("500.0000"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM orders", Long.class)).isEqualTo(1);
    }

    @Test
    void BR38_sell_cannotUseBlockedShares() throws Exception {
        deposit("1000");
        place(order("BUY", "MARKET", aapl, 10, null, null)).andExpect(status().isCreated());
        jdbc.update("UPDATE positions SET blocked_quantity = 4 WHERE user_id = ?", userId);

        place(order("SELL", "MARKET", aapl, 7, null, null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_SHARES"));
        place(order("SELL", "MARKET", aapl, 6, null, null))
                .andExpect(status().isCreated());

        assertThat(jdbc.queryForMap("SELECT quantity, blocked_quantity FROM positions WHERE user_id = ?", userId))
                .containsEntry("quantity", 4)
                .containsEntry("blocked_quantity", 4);
    }

    @Test
    void BR39_marketSell_creditsProceedsKeepsAveragePriceAndRecordsLedger() throws Exception {
        deposit("1000");
        place(order("BUY", "MARKET", aapl, 10, null, null)).andExpect(status().isCreated());   // 10 @ 100
        insertTick(aapl, "120.5000", T0.plusSeconds(60));

        long orderId = readJson(place(order("SELL", "MARKET", aapl, 4, null, null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.side").value("SELL"))
                .andExpect(jsonPath("$.status").value("FILLED"))
                .andExpect(jsonPath("$.filledPrice").value(120.5)))
                .get("id").asLong();

        assertThat(balance()).isEqualTo(new BigDecimal("482.0000"));
        assertThat(jdbc.queryForMap("SELECT quantity, avg_price FROM positions WHERE user_id = ?", userId))
                .containsEntry("quantity", 6)
                .containsEntry("avg_price", new BigDecimal("100.0000"));
        assertThat(jdbc.queryForMap(
                "SELECT amount, balance_after, order_id FROM transactions WHERE type = 'SELL'"))
                .containsEntry("amount", new BigDecimal("482.0000"))
                .containsEntry("balance_after", new BigDecimal("482.0000"))
                .containsEntry("order_id", orderId);
    }

    @Test
    void BR11_sellingAllShares_deletesPosition() throws Exception {
        deposit("1000");
        place(order("BUY", "MARKET", aapl, 5, null, null)).andExpect(status().isCreated());

        place(order("SELL", "MARKET", aapl, 5, null, null)).andExpect(status().isCreated());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM positions", Long.class)).isZero();

        // buying again opens a fresh position at the new price
        insertTick(aapl, "50.0000", T0.plusSeconds(60));
        place(order("BUY", "MARKET", aapl, 2, null, null)).andExpect(status().isCreated());
        assertThat(jdbc.queryForMap("SELECT quantity, avg_price FROM positions WHERE user_id = ?", userId))
                .containsEntry("quantity", 2)
                .containsEntry("avg_price", new BigDecimal("50.0000"));
    }

    @Test
    void BR31_ledgerMatchesBalanceAfterBuysAndSells() throws Exception {
        deposit("10000");
        place(order("BUY", "MARKET", aapl, 20, null, null)).andExpect(status().isCreated());
        insertTick(aapl, "111.1111", T0.plusSeconds(60));
        place(order("SELL", "MARKET", aapl, 7, null, null)).andExpect(status().isCreated());
        place(order("SELL", "MARKET", aapl, 99, null, null)).andExpect(status().isConflict());
        place(order("BUY", "MARKET", aapl, 3, null, null)).andExpect(status().isCreated());

        BigDecimal ledgerSum = jdbc.queryForObject("""
                SELECT SUM(CASE WHEN type IN ('DEPOSIT', 'SELL') THEN amount ELSE -amount END)
                FROM transactions WHERE user_id = ?""", BigDecimal.class, userId);
        BigDecimal lastBalanceAfter = jdbc.queryForObject(
                "SELECT balance_after FROM transactions WHERE user_id = ? ORDER BY id DESC LIMIT 1",
                BigDecimal.class, userId);
        // 10000 - 2000 + 7 * 111.1111 - 3 * 111.1111
        assertThat(balance()).isEqualTo(new BigDecimal("8444.4444"));
        assertThat(ledgerSum).isEqualByComparingTo(balance());
        assertThat(lastBalanceAfter).isEqualTo(balance());
        assertThat(positionQuantity()).isEqualTo(16);
    }

    // ------------------------------------------------------------------ pending SELL

    @ParameterizedTest
    @ValueSource(strings = {"LIMIT", "STOP"})
    void BR34_BR43_pendingSell_isStoredAsNewAndBlocksShares(String type) throws Exception {
        deposit("1000");
        place(order("BUY", "MARKET", aapl, 10, null, null)).andExpect(status().isCreated());

        place(order("SELL", type, aapl, 4, type.equals("LIMIT") ? "120" : null, type.equals("STOP") ? "80" : null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.side").value("SELL"))
                .andExpect(jsonPath("$.type").value(type))
                .andExpect(jsonPath("$.status").value("NEW"))
                .andExpect(jsonPath("$.reservedAmount").value(0));

        assertPosition(10, 4);
        assertAccount("0.0000", "0.0000");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM transactions WHERE type = 'SELL'", Long.class)).isZero();
    }

    @Test
    void BR43_sameSharesCannotBeBlockedTwice() throws Exception {
        deposit("1000");
        place(order("BUY", "MARKET", aapl, 10, null, null)).andExpect(status().isCreated());

        place(order("SELL", "LIMIT", aapl, 6, "120", null)).andExpect(status().isCreated());
        place(order("SELL", "STOP", aapl, 5, null, "80"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_SHARES"));
        place(order("SELL", "STOP", aapl, 4, null, "80")).andExpect(status().isCreated());
        place(order("SELL", "LIMIT", aapl, 1, "150", null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_SHARES"));

        assertPosition(10, 10);
        assertThat(jdbc.queryForObject("SELECT SUM(quantity) FROM orders WHERE side = 'SELL' AND status = 'NEW'",
                Integer.class)).isEqualTo(10);
    }

    @Test
    void BR43_sharesBlockedByPendingSell_cannotBeSoldAtMarket() throws Exception {
        deposit("1000");
        place(order("BUY", "MARKET", aapl, 10, null, null)).andExpect(status().isCreated());
        place(order("SELL", "LIMIT", aapl, 8, "120", null)).andExpect(status().isCreated());

        place(order("SELL", "MARKET", aapl, 3, null, null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_SHARES"));
        place(order("SELL", "MARKET", aapl, 2, null, null)).andExpect(status().isCreated());

        assertPosition(8, 8);
    }

    @ParameterizedTest
    @ValueSource(strings = {"LIMIT", "STOP"})
    void BR43_pendingSellWithoutShares_returns409AndNothingChanges(String type) throws Exception {
        deposit("1000");

        place(order("SELL", type, aapl, 1, type.equals("LIMIT") ? "120" : null, type.equals("STOP") ? "80" : null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_SHARES"));
        assertNothingExecuted("1000.0000");
    }

    // ------------------------------------------------------------------ open order limit

    @Test
    void BR44_21stOpenOrder_returns409AndNothingChanges() throws Exception {
        deposit("1000");
        place(order("BUY", "MARKET", aapl, 5, null, null)).andExpect(status().isCreated());
        for (int i = 0; i < 18; i++) {
            place(order("BUY", "LIMIT", aapl, 1, "1", null)).andExpect(status().isCreated());
        }
        place(order("SELL", "LIMIT", aapl, 1, "200", null)).andExpect(status().isCreated());
        place(order("SELL", "STOP", aapl, 1, null, "50")).andExpect(status().isCreated());   // 20 open

        place(order("BUY", "LIMIT", aapl, 1, "1", null))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("TOO_MANY_OPEN_ORDERS"));
        place(order("SELL", "STOP", aapl, 1, null, "50"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TOO_MANY_OPEN_ORDERS"));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM orders WHERE status = 'NEW'", Long.class)).isEqualTo(20);
        assertAccount("500.0000", "18.0000");
        assertPosition(5, 2);
    }

    @Test
    void BR44_marketOrders_areAllowedAtTheLimit() throws Exception {
        deposit("1000");
        for (int i = 0; i < 20; i++) {
            place(order("BUY", "LIMIT", aapl, 1, "1", null)).andExpect(status().isCreated());
        }

        place(order("BUY", "MARKET", aapl, 1, null, null)).andExpect(status().isCreated());
    }

    @Test
    void BR44_onlyOwnNewOrdersCountTowardsTheLimit() throws Exception {
        deposit("1000");
        long otherId = registerUser("jane@example.com", "secret123");
        for (int i = 0; i < 20; i++) {
            insertOrder(otherId, "NEW");
            insertOrder(userId, "CANCELLED");
        }
        for (int i = 0; i < 19; i++) {
            place(order("BUY", "LIMIT", aapl, 1, "1", null)).andExpect(status().isCreated());
        }

        place(order("BUY", "LIMIT", aapl, 1, "1", null)).andExpect(status().isCreated());
        place(order("BUY", "LIMIT", aapl, 1, "1", null))
                .andExpect(jsonPath("$.code").value("TOO_MANY_OPEN_ORDERS"));
    }

    // ------------------------------------------------------------------ cancel

    @Test
    void cancel_withoutToken_returns401() throws Exception {
        mvc.perform(delete("/api/orders/{id}", 1)).andExpect(status().isUnauthorized());
    }

    @Test
    void BR45_cancelPendingBuy_releasesReservedFunds() throws Exception {
        deposit("1000");
        long limitId = placedId(order("BUY", "LIMIT", aapl, 5, "100", null));     // reserves 500
        placedId(order("BUY", "STOP", aapl, 2, null, "100.0001"));                // reserves 210.0002

        cancel(limitId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(limitId))
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.reservedAmount").value(0));

        assertAccount("1000.0000", "210.0002");
        assertThat(jdbc.queryForMap("SELECT status, reserved_amount FROM orders WHERE id = ?", limitId))
                .containsEntry("status", "CANCELLED")
                .containsEntry("reserved_amount", new BigDecimal("0.0000"));
        // released funds are spendable again
        mvc.perform(withBearer(post("/api/account/withdraw"), token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":789.9998}"))
                .andExpect(status().isOk());
    }

    @Test
    void BR45_cancelPendingSell_releasesBlockedShares() throws Exception {
        deposit("1000");
        place(order("BUY", "MARKET", aapl, 10, null, null)).andExpect(status().isCreated());
        long sellId = placedId(order("SELL", "LIMIT", aapl, 6, "120", null));
        placedId(order("SELL", "STOP", aapl, 3, null, "80"));

        cancel(sellId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        assertPosition(10, 3);
        assertAccount("0.0000", "0.0000");
        // released shares can be sold or blocked again
        place(order("SELL", "LIMIT", aapl, 7, "130", null)).andExpect(status().isCreated());
        assertPosition(10, 10);
    }

    @Test
    void BR45_cancel_isAllowedWhenMarketIsClosed() throws Exception {
        deposit("1000");
        long orderId = placedId(order("BUY", "LIMIT", aapl, 1, "100", null));
        doReturn(false).when(marketHoursService).isOpen();

        cancel(orderId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        assertAccount("1000.0000", "0.0000");
    }

    @Test
    void BR45_cancelOtherUsersOrder_returns404AndKeepsTheirReservation() throws Exception {
        registerUser("jane@example.com", "secret123");
        String janeToken = loginTokens("jane@example.com", "secret123").get("accessToken").asText();
        mvc.perform(withBearer(post("/api/account/deposit"), janeToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":1000}"))
                .andExpect(status().isOk());
        long janesOrder = readJson(mvc.perform(withBearer(post("/api/orders"), janeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(order("BUY", "LIMIT", aapl, 3, "100", null)))
                .andExpect(status().isCreated()))
                .get("id").asLong();

        cancel(janesOrder)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));

        assertThat(jdbc.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, janesOrder))
                .isEqualTo("NEW");
        assertThat(jdbc.queryForObject("SELECT blocked_balance FROM accounts WHERE user_id <> ?",
                BigDecimal.class, userId)).isEqualTo(new BigDecimal("300.0000"));
    }

    @Test
    void BR45_cancelFilledOrder_returns409AndChangesNothing() throws Exception {
        deposit("1000");
        long filledId = placedId(order("BUY", "MARKET", aapl, 2, null, null));

        cancel(filledId)
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("ORDER_NOT_CANCELLABLE"));

        assertThat(jdbc.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, filledId))
                .isEqualTo("FILLED");
        assertAccount("800.0000", "0.0000");
        assertPosition(2, 0);
    }

    @Test
    void BR45_cancellingTwice_releasesOnlyOnce() throws Exception {
        deposit("1000");
        long first = placedId(order("BUY", "LIMIT", aapl, 2, "100", null));
        placedId(order("BUY", "LIMIT", aapl, 3, "100", null));

        cancel(first).andExpect(status().isOk());
        cancel(first)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_CANCELLABLE"));

        assertAccount("1000.0000", "300.0000");
    }

    @Test
    void BR44_BR45_cancelling_freesAnOpenOrderSlot() throws Exception {
        deposit("1000");
        long first = placedId(order("BUY", "LIMIT", aapl, 1, "1", null));
        for (int i = 0; i < 19; i++) {
            place(order("BUY", "LIMIT", aapl, 1, "1", null)).andExpect(status().isCreated());
        }
        place(order("BUY", "LIMIT", aapl, 1, "1", null)).andExpect(status().isConflict());

        cancel(first).andExpect(status().isOk());

        place(order("BUY", "LIMIT", aapl, 1, "1", null)).andExpect(status().isCreated());
    }

    @Test
    void cancel_unknownOrder_returns404() throws Exception {
        cancel(999_999)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
    }

    // ------------------------------------------------------------------ helpers

    private ResultActions cancel(long orderId) throws Exception {
        return mvc.perform(withBearer(delete("/api/orders/{id}", orderId), token));
    }

    private long placedId(String json) throws Exception {
        return readJson(place(json).andExpect(status().isCreated())).get("id").asLong();
    }

    private void insertOrder(long ownerId, String status) {
        jdbc.update("""
                        INSERT INTO orders (user_id, instrument_id, side, type, status, quantity, limit_price)
                        VALUES (?, ?, 'SELL', 'LIMIT', ?, 1, 1)""",
                ownerId, aapl, status);
    }

    private void assertPosition(int expectedQuantity, int expectedBlocked) {
        assertThat(jdbc.queryForMap("SELECT quantity, blocked_quantity FROM positions WHERE user_id = ?", userId))
                .containsEntry("quantity", expectedQuantity)
                .containsEntry("blocked_quantity", expectedBlocked);
    }

    private void assertAccount(String expectedBalance, String expectedBlocked) {
        assertThat(jdbc.queryForMap("SELECT balance, blocked_balance FROM accounts WHERE user_id = ?", userId))
                .containsEntry("balance", new BigDecimal(expectedBalance))
                .containsEntry("blocked_balance", new BigDecimal(expectedBlocked));
    }

    private int positionQuantity() {
        return jdbc.queryForObject("SELECT quantity FROM positions WHERE user_id = ?", Integer.class, userId);
    }

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
