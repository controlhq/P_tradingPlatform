package com.ex.tradingPlatform.controllers;

import com.ex.tradingPlatform.IntegrationTest;
import com.ex.tradingPlatform.repositories.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Registration details beyond the main flow covered in {@link AuthIntegrationTest}.
 */
class RegisterEndpointTest extends IntegrationTest {

    @Autowired
    PasswordEncoder passwordEncoder;

    @MockitoSpyBean
    UserRepository userRepository;

    @Test
    void register_storesArgon2HashNotPlainPassword() throws Exception {
        registerUser("john@example.com", "secret123");

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
        registerUser("john@example.com", "secret123");

        Map<String, Object> account = jdbc.queryForMap("""
                SELECT a.balance, a.blocked_balance FROM accounts a
                JOIN users u ON u.id = a.user_id WHERE u.email = 'john@example.com'""");
        assertThat((BigDecimal) account.get("balance")).isEqualByComparingTo("0");
        assertThat((BigDecimal) account.get("blocked_balance")).isEqualByComparingTo("0");
    }

    @Test
    void BR07_duplicateEmail_doesNotCreateSecondUserOrAccount() throws Exception {
        registerUser("john@example.com", "secret123");
        register("JOHN@example.com", "other1234").andExpect(status().isConflict());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM users", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM accounts", Integer.class)).isEqualTo(1);
    }

    @Test
    void BR07_duplicateEmailCaughtByUniqueConstraintWhenPrecheckMisses() throws Exception {
        registerUser("john@example.com", "secret123");

        // simulate a concurrent registration: the existence check sees nothing
        doReturn(false).when(userRepository).existsByEmail(anyString());

        register("john@example.com", "other1234")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_TAKEN"));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM accounts", Integer.class)).isEqualTo(1);
    }

    @Test
    void emailWithoutDotInDomain_returns400() throws Exception {
        register("john@localhost", "secret123")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("email"));
    }

    @Test
    void tooLongEmail_returns400() throws Exception {
        register("a".repeat(250) + "@x.pl", "secret123")
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
    void BR13_passwordWithoutLetter_returns400() throws Exception {
        register("john@example.com", "1234567890")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("password"));
    }

    @Test
    void missingFields_returns400WithBothErrors() throws Exception {
        postJson("/api/auth/register", Map.of())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field=='email')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field=='password')]").exists());
    }
}
