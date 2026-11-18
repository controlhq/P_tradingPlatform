package com.ex.tradingPlatform.controllers;

import com.ex.tradingPlatform.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminInstrumentControllerTest extends IntegrationTest {

    private static final String URL = "/api/admin/instruments";

    private String adminToken;
    private String userToken;

    @BeforeEach
    void logIn() throws Exception {
        registerUser("admin@example.com", "secret123");
        // role is read at login, so promote before logging in
        jdbc.update("UPDATE users SET role = 'ADMIN' WHERE email = 'admin@example.com'");
        adminToken = loginTokens("admin@example.com", "secret123").get("accessToken").asText();

        registerUser("john@example.com", "secret123");
        userToken = loginTokens("john@example.com", "secret123").get("accessToken").asText();
    }

    // ------------------------------------------------------------------ access

    @Test
    void create_withoutToken_returns401() throws Exception {
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                        .content(json(request("AAPL", "Apple Inc.", "189.50"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void create_asUser_returns403() throws Exception {
        create(userToken, request("AAPL", "Apple Inc.", "189.50"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));

        assertThat(instrumentCount()).isZero();
    }

    @Test
    void create_asUserWithInvalidBody_returns403NotValidationError() throws Exception {
        create(userToken, request("aapl", "", "-1"))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ happy path

    @Test
    void BR21_create_returns201AndStoresStartingPriceTick() throws Exception {
        long id = readJson(create(adminToken, request("AAPL", "Apple Inc.", "189.50"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.symbol").value("AAPL"))
                .andExpect(jsonPath("$.name").value("Apple Inc."))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.lastPrice").value(189.5))
                .andExpect(jsonPath("$.lastPriceAt").isNotEmpty()))
                .get("id").asLong();

        BigDecimal stored = jdbc.queryForObject(
                "SELECT price FROM price_ticks WHERE instrument_id = ?", BigDecimal.class, id);
        assertThat(stored).isEqualByComparingTo("189.5");

        mvc.perform(withBearer(get("/api/instruments"), userToken))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].symbol").value("AAPL"));
    }

    @Test
    void BR20_create_symbolWithDigitsAndDot_returns201() throws Exception {
        create(adminToken, request("BRK.B", "Berkshire Hathaway B", "400"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.symbol").value("BRK.B"));
    }

    @Test
    void BR21_create_priceIsRoundedHalfUpToFourPlaces() throws Exception {
        create(adminToken, request("AAPL", "Apple Inc.", "10.12345"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.lastPrice").value(10.1235));
    }

    // ------------------------------------------------------------------ conflicts

    @Test
    void BR20_create_duplicateSymbol_returns409SymbolTaken() throws Exception {
        create(adminToken, request("AAPL", "Apple Inc.", "189.50")).andExpect(status().isCreated());

        create(adminToken, request("AAPL", "Another Apple", "1"))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("SYMBOL_TAKEN"));

        assertThat(instrumentCount()).isEqualTo(1);
    }

    // ------------------------------------------------------------------ validation

    @Test
    void BR20_create_lowercaseSymbol_returns400() throws Exception {
        expectFieldError(request("aapl", "Apple Inc.", "189.50"), "symbol");
    }

    @Test
    void BR20_create_symbolLongerThan16_returns400() throws Exception {
        expectFieldError(request("ABCDEFGHIJKLMNOPQ", "Too long", "1"), "symbol");
    }

    @Test
    void BR20_create_symbolWithIllegalCharacter_returns400() throws Exception {
        expectFieldError(request("AA-PL", "Apple Inc.", "1"), "symbol");
    }

    @Test
    void create_missingSymbol_returns400() throws Exception {
        expectFieldError(request(null, "Apple Inc.", "1"), "symbol");
    }

    @Test
    void create_blankName_returns400() throws Exception {
        expectFieldError(request("AAPL", "   ", "1"), "name");
    }

    @Test
    void BR21_create_zeroPrice_returns400() throws Exception {
        expectFieldError(request("AAPL", "Apple Inc.", "0"), "initialPrice");
    }

    @Test
    void BR21_create_negativePrice_returns400() throws Exception {
        expectFieldError(request("AAPL", "Apple Inc.", "-5"), "initialPrice");
    }

    @Test
    void BR21_create_priceRoundingToZero_returns400() throws Exception {
        expectFieldError(request("AAPL", "Apple Inc.", "0.00004"), "initialPrice");
    }

    @Test
    void BR21_create_missingPrice_returns400() throws Exception {
        expectFieldError(request("AAPL", "Apple Inc.", null), "initialPrice");
    }

    // ------------------------------------------------------------------ helpers

    private ResultActions create(String token, Map<String, Object> body) throws Exception {
        return mvc.perform(withBearer(post(URL), token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(body)));
    }

    private void expectFieldError(Map<String, Object> body, String field) throws Exception {
        create(adminToken, body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value(field));
        assertThat(instrumentCount()).isZero();
    }

    /** Price is sent as a JSON number, not a string. */
    private static Map<String, Object> request(String symbol, String name, String initialPrice) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("symbol", symbol);
        body.put("name", name);
        body.put("initialPrice", initialPrice != null ? new BigDecimal(initialPrice) : null);
        return body;
    }

    private String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    private int instrumentCount() {
        return jdbc.queryForObject("SELECT count(*) FROM instruments", Integer.class);
    }
}
