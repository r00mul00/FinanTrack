package com.finantrack.common.exception;

/** Parâmetro de consulta inválido (400). */
public class InvalidParameterException extends RuntimeException {

    public InvalidParameterException(String message) {
        super(message);
    }
}
