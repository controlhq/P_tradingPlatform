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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OrderQueryControllerTest extends IntegrationTest {

    private static final Instant T0 = Instant.parse("2026-01-01T10:00:00Z");

    private long userId;
    private long otherId;
    private String token;
    private long aapl;

    @BeforeEach
    void setUp() throws Exception {
        userId = registerUser("john@example.com", "secret123");
        otherId = registerUser("jane@example.com", "secret123");
        token = loginTokens("john@example.com", "secret123").get("accessToken").asText();
        aapl = jdbc.queryForObject(
                "INSERT INTO instruments (symbol, name) VALUES ('AAPL', 'Apple') RETURNING id", Long.class);
    }

    @Test
    void listAndGet_withoutToken_return401() throws Exception {
        long id = insertFilled(userId, 1, T0);

        mvc.perform(get("/api/orders")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/orders/{id}", id)).andExpect(status().isUnauthorized());
    }

    @Test
    void BR40_list_returnsOnlyOwnOrdersNewestFirst() throws Exception {
        insertFilled(userId, 1, T0.plusSeconds(10));
        insertFilled(userId, 2, T0);
        insertFilled(otherId, 99, T0.plusSeconds(5));
        insertFilled(userId, 3, T0.plusSeconds(20));
        // same timestamp as the previous one but inserted later, so it comes first
        insertFilled(userId, 4, T0.plusSeconds(20));

        mvc.perform(withBearer(get("/api/orders"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.content[*].quantity", contains(4, 3, 1, 2)))
                .andExpect(jsonPath("$.content[0].symbol").value("AAPL"))
                .andExpect(jsonPath("$.content[0].instrumentId").value(aapl));
    }

    @Test
    void BR40_list_paginates() throws Exception {
        for (int i = 1; i <= 5; i++) {
            insertFilled(userId, i, T0.plusSeconds(i));
        }

        mvc.perform(withBearer(get("/api/orders").param("page", "1").param("size", "2"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].quantity", contains(3, 2)))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3));
    }

    @Test
    void BR40_list_filtersByStatus() throws Exception {
        insertFilled(userId, 1, T0);
        insertNewLimit(userId, 2, T0.plusSeconds(1));
        insertFilled(userId, 3, T0.plusSeconds(2));

        mvc.perform(withBearer(get("/api/orders").param("status", "FILLED"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[*].status", everyItem(is("FILLED"))));

        mvc.perform(withBearer(get("/api/orders").param("status", "NEW"), token))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].type").value("LIMIT"))
                .andExpect(jsonPath("$.content[0].limitPrice").value(90.0));

        mvc.perform(withBearer(get("/api/orders").param("status", "CANCELLED"), token))
                .andExpect(jsonPath("$.content", hasSize(0)));
    }

    @Test
    void BR40_get_ownOrder_returnsDetails() throws Exception {
        long id = insertFilled(userId, 7, T0);

        mvc.perform(withBearer(get("/api/orders/{id}", id), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.symbol").value("AAPL"))
                .andExpect(jsonPath("$.side").value("BUY"))
                .andExpect(jsonPath("$.type").value("MARKET"))
                .andExpect(jsonPath("$.status").value("FILLED"))
                .andExpect(jsonPath("$.quantity").value(7))
                .andExpect(jsonPath("$.filledPrice").value(100.0))
                .andExpect(jsonPath("$.filledAt").value("2026-01-01T10:00:00Z"))
                .andExpect(jsonPath("$.createdAt").value("2026-01-01T10:00:00Z"));
    }

    @Test
    void BR40_get_otherUsersOrder_returns404() throws Exception {
        long foreign = insertFilled(otherId, 1, T0);

        mvc.perform(withBearer(get("/api/orders/{id}", foreign), token))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
    }

    @Test
    void get_unknownOrder_returns404() throws Exception {
        mvc.perform(withBearer(get("/api/orders/{id}", 999_999), token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
    }

    @ParameterizedTest
    @CsvSource({"size, 0", "size, 101", "page, -1"})
    void BR40_list_pagingOutOfRange_returns400WithFieldError(String name, String value) throws Exception {
        mvc.perform(withBearer(get("/api/orders").param(name, value), token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value(name));
    }

    @Test
    void list_unknownStatus_returns400() throws Exception {
        mvc.perform(withBearer(get("/api/orders").param("status", "DONE"), token))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    private long insertFilled(long userId, int quantity, Instant createdAt) {
        return jdbc.queryForObject("""
                        INSERT INTO orders (user_id, instrument_id, side, type, status, quantity,
                                            filled_price, filled_at, created_at, updated_at)
                        VALUES (?, ?, 'BUY', 'MARKET', 'FILLED', ?, 100, ?, ?, ?) RETURNING id""",
                Long.class, userId, aapl, quantity,
                Timestamp.from(createdAt), Timestamp.from(createdAt), Timestamp.from(createdAt));
    }

    private void insertNewLimit(long userId, int quantity, Instant createdAt) {
        jdbc.update("""
                        INSERT INTO orders (user_id, instrument_id, side, type, status, quantity, limit_price,
                                            reserved_amount, created_at, updated_at)
                        VALUES (?, ?, 'BUY', 'LIMIT', 'NEW', ?, ?, ?, ?, ?)""",
                userId, aapl, quantity, new BigDecimal("90"), new BigDecimal("180"),
                Timestamp.from(createdAt), Timestamp.from(createdAt));
    }
}
