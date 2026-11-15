package com.ex.tradingPlatform.controllers;

import com.ex.tradingPlatform.TestcontainersConfiguration;
import com.ex.tradingPlatform.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class RegisterEndpointTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PasswordEncoder passwordEncoder;

    @MockitoSpyBean
    UserRepository userRepository;

    @BeforeEach
    void cleanDb() {
        jdbc.update("DELETE FROM accounts");
        jdbc.update("DELETE FROM refresh_tokens");
        jdbc.update("DELETE FROM users");
    }

    private ResultActions register(String email, String password) throws Exception {
        String json = "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password);
        return mvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    @Test
    void register_returns201WithUserAndNoHash() throws Exception {
        register("john@example.com", "secret123")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.email").value("john@example.com"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(content().string(not(containsString("argon2"))));
    }

    @Test
    void register_storesArgon2HashNotPlainPassword() throws Exception {
        register("john@example.com", "secret123").andExpect(status().isCreated());

        String hash = jdbc.queryForObject(
                "SELECT password_hash FROM users WHERE email = 'john@example.com'", String.class);
        assertThat(hash).startsWith("$argon2id$").isNotEqualTo("secret123");
        assertThat(passwordEncoder.matches("secret123", hash)).isTrue();
    }

    @Test
    void register_normalizesEmailToLowercaseTrimmed() throws Exception {
        register("  John.Doe@Example.COM", "secret123")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("john.doe@example.com"));
    }

    @Test
    void BR12_registerCreatesAccountWithZeroBalance() throws Exception {
        register("john@example.com", "secret123").andExpect(status().isCreated());

        Map<String, Object> account = jdbc.queryForMap("""
                SELECT a.balance, a.blocked_balance FROM accounts a
                JOIN users u ON u.id = a.user_id WHERE u.email = 'john@example.com'""");
        assertThat((BigDecimal) account.get("balance")).isEqualByComparingTo("0");
        assertThat((BigDecimal) account.get("blocked_balance")).isEqualByComparingTo("0");
    }

    @Test
    void BR07_duplicateEmailReturns409EmailTaken() throws Exception {
        register("john@example.com", "secret123").andExpect(status().isCreated());

        register("JOHN@example.com", "other1234")
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("EMAIL_TAKEN"));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM users", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM accounts", Integer.class)).isEqualTo(1);
    }

    @Test
    void BR07_duplicateEmailCaughtByUniqueConstraintWhenPrecheckMisses() throws Exception {
        register("john@example.com", "secret123").andExpect(status().isCreated());

        // simulate a concurrent registration: the existence check sees nothing
        doReturn(false).when(userRepository).existsByEmail(anyString());

        register("john@example.com", "other1234")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_TAKEN"));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM accounts", Integer.class)).isEqualTo(1);
    }

    @Test
    void invalidEmail_returns400WithFieldError() throws Exception {
        register("not-an-email", "secret123")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("email"));
    }

    @Test
    void emailWithoutDotInDomain_returns400() throws Exception {
        register("john@localhost", "secret123")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("email"));
    }

    @Test
    void tooLongEmail_returns400() throws Exception {
        String email = "a".repeat(250) + "@x.pl";
        register(email, "secret123")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field=='email')]").exists());
    }

    @Test
    void BR13_passwordTooShort_returns400() throws Exception {
        register("john@example.com", "abc123")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("password"));
    }

    @Test
    void BR13_passwordTooLong_returns400() throws Exception {
        register("john@example.com", "a1".repeat(65))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("password"));
    }

    @Test
    void BR13_passwordWithoutDigit_returns400() throws Exception {
        register("john@example.com", "onlyletters")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("password"));
    }

    @Test
    void BR13_passwordWithoutLetter_returns400() throws Exception {
        register("john@example.com", "1234567890")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("password"));
    }

    @Test
    void missingFields_returns400WithBothErrors() throws Exception {
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field=='email')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field=='password')]").exists());
    }
}
