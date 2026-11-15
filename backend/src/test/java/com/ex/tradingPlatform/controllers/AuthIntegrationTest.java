package com.ex.tradingPlatform.controllers;

import com.ex.tradingPlatform.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthIntegrationTest extends IntegrationTest {

    private static final String EMAIL = "john@example.com";
    private static final String PASSWORD = "secret123";

    @Autowired
    JwtEncoder jwtEncoder;

    // ------------------------------------------------------------------ register

    @Test
    void register_returns201WithUserWithoutHash() throws Exception {
        register(EMAIL, PASSWORD)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(content().string(not(containsString("argon2"))));
    }

    @Test
    void BR07_registerDuplicateEmail_returns409EmailTaken() throws Exception {
        registerUser(EMAIL, PASSWORD);

        register(EMAIL, "other1234")
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("EMAIL_TAKEN"));
    }

    @Test
    void BR07_registerDuplicateEmailDifferentCase_returns409EmailTaken() throws Exception {
        registerUser(EMAIL, PASSWORD);

        register("John@EXAMPLE.com", "other1234")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_TAKEN"));
    }

    @Test
    void registerInvalidEmail_returns400WithFieldError() throws Exception {
        register("not-an-email", PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors[0].field").value("email"))
                .andExpect(jsonPath("$.errors[0].message").isNotEmpty());
    }

    @Test
    void BR13_registerWeakPassword_returns400WithFieldError() throws Exception {
        register(EMAIL, "password")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("password"))
                .andExpect(jsonPath("$.errors[0].message").value("must contain at least one letter and one digit"));
    }

    // ------------------------------------------------------------------ login

    @Test
    void BR14_loginWithValidCredentials_returnsTokens() throws Exception {
        registerUser(EMAIL, PASSWORD);

        login(EMAIL, PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900));
    }

    @Test
    void BR14_loginWithWrongPassword_returns401() throws Exception {
        registerUser(EMAIL, PASSWORD);

        login(EMAIL, "wrongpass1")
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Invalid credentials"));
    }

    @Test
    void BR14_loginWithNonexistentEmail_returns401WithBodyIdenticalToWrongPassword() throws Exception {
        registerUser(EMAIL, PASSWORD);

        String wrongPassword = login(EMAIL, "wrongpass1")
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
        String unknownEmail = login("nobody@example.com", PASSWORD)
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        assertThat(unknownEmail).isEqualTo(wrongPassword);
    }

    // ------------------------------------------------------------------ /api/users/me

    @Test
    void me_withoutToken_returns401() throws Exception {
        mvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void me_withValidToken_returns200WithCurrentUser() throws Exception {
        long userId = registerUser(EMAIL, PASSWORD);
        String accessToken = loginTokens(EMAIL, PASSWORD).get("accessToken").asText();

        mvc.perform(withBearer(get("/api/users/me"), accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId))
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    void me_withExpiredToken_returns401() throws Exception {
        long userId = registerUser(EMAIL, PASSWORD);
        Instant issued = Instant.now().minus(1, ChronoUnit.HOURS);
        String expired = encode(JwtClaimsSet.builder()
                .subject(String.valueOf(userId))
                .claim("role", "USER")
                .issuedAt(issued)
                .expiresAt(issued.plus(15, ChronoUnit.MINUTES))
                .build());

        mvc.perform(withBearer(get("/api/users/me"), expired))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void me_withGarbageToken_returns401() throws Exception {
        mvc.perform(withBearer(get("/api/users/me"), "garbage.token.value"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    // ------------------------------------------------------------------ refresh / logout

    @Test
    void BR16_refreshRotatesTokens_andOldRefreshTokenThenFails() throws Exception {
        registerUser(EMAIL, PASSWORD);
        JsonNode first = loginTokens(EMAIL, PASSWORD);
        String oldRefresh = first.get("refreshToken").asText();

        JsonNode second = readJson(refresh(oldRefresh).andExpect(status().isOk()));
        assertThat(second.get("refreshToken").asText()).isNotEqualTo(oldRefresh);
        assertThat(second.get("accessToken").asText()).isNotBlank();

        refresh(oldRefresh)
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void BR17_logoutRevokesRefreshToken() throws Exception {
        registerUser(EMAIL, PASSWORD);
        String refreshToken = loginTokens(EMAIL, PASSWORD).get("refreshToken").asText();

        postJson("/api/auth/logout", body("refreshToken", refreshToken))
                .andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM refresh_tokens WHERE revoked_at IS NULL", Integer.class)).isZero();
        refresh(refreshToken).andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------ roles

    @Test
    void adminPing_asUser_returns403() throws Exception {
        registerUser(EMAIL, PASSWORD);
        String accessToken = loginTokens(EMAIL, PASSWORD).get("accessToken").asText();

        mvc.perform(withBearer(get("/api/admin/ping"), accessToken))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void adminPing_asAdmin_returns200() throws Exception {
        long userId = registerUser(EMAIL, PASSWORD);
        jdbc.update("UPDATE users SET role = 'ADMIN' WHERE id = ?", userId);
        String accessToken = loginTokens(EMAIL, PASSWORD).get("accessToken").asText();

        mvc.perform(withBearer(get("/api/admin/ping"), accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("pong"));
    }

    private String encode(JwtClaimsSet claims) {
        return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }
}
