package com.ex.tradingPlatform.controllers;

import com.ex.tradingPlatform.IntegrationTest;
import com.ex.tradingPlatform.services.SseEmitterRegistry;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.MvcResult;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StreamControllerTest extends IntegrationTest {

    @Autowired
    private SseEmitterRegistry registry;

    @Autowired
    private JwtEncoder jwtEncoder;

    private long userId;
    private String token;

    @BeforeEach
    void logInUser() throws Exception {
        userId = registerUser("john@example.com", "secret123");
        token = loginTokens("john@example.com", "secret123").get("accessToken").asText();
    }

    @AfterEach
    void closeConnections() {
        registry.completeAll();
    }

    @Test
    void BR53_withoutToken_returns401() throws Exception {
        mvc.perform(get("/api/stream").accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "not-a-jwt", "eyJhbGciOiJIUzI1NiJ9.e30.invalid-signature"})
    void BR53_malformedToken_returns401(String bad) throws Exception {
        mvc.perform(get("/api/stream").param("token", bad).accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    void BR53_expiredToken_returns401() throws Exception {
        Instant past = Instant.now().minusSeconds(3600);
        String expired = encode(jwtEncoder, String.valueOf(userId), past, past.plusSeconds(900));

        mvc.perform(get("/api/stream").param("token", expired).accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
        assertThat(registry.connectionCount(userId)).isZero();
    }

    @Test
    void BR53_tokenSignedWithAnotherKey_returns401() throws Exception {
        JwtEncoder foreign = new NimbusJwtEncoder(new ImmutableSecret<>(new SecretKeySpec(
                "another-secret-of-at-least-32-bytes!!".getBytes(StandardCharsets.UTF_8), "HmacSHA256")));
        String forged = encode(foreign, String.valueOf(userId), Instant.now(), Instant.now().plusSeconds(900));

        mvc.perform(get("/api/stream").param("token", forged).accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    void BR53_validToken_opensEventStreamAndRegistersConnection() throws Exception {
        MvcResult result = mvc.perform(get("/api/stream").param("token", token).accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted())
                .andExpect(status().isOk())
                .andReturn();

        assertThat(registry.connectionCount(userId)).isEqualTo(1);

        // headers, including the content type, are written together with the first event
        registry.sendToUser(userId, "order", "hello");
        assertThat(result.getResponse().getContentType()).startsWith(MediaType.TEXT_EVENT_STREAM_VALUE);
        assertThat(result.getResponse().getContentAsString()).contains("event:order\ndata:hello\n\n");
    }

    @Test
    void BR53_corsAllowsConfiguredFrontendOrigin() throws Exception {
        mvc.perform(get("/api/stream").param("token", token)
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"));
    }

    @Test
    void BR53_corsRejectsOtherOrigins() throws Exception {
        mvc.perform(get("/api/stream").param("token", token)
                        .header(HttpHeaders.ORIGIN, "http://evil.example")
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
        assertThat(registry.connectionCount(userId)).isZero();
    }

    private static String encode(JwtEncoder encoder, String subject, Instant issuedAt, Instant expiresAt) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(subject)
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim("role", "USER")
                .build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }
}
