package com.finantrack.auth.dto;

/** Resultado interno de login ou renovação. O refresh token só sai em cookie. */
public record AuthSession(TokenResponse accessToken, String refreshToken) {
}
