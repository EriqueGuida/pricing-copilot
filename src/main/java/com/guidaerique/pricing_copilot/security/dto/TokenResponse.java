package com.guidaerique.pricing_copilot.security.dto;

public record TokenResponse(
        String accessToken,
        String tokenType,
        long expiresIn
) {
    public TokenResponse(String accessToken, long expiresIn) {
        this(accessToken, "Bearer", expiresIn);
    }
}