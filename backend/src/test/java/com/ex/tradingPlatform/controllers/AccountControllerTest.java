package com.ex.tradingPlatform.controllers;

import com.ex.tradingPlatform.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AccountControllerTest extends IntegrationTest {

    private long userId;
    private String token;

    @BeforeEach
    void logInUser() throws Exception {
        userId = registerUser("john@example.com", "secret123");
        token = loginTokens("john@example.com", "secret123").get("accessToken").asText();
    }

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

    private void setBalance(long userId, String balance, String blocked) {
        jdbc.update("UPDATE accounts SET balance = ?, blocked_balance = ? WHERE user_id = ?",
                new BigDecimal(balance), new BigDecimal(blocked), userId);
    }
}
