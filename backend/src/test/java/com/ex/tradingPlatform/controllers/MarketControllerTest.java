package com.ex.tradingPlatform.controllers;

import com.ex.tradingPlatform.IntegrationTest;
import com.ex.tradingPlatform.services.MarketHoursService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.time.Instant;
import java.util.Optional;

import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MarketControllerTest extends IntegrationTest {

    // the hours logic itself is covered with a fixed clock in MarketHoursServiceTest
    @MockitoSpyBean
    private MarketHoursService marketHoursService;

    private String token;

    @BeforeEach
    void logInUser() throws Exception {
        registerUser("john@example.com", "secret123");
        token = loginTokens("john@example.com", "secret123").get("accessToken").asText();
    }

    @Test
    void status_withoutToken_returns401() throws Exception {
        mvc.perform(get("/api/market/status"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void BR23_status_whenOpen_returnsClosingTime() throws Exception {
        doReturn(true).when(marketHoursService).isOpen();
        doReturn(Optional.of(Instant.parse("2026-11-18T16:00:00Z"))).when(marketHoursService).nextChange();

        mvc.perform(withBearer(get("/api/market/status"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.open").value(true))
                .andExpect(jsonPath("$.nextChange").value("2026-11-18T16:00:00Z"));
    }

    @Test
    void BR23_status_whenClosed_returnsNextOpening() throws Exception {
        doReturn(false).when(marketHoursService).isOpen();
        doReturn(Optional.of(Instant.parse("2026-11-23T08:00:00Z"))).when(marketHoursService).nextChange();

        mvc.perform(withBearer(get("/api/market/status"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.open").value(false))
                .andExpect(jsonPath("$.nextChange").value("2026-11-23T08:00:00Z"));
    }

    @Test
    void BR23_status_whenAlwaysOpen_returnsNullNextChange() throws Exception {
        doReturn(true).when(marketHoursService).isOpen();
        doReturn(Optional.empty()).when(marketHoursService).nextChange();

        mvc.perform(withBearer(get("/api/market/status"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.open").value(true))
                .andExpect(jsonPath("$.nextChange").doesNotExist());
    }
}
