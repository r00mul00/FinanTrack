package com.finantrack.common.exception;

/** Duplicidade ou recurso em uso (409). */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
