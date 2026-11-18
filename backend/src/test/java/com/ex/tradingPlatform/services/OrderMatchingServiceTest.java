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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Ticks are passed to the matching service by hand; the scheduler is disabled in tests. */
class OrderMatchingServiceTest extends IntegrationTest {

    private static final Instant T0 = Instant.parse("2026-01-01T10:00:00Z");

    @Autowired
    private OrderMatchingService matching;

    private long userId;
    private String token;
    private long aapl;

    @BeforeEach
    void setUp() throws Exception {
        userId = registerUser("john@example.com", "secret123");
        token = loginTokens("john@example.com", "secret123").get("accessToken").asText();
        aapl = jdbc.queryForObject(
                "INSERT INTO instruments (symbol, name) VALUES ('AAPL', 'Apple') RETURNING id", Long.class);
        jdbc.update("INSERT INTO price_ticks (instrument_id, price, ts) VALUES (?, 100, ?)", aapl, Timestamp.from(T0));
    }

    // ------------------------------------------------------------------ LIMIT BUY

    @Test
    void BR46_BR47_limitBuyHit_fillsAtTickPriceAndReleasesSurplusReservation() {
        deposit("1000");
        long id = place("BUY", "LIMIT", 5, "100", null);              // reserves 500

        assertThat(matching.onPriceTick(aapl, new BigDecimal("98.5"))).isEqualTo(1);

        assertFilled(id, "98.5000");
        // cost 492.5; the unused 7.5 of the reservation is free again
        assertAccount("507.5000", "0.0000");
        assertPosition(5, 0, "98.5000");
        assertLedgerEntry(id, "BUY", "492.5000", "507.5000");
    }

    @Test
    void BR46_limitBuyMiss_staysNewWithReservation() {
        deposit("1000");
        long id = place("BUY", "LIMIT", 5, "100", null);

        assertThat(matching.onPriceTick(aapl, new BigDecimal("100.0001"))).isZero();

        assertStatus(id, "NEW");
        assertAccount("1000.0000", "500.0000");
    }

    // ------------------------------------------------------------------ STOP BUY

    @Test
    void BR46_BR47_stopBuyHit_aboveReservation_paysShortfallFromAvailableFunds() {
        deposit("1000");
        long id = place("BUY", "STOP", 5, null, "100");               // reserves 525

        assertThat(matching.onPriceTick(aapl, new BigDecimal("110"))).isEqualTo(1);

        // cost 550 exceeds the reservation by 25, taken from the free 475
        assertFilled(id, "110.0000");
        assertAccount("450.0000", "0.0000");
        assertPosition(5, 0, "110.0000");
        assertLedgerEntry(id, "BUY", "550.0000", "450.0000");
    }

    @Test
    void BR46_stopBuyMiss_staysNew() {
        deposit("1000");
        long id = place("BUY", "STOP", 5, null, "100");

        assertThat(matching.onPriceTick(aapl, new BigDecimal("99.9999"))).isZero();

        assertStatus(id, "NEW");
        assertAccount("1000.0000", "525.0000");
    }

    // ------------------------------------------------------------------ LIMIT SELL

    @Test
    void BR46_BR48_limitSellHit_fillsAtTickPriceUnblocksAndRemovesShares() {
        deposit("1000");
        place("BUY", "MARKET", 10, null, null);                        // 10 @ 100, cash 0
        long id = place("SELL", "LIMIT", 4, "120", null);

        assertThat(matching.onPriceTick(aapl, new BigDecimal("125"))).isEqualTo(1);

        assertFilled(id, "125.0000");
        assertAccount("500.0000", "0.0000");
        assertPosition(6, 0, "100.0000");
        assertLedgerEntry(id, "SELL", "500.0000", "500.0000");
    }

    @Test
    void BR46_limitSellMiss_staysNewWithBlockedShares() {
        deposit("1000");
        place("BUY", "MARKET", 10, null, null);
        long id = place("SELL", "LIMIT", 4, "120", null);

        assertThat(matching.onPriceTick(aapl, new BigDecimal("119.9999"))).isZero();

        assertStatus(id, "NEW");
        assertPosition(10, 4, "100.0000");
    }

    // ------------------------------------------------------------------ STOP SELL

    @Test
    void BR46_BR48_stopSellHit_sellingAllSharesDeletesPosition() {
        deposit("1000");
        place("BUY", "MARKET", 10, null, null);
        long id = place("SELL", "STOP", 10, null, "80");

        assertThat(matching.onPriceTick(aapl, new BigDecimal("79"))).isEqualTo(1);

        assertFilled(id, "79.0000");
        assertAccount("790.0000", "0.0000");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM positions", Long.class)).isZero();
    }

    @Test
    void BR46_stopSellMiss_staysNew() {
        deposit("1000");
        place("BUY", "MARKET", 10, null, null);
        long id = place("SELL", "STOP", 10, null, "80");

        assertThat(matching.onPriceTick(aapl, new BigDecimal("80.0001"))).isZero();

        assertStatus(id, "NEW");
        assertPosition(10, 10, "100.0000");
    }

    // ------------------------------------------------------------------ REJECTED

    @Test
    void BR50_buyCostAboveReservationAndFreeCash_isRejectedAndReservationReleased() {
        deposit("525");
        long id = place("BUY", "STOP", 5, null, "100");               // reserves all 525

        // cost 550 > 525 and nothing else is free
        assertThat(matching.onPriceTick(aapl, new BigDecimal("110"))).isZero();

        assertThat(jdbc.queryForMap("SELECT status, reserved_amount, filled_price FROM orders WHERE id = ?", id))
                .containsEntry("status", "REJECTED")
                .containsEntry("reserved_amount", new BigDecimal("0.0000"))
                .containsEntry("filled_price", null);
        assertAccount("525.0000", "0.0000");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM positions", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM transactions WHERE type = 'BUY'", Long.class)).isZero();
    }

    @Test
    void BR49_BR50_ordersAreProcessedOldestFirst_soTheLaterOneIsRejected() {
        deposit("1100");
        long older = place("BUY", "STOP", 5, null, "100");            // reserves 525
        long newer = place("BUY", "STOP", 5, null, "100");            // reserves 525, 50 stays free

        // each costs 560: the older one uses its 525 + 35 free cash, leaving 15 + 525 for the newer one
        assertThat(matching.onPriceTick(aapl, new BigDecimal("112"))).isEqualTo(1);

        assertFilled(older, "112.0000");
        assertStatus(newer, "REJECTED");
        assertAccount("540.0000", "0.0000");
        assertPosition(5, 0, "112.0000");
    }

    @Test
    void BR49_BR50_rejectedOrder_isNotMatchedAgain() {
        deposit("525");
        long id = place("BUY", "STOP", 5, null, "100");
        matching.onPriceTick(aapl, new BigDecimal("110"));
        deposit("10000");

        assertThat(matching.onPriceTick(aapl, new BigDecimal("110"))).isZero();

        assertStatus(id, "REJECTED");
        assertAccount("10525.0000", "0.0000");
    }

    // ------------------------------------------------------------------ processing

    @Test
    void BR49_cancelledOrders_areNotFilled() throws Exception {
        deposit("1000");
        long id = place("BUY", "LIMIT", 5, "100", null);
        mvc.perform(withBearer(delete("/api/orders/{id}", id), token)).andExpect(status().isOk());

        assertThat(matching.onPriceTick(aapl, new BigDecimal("50"))).isZero();

        assertStatus(id, "CANCELLED");
        assertAccount("1000.0000", "0.0000");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM transactions WHERE type = 'BUY'", Long.class)).isZero();
    }

    @Test
    void BR49_filledOrders_areNotFilledAgain() {
        deposit("1000");
        long id = place("BUY", "LIMIT", 5, "100", null);

        matching.onPriceTick(aapl, new BigDecimal("90"));
        assertThat(matching.onPriceTick(aapl, new BigDecimal("80"))).isZero();

        assertFilled(id, "90.0000");
        assertPosition(5, 0, "90.0000");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM transactions WHERE type = 'BUY'", Long.class))
                .isEqualTo(1);
    }

    @Test
    void BR49_failingOrder_doesNotBlockTheOthers() {
        deposit("1000");
        // inconsistent data: a pending SELL without any position, placed before the valid order
        long broken = jdbc.queryForObject("""
                INSERT INTO orders (user_id, instrument_id, side, type, status, quantity, limit_price, created_at, updated_at)
                VALUES (?, ?, 'SELL', 'LIMIT', 'NEW', 1, 1, ?, ?) RETURNING id""",
                Long.class, userId, aapl, Timestamp.from(T0), Timestamp.from(T0));
        long valid = place("BUY", "LIMIT", 2, "100", null);

        assertThat(matching.onPriceTick(aapl, new BigDecimal("100"))).isEqualTo(1);

        assertStatus(broken, "NEW");
        assertFilled(valid, "100.0000");
    }

    @Test
    void BR49_onlyOrdersOfTheTickedInstrumentAreMatched() {
        deposit("1000");
        long msft = jdbc.queryForObject(
                "INSERT INTO instruments (symbol, name) VALUES ('MSFT', 'Microsoft') RETURNING id", Long.class);
        long aaplOrder = place("BUY", "LIMIT", 1, "100", null);

        assertThat(matching.onPriceTick(msft, new BigDecimal("1"))).isZero();

        assertStatus(aaplOrder, "NEW");
    }

    // ------------------------------------------------------------------ helpers

    private void deposit(String amount) {
        try {
            mvc.perform(withBearer(post("/api/account/deposit"), token)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":" + amount + "}"))
                    .andExpect(status().isOk());
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private long place(String side, String type, int quantity, String limitPrice, String stopPrice) {
        String json = "{\"side\":\"" + side + "\",\"type\":\"" + type + "\",\"instrumentId\":" + aapl
                + ",\"quantity\":" + quantity + ",\"limitPrice\":" + limitPrice + ",\"stopPrice\":" + stopPrice + "}";
        try {
            return readJson(mvc.perform(withBearer(post("/api/orders"), token)
                            .contentType(MediaType.APPLICATION_JSON).content(json))
                    .andExpect(status().isCreated()))
                    .get("id").asLong();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private void assertStatus(long orderId, String expected) {
        assertThat(jdbc.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, orderId))
                .isEqualTo(expected);
    }

    private void assertFilled(long orderId, String expectedPrice) {
        Map<String, Object> order = jdbc.queryForMap(
                "SELECT status, filled_price, filled_at, reserved_amount FROM orders WHERE id = ?", orderId);
        assertThat(order)
                .containsEntry("status", "FILLED")
                .containsEntry("filled_price", new BigDecimal(expectedPrice))
                .containsEntry("reserved_amount", new BigDecimal("0.0000"));
        assertThat(order.get("filled_at")).isNotNull();
    }

    private void assertAccount(String expectedBalance, String expectedBlocked) {
        assertThat(jdbc.queryForMap("SELECT balance, blocked_balance FROM accounts WHERE user_id = ?", userId))
                .containsEntry("balance", new BigDecimal(expectedBalance))
                .containsEntry("blocked_balance", new BigDecimal(expectedBlocked));
    }

    private void assertPosition(int quantity, int blocked, String avgPrice) {
        assertThat(jdbc.queryForMap(
                "SELECT quantity, blocked_quantity, avg_price FROM positions WHERE user_id = ?", userId))
                .containsEntry("quantity", quantity)
                .containsEntry("blocked_quantity", blocked)
                .containsEntry("avg_price", new BigDecimal(avgPrice));
    }

    private void assertLedgerEntry(long orderId, String type, String amount, String balanceAfter) {
        assertThat(jdbc.queryForMap("SELECT type, amount, balance_after FROM transactions WHERE order_id = ?", orderId))
                .containsEntry("type", type)
                .containsEntry("amount", new BigDecimal(amount))
                .containsEntry("balance_after", new BigDecimal(balanceAfter));
    }
}
