package com.ex.tradingPlatform.services;

import com.ex.tradingPlatform.IntegrationTest;
import com.ex.tradingPlatform.dto.PlaceOrderRequest;
import com.ex.tradingPlatform.entities.OrderSide;
import com.ex.tradingPlatform.entities.OrderType;
import com.ex.tradingPlatform.exceptions.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real concurrent requests against the services and a real Postgres. Each test is repeated because
 * a race may only show up in some interleavings.
 */
class ConcurrencyTest extends IntegrationTest {

    private static final Instant T0 = Instant.parse("2026-01-01T10:00:00Z");

    @Autowired
    private AccountService accountService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderMatchingService matching;

    private long userId;
    private long aapl;

    @BeforeEach
    void setUp() throws Exception {
        userId = registerUser("john@example.com", "secret123");
        aapl = jdbc.queryForObject(
                "INSERT INTO instruments (symbol, name) VALUES ('AAPL', 'Apple') RETURNING id", Long.class);
        jdbc.update("INSERT INTO price_ticks (instrument_id, price, ts) VALUES (?, 100, ?)", aapl, Timestamp.from(T0));
    }

    @RepeatedTest(10)
    void BR37_BR52_twoParallelMarketBuysForNearlyTheWholeBalance_exactlyOneSucceeds() throws Exception {
        accountService.deposit(userId, new BigDecimal("1000"));
        PlaceOrderRequest buy = new PlaceOrderRequest(OrderSide.BUY, OrderType.MARKET, aapl, 9, null, null);   // 900

        List<Outcome> outcomes = runConcurrently(
                () -> orderService.place(userId, buy),
                () -> orderService.place(userId, buy));

        assertThat(outcomes).containsExactlyInAnyOrder(Outcome.OK, Outcome.INSUFFICIENT_FUNDS);
        assertThat(balance()).isEqualTo(new BigDecimal("100.0000"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM orders WHERE status = 'FILLED'", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT quantity FROM positions WHERE user_id = ?", Integer.class, userId))
                .isEqualTo(9);
        assertLedgerMatchesBalance();
    }

    /**
     * Another NEW order stays untouched in the background: a double release of the cancelled/filled
     * order's reservation would eat into its reservation and break blocked_balance = sum of reservations.
     */
    @RepeatedTest(10)
    void BR45_BR52_cancelRacingWithFillOfPendingBuy_releasesReservationExactlyOnce() throws Exception {
        accountService.deposit(userId, new BigDecimal("1000"));
        long racing = orderService.place(userId, limit(OrderSide.BUY, 5, "100")).id();   // reserves 500
        orderService.place(userId, limit(OrderSide.BUY, 2, "50"));                       // reserves 100, not hit at 90
        AtomicInteger filled = new AtomicInteger();

        List<Outcome> outcomes = runConcurrently(
                () -> orderService.cancel(userId, racing),
                () -> filled.addAndGet(matching.onPriceTick(aapl, new BigDecimal("90"))));

        String status = statusOf(racing);
        if (status.equals("CANCELLED")) {
            assertThat(outcomes).containsExactly(Outcome.OK, Outcome.OK);
            assertThat(filled).hasValue(0);
            assertThat(balance()).isEqualTo(new BigDecimal("1000.0000"));
        } else {
            assertThat(status).isEqualTo("FILLED");
            assertThat(outcomes).containsExactly(Outcome.ORDER_NOT_CANCELLABLE, Outcome.OK);
            assertThat(filled).hasValue(1);
            assertThat(balance()).isEqualTo(new BigDecimal("550.0000"));
        }
        assertThat(blockedBalance()).isEqualTo(new BigDecimal("100.0000")).isEqualByComparingTo(reservedByNewOrders());
        assertLedgerMatchesBalance();
    }

    @RepeatedTest(10)
    void BR45_BR52_cancelRacingWithFillOfPendingSell_unblocksSharesExactlyOnce() throws Exception {
        accountService.deposit(userId, new BigDecimal("1000"));
        orderService.place(userId, new PlaceOrderRequest(OrderSide.BUY, OrderType.MARKET, aapl, 10, null, null));
        long racing = orderService.place(userId, limit(OrderSide.SELL, 4, "120")).id();
        orderService.place(userId, limit(OrderSide.SELL, 3, "200"));                     // not hit at 125

        List<Outcome> outcomes = runConcurrently(
                () -> orderService.cancel(userId, racing),
                () -> matching.onPriceTick(aapl, new BigDecimal("125")));

        Map<String, Object> position = jdbc.queryForMap(
                "SELECT quantity, blocked_quantity FROM positions WHERE user_id = ?", userId);
        if (statusOf(racing).equals("CANCELLED")) {
            assertThat(outcomes).containsExactly(Outcome.OK, Outcome.OK);
            assertThat(position).containsEntry("quantity", 10);
        } else {
            assertThat(statusOf(racing)).isEqualTo("FILLED");
            assertThat(outcomes).containsExactly(Outcome.ORDER_NOT_CANCELLABLE, Outcome.OK);
            assertThat(position).containsEntry("quantity", 6);
        }
        assertThat(position).containsEntry("blocked_quantity", 3);
        assertLedgerMatchesBalance();
    }

    // ------------------------------------------------------------------ helpers

    private PlaceOrderRequest limit(OrderSide side, int quantity, String limitPrice) {
        return new PlaceOrderRequest(side, OrderType.LIMIT, aapl, quantity, new BigDecimal(limitPrice), null);
    }

    private String statusOf(long orderId) {
        return jdbc.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, orderId);
    }

    private BigDecimal blockedBalance() {
        return jdbc.queryForObject("SELECT blocked_balance FROM accounts WHERE user_id = ?", BigDecimal.class, userId);
    }

    private BigDecimal reservedByNewOrders() {
        return jdbc.queryForObject(
                "SELECT COALESCE(SUM(reserved_amount), 0) FROM orders WHERE user_id = ? AND status = 'NEW'",
                BigDecimal.class, userId);
    }

    enum Outcome { OK, INSUFFICIENT_FUNDS, ORDER_NOT_CANCELLABLE }

    /** Starts all tasks at the same moment and maps each result; any unexpected exception fails the test. */
    @SafeVarargs
    private static List<Outcome> runConcurrently(Callable<?>... tasks) throws Exception {
        CountDownLatch ready = new CountDownLatch(tasks.length);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Outcome>> futures = new ArrayList<>();

        try (ExecutorService pool = Executors.newFixedThreadPool(tasks.length)) {
            for (Callable<?> task : tasks) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    start.await();
                    try {
                        task.call();
                        return Outcome.OK;
                    } catch (BusinessException ex) {
                        return Outcome.valueOf(ex.getCode());
                    }
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<Outcome> outcomes = new ArrayList<>();
            for (Future<Outcome> future : futures) {
                outcomes.add(future.get(30, TimeUnit.SECONDS));
            }
            return outcomes;
        }
    }

    private BigDecimal balance() {
        return jdbc.queryForObject("SELECT balance FROM accounts WHERE user_id = ?", BigDecimal.class, userId);
    }

    /** BR01 and BR31: the balance is never negative and equals both the ledger sum and the last balance_after. */
    private void assertLedgerMatchesBalance() {
        BigDecimal balance = balance();
        BigDecimal ledgerSum = jdbc.queryForObject("""
                SELECT SUM(CASE WHEN type IN ('DEPOSIT', 'SELL') THEN amount ELSE -amount END)
                FROM transactions WHERE user_id = ?""", BigDecimal.class, userId);
        BigDecimal lastBalanceAfter = jdbc.queryForObject(
                "SELECT balance_after FROM transactions WHERE user_id = ? ORDER BY id DESC LIMIT 1",
                BigDecimal.class, userId);

        assertThat(balance).isNotNegative();
        assertThat(ledgerSum).isEqualByComparingTo(balance);
        assertThat(lastBalanceAfter).isEqualByComparingTo(balance);
    }
}
