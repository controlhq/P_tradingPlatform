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
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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

    // ------------------------------------------------------------------ helpers

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
