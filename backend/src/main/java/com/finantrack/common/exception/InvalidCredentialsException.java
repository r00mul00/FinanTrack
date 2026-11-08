package com.finantrack.common.exception;

/** E-mail ou senha incorretos (401), sem indicar qual dos dois. */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("E-mail ou senha inválidos.");
    }
}
