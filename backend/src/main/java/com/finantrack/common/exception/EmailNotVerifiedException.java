package com.finantrack.common.exception;

/** Credenciais corretas, mas e-mail ainda não validado (403). */
public class EmailNotVerifiedException extends RuntimeException {

    public EmailNotVerifiedException() {
        super("Confirme seu e-mail para entrar.");
    }
}
