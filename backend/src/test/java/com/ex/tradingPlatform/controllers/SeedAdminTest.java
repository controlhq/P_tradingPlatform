package com.ex.tradingPlatform.controllers;

import com.ex.tradingPlatform.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import javax.sql.DataSource;
import java.sql.Connection;

import org.springframework.beans.factory.annotation.Autowired;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the admin seeded by V3__seed_admin.sql. IntegrationTest truncates all tables
 * before each test, so the seed script is re-applied here.
 */
class SeedAdminTest extends IntegrationTest {

    @Autowired
    DataSource dataSource;

    @BeforeEach
    void applySeed() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V3__seed_admin.sql"));
        }
    }

    @Test
    void seededAdminCanLogInAndAccessAdminEndpoint() throws Exception {
        String accessToken = loginTokens("admin@trading.local", "Admin12345").get("accessToken").asText();

        mvc.perform(withBearer(get("/api/users/me"), accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));
        mvc.perform(withBearer(get("/api/admin/ping"), accessToken))
                .andExpect(status().isOk());
    }

    @Test
    void BR09_seededAdminHasAccount() {
        Integer accounts = jdbc.queryForObject("""
                SELECT count(*) FROM accounts a JOIN users u ON u.id = a.user_id
                WHERE u.email = 'admin@trading.local'""", Integer.class);
        org.assertj.core.api.Assertions.assertThat(accounts).isEqualTo(1);
    }
}
