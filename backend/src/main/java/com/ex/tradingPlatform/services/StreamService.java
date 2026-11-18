package com.ex.tradingPlatform.services;

import com.ex.tradingPlatform.exceptions.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Opens an SSE connection. EventSource cannot send headers, so the access token arrives as a query
 * parameter and is checked here with the same JwtDecoder (signature and expiry) as bearer tokens.
 */
@Service
@RequiredArgsConstructor
public class StreamService {

    static final String INVALID_TOKEN = "INVALID_TOKEN";

    private final JwtDecoder jwtDecoder;
    private final SseEmitterRegistry registry;

    public SseEmitter subscribe(String token) {
        if (token == null || token.isBlank()) {
            throw invalidToken();
        }
        try {
            Jwt jwt = jwtDecoder.decode(token);
            return registry.register(Long.valueOf(jwt.getSubject()));
        } catch (JwtException | NumberFormatException ex) {
            throw invalidToken();
        }
    }

    private static BusinessException invalidToken() {
        return new BusinessException(HttpStatus.UNAUTHORIZED, INVALID_TOKEN, "Missing, invalid or expired access token.");
    }
}
