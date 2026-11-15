package com.ex.tradingPlatform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base class for integration tests: full Spring context, MockMvc and a real PostgreSQL.
 * <p>
 * The container is a JVM-wide singleton: started once in the static initializer and
 * never stopped explicitly (Testcontainers' Ryuk removes it when the JVM exits), so all
 * subclasses - and all cached Spring contexts - share one database.
 * Every test starts with empty tables.
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class IntegrationTest {

    @ServiceConnection
    protected static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16"));

    static {
        POSTGRES.start();
    }

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected JdbcTemplate jdbc;

    @BeforeEach
    void cleanDatabase() {
        jdbc.execute("""
                TRUNCATE refresh_tokens, transactions, positions, orders,
                         price_ticks, accounts, instruments, users
                RESTART IDENTITY CASCADE""");
    }

    // ------------------------------------------------------------------ HTTP helpers

    protected ResultActions postJson(String url, Object body) throws Exception {
        return mvc.perform(post(url)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    protected static MockHttpServletRequestBuilder withBearer(MockHttpServletRequestBuilder request, String token) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    protected static Map<String, String> body(String... keyValues) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put(keyValues[i], keyValues[i + 1]);
        }
        return map;
    }

    protected JsonNode readJson(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    // ------------------------------------------------------------------ auth helpers

    protected ResultActions register(String email, String password) throws Exception {
        return postJson("/api/auth/register", body("email", email, "password", password));
    }

    protected ResultActions login(String email, String password) throws Exception {
        return postJson("/api/auth/login", body("email", email, "password", password));
    }

    protected ResultActions refresh(String refreshToken) throws Exception {
        return postJson("/api/auth/refresh", body("refreshToken", refreshToken));
    }

    /** Registers the user and returns the id. */
    protected long registerUser(String email, String password) throws Exception {
        return readJson(register(email, password).andExpect(status().isCreated())).get("id").asLong();
    }

    /** Logs in and returns the token response (accessToken, refreshToken, ...). */
    protected JsonNode loginTokens(String email, String password) throws Exception {
        return readJson(login(email, password).andExpect(status().isOk()));
    }
}
