package com.finantrack.common.exception;

/** Regra de negócio violada, como saldo ou limite insuficiente (422). */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
