package com.ex.tradingPlatform.controllers;

import com.ex.tradingPlatform.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TransactionControllerTest extends IntegrationTest {

    private static final Instant T0 = Instant.parse("2026-01-01T10:00:00Z");

    private long userId;
    private String token;

    @BeforeEach
    void logInUser() throws Exception {
        userId = registerUser("john@example.com", "secret123");
        token = loginTokens("john@example.com", "secret123").get("accessToken").asText();
    }

    @Test
    void list_withoutToken_returns401() throws Exception {
        mvc.perform(get("/api/transactions"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void list_withNoTransactions_returnsEmptyPage() throws Exception {
        mvc.perform(withBearer(get("/api/transactions"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0));
    }

    @Test
    void BR32_list_returnsOnlyOwnTransactions() throws Exception {
        long otherId = registerUser("jane@example.com", "secret123");
        insert(userId, "DEPOSIT", "10.0000", "10.0000", T0);
        insert(otherId, "DEPOSIT", "999.0000", "999.0000", T0);
        insert(otherId, "WITHDRAW", "1.0000", "998.0000", T0.plusSeconds(1));

        mvc.perform(withBearer(get("/api/transactions"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].type").value("DEPOSIT"))
                .andExpect(jsonPath("$.content[0].amount").value(10.0))
                .andExpect(jsonPath("$.content[0].balanceAfter").value(10.0))
                .andExpect(jsonPath("$.content[0].createdAt").value("2026-01-01T10:00:00Z"));
    }

    @Test
    void BR32_list_isSortedByCreatedAtDescending() throws Exception {
        insert(userId, "DEPOSIT", "1.0000", "1.0000", T0.plusSeconds(10));
        insert(userId, "DEPOSIT", "2.0000", "3.0000", T0);
        insert(userId, "DEPOSIT", "3.0000", "6.0000", T0.plusSeconds(20));
        // same timestamp as the previous one but inserted later, so it comes first
        insert(userId, "DEPOSIT", "4.0000", "10.0000", T0.plusSeconds(20));

        mvc.perform(withBearer(get("/api/transactions"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].amount", contains(4.0, 3.0, 1.0, 2.0)));
    }

    @Test
    void BR32_list_paginates() throws Exception {
        for (int i = 1; i <= 5; i++) {
            insert(userId, "DEPOSIT", i + ".0000", "1.0000", T0.plusSeconds(i));
        }

        mvc.perform(withBearer(get("/api/transactions").param("page", "1").param("size", "2"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].amount", contains(3.0, 2.0)))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3));

        mvc.perform(withBearer(get("/api/transactions").param("page", "2").param("size", "2"), token))
                .andExpect(jsonPath("$.content[*].amount", contains(1.0)));

        mvc.perform(withBearer(get("/api/transactions").param("page", "3").param("size", "2"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)));
    }

    @Test
    void BR32_list_filtersByType() throws Exception {
        insert(userId, "DEPOSIT", "100.0000", "100.0000", T0);
        insert(userId, "WITHDRAW", "30.0000", "70.0000", T0.plusSeconds(1));
        insert(userId, "DEPOSIT", "5.0000", "75.0000", T0.plusSeconds(2));

        mvc.perform(withBearer(get("/api/transactions").param("type", "DEPOSIT"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[*].type", everyItem(is("DEPOSIT"))));

        mvc.perform(withBearer(get("/api/transactions").param("type", "WITHDRAW"), token))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].amount").value(30.0));
    }

    @Test
    void BR31_list_showsTransactionCreatedByDeposit() throws Exception {
        mvc.perform(withBearer(post("/api/account/deposit"), token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":50}"))
                .andExpect(status().isOk());

        mvc.perform(withBearer(get("/api/transactions"), token))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].type").value("DEPOSIT"))
                .andExpect(jsonPath("$.content[0].balanceAfter").value(50.0));
    }

    @Test
    void BR32_list_size100_isAccepted() throws Exception {
        mvc.perform(withBearer(get("/api/transactions").param("size", "100"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100));
    }

    @ParameterizedTest
    @CsvSource({"size, 0", "size, 101", "size, -1", "page, -1"})
    void BR32_list_pagingOutOfRange_returns400WithFieldError(String name, String value) throws Exception {
        mvc.perform(withBearer(get("/api/transactions").param(name, value), token))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors[0].field").value(name));
    }

    @ParameterizedTest
    @CsvSource({"type, FOO", "type, deposit", "page, abc"})
    void list_malformedParameter_returns400(String name, String value) throws Exception {
        mvc.perform(withBearer(get("/api/transactions").param(name, value), token))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    private void insert(long userId, String type, String amount, String balanceAfter, Instant createdAt) {
        jdbc.update("""
                        INSERT INTO transactions (user_id, type, amount, balance_after, created_at)
                        VALUES (?, ?, ?, ?, ?)""",
                userId, type, new BigDecimal(amount), new BigDecimal(balanceAfter), Timestamp.from(createdAt));
    }
}
