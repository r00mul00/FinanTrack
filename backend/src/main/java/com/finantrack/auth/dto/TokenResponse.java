package com.finantrack.auth.dto;

public record TokenResponse(String accessToken, String tokenType, long expiresIn) {
}
