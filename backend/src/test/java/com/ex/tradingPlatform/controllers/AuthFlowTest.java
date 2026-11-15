package com.ex.tradingPlatform.controllers;

import com.ex.tradingPlatform.TestcontainersConfiguration;
import com.ex.tradingPlatform.services.TokenService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AuthFlowTest {

    private static final String EMAIL = "john@example.com";
    private static final String PASSWORD = "secret123";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtDecoder jwtDecoder;
    @Autowired JwtEncoder jwtEncoder;
    @Autowired TokenService tokenService;

    private long userId;

    @BeforeEach
    void setUp() throws Exception {
        jdbc.update("DELETE FROM refresh_tokens");
        jdbc.update("DELETE FROM accounts");
        jdbc.update("DELETE FROM users");

        String body = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("email", EMAIL, "password", PASSWORD)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        userId = objectMapper.readTree(body).get("id").asLong();
    }

    // ---------------------------------------------------------------- login

    @Test
    void BR14_loginReturnsAccessAndRefreshToken() throws Exception {
        JsonNode tokens = loginOk(EMAIL, PASSWORD);

        assertThat(tokens.get("tokenType").asText()).isEqualTo("Bearer");
        assertThat(tokens.get("expiresIn").asLong()).isEqualTo(900);

        Jwt jwt = jwtDecoder.decode(tokens.get("accessToken").asText());
        assertThat(jwt.getSubject()).isEqualTo(String.valueOf(userId));
        assertThat(jwt.getClaimAsString("role")).isEqualTo("USER");
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())).isEqualTo(Duration.ofMinutes(15));
        assertThat(jwt.getHeaders()).containsEntry("alg", "HS256");
    }

    @Test
    void BR15_refreshTokenStoredOnlyAsSha256HashWith7DayExpiry() throws Exception {
        String raw = loginOk(EMAIL, PASSWORD).get("refreshToken").asText();

        assertThat(raw).matches("^[A-Za-z0-9_-]{43}$"); // 32 bytes base64url
        var row = jdbc.queryForMap("SELECT token_hash, expires_at, created_at FROM refresh_tokens");
        assertThat(row.get("token_hash")).isEqualTo(tokenService.hashRefreshToken(raw)).isNotEqualTo(raw);
        Instant created = ((java.sql.Timestamp) row.get("created_at")).toInstant();
        Instant expires = ((java.sql.Timestamp) row.get("expires_at")).toInstant();
        assertThat(Duration.between(created, expires)).isCloseTo(Duration.ofDays(7), Duration.ofSeconds(5));
    }

    @Test
    void loginNormalizesEmail() throws Exception {
        loginOk("  JOHN@Example.com ", PASSWORD);
    }

    @Test
    void BR14_wrongPasswordAndUnknownEmailGiveIdenticalResponse() throws Exception {
        String wrongPassword = login(EMAIL, "wrongpass1")
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Invalid credentials"))
                .andReturn().getResponse().getContentAsString();

        String unknownEmail = login("nobody@example.com", PASSWORD)
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        assertThat(unknownEmail).isEqualTo(wrongPassword);
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

    // ---------------------------------------------------------------- /api/users/me

    @Test
    void me_withValidAccessToken_returnsCurrentUser() throws Exception {
        String access = loginOk(EMAIL, PASSWORD).get("accessToken").asText();

        mvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId))
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void me_withoutToken_returns401ProblemDetail() throws Exception {
        mvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void me_withMalformedToken_returns401ProblemDetail() throws Exception {
        mvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer not.a.jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void me_withExpiredToken_returns401ProblemDetail() throws Exception {
        Instant past = Instant.now().minus(1, ChronoUnit.HOURS);
        String expired = encode(JwtClaimsSet.builder()
                .subject(String.valueOf(userId))
                .issuedAt(past)
                .expiresAt(past.plus(15, ChronoUnit.MINUTES))
                .claim("role", "USER")
                .build());

        mvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + expired))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    // ---------------------------------------------------------------- refresh

    @Test
    void BR16_refreshRotatesTokens() throws Exception {
        JsonNode first = loginOk(EMAIL, PASSWORD);
        String oldRefresh = first.get("refreshToken").asText();

        JsonNode second = refreshOk(oldRefresh);
        assertThat(second.get("refreshToken").asText()).isNotEqualTo(oldRefresh);
        assertThat(jwtDecoder.decode(second.get("accessToken").asText()).getSubject())
                .isEqualTo(String.valueOf(userId));

        assertThat(revokedAt(oldRefresh)).isNotNull();
        assertThat(revokedAt(second.get("refreshToken").asText())).isNull();
    }

    @Test
    void BR16_reusingRevokedRefreshTokenRevokesAllUserTokens() throws Exception {
        String stolen = loginOk(EMAIL, PASSWORD).get("refreshToken").asText();
        String otherSession = loginOk(EMAIL, PASSWORD).get("refreshToken").asText();
        String rotated = refreshOk(stolen).get("refreshToken").asText();

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
        String raw = loginOk(EMAIL, PASSWORD).get("refreshToken").asText();

        mvc.perform(post("/api/auth/refresh")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer expired.or.garbage")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("refreshToken", raw)))
                .andExpect(status().isOk());
    }

    // ---------------------------------------------------------------- logout

    @Test
    void BR17_logoutRevokesRefreshToken() throws Exception {
        String raw = loginOk(EMAIL, PASSWORD).get("refreshToken").asText();

        mvc.perform(post("/api/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("refreshToken", raw)))
                .andExpect(status().isNoContent());

        assertThat(revokedAt(raw)).isNotNull();
        refresh(raw).andExpect(status().isUnauthorized());
    }

    @Test
    void BR17_logoutWithUnknownTokenIsNoContent() throws Exception {
        mvc.perform(post("/api/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("refreshToken", tokenService.generateRefreshToken())))
                .andExpect(status().isNoContent());
    }

    // ---------------------------------------------------------------- roles

    @Test
    void adminPing_asUser_returns403ProblemDetail() throws Exception {
        String access = loginOk(EMAIL, PASSWORD).get("accessToken").asText();

        mvc.perform(get("/api/admin/ping").header(HttpHeaders.AUTHORIZATION, "Bearer " + access))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void adminPing_asAdmin_returns200() throws Exception {
        jdbc.update("UPDATE users SET role = 'ADMIN'");
        String access = loginOk(EMAIL, PASSWORD).get("accessToken").asText();

        mvc.perform(get("/api/admin/ping").header(HttpHeaders.AUTHORIZATION, "Bearer " + access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("pong"));
    }

    @Test
    void adminPing_withoutToken_returns401() throws Exception {
        mvc.perform(get("/api/admin/ping")).andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------- public paths & CORS

    @Test
    void openApiDocsArePublic() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
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

    // ---------------------------------------------------------------- helpers

    private ResultActions login(String email, String password) throws Exception {
        return mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("email", email, "password", password)));
    }

    private JsonNode loginOk(String email, String password) throws Exception {
        String body = login(email, password).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private ResultActions refresh(String refreshToken) throws Exception {
        return mvc.perform(post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("refreshToken", refreshToken)));
    }

    private JsonNode refreshOk(String refreshToken) throws Exception {
        String body = refresh(refreshToken).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private Instant revokedAt(String rawRefreshToken) {
        java.sql.Timestamp ts = jdbc.queryForObject(
                "SELECT revoked_at FROM refresh_tokens WHERE token_hash = ?",
                java.sql.Timestamp.class, tokenService.hashRefreshToken(rawRefreshToken));
        return ts == null ? null : ts.toInstant();
    }

    private String encode(JwtClaimsSet claims) {
        return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    private String json(String... keyValues) throws Exception {
        var node = objectMapper.createObjectNode();
        for (int i = 0; i < keyValues.length; i += 2) {
            node.put(keyValues[i], keyValues[i + 1]);
        }
        return objectMapper.writeValueAsString(node);
    }
}
