package com.finantrack.common.exception;

/** Registro inexistente ou pertencente a outro usuário (sempre 404). */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
