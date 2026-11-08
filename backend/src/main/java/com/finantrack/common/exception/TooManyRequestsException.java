package com.finantrack.common.exception;

/** Limite de tentativas excedido (429). */
public class TooManyRequestsException extends RuntimeException {

    public TooManyRequestsException() {
        super("Muitas tentativas. Aguarde alguns minutos e tente novamente.");
    }
}
