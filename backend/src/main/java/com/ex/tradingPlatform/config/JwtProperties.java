package com.ex.tradingPlatform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

@ConfigurationProperties("app.jwt")
public record JwtProperties(
        String secret,
        @DefaultValue("15m") Duration accessTokenTtl,
        @DefaultValue("7d") Duration refreshTokenTtl
) {

    static final int MIN_SECRET_BYTES = 32;

    public JwtProperties {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalArgumentException(
                    "JWT secret (env JWT_SECRET) must be at least " + MIN_SECRET_BYTES + " bytes for HS256");
        }
    }

    @Override
    public String toString() {
        return "JwtProperties[secret=***, accessTokenTtl=" + accessTokenTtl
                + ", refreshTokenTtl=" + refreshTokenTtl + "]";
    }
}
