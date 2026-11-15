package com.ex.tradingPlatform.controllers;

import com.ex.tradingPlatform.IntegrationTest;
import com.ex.tradingPlatform.services.TokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Token internals and security edge cases beyond the main flow covered in {@link AuthIntegrationTest}.
 */
class AuthEdgeCasesTest extends IntegrationTest {

    private static final String EMAIL = "john@example.com";
    private static final String PASSWORD = "secret123";

    @Autowired JwtDecoder jwtDecoder;
    @Autowired TokenService tokenService;

    private long userId;

    @BeforeEach
    void setUp() throws Exception {
        userId = registerUser(EMAIL, PASSWORD);
    }

    // ---------------------------------------------------------------- tokens

    @Test
    void BR14_accessTokenIsHs256WithSubRoleAnd15MinLifetime() throws Exception {
        Jwt jwt = jwtDecoder.decode(loginTokens(EMAIL, PASSWORD).get("accessToken").asText());

        assertThat(jwt.getSubject()).isEqualTo(String.valueOf(userId));
        assertThat(jwt.getClaimAsString("role")).isEqualTo("USER");
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())).isEqualTo(Duration.ofMinutes(15));
        assertThat(jwt.getHeaders()).containsEntry("alg", "HS256");
    }

    @Test
    void BR15_refreshTokenStoredOnlyAsSha256HashWith7DayExpiry() throws Exception {
        String raw = loginTokens(EMAIL, PASSWORD).get("refreshToken").asText();

        assertThat(raw).matches("^[A-Za-z0-9_-]{43}$"); // 32 bytes base64url
        Map<String, Object> row = jdbc.queryForMap("SELECT token_hash, expires_at, created_at FROM refresh_tokens");
        assertThat(row.get("token_hash")).isEqualTo(tokenService.hashRefreshToken(raw)).isNotEqualTo(raw);
        Instant created = ((Timestamp) row.get("created_at")).toInstant();
        Instant expires = ((Timestamp) row.get("expires_at")).toInstant();
        assertThat(Duration.between(created, expires)).isCloseTo(Duration.ofDays(7), Duration.ofSeconds(5));
    }

    // ---------------------------------------------------------------- login

    @Test
    void loginNormalizesEmail() throws Exception {
        loginTokens("  JOHN@Example.com ", PASSWORD);
    }

    @Test
    void BR14_disabledUserCannotLogIn() throws Exception {
        jdbc.update("UPDATE users SET enabled = false");
        login(EMAIL, PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid credentials"));
    }

    @Test
    void loginWithBlankFields_returns400() throws Exception {
        login("", "").andExpect(status().isBadRequest());
    }

    // ---------------------------------------------------------------- refresh / logout

    @Test
    void BR16_reusingRevokedRefreshTokenRevokesAllUserTokens() throws Exception {
        String stolen = loginTokens(EMAIL, PASSWORD).get("refreshToken").asText();
        String otherSession = loginTokens(EMAIL, PASSWORD).get("refreshToken").asText();
        String rotated = readJson(refresh(stolen).andExpect(status().isOk())).get("refreshToken").asText();

        refresh(stolen)
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));

        assertThat(revokedAt(rotated)).isNotNull();
        assertThat(revokedAt(otherSession)).isNotNull();
        refresh(rotated).andExpect(status().isUnauthorized());
        refresh(otherSession).andExpect(status().isUnauthorized());
    }

    @Test
    void BR16_expiredRefreshTokenIsRejected() throws Exception {
        String raw = tokenService.generateRefreshToken();
        jdbc.update("""
                INSERT INTO refresh_tokens(user_id, token_hash, created_at, expires_at)
                VALUES (?, ?, now() - interval '8 days', now() - interval '1 day')""",
                userId, tokenService.hashRefreshToken(raw));

        refresh(raw).andExpect(status().isUnauthorized());
    }

    @Test
    void BR16_unknownRefreshTokenIsRejected() throws Exception {
        refresh(tokenService.generateRefreshToken())
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void refreshIgnoresStaleBearerHeader() throws Exception {
        String raw = loginTokens(EMAIL, PASSWORD).get("refreshToken").asText();

        mvc.perform(withBearer(post("/api/auth/refresh"), "expired.or.garbage")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body("refreshToken", raw))))
                .andExpect(status().isOk());
    }

    @Test
    void BR17_logoutWithUnknownTokenIsNoContent() throws Exception {
        postJson("/api/auth/logout", body("refreshToken", tokenService.generateRefreshToken()))
                .andExpect(status().isNoContent());
    }

    // ---------------------------------------------------------------- public paths & CORS

    @Test
    void adminPing_withoutToken_returns401() throws Exception {
        mvc.perform(get("/api/admin/ping")).andExpect(status().isUnauthorized());
    }

    @Test
    void openApiDocsArePublicAndDeclareBearerScheme() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.bearerFormat").value("JWT"))
                .andExpect(jsonPath("$.security[0].bearerAuth").exists())
                // auth endpoints are public: explicit empty security requirement
                .andExpect(jsonPath("$.paths['/api/auth/login'].post.security").isEmpty());
    }

    @Test
    void swaggerUiIsPublic() throws Exception {
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    }

    @Test
    void corsPreflightFromFrontendOriginIsAllowed() throws Exception {
        mvc.perform(options("/api/users/me")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"));
    }

    @Test
    void corsPreflightFromOtherOriginIsRejected() throws Exception {
        mvc.perform(options("/api/users/me")
                        .header(HttpHeaders.ORIGIN, "http://evil.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isForbidden());
    }

    private Instant revokedAt(String rawRefreshToken) {
        Timestamp ts = jdbc.queryForObject(
                "SELECT revoked_at FROM refresh_tokens WHERE token_hash = ?",
                Timestamp.class, tokenService.hashRefreshToken(rawRefreshToken));
        return ts == null ? null : ts.toInstant();
    }
}
