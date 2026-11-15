package com.ex.tradingPlatform.dto;

/**
 * @param expiresIn access token lifetime in seconds
 */
public record TokenResponse(String accessToken, String refreshToken, String tokenType, long expiresIn) {

    public static TokenResponse bearer(String accessToken, String refreshToken, long expiresIn) {
        return new TokenResponse(accessToken, refreshToken, "Bearer", expiresIn);
    }

    @Override
    public String toString() {
        return "TokenResponse[accessToken=***, refreshToken=***, tokenType=" + tokenType
                + ", expiresIn=" + expiresIn + "]";
    }
}
