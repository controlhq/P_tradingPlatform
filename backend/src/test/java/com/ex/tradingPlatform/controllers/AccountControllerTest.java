package com.ex.tradingPlatform.controllers;

import com.ex.tradingPlatform.IntegrationTest;
import com.ex.tradingPlatform.exceptions.BusinessException;
import com.ex.tradingPlatform.services.AccountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AccountControllerTest extends IntegrationTest {

    @Autowired
    private AccountService accountService;

    private long userId;
    private String token;

    @BeforeEach
    void logInUser() throws Exception {
        userId = registerUser("john@example.com", "secret123");
        token = loginTokens("john@example.com", "secret123").get("accessToken").asText();
    }

    // ------------------------------------------------------------------ GET /api/account

    @Test
    void get_withoutToken_returns401() throws Exception {
        mvc.perform(get("/api/account"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void BR12_get_newAccount_hasZeroBalance() throws Exception {
        mvc.perform(withBearer(get("/api/account"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(0))
                .andExpect(jsonPath("$.blockedBalance").value(0))
                .andExpect(jsonPath("$.available").value(0));
    }

    @Test
    void BR28_get_availableIsBalanceMinusBlocked() throws Exception {
        setBalance(userId, "1000.5000", "250.2500");

        mvc.perform(withBearer(get("/api/account"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(1000.5))
                .andExpect(jsonPath("$.blockedBalance").value(250.25))
                .andExpect(jsonPath("$.available").value(750.25));
    }

    @Test
    void BR28_get_returnsOnlyCallersAccount() throws Exception {
        long otherId = registerUser("jane@example.com", "secret123");
        setBalance(otherId, "999.0000", "0");

        mvc.perform(withBearer(get("/api/account"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(0));
    }

    // ------------------------------------------------------------------ deposit / withdraw

    @Test
    void depositAndWithdraw_withoutToken_return401() throws Exception {
        mvc.perform(post("/api/account/deposit").contentType(MediaType.APPLICATION_JSON).content("{\"amount\":1}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/account/withdraw").contentType(MediaType.APPLICATION_JSON).content("{\"amount\":1}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void BR31_deposit_increasesBalanceAndRecordsTransaction() throws Exception {
        cash("deposit", "100.5")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(100.5))
                .andExpect(jsonPath("$.available").value(100.5));

        assertThat(jdbc.queryForMap("SELECT type, amount, balance_after FROM transactions WHERE user_id = ?", userId))
                .containsEntry("type", "DEPOSIT")
                .containsEntry("amount", new BigDecimal("100.5000"))
                .containsEntry("balance_after", new BigDecimal("100.5000"));
    }

    @Test
    void BR31_withdraw_decreasesBalanceAndRecordsTransaction() throws Exception {
        cash("deposit", "100").andExpect(status().isOk());

        cash("withdraw", "30.25")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(69.75));

        assertThat(jdbc.queryForMap(
                "SELECT type, amount, balance_after FROM transactions WHERE user_id = ? ORDER BY id DESC LIMIT 1", userId))
                .containsEntry("type", "WITHDRAW")
                .containsEntry("amount", new BigDecimal("30.2500"))
                .containsEntry("balance_after", new BigDecimal("69.7500"));
    }

    @Test
    void BR29_deposit_maxAmountAndFourDecimalPlaces_areAccepted() throws Exception {
        cash("deposit", "1000000").andExpect(status().isOk());
        cash("deposit", "0.0001").andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(1000000.0001));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "-0.0001", "0.00001", "12.34567", "1000000.0001", "2000000", "null"})
    void BR29_deposit_invalidAmount_returns400WithFieldError(String amount) throws Exception {
        cash("deposit", amount)
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors[0].field").value("amount"));

        assertBalanceAndLedger("0.0000", 0);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-5", "0.00001", "1000000.0001"})
    void BR29_withdraw_invalidAmount_returns400WithFieldError(String amount) throws Exception {
        setBalance(userId, "5000000.0000", "0");

        cash("withdraw", amount)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("amount"));
    }

    @Test
    void deposit_missingOrMalformedAmount_returns400() throws Exception {
        mvc.perform(withBearer(post("/api/account/deposit"), token)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("amount"));
        mvc.perform(withBearer(post("/api/account/deposit"), token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":\"abc\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void BR01_withdraw_fromEmptyAccount_returns409AndBalanceStaysZero() throws Exception {
        cash("withdraw", "0.0001")
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"));

        assertBalanceAndLedger("0.0000", 0);
    }

    @Test
    void BR30_withdraw_moreThanBalance_returns409AndChangesNothing() throws Exception {
        cash("deposit", "100").andExpect(status().isOk());

        cash("withdraw", "100.0001")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"));

        assertBalanceAndLedger("100.0000", 1);
    }

    @Test
    void BR30_withdraw_cannotTouchBlockedFunds() throws Exception {
        setBalance(userId, "1000.0000", "600.0000");

        cash("withdraw", "400.0001")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"));

        cash("withdraw", "400")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(600))
                .andExpect(jsonPath("$.blockedBalance").value(600))
                .andExpect(jsonPath("$.available").value(0));
    }

    @Test
    void BR01_BR30_concurrentWithdrawals_neverOverdrawTheAccount() throws Exception {
        cash("deposit", "500").andExpect(status().isOk());

        List<Callable<Boolean>> withdrawals = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            withdrawals.add(() -> {
                try {
                    accountService.withdraw(userId, new BigDecimal("100"));
                    return true;
                } catch (BusinessException ex) {
                    return false;
                }
            });
        }

        int succeeded = 0;
        try (ExecutorService pool = Executors.newFixedThreadPool(10)) {
            for (Future<Boolean> result : pool.invokeAll(withdrawals)) {
                succeeded += result.get() ? 1 : 0;
            }
        }

        assertThat(succeeded).isEqualTo(5);
        assertBalanceAndLedger("0.0000", 6);
    }

    @Test
    void BR31_ledger_matchesBalanceAfterMixedOperations() throws Exception {
        cash("deposit", "1000").andExpect(status().isOk());
        cash("withdraw", "250.5").andExpect(status().isOk());
        cash("withdraw", "9999").andExpect(status().isConflict());
        cash("deposit", "0.1234").andExpect(status().isOk());
        cash("deposit", "0").andExpect(status().isBadRequest());
        cash("withdraw", "749.6234").andExpect(status().isOk());
        cash("deposit", "42").andExpect(status().isOk());

        assertBalanceAndLedger("42.0000", 5);
        BigDecimal ledgerSum = jdbc.queryForObject("""
                SELECT COALESCE(SUM(CASE WHEN type IN ('DEPOSIT', 'SELL') THEN amount ELSE -amount END), 0)
                FROM transactions WHERE user_id = ?""", BigDecimal.class, userId);
        assertThat(ledgerSum).isEqualByComparingTo("42");
    }

    // ------------------------------------------------------------------ helpers

    private ResultActions cash(String operation, String amount) throws Exception {
        return mvc.perform(withBearer(post("/api/account/" + operation), token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":" + amount + "}"));
    }

    /** Balance in the account, number of ledger entries and that the newest entry's balance_after matches. */
    private void assertBalanceAndLedger(String expectedBalance, int expectedTransactions) {
        BigDecimal balance = jdbc.queryForObject(
                "SELECT balance FROM accounts WHERE user_id = ?", BigDecimal.class, userId);
        assertThat(balance).isEqualTo(new BigDecimal(expectedBalance));

        List<BigDecimal> balancesAfter = jdbc.queryForList(
                "SELECT balance_after FROM transactions WHERE user_id = ? ORDER BY id DESC", BigDecimal.class, userId);
        assertThat(balancesAfter).hasSize(expectedTransactions);
        if (expectedTransactions > 0) {
            assertThat(balancesAfter.getFirst()).isEqualTo(balance);
        }
    }

    private void setBalance(long userId, String balance, String blocked) {
        jdbc.update("UPDATE accounts SET balance = ?, blocked_balance = ? WHERE user_id = ?",
                new BigDecimal(balance), new BigDecimal(blocked), userId);
    }
}
