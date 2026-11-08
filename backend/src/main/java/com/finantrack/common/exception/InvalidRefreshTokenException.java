package com.finantrack.common.exception;

/** Refresh token ausente, expirado, revogado ou reutilizado (401). */
public class InvalidRefreshTokenException extends RuntimeException {

    public InvalidRefreshTokenException() {
        super("Sessão expirada. Faça login novamente.");
    }
}
